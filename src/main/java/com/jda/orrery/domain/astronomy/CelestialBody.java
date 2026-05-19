package com.jda.orrery.domain.astronomy;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import java.util.List;
import java.util.Optional;

/**
 * Interface for all celestial bodies in the solar system.
 *
 * This represents the pure domain model for astronomical objects, with no rendering
 * dependencies. All position calculations and physical properties are accessed through this
 * interface.
 */
public interface CelestialBody {

    // Identity

    /**
     * Get the unique identifier for this body. For planets, this is typically the NAIF/SPICE ID
     * (e.g., "399" for Earth).
     *
     * @return Unique identifier string
     */
    String getId();

    /** Get the human-readable name of this body. */
    String getName();

    /** Get the type classification of this body. */
    BodyType getBodyType();

    // Hierarchy

    /**
     * Get the parent body if this body orbits another. For example, the Moon's parent is Earth.
     *
     * @return Parent body, or empty if this is a primary body
     */
    Optional<CelestialBody> getParent();

    /**
     * Get the parent body without Optional wrapping. For render-path use where allocation matters.
     *
     * @return Parent body, or null if this is a primary body
     */
    default CelestialBody getParentOrNull() {
        return getParent().orElse(null);
    }

    /**
     * Get all bodies that orbit this one. For example, Earth's children include the Moon.
     *
     * @return List of child bodies (may be empty)
     */
    List<CelestialBody> getChildren();

    /**
     * Add a child body that orbits this one.
     *
     * @param child The child body to add
     */
    void addChild(CelestialBody child);

    // Physical Properties

    /**
     * Get the mass of this body in kilograms.
     *
     * @return Mass in kg
     */
    double getMass();

    /**
     * Get the mean radius of this body in kilometers.
     *
     * @return Mean radius in km
     */
    double getRadius();

    // Dynamics

    /**
     * Get the position and velocity state at the given time.
     *
     * @param time The time context for the calculation
     * @return Framed state including position, velocity, reference frame, and metadata
     */
    FramedState getState(TimeContext time);

    /** Get the ephemeris provider used for position calculations. */
    EphemerisProvider getEphemerisProvider();

    /**
     * Get the default reference frame for this body's coordinates. Now that states carry their own
     * frame information, this is primarily for informational purposes.
     *
     * @return Reference frame name (e.g., "J2000", "ECLIPJ2000")
     */
    String getReferenceFrame();

    // Metadata

    /**
     * Get the positional accuracy at the given time.
     *
     * @param time The time context
     * @return Accuracy in arcseconds, or NaN if unknown
     */
    double getAccuracy(TimeContext time);

    /**
     * Get the valid time range for this body's ephemeris.
     *
     * @return Array of [minJulianDate, maxJulianDate], or null if unlimited
     */
    double[] getValidTimeRange();

    /**
     * Check if ephemeris data is valid at the given time.
     *
     * @param time The time context to check
     * @return true if data is available for this time
     */
    default boolean isValidAt(TimeContext time) {
        double[] range = getValidTimeRange();
        if (range == null) return true;

        double jd = time.getJulianDateTDB();
        return jd >= range[0] && jd <= range[1];
    }

    /**
     * Check if this body is a satellite (natural or artificial).
     *
     * @return true if this body orbits another body (not the sun/barycenter)
     */
    default boolean isSatellite() {
        BodyType type = getBodyType();
        return type == BodyType.NATURAL_SATELLITE || type == BodyType.ARTIFICIAL_SATELLITE;
    }
}
