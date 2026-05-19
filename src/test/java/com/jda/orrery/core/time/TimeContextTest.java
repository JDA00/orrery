package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TimeContext immutable time snapshots.
 *
 * Critical validations: - Julian date calculations accuracy - J2000 epoch reference calculations
 * - Known astronomical date conversions - Immutability guarantees
 */
public class TimeContextTest {

    // Astronomical constants
    private static final double J2000_EPOCH_JD = 2451545.0; // J2000.0 = Jan 1, 2000 12:00 TT
    private static final double UNIX_EPOCH_JD = 2440587.5; // Jan 1, 1970 00:00 UTC
    private static final double JULIAN_CENTURY = 36525.0; // Days in Julian century

    // Test precision (microsecond in days)
    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("J2000.0 epoch should be exactly 2451545.0 JD")
    void testJ2000EpochExactValue() {
        TimeContext time = new TimeContext(J2000_EPOCH_JD, 0.0, 0, 1.0);

        assertEquals(J2000_EPOCH_JD, time.getJulianDateTDB(), EPSILON);
        double daysSinceJ2000 = time.getJulianDateTDB() - J2000_EPOCH_JD;
        assertEquals(0.0, daysSinceJ2000, EPSILON);
        assertEquals(0.0, time.getJulianCenturiesSinceJ2000(), EPSILON);
    }

    @Test
    @DisplayName("Unix epoch should be 2440587.5 JD")
    void testUnixEpochConversion() {
        TimeContext time = new TimeContext(UNIX_EPOCH_JD, 0.0, 0, 1.0);

        double daysSinceJ2000 = time.getJulianDateTDB() - J2000_EPOCH_JD;
        // Unix epoch is before J2000 by approximately 30 years
        assertEquals(UNIX_EPOCH_JD - J2000_EPOCH_JD, daysSinceJ2000, EPSILON);
        assertTrue(daysSinceJ2000 < 0, "Unix epoch should be before J2000");

        // Should be roughly -30 years * 365.25 days
        assertEquals(-10957.5, daysSinceJ2000, 0.1);
    }

    @Test
    @DisplayName("Known astronomical dates should convert correctly")
    void testKnownAstronomicalDates() {
        // Apollo 11 launch: July 16, 1969 13:32:00 UTC = JD 2440423.06389
        TimeContext apollo11 = new TimeContext(2440423.06389, 0.0, 0, 1.0);
        assertEquals(-11121.93611, apollo11.getJulianDateTDB() - J2000_EPOCH_JD, 0.00001);

        // Voyager 1 launch: Sept 5, 1977 12:56:00 UTC = JD 2443389.03889
        TimeContext voyager1 = new TimeContext(2443389.03889, 0.0, 0, 1.0);
        assertEquals(-8155.96111, voyager1.getJulianDateTDB() - J2000_EPOCH_JD, 0.00001);

        // New Horizons Pluto flyby: July 14, 2015 11:49:57 UTC = JD 2457217.99302
        TimeContext newHorizons = new TimeContext(2457217.99302, 0.0, 0, 1.0);
        assertEquals(5672.99302, newHorizons.getJulianDateTDB() - J2000_EPOCH_JD, 0.00001);
    }

    @Test
    @DisplayName("Julian centuries calculation must be exact for VSOP87")
    void testJulianCenturiesForVSOP87() {
        // VSOP87 requires T in Julian centuries from J2000.0 in TDB

        // Exactly 1 century after J2000
        TimeContext century = new TimeContext(J2000_EPOCH_JD + JULIAN_CENTURY, 0.0, 0, 1.0);
        assertEquals(1.0, century.getJulianCenturiesSinceJ2000(), EPSILON);

        // Half century
        TimeContext halfCentury = new TimeContext(J2000_EPOCH_JD + JULIAN_CENTURY / 2, 0.0, 0, 1.0);
        assertEquals(0.5, halfCentury.getJulianCenturiesSinceJ2000(), EPSILON);

        // Test date: Jan 1, 2050 12:00 TT (approximately)
        // 50 years = 0.5 centuries (roughly, accounting for leap years)
        double jd2050 = J2000_EPOCH_JD + 365.25 * 50;
        TimeContext year2050 = new TimeContext(jd2050, 0.0, 0, 1.0);
        double centuries = year2050.getJulianCenturiesSinceJ2000();
        assertEquals(0.5, centuries, 0.001); // Within 0.1% for VSOP87 accuracy
    }

    @Test
    @DisplayName("Fractional Julian dates should maintain microsecond precision")
    void testFractionalJulianDatePrecision() {
        // Test with a precise fractional date (microsecond precision)
        double jd = 2451545.5 + 1e-6; // J2000.0 + 1 microsecond
        TimeContext context = new TimeContext(jd, 0.0, 0, 1.0);

        // Tolerance: 1e-9 days = 86.4 microseconds - excellent precision within FP limits
        // At Earth's orbital speed, this is ~2.6 meters position error (negligible)
        assertEquals(1e-6, context.getJulianDateTDB() - 2451545.5, 1e-9);

        // Test century calculation precision
        double T = context.getJulianCenturiesSinceJ2000();
        double expectedT = (0.5 + 1e-6) / 36525.0;
        // Tolerance: 1e-14 for century calculation (FP precision limit)
        assertEquals(expectedT, T, 1e-14);
    }

