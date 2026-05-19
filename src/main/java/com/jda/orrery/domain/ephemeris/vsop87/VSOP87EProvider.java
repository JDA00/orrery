package com.jda.orrery.domain.ephemeris.vsop87;

import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.vsop87.data.VSOP87ELarge;
import com.jda.orrery.domain.ephemeris.vsop87.data.VSOP87ELargeVelocities;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ephemeris provider using VSOP87E theory for barycentric positions.
 *
 * VSOP87E provides BARYCENTRIC positions in J2000 ECLIPTIC coordinates. This means all bodies
 * (including the Sun) are positioned relative to the solar system barycenter (center of mass).
 *
 * Key differences from VSOP87A: - VSOP87A: Heliocentric (Sun at origin, planets orbit Sun) -
 * VSOP87E: Barycentric (Everything orbits center of mass)
 *
 * The Sun's barycentric motion is primarily caused by Jupiter (~1 solar radius) with smaller
 * contributions from Saturn and other planets.
 *
 * This implementation uses the "large" version for maximum accuracy. Velocities are provided
 * directly by VSOP87E velocity calculations.
 */
public class VSOP87EProvider implements EphemerisProvider {

    // Supported body identifiers (lowercase for consistency)
    private static final Set<String> SUPPORTED_BODIES =
            Set.of(
                    "sun", // The Sun now has a position!
                    "mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune");

    // Accuracy estimates in arcseconds (from VSOP87 documentation)
    // VSOP87E "large" version provides better accuracy than "small"
    private static final Map<String, Double> ACCURACY = new HashMap<>();

    static {
        ACCURACY.put("sun", 0.1); // Sun's barycentric position
        ACCURACY.put("mercury", 0.1);
        ACCURACY.put("venus", 0.1);
        ACCURACY.put("earth", 0.1);
        ACCURACY.put("mars", 0.2);
        ACCURACY.put("jupiter", 0.2);
        ACCURACY.put("saturn", 0.3);
        ACCURACY.put("uranus", 0.5);
        ACCURACY.put("neptune", 0.5);
    }

    // Time range: 2000 BC to 6000 AD in Julian Date
    private static final double MIN_JD = 1270563.5; // 2000 BC
    private static final double MAX_JD = 3912880.5; // 6000 AD

    // J2000.0 epoch
    private static final double J2000 = 2451545.0;

    @Override
    public FramedState getState(TimeContext time, String bodyId) {
        if (!supports(bodyId)) {
            return null;
        }

        String id = bodyId.toLowerCase();
        double jd = time.getJulianDateTDB();

        // VSOP87 uses Julian millennia since J2000.0
        double T = (jd - J2000) / 365250.0;

        // Get position from VSOP87E (barycentric, equatorial)
        double[] position = calculatePosition(id, T);
        if (position == null) {
            return null;
        }

        // Get velocity from VSOP87E velocity calculations
        double[] velocity = calculateVelocity(id, T);
        if (velocity == null) {
            // Fallback to zero velocity if not available
            velocity = new double[] {0.0, 0.0, 0.0};
        }

        // Convert to Vec3d
        Vec3d pos = new Vec3d(position[0], position[1], position[2]);
        Vec3d vel = new Vec3d(velocity[0], velocity[1], velocity[2]);

        // VSOP87E returns J2000 barycentric ECLIPTIC coordinates
        return new FramedState(
                pos,
                vel,
                FrameNames.ECLIPJ2000, // VSOP87E provides J2000 ecliptic
                time.getEphemerisTime(),
                getAccuracy(bodyId));
    }

    @Override
    public boolean supports(String bodyId) {
        return SUPPORTED_BODIES.contains(bodyId.toLowerCase());
    }

    @Override
    public double getAccuracy(String bodyId) {
        return ACCURACY.getOrDefault(bodyId.toLowerCase(), Double.NaN);
    }

    @Override
    public double[] getValidTimeRange() {
        return new double[] {MIN_JD, MAX_JD};
    }

    @Override
    public String getName() {
        return "VSOP87E";
    }

    /**
     * Calculate position using VSOP87E theory.
     *
     * @param bodyId Body identifier (lowercase)
     * @param T Julian millennia since J2000.0
     * @return Position in AU [x, y, z] in barycentric equatorial coordinates
     */
    private double[] calculatePosition(String bodyId, double T) {
        switch (bodyId) {
            case "sun":
                return VSOP87ELarge.getSun(T);
            case "mercury":
                return VSOP87ELarge.getMercury(T);
            case "venus":
                return VSOP87ELarge.getVenus(T);
            case "earth":
                return VSOP87ELarge.getEarth(T);
            case "mars":
                return VSOP87ELarge.getMars(T);
            case "jupiter":
                return VSOP87ELarge.getJupiter(T);
            case "saturn":
                return VSOP87ELarge.getSaturn(T);
            case "uranus":
                return VSOP87ELarge.getUranus(T);
            case "neptune":
                return VSOP87ELarge.getNeptune(T);
            default:
                return null;
        }
    }

    /**
     * Calculate velocity using VSOP87E velocity calculations.
     *
     * @param bodyId Body identifier (lowercase)
     * @param T Julian millennia since J2000.0
     * @return Velocity in AU/day [vx, vy, vz] in barycentric equatorial coordinates
     */
    private double[] calculateVelocity(String bodyId, double T) {
        switch (bodyId) {
            case "sun":
                return VSOP87ELargeVelocities.getSun(T);
            case "mercury":
                return VSOP87ELargeVelocities.getMercury(T);
            case "venus":
                return VSOP87ELargeVelocities.getVenus(T);
            case "earth":
                return VSOP87ELargeVelocities.getEarth(T);
            case "mars":
                return VSOP87ELargeVelocities.getMars(T);
            case "jupiter":
                return VSOP87ELargeVelocities.getJupiter(T);
            case "saturn":
                return VSOP87ELargeVelocities.getSaturn(T);
            case "uranus":
                return VSOP87ELargeVelocities.getUranus(T);
            case "neptune":
                return VSOP87ELargeVelocities.getNeptune(T);
            default:
                return null;
        }
    }
}
