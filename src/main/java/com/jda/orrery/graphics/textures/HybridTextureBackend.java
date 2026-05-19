package com.jda.orrery.graphics.textures;

import static org.lwjgl.opengl.EXTTextureCompressionS3TC.*;
import static org.lwjgl.opengl.EXTTextureSRGB.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.system.MemoryUtil.*;

import com.jda.orrery.core.logging.Logging;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Texture backend with dynamic bucket allocation.
 *
 * Key features: - Discovers available textures at runtime based on GPU tier - Groups textures
 * into buckets by similar dimensions - Preserves scientific imaging resolutions (non-power-of-2) -
 * Direct DDS BC3 loading without complex loader infrastructure - Synchronous loading at startup for
 * deterministic behavior
 */
public class HybridTextureBackend implements TextureArraySystem.TextureBackend {
    private static final Logger LOGGER = Logging.logger(HybridTextureBackend.class);

    // Critical bodies that must be loaded at startup
    private static final Set<String> CRITICAL_BODIES =
            Set.of(
                    "sun", "mercury", "venus", "earth", "moon", "mars", "jupiter", "saturn",
                    "uranus", "neptune");

    // DDS file constants
    private static final int DDS_HEADER_SIZE = 128;
    private static final int DDS_MAGIC = 0x20534444; // "DDS "

    // Texture storage
    private final Map<String, TextureArraySystem.TextureHandle> criticalTextures = new HashMap<>();
    private final List<TextureBucket> buckets = new ArrayList<>();

    // GPU capabilities
    private GPUCapabilities.GPUInfo gpuInfo;
    private GPUCapabilities.GPUTier gpuTier;
    private boolean supportsImmutableStorage;

    // Texture resolver for format priority and selection
    private TextureResolver textureResolver;

    /** Represents texture metadata discovered from file headers. */
    private static class TextureInfo {
        final String bodyId;
        final Path path;
        final int width;
        final int height;
        final boolean isCompressed;
        final long fileSize;
        final int compressedSize; // Actual compressed data size from DDS header
        final int mipMapCount; // Number of mipmap levels in DDS

        TextureInfo(
                String bodyId,
                Path path,
                int width,
                int height,
                boolean isCompressed,
                long fileSize,
                int compressedSize,
                int mipMapCount) {
            this.bodyId = bodyId;
            this.path = path;
            this.width = width;
            this.height = height;
            this.isCompressed = isCompressed;
            this.fileSize = fileSize;
            this.compressedSize = compressedSize;
            this.mipMapCount = mipMapCount;
        }

        long getPixelCount() {
            return (long) width * height;
        }
    }

    /** Dynamic texture bucket sized to actual texture dimensions. */
    private static class TextureBucket {
        final String id;
        final int width;
        final int height;
        int textureArrayId = -1;
        int maxLayers;
        int mipMapCount = 1; // Track maximum mip levels needed for this bucket
        int nextLayer = 0;
        final List<String> bodies = new ArrayList<>();

        TextureBucket(int width, int height) {
            this.width = width;
            this.height = height;
            this.id = width + "x" + height;
        }

        long getTotalPixels() {
            return (long) width * height * maxLayers;
        }

        long getUsedPixels() {
            // Will be calculated based on actual textures
            return 0;
        }

        float getEfficiency() {
            long total = getTotalPixels();
            return total > 0 ? (float) getUsedPixels() / total : 0;
        }

        boolean canFit(TextureInfo info) {
            return info.width <= width && info.height <= height;
        }
    }

    @Override
    public void initialize() {
        LOGGER.info("Initializing HybridTextureBackend with TextureResolver");

        // 1. Detect GPU capabilities
        detectGPUCapabilities();

        // 2. Initialize TextureResolver for texture discovery and selection
        Path textureRoot = Paths.get("src/main/resources/textures");
        textureResolver = new TextureResolver(textureRoot);
        textureResolver.initialize();

        // 3. Get best textures for each critical body using TextureResolver
        Map<String, TextureInfo> availableTextures = resolveTexturesForTier(gpuTier);

        if (availableTextures.isEmpty()) {
            LOGGER.warning("No textures found for tier " + gpuTier);
            return;
        }

        // Group textures into buckets for shared-array packing.
        List<TextureBucket> buckets = createOptimalBuckets(availableTextures.values());

        // 4. Create texture arrays for each bucket
        createTextureArraysForBuckets(buckets, availableTextures);

        // 5. Load and upload textures to appropriate buckets
        loadTexturesIntoBuckets(availableTextures, buckets);

        // 6. Log efficiency metrics
        logBucketEfficiency(buckets);

        // Force GPU to complete all pending texture operations.
        // This ensures mipmaps are generated NOW, not during first frame
        LOGGER.info("Forcing GPU texture upload completion...");
        glFinish();

        LOGGER.info(
                String.format(
                        "HybridTextureBackend ready: %d textures in %d buckets",
                        criticalTextures.size(), buckets.size()));
    }

