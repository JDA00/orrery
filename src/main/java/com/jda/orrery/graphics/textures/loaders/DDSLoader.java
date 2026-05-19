package com.jda.orrery.graphics.textures.loaders;

import static org.lwjgl.opengl.ARBTextureCompressionBPTC.*;
import static org.lwjgl.opengl.EXTTextureCompressionS3TC.*;
import static org.lwjgl.opengl.EXTTextureSRGB.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.textures.TextureData;
import com.jda.orrery.graphics.textures.TextureResolver;
import com.jda.orrery.graphics.textures.TextureResolver.TextureLayer;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.lwjgl.system.MemoryUtil;

/**
 * DDS (DirectDraw Surface) texture loader.
 *
 * Currently implemented: - BC1/DXT1: RGB with optional 1-bit alpha (6:1 or 8:1 compression) -
 * BC2/DXT3: RGBA with explicit 4-bit alpha (4:1 compression) - BC3/DXT5: RGBA with interpolated
 * alpha (4:1 compression)
 *
 * Planned (not yet implemented): - BC4/RGTC1: Single channel (2:1 compression) -
 * height/roughness maps - BC5/RGTC2: Two channel (2:1 compression) - normal maps - BC6H: HDR RGB
 * (6:1 compression) - for emissive/Sun textures - BC7: High quality RGBA (3:1 compression) —
 * requires OpenGL 4.2+ (currently targeting 4.1 for macOS compatibility)
 */
public class DDSLoader implements TextureFormatLoader {
    private static final Logger LOGGER = Logging.logger(DDSLoader.class);

    // DDS magic number "DDS " (0x20534444)
    private static final int DDS_MAGIC = 0x20534444;

    // DDS header flags
    private static final int DDSD_CAPS = 0x1;
    private static final int DDSD_HEIGHT = 0x2;
    private static final int DDSD_WIDTH = 0x4;
    private static final int DDSD_PITCH = 0x8;
    private static final int DDSD_PIXELFORMAT = 0x1000;
    private static final int DDSD_MIPMAPCOUNT = 0x20000;
    private static final int DDSD_LINEARSIZE = 0x80000;

    // Pixel format flags
    private static final int DDPF_ALPHAPIXELS = 0x1;
    private static final int DDPF_FOURCC = 0x4;
    private static final int DDPF_RGB = 0x40;

    // FourCC codes for compressed formats
    private static final int FOURCC_DXT1 = 0x31545844; // "DXT1"
    private static final int FOURCC_DXT3 = 0x33545844; // "DXT3"
    private static final int FOURCC_DXT5 = 0x35545844; // "DXT5"
    private static final int FOURCC_BC4U = 0x55344342; // "BC4U"
    private static final int FOURCC_BC4S = 0x53344342; // "BC4S"
    private static final int FOURCC_BC5U = 0x55354342; // "BC5U"
    private static final int FOURCC_BC5S = 0x53354342; // "BC5S"
    private static final int FOURCC_DX10 = 0x30315844; // "DX10" - for BC6/BC7

    // DX10 header format enums for BC6/BC7
    private static final int DXGI_FORMAT_BC6H_UF16 = 95;
    private static final int DXGI_FORMAT_BC6H_SF16 = 96;
    private static final int DXGI_FORMAT_BC7_UNORM = 98;
    private static final int DXGI_FORMAT_BC7_UNORM_SRGB = 99;

    @Override
    public boolean canLoad(TextureResolver.TextureFormat format) {
        // Support all DDS BC formats
        return format == TextureResolver.TextureFormat.DDS_BC1
                || format == TextureResolver.TextureFormat.DDS_BC3
                || format == TextureResolver.TextureFormat.DDS_BC5
                || format == TextureResolver.TextureFormat.DDS_BC7;
    }

    @Override
    public boolean canLoad(String extension) {
        return extension.equalsIgnoreCase(".dds");
    }

    @Override
    public String getName() {
        return "DDS Loader (BC1-BC7)";
    }

