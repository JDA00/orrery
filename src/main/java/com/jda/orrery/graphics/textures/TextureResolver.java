package com.jda.orrery.graphics.textures;

import com.jda.orrery.core.logging.Logging;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Convention-based texture discovery and resolution system. Scans texture directories at startup
 * and builds a priority-sorted catalog.
 *
 * Naming conventions supported: - {body}_{layer}_{resolution}k.{format} (earth_visual_16k.ktx2)
 * - {body}_{resolution}k.{format} (earth_16k.png) - {body}_{layer}.{format} (earth_night.png) -
 * {body}.{format} (earth.png)
 */
public class TextureResolver {
    private static final Logger LOGGER = Logging.logger(TextureResolver.class);

    // Cached scan results organized by body
    private final Map<String, BodyTextureSet> textureDatabase = new ConcurrentHashMap<>();
    private final Path textureRoot;
    private boolean initialized = false;

    public static class ResolvedTexture {
        public final String bodyId;
        public final TextureLayer layer;
        public final int resolution;
        public final TextureFormat format;
        public final Path path;
        public final long fileSize;
        public final int priority; // Loading priority
        public int maxTextureSize = 0; // Maximum texture size for GPU tier (0 = no limit)

        public ResolvedTexture(
                String bodyId,
                TextureLayer layer,
                int resolution,
                TextureFormat format,
                Path path,
                long fileSize) {
            this.bodyId = bodyId;
            this.layer = layer;
            this.resolution = resolution;
            this.format = format;
            this.path = path;
            this.fileSize = fileSize;
            this.priority = calculatePriority(bodyId, layer);
        }

        /**
         * Explicit loading priority order based on visibility from Earth, scientific interest, and
         * rendering importance.
         */
        private static int calculatePriority(String body, TextureLayer layer) {
            // Higher number = loaded first.
            int basePriority = getBodyLoadingPriority(body.toLowerCase());

            // Layer priorities (visual always loads first)
            int layerPriority = getLayerPriority(layer);

            return basePriority + layerPriority;
        }

        private static int getBodyLoadingPriority(String body) {
            switch (body) {
                // Critical (always visible)
                case "sun":
                    return 100; // Light source, always needed

                // Primary viewing targets
                case "earth":
                    return 90; // Home planet
                case "moon":
                    return 85; // Earth's companion

                // High scientific interest
                case "mars":
                    return 75; // Primary exploration target
                case "jupiter":
                    return 70; // Largest planet
                case "saturn":
                    return 65; // Ring system showcase

                // Inner system
                case "venus":
                    return 55; // Earth's neighbor
                case "mercury":
                    return 50; // Innermost planet

                // Outer system
                case "uranus":
                    return 40; // Ice giant
                case "neptune":
                    return 35; // Outermost major planet

                // Galilean moons (if viewing Jupiter)
                case "io":
                    return 30;
                case "europa":
                    return 28;
                case "ganymede":
                    return 26;
                case "callisto":
                    return 24;

                // Other moons and bodies
                default:
                    return 20;
            }
        }

        private static int getLayerPriority(TextureLayer layer) {
            // Visual data first, then scientific layers.
            switch (layer) {
                case VISUAL:
                    return 10; // Primary visual
                case NIGHT:
                    return 8; // City lights/aurora
                case CLOUDS:
                    return 6; // Atmospheric features
                case ELEVATION:
                    return 5; // Topographic data
                case NORMAL:
                    return 4; // Surface detail
                case SPECULAR:
                    return 3; // Water/ice reflection
                case ROUGHNESS:
                    return 2; // Surface properties
                case MASK:
                    return 1; // Ocean mask
                default:
                    return 0;
            }
        }
    }

    public static class BodyTextureSet {
        public final String bodyId;
        public final Map<TextureLayer, List<ResolvedTexture>> layerTextures;
        public int maxResolution;
        public int overallPriority;

        public BodyTextureSet(String bodyId) {
            this.bodyId = bodyId;
            this.layerTextures = new EnumMap<>(TextureLayer.class);
            this.maxResolution = 0;
            this.overallPriority = 0;
        }

