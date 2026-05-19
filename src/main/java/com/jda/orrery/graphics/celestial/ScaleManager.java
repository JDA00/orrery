package com.jda.orrery.graphics.celestial;

import com.jda.orrery.domain.astronomy.BodyType;
import com.jda.orrery.domain.astronomy.CelestialBody;
import java.util.Map;
import org.joml.Vector3d;

/**
 * Manages scaling of astronomical distances and sizes for visualization.
 *
 * Uses adaptive logarithmic scaling to handle the extreme scale differences in the solar system
 * (Sun radius: 695,700 km vs Mercury radius: 2,439 km, Neptune orbit: 30 AU vs Mercury orbit: 0.39
 * AU).
 *
 * Implements adaptive scaling so distant bodies stay visible without overwhelming near-field
 * detail.
 *
 * Implements hybrid scaling strategy: - Satellites: Fixed or parent-relative visual distances
 * with large multipliers - Asteroids: LUT-based fast scaling - Planets: Power law scaling
 */
public class ScaleManager {

    // Scale factors
    private static final double EARTH_RADIUS_KM = 6371.0; // Reference radius
    private static final double MIN_VISUAL_RADIUS = 0.5; // Minimum size for visibility
    private static final double SUN_SCALE_FACTOR = 0.1; // Sun is huge, scale it down

    // Satellite visual configurations
    // Distance multipliers chosen to balance visibility with spatial relationships:
    // - Moon (10,000x): Makes 0.00257 AU = 25.7 visual units, clearly visible from Earth
    // - Jovian moons (8,000x): Keeps them as cohesive system while showing relative distances
    // - Martian moons would need even higher multipliers due to extreme proximity
    private static final Map<String, SatelliteConfig> SATELLITE_CONFIGS =
            Map.of(
                    "moon", new SatelliteConfig(30.0, SatelliteScaleMode.FIXED_MULTIPLIER, 10000.0),
                    "phobos",
                            new SatelliteConfig(
                                    5.0,
                                    SatelliteScaleMode.FIXED_MULTIPLIER,
                                    50000.0), // Very close to Mars
                    "deimos",
                            new SatelliteConfig(
                                    3.0,
                                    SatelliteScaleMode.FIXED_MULTIPLIER,
                                    30000.0), // Also very close
                    "io", new SatelliteConfig(15.0, SatelliteScaleMode.FIXED_MULTIPLIER, 8000.0),
                    "europa",
                            new SatelliteConfig(15.0, SatelliteScaleMode.FIXED_MULTIPLIER, 8000.0),
                    "ganymede",
                            new SatelliteConfig(20.0, SatelliteScaleMode.FIXED_MULTIPLIER, 8000.0),
                    "callisto",
                            new SatelliteConfig(18.0, SatelliteScaleMode.FIXED_MULTIPLIER, 8000.0));

    // Pre-computed LUT for asteroid scaling (0.1 AU resolution, up to 50 AU)
    private static final float[] ASTEROID_SCALE_LUT = computeAsteroidScaleLUT();
    private static final int LUT_RESOLUTION = 10; // Per AU
    private static final int MAX_LUT_INDEX = 500; // 50 AU max

    /** Satellite scaling configuration. */
    private static class SatelliteConfig {
        final double visualRadius;
        final SatelliteScaleMode mode;
        final double distanceMultiplier; // For FIXED_MULTIPLIER mode

        SatelliteConfig(double visualRadius, SatelliteScaleMode mode) {
            this(visualRadius, mode, 15000.0); // Default multiplier for visibility
        }

        SatelliteConfig(double visualRadius, SatelliteScaleMode mode, double distanceMultiplier) {
            this.visualRadius = visualRadius;
            this.mode = mode;
            this.distanceMultiplier = distanceMultiplier;
        }
    }

    /** Satellite scaling modes. */
    public enum SatelliteScaleMode {
        FIXED_RADIUS, // Fixed visual distance
        PARENT_RELATIVE, // Scale relative to parent's visual size
        LOGARITHMIC, // Log scale of actual distance
        FIXED_MULTIPLIER // Fixed multiplier of actual distance for visibility
    }

    /** Pre-compute asteroid scale lookup table for performance. */
    private static float[] computeAsteroidScaleLUT() {
        float[] lut = new float[MAX_LUT_INDEX + 1];
        for (int i = 0; i <= MAX_LUT_INDEX; i++) {
            double distanceAU = i / (double) LUT_RESOLUTION;
            if (distanceAU < 0.1) {
                lut[i] = 10.0f; // Minimum visual distance
            } else {
                // Use same power law as planets for consistency
                lut[i] = (float) (Math.pow(distanceAU, 0.6) * 100.0);
            }
        }
        return lut;
    }

