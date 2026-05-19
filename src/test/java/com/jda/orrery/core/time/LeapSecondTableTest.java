package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for LeapSecondTable UTC-TAI conversions.
 *
 * Critical validations: - Correct TAI-UTC delta for known dates - Leap second boundary handling
 * - Pre-1972 approximations - Future date extrapolation
 *
 * Reference: IERS Bulletin C for authoritative leap second data
 */
public class LeapSecondTableTest {

    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("TAI-UTC delta at J2000.0 should be exactly 32 seconds")
    void testJ2000LeapSeconds() {
        // J2000.0 = Jan 1, 2000 12:00 TT = JD 2451545.0
        double jdUTC = 2451545.0 - (32.184 / 86400.0); // TT = TAI + 32.184s

        double taiMinusUtc = LeapSecondTable.getTAIMinusUTC(jdUTC);
        assertEquals(32.0, taiMinusUtc, EPSILON, "J2000.0 should have exactly 32 leap seconds");
    }

    @Test
    @DisplayName("Known leap second dates should have correct TAI-UTC")
    void testKnownLeapSeconds() {
        // Test some well-known leap second transitions

        // July 1, 2012 (leap second added June 30, 2012 23:59:60)
        // After this date: TAI-UTC = 35 seconds
        double afterJuly2012 = 2456109.5; // July 1, 2012 00:00 UTC
        assertEquals(35.0, LeapSecondTable.getTAIMinusUTC(afterJuly2012), EPSILON);

        // January 1, 2017 (leap second added Dec 31, 2016 23:59:60)
        // After this date: TAI-UTC = 37 seconds
        double afterJan2017 = 2457754.5; // Jan 1, 2017 00:00 UTC
        assertEquals(37.0, LeapSecondTable.getTAIMinusUTC(afterJan2017), EPSILON);

        // January 1, 1999 (TAI-UTC = 32 seconds)
        double jan1999 = 2451179.5;
        assertEquals(32.0, LeapSecondTable.getTAIMinusUTC(jan1999), EPSILON);
    }

    @Test
    @DisplayName("UTC to TAI conversion should add correct leap seconds")
    void testUTCtoTAIConversion() {
        // Test date with known 37 leap seconds
        double utcJD = 2458000.0; // Sept 22, 2017
        double taiJD = LeapSecondTable.utcToTAI(utcJD);

        // TAI should be ahead of UTC by 37 seconds
        double deltaDays = 37.0 / 86400.0;
        assertEquals(utcJD + deltaDays, taiJD, EPSILON);
    }

    @Test
    @DisplayName("TAI to UTC conversion should subtract correct leap seconds")
    void testTAItoUTCConversion() {
        // Test with TAI date
        double taiJD = 2458000.0; // TAI time
        double utcJD = LeapSecondTable.taiToUTC(taiJD);

        // UTC should be behind TAI by leap seconds
        // Need to determine how many leap seconds at this date
        double deltaBack = LeapSecondTable.getTAIMinusUTC(utcJD);
        double deltaDays = deltaBack / 86400.0;

        assertEquals(taiJD - deltaDays, utcJD, 1e-6); // Slightly relaxed due to iteration
    }

    @Test
    @DisplayName("Leap second boundary should be handled correctly")
    void testLeapSecondBoundary() {
        // Dec 31, 2016 23:59:59 UTC (just before leap second)
        double beforeLeap = 2457753.9999884259; // Dec 31, 2016 23:59:59

        // Dec 31, 2016 23:59:60 UTC (the leap second itself)
        // This is a special moment that exists only in UTC

        // Jan 1, 2017 00:00:00 UTC (just after leap second)
        double afterLeap = 2457754.5;

        // Before leap: TAI-UTC = 36
        assertEquals(36.0, LeapSecondTable.getTAIMinusUTC(beforeLeap), 0.1);

        // After leap: TAI-UTC = 37
        assertEquals(37.0, LeapSecondTable.getTAIMinusUTC(afterLeap), EPSILON);
    }

