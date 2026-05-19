package com.jda.orrery.core.frames;

import com.jda.orrery.core.logging.Logging;
import java.util.logging.Logger;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * IAU rotation models for transforming between J2000 and body-fixed frames.
 *
 * Implements the IAU/IAG Working Group formulas for planetary rotation. These transformations
 * are essential for: - Correct texture alignment on planets - Realistic rotation animation -
 * Surface feature tracking - Landing site calculations
 *
 * The transformation follows the IAU convention using three Euler angles: 1. Rotate by α (pole
 * right ascension) around Z 2. Rotate by 90°-δ (co-declination) around X 3. Rotate by W (prime
 * meridian) around Z
 *
 * Reference: Archinal et al. (2018) "Report of the IAU Working Group on Cartographic Coordinates
 * and Rotational Elements: 2015"
 *
 * Based on NASA SPICE PCK implementation.
 */
public class IAURotationModels {
    private static final Logger LOGGER = Logging.logger(IAURotationModels.class);

    // Constants
    private static final double J2000_EPOCH_JD = 2451545.0; // Julian Date of J2000 epoch
    private static final double DAYS_PER_CENTURY = 36525.0;
    private static final double DEG_TO_RAD = Math.PI / 180.0;

    /**
     * Get the transformation matrix from J2000 to IAU body-fixed frame.
     *
     * This transforms coordinates from the J2000 inertial frame to a body-fixed frame where: -
     * Z-axis points through the north pole - X-axis points through the prime meridian at the
     * equator - Y-axis completes the right-handed system
     *
     * @param bodyId The celestial body identifier (e.g., "earth", "mars")
     * @param julianDateTDB Julian date in Barycentric Dynamical Time
     * @return 3x3 rotation matrix from J2000 to body-fixed, or null if body not found
     */
    public static Matrix3d getJ2000ToBodyFixed(String bodyId, double julianDateTDB) {
        // Get rotation parameters for this body
        IAURotationParameters params = IAURotationParameters.forBody(bodyId);
        if (params == null) {
            LOGGER.warning("No IAU rotation parameters found for body: " + bodyId);
            return null;
        }

        // Calculate time since J2000 epoch
        double daysSinceJ2000 = julianDateTDB - J2000_EPOCH_JD;
        double centuriesSinceJ2000 = daysSinceJ2000 / DAYS_PER_CENTURY;

        // Calculate pole orientation at this epoch
        double poleRA = params.getPoleRA(centuriesSinceJ2000) * DEG_TO_RAD;
        double poleDec = params.getPoleDec(centuriesSinceJ2000) * DEG_TO_RAD;

        // Calculate prime meridian angle at this epoch
        double primeMeridianDeg = params.getPrimeMeridian(daysSinceJ2000);
        double primeMeridian = primeMeridianDeg * DEG_TO_RAD;

        // Log rotation state for debugging (reduced frequency)
        if (Math.abs(daysSinceJ2000) < 0.001) { // Near J2000 epoch
            LOGGER.fine(
                    String.format(
                            "%s rotation at J2000: pole=(%.2f°,%.2f°), W=%.2f°",
                            params.name,
                            poleRA / DEG_TO_RAD,
                            poleDec / DEG_TO_RAD,
                            primeMeridian / DEG_TO_RAD));
        }

        // IAU convention: M = Rz(90° + α) * Rx(90° - δ) * Rz(W). The helper
        // builds the matrix directly from the pole and meridian angles instead
        // of composing three rotations.
        return constructBodyFixedMatrix(poleRA, poleDec, primeMeridian);
    }

    /**
     * Get the transformation matrix from IAU body-fixed to J2000 frame.
     *
     * This is the inverse of getJ2000ToBodyFixed.
     *
     * @param bodyId The celestial body identifier
     * @param julianDateTDB Julian date in TDB
     * @return 3x3 rotation matrix from body-fixed to J2000
     */
    public static Matrix3d getBodyFixedToJ2000(String bodyId, double julianDateTDB) {
        Matrix3d j2000ToBody = getJ2000ToBodyFixed(bodyId, julianDateTDB);
        if (j2000ToBody == null) {
            return null;
        }

        // Return the transpose (inverse of rotation matrix)
        return new Matrix3d(j2000ToBody).transpose();
    }

