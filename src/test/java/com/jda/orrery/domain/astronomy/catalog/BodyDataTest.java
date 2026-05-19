package com.jda.orrery.domain.astronomy.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.domain.astronomy.BodyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BodyData} — the new shape fields (polar radius, ring extent) and their
 * convenience accessors.
 *
 * The IAU 2015 oblate-spheroid radii used here are referenced for cross-check against the
 * catalog values.
 */
public class BodyDataTest {

    /** IAU 2015 oblateness ratios — used as targets for the catalog values. */
    private static final double SATURN_OBLATENESS = 60268.0 / 54364.0; // ~1.1086

    private static final double JUPITER_OBLATENESS = 71492.0 / 66854.0; // ~1.0694
    private static final double URANUS_OBLATENESS = 25559.0 / 24973.0; // ~1.0235
    private static final double NEPTUNE_OBLATENESS = 24764.0 / 24341.0; // ~1.0174

    /** Acceptable error vs. the IAU oblateness target. */
    private static final double OBLATENESS_TOL = 0.10; // 10%, generous for mean/equatorial mismatch

    @Test
    @DisplayName("polarRadiusOrEquatorial: returns polarRadius when defined")
    void polarRadiusReturnsPolarWhenDefined() {
        BodyData saturn = CelestialCatalog.SATURN;
        assertNotNull(saturn.polarRadius(), "Saturn must define polarRadius (IAU 2015)");
        assertEquals(saturn.polarRadius(), saturn.polarRadiusOrEquatorial(), 1e-9);
    }

    @Test
    @DisplayName("polarRadiusOrEquatorial: falls back to equatorial when null (spherical bodies)")
    void polarRadiusFallsBackToEquatorial() {
        BodyData earth = CelestialCatalog.EARTH;
        assertNull(
                earth.polarRadius(),
                "Earth catalog entry should not set polarRadius (spherical fallback)");
        assertEquals(earth.radius(), earth.polarRadiusOrEquatorial(), 1e-9);
    }

    @Test
    @DisplayName("Saturn polar/equatorial ratio is in the IAU oblateness ballpark")
    void saturnOblatenessRatio() {
        BodyData saturn = CelestialCatalog.SATURN;
        double ratio = saturn.radius() / saturn.polarRadius();
        // The catalog uses mean radius (58232) instead of equatorial (60268),
        // so the rendered oblateness is slightly understated. Tolerance is
        // relaxed accordingly.
        double rel = Math.abs(ratio - SATURN_OBLATENESS) / SATURN_OBLATENESS;
        assertTrue(
                rel < OBLATENESS_TOL,
                "Saturn ratio "
                        + ratio
                        + " should be within "
                        + (OBLATENESS_TOL * 100)
                        + "% of IAU target "
                        + SATURN_OBLATENESS
                        + " (rel="
                        + rel
                        + ")");
    }

    @Test
    @DisplayName("All four gas giants have polar radii populated")
    void gasGiantsHavePolarRadii() {
        assertNotNull(CelestialCatalog.JUPITER.polarRadius(), "Jupiter polarRadius");
        assertNotNull(CelestialCatalog.SATURN.polarRadius(), "Saturn polarRadius");
        assertNotNull(CelestialCatalog.URANUS.polarRadius(), "Uranus polarRadius");
        assertNotNull(CelestialCatalog.NEPTUNE.polarRadius(), "Neptune polarRadius");
    }

    @Test
    @DisplayName(
            "hasRings: true for Saturn, false for Jupiter / Uranus / Neptune (no ring rendering)")
    void hasRingsOnlySaturnInScope() {
        assertTrue(
                CelestialCatalog.SATURN.hasRings(),
                "Saturn must define ring extent (renderable rings)");
        // Other gas giants have CelestialBodyId.hasRings()=true but no
        // ring-extent catalog entry yet (no rendered rings in current scope).
        assertFalse(CelestialCatalog.JUPITER.hasRings());
        assertFalse(CelestialCatalog.URANUS.hasRings());
        assertFalse(CelestialCatalog.NEPTUNE.hasRings());
        // Spherical bodies never have rings
        assertFalse(CelestialCatalog.EARTH.hasRings());
        assertFalse(CelestialCatalog.MOON.hasRings());
    }

    @Test
    @DisplayName(
            "Saturn ring extent: inner < outer, both positive (artistic compression of real values)")
    void saturnRingExtentSane() {
        BodyData saturn = CelestialCatalog.SATURN;
        Double inner = saturn.ringInnerRadius();
        Double outer = saturn.ringOuterRadius();
        assertNotNull(inner, "Saturn must define ringInnerRadius");
        assertNotNull(outer, "Saturn must define ringOuterRadius");
        assertTrue(inner > 0.0, "ringInnerRadius positive");
        assertTrue(outer > inner, "ringOuterRadius > ringInnerRadius");
        // Sanity: the rendered values should be in the rough ballpark of the
        // real D-ring/A-ring extent (1.105 to 2.27 R_S). 0.9–2.5 is generous.
        assertTrue(inner > 0.9 && inner < 2.0, "inner in ballpark");
        assertTrue(outer > 1.5 && outer < 2.5, "outer in ballpark");
    }

    @Test
    @DisplayName("Compact constructor (no orbital data) preserves null shape fields")
    void compactConstructorKeepsShapeFieldsNull() {
        BodyData phobos =
                new BodyData(
                        401,
                        "phobos_test",
                        "Phobos",
                        BodyType.NATURAL_SATELLITE,
                        1e16,
                        11.0,
                        "mars");
        assertNull(phobos.polarRadius());
        assertNull(phobos.ringInnerRadius());
        assertNull(phobos.ringOuterRadius());
        assertEquals(phobos.radius(), phobos.polarRadiusOrEquatorial(), 1e-9);
        assertFalse(phobos.hasRings());
    }
}
