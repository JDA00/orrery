package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Performance benchmarks for time system.
 *
 * Ensures timing calculations don't bottleneck rendering at 60+ FPS.
 *
 * Performance targets: - 1M time conversions < 100ms - TimeContext creation < 1 microsecond - No
 * memory leaks from TimeContext objects
 */
public class TimeSystemPerformanceTest {

    private static final int ITERATIONS = 1_000_000;
    private static final double J2000 = 2451545.0;

    @Test
    @DisplayName("1 million TimeContext creations should take < 100ms")
    void testTimeContextCreationPerformance() {
        // Warm up JIT
        for (int i = 0; i < 10000; i++) {
            new TimeContext(J2000 + i, 0.016, i, 1.0);
        }

        long startNanos = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            TimeContext context = new TimeContext(J2000 + i * 0.000001, 0.016, i, 1.0);
            // Ensure object is used (prevents optimization)
            if (context.getFrameNumber() < 0) {
                fail("Should not happen");
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        System.out.printf(
                "TimeContext creation: %.2f ms for %d iterations (%.2f ns/op)%n",
                elapsedMs, ITERATIONS, (double) elapsedNanos / ITERATIONS);

        assertTrue(
                elapsedMs < 100,
                String.format("Creation took %.2f ms, should be < 100ms", elapsedMs));

        // Calculate per-operation time
        double nanosPerOp = (double) elapsedNanos / ITERATIONS;
        assertTrue(
                nanosPerOp < 1000,
                String.format("Each creation took %.0f ns, should be < 1000ns (1μs)", nanosPerOp));
    }

    @Test
    @DisplayName("Julian date calculations should be fast")
    void testJulianDateCalculationPerformance() {
        TimeContext context = new TimeContext(J2000, 0.0, 0, 1.0);

        // Warm up
        for (int i = 0; i < 10000; i++) {
            double days = context.getJulianDateTDB() - J2000;
            double centuries = context.getJulianCenturiesSinceJ2000();
        }

        long startNanos = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            double days = context.getJulianDateTDB() - J2000;
            double centuries = context.getJulianCenturiesSinceJ2000();

            // Use results to prevent optimization
            if (days < -1e6 || centuries < -100) {
                fail("Should not happen");
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        System.out.printf(
                "Julian date calculations: %.2f ms for %d iterations%n", elapsedMs, ITERATIONS);

        assertTrue(
                elapsedMs < 50,
                String.format("Calculations took %.2f ms, should be < 50ms", elapsedMs));
    }

    @Test
    @DisplayName("TimeManager frame advancement should be fast")
    void testTimeManagerPerformance() {
        TimeManager manager = new TimeManager(J2000);
        long baseNanos = 0;

        // Warm up
        for (int i = 0; i < 10000; i++) {
            manager.advanceTime(baseNanos + i * 16_666_667L); // 60 FPS
        }

        manager = new TimeManager(J2000); // Fresh manager
        baseNanos = 0;

        long startNanos = System.nanoTime();

        // Simulate 60 FPS for 1000 seconds (60,000 frames)
        int frames = 60_000;
        for (int i = 0; i < frames; i++) {
            TimeContext context = manager.advanceTime(baseNanos + i * 16_666_667L);

            // Use result
            if (context.getFrameNumber() != i) {
                fail("Frame number mismatch");
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double msPerFrame = elapsedMs / frames;

        System.out.printf(
                "TimeManager advancement: %.2f ms for %d frames (%.4f ms/frame)%n",
                elapsedMs, frames, msPerFrame);

        // Should be able to handle 60,000 frames in under 100ms
        // That's about 0.0016ms per frame, leaving plenty of time for rendering
        assertTrue(
                elapsedMs < 100,
                String.format("Frame advancement took %.2f ms, should be < 100ms", elapsedMs));
    }

    @Test
    @DisplayName("Leap second lookups should be fast")
    void testLeapSecondPerformance() {
        // Warm up
        for (int i = 0; i < 10000; i++) {
            LeapSecondTable.getTAIMinusUTC(J2000 + i);
        }

        long startNanos = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            double taiMinusUtc = LeapSecondTable.getTAIMinusUTC(J2000 + i * 0.001);

            // Use result
            if (taiMinusUtc < 0) {
                fail("Should not happen");
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        System.out.printf(
                "Leap second lookups: %.2f ms for %d iterations%n", elapsedMs, ITERATIONS);

        assertTrue(
                elapsedMs < 100,
                String.format("Lookups took %.2f ms, should be < 100ms", elapsedMs));
    }

    @Test
    @DisplayName("Complete UTC to TDB conversion chain performance")
    void testCompleteConversionChainPerformance() {
        // This is what happens for each ephemeris calculation

        // Warm up
        for (int i = 0; i < 10000; i++) {
            convertUTCtoTDB(J2000 + i * 0.001);
        }

        long startNanos = System.nanoTime();

        // Test 100,000 conversions (more realistic for rendering)
        int conversions = 100_000;
        for (int i = 0; i < conversions; i++) {
            double tdb = convertUTCtoTDB(J2000 + i * 0.001);

            // Use result
            if (tdb < J2000 - 1) {
                fail("Should not happen");
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double usPerConversion = (elapsedNanos / 1000.0) / conversions;

        System.out.printf(
                "UTC→TDB conversions: %.2f ms for %d conversions (%.2f μs/op)%n",
                elapsedMs, conversions, usPerConversion);

        // Should handle 100K conversions in under 50ms
        assertTrue(
                elapsedMs < 50,
                String.format("Conversions took %.2f ms, should be < 50ms", elapsedMs));

        // Each conversion should take less than 1 microsecond
        assertTrue(
                usPerConversion < 1.0,
                String.format("Each conversion took %.2f μs, should be < 1μs", usPerConversion));
    }

    @Test
    @DisplayName("Memory allocation should be minimal")
    void testMemoryEfficiency() {
        // Get initial memory
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Create many TimeContext objects
        TimeContext[] contexts = new TimeContext[100_000];
        for (int i = 0; i < contexts.length; i++) {
            contexts[i] = new TimeContext(J2000 + i, 0.016, i, 1.0);
        }

        // Force reference to prevent optimization
        if (contexts[50000].getFrameNumber() < 0) {
            fail("Should not happen");
        }

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memUsed = memAfter - memBefore;
        double bytesPerObject = (double) memUsed / contexts.length;

        System.out.printf(
                "Memory usage: %d bytes for %d objects (%.1f bytes/object)%n",
                memUsed, contexts.length, bytesPerObject);

        // Each TimeContext should use less than 100 bytes
        // (4 doubles + 1 int + object overhead)
        assertTrue(
                bytesPerObject < 100,
                String.format("Each object uses %.1f bytes, should be < 100", bytesPerObject));
    }

    @Test
    @DisplayName("60 FPS with 10 planets should maintain performance")
    void testRealisticRenderingLoad() {
        TimeManager manager = new TimeManager(J2000);
        long baseNanos = 0;

        // Simulate 1 second of 60 FPS with 10 planets
        long startNanos = System.nanoTime();

        for (int frame = 0; frame < 60; frame++) {
            // Advance time once per frame
            TimeContext context = manager.advanceTime(baseNanos + frame * 16_666_667L);

            // Simulate 10 planets getting time and calculating positions
            for (int planet = 0; planet < 10; planet++) {
                double T = context.getJulianCenturiesSinceJ2000();

                // Simulate ephemeris time usage
                double M = 357.5277233 + 35999.05034 * T;
                double sinM = Math.sin(Math.toRadians(M));

                // Use result
                if (sinM > 2.0) {
                    fail("Should not happen");
                }
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        System.out.printf("Realistic load (60 frames, 10 planets): %.2f ms%n", elapsedMs);

        // Should complete 1 second of simulation in less than 16ms
        // (leaving time for actual rendering)
        assertTrue(
                elapsedMs < 16,
                String.format("Simulation took %.2f ms, should be < 16ms (one frame)", elapsedMs));
    }

    // Helper method
    private double convertUTCtoTDB(double utcJD) {
        double taiJD = LeapSecondTable.utcToTAI(utcJD);
        double ttJD = taiJD + (32.184 / 86400.0);

        double T = (ttJD - J2000) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaTDB = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);

        return ttJD + (deltaTDB / 86400.0);
    }
}