        public void addTexture(ResolvedTexture texture) {
            layerTextures.computeIfAbsent(texture.layer, k -> new ArrayList<>()).add(texture);
            // Update max resolution
            if (texture.resolution > maxResolution) {
                maxResolution = texture.resolution;
            }
            // Update overall priority
            if (texture.priority > overallPriority) {
                overallPriority = texture.priority;
            }
        }

        public ResolvedTexture getBestTexture(TextureLayer layer, int maxRes) {
            List<ResolvedTexture> candidates = layerTextures.get(layer);
            if (candidates == null || candidates.isEmpty()) return null;

            // Format priority cascade based on platform capabilities
            List<TextureFormat> formatPriority = getFormatPriorityList();

            // For RING textures, don't filter by resolution - they're radial cross-sections
            List<ResolvedTexture> validCandidates;
            if (layer == TextureLayer.RING) {
                // Ring textures: accept any resolution, just check format support
                validCandidates =
                        candidates.stream()
                                .filter(t -> isFormatSupported(t.format))
                                .collect(java.util.stream.Collectors.toList());
            } else {
                // Normal textures: filter by resolution and support
                validCandidates =
                        candidates.stream()
                                .filter(t -> t.resolution <= maxRes)
                                .filter(t -> isFormatSupported(t.format))
                                .collect(java.util.stream.Collectors.toList());
            }

            if (validCandidates.isEmpty()) return null;

            // Select by format priority first, then resolution.
            // Format takes precedence so the GPU-friendliest layout wins even
            // if a less-friendly format happens to ship a higher resolution.
            for (TextureFormat preferredFormat : formatPriority) {
                // Find best resolution for this format
                Optional<ResolvedTexture> best =
                        validCandidates.stream()
                                .filter(t -> t.format == preferredFormat)
                                .max(Comparator.comparingInt(t -> t.resolution));

                if (best.isPresent()) {
                    return best.get(); // Return first match in priority order
                }
            }

            // Fallback: highest resolution of any format
            return validCandidates.stream()
                    .max(Comparator.comparingInt(t -> t.resolution))
                    .orElse(null);
        }
    }

    public enum TextureLayer {
        // Standard texture layers for planetary visualization

        // Primary visual layers (sRGB color space)
        VISUAL("visual", ""), // Diffuse/albedo color texture
        EMISSION("emission", "emissive"), // Self-illumination (lava, auroras)
        NIGHT("night", "lights"), // Night-side lights (cities, volcanic)
        CLOUDS("clouds", "atmosphere"), // Atmospheric visual layers
        RING("ring"), // Ring system textures (Saturn, etc.)

        // PBR material parameters (linear color space)
        NORMAL("normal", "norm"), // Surface normal perturbation
        ROUGHNESS("roughness", "rough"), // Microfacet roughness (0-1)
        METALLIC("metallic", "metal"), // Metalness map (0-1)
        AMBIENT_OCCLUSION("ao", "ambient"), // Ambient occlusion factor

        // Scientific data layers (linear values)
        ELEVATION("elevation", "elev", "height", "dem"), // Height/displacement maps
        TEMPERATURE("temperature", "temp"), // Thermal emission data
        MASK("mask", "water", "ocean"), // Binary masks (land/water)
        SPECULAR("specular", "spec"), // Legacy specular intensity

        // Specialized visualization layers
        SUBSURFACE("subsurface", "sss"), // Subsurface scattering (icy moons)
        DETAIL("detail"), // High-frequency detail overlay
        SEASONAL("seasonal"); // Time-varying textures (Mars polar caps)

        public final String[] aliases;

        TextureLayer(String... aliases) {
            this.aliases = aliases;
        }

