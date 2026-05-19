package com.jda.orrery.core.time;

import com.jda.orrery.core.logging.Logging;
import java.util.logging.Logger;

/**
 * Manages time advancement and creates immutable {@link TimeContext} snapshots. Mutable
 * counterpart to {@code TimeContext}; handles time progression, simulation speed, and
 * pause/resume.
 *
 * Thread-safety: render-thread confined. {@code advanceTime()} and the other mutators must be
 * called from the thread driving the render loop. {@code TimeContext} instances it returns are
 * immutable and safe to read from anywhere.
 */
public class TimeManager {
    private static final Logger LOGGER = Logging.logger(TimeManager.class);

    // Constants
    private static final double DEFAULT_SIMULATION_SPEED = 1.0;
    private static final double SECONDS_PER_DAY = 86400.0;

    // Time state (mutable)
    private double currentJulianDateTDB;
    private double simulationSpeed = DEFAULT_SIMULATION_SPEED;
    private boolean paused = false;

    // Frame timing. Accumulative advanceTime drifts over long sessions;
    // a wall-clock-reference / rebasing design would eliminate this.
    private final double startJulianDateTDB;
    private final long startNanos;
    private long lastFrameNanos = 0;
    private int frameCount = 0;

    /** Initialize time manager with current real-world time. */
    public TimeManager() {
        this(getCurrentRealWorldJulianDateTDB());
    }

    /**
     * Initialize time manager with specific starting time.
     *
     * @param startJulianDateTDB Starting time in TDB
     */
    public TimeManager(double startJulianDateTDB) {
        this.startJulianDateTDB = startJulianDateTDB;
        this.currentJulianDateTDB = startJulianDateTDB;
        this.startNanos = System.nanoTime();

        LOGGER.info(
                "TimeManager initialized at JD_TDB "
                        + startJulianDateTDB
                        + " (speed="
                        + simulationSpeed
                        + "x)");
    }

