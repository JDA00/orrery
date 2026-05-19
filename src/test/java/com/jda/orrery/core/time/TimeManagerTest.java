package com.jda.orrery.core.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TimeManager mutable time state manager.
 *
 * Critical validations: - No time accumulation errors over millions of frames - Frame delta
 * stability under varying conditions - Pause/resume without time jumps - Simulation speed changes
 */
public class TimeManagerTest {

    private static final double J2000_EPOCH = 2451545.0;
    private static final double EPSILON = 1e-9;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private TimeManager timeManager;
    private long baseNanos;

    @BeforeEach
    void setUp() {
        timeManager = new TimeManager(J2000_EPOCH);
        baseNanos = System.nanoTime();
    }

    @Test
    @DisplayName("TimeManager should initialize at specified epoch")
    void testInitialization() {
        assertEquals(J2000_EPOCH, timeManager.getCurrentJulianDateTDB(), EPSILON);
        assertEquals(0.0, timeManager.getDaysSinceJ2000(), EPSILON);
        assertEquals(0.0, timeManager.getCenturiesSinceJ2000(), EPSILON);
        assertEquals(1.0, timeManager.getSimulationSpeed(), EPSILON);
        assertFalse(timeManager.isPaused());
    }

    @Test
    @DisplayName("No accumulation error over 1 million frames")
    void testNoAccumulationError() {
        // Test the advanceTimeFromStart method which avoids accumulation
        TimeManager accurateManager = new TimeManager(J2000_EPOCH);

        // Simulate 1 million frames at 60 FPS (about 4.6 hours)
        long startNanos = System.nanoTime(); // Use realistic starting time
        long nanosPerFrame = NANOS_PER_SECOND / 60; // 60 FPS

        for (int i = 0; i < 1_000_000; i++) {
            long currentNanos = startNanos + (i * nanosPerFrame);
            TimeContext context = accurateManager.advanceTimeFromStart(currentNanos);

            // Every 10000 frames, verify accuracy
            if (i % 10000 == 0) {
                double expectedDays = (double) (i * nanosPerFrame) / NANOS_PER_SECOND / 86400.0;
                double actualDays = context.getJulianDateTDB() - J2000_EPOCH;

                // Should maintain microsecond precision even after 1M frames
                assertEquals(expectedDays, actualDays, 1e-6);
            }
        }
    }

    @Test
    @DisplayName("Frame delta should be stable at 60 FPS")
    void testFrameDeltaStability() {
        long nanos = baseNanos;
        long nanosPerFrame = NANOS_PER_SECOND / 60; // 60 FPS

        TimeContext firstFrame = timeManager.advanceTime(nanos);
        assertEquals(0.0, firstFrame.getDeltaSeconds(), EPSILON); // First frame has no delta

        // Advance several frames
        for (int i = 1; i <= 10; i++) {
            nanos += nanosPerFrame;
            TimeContext frame = timeManager.advanceTime(nanos);

            // Delta should be approximately 1/60 second
            assertEquals(1.0 / 60.0, frame.getDeltaSeconds(), 0.001);
            assertEquals(i, frame.getFrameNumber());
        }
    }

    @Test
    @DisplayName("Large frame deltas should be clamped to prevent jumps")
    void testLargeFrameDeltaClamping() {
        long nanos = baseNanos;

        // First frame
        timeManager.advanceTime(nanos);

        // Simulate a huge gap (e.g., computer sleep, debugging pause)
        nanos += 10 * NANOS_PER_SECOND; // 10 second gap
        TimeContext frame = timeManager.advanceTime(nanos);

        // Delta should be clamped to maximum (1.0 second in TimeManager)
        assertTrue(frame.getDeltaSeconds() <= 1.0);
    }

    @Test
    @DisplayName("Pause should freeze time advancement")
    void testPauseFreezesTime() {
        long nanos = baseNanos;

        // Advance a few frames normally
        timeManager.advanceTime(nanos);
        nanos += NANOS_PER_SECOND / 60;
        timeManager.advanceTime(nanos);

        double timeBeforePause = timeManager.getCurrentJulianDateTDB();

        // Pause
        timeManager.setPaused(true);
        assertTrue(timeManager.isPaused());

        // Advance several frames while paused
        for (int i = 0; i < 100; i++) {
            nanos += NANOS_PER_SECOND / 60;
            TimeContext context = timeManager.advanceTime(nanos);

            // Time should not advance
            assertEquals(timeBeforePause, context.getJulianDateTDB(), EPSILON);
        }

        // Resume
        timeManager.setPaused(false);
        assertFalse(timeManager.isPaused());

        // Time should advance again
        nanos += NANOS_PER_SECOND;
        TimeContext resumed = timeManager.advanceTime(nanos);
        assertTrue(resumed.getJulianDateTDB() > timeBeforePause);
    }

