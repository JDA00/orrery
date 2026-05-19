package com.jda.orrery.core.frames;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IAURotationModels#getSpinAxisJ2000}, which writes a body's spin axis (J2000
 * frame) into a caller-supplied destination vector.
 *
 * The Saturn pole reference point is from the IAU 2015 Working Group: north pole RA ≈ 40.589°,
 * Dec ≈ 83.537° at J2000.
 */
public class IAURotationModelsTest {

    /** Julian Date at the J2000 epoch (TDB). */
    private static final double J2000_JD = 2451545.0;

    /** Acceptable tolerance for unit-vector components and angles. */
    private static final double TOL = 1e-3;

    @Test
    @DisplayName("getSpinAxisJ2000 returns a unit vector for Saturn")
    void saturnAxisIsUnitVector() {
        Vector3d dest = new Vector3d();
        Vector3d result = IAURotationModels.getSpinAxisJ2000("saturn", J2000_JD, dest);
        assertNotNull(result);
        assertSame(dest, result, "Helper must return the destination vector for chaining");
        double len = dest.length();
        assertEquals(1.0, len, TOL, "Spin axis must be unit length");
    }

    @Test
    @DisplayName("Saturn declination at J2000 is ≈ 83.5° (IAU 2015 north pole)")
    void saturnDeclinationMatchesIAU() {
        Vector3d dest = new Vector3d();
        IAURotationModels.getSpinAxisJ2000("saturn", J2000_JD, dest);
        // Declination = arcsin(z) for a unit vector in equatorial coords.
        double decDeg = Math.toDegrees(Math.asin(dest.z));
        // IAU 2015 Saturn pole Dec at J2000: 83.537° ± epoch drift.
        assertEquals(
                83.537,
                decDeg,
                0.1,
                "Saturn declination should match IAU 2015 Working Group value");
    }

    @Test
    @DisplayName("Earth pole has high declination (close to north celestial pole)")
    void earthPoleSanity() {
        Vector3d dest = new Vector3d();
        Vector3d result = IAURotationModels.getSpinAxisJ2000("earth", J2000_JD, dest);
        assertNotNull(result, "Earth must have rotation parameters");
        double decDeg = Math.toDegrees(Math.asin(dest.z));
        // Earth's pole declination is +90° in equatorial coords, but we're
        // in J2000 inertial frame — the J2000 pole is by definition along Z.
        // So Earth's spin axis (close to J2000 Z) should have Dec close to 90°.
        assertTrue(decDeg > 89.0, "Earth pole should be near +90° declination, got " + decDeg);
    }

    @Test
    @DisplayName("Returns null for unknown body without writing to destination")
    void unknownBodyReturnsNull() {
        Vector3d dest = new Vector3d(7.0, 8.0, 9.0); // sentinel values
        Vector3d result = IAURotationModels.getSpinAxisJ2000("nonexistent_body", J2000_JD, dest);
        assertNull(result);
        // dest should be unmodified
        assertEquals(7.0, dest.x, 0.0);
        assertEquals(8.0, dest.y, 0.0);
        assertEquals(9.0, dest.z, 0.0);
    }

    @Test
    @DisplayName("Zero-alloc contract: caller-supplied dest is returned, not a new vector")
    void destIsReturnedNotANewVector() {
        Vector3d dest = new Vector3d();
        Vector3d result = IAURotationModels.getSpinAxisJ2000("mars", J2000_JD, dest);
        assertSame(dest, result, "Method must return the caller's dest, not allocate");
    }
}
