package com.jda.orrery.core.frames;

import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3d;

/**
 * Built-in frame kernel providing IAU standard transformations.
 *
 * This implements the most common reference frame transformations using IAU (International
 * Astronomical Union) standard values.
 *
 * For now, only time-independent transformations are implemented. Future versions can add
 * precession, nutation, and body rotation models.
 */
public class BuiltInFrameKernel implements FrameKernel {

    // IAU standard constants
    private static final double OBLIQUITY_J2000_DEG = 23.4392911; // Earth's obliquity at J2000
    private static final double OBLIQUITY_J2000_RAD = Math.toRadians(OBLIQUITY_J2000_DEG);

    // Time reference constants
    private static final double J2000_EPOCH_JD = 2451545.0; // Julian Date of J2000.0 epoch
    private static final double SECONDS_PER_DAY = 86400.0;

    // Cache of transformation matrices (for fixed transformations)
    private final Map<String, Matrix3d> transformCache = new HashMap<>();

    public BuiltInFrameKernel() {
        initializeTransformations();
    }

    /**
     * Initialize the standard transformations.
     *
     * Convention: scientific frames stay pure; visualization transformations are isolated at the
     * rendering boundary.
     */
    private void initializeTransformations() {
        // Scientific frame transformations (IAU/SPICE standard).

        // J2000 to ECLIPJ2000: Rotate by obliquity around X-axis
        // This aligns the ecliptic (Earth's orbital plane) with the XY plane
        Matrix3d j2000ToEcliptic = new Matrix3d().rotationX(OBLIQUITY_J2000_RAD);
        transformCache.put("J2000->ECLIPJ2000", j2000ToEcliptic);

        // Inverse transformation
        Matrix3d eclipticToJ2000 = new Matrix3d().rotationX(-OBLIQUITY_J2000_RAD);
        transformCache.put("ECLIPJ2000->J2000", eclipticToJ2000);

        // Visualization frame transformation (OpenGL convention).

        // J2000 to OPENGL_RENDER: Transform for OpenGL visualization
        //
        // Problem: In J2000, planets orbit in a plane tilted from XY by obliquity.
        //          In OpenGL convention, we want orbits in the XZ plane (Y-up).
        //
        // Solution: Compound transformation
        //   Step 1: Rotate by -obliquity around X to align ecliptic with XY plane
        //   Step 2: Rotate by -90° around X to move ecliptic from XY to XZ plane
        //
        // Mathematical composition: R_x(-90°) * R_x(-obliquity) = R_x(-90° - obliquity)
        //
        // Standard astronomy-to-OpenGL frame conversion
        double renderRotationAngle = -Math.toRadians(90) - OBLIQUITY_J2000_RAD;
        Matrix3d j2000ToRender = new Matrix3d().rotationX(renderRotationAngle);
        transformCache.put("J2000->OPENGL_RENDER", j2000ToRender);

        // Inverse transformation
        Matrix3d renderToJ2000 = new Matrix3d().rotationX(-renderRotationAngle);
        transformCache.put("OPENGL_RENDER->J2000", renderToJ2000);

        // ECLIPJ2000 to OPENGL_RENDER: Transform ecliptic XY plane to OpenGL XZ plane
        // ECLIPJ2000: Ecliptic in XY, planets orbit clockwise from north (+Z)
        // OPENGL_RENDER: Want ecliptic in XZ, planets orbit counter-clockwise from above (+Y)
        //
        // Solution: Rotate -90° around X axis (moves +Y to -Z, +Z to +Y)
        // This flips the orbital direction to counter-clockwise when viewed from +Y
        Matrix3d eclipticToRender = new Matrix3d().rotationX(Math.toRadians(-90));
        transformCache.put("ECLIPJ2000->OPENGL_RENDER", eclipticToRender);

        Matrix3d renderToEcliptic = new Matrix3d().rotationX(Math.toRadians(90));
        transformCache.put("OPENGL_RENDER->ECLIPJ2000", renderToEcliptic);

        // Identity transformations (frame to itself)
        Matrix3d identity = new Matrix3d().identity();
        transformCache.put("J2000->J2000", identity);
        transformCache.put("ECLIPJ2000->ECLIPJ2000", identity);
        transformCache.put("OPENGL_RENDER->OPENGL_RENDER", identity);

        // ICRF is essentially J2000 for our purposes (difference is microarcseconds)
        transformCache.put("ICRF->J2000", identity);
        transformCache.put("J2000->ICRF", identity);
        transformCache.put("ICRF->ICRF", identity);

        // Add ICRF to other frames (via J2000)
        transformCache.put("ICRF->ECLIPJ2000", j2000ToEcliptic);
        transformCache.put("ECLIPJ2000->ICRF", eclipticToJ2000);
        transformCache.put("ICRF->OPENGL_RENDER", j2000ToRender);
        transformCache.put("OPENGL_RENDER->ICRF", renderToJ2000);
    }