        public static TextureLayer parse(String str) {
            if (str == null || str.isEmpty()) return VISUAL;

            String lower = str.toLowerCase();
            for (TextureLayer layer : values()) {
                for (String alias : layer.aliases) {
                    // Skip empty aliases — empty input is already handled by
                    // the guard above. Leaving them in would short-circuit
                    // every input to VISUAL (since VISUAL declares "" as an
                    // alias).
                    if (!alias.isEmpty() && alias.equals(lower)) {
                        return layer;
                    }
                }
                // Also check enum name (e.g., "ring" matches RING.name())
                if (layer.name().toLowerCase().equals(lower)) {
                    return layer;
                }
            }
            return VISUAL; // Default
        }
    }

    public enum TextureFormat {
        ASTC(".astc", true), // Hardware-accelerated on Apple/ARM
        DDS_BC7(".dds", true), // BC7 high quality compression
        DDS_BC5(".dds", true), // BC5/RGTC2 for normal maps
        DDS_BC3(".dds", true), // BC3/DXT5, universally supported
        DDS_BC1(".dds", true), // BC1/DXT1, highest compression ratio
        KTX2(".ktx2", true), // Universal format with transcoding
        PNG(".png", false), // Fallback, uncompressed
        JPEG(".jpg", false); // Lower quality fallback

        public final String extension;
        public final boolean compressed;

        TextureFormat(String ext, boolean comp) {
            this.extension = ext;
            this.compressed = comp;
        }

        public static TextureFormat fromExtension(String ext) {
            String lower = ext.toLowerCase();
            if (!lower.startsWith(".")) lower = "." + lower;

            for (TextureFormat format : values()) {
                if (format.extension.equals(lower)) {
                    return format;
                }
            }
            return PNG; // Default
        }
    }

    /**
     * Get format priority list based on platform capabilities. Explicit priority order (cascade),
     * not scores.
     */
    private static List<TextureFormat> getFormatPriorityList() {
        List<TextureFormat> priority = new ArrayList<>();

        try {
            GPUCapabilities.GPUInfo gpu = GPUCapabilities.getGPUInfo();

            // Platform-specific priority order
            if (gpu.isAppleSilicon && gpu.supportsASTC) {
                // Apple Silicon: ASTC is hardware-accelerated
                priority.add(TextureFormat.ASTC);
                priority.add(TextureFormat.DDS_BC7);
                priority.add(TextureFormat.DDS_BC3);
            } else if (gpu.supportsBC7) {
                // Modern desktop GPU: BC7 for quality
                priority.add(TextureFormat.DDS_BC7);
                priority.add(TextureFormat.DDS_BC3);
                priority.add(TextureFormat.DDS_BC5);
                priority.add(TextureFormat.DDS_BC1);
            } else {
                // Older GPU: BC3 for compatibility
                priority.add(TextureFormat.DDS_BC3);
                priority.add(TextureFormat.DDS_BC1);
            }
        } catch (Exception e) {
            // Fallback order if GPU detection fails
            priority.add(TextureFormat.DDS_BC3);
            priority.add(TextureFormat.DDS_BC1);
        }

        // PNG is always last resort
        priority.add(TextureFormat.PNG);
        priority.add(TextureFormat.JPEG);

        return priority;
    }

    /** Check if a texture format is supported on current hardware. */
    private static boolean isFormatSupported(TextureFormat format) {
        try {
            GPUCapabilities.GPUInfo gpu = GPUCapabilities.getGPUInfo();

            switch (format) {
                case ASTC:
                    return gpu.supportsASTC;
                case DDS_BC7:
                    return gpu.supportsBC7;
                case DDS_BC5:
                    // BC5/RGTC2 is supported on all modern desktop GPUs
                    return true;
                case DDS_BC3:
                    // BC3/DXT5 is universally supported on all desktop GPUs
                    return true;
                case DDS_BC1:
                    // BC1/DXT1 is universally supported on all desktop GPUs
                    return true;
                case KTX2:
                case PNG:
                case JPEG:
                    return true; // Always supported
                default:
                    return false;
            }
        } catch (Exception e) {
            // If GPU detection fails, only allow uncompressed formats
            return !format.compressed;
        }
    }

