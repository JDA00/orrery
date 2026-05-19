package com.jda.orrery.graphics.ubo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pinning test for the {@link CelestialUBO} buffer size after the Group 2 vec4 bodyGeometry
 * migration (368 → 384 bytes).
 *
 * The buffer size and offset layout must stay in sync with the shader's std140 declaration in
 * celestial_unified_ubo.{vert,frag}. Constructing the UBO requires a GL context; this test only
 * locks the static constant.
 */
public class CelestialUBOTest {

    @Test
    @DisplayName("BUFFER_SIZE = 384 (368-byte original + 16-byte vec4 bodyGeometry)")
    void bufferSizeMatchesStd140Layout() {
        assertEquals(
                384,
                CelestialUBO.BUFFER_SIZE,
                "UBO buffer size must remain 384. If you change this, update the "
                        + "std140 CelestialData block in both celestial_unified_ubo.vert "
                        + "and celestial_unified_ubo.frag to match.");
    }

    @Test
    @DisplayName("BUFFER_SIZE is 16-byte aligned (std140 requirement)")
    void bufferSizeIs16ByteAligned() {
        assertEquals(
                0,
                CelestialUBO.BUFFER_SIZE % 16,
                "std140 layouts must round up to a multiple of 16");
    }
}