    @Override
    public Matrix3d getTransform(String fromFrame, String toFrame, double et) {
        // Handle IAU body-fixed frames (time-dependent)
        if (fromFrame.startsWith("IAU_") || toFrame.startsWith("IAU_")) {
            return handleIAUFrameTransform(fromFrame, toFrame, et);
        }

        // For non-IAU frames, ignore et (ephemeris time) since we only have fixed transformations

        // Check direct transformation
        String key = fromFrame + "->" + toFrame;
        Matrix3d transform = transformCache.get(key);

        if (transform != null) {
            // Return a copy to prevent external modification
            return new Matrix3d(transform);
        }

        // Try to find a path through J2000 (common intermediate frame)
        if (!fromFrame.equals("J2000") && !toFrame.equals("J2000")) {
            Matrix3d toJ2000 = transformCache.get(fromFrame + "->J2000");
            Matrix3d fromJ2000 = transformCache.get("J2000->" + toFrame);

            if (toJ2000 != null && fromJ2000 != null) {
                // Combine transformations: from -> J2000 -> to
                Matrix3d combined = new Matrix3d(fromJ2000);
                combined.mul(toJ2000);
                return combined;
            }
        }

        // No transformation available
        return null;
    }

    /**
     * Handle transformations involving IAU body-fixed frames. These are time-dependent and use the
     * IAU rotation models.
     */
    private Matrix3d handleIAUFrameTransform(String fromFrame, String toFrame, double et) {
        // Convert ephemeris time (seconds since J2000) to Julian Date TDB.
        double julianDateTDB = J2000_EPOCH_JD + (et / SECONDS_PER_DAY);

        // Case 1: IAU_BODY to J2000
        if (fromFrame.startsWith("IAU_") && toFrame.equals("J2000")) {
            String bodyId = fromFrame.substring(4).toLowerCase();
            return IAURotationModels.getBodyFixedToJ2000(bodyId, julianDateTDB);
        }

        // Case 2: J2000 to IAU_BODY
        if (fromFrame.equals("J2000") && toFrame.startsWith("IAU_")) {
            String bodyId = toFrame.substring(4).toLowerCase();
            return IAURotationModels.getJ2000ToBodyFixed(bodyId, julianDateTDB);
        }

        // Case 3: IAU_BODY1 to IAU_BODY2 (via J2000)
        if (fromFrame.startsWith("IAU_") && toFrame.startsWith("IAU_")) {
            String bodyId1 = fromFrame.substring(4).toLowerCase();
            String bodyId2 = toFrame.substring(4).toLowerCase();

            Matrix3d body1ToJ2000 = IAURotationModels.getBodyFixedToJ2000(bodyId1, julianDateTDB);
            Matrix3d j2000ToBody2 = IAURotationModels.getJ2000ToBodyFixed(bodyId2, julianDateTDB);

            if (body1ToJ2000 != null && j2000ToBody2 != null) {
                Matrix3d combined = new Matrix3d(j2000ToBody2);
                combined.mul(body1ToJ2000);
                return combined;
            }
        }

        // Case 4: IAU_BODY to other frame (via J2000)
        if (fromFrame.startsWith("IAU_")) {
            String bodyId = fromFrame.substring(4).toLowerCase();
            Matrix3d bodyToJ2000 = IAURotationModels.getBodyFixedToJ2000(bodyId, julianDateTDB);
            Matrix3d j2000ToTarget = transformCache.get("J2000->" + toFrame);

            if (bodyToJ2000 != null && j2000ToTarget != null) {
                Matrix3d combined = new Matrix3d(j2000ToTarget);
                combined.mul(bodyToJ2000);
                return combined;
            }
        }

        // Case 5: Other frame to IAU_BODY (via J2000)
        if (toFrame.startsWith("IAU_")) {
            String bodyId = toFrame.substring(4).toLowerCase();
            Matrix3d sourceToJ2000 = transformCache.get(fromFrame + "->J2000");
            Matrix3d j2000ToBody = IAURotationModels.getJ2000ToBodyFixed(bodyId, julianDateTDB);

            if (sourceToJ2000 != null && j2000ToBody != null) {
                Matrix3d combined = new Matrix3d(j2000ToBody);
                combined.mul(sourceToJ2000);
                return combined;
            }
        }

        return null;
    }

