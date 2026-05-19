package com.jda.orrery.domain.ephemeris;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;

/**
 * Calculates celestial body states (position and velocity). Implementations return states with
 * an explicit reference frame (e.g. {@code J2000}, {@code ECLIPJ2000}), positions in AU, and
 * velocities in AU/day.
 */
public interface EphemerisProvider {

    /**
     * Calculate the state (position and velocity) of a celestial body at the given time.
     *
     * @param time The time context for the calculation
     * @param bodyId Identifier for the celestial body (e.g., "earth", "mars", "301" for Moon)
     * @return Framed state with position (AU), velocity (AU/day), and reference frame, or null if
     *     not supported
     */
    FramedState getState(TimeContext time, String bodyId);

    /**
     * Check if this provider supports a given celestial body.
     *
     * @param bodyId Identifier for the celestial body
     * @return true if this provider can calculate positions for this body
     */
    boolean supports(String bodyId);

    /**
     * Get the accuracy of this provider in arcseconds. This is the expected angular error as seen
     * from Earth.
     *
     * @param bodyId Identifier for the celestial body
     * @return Accuracy in arcseconds, or Double.NaN if unknown
     */
    default double getAccuracy(String bodyId) {
        return Double.NaN;
    }

    /**
     * Get the valid time range for this provider.
     *
     * @return Array of [minJulianDate, maxJulianDate] in TDB, or null if unlimited
     */
    default double[] getValidTimeRange() {
        return null;
    }

    /**
     * Get a human-readable name for this provider.
     *
     * @return Provider name (e.g., "VSOP87", "JPL DE438", "ELP82")
     */
    String getName();
}
