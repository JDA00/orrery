package com.jda.orrery.core.frames;

import org.joml.Matrix3d;

/**
 * Provider of reference frame transformation matrices. The interface is pluggable so additional
 * kernels can be wired later; {@link BuiltInFrameKernel} is the only implementation today.
 */
public interface FrameKernel {

    /**
     * Get the 3x3 rotation matrix to transform from one frame to another.
     *
     * The returned matrix M transforms a position vector p as: p' = M * p
     *
     * @param fromFrame Source reference frame (e.g., "J2000")
     * @param toFrame Target reference frame (e.g., "ECLIPJ2000")
     * @param et Ephemeris time in seconds since J2000 (for time-dependent transformations like
     *     precession)
     * @return 3x3 rotation matrix, or null if transformation not available
     */
    Matrix3d getTransform(String fromFrame, String toFrame, double et);

    /**
     * Check if this kernel can provide a transformation between two frames.
     *
     * @param fromFrame Source reference frame
     * @param toFrame Target reference frame
     * @return true if this kernel can perform the transformation
     */
    boolean canTransform(String fromFrame, String toFrame);

    /** Human-readable name for this kernel (used in logs). */
    String getName();

    /**
     * Check if transformations are time-dependent.
     *
     * If false, the 'et' parameter in getTransform() is ignored and transformations can be
     * cached indefinitely.
     *
     * @param fromFrame Source reference frame
     * @param toFrame Target reference frame
     * @return true if the transformation varies with time
     */
    default boolean isTimeDependent(String fromFrame, String toFrame) {
        // Most transformations are fixed (e.g., J2000 to ECLIPJ2000)
        // Only body-fixed frames and precession are time-dependent
        return fromFrame.startsWith("IAU_")
                || toFrame.startsWith("IAU_")
                || fromFrame.equals("ECLIPDATE")
                || toFrame.equals("ECLIPDATE");
    }
}
