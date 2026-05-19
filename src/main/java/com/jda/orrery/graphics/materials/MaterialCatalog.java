package com.jda.orrery.graphics.materials;

import com.jda.orrery.core.logging.Logging;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.joml.Vector4f;

/**
 * Catalog of measured physical properties for celestial bodies (albedo, roughness, emission, …).
 * Properties are treated as immutable data. Follows the {@link java.lang.Math}-style pattern:
 * final class with private constructor, static access to per-body properties. Per-body
 * {@code .source(...)} builder calls record the provenance of each value.
 */
public final class MaterialCatalog {
    private static final Logger LOGGER = Logging.logger(MaterialCatalog.class);

    // Private constructor - prevent instantiation
    private MaterialCatalog() {
        throw new AssertionError("MaterialCatalog is a constants repository - do not instantiate");
    }

    // Immutable per-body material properties; provenance lives on each entry via .source(...).
    private static final Map<String, MaterialProperties> MATERIALS =
            Collections.unmodifiableMap(buildCatalog());

    // Default material for unknown bodies
    private static final MaterialProperties DEFAULT_MATERIAL =
            MaterialProperties.builder()
                    .albedo(0.1f, 0.1f, 0.1f) // Dark gray - typical asteroid
                    .roughness(0.9f)
                    .metallic(0.0f)
                    .source("Default")
                    .build();

    /** Build the per-body material catalog at class load time. */
    private static Map<String, MaterialProperties> buildCatalog() {
        Map<String, MaterialProperties> catalog = new HashMap<>();

        // Star
        catalog.put(
                "sun",
                MaterialProperties.builder()
                        .albedo(1.0f, 1.0f, 1.0f)
                        .roughness(0.0f)
                        .metallic(0.0f)
                        .emission(
                                1.0f, 0.956f,
                                0.839f) // 5778K blackbody in sRGB (NASA solar spectrum)
                        .emissionStrength(1.0f) // Base emission (HDR boost in shader)
                        .source("NASA Solar Dynamics Observatory")
                        .lastUpdated("2024-01-01")
                        .notes("5778K effective temperature, limb darkening u=0.6")
                        .build());

        // Terrestrial planets
        catalog.put(
                "mercury",
                MaterialProperties.builder()
                        .albedo(0.142f, 0.142f, 0.142f) // NASA geometric albedo 0.142
                        .roughness(0.9f) // Very rough, no atmosphere
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .notes("Bond albedo: 0.068, heavily cratered regolith")
                        .build());

        catalog.put(
                "venus",
                MaterialProperties.builder()
                        .albedo(0.689f, 0.689f, 0.689f) // NASA geometric albedo 0.689
                        .roughness(0.15f) // Very smooth sulfuric acid clouds
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .notes("Bond albedo: 0.77, H2SO4 clouds, highest albedo")
                        .build());

        catalog.put(
                "earth",
                MaterialProperties.builder()
                        .albedo(0.434f, 0.434f, 0.434f) // NASA geometric albedo 0.434
                        .roughness(0.5f) // Mixed surfaces (ocean/land/clouds)
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .lastUpdated("2024-01-01")
                        .notes("Bond albedo: 0.306, composite: 71% ocean, 29% land")
                        .build());

        catalog.put(
                "mars",
                MaterialProperties.builder()
                        .albedo(0.170f, 0.130f, 0.100f) // NASA geometric albedo 0.17, red tinted
                        .roughness(0.8f) // Dusty, rocky surface
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .notes("Bond albedo: 0.25, Fe2O3 dust gives red color")
                        .build());

        // Natural satellites
        catalog.put(
                "moon",
                MaterialProperties.builder()
                        .albedo(0.12f, 0.12f, 0.12f) // NASA geometric albedo 0.12 (very dark)
                        .roughness(0.95f) // Extremely rough from micrometeorite impacts
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .notes("Bond albedo: 0.11, anorthosite highlands, basaltic maria")
                        .build());

        // Gas giants
        catalog.put(
                "jupiter",
                MaterialProperties.builder()
                        .albedo(0.52f, 0.47f, 0.39f) // NASA geometric albedo 0.52, banded colors
                        .roughness(0.15f) // Smooth gas layers, reduced specular
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet / Cassini")
                        .notes("Bond albedo: 0.503 (Cassini), NH3 ice crystals")
                        .build());

        catalog.put(
                "saturn",
                MaterialProperties.builder()
                        .albedo(0.47f, 0.44f, 0.36f) // NASA geometric albedo 0.47, pale gold
                        .roughness(0.2f) // Smooth atmosphere, reduced specular
                        .metallic(0.0f)
                        // Limb refraction broadens the ring penumbra. ~0.4° (0.007 rad) is
                        // the Lindal 1985 / Schinder 2011 midpoint for the visible cloud-top
                        // altitude. The geometric solar contribution at Saturn is ~4.87e-4
                        // rad, so atmosphere dominates the visible penumbra ~14×.
                        .atmosphericRefraction(0.007f)
                        .source("NASA Planetary Fact Sheet")
                        .lastUpdated("2017-09-15") // End of Cassini mission
                        .notes("Bond albedo: 0.342, less NH3 than Jupiter")
                        .build());

        // Ice giants
        catalog.put(
                "uranus",
                MaterialProperties.builder()
                        .albedo(0.51f, 0.55f, 0.58f) // NASA geometric albedo 0.51, cyan tint
                        .roughness(0.05f) // Very smooth, few features
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .lastUpdated("1986-01-24") // Voyager 2 encounter
                        .notes("Bond albedo: 0.30, CH4 absorbs red, reflects blue-green")
                        .build());

        catalog.put(
                "neptune",
                MaterialProperties.builder()
                        .albedo(0.41f, 0.45f, 0.52f) // NASA geometric albedo 0.41, deep blue
                        .roughness(0.05f) // Very smooth
                        .metallic(0.0f)
                        .source("NASA Planetary Fact Sheet")
                        .lastUpdated("1989-08-25") // Voyager 2 encounter
                        .notes("Bond albedo: 0.29, more methane than Uranus")
                        .build());

        // Dwarf planets
        catalog.put(
                "pluto",
                MaterialProperties.builder()
                        .albedo(0.52f, 0.50f, 0.48f) // Bright nitrogen ice
                        .roughness(0.3f) // Mixed ice/rock terrain
                        .metallic(0.0f)
                        .source("NASA New Horizons")
                        .lastUpdated("2015-07-14") // New Horizons encounter
                        .notes("N2, CH4, and CO ices")
                        .build());

        // Galilean moons
        catalog.put(
                "io",
                MaterialProperties.builder()
                        .albedo(0.63f, 0.60f, 0.45f) // Sulfur compounds
                        .roughness(0.7f) // Volcanic surface
                        .metallic(0.0f)
                        .source("NASA Galileo Mission")
                        .notes("Active volcanism, sulfur dioxide frost")
                        .build());

        catalog.put(
                "europa",
                MaterialProperties.builder()
                        .albedo(0.67f, 0.67f, 0.67f) // Water ice
                        .roughness(0.2f) // Smooth ice shell
                        .metallic(0.0f)
                        .source("NASA Galileo Mission")
                        .notes("Subsurface ocean beneath ice shell")
                        .build());

        catalog.put(
                "ganymede",
                MaterialProperties.builder()
                        .albedo(0.43f, 0.43f, 0.43f) // Dirty ice
                        .roughness(0.4f) // Mixed terrain
                        .metallic(0.0f)
                        .source("NASA Galileo Mission")
                        .notes("Largest moon, differentiated interior")
                        .build());

        catalog.put(
                "callisto",
                MaterialProperties.builder()
                        .albedo(0.17f, 0.17f, 0.17f) // Dark, ancient surface
                        .roughness(0.8f) // Heavily cratered
                        .metallic(0.0f)
                        .source("NASA Galileo Mission")
                        .notes("Most heavily cratered object in solar system")
                        .build());

        // Saturn's rings
        catalog.put(
                "saturn_rings",
                MaterialProperties.builder()
                        .albedo(
                                0.85f, 0.78f,
                                0.65f) // Cassini: Higher geometric albedo, warm golden tones from
                        // tholins
                        .roughness(0.15f) // Smooth ice crystals
                        .metallic(0.0f)
                        // Ring-specific optical properties from Cassini UVIS/VIMS
                        .opticalDepth(1.5f) // B ring typical optical depth at normal incidence
                        .ringScattering(
                                0.6f, -0.65f, 0.3f) // Forward g, backward g, 30% small particles
                        .saturnshine(0.342f) // Saturn's bond albedo (inter-ring scattering computed
                        // analytically in-shader)
                        .source("NASA Cassini Mission")
                        .lastUpdated("2017-09-15") // End of Cassini
                        .notes(
                                "99.9% water ice with organic compounds, optical depth: B ring 1.5-2.5, A ring 0.4-0.6, C ring 0.05-0.12")
                        .build());

        // Saturn's moons
        catalog.put(
                "titan",
                MaterialProperties.builder()
                        .albedo(0.22f, 0.20f, 0.18f) // Thick hazy atmosphere
                        .roughness(0.3f) // Obscured surface
                        .metallic(0.0f)
                        .source("NASA Cassini-Huygens")
                        .notes("Dense N2/CH4 atmosphere, hydrocarbon lakes")
                        .build());

        catalog.put(
                "enceladus",
                MaterialProperties.builder()
                        .albedo(1.0f, 1.0f, 1.0f) // Nearly pure water ice
                        .roughness(0.1f) // Very smooth, young surface
                        .metallic(0.0f)
                        .source("NASA Cassini")
                        .notes("Active geysers from subsurface ocean")
                        .build());

        LOGGER.fine(String.format("Built catalog with %d bodies", catalog.size()));
        return catalog;
    }

