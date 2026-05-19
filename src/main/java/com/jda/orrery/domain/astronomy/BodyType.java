package com.jda.orrery.domain.astronomy;

/**
 * Classification of celestial body types.
 *
 * Used to determine appropriate rendering strategies and ephemeris providers for different kinds
 * of objects.
 */
public enum BodyType {

    /** Star (Sun or other stars). */
    STAR("Star"),

    /** Major planet (Mercury through Neptune). */
    PLANET("Planet"),

    /** Dwarf planet (Pluto, Eris, Ceres, etc.). */
    DWARF_PLANET("Dwarf Planet"),

    /** Natural satellite (moons). */
    NATURAL_SATELLITE("Natural Satellite"),

    /** Artificial satellite or spacecraft. */
    ARTIFICIAL_SATELLITE("Artificial Satellite"),

    /** Asteroid or minor planet. */
    ASTEROID("Asteroid"),

    /** Comet. */
    COMET("Comet"),

    /** Trans-Neptunian object. */
    TNO("Trans-Neptunian Object"),

    /** Barycenter (center of mass of a system). */
    BARYCENTER("Barycenter"),

    /** Lagrange point. */
    LAGRANGE_POINT("Lagrange Point"),

    /** Generic small body. */
    SMALL_BODY("Small Body"),

    /** Unknown or undefined type. */
    UNKNOWN("Unknown");

    private final String displayName;

    BodyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Check if this is a natural body (not artificial). */
    public boolean isNatural() {
        return this != ARTIFICIAL_SATELLITE && this != LAGRANGE_POINT;
    }

    /** Check if this body type typically orbits another body. */
    public boolean isOrbiting() {
        return this == NATURAL_SATELLITE
                || this == ARTIFICIAL_SATELLITE
                || this == ASTEROID
                || this == COMET
                || this == TNO;
    }

    /** Check if this is a major body (star or planet). */
    public boolean isMajor() {
        return this == STAR || this == PLANET;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
