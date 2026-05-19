package com.jda.orrery.domain.ephemeris;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.ephemeris.elp82.ELP82Provider;
import com.jda.orrery.domain.ephemeris.vsop87.VSOP87EProvider;
import java.util.Set;

/**
 * Analytical ephemeris provider combining VSOP87E for barycentric positions and ELP82 for the Moon.
 *
 * This provider uses analytical theories (mathematical series expansions) rather than numerical
 * integration. It provides good accuracy for visual astronomy applications: - VSOP87E: ~0.1-0.5
 * arcseconds for bodies in barycentric coordinates - ELP82: ~10 arcseconds for the Moon (1900-2100)
 *
 * VSOP87E provides barycentric coordinates where all bodies (including the Sun) are positioned
 * relative to the solar system barycenter. This is critical for accurate lighting calculations.
 */
public class AnalyticalEphemerisProvider implements EphemerisProvider {

    // Delegate providers for specific theories
    private final VSOP87EProvider vsop87e;
    private final ELP82Provider elp82;

    // Bodies supported by VSOP87E (includes Sun!)
    private static final Set<String> VSOP87E_BODIES =
            Set.of(
                    "sun", // Sun now has barycentric position
                    "mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune");

    // Bodies supported by ELP82
    private static final Set<String> ELP82_BODIES =
            Set.of(
                    "moon", "301" // Both name and NAIF ID
                    );

    /** Create a new analytical ephemeris provider. Uses VSOP87E for barycentric coordinates. */
    public AnalyticalEphemerisProvider() {
        this.vsop87e = new VSOP87EProvider();
        this.elp82 = new ELP82Provider();
    }

    @Override
    public FramedState getState(TimeContext time, String bodyId) {
        String normalizedId = bodyId.toLowerCase();

        // Handle Moon with ELP82
        if (ELP82_BODIES.contains(normalizedId)) {
            // ELP82 returns Moon position relative to Earth
            // Parent-child handling is done in AbstractCelestialBody
            return elp82.getState(time, bodyId);
        }

        // Handle Sun and planets with VSOP87E (barycentric)
        if (VSOP87E_BODIES.contains(normalizedId)) {
            return vsop87e.getState(time, bodyId);
        }

        // Body not supported
        return null;
    }

    @Override
    public boolean supports(String bodyId) {
        String normalizedId = bodyId.toLowerCase();
        return VSOP87E_BODIES.contains(normalizedId) || ELP82_BODIES.contains(normalizedId);
    }

    @Override
    public double getAccuracy(String bodyId) {
        String normalizedId = bodyId.toLowerCase();

        // Moon accuracy from ELP82
        if (ELP82_BODIES.contains(normalizedId)) {
            return 10.0; // ~10 arcseconds
        }

        // Sun and planet accuracy from VSOP87E
        if (VSOP87E_BODIES.contains(normalizedId)) {
            return vsop87e.getAccuracy(bodyId);
        }

        return Double.NaN;
    }

    @Override
    public double[] getValidTimeRange() {
        // Use the more restrictive range (ELP82: 1900-2100)
        // VSOP87 is valid 2000 BC to 6000 AD, but ELP82 is more limited
        return new double[] {2415020.5, 2488070.5}; // Jan 1 1900 to Jan 1 2100
    }

    @Override
    public String getName() {
        return "VSOP87E/ELP82";
    }

    /** Get information about the theories used. */
    public String getDescription() {
        return "Analytical ephemeris using VSOP87E (barycentric) and ELP-2000/82 (Moon)";
    }
}