    // Regex patterns for filename parsing
    private static final List<Pattern> PATTERNS =
            Arrays.asList(
                    // With BC format: body_widthxheight_bc3/bc7
                    Pattern.compile("([a-z]+)_([0-9]+)x([0-9]+)_(bc[0-9])\\.(dds)"),
                    // Full: body_layer_resolution
                    Pattern.compile("([a-z]+)_([a-z]+)_([0-9]+)k?\\.(ktx2|astc|dds|png|jpg)"),
                    // No layer: body_resolution
                    Pattern.compile("([a-z]+)_([0-9]+)k?\\.(ktx2|astc|dds|png|jpg)"),
                    // Pixel dimensions: body_widthxheight
                    Pattern.compile("([a-z]+)_([0-9]+)x([0-9]+)\\.(ktx2|astc|dds|png|jpg)"),
                    // Compound layer names: body_layer_variant (e.g., saturn_ring_alpha)
                    Pattern.compile("([a-z]+)_([a-z]+_[a-z]+)\\.(ktx2|astc|dds|png|jpg)"),
                    // Layer without resolution: body_layer
                    Pattern.compile("([a-z]+)_([a-z]+)\\.(ktx2|astc|dds|png|jpg)"),
                    // Simple: body only
                    Pattern.compile("([a-z]+)\\.(ktx2|astc|dds|png|jpg)"));

    public TextureResolver(Path textureRoot) {
        this.textureRoot = textureRoot;
    }

    /** Initialize and scan textures. Called lazily on first use. */
    public synchronized void initialize() {
        if (initialized) return;

        long startTime = System.currentTimeMillis();
        scanTextures();
        initialized = true;

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info(String.format("Texture resolver initialized in %dms", elapsed));
    }

    /**
     * Scan and catalog all available textures. Handles both file system paths and JAR resources
     * properly.
     */
    private void scanTextures() {
        int fileCount = 0;

        try {
            // Check if we're running from a JAR or file system
            URL textureUrl = getClass().getResource("/textures");
            if (textureUrl == null) {
                LOGGER.severe("Cannot find /textures resource folder");
                return;
            }

            String protocol = textureUrl.getProtocol();
            LOGGER.info("Texture resource protocol: " + protocol);

            if ("file".equals(protocol)) {
                // Running from file system (development)
                scanFileSystemTextures(Paths.get(textureUrl.toURI()));
            } else if ("jar".equals(protocol)) {
                // Running from JAR (production)
                scanJarTextures(textureUrl);
            } else {
                LOGGER.warning("Unknown protocol for texture resources: " + protocol);
                // Fall back to classpath scanning
                scanClasspathTextures();
            }

            // Log summary
            LOGGER.info(
                    String.format(
                            "Texture scan complete: %d bodies found", textureDatabase.size()));

            logTextureSummary();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to scan textures", e);
        }
    }

    /** Scan textures from file system (development mode). */
    private void scanFileSystemTextures(Path root) throws IOException {
        Files.walk(root, 3) // Increased depth to scan subdirectories like jupiter/
                .filter(Files::isRegularFile)
                .filter(p -> isTextureFile(p.getFileName().toString()))
                .forEach(
                        path -> {
                            try {
                                LOGGER.fine("Processing texture file: " + path);
                                ResolvedTexture texture = parseTextureFile(path);
                                if (texture != null) {
                                    LOGGER.fine(
                                            "Cataloged texture: "
                                                    + texture.bodyId
                                                    + " ("
                                                    + texture.layer
                                                    + ", "
                                                    + texture.resolution
                                                    + ", "
                                                    + texture.format
                                                    + ")");
                                    textureDatabase
                                            .computeIfAbsent(texture.bodyId, BodyTextureSet::new)
                                            .addTexture(texture);
                                } else {
                                    LOGGER.fine("Could not parse texture filename: " + path);
                                }
                            } catch (Exception e) {
                                LOGGER.warning(
                                        "Error processing texture " + path + ": " + e.getMessage());
                            }
                        });
    }

