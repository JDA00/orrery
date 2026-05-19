package com.jda.orrery.domain.astronomy.catalog;

import com.jda.orrery.domain.astronomy.BodyType;

/**
 * Immutable astronomical data for a celestial body.
 *
 * Contains only pure astronomical/physical properties.
 *
 * NAIF/SPICE ID Reference: https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/C/req/naif_ids.html
 *
 * @param naifId NAIF/SPICE numerical ID (e.g., 399 for Earth, 301 for Moon)
 * @param code Short identifier for internal use (e.g., "earth", "moon")
 * @param name Full display name (e.g., "Earth", "Moon")
 * @param type Classification (STAR, PLANET, NATURAL_SATELLITE, etc.)
 * @param mass Mass in kilograms
 * @param radius Mean (equatorial) radius in kilometers
 * @param parentCode Parent body code (null for Sun/barycenters)
 * @param semiMajorAxis Semi-major axis in AU (null for Sun)
 * @param orbitalPeriod Orbital period in days
 * @param rotationPeriod Rotation period in hours
 * @param axialTilt Axial tilt in degrees
 * @param polarRadius Polar (semi-minor) radius in km, null for spherical bodies (IAU 2015)
 * @param ringInnerRadius Inner ring radius in planet-radii units, null for ringless bodies
 * @param ringOuterRadius Outer ring radius in planet-radii units, null for ringless bodies
 */
public record BodyData(
        int naifId,
        String code,
        String name,
        BodyType type,
        double mass,
        double radius,
        String parentCode,
        Double semiMajorAxis,
        Double orbitalPeriod,
        Double rotationPeriod,
        Double axialTilt,
        Double polarRadius,
        Double ringInnerRadius,
        Double ringOuterRadius) {
    /** Basic constructor for bodies without orbital data. */
    public BodyData(
            int naifId,
            String code,
            String name,
            BodyType type,
            double mass,
            double radius,
            String parentCode) {
        this(
                naifId,
                code,
                name,
                type,
                mass,
                radius,
                parentCode,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Constructor for primary bodies (no parent). */
    public BodyData(
            int naifId, String code, String name, BodyType type, double mass, double radius) {
        this(
                naifId, code, name, type, mass, radius, null, null, null, null, null, null, null,
                null);
    }

    /** Full constructor with orbital and rotation data. */
    public static BodyData withFullData(
            int naifId,
            String code,
            String name,
            BodyType type,
            double mass,
            double radius,
            String parentCode,
            double semiMajorAxis,
            double orbitalPeriod,
            double rotationPeriod,
            double axialTilt) {
        return new BodyData(
                naifId,
                code,
                name,
                type,
                mass,
                radius,
                parentCode,
                semiMajorAxis,
                orbitalPeriod,
                rotationPeriod,
                axialTilt,
                null,
                null,
                null);
    }

    /**
     * Full constructor with orbital, rotation, and shape data.
     *
     * Use this for oblate bodies (gas giants) and ring-bearing bodies. Pass null for any of the
     * trailing fields to inherit defaults (spherical / ringless).
     */
    public static BodyData withFullData(
            int naifId,
            String code,
            String name,
            BodyType type,
            double mass,
            double radius,
            String parentCode,
            double semiMajorAxis,
            double orbitalPeriod,
            double rotationPeriod,
            double axialTilt,
            Double polarRadius,
            Double ringInnerRadius,
            Double ringOuterRadius) {
        return new BodyData(
                naifId,
                code,
                name,
                type,
                mass,
                radius,
                parentCode,
                semiMajorAxis,
                orbitalPeriod,
                rotationPeriod,
                axialTilt,
                polarRadius,
                ringInnerRadius,
                ringOuterRadius);
    }

    /** Check if this body orbits another. */
    public boolean hasParent() {
        return parentCode != null;
    }

    /** Check if this is a primary body (no parent). */
    public boolean isPrimary() {
        return parentCode == null;
    }

    /** Check if this body has orbital parameters defined. */
    public boolean hasOrbitalData() {
        return semiMajorAxis != null && orbitalPeriod != null;
    }

    /** Check if this body has rotation data defined. */
    public boolean hasRotationData() {
        return rotationPeriod != null;
    }

    /**
     * Polar radius (semi-minor axis) if defined, falling back to equatorial radius for spherical
     * bodies. Used by the renderer to compute oblate body geometry.
     */
    public double polarRadiusOrEquatorial() {
        return polarRadius != null ? polarRadius : radius;
    }

    /** Check if this body has rings with valid inner/outer radii. */
    public boolean hasRings() {
        return ringInnerRadius != null
                && ringOuterRadius != null
                && ringInnerRadius > 0.0
                && ringOuterRadius > ringInnerRadius;
    }

    /**
     * Get the old-style ID for backward compatibility. Returns the code for primary bodies, or NAIF
     * ID string for satellites.
     */
    public String getId() {
        return code;
    }

    /** Get a display string for debugging. */
    @Override
    public String toString() {
        String parent = parentCode != null ? " orbiting " + parentCode : "";
        return String.format(
                "Body[NAIF:%d code:%s '%s' %s r=%.1fkm%s]",
                naifId, code, name, type, radius, parent);
    }
}
