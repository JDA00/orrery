package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.domain.astronomy.catalog.BodyData;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;

/**
 * Represents a planet in the solar system.
 *
 * Planets are treated as data entries (via {@link BodyData}) rather than requiring a separate
 * class per planet.
 */
public class Planet extends AbstractCelestialBody {

    private final BodyData data;

    /**
     * Create a planet from catalog data.
     *
     * @param data Planet data from the celestial catalog
     * @param ephemerisProvider Provider for position calculations
     * @param cache Ephemeris cache
     */
    public Planet(BodyData data, EphemerisProvider ephemerisProvider, EphemerisCache cache) {
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

        // Validate that this is actually a planet
        if (data.type() != BodyType.PLANET) {
            throw new IllegalArgumentException(
                    "Planet requires BodyType.PLANET, got: " + data.type());
        }

        this.data = data;
    }

    /** Sidereal rotation period in hours. Negative for retrograde rotation (Venus, Uranus). */
    public double getRotationPeriod() {
        return data.rotationPeriod();
    }

    /**
     * Axial tilt in degrees. Values &gt; 90° mean the rotation axis is flipped relative to the
     * orbital normal (Venus 177°, Uranus 98°).
     */
    public double getObliquity() {
        return data.axialTilt();
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
        return String.format("Planet[%s (%s)]", getName(), getId());
    }
}
