package com.jda.orrery.graphics.materials;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Builder roundtrip tests for {@link MaterialProperties}, focused on the new atmospheric-refraction
 * field added for Saturn ring penumbra.
 */
public class MaterialPropertiesTest {

    @Test
    @DisplayName("atmosphericRefraction default is 0 (airless / vacuum)")
    void atmosphericRefractionDefaultZero() {
        MaterialProperties mat = MaterialProperties.builder().build();
        assertEquals(0.0f, mat.atmosphericRefractionRad, 0.0f);
    }

    @Test
    @DisplayName("atmosphericRefraction roundtrips through builder")
    void atmosphericRefractionRoundtrip() {
        MaterialProperties mat = MaterialProperties.builder().atmosphericRefraction(0.012f).build();
        assertEquals(0.012f, mat.atmosphericRefractionRad, 0.0f);
    }

    @Test
    @DisplayName("atmosphericRefraction clamps negatives to 0")
    void atmosphericRefractionClampsNegative() {
        MaterialProperties mat = MaterialProperties.builder().atmosphericRefraction(-0.5f).build();
        assertEquals(0.0f, mat.atmosphericRefractionRad, 0.0f);
    }

    @Test
    @DisplayName("Existing ring fields and atmospheric refraction coexist on one material")
    void atmosphericRefractionCoexistsWithRingFields() {
        MaterialProperties mat =
                MaterialProperties.builder()
                        .opticalDepth(1.5f)
                        .ringScattering(0.6f, -0.65f, 0.3f)
                        .saturnshine(0.342f)
                        .atmosphericRefraction(0.007f)
                        .build();
        assertEquals(1.5f, mat.opticalDepthNormal, 0.0f);
        assertEquals(0.6f, mat.forwardScatteringG, 0.0f);
        assertEquals(-0.65f, mat.backwardScatteringG, 0.0f);
        assertEquals(0.3f, mat.particleMixRatio, 0.0f);
        assertEquals(0.342f, mat.saturnshineAlbedo, 0.0f);
        assertEquals(0.007f, mat.atmosphericRefractionRad, 0.0f);
    }
}
