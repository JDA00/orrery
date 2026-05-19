package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Basic smoke tests to verify the timing system works. Once these pass, we can fix the more
 * comprehensive tests.
 */
public class BasicTimeTest {

    private static final double J2000 = 2451545.0;
    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("TimeContext can be created")
    void testTimeContextCreation() {
        TimeContext time = new TimeContext(J2000, 0.016, 0, 1.0);
        assertNotNull(time);
    }

    @Test
    @DisplayName("TimeContext returns correct Julian date")
    void testJulianDate() {
        TimeContext time = new TimeContext(J2000, 0.016, 0, 1.0);
        assertEquals(J2000, time.getJulianDateTDB(), EPSILON);
    }

    @Test
    @DisplayName("TimeContext calculates centuries since J2000")
    void testCenturiesSinceJ2000() {
        TimeContext time = new TimeContext(J2000, 0.016, 0, 1.0);
        assertEquals(0.0, time.getJulianCenturiesSinceJ2000(), EPSILON);

        // One century later
        TimeContext centuryLater = new TimeContext(J2000 + 36525.0, 0.016, 0, 1.0);
        assertEquals(1.0, centuryLater.getJulianCenturiesSinceJ2000(), EPSILON);
    }

    @Test
    @DisplayName("TimeManager can advance time")
    void testTimeManagerAdvance() {
        TimeManager manager = new TimeManager(J2000);

        long nanos = System.nanoTime();
        TimeContext time1 = manager.advanceTime(nanos);
        assertNotNull(time1);

        // Advance by 1 second
        nanos += 1_000_000_000L;
        TimeContext time2 = manager.advanceTime(nanos);

        // Should have advanced (approximately 1/86400 day)
        double delta = time2.getJulianDateTDB() - time1.getJulianDateTDB();
        assertEquals(1.0 / 86400.0, delta, 1e-6);
    }

    @Test
    @DisplayName("LeapSecondTable provides TAI-UTC offset")
    void testLeapSeconds() {
        // At J2000, TAI-UTC = 32 seconds
        double taiMinusUtc = LeapSecondTable.getTAIMinusUTC(J2000);
        assertEquals(32.0, taiMinusUtc, 0.1);
    }
}