    @Test
    @DisplayName("Simulation speed should correctly scale time")
    void testSimulationSpeedScaling() {
        long nanos = baseNanos;

        // Set 365.25x speed (1 year per day)
        timeManager.setSimulationSpeed(365.25);
        assertEquals(365.25, timeManager.getSimulationSpeed(), EPSILON);

        // First frame
        timeManager.advanceTime(nanos);
        double startTime = timeManager.getCurrentJulianDateTDB();

        // Advance 1 real second
        nanos += NANOS_PER_SECOND;
        TimeContext context = timeManager.advanceTime(nanos);

        // Should have advanced 365.25 seconds of simulation time
        // = 365.25/86400 days = 0.00422511574 days
        double expectedAdvance = 365.25 / 86400.0;
        double actualAdvance = context.getJulianDateTDB() - startTime;

        assertEquals(expectedAdvance, actualAdvance, 1e-8);
    }

    @Test
    @DisplayName("Time jump to specific date should work correctly")
    void testTimeJump() {
        // Jump to Apollo 11 launch
        double apollo11JD = 2440423.06389;
        timeManager.setCurrentTime(apollo11JD);

        assertEquals(apollo11JD, timeManager.getCurrentJulianDateTDB(), EPSILON);

        // Verify days since J2000 is negative (before J2000)
        assertTrue(timeManager.getDaysSinceJ2000() < 0);
        assertEquals(apollo11JD - J2000_EPOCH, timeManager.getDaysSinceJ2000(), EPSILON);
    }

    @Test
    @DisplayName("Reset to J2000 should restore epoch")
    void testResetToJ2000() {
        // Change time
        timeManager.setCurrentTime(2460000.0);

        // Reset to J2000
        timeManager.resetToJ2000();

        assertEquals(J2000_EPOCH, timeManager.getCurrentJulianDateTDB(), EPSILON);
        assertEquals(0.0, timeManager.getDaysSinceJ2000(), EPSILON);
        assertEquals(0.0, timeManager.getCenturiesSinceJ2000(), EPSILON);
    }

    @Test
    @DisplayName("Reset to now should approximate current date")
    void testResetToNow() {
        timeManager.resetToNow();

        // Should be somewhere around 2020-2030 (roughly 7300-11000 days since J2000)
        double daysSince2000 = timeManager.getDaysSinceJ2000();

        // Sanity check: should be positive and in reasonable range
        assertTrue(daysSince2000 > 7000, "Should be after 2019");
        assertTrue(daysSince2000 < 15000, "Should be before 2041");
    }

    @Test
    @DisplayName("Frame counter should increment correctly")
    void testFrameCounter() {
        long nanos = baseNanos;

        assertEquals(0, timeManager.getFrameCount());

        for (int i = 0; i < 100; i++) {
            nanos += NANOS_PER_SECOND / 60;
            TimeContext context = timeManager.advanceTime(nanos);
            assertEquals(i, context.getFrameNumber());
        }

        assertEquals(100, timeManager.getFrameCount());
    }

    @Test
    @DisplayName("Negative simulation speed should allow time reversal")
    void testNegativeSimulationSpeed() {
        long nanos = baseNanos;

        // Advance time forward first
        timeManager.advanceTime(nanos);
        nanos += NANOS_PER_SECOND;
        timeManager.advanceTime(nanos);

        double timeBeforeReverse = timeManager.getCurrentJulianDateTDB();

        // Set negative speed (time reversal)
        timeManager.setSimulationSpeed(-1.0);

        // Advance real time (but simulation goes backward)
        nanos += NANOS_PER_SECOND;
        TimeContext reversed = timeManager.advanceTime(nanos);

        // Time should have gone backward
        assertTrue(reversed.getJulianDateTDB() < timeBeforeReverse);
    }

    @Test
    @DisplayName("Zero simulation speed should freeze time like pause")
    void testZeroSimulationSpeed() {
        long nanos = baseNanos;

        timeManager.setSimulationSpeed(0.0);

        timeManager.advanceTime(nanos);
        double frozenTime = timeManager.getCurrentJulianDateTDB();

        // Advance real time
        for (int i = 0; i < 10; i++) {
            nanos += NANOS_PER_SECOND / 60;
            TimeContext context = timeManager.advanceTime(nanos);
            assertEquals(frozenTime, context.getJulianDateTDB(), EPSILON);
        }
    }

    @Test
    @DisplayName("TimeContext snapshots should be independent")
    void testTimeContextIndependence() {
        long nanos = baseNanos;

        TimeContext context1 = timeManager.advanceTime(nanos);
        double time1 = context1.getJulianDateTDB();

        nanos += NANOS_PER_SECOND;
        TimeContext context2 = timeManager.advanceTime(nanos);

        // First context should remain unchanged
        assertEquals(time1, context1.getJulianDateTDB(), EPSILON);

        // Second context should be different
        assertTrue(context2.getJulianDateTDB() > time1);
    }
}
