package com.jda.orrery.graphics.api;

import com.jda.orrery.graphics.core.DrawContext;
import org.joml.Matrix4f;

/**
 * Camera/view abstraction consumed by the render loop. Implementations write into
 * caller-supplied matrices (output-parameter pattern) so the render loop avoids heap allocation.
 */
public interface View {
    /**
     * Apply this view's state to the draw context. Called once per frame before rendering.
     *
     * @param dc Draw context to apply view state to
     */
    void apply(DrawContext dc);

    /**
     * Fill pre-allocated matrix with view transform.
     *
     * @param output Pre-allocated Matrix4f to fill (modified in place)
     */
    void fillViewMatrix(Matrix4f output);

    /**
     * Fill pre-allocated matrix with projection transform.
     *
     * @param output Pre-allocated Matrix4f to fill (modified in place)
     */
    void fillProjectionMatrix(Matrix4f output);

    /**
     * Fill pre-allocated matrix with view transform in double precision. Calculations stay in
     * double; the float variants exist only for GPU upload.
     *
     * @param output Pre-allocated Matrix4d to fill (modified in place)
     */
    void fillViewMatrixDouble(org.joml.Matrix4d output);

    /**
     * Fill pre-allocated matrix with projection transform in double precision.
     *
     * @param output Pre-allocated Matrix4d to fill (modified in place)
     */
    void fillProjectionMatrixDouble(org.joml.Matrix4d output);

    /**
     * Get eye X coordinate in world space.
     *
     * @return Eye X coordinate
     */
    double getEyeX();

    /**
     * Get eye Y coordinate in world space.
     *
     * @return Eye Y coordinate
     */
    double getEyeY();

    /**
     * Get eye Z coordinate in world space.
     *
     * @return Eye Z coordinate
     */
    double getEyeZ();

    /**
     * Set the viewport dimensions.
     *
     * @param x Viewport X origin
     * @param y Viewport Y origin
     * @param width Viewport width in pixels
     * @param height Viewport height in pixels
     */
    void setViewport(int x, int y, int width, int height);

    /**
     * Get the field of view in degrees. Used for LOD calculations and screen coverage metrics.
     *
     * @return Field of view in degrees
     */
    double getFieldOfView();

    /**
     * Get the camera's world position for camera-relative rendering.
     *
     * Camera-relative rendering keeps object positions small (offsets from the camera, not
     * absolute world coordinates), avoiding float precision loss at astronomical scales.
     *
     * @return Camera position in world coordinates (visual units), or null if not tracked
     */
    com.jda.orrery.core.math.Vec3d getCameraWorldPosition();
}