    /**
     * Advance time based on real-world elapsed time and create a new TimeContext. This should be
     * called once per frame.
     *
     * Pause semantics: - When paused, simulation time is frozen (no calculations advance) - Real
     * delta time is still tracked for other systems (camera momentum, etc.) - Called exactly once
     * per frame from a single time authority
     *
     * @param currentNanos Current system time in nanoseconds
     * @return Immutable TimeContext for this frame
     */
    public TimeContext advanceTime(long currentNanos) {
        // Handle first frame
        if (lastFrameNanos == 0) {
            lastFrameNanos = currentNanos;
            LOGGER.fine(
                    String.format(
                            "[TIME] First frame: JD=%.15f, frame=%d",
                            currentJulianDateTDB, frameCount));
            return new TimeContext(
                    currentJulianDateTDB, 0.0, frameCount++, simulationSpeed, paused);
        }

        // Calculate real-world delta time (always computed for camera/animation)
        double deltaSeconds = (currentNanos - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = currentNanos;

        // Clamp delta to prevent huge jumps (e.g., when debugging or computer sleeps)
        deltaSeconds = Math.min(deltaSeconds, 1.0);

        // Only advance time if not paused.
        // When paused, currentJulianDateTDB remains exactly the same (no floating point drift).
        if (!paused && simulationSpeed != 0.0) {
            double deltaDays = (deltaSeconds / SECONDS_PER_DAY) * simulationSpeed;
            currentJulianDateTDB += deltaDays;

            // Detect if we're running too slow
            if (deltaSeconds > 0.05) { // 50ms
                LOGGER.fine(
                        String.format(
                                "Large frame delta: %.3f seconds (frame %d)",
                                deltaSeconds, frameCount));
            }
        }

        // Create immutable snapshot
        // Note: deltaSeconds is ALWAYS provided (even when paused) for camera/UI updates
        return new TimeContext(
                currentJulianDateTDB, deltaSeconds, frameCount++, simulationSpeed, paused);
    }

    /**
     * Alternative time advancement that avoids accumulation errors. Computes current time from
     * elapsed wall-clock time.
     *
     * @param currentNanos Current system time in nanoseconds
     * @return Immutable TimeContext for this frame
     */
    public TimeContext advanceTimeFromStart(long currentNanos) {
        // Calculate delta from previous frame
        double deltaSeconds = 0.0;
        if (lastFrameNanos != 0) {
            deltaSeconds = (currentNanos - lastFrameNanos) / 1_000_000_000.0;
            deltaSeconds = Math.min(deltaSeconds, 1.0);
        }
        lastFrameNanos = currentNanos;

        // Compute time from fixed start point to avoid accumulation errors
        if (!paused && simulationSpeed != 0.0) {
            long elapsedNanos = currentNanos - startNanos;
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double elapsedDays = (elapsedSeconds / SECONDS_PER_DAY) * simulationSpeed;
            currentJulianDateTDB = startJulianDateTDB + elapsedDays;
        }

        return new TimeContext(
                currentJulianDateTDB, deltaSeconds, frameCount++, simulationSpeed, paused);
    }

    /**
     * Get current time without advancing.
     *
     * @return Current Julian Date in TDB
     */
    public double getCurrentJulianDateTDB() {
        return currentJulianDateTDB;
    }

    /**
     * Jump to a specific time.
     *
     * @param julianDateTDB New time in TDB
     */
    public void setCurrentTime(double julianDateTDB) {
        this.currentJulianDateTDB = julianDateTDB;
        LOGGER.fine("Time set to JD_TDB " + julianDateTDB);
    }

    /**
     * Set simulation speed multiplier.
     *
     * @param speed Speed factor (1.0 = real time, 365.0 = 1 year per day, 0.0 = stopped, negative =
     *     reverse)
     */
    public void setSimulationSpeed(double speed) {
        double oldSpeed = this.simulationSpeed;
        this.simulationSpeed = speed;

        LOGGER.fine(String.format("Simulation speed changed from %.2fx to %.2fx", oldSpeed, speed));
    }

    /** Get current simulation speed. */
    public double getSimulationSpeed() {
        return simulationSpeed;
    }

    /**
     * Pause or resume time advancement.
     *
     * On resume, reset lastFrameNanos so the next deltaSeconds reflects one frame of wall time,
     * not the full pause duration.
     */
    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;

            if (paused) {
                // When pausing, log current state for debugging
                LOGGER.fine(
                        String.format(
                                "[TIME-PAUSE] PAUSING at JD_TDB %.15f (frame %d, speed=%.1fx)",
                                currentJulianDateTDB, frameCount, simulationSpeed));
                // Store the exact pause time
                LOGGER.fine(
                        String.format("[TIME-PAUSE] Stored pause JD: %.15f", currentJulianDateTDB));
            } else {
                // When resuming, reset timing to avoid jump
                double pausedJD = currentJulianDateTDB;
                lastFrameNanos = System.nanoTime();
                LOGGER.fine(
                        String.format(
                                "[TIME-RESUME] RESUMING from JD_TDB %.15f (frame %d, speed=%.1fx)",
                                currentJulianDateTDB, frameCount, simulationSpeed));
                LOGGER.fine(
                        String.format(
                                "[TIME-RESUME] JD unchanged: %.15f (should match pause JD)",
                                currentJulianDateTDB));
                // Verify JD hasn't changed
                if (Math.abs(pausedJD - currentJulianDateTDB) > 1e-15) {
                    LOGGER.warning(
                            String.format(
                                    "[TIME-RESUME] WARNING: JD changed during pause! Was %.15f",
                                    pausedJD));
                }
            }
        }
    }

    /** Check if time advancement is paused. */
    public boolean isPaused() {
        return paused;
    }

    /** Reset to current real-world time. */
    public void resetToNow() {
        double newTime = getCurrentRealWorldJulianDateTDB();
        setCurrentTime(newTime);
        LOGGER.info("Time reset to current real-world time");
    }

    /** Reset to J2000.0 epoch. */
    public void resetToJ2000() {
        setCurrentTime(2451545.0); // J2000.0
        LOGGER.info("Time reset to J2000.0 epoch");
    }

    /** Get days since J2000.0 epoch. */
    public double getDaysSinceJ2000() {
        return currentJulianDateTDB - 2451545.0;
    }

    /** Get Julian centuries since J2000.0. */
    public double getCenturiesSinceJ2000() {
        return getDaysSinceJ2000() / 36525.0;
    }

    /**
     * Calculate current real-world Julian Date in TDB.
     *
     * @return Current JD in TDB scale
     */
    private static double getCurrentRealWorldJulianDateTDB() {
        // Start with current system time (approximately UTC)
        long currentMillis = System.currentTimeMillis();
        double daysSince1970 = currentMillis / 86400000.0;
        double jdUTC = daysSince1970 + 2440587.5; // JD for Jan 1, 1970 00:00 UTC

        // Convert to TDB via TAI and TT
        try {
            double jdTAI = LeapSecondTable.utcToTAI(jdUTC);
            double jdTT = jdTAI + (32.184 / 86400.0);
            return ttToTDB(jdTT);
        } catch (IllegalArgumentException e) {
            // Before 1972, use TT approximation
            LOGGER.warning("Using TT approximation for pre-1972 date: " + e.getMessage());
            return jdUTC + (32.184 + 37.0) / 86400.0; // Rough approximation
        }
    }

    /** Convert TT to TDB (simplified version). */
    private static double ttToTDB(double jdTT) {
        double T = (jdTT - 2451545.0) / 36525.0;
        double M = Math.toRadians(357.5277233 + 35999.05034 * T);
        double deltaT = 0.001657 * Math.sin(M) + 0.000022 * Math.sin(2 * M);
        return jdTT + (deltaT / 86400.0);
    }

    /** Get frame count since initialization. */
    public int getFrameCount() {
        return frameCount;
    }

    @Override
    public String toString() {
        return String.format(
                "TimeManager[JD_TDB=%.6f, speed=%.2fx, paused=%s, frames=%d]",
                currentJulianDateTDB, simulationSpeed, paused, frameCount);
    }
}
