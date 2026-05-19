package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.domain.astronomy.catalog.BodyData;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;

/**
 * Natural satellite (moon) of a planet or other celestial body.
 *
 * Generic — represents any natural satellite. Satellites are treated as data entries (via {@link
 * BodyData}) rather than requiring a separate class per moon.
 */
public class NaturalSatellite extends AbstractCelestialBody {

    /**
     * Create a natural satellite from catalog data.
     *
     * @param data Satellite data from the celestial catalog
     * @param ephemerisProvider Provider for position calculations
     * @param cache Ephemeris cache
     */
    public NaturalSatellite(
            BodyData data, EphemerisProvider ephemerisProvider, EphemerisCache cache) {
        super(
                data.getId(),
                data.name(),
                data.type(),
                data.mass(),
                data.radius(),
                ephemerisProvider,
                cache,
                FrameNames.ECLIPJ2000 // ELP82 now returns ecliptic to match planets
                );

        // Validate that this is actually a moon
        if (data.type() != BodyType.NATURAL_SATELLITE) {
            throw new IllegalArgumentException(
                    "NaturalSatellite requires BodyType.NATURAL_SATELLITE, got: " + data.type());
        }

        // Validate that it has a parent
        if (data.parentCode() == null) {
            throw new IllegalArgumentException("NaturalSatellite requires a parent body");
        }
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
        String parentName = getParent().map(CelestialBody::getName).orElse("unknown");

        return String.format(
                "NaturalSatellite[%s (%s) orbiting %s]", getName(), getId(), parentName);
    }
}
