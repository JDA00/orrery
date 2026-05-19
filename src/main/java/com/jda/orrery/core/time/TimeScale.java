package com.jda.orrery.core.time;

/**
 * Time scales used in astronomical calculations.
 *
 * Different astronomical calculations require different time scales. This enum provides all
 * commonly used scales with their relationships documented.
 *
 * Reference: IAU SOFA Time Scale and Calendar Tools https://www.iausofa.org/sofa_ts_c.pdf
 */
public enum TimeScale {
    /**
     * UTC - Coordinated Universal Time Civil time with leap seconds to keep synchronized with Earth
     * rotation. Discontinuous at leap seconds.
     */
    UTC,

    /**
     * TAI - International Atomic Time (Temps Atomique International) Continuous atomic time scale,
     * no leap seconds. TAI = UTC + leap_seconds (currently 37 seconds ahead of UTC as of 2023)
     */
    TAI,

    /**
     * TT - Terrestrial Time Used for geocentric ephemerides. TT = TAI + 32.184 seconds Continuous,
     * no leap seconds.
     */
    TT,

    /**
     * TDB - Barycentric Dynamical Time Used for solar system ephemerides (VSOP87, JPL DE series).
     * Differs from TT by periodic terms (max ~1.7ms) due to relativistic effects. This is the
     * primary time scale for planetary positions.
     */
    TDB,

    /**
     * TCB - Barycentric Coordinate Time Used by IAU for solar system dynamics and Gaia catalog.
     * Linear transformation from TDB: TCB - TDB ≈ L_B * (JD - T0) * 86400 where L_B =
     * 1.550519768e-8
     */
    TCB,

    /**
     * UT1 - Universal Time Based on Earth rotation, needed for sidereal time calculations. UT1 =
     * UTC + DUT1 (from IERS bulletins, typically < 0.9s)
     */
    UT1,

    /**
     * GPS - GPS Time Used by GPS satellites. GPS = TAI - 19 seconds (fixed offset since Jan 6,
     * 1980) No leap seconds since 1980.
     */
    GPS;

    /** Returns true if this time scale includes leap seconds. */
    public boolean hasLeapSeconds() {
        return this == UTC;
    }

    /** Returns true if this time scale is continuous (no discontinuities). */
    public boolean isContinuous() {
        return this != UTC;
    }

    /** Returns the preferred time scale for ephemeris calculations. */
    public static TimeScale getEphemerisScale() {
        return TDB;
    }

    /** Returns the standard civil time scale. */
    public static TimeScale getCivilScale() {
        return UTC;
    }
}