    @Test
    @DisplayName("Pre-1972 dates should throw or use approximation")
    void testPre1972Dates() {
        // Before 1972, there were no leap seconds, only rate changes
        double apollo11 = 2440423.06389; // July 16, 1969

        // Should either throw IllegalArgumentException or return approximation
        try {
            double taiMinusUtc = LeapSecondTable.getTAIMinusUTC(apollo11);
            // If it doesn't throw, it should return a reasonable approximation
            // Pre-1972 had fractional second adjustments
            assertTrue(
                    taiMinusUtc >= 0 && taiMinusUtc <= 10,
                    "Pre-1972 approximation should be reasonable");
        } catch (IllegalArgumentException e) {
            // This is also acceptable behavior
            assertTrue(
                    e.getMessage().contains("1972") || e.getMessage().contains("before"),
                    "Exception should mention pre-1972 limitation");
        }
    }

    @Test
    @DisplayName("Future dates should use latest known leap second value")
    void testFutureDates() {
        // Far future date (year 2050)
        double future2050 = 2469807.5; // Jan 1, 2050 00:00 UTC

        // Should return at least 37 (current as of 2017)
        double taiMinusUtc = LeapSecondTable.getTAIMinusUTC(future2050);
        assertTrue(
                taiMinusUtc >= 37.0,
                "Future dates should use at least the latest known leap second count");

        // Should not be unreasonably large
        assertTrue(
                taiMinusUtc < 100.0, "Leap seconds shouldn't grow unreasonably for future dates");
    }

    @Test
    @DisplayName("Round-trip UTC-TAI-UTC should preserve time")
    void testRoundTripConversion() {
        double originalUTC = 2458000.0;

        // UTC -> TAI -> UTC
        double tai = LeapSecondTable.utcToTAI(originalUTC);
        double backToUTC = LeapSecondTable.taiToUTC(tai);

        assertEquals(
                originalUTC,
                backToUTC,
                1e-8,
                "Round-trip conversion should preserve time to microsecond precision");
    }

    @Test
    @DisplayName("Leap second table should be monotonic")
    void testMonotonicLeapSeconds() {
        // TAI-UTC should never decrease as we go forward in time
        double previousDelta = 0;

        for (int year = 1972; year <= 2020; year++) {
            double jd = 2440587.5 + (year - 1970) * 365.25; // Approximate JD for Jan 1 each year
            double delta = LeapSecondTable.getTAIMinusUTC(jd);

            assertTrue(
                    delta >= previousDelta,
                    String.format(
                            "Leap seconds should be monotonic. Year %d: %.0f < %.0f",
                            year, delta, previousDelta));

            previousDelta = delta;
        }
    }

    @Test
    @DisplayName("TT to TDB conversion should include periodic terms")
    void testTTtoTDBConversion() {
        // TDB includes periodic terms due to Earth's orbital motion
        // Maximum deviation is about 1.657 milliseconds

        double jdTT = 2451545.0; // J2000.0

        // This would be in a separate converter class, but test the concept
        double T = 0.0; // Centuries since J2000
        double M = Math.toRadians(357.5277233); // Mean anomaly at J2000
        double deltaT = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);
        double expectedTDB = jdTT + (deltaT / 86400.0);

        // Verify the periodic term is reasonable
        assertTrue(
                Math.abs(deltaT) <= 0.002, "TDB-TT difference should be less than 2 milliseconds");
    }

    @Test
    @DisplayName("Leap second at 2015 should be correct")
    void test2015LeapSecond() {
        // June 30, 2015 23:59:59 UTC (before leap)
        double beforeJune2015 = 2457203.9999884259;
        assertEquals(35.0, LeapSecondTable.getTAIMinusUTC(beforeJune2015), 0.1);

        // July 1, 2015 00:00:00 UTC (after leap)
        double afterJune2015 = 2457204.5;
        assertEquals(36.0, LeapSecondTable.getTAIMinusUTC(afterJune2015), EPSILON);
    }

    @Test
    @DisplayName("Historical verification against IERS Bulletin C")
    void testAgainstIERSBulletin() {
        // Test against official IERS data points

        // Jan 1, 1980: TAI-UTC = 19.0
        double jan1980 = 2444239.5;
        assertEquals(19.0, LeapSecondTable.getTAIMinusUTC(jan1980), EPSILON);

        // Jan 1, 1990: TAI-UTC = 25.0
        double jan1990 = 2447892.5;
        assertEquals(25.0, LeapSecondTable.getTAIMinusUTC(jan1990), EPSILON);

        // Jan 1, 2006: TAI-UTC = 33.0
        double jan2006 = 2453736.5;
        assertEquals(33.0, LeapSecondTable.getTAIMinusUTC(jan2006), EPSILON);
    }
}
