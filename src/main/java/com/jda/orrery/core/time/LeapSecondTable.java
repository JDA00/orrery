package com.jda.orrery.core.time;

/**
 * Table of leap seconds for converting between UTC and TAI.
 *
 * Leap seconds are announced by IERS about 6 months in advance. This table must be updated when
 * new leap seconds are announced.
 *
 * Reference: IERS Bulletin C https://www.iers.org/IERS/EN/Publications/Bulletins/bulletins.html
 *
 * No leap seconds existed before 1972; UTC used fractional leap seconds then and this table does
 * not handle that era.
 */
public class LeapSecondTable {

    /**
     * Leap second data: [Julian Date, TAI-UTC in seconds] Ordered chronologically for binary
     * search.
     *
     * Current as of 2023. Last leap second was 2017-01-01.
     */
    private static final double[][] LEAP_SECONDS = {
        // JD            TAI-UTC  Date
        {2441317.5, 10.0}, // 1972-01-01
        {2441499.5, 11.0}, // 1972-07-01
        {2441683.5, 12.0}, // 1973-01-01
        {2442048.5, 13.0}, // 1974-01-01
        {2442413.5, 14.0}, // 1975-01-01
        {2442778.5, 15.0}, // 1976-01-01
        {2443144.5, 16.0}, // 1977-01-01
        {2443509.5, 17.0}, // 1978-01-01
        {2443874.5, 18.0}, // 1979-01-01
        {2444239.5, 19.0}, // 1980-01-01
        {2444786.5, 20.0}, // 1981-07-01
        {2445151.5, 21.0}, // 1982-07-01
        {2445516.5, 22.0}, // 1983-07-01
        {2446247.5, 23.0}, // 1985-07-01
        {2447161.5, 24.0}, // 1988-01-01
        {2447892.5, 25.0}, // 1990-01-01
        {2448257.5, 26.0}, // 1991-01-01
        {2448804.5, 27.0}, // 1992-07-01
        {2449169.5, 28.0}, // 1993-07-01
        {2449534.5, 29.0}, // 1994-07-01
        {2450083.5, 30.0}, // 1996-01-01
        {2450630.5, 31.0}, // 1997-07-01
        {2451179.5, 32.0}, // 1999-01-01
        {2453736.5, 33.0}, // 2006-01-01
        {2454832.5, 34.0}, // 2009-01-01
        {2456109.5, 35.0}, // 2012-07-01
        {2457204.5, 36.0}, // 2015-07-01
        {2457754.5, 37.0}, // 2017-01-01
        // Add new leap seconds here when announced by IERS
    };

    /**
     * Get TAI-UTC difference for a given Julian Date in UTC.
     *
     * @param jdUTC Julian Date in UTC scale
     * @return TAI minus UTC in seconds
     * @throws IllegalArgumentException if date is before 1972
     */
    public static double getTAIMinusUTC(double jdUTC) {
        // Before 1972, leap seconds don't exist
        if (jdUTC < LEAP_SECONDS[0][0]) {
            throw new IllegalArgumentException(
                    "Leap seconds not defined before 1972-01-01 (JD " + LEAP_SECONDS[0][0] + ")");
        }

        // Binary search for the applicable leap second
        int low = 0;
        int high = LEAP_SECONDS.length - 1;

        // If after the last entry, use the last value
        if (jdUTC >= LEAP_SECONDS[high][0]) {
            return LEAP_SECONDS[high][1];
        }

        // Binary search for the correct interval
        while (low < high - 1) {
            int mid = (low + high) / 2;
            if (jdUTC < LEAP_SECONDS[mid][0]) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return LEAP_SECONDS[low][1];
    }

    /**
     * Convert Julian Date from UTC to TAI.
     *
     * @param jdUTC Julian Date in UTC scale
     * @return Julian Date in TAI scale
     */
    public static double utcToTAI(double jdUTC) {
        double leapSeconds = getTAIMinusUTC(jdUTC);
        return jdUTC + (leapSeconds / 86400.0); // Convert seconds to days
    }

    /**
     * Convert Julian Date from TAI to UTC. Note: This is more complex due to discontinuities at
     * leap seconds.
     *
     * @param jdTAI Julian Date in TAI scale
     * @return Julian Date in UTC scale
     */
    public static double taiToUTC(double jdTAI) {
        // First approximation
        double jdUTC = jdTAI;

        // Iterate to handle leap second boundaries correctly
        for (int i = 0; i < 3; i++) {
            double leapSeconds = getTAIMinusUTC(jdUTC);
            jdUTC = jdTAI - (leapSeconds / 86400.0);
        }

        return jdUTC;
    }

    /**
     * Check if a given UTC date is within a leap second. Leap seconds occur at 23:59:60 UTC.
     *
     * @param jdUTC Julian Date in UTC
     * @return true if this instant is during a leap second
     */
    public static boolean isLeapSecond(double jdUTC) {
        // Check if we're at a leap second boundary
        for (int i = 1; i < LEAP_SECONDS.length; i++) {
            double leapJD = LEAP_SECONDS[i][0];
            // Leap second occurs in the last second before the boundary
            double startOfLeapSecond = leapJD - (1.0 / 86400.0);
            if (jdUTC >= startOfLeapSecond && jdUTC < leapJD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the date of the most recent leap second.
     *
     * @return Julian Date of the most recent leap second
     */
    public static double getMostRecentLeapSecondJD() {
        return LEAP_SECONDS[LEAP_SECONDS.length - 1][0];
    }

    /**
     * Get the current TAI-UTC offset (as of the most recent leap second).
     *
     * @return Current TAI minus UTC in seconds
     */
    public static double getCurrentTAIMinusUTC() {
        return LEAP_SECONDS[LEAP_SECONDS.length - 1][1];
    }
}