    private void detectGPUCapabilities() {
        gpuInfo = GPUCapabilities.getGPUInfo();
        gpuTier = GPUCapabilities.detectGPUTier();
        supportsImmutableStorage = gpuInfo.supportsImmutableStorage;

        LOGGER.info(
                String.format(
                        "GPU: %s, Tier: %s, VRAM: %.1f GB, Immutable storage: %s",
                        gpuInfo.renderer,
                        gpuTier.name(),
                        gpuInfo.estimatedVRAM / (1024.0 * 1024.0 * 1024.0),
                        supportsImmutableStorage));
    }

    /**
     * Uses TextureResolver to get best textures for each critical body. Single source of truth for
     * texture selection.
     */
    private Map<String, TextureInfo> resolveTexturesForTier(GPUCapabilities.GPUTier tier) {
        Map<String, TextureInfo> textures = new HashMap<>();

        for (String body : CRITICAL_BODIES) {
            // Get available resolutions for this body (sorted lowest to highest)
            List<Integer> availableResolutions = textureResolver.getAvailableResolutions(body);
            if (availableResolutions.isEmpty()) {
                LOGGER.warning("No textures available for " + body);
                continue;
            }

            // Direct 1:1 mapping: 4 tiers → 4 resolutions
            // ULTRA(0) → highest res (last index)
            // LOW(3) → lowest res (first index)
            int tierIndex = tier.ordinal();
            int resolutionIndex = Math.max(0, availableResolutions.size() - 1 - tierIndex);

            int selectedResolution = availableResolutions.get(resolutionIndex);

            // Get the texture at this exact resolution
            TextureResolver.ResolvedTexture resolved =
                    textureResolver.resolve(
                            body, TextureResolver.TextureLayer.VISUAL, selectedResolution);

            if (resolved != null) {
                try {
                    // Read actual dimensions from the file header - no assumptions
                    TextureInfo info = readTextureInfo(body, resolved.path);
                    textures.put(body, info);
                    LOGGER.fine(
                            String.format(
                                    "%s @ %s: %s %dx%d",
                                    body, tier, resolved.format, info.width, info.height));
                } catch (IOException e) {
                    LOGGER.warning(
                            "Failed to read texture info for " + body + ": " + e.getMessage());
                }
            }

            // Also check for ring textures for planets that have rings
            if (body.equals("saturn")
                    || body.equals("jupiter")
                    || body.equals("uranus")
                    || body.equals("neptune")) {

                LOGGER.fine("Looking for ring texture for " + body);

                // Ring textures are special - they're radial cross-sections that don't need
                // resolution tiers
                // Just request any available ring texture (resolution parameter is ignored for
                // rings)
                TextureResolver.ResolvedTexture ringResolved =
                        textureResolver.resolve(
                                body,
                                TextureResolver.TextureLayer.RING,
                                Integer.MAX_VALUE // Accept any resolution - rings don't have tiers
                                // currently
                                );

                if (ringResolved != null) {
                    LOGGER.fine(
                            String.format(
                                    "TextureResolver returned for %s RING layer: %s (layer=%s)",
                                    body, ringResolved.path, ringResolved.layer));

                    // Verify this is actually a ring texture
                    String filename = ringResolved.path.getFileName().toString().toLowerCase();
                    if (!filename.contains("_ring")) {
                        LOGGER.warning(
                                String.format(
                                        "ERROR: Got non-ring texture for %s: %s", body, filename));
                        continue; // Skip this incorrect texture
                    }

                    try {
                        TextureInfo ringInfo = readTextureInfo(body + "_ring", ringResolved.path);
                        textures.put(body + "_ring", ringInfo);
                        LOGGER.fine(
                                String.format(
                                        "RING TEXTURE LOADED: %s ring @ %s: %s %dx%d at %s",
                                        body,
                                        tier,
                                        ringResolved.format,
                                        ringInfo.width,
                                        ringInfo.height,
                                        ringResolved.path));
                    } catch (IOException e) {
                        LOGGER.warning(
                                "Failed to read ring texture info for "
                                        + body
                                        + ": "
                                        + e.getMessage());
                    }
                } else {
                    LOGGER.fine("No ring texture found for " + body);
                }
            }
        }

        LOGGER.info(String.format("Resolved %d textures for tier %s", textures.size(), tier));
        return textures;
    }