    /**
     * Construct the body-fixed transformation matrix directly from pole and meridian angles.
     *
     * Body-fixed frame:
     * - Z-axis: Points through the north pole (RA=α, Dec=δ).
     * - X-axis: Points through prime meridian at the equator.
     * - Y-axis: Completes a right-handed system.
     *
     * @param poleRA Right ascension of north pole (radians)
     * @param poleDec Declination of north pole (radians)
     * @param primeMeridian Angle of prime meridian (radians)
     * @return Rotation matrix from J2000 to body-fixed frame
     */
    private static Matrix3d constructBodyFixedMatrix(
            double poleRA, double poleDec, double primeMeridian) {
        // Calculate trigonometric values
        double cosRA = Math.cos(poleRA);
        double sinRA = Math.sin(poleRA);
        double cosDec = Math.cos(poleDec);
        double sinDec = Math.sin(poleDec);
        double cosW = Math.cos(primeMeridian);
        double sinW = Math.sin(primeMeridian);

        // Direct construction of the rotation matrix elements.
        // Columns of this matrix are:
        // Col 1: Body X-axis in J2000 coordinates (prime meridian).
        // Col 2: Body Y-axis in J2000 coordinates (90° east of prime meridian).
        // Col 3: Body Z-axis in J2000 coordinates (north pole).

        Matrix3d matrix = new Matrix3d();

        // Column 1: Body X-axis (prime meridian at equator) in J2000
        matrix.m00 = -sinRA * cosW - cosRA * sinDec * sinW;
        matrix.m10 = cosRA * cosW - sinRA * sinDec * sinW;
        matrix.m20 = cosDec * sinW;

        // Column 2: Body Y-axis (90° east of prime meridian) in J2000
        matrix.m01 = sinRA * sinW - cosRA * sinDec * cosW;
        matrix.m11 = -cosRA * sinW - sinRA * sinDec * cosW;
        matrix.m21 = cosDec * cosW;

        // Column 3: Body Z-axis (north pole) in J2000
        matrix.m02 = cosRA * cosDec;
        matrix.m12 = sinRA * cosDec;
        matrix.m22 = sinDec;

        // Note: This matrix transforms vectors FROM J2000 TO body-fixed
        // by expressing J2000 basis vectors in body-fixed coordinates

        return matrix;
    }

    /**
     * Write the body's spin axis (north pole direction) in J2000 coordinates into the
     * caller-supplied destination vector, so the render loop can call this without allocating.
     *
     * The spin axis is column 3 of getBodyFixedToJ2000 (equivalently row 3 of
     * getJ2000ToBodyFixed). Computed directly from the IAU pole RA/Dec to avoid allocating an
     * intermediate rotation matrix.
     *
     * @param bodyId The celestial body identifier
     * @param julianDateTDB Julian date in TDB
     * @param dest Output vector — receives the unit-length spin axis
     * @return the {@code dest} vector for chaining, or null if the body has no rotation parameters
     *     (in which case {@code dest} is unchanged)
     */
    public static Vector3d getSpinAxisJ2000(String bodyId, double julianDateTDB, Vector3d dest) {
        IAURotationParameters params = IAURotationParameters.forBody(bodyId);
        if (params == null) {
            return null;
        }
        double centuriesSinceJ2000 = (julianDateTDB - J2000_EPOCH_JD) / DAYS_PER_CENTURY;
        double poleRA = params.getPoleRA(centuriesSinceJ2000) * DEG_TO_RAD;
        double poleDec = params.getPoleDec(centuriesSinceJ2000) * DEG_TO_RAD;
        double cosDec = Math.cos(poleDec);
        dest.x = Math.cos(poleRA) * cosDec;
        dest.y = Math.sin(poleRA) * cosDec;
        dest.z = Math.sin(poleDec);
        return dest;
    }

    /**
     * Check if a body has IAU rotation parameters defined.
     *
     * @param bodyId The celestial body identifier
     * @return true if rotation parameters are available
     */
    public static boolean hasRotationModel(String bodyId) {
        return IAURotationParameters.forBody(bodyId) != null;
    }

    /**
     * Get the rotation rate of a body in degrees per day.
     *
     * Useful for animation and time-step calculations.
     *
     * @param bodyId The celestial body identifier
     * @return Rotation rate in degrees/day, or 0 if not found
     */
    public static double getRotationRate(String bodyId) {
        IAURotationParameters params = IAURotationParameters.forBody(bodyId);
        return params != null ? params.meridianRate : 0.0;
    }

    /**
     * Get the prime meridian angle at a given time.
     *
     * @param bodyId The celestial body identifier
     * @param julianDateTDB Julian date in TDB
     * @return Prime meridian angle in degrees
     */
    public static double getPrimeMeridianAngle(String bodyId, double julianDateTDB) {
        IAURotationParameters params = IAURotationParameters.forBody(bodyId);
        if (params == null) {
            return 0.0;
        }
        double daysSinceJ2000 = julianDateTDB - J2000_EPOCH_JD;
        return params.getPrimeMeridian(daysSinceJ2000);
    }
}
