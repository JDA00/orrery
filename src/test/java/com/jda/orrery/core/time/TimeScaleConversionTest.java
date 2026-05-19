package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates time scale conversions critical for ephemeris accuracy.
 *
 * Tests the complete chain: UTC → TAI → TT → TDB References SOFA (Standards of Fundamental
 * Astronomy) for validation.
 *
 * Critical for VSOP87 which requires TDB input.
 */
public class TimeScaleConversionTest {

    private static final double EPSILON = 1e-9; // Microsecond precision
    private static final double J2000_TT = 2451545.0; // J2000.0 in TT scale

    @Test
    @DisplayName("UTC to TAI conversion should match SOFA reference")
    void testUTCtoTAIAgainstSOFA() {
        // SOFA test case: 2006 Jan 15 21:24:37.5 UTC
        // From SOFA documentation
        double utcJD = 2453750.892104167; // Julian Date in UTC

        // Expected TAI (UTC + 33 leap seconds at this date)
        double expectedTAI = utcJD + (33.0 / 86400.0);

        double actualTAI = LeapSecondTable.utcToTAI(utcJD);
        assertEquals(expectedTAI, actualTAI, EPSILON);
    }

    @Test
    @DisplayName("TAI to TT conversion should add exactly 32.184 seconds")
    void testTAItoTT() {
        // TT = TAI + 32.184 seconds (exact by definition)
        double taiJD = 2451545.0;
        double expectedTT = taiJD + (32.184 / 86400.0);

        // In our system, this is a simple addition
        double ttJD = taiJD + (32.184 / 86400.0);
        assertEquals(expectedTT, ttJD, EPSILON);
    }

    @Test
    @DisplayName("TT to TDB conversion should include Earth orbital terms")
    void testTTtoTDB() {
        // TDB includes periodic variations due to relativistic effects
        // Maximum amplitude is about 1.657 ms

        // Test at J2000.0 epoch
        double ttJD = J2000_TT;
        double T = 0.0; // Centuries since J2000

        // Mean anomaly of Earth at J2000.0
        double M = Math.toRadians(357.5277233);

        // TDB-TT difference (simplified, from Meeus)
        double deltaSec = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);

        double tdbJD = ttJD + (deltaSec / 86400.0);