    /** Reads texture metadata from file header without loading pixel data. */
    private TextureInfo readTextureInfo(String bodyId, Path path) throws IOException {
        String filename = path.getFileName().toString().toLowerCase();

        if (filename.endsWith(".dds")) {
            return readDDSInfo(bodyId, path);
        } else if (filename.endsWith(".png") || filename.endsWith(".jpg")) {
            return readImageInfo(bodyId, path);
        } else {
            throw new IOException("Unsupported texture format: " + filename);
        }
    }

    /**
     * Reads DDS header to extract dimensions, format, and actual data size. Read actual compressed
     * size from header — don't recompute it.
     */
    private TextureInfo readDDSInfo(String bodyId, Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < DDS_HEADER_SIZE) {
                throw new IOException("Invalid DDS file: too small");
            }

            byte[] header = new byte[DDS_HEADER_SIZE];
            file.readFully(header);

            // Check magic number
            int magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (magic != DDS_MAGIC) {
                throw new IOException("Invalid DDS magic number");
            }

            // Read DDS header fields (DWORD offsets after magic "DDS ")
            int height = ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int width = ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int pitchOrLinearSize =
                    ByteBuffer.wrap(header, 20, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int mipMapCount =
                    ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // Check for BC3/DXT5 compression (FourCC at offset 84)
            String fourCC = new String(header, 84, 4);
            boolean isBC3 = fourCC.equals("DXT5");

            // For compressed textures, pitchOrLinearSize is the total compressed size for main
            // image
            // If 0 or looks wrong, calculate it
            int compressedSize;
            if (isBC3 && pitchOrLinearSize > 0) {
                compressedSize = pitchOrLinearSize;
            } else if (isBC3) {
                // Calculate BC3 size: 16 bytes per 4x4 block
                int blocksWide = (width + 3) / 4;
                int blocksHigh = (height + 3) / 4;
                compressedSize = blocksWide * blocksHigh * 16;
            } else {
                compressedSize = width * height * 4; // RGBA uncompressed
            }

            return new TextureInfo(
                    bodyId,
                    path,
                    width,
                    height,
                    isBC3,
                    file.length(),
                    compressedSize,
                    Math.max(1, mipMapCount));
        }
    }

    /** Reads PNG/JPG info using STB. */
    private TextureInfo readImageInfo(String bodyId, Path path) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Just get info, don't load data
            if (!STBImage.stbi_info(path.toString(), w, h, comp)) {
                throw new IOException("Failed to read image info: " + path);
            }

            // PNG files: uncompressed, no mipmaps
            return new TextureInfo(
                    bodyId,
                    path,
                    w.get(0),
                    h.get(0),
                    false,
                    Files.size(path),
                    w.get(0) * h.get(0) * 4,
                    1);
        }
    }

    /**
     * Group textures into buckets by GPU format and dimensions so each bucket can be packed into
     * a single texture array. Compressed and uncompressed textures must not share a bucket.
     */
    private List<TextureBucket> createOptimalBuckets(Collection<TextureInfo> textures) {
        List<TextureBucket> buckets = new ArrayList<>();

        // SEPARATE compressed and uncompressed textures - they cannot share arrays!
        List<TextureInfo> compressedTextures = new ArrayList<>();
        List<TextureInfo> uncompressedTextures = new ArrayList<>();

        for (TextureInfo texture : textures) {
            if (texture.isCompressed) {
                compressedTextures.add(texture);
            } else {
                uncompressedTextures.add(texture);
            }
        }

        // Sort each group by pixel count
        compressedTextures.sort((a, b) -> Long.compare(b.getPixelCount(), a.getPixelCount()));
        uncompressedTextures.sort((a, b) -> Long.compare(b.getPixelCount(), a.getPixelCount()));

        // Create buckets for compressed textures first
        for (TextureInfo texture : compressedTextures) {
            TextureBucket suitableBucket = null;

            // Find existing COMPRESSED bucket that can fit this texture
            for (TextureBucket bucket : buckets) {
                // Only consider buckets with compressed textures
                boolean bucketHasCompressed =
                        bucket.bodies.stream()
                                .anyMatch(
                                        bodyId ->
                                                compressedTextures.stream()
                                                        .anyMatch(t -> t.bodyId.equals(bodyId)));

                if (bucketHasCompressed && canFitEfficiently(texture, bucket)) {
                    suitableBucket = bucket;
                    break;
                }
            }

            if (suitableBucket == null) {
                // Create new bucket for compressed texture
                // Use exact dimensions for compressed textures to avoid seams
                int bucketWidth = texture.width;
                int bucketHeight = texture.height;
                suitableBucket = new TextureBucket(bucketWidth, bucketHeight);
                buckets.add(suitableBucket);
            }

            suitableBucket.bodies.add(texture.bodyId);
            suitableBucket.maxLayers = suitableBucket.bodies.size();
        }

        // Create buckets for uncompressed textures
        for (TextureInfo texture : uncompressedTextures) {
            TextureBucket suitableBucket = null;

            // Find existing UNCOMPRESSED bucket that can fit this texture
            for (TextureBucket bucket : buckets) {
                // Only consider buckets WITHOUT compressed textures
                boolean bucketHasCompressed =
                        bucket.bodies.stream()
                                .anyMatch(
                                        bodyId ->
                                                compressedTextures.stream()
                                                        .anyMatch(t -> t.bodyId.equals(bodyId)));

                if (!bucketHasCompressed && canFitEfficiently(texture, bucket)) {
                    suitableBucket = bucket;
                    break;
                }
            }

            if (suitableBucket == null) {
                // Create new bucket for uncompressed texture
                // Use exact dimensions to avoid UV scaling issues
                int bucketWidth = texture.width;
                int bucketHeight = texture.height;
                suitableBucket = new TextureBucket(bucketWidth, bucketHeight);
                buckets.add(suitableBucket);
            }

            suitableBucket.bodies.add(texture.bodyId);
            suitableBucket.maxLayers = suitableBucket.bodies.size();
        }

        LOGGER.info(
                String.format(
                        "Created %d texture buckets (%d compressed, %d uncompressed) for %d textures",
                        buckets.size(),
                        compressedTextures.isEmpty()
                                ? 0
                                : buckets.stream()
                                        .filter(
                                                b ->
                                                        b.bodies.stream()
                                                                .anyMatch(
                                                                        bodyId ->
                                                                                compressedTextures
                                                                                        .stream()
                                                                                        .anyMatch(
                                                                                                t ->
                                                                                                        t
                                                                                                                .bodyId
                                                                                                                .equals(
                                                                                                                        bodyId))))
                                        .count(),
                        uncompressedTextures.isEmpty()
                                ? 0
                                : buckets.stream()
                                        .filter(
                                                b ->
                                                        b.bodies.stream()
                                                                .anyMatch(
                                                                        bodyId ->
                                                                                uncompressedTextures
                                                                                        .stream()
                                                                                        .anyMatch(
                                                                                                t ->
                                                                                                        t
                                                                                                                .bodyId
                                                                                                                .equals(
                                                                                                                        bodyId))))
                                        .count(),
                        textures.size()));

        return buckets;
    }

    /** Checks if a texture fits efficiently in a bucket (< 25% wasted space). */
    private boolean canFitEfficiently(TextureInfo texture, TextureBucket bucket) {
        if (!bucket.canFit(texture)) {
            return false;
        }

        // Check waste ratio
        long texturePixels = texture.getPixelCount();
        long bucketPixels = (long) bucket.width * bucket.height;
        float wasteRatio = 1.0f - ((float) texturePixels / bucketPixels);

        return wasteRatio < 0.25f; // Less than 25% waste
    }

    /** Creates OpenGL texture arrays for each bucket. */
    private void createTextureArraysForBuckets(
            List<TextureBucket> buckets, Map<String, TextureInfo> textures) {
        for (TextureBucket bucket : buckets) {
            int[] id = new int[1];
            glGenTextures(id);
            bucket.textureArrayId = id[0];

            glBindTexture(GL_TEXTURE_2D_ARRAY, bucket.textureArrayId);

            // Get first texture in bucket to determine format (all should be same type)
            String firstBodyId = bucket.bodies.get(0);
            TextureInfo firstTexture = textures.get(firstBodyId);

            if (firstTexture.isCompressed) {
                // Diffuse textures are authored in sRGB; the driver decodes to linear on sample.
                // BC3 stays at base level only — pre-generated compressed mips via the asset
                // pipeline (toktx) are post-1.0 work.
                int internalFormat = GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
                glTexStorage3D(
                        GL_TEXTURE_2D_ARRAY,
                        1,
                        internalFormat,
                        bucket.width,
                        bucket.height,
                        bucket.maxLayers);
                glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            } else {
                // Uncompressed textures: sRGB internal format so the driver returns linear on
                // sample,
                // with a full mip chain populated via glGenerateMipmap after each upload.
                int internalFormat = GL_SRGB8_ALPHA8;
                int mipLevels = mipLevelsFor(bucket.width, bucket.height);
                glTexStorage3D(
                        GL_TEXTURE_2D_ARRAY,
                        mipLevels,
                        internalFormat,
                        bucket.width,
                        bucket.height,
                        bucket.maxLayers);
                glTexParameteri(
                        GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            }

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            LOGGER.fine(
                    String.format(
                            "Created texture array for bucket %s: %dx%d×%d",
                            bucket.id, bucket.width, bucket.height, bucket.maxLayers));
        }

        this.buckets.addAll(buckets);
    }

    /** Loads textures and uploads them to appropriate buckets. */
    private void loadTexturesIntoBuckets(
            Map<String, TextureInfo> textures, List<TextureBucket> buckets) {
        LOGGER.info(
                "Loading "
                        + textures.size()
                        + " textures into buckets. Keys: "
                        + textures.keySet());
        for (Map.Entry<String, TextureInfo> entry : textures.entrySet()) {
            String bodyId = entry.getKey();
            TextureInfo info = entry.getValue();
            LOGGER.info(
                    "Processing texture: " + bodyId + " (" + info.width + "x" + info.height + ")");

            // Find the bucket for this texture
            TextureBucket bucket = findBucketForTexture(info, buckets);
            if (bucket == null) {
                LOGGER.warning("No suitable bucket found for " + bodyId);
                continue;
            }

            ByteBuffer data = null;
            try {
                data = loadTextureData(info);
                uploadTextureToBucket(info, bucket, data);

                float scaleU = (float) info.width / bucket.width;
                float scaleV = (float) info.height / bucket.height;

                TextureArraySystem.TextureHandle handle =
                        new TextureArraySystem.TextureHandle(
                                bucket.textureArrayId,
                                bucket.nextLayer++,
                                TextureArraySystem.BackendType.IMMUTABLE,
                                scaleU,
                                scaleV,
                                bodyId,
                                info.isCompressed);

                criticalTextures.put(bodyId.toLowerCase(), handle);

                LOGGER.fine(
                        String.format(
                                "Loaded %s into bucket %s at layer %d",
                                bodyId, bucket.id, bucket.nextLayer - 1));

            } catch (IOException e) {
                LOGGER.warning("Failed to load texture for " + bodyId + ": " + e.getMessage());
            } finally {
                // Always free the native buffer — previous code leaked on exception
                if (data != null) {
                    MemoryUtil.memFree(data);
                }
            }
        }
    }

    /** Finds the best bucket for a texture. */
    private TextureBucket findBucketForTexture(TextureInfo info, List<TextureBucket> buckets) {
        for (TextureBucket bucket : buckets) {
            if (bucket.bodies.contains(info.bodyId)) {
                return bucket;
            }
        }
        return null;
    }

    /** Loads texture data from file. */
    private ByteBuffer loadTextureData(TextureInfo info) throws IOException {
        if (info.path.toString().toLowerCase().endsWith(".dds")) {
            return loadDDSData(info.path);
        } else {
            return loadImageData(info.path);
        }
    }

    /** Loads DDS compressed texture data including all mipmap levels. */
    private ByteBuffer loadDDSData(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            // Skip header
            file.seek(DDS_HEADER_SIZE);

            // Read all compressed data (includes all mipmap levels)
            int dataSize = (int) (file.length() - DDS_HEADER_SIZE);
            ByteBuffer buffer = MemoryUtil.memAlloc(dataSize);

            FileChannel channel = file.getChannel();
            channel.position(DDS_HEADER_SIZE);
            channel.read(buffer);
            buffer.flip();

            return buffer;
        }
    }

    /** Loads PNG/JPG texture data. */
    private ByteBuffer loadImageData(Path path) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Force RGBA format
            ByteBuffer data = STBImage.stbi_load(path.toString(), w, h, comp, 4);

            if (data == null) {
                throw new IOException("Failed to load image: " + STBImage.stbi_failure_reason());
            }

            return data;
        }
    }

    /**
     * Number of mipmap levels for a 2D image, including the base level. floor(log2(max(w, h))) + 1
     * — the standard full chain down to 1x1.
     */
    private static int mipLevelsFor(int width, int height) {
        return 1 + (int) (Math.log(Math.max(width, height)) / Math.log(2));
    }

    /** Uploads texture data to a bucket's texture array. */
    private void uploadTextureToBucket(TextureInfo info, TextureBucket bucket, ByteBuffer data) {
        glBindTexture(GL_TEXTURE_2D_ARRAY, bucket.textureArrayId);

        if (info.isCompressed) {
            // BC3/DXT5 compressed upload - just base level for now
            // Calculate size for base level
            int blocksWide = Math.max(1, (info.width + 3) / 4);
            int blocksHigh = Math.max(1, (info.height + 3) / 4);
            int levelSize = blocksWide * blocksHigh * 16; // 16 bytes per 4x4 block

            // Limit buffer to just the base level data
            data.position(0);
            data.limit(levelSize);

            glCompressedTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    0,
                    0,
                    0,
                    bucket.nextLayer,
                    info.width,
                    info.height,
                    1,
                    GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT,
                    data.slice());

            // Debug: Log upload details
            LOGGER.fine(
                    String.format(
                            "Uploaded BC3 texture %s: %dx%d, %d bytes to layer %d",
                            info.bodyId, info.width, info.height, levelSize, bucket.nextLayer));

            // Reset buffer position/limit
            data.position(0);
            data.limit(data.capacity());
        } else {
            // Uncompressed RGBA upload. Source bytes are sRGB (from STB); the array is
            // GL_SRGB8_ALPHA8, so no CPU-side conversion is needed.
            glTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    0,
                    0,
                    0,
                    bucket.nextLayer,
                    info.width,
                    info.height,
                    1,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    data);

            // Regenerate the mip chain for the whole array. Only runs at load time, so
            // the cost of re-generating all layers on each upload is negligible.
            glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        }
    }

    /** Logs bucket efficiency metrics. */
    private void logBucketEfficiency(List<TextureBucket> buckets) {
        LOGGER.info("=== Texture Bucket Efficiency Report ===");

        long totalPixels = 0;
        long totalMemory = 0;

        for (TextureBucket bucket : buckets) {
            long bucketPixels = bucket.getTotalPixels();
            long bucketMemory = bucketPixels * 4; // Assume 4 bytes per pixel

            LOGGER.info(
                    String.format(
                            "Bucket %s: %d bodies, %.1f MB, efficiency: %.1f%%",
                            bucket.id,
                            bucket.bodies.size(),
                            bucketMemory / (1024.0 * 1024.0),
                            bucket.getEfficiency() * 100));

            totalPixels += bucketPixels;
            totalMemory += bucketMemory;
        }

        LOGGER.info(
                String.format(
                        "Total: %d buckets, %.1f MB VRAM",
                        buckets.size(), totalMemory / (1024.0 * 1024.0)));
    }

    @Override
    public TextureArraySystem.TextureHandle requestTexture(
            String bodyId, TextureArraySystem.ViewContext context) {
        // Caller passes already-lowercased bodyId; skips a per-call toLowerCase() allocation.
        // The catalog should store keys in lowercase already
        return criticalTextures.get(bodyId);
    }

    @Override
    public boolean validate() {
        return !criticalTextures.isEmpty();
    }

    @Override
    public long getAvailableMemory() {
        // Calculate used memory
        long usedMemory = 0;
        for (TextureBucket bucket : buckets) {
            usedMemory += bucket.getTotalPixels() * 4; // 4 bytes per pixel
        }

        // Get total VRAM
        long totalVRAM = gpuInfo.estimatedVRAM;

        // Reserve 25% for other GPU resources
        long availableMemory = (totalVRAM * 3 / 4) - usedMemory;

        return Math.max(availableMemory, GPUCapabilities.GPUTier.LOW.targetMemory);
    }

    @Override
    public TextureArraySystem.BackendType getType() {
        return TextureArraySystem.BackendType.IMMUTABLE;
    }

    @Override
    public void update(float deltaTime) {
        // No updates needed - all textures loaded at startup
    }

    @Override
    public void dispose() {
        LOGGER.info("Disposing HybridTextureBackend");

        // Delete texture arrays
        for (TextureBucket bucket : buckets) {
            if (bucket.textureArrayId != -1) {
                glDeleteTextures(bucket.textureArrayId);
            }
        }

        buckets.clear();
        criticalTextures.clear();
    }
}
