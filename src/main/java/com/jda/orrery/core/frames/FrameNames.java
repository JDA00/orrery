package com.jda.orrery.core.frames;

/**
 * Standard reference frame names following SPICE/NAIF conventions.
 *
 * These names match the official NAIF/SPICE frame identifiers to ensure compatibility with
 * future SPICE kernel integration.
 *
 * Reference: https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/req/frames.html
 */
public final class FrameNames {

    // Prevent instantiation
    private FrameNames() {}

    // Inertial frames

    /**
     * J2000 Earth Mean Equator and Equinox of J2000.0 epoch. This is the standard inertial
     * reference frame for most modern ephemerides.
     */
    public static final String J2000 = "J2000";

    /**
     * International Celestial Reference Frame. More accurate than J2000, aligned with extragalactic
     * radio sources.
     */
    public static final String ICRF = "ICRF";

    /**
     * B1950 Earth Mean Equator and Equinox of B1950.0 epoch. Older standard, still used in some
     * catalogs.
     */
    public static final String FK4 = "FK4";

    // Ecliptic frames

    /** Ecliptic of J2000.0 epoch. The plane of Earth's orbit around the Sun at J2000.0. */
    public static final String ECLIPJ2000 = "ECLIPJ2000";

    /** Mean ecliptic of date. Time-varying ecliptic accounting for precession. */
    public static final String ECLIPDATE = "ECLIPDATE";

    // Body-fixed frames

    /** IAU body-fixed frame for the Sun. */
    public static final String IAU_SUN = "IAU_SUN";

    /** IAU body-fixed frame for Earth. Rotates with Earth, useful for ground-based observations. */
    public static final String IAU_EARTH = "IAU_EARTH";

    /** IAU body-fixed frame for the Moon. */
    public static final String IAU_MOON = "IAU_MOON";

    /** IAU body-fixed frame for Mars. */
    public static final String IAU_MARS = "IAU_MARS";

    // Special frames

    /** Galactic coordinates (centered on Sun, aligned with Milky Way). */
    public static final String GALACTIC = "GALACTIC";

    /** Solar System Barycenter inertial frame. */
    public static final String SSB = "SSB";

    /** Earth-Moon Barycenter frame. */
    public static final String EMB = "EMB";

    // Custom frames for rendering

    /**
     * OpenGL rendering frame. J2000 equatorial rotated so ecliptic aligns with XZ plane. This is
     * NOT a standard SPICE frame.
     */
    public static final String OPENGL_RENDER = "OPENGL_RENDER";

    /** Viewport-aligned frame for HUD elements. This is NOT a standard SPICE frame. */
    public static final String VIEWPORT = "VIEWPORT";
}
