package com.jda.orrery.graphics.materials;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks the Saturn-specific material values that downstream shader code depends on. If any of these
 * change, the shader's penumbra width or the ring optics will shift visibly.
 */
public class MaterialCatalogTest {

    @Test
    @DisplayName("Saturn material has Lindal/Schinder atmospheric refraction (~0.4°)")
    void saturnAtmosphericRefraction() {
        MaterialProperties saturn = MaterialCatalog.getMaterial("saturn");
        assertNotNull(saturn, "Saturn material must exist");
        // 0.007 rad ≈ 0.4° — Lindal 1985 / Schinder 2011 midpoint for the
        // visible cloud-top altitude.
        assertEquals(0.007f, saturn.atmosphericRefractionRad, 1e-6f);
    }

    @Test
    @DisplayName("Non-atmosphere bodies default to 0 refraction")
    void airlessBodiesNoRefraction() {
        // Sun is emissive but should still have 0 refraction (vacuum).
        MaterialProperties sun = MaterialCatalog.getMaterial("sun");
        if (sun != null) {
            assertEquals(0.0f, sun.atmosphericRefractionRad, 0.0f);
        }
        MaterialProperties moon = MaterialCatalog.getMaterial("moon");
        if (moon != null) {
            assertEquals(0.0f, moon.atmosphericRefractionRad, 0.0f);
        }
    }

    @Test
    @DisplayName("Saturn rings have Cassini-derived optical properties")
    void saturnRingsCassiniValues() {
        MaterialProperties rings = MaterialCatalog.getMaterial("saturn_rings");
        assertNotNull(rings, "saturn_rings material must exist");
        // Henyey-Greenstein g values from Cassini ring photometry
        assertEquals(0.6f, rings.forwardScatteringG, 1e-6f);
        assertEquals(-0.65f, rings.backwardScatteringG, 1e-6f);
        assertEquals(0.3f, rings.particleMixRatio, 1e-6f);
        // Saturn's bond albedo (Voyager/Cassini)
        assertEquals(0.342f, rings.saturnshineAlbedo, 1e-6f);
    }
}
