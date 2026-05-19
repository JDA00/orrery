package com.jda.orrery.graphics.pipeline;

/**
 * Fast body identification enum for rendering hot path. Replaces string comparisons with O(1) enum
 * checks. Separate from domain BodyType to avoid coupling rendering optimization to domain model.
 */
public enum CelestialBodyId {
    SUN(0, "sun", true, false),
    MERCURY(1, "mercury", false, false),
    VENUS(2, "venus", false, false),
    EARTH(3, "earth", false, false),
    MARS(4, "mars", false, false),
    JUPITER(5, "jupiter", false, true),
    SATURN(6, "saturn", false, true),
    URANUS(7, "uranus", false, true),
    NEPTUNE(8, "neptune", false, true),
    MOON(9, "moon", false, false),
    UNKNOWN(-1, null, false, false);

    private final int id;
    private final String bodyIdString;
    private final boolean emissive;
    private final boolean gasGiant;

    // Pre-cached values array to avoid cloning overhead
    private static final CelestialBodyId[] VALUES = values();

    // Pre-allocated ring texture IDs to avoid string concatenation
    private static final String JUPITER_RING = "jupiter_ring";
    private static final String SATURN_RING = "saturn_ring";
    private static final String URANUS_RING = "uranus_ring";
    private static final String NEPTUNE_RING = "neptune_ring";

    CelestialBodyId(int id, String bodyIdString, boolean emissive, boolean gasGiant) {
        this.id = id;
        this.bodyIdString = bodyIdString;
        this.emissive = emissive;
        this.gasGiant = gasGiant;
    }

    public int getId() {
        return id;
    }

    public String getBodyIdString() {
        return bodyIdString;
    }

    public boolean isEmissive() {
        return emissive;
    }

    public boolean isGasGiant() {
        return gasGiant;
    }

    /**
     * Get shader body type for GPU uniforms. 0 = rocky/terrestrial, 1 = gas giant (Jupiter,
     * Saturn), 2 = ice giant (Uranus, Neptune).
     *
     * Ice giants share the gas-giant `gasGiant` flag (used for ring detection, polar-radius
     * expectations, oblateness) but route to a separate shader path because their atmospheric
     * scattering is methane-dominated rather than ammonia-dominated, with measurably different limb
     * behaviour (Karkoschka 1998 Minnaert k ≈ 0.6 at visible wavelengths).
     */
    public int getShaderBodyType() {
        if (this == URANUS || this == NEPTUNE) return 2;
        return gasGiant ? 1 : 0;
    }

    public boolean hasRings() {
        return this == JUPITER || this == SATURN || this == URANUS || this == NEPTUNE;
    }

    /**
     * Get pre-allocated ring texture ID for bodies with rings. Returns null for bodies without
     * rings. Zero-allocation method using pre-allocated strings.
     */
    public String getRingTextureId() {
        switch (this) {
            case JUPITER:
                return JUPITER_RING;
            case SATURN:
                return SATURN_RING;
            case URANUS:
                return URANUS_RING;
            case NEPTUNE:
                return NEPTUNE_RING;
            default:
                return null;
        }
    }

    /**
     * Fast lookup by body ID string. Uses direct string comparison without {@code toLowerCase()};
     * callers must pass an already-lowercased ID. Called once per body per frame, not in inner
     * loops.
     */
    public static CelestialBodyId fromString(String bodyId) {
        if (bodyId == null) return UNKNOWN;

        // Direct comparison - no allocation
        // Assumes body IDs are already lowercase (which they should be)
        switch (bodyId) {
            case "sun":
                return SUN;
            case "Sun":
                return SUN; // Fallback for compatibility
            case "mercury":
                return MERCURY;
            case "Mercury":
                return MERCURY;
            case "venus":
                return VENUS;
            case "Venus":
                return VENUS;
            case "earth":
                return EARTH;
            case "Earth":
                return EARTH;
            case "mars":
                return MARS;
            case "Mars":
                return MARS;
            case "jupiter":
                return JUPITER;
            case "Jupiter":
                return JUPITER;
            case "saturn":
                return SATURN;
            case "Saturn":
                return SATURN;
            case "uranus":
                return URANUS;
            case "Uranus":
                return URANUS;
            case "neptune":
                return NEPTUNE;
            case "Neptune":
                return NEPTUNE;
            case "moon":
                return MOON;
            case "Moon":
                return MOON;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Get cached values array without cloning. Use this instead of values() in loops to avoid array
     * cloning.
     */
    public static CelestialBodyId[] getCachedValues() {
        return VALUES;
    }
}