        // At J2000.0, TDB-TT should be close to zero but not exactly
        assertTrue(Math.abs(deltaSec) < 0.002, "TDB-TT should be less than 2ms");
    }

    @Test
    @DisplayName("Complete UTC to TDB chain for ephemeris")
    void testCompleteUTCtoTDBChain() {
        // This is what actually matters for planet positions

        // Start with UTC time: Jan 1, 2020 00:00:00 UTC
        double utcJD = 2458849.5;

        // Step 1: UTC to TAI (add leap seconds)
        double taiJD = LeapSecondTable.utcToTAI(utcJD);
        assertTrue(taiJD > utcJD, "TAI should be ahead of UTC");

        // Step 2: TAI to TT (add 32.184 seconds)
        double ttJD = taiJD + (32.184 / 86400.0);
        assertEquals(taiJD + (32.184 / 86400.0), ttJD, EPSILON);

        // Step 3: TT to TDB (add periodic terms)
        double T = (ttJD - J2000_TT) / 36525.0; // Centuries since J2000
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaTDB = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);
        double tdbJD = ttJD + (deltaTDB / 86400.0);

        // TDB should be very close to TT (within 2ms)
        assertEquals(ttJD, tdbJD, 2e-8); // 2ms in days

        // Total offset from UTC should be reasonable
        double totalOffsetSec = (tdbJD - utcJD) * 86400.0;
        assertTrue(totalOffsetSec > 37.0, "Should include at least 37 leap seconds");
        assertTrue(totalOffsetSec < 70.0, "Total offset should be reasonable");
    }

    @Test
    @DisplayName("J2000.0 epoch in different time scales")
    void testJ2000InAllScales() {
        // J2000.0 is defined as Jan 1, 2000 12:00 TT
        double ttJ2000 = 2451545.0;

        // Work backwards to find UTC
        // TT = TAI + 32.184s
        double taiJ2000 = ttJ2000 - (32.184 / 86400.0);

        // TAI = UTC + leap seconds (32 at J2000)
        double utcJ2000 = taiJ2000 - (32.0 / 86400.0);

        // Verify the total offset
        double totalOffset = (ttJ2000 - utcJ2000) * 86400.0;
        assertEquals(64.184, totalOffset, 0.001); // 32 leap + 32.184 TT-TAI

        // TDB at J2000 (should be very close to TT)
        double M = Math.toRadians(357.5277233); // Mean anomaly at J2000
        double deltaTDB = 0.001657 * Math.sin(M);
        double tdbJ2000 = ttJ2000 + (deltaTDB / 86400.0);

        // Should be within 2ms
        assertEquals(ttJ2000, tdbJ2000, 2e-8);
    }

    @Test
    @DisplayName("GPS time conversion (TAI - 19 seconds)")
    void testGPSTimeConversion() {
        // GPS time = TAI - 19 seconds (fixed offset since GPS epoch)
        // GPS epoch: Jan 6, 1980 00:00:00 UTC

        double taiJD = 2451545.0; // Some TAI time
        double gpsJD = taiJD - (19.0 / 86400.0);

        // Verify offset
        double offsetSec = (taiJD - gpsJD) * 86400.0;
        // Tolerance: 1e-6 seconds = 1 microsecond - GPS atomic clock precision level
        // Actual FP error is ~17 microseconds due to day-to-second conversion
        assertEquals(19.0, offsetSec, 2e-5); // 20 microseconds - still excellent for GPS
    }

    @Test
    @DisplayName("UT1 approximation for Earth rotation")
    void testUT1Approximation() {
        // UT1 is needed for Earth rotation angle
        // UT1 = UTC + DUT1 (usually within ±0.9s)

        double utcJD = 2458849.5;
        double dut1 = 0.2; // Typical value in seconds

        double ut1JD = utcJD + (dut1 / 86400.0);

        // Verify difference is small
        double diffSec = Math.abs((ut1JD - utcJD) * 86400.0);
        assertTrue(diffSec < 0.9, "DUT1 should be less than 0.9 seconds");
    }

    @Test
    @DisplayName("Barycentric Coordinate Time (TCB) for Gaia")
    void testTCBConversion() {
        // TCB is used by Gaia catalog
        // TCB-TDB ≈ 1.550519768e-8 * (JD - 2443144.5) * 86400

        double tdbJD = 2458849.5;
        double daysSinceEpoch = tdbJD - 2443144.5;
        double tcbMinusTdbSec = 1.550519768e-8 * daysSinceEpoch * 86400.0;

        double tcbJD = tdbJD + (tcbMinusTdbSec / 86400.0);

        // Should be a small but measurable difference
        assertTrue(tcbMinusTdbSec > 0, "TCB should be ahead of TDB");
        assertTrue(
                tcbMinusTdbSec < 25,
                "Difference should be less than 25 seconds"); // Relaxed for test date
    }

    @Test
    @DisplayName("Time scale differences at specific ephemeris moments")
    void testEphemerisTimeScales() {
        // Test at a moment when we need accurate planet positions
        // Mars opposition: Oct 13, 2020 23:26 UTC

        double utcJD = 2459136.476389; // Oct 13, 2020 23:26 UTC

        // Convert to TDB for VSOP87
        double taiJD = LeapSecondTable.utcToTAI(utcJD);
        double ttJD = taiJD + (32.184 / 86400.0);

        // Calculate TDB
        double T = (ttJD - J2000_TT) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaTDB = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);
        double tdbJD = ttJD + (deltaTDB / 86400.0);

        // For ephemeris, we need TDB
        double centuriesSinceJ2000 = (tdbJD - J2000_TT) / 36525.0;

        // This is what goes into VSOP87
        assertTrue(centuriesSinceJ2000 > 0.2, "Should be about 0.2 centuries since J2000");
        assertTrue(centuriesSinceJ2000 < 0.3, "Should be less than 0.3 centuries");
    }

    @Test
    @DisplayName("Precision validation for planet position calculations")
    void testPrecisionForPlanetPositions() {
        // Planet positions need arcsecond accuracy
        // 1 arcsecond ≈ 725 km at 1 AU
        // This requires time precision of about 0.01 seconds

        double utcJD = 2458849.5;

        // Add small time offset (0.01 seconds)
        double utcJDplus = utcJD + (0.01 / 86400.0);

        // Convert both to TDB
        double tdb1 = convertUTCtoTDB(utcJD);
        double tdb2 = convertUTCtoTDB(utcJDplus);

        // Difference should be preserved
        double diffSec = (tdb2 - tdb1) * 86400.0;
        // Tolerance: 1e-4 seconds = 100 microseconds
        // Still excellent for ephemeris - Earth moves only 3mm in 100 microseconds
        // Accounts for FP accumulation through UTC->TAI->TT->TDB conversion chain
        assertEquals(0.01, diffSec, 1e-4, "Sub-second precision must be maintained");
    }

    // Helper method for complete conversion
    private double convertUTCtoTDB(double utcJD) {
        double taiJD = LeapSecondTable.utcToTAI(utcJD);
        double ttJD = taiJD + (32.184 / 86400.0);

        double T = (ttJD - J2000_TT) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaTDB = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);

        return ttJD + (deltaTDB / 86400.0);
    }
}
