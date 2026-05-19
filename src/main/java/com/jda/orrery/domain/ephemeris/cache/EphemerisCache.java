package com.jda.orrery.domain.ephemeris.cache;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.time.TimeContext;

/**
 * Cache interface for ephemeris calculations.
 *
 * Provides a clean abstraction over caching strategy: bodies depend on this interface, not on
 * any concrete implementation. The cache handles temporal storage; bodies handle the astronomy.
 */
public interface EphemerisCache {

    /**
     * Get the state of a celestial body, using cache if available.
     *
     * This method implements a multi-tier caching strategy: 1. Check frame cache for exact
     * TimeContext match 2. Check temporal cache for recent calculations 3. Calculate if cache miss
     * and store result
     *
     * @param bodyId Unique identifier for the celestial body
     * @param time The time context for the state
     * @param calculator Function to calculate state if not cached
     * @return The framed state at the given time
     */
    FramedState getState(String bodyId, TimeContext time, StateCalculator calculator);

    /**
     * Clear the frame cache at the start of a new frame.
     *
     * Called by the render loop to prepare for a new frame. Frame cache provides >95% hit rate
     * within a single render frame.
     */
    void clearFrame();

    /**
     * Handle a significant time jump.
     *
     * Called when simulation time changes by more than the threshold. May clear caches and
     * pre-calculate new time window.
     *
     * @param deltaJD Change in Julian date
     */
    void onTimeJump(double deltaJD);

    /**
     * Get cache statistics for monitoring.
     *
     * @return Current cache statistics
     */
    CacheStatistics getStatistics();

    /**
     * Functional interface for state calculation.
     *
     * This allows bodies to provide their calculation logic without the cache knowing
     * implementation details.
     */
    @FunctionalInterface
    interface StateCalculator {
        /**
         * Calculate the state for a given time.
         *
         * @param time The time context
         * @return The calculated framed state
         */
        FramedState calculate(TimeContext time);
    }
}
