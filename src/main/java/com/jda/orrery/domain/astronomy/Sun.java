package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.domain.astronomy.catalog.BodyData;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;

/**
 * Represents the Sun in the solar system.
 *
 * The Sun is at the origin in heliocentric coordinates, but has a small motion in barycentric
 * coordinates.
 */
public class Sun extends AbstractCelestialBody {

    // Solar rotation parameters
    private static final double ROTATION_PERIOD = 609.12; // hours (25.38 days at equator)
    private static final double OBLIQUITY = 7.25; // degrees

    /**
     * Create the Sun from catalog data.
     *
     * @param data Sun data from the celestial catalog
     * @param ephemerisProvider Provider for position calculations (typically returns origin)
     * @param cache Ephemeris cache
     */
    public Sun(BodyData data, EphemerisProvider ephemerisProvider, EphemerisCache cache) {
        super(
                data.getId(),
                data.name(),
                data.type(),
                data.mass(),
                data.radius(),
                ephemerisProvider,
                cache,
                FrameNames.ECLIPJ2000 // VSOP87E provides J2000 ecliptic coordinates
                );

        // Validate that this is actually the Sun
        if (data.type() != BodyType.STAR) {
            throw new IllegalArgumentException("Sun requires BodyType.STAR, got: " + data.type());
        }
    }

    /**
     * Get the solar rotation period at the equator.
     *
     * @return Rotation period in hours
     */
    public double getRotationPeriod() {
        return ROTATION_PERIOD;
    }

    /**
     * Get the solar obliquity (tilt of rotation axis).
     *
     * @return Obliquity in degrees
     */
    public double getObliquity() {
        return OBLIQUITY;
    }

    /**
     * Calculate differential rotation rate at a given latitude. The Sun rotates faster at the
     * equator than at the poles.
     *
     * @param latitude Heliographic latitude in degrees
     * @return Rotation period in hours at that latitude
     */
    public double getDifferentialRotationPeriod(double latitude) {
        // Carrington rotation formula
        double latRad = Math.toRadians(latitude);
        double sinSquared = Math.sin(latRad) * Math.sin(latRad);

        // Sidereal rotation rate in degrees per day
        double rate = 14.522 - 2.84 * sinSquared;

        // Convert to hours
        return 360.0 / rate * 24.0;
    }

    @Override
    public double getAccuracy(com.jda.orrery.core.time.TimeContext time) {
        // Delegate to ephemeris provider
        return getEphemerisProvider().getAccuracy(getId());
    }

    @Override
    public double[] getValidTimeRange() {
        // Delegate to ephemeris provider
        return getEphemerisProvider().getValidTimeRange();
    }

    @Override
    public String toString() {
        return String.format("Sun[%s]", getId());
    }
}