    /** Scan textures from JAR (production mode). */
    private void scanJarTextures(URL jarUrl) throws IOException, URISyntaxException {
        // Parse JAR URL: jar:file:/path/to/file.jar!/textures
        String jarPath = jarUrl.toString();
        String jarFile = jarPath.substring(jarPath.indexOf(":") + 1, jarPath.indexOf("!"));

        try (JarFile jar = new JarFile(new File(new URI(jarFile)))) {
            jar.stream()
                    .filter(entry -> entry.getName().startsWith("textures/"))
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> isTextureFile(entry.getName()))
                    .forEach(
                            entry -> {
                                // Create a virtual path for consistency
                                Path virtualPath =
                                        Paths.get(entry.getName().substring("textures/".length()));
                                ResolvedTexture texture = parseTextureFile(virtualPath);
                                if (texture != null) {
                                    textureDatabase
                                            .computeIfAbsent(texture.bodyId, BodyTextureSet::new)
                                            .addTexture(texture);
                                }
                            });
        }
    }

    /** Fallback: scan using classpath resources. */
    private void scanClasspathTextures() {
        // Use ClassLoader to find resources
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("textures");

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    scanFileSystemTextures(Paths.get(resource.toURI()));
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Classpath scanning failed: " + e.getMessage());
        }
    }

    private boolean isTextureFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".dds")
                || lower.endsWith(".astc");
    }

    // Package-private for test access. Parses a single filename into a
    // ResolvedTexture without triggering a full directory scan.
    ResolvedTexture parseTextureFile(Path file) {
        try {
            String filename = file.getFileName().toString().toLowerCase();

            // Skip special cases we don't want to load as planet textures
            if (filename.contains("world.topo.bathy")) {
                return null; // Skip large Earth topography file for now
            }

            // Try each pattern
            for (Pattern pattern : PATTERNS) {
                Matcher matcher = pattern.matcher(filename);
                if (matcher.matches()) {
                    return parseMatch(matcher, file);
                }
            }

            LOGGER.fine("No pattern matched for: " + filename);
            return null;

        } catch (Exception e) {
            LOGGER.fine("Failed to parse texture file " + file + ": " + e.getMessage());
            return null;
        }
    }

    private ResolvedTexture parseMatch(Matcher matcher, Path file) throws IOException {
        String bodyId = matcher.group(1);
        TextureLayer layer = TextureLayer.VISUAL; // Default
        int resolution = 0;
        String formatStr = matcher.group(matcher.groupCount());
        TextureFormat format = TextureFormat.fromExtension(formatStr);

        // Parse based on pattern group count first
        if (matcher.groupCount() == 5) {
            // BC format: body_widthxheight_bc1/bc3/bc5/bc7.dds
            int width = Integer.parseInt(matcher.group(2));
            int height = Integer.parseInt(matcher.group(3));
            // Use width as resolution (standard practice for equirectangular textures)
            // This allows proper comparison: 7200x3600 → 7200, 3600x1800 → 3600, etc.
            resolution = width;

            String bcFormat = matcher.group(4); // "bc1", "bc3", "bc5", or "bc7"
            if ("bc1".equals(bcFormat)) {
                format = TextureFormat.DDS_BC1;
            } else if ("bc3".equals(bcFormat)) {
                format = TextureFormat.DDS_BC3;
            } else if ("bc5".equals(bcFormat)) {
                format = TextureFormat.DDS_BC5;
            } else if ("bc7".equals(bcFormat)) {
                format = TextureFormat.DDS_BC7;
            }
        } else if (matcher.groupCount() == 4) {
            // Check if it's body_widthxheight format
            String second = matcher.group(2);
            String third = matcher.group(3);
            if (Character.isDigit(second.charAt(0)) && Character.isDigit(third.charAt(0))) {
                // It's body_widthxheight.ext - use width as resolution
                resolution = Integer.parseInt(second);
            } else {
                // Full format: body_layer_resolution.ext
                layer = TextureLayer.parse(second);
                resolution = parseResolution(third);
            }
        } else if (matcher.groupCount() == 3) {
            // Either body_layer.ext or body_resolution.ext
            String middle = matcher.group(2);
            if (Character.isDigit(middle.charAt(0))) {
                // It's a resolution
                resolution = parseResolution(middle);
            } else {
                // It's a layer
                layer = TextureLayer.parse(middle);
            }
        }
        // groupCount == 2 means simple body.ext format

        // AFTER parsing, check if filename contains "_ring" to override layer detection
        // This must come AFTER pattern parsing to avoid being overwritten
        String filename = file.getFileName().toString().toLowerCase();
        if (filename.contains("_ring")) {
            layer = TextureLayer.RING;
            LOGGER.fine("Detected RING texture: " + filename + " for body: " + bodyId);
        }

        // Auto-detect resolution if not specified
        if (resolution == 0) {
            resolution = detectResolution(file);
        }

        // Get file size
        long fileSize = Files.size(file);

        return new ResolvedTexture(bodyId, layer, resolution, format, file, fileSize);
    }

    private int parseResolution(String resStr) {
        // Handle formats like "16k", "16384", "8", etc.
        resStr = resStr.toLowerCase().replace("k", "000");
        try {
            int res = Integer.parseInt(resStr);
            // If it's a small number, assume it's in thousands
            if (res < 100) res *= 1024;
            return res;
        } catch (NumberFormatException e) {
            return 2048; // Default
        }
    }

    private int detectResolution(Path file) {
        try {
            String filename = file.getFileName().toString().toLowerCase();

            // Quick detection for PNG files
            if (filename.endsWith(".png")) {
                return detectPNGResolution(file);
            }

            // Quick detection for ASTC files
            if (filename.endsWith(".astc")) {
                return detectASTCResolution(file);
            }

            // For other formats, use a default based on file size
            long size = Files.size(file);
            if (size > 50_000_000) return 8192; // >50MB probably 8K
            if (size > 10_000_000) return 4096; // >10MB probably 4K
            if (size > 2_000_000) return 2048; // >2MB probably 2K
            return 1024; // Default for small files

        } catch (Exception e) {
            LOGGER.fine("Could not detect resolution for " + file);
            return 2048; // Safe default
        }
    }

    private int detectPNGResolution(Path file) throws IOException {
        // PNG header: width is at bytes 16-19 (big endian)
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] header = new byte[24];
            raf.read(header);

            // Check PNG signature
            if (header[0] != (byte) 0x89
                    || header[1] != 'P'
                    || header[2] != 'N'
                    || header[3] != 'G') {
                return 2048; // Not a valid PNG
            }

            // Width is at offset 16-19 (big endian)
            int width =
                    ((header[16] & 0xFF) << 24)
                            | ((header[17] & 0xFF) << 16)
                            | ((header[18] & 0xFF) << 8)
                            | (header[19] & 0xFF);

            return width;
        }
    }

    private int detectASTCResolution(Path file) throws IOException {
        // ASTC header: 16 bytes
        // Magic number: 0x5CA1AB13 (little endian) at bytes 0-3
        // X size: 3 bytes at offset 7-9 (little endian)
        // Y size: 3 bytes at offset 10-12 (little endian)
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] header = new byte[16];
            raf.read(header);

            // Check ASTC magic number (little endian)
            int magic =
                    (header[0] & 0xFF)
                            | ((header[1] & 0xFF) << 8)
                            | ((header[2] & 0xFF) << 16)
                            | ((header[3] & 0xFF) << 24);

            if (magic != 0x5CA1AB13) {
                LOGGER.warning("Invalid ASTC magic number: " + Integer.toHexString(magic));
                return 2048; // Not a valid ASTC file
            }

            // Extract X dimension (width) - 3 bytes little endian at offset 7
            int width = (header[7] & 0xFF) | ((header[8] & 0xFF) << 8) | ((header[9] & 0xFF) << 16);

            return width;
        }
    }

    private void logTextureSummary() {
        if (textureDatabase.isEmpty()) {
            LOGGER.warning("No textures found!");
            return;
        }

        // Get GPU tier to determine actual loading resolution
        GPUCapabilities.GPUTier gpuTier = GPUCapabilities.detectGPUTier();

        // Determine max resolution based on GPU tier (assuming close distance for display)
        int maxLoadResolution;
        switch (gpuTier) {
            case ULTRA:
                maxLoadResolution = 16384;
                break;
            case HIGH:
                maxLoadResolution = 8192;
                break;
            case MEDIUM:
                maxLoadResolution = 4096;
                break;
            default: // LOW
                maxLoadResolution = 2048;
                break;
        }

        LOGGER.info(
                "=== Textures Available (GPU Tier: "
                        + gpuTier
                        + ", Max: "
                        + maxLoadResolution
                        + ") ===");
        textureDatabase.values().stream()
                .sorted((a, b) -> Integer.compare(b.overallPriority, a.overallPriority))
                .forEach(
                        set -> {
                            int textureCount =
                                    set.layerTextures.values().stream().mapToInt(List::size).sum();

                            // Find the best compatible texture to be loaded
                            ResolvedTexture bestTexture = null;
                            for (List<ResolvedTexture> textures : set.layerTextures.values()) {
                                for (ResolvedTexture tex : textures) {
                                    // Must be both supported format AND within resolution limits
                                    if (isFormatSupported(tex.format)
                                            && tex.resolution <= maxLoadResolution) {
                                        if (bestTexture == null
                                                || tex.resolution > bestTexture.resolution) {
                                            bestTexture = tex;
                                        }
                                    }
                                }
                            }

                            // Show what will actually be loaded
                            String resolutionInfo;
                            if (bestTexture != null) {
                                int width = bestTexture.resolution;
                                int height =
                                        bestTexture.resolution / 2; // Assuming 2:1 aspect ratio
                                resolutionInfo =
                                        String.format(
                                                "%dx%d %s",
                                                width,
                                                height,
                                                bestTexture.format.name().toLowerCase());
                            } else {
                                // Check if we have textures but they're all too large for current
                                // GPU tier
                                boolean hasLargerTextures = false;
                                for (List<ResolvedTexture> textures : set.layerTextures.values()) {
                                    for (ResolvedTexture tex : textures) {
                                        if (isFormatSupported(tex.format)
                                                && tex.resolution > maxLoadResolution) {
                                            hasLargerTextures = true;
                                            break;
                                        }
                                    }
                                }

                                if (hasLargerTextures) {
                                    // We have textures but they need downsampling
                                    resolutionInfo = "will be downsampled to " + maxLoadResolution;
                                } else {
                                    resolutionInfo = "no compatible texture found";
                                }
                            }

                            LOGGER.info(
                                    String.format(
                                            "  %s: %s, %d textures, priority %d",
                                            set.bodyId,
                                            resolutionInfo,
                                            textureCount,
                                            set.overallPriority));

                            // Debug logging for what textures exist but aren't being used
                            if (LOGGER.isLoggable(java.util.logging.Level.FINE)) {
                                for (List<ResolvedTexture> textures : set.layerTextures.values()) {
                                    for (ResolvedTexture tex : textures) {
                                        String status =
                                                isFormatSupported(tex.format)
                                                        ? (tex.resolution <= maxLoadResolution
                                                                ? "available"
                                                                : "too large")
                                                        : "unsupported format";
                                        LOGGER.fine(
                                                String.format(
                                                        "    - %dx%d %s (%s)",
                                                        tex.resolution,
                                                        tex.resolution / 2,
                                                        tex.format.name().toLowerCase(),
                                                        status));
                                    }
                                }
                            }
                        });
    }

    /** Resolve texture with fallback chain. */
    public ResolvedTexture resolve(String bodyId, TextureLayer layer, int targetResolution) {
        if (!initialized) initialize();

        BodyTextureSet bodySet = textureDatabase.get(bodyId.toLowerCase());
        if (bodySet == null) {
            LOGGER.warning(
                    "No textures found for body: "
                            + bodyId
                            + ". Database contains: "
                            + textureDatabase.keySet());
            return null;
        }

        // Try exact layer match at target resolution or below
        ResolvedTexture texture = bodySet.getBestTexture(layer, targetResolution);
        if (texture != null) return texture;

        // Fallback chain for resolutions
        for (int res = targetResolution / 2; res >= 512; res /= 2) {
            texture = bodySet.getBestTexture(layer, res);
            if (texture != null) {
                LOGGER.fine(
                        String.format(
                                "Using %dk %s for %s (requested %dk)",
                                res / 1024, layer, bodyId, targetResolution / 1024));
                return texture;
            }
        }

        // Final fallback: visual layer if we were looking for something else
        // EXCEPT for RING textures - they should never fall back to planet textures
        if (layer != TextureLayer.VISUAL && layer != TextureLayer.RING) {
            LOGGER.fine("Falling back to visual layer for " + bodyId);
            return resolve(bodyId, TextureLayer.VISUAL, targetResolution);
        }

        return null;
    }

    /** Get all bodies sorted by priority for progressive loading. */
    public List<String> getBodiesByPriority() {
        if (!initialized) initialize();

        return textureDatabase.values().stream()
                .sorted((a, b) -> Integer.compare(b.overallPriority, a.overallPriority))
                .map(set -> set.bodyId)
                .collect(Collectors.toList());
    }

    /** Get all available textures for testing/debugging. */
    public Map<String, BodyTextureSet> getAllTextures() {
        if (!initialized) initialize();
        return new HashMap<>(textureDatabase);
    }

    /** Check if a body has any textures available. */
    public boolean hasTexturesForBody(String bodyId) {
        if (!initialized) initialize();
        return textureDatabase.containsKey(bodyId.toLowerCase());
    }

    /** Get available layers for a body. */
    public Set<TextureLayer> getAvailableLayers(String bodyId) {
        if (!initialized) initialize();

        BodyTextureSet bodySet = textureDatabase.get(bodyId.toLowerCase());
        if (bodySet == null) return Collections.emptySet();

        return new HashSet<>(bodySet.layerTextures.keySet());
    }

    /**
     * Get available texture resolutions for a body. Returns unique resolution values sorted in
     * ascending order.
     */
    public List<Integer> getAvailableResolutions(String bodyId) {
        if (!initialized) initialize();

        if (bodyId == null) {
            return Collections.emptyList();
        }

        BodyTextureSet bodySet = textureDatabase.get(bodyId.toLowerCase());
        if (bodySet == null) {
            return Collections.emptyList();
        }

        // Collect unique resolutions, prioritizing compressed formats
        // DDS/KTX2 textures should be separate from PNG fallbacks
        Set<Integer> ddsResolutions = new HashSet<>();
        Set<Integer> pngResolutions = new HashSet<>();

        for (Map.Entry<TextureLayer, List<ResolvedTexture>> layerEntry :
                bodySet.layerTextures.entrySet()) {
            // Only look at VISUAL layer for primary textures
            if (layerEntry.getKey() != TextureLayer.VISUAL) continue;

            for (ResolvedTexture texture : layerEntry.getValue()) {
                // Separate by format
                switch (texture.format) {
                    case DDS_BC1:
                    case DDS_BC3:
                    case DDS_BC5:
                    case DDS_BC7:
                    case KTX2:
                        ddsResolutions.add(texture.resolution);
                        break;
                    case PNG:
                        pngResolutions.add(texture.resolution);
                        break;
                }
            }
        }

        // If we have DDS textures, use only those resolutions
        // PNG should only be used as fallback when no DDS exists
        List<Integer> sortedResolutions;
        if (!ddsResolutions.isEmpty()) {
            sortedResolutions = new ArrayList<>(ddsResolutions);
            LOGGER.fine(
                    String.format(
                            "%s: Using DDS resolutions %s (ignoring PNG fallback)",
                            bodyId, ddsResolutions));
        } else {
            sortedResolutions = new ArrayList<>(pngResolutions);
            LOGGER.fine(
                    String.format(
                            "%s: No DDS found, using PNG resolutions %s", bodyId, pngResolutions));
        }

        // Return sorted list
        Collections.sort(sortedResolutions);
        return sortedResolutions;
    }
}
