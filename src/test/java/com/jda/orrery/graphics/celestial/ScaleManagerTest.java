package com.jda.orrery.graphics.celestial;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.BodyType;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ScaleManager}'s piecewise scaling math.
 *
 * Pins current behavior — including the three known discontinuities in {@link
 * ScaleManager#getVisualRadius} at {@code earthScale} boundaries 0.1, 0.5, and 2.0. Those
 * discontinuities are recorded here, not asserted against, so future refactors have a reference for
 * what "current" means.
 */
public class ScaleManagerTest {

    private static final double EPSILON = 1e-9;
    private static final double EARTH_RADIUS_KM = 6371.0;

    // ────────────────────────────────────────────────────────────────────
    // getVisualDistance — single power law, distance^0.6 * 100
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVisualDistance at 1 AU returns 100")
    void distance_1AU_returnsExpectedScale() {
        assertEquals(100.0, ScaleManager.getVisualDistance(1.0), EPSILON);
    }

    @Test
    @DisplayName("getVisualDistance at 30 AU matches pow(30, 0.6) * 100")
    void distance_30AU_matchesFormula() {
        double expected = Math.pow(30.0, 0.6) * 100.0;
        assertEquals(expected, ScaleManager.getVisualDistance(30.0), EPSILON);
    }

    @Test
    @DisplayName("getVisualDistance returns 0 at origin (distance < 0.001)")
    void distance_nearZero_returnsZero() {
        assertEquals(0.0, ScaleManager.getVisualDistance(0.0), EPSILON);
        assertEquals(0.0, ScaleManager.getVisualDistance(0.0005), EPSILON);
    }

    @ParameterizedTest(name = "distance {0} AU < distance {1} AU")
    @CsvSource({"0.1, 0.4", "0.4, 1.0", "1.0, 5.2", "5.2, 30.0", "30.0, 100.0"})
    @DisplayName("getVisualDistance is monotonically increasing")
    void distance_isMonotonicallyIncreasing(double smaller, double larger) {
        assertTrue(
                ScaleManager.getVisualDistance(smaller) < ScaleManager.getVisualDistance(larger),
                () -> "scale(" + smaller + ") should be < scale(" + larger + ")");
    }

    @Test
    @DisplayName(
            "getVisualDistance gives expected planetary ordering (Mercury < Earth < Jupiter < Neptune)")
    void distance_planetOrdering() {
        double mercury = ScaleManager.getVisualDistance(0.39);
        double earth = ScaleManager.getVisualDistance(1.0);
        double jupiter = ScaleManager.getVisualDistance(5.2);
        double neptune = ScaleManager.getVisualDistance(30.0);

        assertTrue(mercury < earth);
        assertTrue(earth < jupiter);
        assertTrue(jupiter < neptune);
    }

    // ────────────────────────────────────────────────────────────────────
    // getVisualRadius — 6-region piecewise, plus Sun/Moon special cases.
    // Takes a string bodyType (lowercase key) for the special cases.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVisualRadius for Sun uses log10 formula with 3.0 factor")
    void radius_sunSpecialCase() {
        double sunRadius = 695700.0;
        double expected = Math.log10(sunRadius / EARTH_RADIUS_KM) * 3.0;

        assertEquals(expected, ScaleManager.getVisualRadius(sunRadius, "sun"), EPSILON);
        // Case-insensitive per implementation
        assertEquals(expected, ScaleManager.getVisualRadius(sunRadius, "SUN"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius for Moon uses ratio * 2.0 formula")
    void radius_moonSpecialCase() {
        double moonRadius = 1737.4;
        double expected = (moonRadius / EARTH_RADIUS_KM) * 2.0;

        assertEquals(expected, ScaleManager.getVisualRadius(moonRadius, "moon"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius for Earth (ratio 1.0) lands in 0.5-2.0 region: scale * 1.5")
    void radius_earth_landsInMiddleRegion() {
        // earthScale = 6371 / 6371 = 1.0 → middle region: 1.0 * 1.5 = 1.5
        assertEquals(1.5, ScaleManager.getVisualRadius(EARTH_RADIUS_KM, "earth"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius for Jupiter (ratio ~11) lands in gas-giant region")
    void radius_jupiter_landsInOuterRegion() {
        double jupiterRadius = 69911.0;
        double earthScale = jupiterRadius / EARTH_RADIUS_KM; // ~10.97
        double expected = 3.0 + Math.log10(earthScale) * 4.0;

        assertEquals(expected, ScaleManager.getVisualRadius(jupiterRadius, "jupiter"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius for Mercury (ratio ~0.38) lands in small-planet region")
    void radius_mercury_landsInInnerRegion() {
        double mercuryRadius = 2439.7;
        double earthScale = mercuryRadius / EARTH_RADIUS_KM; // ~0.383
        // 0.1 ≤ scale < 0.5 → 0.5 + scale * 2.0
        double expected = 0.5 + earthScale * 2.0;

        assertEquals(expected, ScaleManager.getVisualRadius(mercuryRadius, "mercury"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius for very small body (ratio < 0.1) uses log-of-km formula")
    void radius_verySmallBody_usesLogFormula() {
        double radiusKm = 500.0; // earthScale ≈ 0.078, below 0.1 threshold
        double expected = 0.5 + Math.log10(radiusKm / 1000.0) * 0.2;

        assertEquals(expected, ScaleManager.getVisualRadius(radiusKm, "asteroid"), EPSILON);
    }

    @Test
    @DisplayName("getVisualRadius has documented discontinuities at region boundaries")
    void radius_regionBoundaries_areDiscontinuous() {
        // These three boundaries currently produce visible jumps in visual
        // radius. The test pins current behavior; if boundaries are made
        // continuous in a future refactor, this test will flag the change.
        double tiny = 1e-9;

        // Boundary 1: earthScale = 0.1 (radius = 637.1 km)
        double belowB1 = ScaleManager.getVisualRadius(637.1 - tiny, "x");
        double aboveB1 = ScaleManager.getVisualRadius(637.1 + tiny, "x");
        assertTrue(
                Math.abs(aboveB1 - belowB1) > 0.1,
                "Boundary at earthScale=0.1 is discontinuous (gap > 0.1)");

        // Boundary 2: earthScale = 0.5 (radius = 3185.5 km)
        double belowB2 = ScaleManager.getVisualRadius(3185.5 - tiny, "x");
        double aboveB2 = ScaleManager.getVisualRadius(3185.5 + tiny, "x");
        assertTrue(
                Math.abs(belowB2 - aboveB2) > 0.5,
                "Boundary at earthScale=0.5 is discontinuous (gap > 0.5)");

        // Boundary 3: earthScale = 2.0 (radius = 12742 km)
        double belowB3 = ScaleManager.getVisualRadius(12742.0 - tiny, "x");
        double aboveB3 = ScaleManager.getVisualRadius(12742.0 + tiny, "x");
        assertTrue(
                Math.abs(aboveB3 - belowB3) > 0.5,
                "Boundary at earthScale=2.0 is discontinuous (gap > 0.5)");
    }

    // ────────────────────────────────────────────────────────────────────
    // getSatelliteDistanceMultiplier
    // ────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → multiplier {1}")
    @CsvSource({
        "moon,     10000.0",
        "phobos,   50000.0",
        "deimos,   30000.0",
        "io,        8000.0",
        "europa,    8000.0",
        "ganymede,  8000.0",
        "callisto,  8000.0"
    })
    @DisplayName("getSatelliteDistanceMultiplier returns configured multiplier per known body")
    void satelliteMultiplier_returnsConfiguredValue(String satelliteId, double expected) {
        assertEquals(expected, ScaleManager.getSatelliteDistanceMultiplier(satelliteId), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"titan", "triton", "nonexistent-moon", ""})
    @DisplayName("getSatelliteDistanceMultiplier returns 15000 default for unknown IDs")
    void satelliteMultiplier_unknownSatellite_returnsDefault(String satelliteId) {
        assertEquals(15000.0, ScaleManager.getSatelliteDistanceMultiplier(satelliteId), EPSILON);
    }

    // ────────────────────────────────────────────────────────────────────
    // scaleBodyPositionInto — routes planets through getVisualDistance and
    // asteroids through the precomputed LUT.
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("scaleBodyPositionInto for planet writes visual-distance-scaled position")
    void scaleBodyPositionInto_planet_usesVisualDistance() {
        CelestialBody planet = new TestBody(BodyType.PLANET);
        Vector3d input = new Vector3d(1.0, 0.0, 0.0); // 1 AU along X
        Vector3d output = new Vector3d();

        ScaleManager.scaleBodyPositionInto(planet, input, output);

        // Visual distance at 1 AU = 100, direction preserved
        assertEquals(100.0, output.x, EPSILON);
        assertEquals(0.0, output.y, EPSILON);
        assertEquals(0.0, output.z, EPSILON);
    }

    @Test
    @DisplayName("scaleBodyPositionInto preserves direction of input vector")
    void scaleBodyPositionInto_preservesDirection() {
        CelestialBody planet = new TestBody(BodyType.PLANET);
        Vector3d input = new Vector3d(1.0, 2.0, 3.0);
        double inputMag = input.length();
        Vector3d output = new Vector3d();

        ScaleManager.scaleBodyPositionInto(planet, input, output);

        // Direction preserved: output should be input * (outputMag / inputMag)
        double outputMag = output.length();
        double scale = outputMag / inputMag;
        assertEquals(input.x * scale, output.x, EPSILON);
        assertEquals(input.y * scale, output.y, EPSILON);
        assertEquals(input.z * scale, output.z, EPSILON);
    }

    @Test
    @DisplayName("scaleBodyPositionInto at origin writes (0, 0, 0)")
    void scaleBodyPositionInto_atOrigin_writesZero() {
        CelestialBody planet = new TestBody(BodyType.PLANET);
        Vector3d input = new Vector3d(0.0, 0.0, 0.0);
        Vector3d output = new Vector3d(99, 99, 99); // non-zero initial

        ScaleManager.scaleBodyPositionInto(planet, input, output);

        assertEquals(0.0, output.x, EPSILON);
        assertEquals(0.0, output.y, EPSILON);
        assertEquals(0.0, output.z, EPSILON);
    }

    @Test
    @DisplayName("scaleBodyPositionInto for asteroid at 0.05 AU uses LUT (returns 10.0 floor)")
    void scaleBodyPositionInto_asteroid_usesLUTFloor() {
        CelestialBody asteroid = new TestBody(BodyType.ASTEROID);
        Vector3d input = new Vector3d(0.05, 0.0, 0.0); // 0.05 AU, below LUT floor of 0.1 AU
        Vector3d output = new Vector3d();

        ScaleManager.scaleBodyPositionInto(asteroid, input, output);

        // LUT[0] = 10.0 for distance < 0.1 AU. Planet path would give ≈17.
        assertEquals(10.0, output.length(), 1e-6);
    }

    @Test
    @DisplayName("scaleBodyPositionInto asteroid LUT matches planet power-law at 1 AU")
    void scaleBodyPositionInto_asteroid_matchesPlanetAt1AU() {
        Vector3d input = new Vector3d(1.0, 0.0, 0.0);
        Vector3d planetOutput = new Vector3d();
        Vector3d asteroidOutput = new Vector3d();

        ScaleManager.scaleBodyPositionInto(new TestBody(BodyType.PLANET), input, planetOutput);
        ScaleManager.scaleBodyPositionInto(new TestBody(BodyType.ASTEROID), input, asteroidOutput);

        // LUT uses the same formula as planets for distance ≥ 0.1 AU, so
        // magnitudes should match at 1 AU (within float→double conversion).
        assertEquals(planetOutput.length(), asteroidOutput.length(), 1e-5);
    }

    @Test
    @DisplayName("scaleBodyPositionInto writes to the provided output (no allocation)")
    void scaleBodyPositionInto_writesInPlace() {
        CelestialBody planet = new TestBody(BodyType.PLANET);
        Vector3d input = new Vector3d(1.0, 0.0, 0.0);
        Vector3d providedOutput = new Vector3d();

        ScaleManager.scaleBodyPositionInto(planet, input, providedOutput);

        // The same Vector3d instance we passed in should now hold the result.
        assertTrue(providedOutput.length() > 0, "output should be written, not untouched");
    }

    // ────────────────────────────────────────────────────────────────────
    // Test fixtures
    // ────────────────────────────────────────────────────────────────────

    /**
     * Minimal {@link CelestialBody} stub for tests. Only {@code getBodyType} is implemented — every
     * other method throws to catch unintended dependencies from {@link ScaleManager}.
     */
    private static final class TestBody implements CelestialBody {
        private final BodyType bodyType;

        TestBody(BodyType bodyType) {
            this.bodyType = bodyType;
        }

        @Override
        public BodyType getBodyType() {
            return bodyType;
        }

        @Override
        public String getId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CelestialBody> getParent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CelestialBody> getChildren() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addChild(CelestialBody child) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getMass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getRadius() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FramedState getState(TimeContext time) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EphemerisProvider getEphemerisProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getReferenceFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getAccuracy(TimeContext time) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] getValidTimeRange() {
            throw new UnsupportedOperationException();
        }
    }
}
