package com.jda.orrery.graphics.textures;

/**
 * A single resolution level in a resolution pyramid.
 *
 * Each level is a complete texture at a specific resolution (NOT a mipmap level), with metadata
 * needed for GPU rendering and LOD selection.
 */
public class ResolutionLevel {
    public final int resolution; // Maximum dimension (width for equirectangular)
    public final int width; // Actual texture width
    public final int height; // Actual texture height
    public final int arrayLayer; // Layer in texture array
    public final float scaleU; // Texture coordinate scale U
    public final float scaleV; // Texture coordinate scale V

    /** Create a resolution level from texture data. */
    public ResolutionLevel(int resolution, int arrayLayer, TextureData data) {
        this.resolution = resolution;
        this.width = data.width;
        this.height = data.height;
        this.arrayLayer = arrayLayer;

        // Default scale factors (may be adjusted for non-power-of-2 textures)
        this.scaleU = 1.0f;
        this.scaleV = 1.0f;
    }

    /** Create a resolution level with explicit dimensions. */
    public ResolutionLevel(int resolution, int width, int height, int arrayLayer) {
        this.resolution = resolution;
        this.width = width;
        this.height = height;
        this.arrayLayer = arrayLayer;
        this.scaleU = 1.0f;
        this.scaleV = 1.0f;
    }

    /**
     * Create a resolution level with custom texture coordinate scaling. Used when texture doesn't
     * fill the entire array layer.
     */
    public ResolutionLevel(
            int resolution, int width, int height, int arrayLayer, float scaleU, float scaleV) {
        this.resolution = resolution;
        this.width = width;
        this.height = height;
        this.arrayLayer = arrayLayer;
        this.scaleU = scaleU;
        this.scaleV = scaleV;
    }

    /** Get pixel count for memory estimation. */
    public int getPixelCount() {
        return width * height;
    }

    /**
     * Check if this level meets a required resolution. A level is suitable if its resolution is at
     * least the required value.
     */
    public boolean meetsRequirement(int requiredResolution) {
        return resolution >= requiredResolution;
    }

    /** Calculate texel density (texels per unit) for quality metrics. */
    public float getTexelDensity() {
        // Assuming equirectangular projection: width covers 360 degrees
        return width / 360.0f; // Texels per degree
    }

    @Override
    public String toString() {
        return String.format(
                "ResolutionLevel[%dx%d, layer=%d, scale=(%.2f,%.2f)]",
                width, height, arrayLayer, scaleU, scaleV);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResolutionLevel that = (ResolutionLevel) o;
        return resolution == that.resolution
                && width == that.width
                && height == that.height
                && arrayLayer == that.arrayLayer;
    }

    @Override
    public int hashCode() {
        int result = resolution;
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + arrayLayer;
        return result;
    }
}
