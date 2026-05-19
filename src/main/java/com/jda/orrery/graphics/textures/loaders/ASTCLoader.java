package com.jda.orrery.graphics.textures.loaders;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.KHRTextureCompressionASTCLDR.*;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.textures.TextureData;
import com.jda.orrery.graphics.textures.TextureResolver;
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
 * ASTC (Adaptive Scalable Texture Compression) loader. Supports ASTC compressed textures which are
 * hardware-accelerated on Apple Silicon and ARM platforms.
 *
 * ASTC provides excellent quality with flexible block sizes from 4x4 (highest quality) to 12x12
 * (highest compression).
 */
public class ASTCLoader implements TextureFormatLoader {
    private static final Logger LOGGER = Logging.logger(ASTCLoader.class);

    // ASTC magic number (little endian)
    private static final int ASTC_MAGIC = 0x5CA1AB13;

    // ASTC header is always 16 bytes
    private static final int HEADER_SIZE = 16;

    @Override
    public boolean canLoad(TextureResolver.TextureFormat format) {
        return format == TextureResolver.TextureFormat.ASTC;
    }

    @Override
    public boolean canLoad(String extension) {
        return extension.equalsIgnoreCase(".astc");
    }

    @Override
    public String getName() {
        return "ASTC Loader";
    }

    @Override
    public CompletableFuture<TextureData> loadAsync(
            Path file, TextureResolver.ResolvedTexture metadata) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return loadSync(file, metadata);
                    } catch (Exception e) {
                        throw new CompletionException("Failed to load ASTC: " + file, e);
                    }
                });
    }

    @Override
    public TextureData loadSync(Path file, TextureResolver.ResolvedTexture metadata) {

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            // Read ASTC header (16 bytes)
            byte[] headerBytes = new byte[HEADER_SIZE];
            raf.read(headerBytes);
            ByteBuffer header = ByteBuffer.wrap(headerBytes);
            header.order(ByteOrder.LITTLE_ENDIAN);

            // Validate magic number
            int magic = header.getInt(0);
            if (magic != ASTC_MAGIC) {
                LOGGER.severe("Invalid ASTC magic number: " + Integer.toHexString(magic));
                return null;
            }

            // Extract block dimensions (1 byte each at offsets 4, 5, 6)
            int blockX = header.get(4) & 0xFF;
            int blockY = header.get(5) & 0xFF;
            int blockZ = header.get(6) & 0xFF;

            // Extract image dimensions (3 bytes each, little endian)
            int width =
                    (header.get(7) & 0xFF)
                            | ((header.get(8) & 0xFF) << 8)
                            | ((header.get(9) & 0xFF) << 16);

            int height =
                    (header.get(10) & 0xFF)
                            | ((header.get(11) & 0xFF) << 8)
                            | ((header.get(12) & 0xFF) << 16);

            int depth =
                    (header.get(13) & 0xFF)
                            | ((header.get(14) & 0xFF) << 8)
                            | ((header.get(15) & 0xFF) << 16);

            // We only support 2D textures for now
            if (blockZ != 1 || depth != 1) {
                LOGGER.warning(
                        "3D ASTC textures not supported. Block: "
                                + blockX
                                + "x"
                                + blockY
                                + "x"
                                + blockZ);
                return null;
            }

            LOGGER.fine(
                    String.format(
                            "ASTC texture: %dx%d, block size %dx%d",
                            width, height, blockX, blockY));

            // Calculate compressed data size
            int blocksWide = (width + blockX - 1) / blockX;
            int blocksHigh = (height + blockY - 1) / blockY;
            int blockCount = blocksWide * blocksHigh;
            int dataSize = blockCount * 16; // ASTC always uses 16 bytes per block

            // Read compressed texture data using LWJGL's memory management
            // This ensures proper memory tracking for OpenGL operations
            ByteBuffer textureData = MemoryUtil.memAlloc(dataSize);
            byte[] buffer = new byte[Math.min(8192, dataSize)];
            int bytesRead;
            while ((bytesRead =
                            raf.read(buffer, 0, Math.min(buffer.length, textureData.remaining())))
                    > 0) {
                textureData.put(buffer, 0, bytesRead);
            }
            textureData.flip();

            // Determine OpenGL format constant based on block size
            int glFormat = getASTCFormat(blockX, blockY);
            if (glFormat == 0) {
                LOGGER.severe("Unsupported ASTC block size: " + blockX + "x" + blockY);
                return null;
            }

            return new TextureData(
                    textureData,
                    width,
                    height,
                    glFormat,
                    true, // Compressed
                    false, // ASTC files don't include mipmaps
                    metadata.bodyId,
                    metadata.layer);

        } catch (IOException e) {
            LOGGER.severe("Failed to load ASTC " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Get OpenGL format constant for ASTC block size. Returns 0 if block size is not supported. */
    private int getASTCFormat(int blockX, int blockY) {
        // Map block sizes to OpenGL constants
        // Using sRGB formats for better color accuracy
        if (blockX == 4 && blockY == 4) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR;
        } else if (blockX == 5 && blockY == 4) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR;
        } else if (blockX == 5 && blockY == 5) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR;
        } else if (blockX == 6 && blockY == 5) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR;
        } else if (blockX == 6 && blockY == 6) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR;
        } else if (blockX == 8 && blockY == 5) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR;
        } else if (blockX == 8 && blockY == 6) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR;
        } else if (blockX == 8 && blockY == 8) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR;
        } else if (blockX == 10 && blockY == 5) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR;
        } else if (blockX == 10 && blockY == 6) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR;
        } else if (blockX == 10 && blockY == 8) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR;
        } else if (blockX == 10 && blockY == 10) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR;
        } else if (blockX == 12 && blockY == 10) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR;
        } else if (blockX == 12 && blockY == 12) {
            return GL_COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR;
        }

        // Unsupported block size
        return 0;
    }

    /** Get compression ratio for ASTC block size. Useful for memory estimation. */
    public static float getCompressionRatio(int blockX, int blockY) {
        // ASTC uses 16 bytes per block
        // Uncompressed RGBA8 uses 4 bytes per pixel
        int pixelsPerBlock = blockX * blockY;
        float uncompressedSize = pixelsPerBlock * 4.0f;
        float compressedSize = 16.0f;
        return uncompressedSize / compressedSize;
    }
}