    /**
     * Convert astronomical distance (AU) to visual distance (render units).
     *
     * Power law: r_visual = k * (r_actual)^α with α < 1. Compresses distances while preserving
     * relative ordering.
     *
     * @param distanceAU Distance in astronomical units
     * @return Visual distance in render units
     */
    public static double getVisualDistance(double distanceAU) {
        if (distanceAU < 0.001) {
            // Handle origin (Sun)
            return 0.0;
        }

        // Power law scaling with exponent < 1; 0.6 gives a comfortable spread
        // (Mercury ~40, Earth ~100, Neptune ~900).
        double powerScale = Math.pow(distanceAU, 0.6);

        // Scale factor to get similar numbers to JavaFX
        // (Mercury at ~40, Earth at ~100, Neptune at ~900)
        double scaleFactor = 100.0;

        return powerScale * scaleFactor;

        /* This gives approximately:
         * Mercury (0.39 AU): 39 * 0.6^0.6 ≈ 53 units
         * Venus (0.72 AU): 72 * 0.82 ≈ 82 units
         * Earth (1.0 AU): 100 units
         * Mars (1.52 AU): 152 * 0.6^0.6 ≈ 118 units
         * Jupiter (5.2 AU): 520 * 0.6^0.6 ≈ 233 units
         * Saturn (9.5 AU): 950 * 0.6^0.6 ≈ 330 units
         * Uranus (19.2 AU): 1920 * 0.6^0.6 ≈ 500 units
         * Neptune (30 AU): 3000 * 0.6^0.6 ≈ 650 units
         */
    }

    /**
     * Convert radius in kilometers to visual radius (render units).
     *
     * Uses logarithmic scaling to make all bodies visible while preserving relative size
     * relationships.
     *
     * @param radiusKm Actual radius in kilometers
     * @param bodyType Type of body for special handling (e.g., "sun")
     * @return Visual radius in render units
     */
    public static double getVisualRadius(double radiusKm, String bodyType) {
        // Special case for the Sun
        if ("sun".equalsIgnoreCase(bodyType)) {
            // Sun is 109x Earth radius, scale it down to ~10x for visibility
            return Math.log10(radiusKm / EARTH_RADIUS_KM) * 3.0;
        }

        // Special case for Moon - make it more visible
        if ("moon".equalsIgnoreCase(bodyType)) {
            // Moon is 0.27x Earth radius, boost to ~0.5x for visibility
            return (radiusKm / EARTH_RADIUS_KM) * 2.0;
        }

        // Calculate base scale relative to Earth
        double earthScale = radiusKm / EARTH_RADIUS_KM;

        if (earthScale < 0.1) {
            // Very small bodies (asteroids, small moons): Boost size
            return MIN_VISUAL_RADIUS + Math.log10(radiusKm / 1000.0) * 0.2;
        } else if (earthScale < 0.5) {
            // Small planets (Mercury, Mars): Moderate boost
            return MIN_VISUAL_RADIUS + earthScale * 2.0;
        } else if (earthScale <= 2.0) {
            // Earth-like bodies: Near-linear scaling
            return earthScale * 1.5;
        } else {
            // Gas giants: Logarithmic compression
            return 3.0 + Math.log10(earthScale) * 4.0;
        }
    }

    /**
     * Scale an astronomical position (AU) into render units, writing the result into the
     * caller-supplied {@code output} vector.
     *
     * @param body The celestial body to scale
     * @param astronomicalPosition Position in astronomical coordinates (AU) as JOML Vector3d
     * @param output Pre-allocated Vector3d to store result (modified in place)
     */
    public static void scaleBodyPositionInto(
            CelestialBody body, Vector3d astronomicalPosition, Vector3d output) {
        double distance = astronomicalPosition.length();

        if (distance < 0.001) {
            // At origin (Sun)
            output.set(0, 0, 0);
            return;
        }

        // Calculate visual distance using appropriate scaling
        double visualDistance;
        BodyType bodyType = body.getBodyType();

        // Fast path for asteroids
        if (bodyType == BodyType.ASTEROID
                || bodyType == BodyType.COMET
                || bodyType == BodyType.TNO
                || bodyType == BodyType.SMALL_BODY) {
            // Use LUT for fast scaling
            int lutIndex = Math.min((int) (distance * LUT_RESOLUTION), MAX_LUT_INDEX);
            visualDistance = ASTEROID_SCALE_LUT[lutIndex];
        } else {
            // Planets and Sun use power law
            visualDistance = getVisualDistance(distance);
        }

        // Calculate scale factor
        double scale = visualDistance / distance;

        // Scale directly into output vector (no allocation!)
        output.set(
                astronomicalPosition.x * scale,
                astronomicalPosition.y * scale,
                astronomicalPosition.z * scale);
    }

    /**
     * Get the distance multiplier for a satellite. Used to scale relative offsets for visual
     * clarity.
     *
     * @param satelliteId The satellite identifier (must be lowercase)
     * @return Distance multiplier for the satellite
     */
    public static double getSatelliteDistanceMultiplier(String satelliteId) {
        // Expect lowercase satellite IDs (caller normalizes) to avoid allocation.
        SatelliteConfig config = SATELLITE_CONFIGS.get(satelliteId);

        if (config != null && config.mode == SatelliteScaleMode.FIXED_MULTIPLIER) {
            return config.distanceMultiplier;
        } else {
            // Default multiplier for visibility
            return 15000.0;
        }
    }
}
