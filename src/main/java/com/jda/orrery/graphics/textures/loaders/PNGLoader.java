package com.jda.orrery.graphics.textures.loaders;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.textures.TextureData;
import com.jda.orrery.graphics.textures.TextureResolver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** PNG texture loader using STB. Handles standard PNG files and 16-bit elevation data. */
public class PNGLoader implements TextureFormatLoader {
    private static final Logger LOGGER = Logging.logger(PNGLoader.class);

    @Override
    public boolean canLoad(TextureResolver.TextureFormat format) {
        return format == TextureResolver.TextureFormat.PNG;
    }

    @Override
    public boolean canLoad(String extension) {
        return extension.equalsIgnoreCase(".png");
    }

    @Override
    public String getName() {
        return "PNG Loader (STB)";
    }

    @Override
    public CompletableFuture<TextureData> loadAsync(
            Path file, TextureResolver.ResolvedTexture metadata) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return loadSync(file, metadata);
                    } catch (Exception e) {
                        throw new CompletionException("Failed to load PNG: " + file, e);
                    }
                });
    }

    @Override
    public TextureData loadSync(Path file, TextureResolver.ResolvedTexture metadata) {
        try {
            // Read file into byte array
            byte[] fileData = Files.readAllBytes(file);

            // Allocate direct ByteBuffer for STB
            ByteBuffer buffer = MemoryUtil.memAlloc(fileData.length);
            buffer.put(fileData);
            buffer.flip();

            // Decode image with STB
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);

                // Check if this is elevation data (16-bit)
                boolean is16Bit =
                        metadata.layer == TextureResolver.TextureLayer.ELEVATION
                                || file.toString().contains("elevation")
                                || file.toString().contains("height")
                                || file.toString().contains("dem");

                TextureData textureData;

                if (is16Bit) {
                    // Load as 16-bit single channel for elevation
                    ShortBuffer imageData16 =
                            STBImage.stbi_load_16_from_memory(buffer, width, height, channels, 1);

                    // Free the file buffer
                    MemoryUtil.memFree(buffer);

                    if (imageData16 == null) {
                        String error = STBImage.stbi_failure_reason();
                        throw new IOException("STB failed to load 16-bit PNG: " + error);
                    }

                    LOGGER.fine(
                            String.format(
                                    "Loaded 16-bit elevation: %s (%dx%d)",
                                    file.getFileName(), width.get(0), height.get(0)));

                    // Create 16-bit texture data.
                    // The TextureData now owns the imageData16 buffer and is responsible for
                    // freeing it.
                    textureData =
                            new TextureData(
                                    imageData16,
                                    width.get(0),
                                    height.get(0),
                                    GL_R16, // 16-bit red channel for elevation
                                    false, // Not compressed
                                    false, // No mipmaps (will generate later)
                                    metadata.bodyId,
                                    metadata.layer);

                    // DO NOT free imageData16 here - TextureData owns it now
                } else {
                    // Load as standard RGBA 8-bit
                    ByteBuffer imageData =
                            STBImage.stbi_load_from_memory(buffer, width, height, channels, 4);

                    // Free the file buffer
                    MemoryUtil.memFree(buffer);

                    if (imageData == null) {
                        String error = STBImage.stbi_failure_reason();
                        throw new IOException("STB failed to load PNG: " + error);
                    }

                    LOGGER.fine(
                            String.format(
                                    "Loaded PNG: %s (%dx%d, %d channels)",
                                    file.getFileName(),
                                    width.get(0),
                                    height.get(0),
                                    channels.get(0)));

                    // Check if downsampling is needed
                    int maxSize = metadata.maxTextureSize;
                    int originalWidth = width.get(0);
                    int originalHeight = height.get(0);

                    if (maxSize > 0 && (originalWidth > maxSize || originalHeight > maxSize)) {
                        // Downsample the texture to fit GPU capabilities
                        LOGGER.info(
                                String.format(
                                        "Downsampling %s from %dx%d to fit max size %d",
                                        file.getFileName(),
                                        originalWidth,
                                        originalHeight,
                                        maxSize));

                        // downsample8Bit returns a MemoryUtil allocated buffer and frees the
                        // original
                        ByteBuffer downsampledData =
                                downsample8Bit(imageData, originalWidth, originalHeight, maxSize);

                        // Calculate new dimensions
                        float scale =
                                Math.min(
                                        (float) maxSize / originalWidth,
                                        (float) maxSize / originalHeight);
                        int newWidth = (int) (originalWidth * scale);
                        int newHeight = (int) (originalHeight * scale);

                        // Create texture data with downsampled dimensions
                        // Mark as downsampled so cache knows to use MemoryUtil.memFree
                        textureData =
                                new TextureData(
                                        downsampledData,
                                        newWidth,
                                        newHeight,
                                        GL_RGBA8,
                                        false, // Not compressed
                                        false, // No mipmaps (will generate later)
                                        metadata.bodyId,
                                        metadata.layer,
                                        true // isDownsampled flag - uses MemoryUtil allocation
                                        );

                        LOGGER.info(
                                String.format(
                                        "Downsampled %s to %dx%d",
                                        file.getFileName(), newWidth, newHeight));
                    } else {
                        // No downsampling needed - buffer from STBImage
                        textureData =
                                new TextureData(
                                        imageData,
                                        originalWidth,
                                        originalHeight,
                                        GL_RGBA8,
                                        false, // Not compressed
                                        false, // No mipmaps (will generate later)
                                        metadata.bodyId,
                                        metadata.layer,
                                        false // Not downsampled - uses STBImage allocation
                                        );
                    }

                    // DO NOT free imageData here - TextureData owns it now
                }

                return textureData;
            }

        } catch (IOException e) {
            LOGGER.severe("Failed to load PNG " + file + ": " + e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.severe("Unexpected error loading PNG " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Downsample an 8-bit RGBA texture to fit within maxSize. */
    private ByteBuffer downsample8Bit(
            ByteBuffer input, int inputWidth, int inputHeight, int maxSize) {
        // Calculate new dimensions maintaining aspect ratio
        float scale = Math.min((float) maxSize / inputWidth, (float) maxSize / inputHeight);
        int outputWidth = (int) (inputWidth * scale);
        int outputHeight = (int) (inputHeight * scale);

        // Allocate output buffer
        ByteBuffer output = MemoryUtil.memAlloc(outputWidth * outputHeight * 4);

        // Perform the resize using high-quality Mitchell filter
        boolean success =
                STBImageResize.stbir_resize_uint8(
                        input,
                        inputWidth,
                        inputHeight,
                        0,
                        output,
                        outputWidth,
                        outputHeight,
                        0,
                        4 // RGBA channels
                        );

        if (!success) {
            MemoryUtil.memFree(output);
            LOGGER.severe("Failed to downsample texture");
            return input; // Return original on failure
        }

        // Free the original buffer and return the downsampled one
        STBImage.stbi_image_free(input);
        return output;
    }
}
