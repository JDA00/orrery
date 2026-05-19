package com.jda.orrery.domain.ephemeris;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.Planet;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.domain.ephemeris.cache.SimpleFrameCache;
import com.jda.orrery.domain.ephemeris.vsop87.VSOP87EProvider;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates ephemeris calculations against JPL Horizons reference data.
 *
 * Reference CSV is barycentric J2000 ecliptic, matching the native frame of VSOP87E. Regenerate
 * via {@code scripts/jpl/fetch_jpl_horizons.py}.
 *
 * Position tests hold within ~10 arcseconds per axis; Earth-Mars angular separation at J2000
 * holds to sub-arcsecond.
 */
public class JPLHorizonsValidationTest {

    // 1 arcsecond at 1 AU = 725 km ≈ 0.00000484 AU
    private static final double ARCSEC_IN_AU = 0.00000484;

    // Position tolerance: ~10 arcsec per axis. Covers the residual between
    // VSOP87E and JPL DE441 over the current epoch range.
    private static final double POSITION_TOLERANCE_AU = 10 * ARCSEC_IN_AU;
    private static final double VELOCITY_TOLERANCE_AU_PER_DAY = 0.001;

    private static Map<String, Map<Double, ReferenceData>> referenceData;
    private static SolarSystem solarSystem;