    @Override
    public CompletableFuture<TextureData> loadAsync(
            Path file, TextureResolver.ResolvedTexture metadata) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return loadSync(file, metadata);
                    } catch (Exception e) {
                        throw new CompletionException("Failed to load DDS: " + file, e);
                    }
                });
    }

    @Override
    public TextureData loadSync(Path file, TextureResolver.ResolvedTexture metadata) {

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            // Read DDS header (128 bytes total)
            byte[] headerBytes = new byte[128];
            raf.read(headerBytes);
            ByteBuffer header = ByteBuffer.wrap(headerBytes);
            header.order(ByteOrder.LITTLE_ENDIAN);

            // Validate magic number
            int magic = header.getInt(0);
            if (magic != DDS_MAGIC) {
                LOGGER.severe("Invalid DDS magic number: " + Integer.toHexString(magic));
                return null;
            }

            // Read main header fields
            int headerSize = header.getInt(4);
            int flags = header.getInt(8);
            int height = header.getInt(12);
            int width = header.getInt(16);
            int pitchOrLinearSize = header.getInt(20);
            int depth = header.getInt(24);
            int mipMapCount = header.getInt(28);

            // Read pixel format (at offset 76)
            header.position(76);
            int pfSize = header.getInt();
            int pfFlags = header.getInt();
            int fourCC = header.getInt();

            LOGGER.fine(
                    String.format(
                            "DDS: %dx%d, MipMaps: %d, FourCC: %s",
                            width, height, mipMapCount, fourCCToString(fourCC)));

            // Determine format and calculate data size
            DDSFormat format = detectFormat(fourCC, pfFlags, header, metadata);
            if (format == null) {
                LOGGER.severe("Unsupported DDS format: " + fourCCToString(fourCC));
                return null;
            }

            // Check for DX10 extended header (for BC6/BC7)
            if (fourCC == FOURCC_DX10) {
                byte[] dx10HeaderBytes = new byte[20];
                raf.read(dx10HeaderBytes);
                ByteBuffer dx10Header = ByteBuffer.wrap(dx10HeaderBytes);
                dx10Header.order(ByteOrder.LITTLE_ENDIAN);

                int dxgiFormat = dx10Header.getInt(0);
                format = detectDX10Format(dxgiFormat);

                if (format == null) {
                    LOGGER.severe("Unsupported DX10 format: " + dxgiFormat);
                    return null;
                }
            }

            // Calculate compressed data size
            int blockSize = format.blockSize;
            int blocksWide = (width + 3) / 4;
            int blocksHigh = (height + 3) / 4;
            int baseMipSize = blocksWide * blocksHigh * blockSize; // Size of mip level 0 only
            int dataSize = baseMipSize; // Start with base mip size

            // Include mipmaps if present
            boolean hasMipmaps = mipMapCount > 1;
            if (hasMipmaps) {
                int totalSize = dataSize;
                int mipWidth = width / 2;
                int mipHeight = height / 2;

                for (int i = 1; i < mipMapCount && (mipWidth > 0 || mipHeight > 0); i++) {
                    mipWidth = Math.max(1, mipWidth);
                    mipHeight = Math.max(1, mipHeight);

                    int mipBlocksWide = (mipWidth + 3) / 4;
                    int mipBlocksHigh = (mipHeight + 3) / 4;
                    totalSize += Math.max(blockSize, mipBlocksWide * mipBlocksHigh * blockSize);

                    mipWidth /= 2;
                    mipHeight /= 2;
                }
                dataSize = totalSize;
            }

            // Read compressed texture data using LWJGL's memory management
            // The dataSize is exact based on BC3 block calculations
            // File should have exactly this much data after headers
            ByteBuffer textureData = MemoryUtil.memAlloc(dataSize);

            // Read exactly dataSize bytes from current position
            byte[] buffer = new byte[Math.min(8192, dataSize)];
            int totalRead = 0;
            int bytesRead;

            while (totalRead < dataSize
                    && (bytesRead =
                                    raf.read(
                                            buffer,
                                            0,
                                            Math.min(buffer.length, dataSize - totalRead)))
                            > 0) {
                textureData.put(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            // Verify we read the expected amount
            if (totalRead != dataSize) {
                LOGGER.warning(
                        String.format(
                                "DDS %s: Expected %d bytes, read %d bytes",
                                file.getFileName(), dataSize, totalRead));
            }

            textureData.flip();

            LOGGER.info(
                    String.format(
                            "Loaded DDS %s: %dx%d, %s, %s",
                            file.getFileName(),
                            width,
                            height,
                            format.name,
                            hasMipmaps ? "with mipmaps" : "no mipmaps"));

            // Keep full mipmap data for proper texture filtering
            if (hasMipmaps) {
                LOGGER.info(
                        String.format(
                                "DDS %s: Keeping full mipmap chain (%d levels, %d bytes total)",
                                file.getFileName(), mipMapCount, dataSize));
            }

            return new TextureData(
                    textureData,
                    width,
                    height,
                    format.glFormat,
                    true, // Compressed
                    hasMipmaps, // Report actual mipmap status
                    metadata.bodyId,
                    metadata.layer,
                    false, // Not downsampled
                    baseMipSize, // Store base mip size for first level
                    mipMapCount // Store mipmap count for upload
                    );

        } catch (IOException e) {
            LOGGER.severe("Failed to load DDS " + file + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Detect DDS format from FourCC code. Uses layer type metadata to determine sRGB vs linear for
     * ambiguous formats.
     */
    private DDSFormat detectFormat(
            int fourCC, int pfFlags, ByteBuffer header, TextureResolver.ResolvedTexture metadata) {
        // Check for compressed formats by FourCC
        switch (fourCC) {
            case FOURCC_DXT1:
                // BC1/DXT1: Choose format based on texture layer type
                if (shouldUseSRGB(metadata.layer)) {
                    return new DDSFormat("BC1/DXT1 (sRGB)", GL_COMPRESSED_SRGB_S3TC_DXT1_EXT, 8);
                } else {
                    return new DDSFormat("BC1/DXT1 (linear)", GL_COMPRESSED_RGB_S3TC_DXT1_EXT, 8);
                }
            case FOURCC_DXT3:
                // BC2/DXT3: Always has alpha, use layer type for color space
                if (shouldUseSRGB(metadata.layer)) {
                    return new DDSFormat(
                            "BC2/DXT3 (sRGB)", GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT, 16);
                } else {
                    return new DDSFormat("BC2/DXT3 (linear)", GL_COMPRESSED_RGBA_S3TC_DXT3_EXT, 16);
                }
            case FOURCC_DXT5:
                // BC3/DXT5: Most common for diffuse textures with alpha
                if (shouldUseSRGB(metadata.layer)) {
                    return new DDSFormat(
                            "BC3/DXT5 (sRGB)", GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT, 16);
                } else {
                    return new DDSFormat("BC3/DXT5 (linear)", GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, 16);
                }
            case FOURCC_BC4U:
                return new DDSFormat("BC4", GL_COMPRESSED_RED_RGTC1, 8);
            case FOURCC_BC4S:
                return new DDSFormat("BC4S", GL_COMPRESSED_SIGNED_RED_RGTC1, 8);
            case FOURCC_BC5U:
                return new DDSFormat("BC5", GL_COMPRESSED_RG_RGTC2, 16);
            case FOURCC_BC5S:
                return new DDSFormat("BC5S", GL_COMPRESSED_SIGNED_RG_RGTC2, 16);
            case FOURCC_DX10:
                // DX10 header follows, will be handled separately

                return null;
        }

        // Legacy ATI formats (BC4/BC5 alternatives)
        if (fourCC == 0x31495441) { // "ATI1"
            return new DDSFormat("BC4/ATI1", GL_COMPRESSED_RED_RGTC1, 8);
        }
        if (fourCC == 0x32495441) { // "ATI2"
            return new DDSFormat("BC5/ATI2", GL_COMPRESSED_RG_RGTC2, 16);
        }

        return null;
    }

    /** Detect DX10 extended formats (BC6/BC7). */
    private DDSFormat detectDX10Format(int dxgiFormat) {
        switch (dxgiFormat) {
            case DXGI_FORMAT_BC6H_UF16:
                return new DDSFormat("BC6H", GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_ARB, 16);
            case DXGI_FORMAT_BC6H_SF16:
                return new DDSFormat("BC6H_SIGNED", GL_COMPRESSED_RGB_BPTC_SIGNED_FLOAT_ARB, 16);
            case DXGI_FORMAT_BC7_UNORM:
                return new DDSFormat("BC7", GL_COMPRESSED_RGBA_BPTC_UNORM_ARB, 16);
            case DXGI_FORMAT_BC7_UNORM_SRGB:
                return new DDSFormat("BC7_SRGB", GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM_ARB, 16);
            default:
                return null;
        }
    }

    /**
     * Determine if texture should use sRGB format based on layer type. Visual/color data uses sRGB
     * for correct gamma; scientific/measurement data stays linear for accuracy.
     */
    private boolean shouldUseSRGB(TextureLayer layer) {
        if (layer == null) {
            // No layer metadata, default to sRGB for safety
            LOGGER.fine("No layer metadata, defaulting to sRGB");
            return true;
        }

        switch (layer) {
            // Color/visual data uses sRGB for correct gamma.
            case VISUAL: // Diffuse/albedo textures
            case EMISSION: // Self-illumination (lava, auroras)
            case NIGHT: // Night lights (cities, volcanic)
            case CLOUDS: // Atmospheric visual layers
            case RING: // Saturn rings visual texture
                return true;

            // Scientific/measurement data must stay linear.
            case NORMAL: // Surface normals (direction vectors)
            case ROUGHNESS: // PBR microfacet roughness (0-1)
            case METALLIC: // PBR metalness parameter (0-1)
            case AMBIENT_OCCLUSION: // Occlusion factor (0-1)
            case ELEVATION: // Height measurements in meters
            case TEMPERATURE: // Thermal emission values
            case MASK: // Binary masks (land/water)
            case SPECULAR: // Legacy specular intensity
            case SUBSURFACE: // Subsurface scattering parameters
            case DETAIL: // Detail overlay maps
            case SEASONAL: // Time-varying data (often scientific)
                return false;

            default:
                // Fail-safe default for unknown layers.
                LOGGER.warning("Unknown texture layer type: " + layer + ", defaulting to sRGB");
                return true;
        }
    }

    /** Convert FourCC to readable string. */
    private String fourCCToString(int fourCC) {
        if (fourCC == 0) return "NONE";

        char[] chars = new char[4];
        chars[0] = (char) (fourCC & 0xFF);
        chars[1] = (char) ((fourCC >> 8) & 0xFF);
        chars[2] = (char) ((fourCC >> 16) & 0xFF);
        chars[3] = (char) ((fourCC >> 24) & 0xFF);
        return new String(chars);
    }

    /** Internal format descriptor. */
    private static class DDSFormat {
        final String name;
        final int glFormat;
        final int blockSize; // Bytes per 4x4 block

        DDSFormat(String name, int glFormat, int blockSize) {
            this.name = name;
            this.glFormat = glFormat;
            this.blockSize = blockSize;
        }
    }
}