    /**
     * Get material properties for a celestial body.
     *
     * Material properties are universal physical constants, not instance data.
     *
     * @param bodyId Body identifier (must be lowercase)
     * @return Material properties, or default if not found
     */
    public static MaterialProperties getMaterial(String bodyId) {
        if (bodyId == null) {
            return DEFAULT_MATERIAL;
        }

        // Expect lowercase body IDs (caller normalizes) to avoid allocation.
        MaterialProperties material = MATERIALS.get(bodyId);

        if (material == null) {
            LOGGER.fine(String.format("No material found for '%s', using default", bodyId));
            return DEFAULT_MATERIAL;
        }

        return material;
    }

    /**
     * Check if a body is emissive (emits light).
     *
     * @param bodyId Body identifier
     * @return true if body emits light
     */
    public static boolean isEmissive(String bodyId) {
        MaterialProperties props = getMaterial(bodyId);
        return props.emissionStrength > 0.0f;
    }

    /**
     * Get emission properties for a body.
     *
     * @param bodyId Body identifier
     * @return Emission as Vector4f (RGB + strength)
     */
    public static Vector4f getEmission(String bodyId) {
        MaterialProperties props = getMaterial(bodyId);
        if (props.emissionStrength > 0.0f) {
            return new Vector4f(
                    props.emission.x, props.emission.y, props.emission.z, props.emissionStrength);
        }
        return new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Get all available body IDs in the catalog.
     *
     * @return Array of body identifiers
     */
    public static String[] getAvailableBodies() {
        return MATERIALS.keySet().toArray(new String[0]);
    }
}