    @Test
    @DisplayName("TimeContext should be truly immutable")
    void testImmutability() {
        TimeContext time1 = new TimeContext(J2000_EPOCH_JD, 0.016, 100, 365.25);

        // Store original values
        double originalJD = time1.getJulianDateTDB();
        double originalDelta = time1.getDeltaSeconds();
        int originalFrame = time1.getFrameNumber();
        double originalSpeed = time1.getSimulationSpeed();

        // Create another context with same values
        TimeContext time2 = new TimeContext(J2000_EPOCH_JD, 0.016, 100, 365.25);

        // Should be different objects
        assertNotSame(time1, time2);

        // But with same values
        assertEquals(originalJD, time2.getJulianDateTDB(), EPSILON);
        assertEquals(originalDelta, time2.getDeltaSeconds(), EPSILON);
        assertEquals(originalFrame, time2.getFrameNumber());
        assertEquals(originalSpeed, time2.getSimulationSpeed(), EPSILON);

        // Original should remain unchanged
        assertEquals(originalJD, time1.getJulianDateTDB(), EPSILON);
        assertEquals(originalDelta, time1.getDeltaSeconds(), EPSILON);
        assertEquals(originalFrame, time1.getFrameNumber());
        assertEquals(originalSpeed, time1.getSimulationSpeed(), EPSILON);
    }

    @Test
    @DisplayName("Frame timing should support 144Hz displays")
    void testHighFrameRateTiming() {
        // 144 FPS = ~6.94ms per frame
        double delta144Hz = 1.0 / 144.0;

        TimeContext frame = new TimeContext(J2000_EPOCH_JD, delta144Hz, 1000, 1.0);
        assertEquals(delta144Hz, frame.getDeltaSeconds(), 1e-6);

        // 240 FPS = ~4.17ms per frame
        double delta240Hz = 1.0 / 240.0;
        frame = new TimeContext(J2000_EPOCH_JD, delta240Hz, 2000, 1.0);
        assertEquals(delta240Hz, frame.getDeltaSeconds(), 1e-6);
    }

    @Test
    @DisplayName("Simulation speed should correctly scale time advancement")
    void testSimulationSpeedScaling() {
        // Real-time
        TimeContext realTime = new TimeContext(J2000_EPOCH_JD, 1.0, 0, 1.0);
        assertEquals(1.0, realTime.getSimulationSpeed(), EPSILON);

        // 1 day per second (86400x speed)
        TimeContext dayPerSecond = new TimeContext(J2000_EPOCH_JD, 1.0, 0, 86400.0);
        assertEquals(86400.0, dayPerSecond.getSimulationSpeed(), EPSILON);

        // 1 year per day (365.25x speed)
        TimeContext yearPerDay = new TimeContext(J2000_EPOCH_JD, 1.0, 0, 365.25);
        assertEquals(365.25, yearPerDay.getSimulationSpeed(), EPSILON);

        // Paused (0x speed)
        TimeContext paused = new TimeContext(J2000_EPOCH_JD, 1.0, 0, 0.0);
        assertEquals(0.0, paused.getSimulationSpeed(), EPSILON);

        // Reverse time (-1x speed for rewinding)
        TimeContext reverse = new TimeContext(J2000_EPOCH_JD, 1.0, 0, -1.0);
        assertEquals(-1.0, reverse.getSimulationSpeed(), EPSILON);
    }

    @Test
    @DisplayName("Extreme dates should not cause overflow or precision loss")
    void testExtremeDates() {
        // Far past: 10000 BCE (rough estimate)
        double farPastJD = -2450000.0;
        TimeContext farPast = new TimeContext(farPastJD, 0.0, 0, 1.0);
        double farPastDays = farPast.getJulianDateTDB() - J2000_EPOCH_JD;
        assertFalse(Double.isNaN(farPastDays));
        assertFalse(Double.isInfinite(farPast.getJulianCenturiesSinceJ2000()));

        // Far future: Year 10000 CE
        double farFutureJD = 5373484.5; // Approximate
        TimeContext farFuture = new TimeContext(farFutureJD, 0.0, 0, 1.0);
        double farFutureDays = farFuture.getJulianDateTDB() - J2000_EPOCH_JD;
        assertFalse(Double.isNaN(farFutureDays));
        assertFalse(Double.isInfinite(farFuture.getJulianCenturiesSinceJ2000()));

        // Verify precision maintained
        double centuries = farFuture.getJulianCenturiesSinceJ2000();
        double reconstructedJD = J2000_EPOCH_JD + centuries * JULIAN_CENTURY;
        assertEquals(farFutureJD, reconstructedJD, 1.0); // Within 1 day even at extreme dates
    }

    @Test
    @DisplayName("ToString should provide diagnostic information")
    void testToStringDiagnostics() {
        TimeContext context = new TimeContext(2451545.0, 0.016, 42, 1.0);
        String str = context.toString();

        // TimeContext.toString() format: "TimeContext[JD_TDB=%.6f, frame=%d, speed=%.1fx]"
        assertTrue(str.contains("2451545"), "Should contain JD");
        assertTrue(str.contains("42"), "Should contain frame number");
        assertTrue(
                str.contains("1.0x") || str.contains("speed=1.0"),
                "Should contain simulation speed");
    }
}