    @Override
    public boolean canTransform(String fromFrame, String toFrame) {
        // Check for IAU body-fixed frames
        if (fromFrame.startsWith("IAU_") || toFrame.startsWith("IAU_")) {
            // Extract body IDs
            String bodyId1 =
                    fromFrame.startsWith("IAU_") ? fromFrame.substring(4).toLowerCase() : null;
            String bodyId2 = toFrame.startsWith("IAU_") ? toFrame.substring(4).toLowerCase() : null;

            // Check if we have rotation models for the bodies
            boolean canTransform = true;
            if (bodyId1 != null) {
                canTransform = IAURotationModels.hasRotationModel(bodyId1);
            }
            if (bodyId2 != null && canTransform) {
                canTransform = IAURotationModels.hasRotationModel(bodyId2);
            }

            // Also need to be able to transform to/from J2000
            if (!fromFrame.startsWith("IAU_") && canTransform) {
                canTransform = transformCache.containsKey(fromFrame + "->J2000");
            }
            if (!toFrame.startsWith("IAU_") && canTransform) {
                canTransform = transformCache.containsKey("J2000->" + toFrame);
            }

            return canTransform;
        }

        // Check if we have a direct transformation
        if (transformCache.containsKey(fromFrame + "->" + toFrame)) {
            return true;
        }

        // Check if we can go through J2000
        return transformCache.containsKey(fromFrame + "->J2000")
                && transformCache.containsKey("J2000->" + toFrame);
    }

    @Override
    public String getName() {
        return "Built-in IAU";
    }

    @Override
    public boolean isTimeDependent(String fromFrame, String toFrame) {
        // IAU body-fixed frames are time-dependent (planet rotation)
        if (fromFrame.startsWith("IAU_") || toFrame.startsWith("IAU_")) {
            return true;
        }

        // Our other built-in transformations are fixed
        return false;
    }

    /** Get the obliquity value used by this kernel. */
    public double getObliquityDegrees() {
        return OBLIQUITY_J2000_DEG;
    }

    /** Get the obliquity value in radians. */
    public double getObliquityRadians() {
        return OBLIQUITY_J2000_RAD;
    }

    /**
     * Get a static (time-independent) transformation matrix.
     *
     * <b>Contract:</b> the returned matrix is shared cache state and MUST NOT be mutated by the
     * caller.
     *
     * @param fromFrame Source reference frame
     * @param toFrame Target reference frame
     * @return Cached transformation matrix (do not mutate), or null if not available
     */
    public Matrix3d getStaticTransform(String fromFrame, String toFrame) {
        return transformCache.get(fromFrame + "->" + toFrame);
    }

    /**
     * Check if a static transformation exists.
     *
     * @param fromFrame Source reference frame
     * @param toFrame Target reference frame
     * @return true if the transformation is available
     */
    public boolean hasStaticTransform(String fromFrame, String toFrame) {
        return transformCache.containsKey(fromFrame + "->" + toFrame);
    }
}