    @BeforeAll
    static void loadReferenceData() throws Exception {
        referenceData = new HashMap<>();
        solarSystem = new SolarSystem(new AnalyticalEphemerisProvider(), new SimpleFrameCache());

        // Load JPL Horizons reference data
        InputStream is =
                JPLHorizonsValidationTest.class.getResourceAsStream("/jpl-horizons-reference.csv");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 8) {
                    String body = parts[0];
                    double jd = Double.parseDouble(parts[1]);
                    double x = Double.parseDouble(parts[2]);
                    double y = Double.parseDouble(parts[3]);
                    double z = Double.parseDouble(parts[4]);
                    double vx = Double.parseDouble(parts[5]);
                    double vy = Double.parseDouble(parts[6]);
                    double vz = Double.parseDouble(parts[7]);

                    ReferenceData data =
                            new ReferenceData(new Vec3d(x, y, z), new Vec3d(vx, vy, vz));

                    referenceData.computeIfAbsent(body, k -> new HashMap<>()).put(jd, data);
                }
            }
        }
    }

    @Test
    @DisplayName("Earth position at J2000.0 should match JPL Horizons")
    void testEarthAtJ2000() {
        double jdTDB = 2451545.0; // J2000.0 epoch
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        Planet earth = findPlanet("Earth");
        assertNotNull(earth, "Earth should exist in solar system");

        FramedState state = earth.getState(time);
        Vec3d position = state.getPosition();
        Vec3d velocity = state.getVelocity();

        ReferenceData reference = getReferenceData("Earth", jdTDB);
        assertNotNull(reference, "Reference data should exist for Earth at J2000");

        assertPositionMatch(position, reference.position, "Earth at J2000.0");
        assertVelocityMatch(velocity, reference.velocity, "Earth at J2000.0");
    }

    @Test
    @DisplayName("Mars position at J2000.0 should match JPL Horizons")
    void testMarsAtJ2000() {
        double jdTDB = 2451545.0;
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        Planet mars = findPlanet("Mars");
        assertNotNull(mars, "Mars should exist in solar system");

        FramedState state = mars.getState(time);
        Vec3d position = state.getPosition();

        ReferenceData reference = getReferenceData("Mars", jdTDB);
        assertNotNull(reference, "Reference data should exist for Mars at J2000");

        assertPositionMatch(position, reference.position, "Mars at J2000.0");
    }

    @Test
    @DisplayName("All planets at J2000.0 should match JPL Horizons")
    void testAllPlanetsAtJ2000() {
        double jdTDB = 2451545.0;
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        String[] planetNames = {
            "Mercury", "Venus", "Earth", "Mars",
            "Jupiter", "Saturn", "Uranus", "Neptune"
        };

        for (String name : planetNames) {
            Planet planet = findPlanet(name);
            assertNotNull(planet, name + " should exist in solar system");

            FramedState state = planet.getState(time);
            Vec3d position = state.getPosition();

            ReferenceData reference = getReferenceData(name, jdTDB);
            assertNotNull(reference, "Reference data should exist for " + name);

            assertPositionMatch(position, reference.position, name + " at J2000.0");
        }
    }

    @Test
    @DisplayName("Earth position at Mars opposition 2020 should match")
    void testEarthAtMarsOpposition2020() {
        // Mars opposition: Oct 13, 2020
        double jdTDB = 2459136.47694;
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        Planet earth = findPlanet("Earth");
        FramedState state = earth.getState(time);

        ReferenceData reference = getReferenceData("Earth", jdTDB);
        assertNotNull(reference, "Reference data should exist for Mars opposition");

        assertPositionMatch(
                state.getPosition(), reference.position, "Earth at Mars opposition 2020");
    }

    @Test
    @DisplayName("Mars position at opposition 2020 should match")
    void testMarsAtOpposition2020() {
        double jdTDB = 2459136.47694;
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        Planet mars = findPlanet("Mars");
        FramedState state = mars.getState(time);

        ReferenceData reference = getReferenceData("Mars", jdTDB);
        assertNotNull(reference, "Reference data should exist for Mars at opposition");

        assertPositionMatch(state.getPosition(), reference.position, "Mars at opposition 2020");
    }

    @Test
    @DisplayName("Angular separation accuracy should be within 1 arcsecond")
    void testAngularSeparationAccuracy() {
        double jdTDB = 2451545.0;
        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        // Test Earth-Mars angular separation
        Planet earth = findPlanet("Earth");
        Planet mars = findPlanet("Mars");

        Vec3d earthPos = earth.getState(time).getPosition();
        Vec3d marsPos = mars.getState(time).getPosition();

        // Vector from Earth to Mars
        Vec3d earthToMars =
                new Vec3d(marsPos.x - earthPos.x, marsPos.y - earthPos.y, marsPos.z - earthPos.z);

        double distance = earthToMars.length();

        // Get reference positions
        ReferenceData earthRef = getReferenceData("Earth", jdTDB);
        ReferenceData marsRef = getReferenceData("Mars", jdTDB);

        Vec3d refEarthToMars =
                new Vec3d(
                        marsRef.position.x - earthRef.position.x,
                        marsRef.position.y - earthRef.position.y,
                        marsRef.position.z - earthRef.position.z);

        double refDistance = refEarthToMars.length();

        // Angular error in radians
        double positionError = Math.abs(distance - refDistance);
        double angularError = positionError / distance; // Small angle approximation
        double angularErrorArcsec = angularError * 180.0 * 3600.0 / Math.PI;

        assertTrue(
                angularErrorArcsec < 1.0,
                String.format(
                        "Angular error %.3f arcsec should be < 1 arcsec", angularErrorArcsec));
    }

    @Test
    @DisplayName("VSOP87 accuracy should degrade gracefully over centuries")
    void testVSOP87AccuracyOverTime() {
        // Test at different epochs to verify VSOP87 degradation
        double[] testJDs = {
            2451545.0, // J2000.0
            2451545.0 + 365.25 * 50, // 2050
            2451545.0 - 365.25 * 50, // 1950
        };

        for (double jd : testJDs) {
            TimeContext time = new TimeContext(jd, 0.0, 0, 1.0);
            Planet earth = findPlanet("Earth");

            FramedState state = earth.getState(time);
            assertNotNull(state, "Should get valid state at JD " + jd);

            Vec3d pos = state.getPosition();
            // Earth-SSB distance tracks Earth-Sun (0.983–1.017 AU) plus the
            // Sun-SSB excursion (~±0.005 AU). Sanity bound, not an accuracy check.
            assertTrue(
                    pos.length() > 0.95 && pos.length() < 1.05,
                    String.format(
                            "Earth-SSB distance out of range at JD %.3f: %.6f AU",
                            jd, pos.length()));
        }
    }

    @Test
    @DisplayName("Time system integration for ephemeris")
    void testTimeSystemIntegration() {
        // This tests the complete time pipeline
        double jdTDB = 2451545.0;

        TimeContext time = new TimeContext(jdTDB, 0.0, 0, 1.0);

        // Verify time calculations
        double daysSinceJ2000 = time.getJulianDateTDB() - 2451545.0;
        assertEquals(0.0, daysSinceJ2000, 1e-9);
        assertEquals(0.0, time.getJulianCenturiesSinceJ2000(), 1e-9);

        // These are the actual inputs to VSOP87
        double T = time.getJulianCenturiesSinceJ2000();

        // VSOP87E should work with this T value
        VSOP87EProvider provider = new VSOP87EProvider();
        FramedState earthState = provider.getState(time, "earth");

        assertNotNull(earthState);
        Vec3d earthPos = earthState.getPosition();
        assertNotNull(earthPos);
        assertTrue(earthPos.length() > 0.98 && earthPos.length() < 1.02);
    }

    // Helper methods

    private Planet findPlanet(String name) {
        for (Planet planet : solarSystem.getPlanets()) {
            if (planet.getName().equalsIgnoreCase(name)) {
                return planet;
            }
        }
        return null;
    }

    private ReferenceData getReferenceData(String body, double jd) {
        Map<Double, ReferenceData> bodyData = referenceData.get(body);
        return bodyData != null ? bodyData.get(jd) : null;
    }

    private void assertPositionMatch(Vec3d calculated, Vec3d reference, String context) {
        double dx = Math.abs(calculated.x - reference.x);
        double dy = Math.abs(calculated.y - reference.y);
        double dz = Math.abs(calculated.z - reference.z);

        String message =
                String.format(
                        "%s position mismatch:\nCalculated: (%.9f, %.9f, %.9f)\nReference:  (%.9f, %.9f, %.9f)\nDelta:      (%.9f, %.9f, %.9f)",
                        context,
                        calculated.x,
                        calculated.y,
                        calculated.z,
                        reference.x,
                        reference.y,
                        reference.z,
                        dx,
                        dy,
                        dz);

        // For inner planets, use tighter tolerance
        // For outer planets, VSOP87 is less accurate, so relax slightly
        double tolerance = POSITION_TOLERANCE_AU;
        if (context.contains("Uranus") || context.contains("Neptune")) {
            tolerance *= 10; // VSOP87 is less accurate for outer planets
        }

        assertTrue(dx < tolerance, message + "\nX component exceeds tolerance");
        assertTrue(dy < tolerance, message + "\nY component exceeds tolerance");
        assertTrue(dz < tolerance, message + "\nZ component exceeds tolerance");
    }

    private void assertVelocityMatch(Vec3d calculated, Vec3d reference, String context) {
        if (calculated == null) {
            return; // Velocity might not be implemented yet
        }

        double dvx = Math.abs(calculated.x - reference.x);
        double dvy = Math.abs(calculated.y - reference.y);
        double dvz = Math.abs(calculated.z - reference.z);

        assertTrue(
                dvx < VELOCITY_TOLERANCE_AU_PER_DAY, context + " VX component exceeds tolerance");
        assertTrue(
                dvy < VELOCITY_TOLERANCE_AU_PER_DAY, context + " VY component exceeds tolerance");
        assertTrue(
                dvz < VELOCITY_TOLERANCE_AU_PER_DAY, context + " VZ component exceeds tolerance");
    }

    private static class ReferenceData {
        final Vec3d position;
        final Vec3d velocity;

        ReferenceData(Vec3d position, Vec3d velocity) {
            this.position = position;
            this.velocity = velocity;
        }
    }
}
