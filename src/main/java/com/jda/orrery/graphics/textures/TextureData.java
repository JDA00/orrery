package com.jda.orrery.graphics.textures;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/** Immutable container for loaded texture pixel data. */
public class TextureData {
    public final ByteBuffer data;
    public final ShortBuffer data16; // For 16-bit textures
    public final int width;
    public final int height;
    public final int format; // OpenGL format constant
    public final boolean compressed;
    public final boolean hasMipmaps;
    public final boolean is16Bit;
    public final boolean isDownsampled; // True if buffer was allocated with MemoryUtil
    public final String bodyId;
    public final TextureResolver.TextureLayer layer;
    public final int baseMipSize; // Size of first mipmap level (for compressed textures)
    public final int mipMapCount; // Number of mipmap levels

    // Constructor for 8-bit data
    public TextureData(
            ByteBuffer data,
            int width,
            int height,
            int format,
            boolean compressed,
            boolean hasMipmaps,
            String bodyId,
            TextureResolver.TextureLayer layer) {
        this(data, width, height, format, compressed, hasMipmaps, bodyId, layer, false, 0, 1);
    }

    // Constructor for 8-bit data with downsample flag
    public TextureData(
            ByteBuffer data,
            int width,
            int height,
            int format,
            boolean compressed,
            boolean hasMipmaps,
            String bodyId,
            TextureResolver.TextureLayer layer,
            boolean isDownsampled) {
        this(
                data,
                width,
                height,
                format,
                compressed,
                hasMipmaps,
                bodyId,
                layer,
                isDownsampled,
                0,
                1);
    }

    // Full constructor with mipmap metadata
    public TextureData(
            ByteBuffer data,
            int width,
            int height,
            int format,
            boolean compressed,
            boolean hasMipmaps,
            String bodyId,
            TextureResolver.TextureLayer layer,
            boolean isDownsampled,
            int baseMipSize,
            int mipMapCount) {
        this.data = data;
        this.data16 = null;
        this.width = width;
        this.height = height;
        this.format = format;
        this.compressed = compressed;
        this.hasMipmaps = hasMipmaps;
        this.is16Bit = false;
        this.isDownsampled = isDownsampled;
        this.bodyId = bodyId;
        this.layer = layer;
        this.baseMipSize = baseMipSize;
        this.mipMapCount = mipMapCount;
    }

    // Constructor for 16-bit data
    public TextureData(
            ShortBuffer data16,
            int width,
            int height,
            int format,
            boolean compressed,
            boolean hasMipmaps,
            String bodyId,
            TextureResolver.TextureLayer layer) {
        this.data = null;
        this.data16 = data16;
        this.width = width;
        this.height = height;
        this.format = format;
        this.compressed = compressed;
        this.hasMipmaps = hasMipmaps;
        this.is16Bit = true;
        this.isDownsampled = false; // 16-bit not downsampled
        this.bodyId = bodyId;
        this.layer = layer;
        this.baseMipSize = 0;
        this.mipMapCount = 1;
    }

    /** Calculate the size of texture data in bytes. */
    public long getSize() {
        long size;
        if (is16Bit && data16 != null) {
            size = data16.remaining() * 2; // 2 bytes per short
        } else if (data != null) {
            size = data.remaining();
        } else {
            size = 0;
        }
        // Add mipmap overhead
        if (hasMipmaps) {
            size = (size * 4) / 3; // Approximately 1.33x for full mipmap chain
        }
        return size;
    }
}
