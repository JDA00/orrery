package com.jda.orrery.domain.ephemeris.cache;

import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.time.TimeContext;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Frame-based cache for ephemeris calculations.
 *
 * Within a single render frame, celestial bodies at the same TimeContext reuse calculated
 * positions. The map is keyed by body ID string only — correctness relies on the cache being
 * cleared whenever the frame's Julian Date changes (see TIME_EPSILON check in getState).
 *
 * Thread-safety: render-thread confined. Accessed only from the thread driving the render loop,
 * like the GL context it supports. Concurrent access from other threads would corrupt state.
 */
public class SimpleFrameCache implements EphemerisCache {

    private static final Logger LOGGER = Logging.logger(SimpleFrameCache.class);

    // Frame cache - keyed by body ID, cleared on time change
    private final Map<String, FramedState> frameCache = new HashMap<>(16);

    // Current frame's TimeContext for validation
    private TimeContext currentFrameTime = null;

    // Statistics tracking
    private final CacheStatistics statistics = new CacheStatistics();

    // Per-frame counters for periodic hit-rate logging.
    private int currentFrameHits = 0;
    private int currentFrameMisses = 0;

    // Configuration
    // FRAME_WINDOW: Maximum time difference to consider same frame for cache clearing
    // Should cover a full frame at 60 FPS plus margin for frame time variations
    private static final double FRAME_WINDOW = 2e-7; // ~17 milliseconds (60 FPS frame time)
    private static final double JUMP_THRESHOLD = 1.0; // Day threshold for time jump
    // TIME_EPSILON: Minimum time change to trigger cache clear (1e-15 JD ≈ 0.086 nanoseconds)
    // This catches any actual time change while ignoring floating-point noise
    private static final double TIME_EPSILON = 1e-15;

    @Override
    public FramedState getState(String bodyId, TimeContext time, StateCalculator calculator) {
        // Track if time has changed (not just frame number)
        // When paused, time stays the same even though frame number increments
        boolean timeChanged = false;
        if (currentFrameTime == null
                || Math.abs(time.getJulianDateTDB() - currentFrameTime.getJulianDateTDB())
                        > TIME_EPSILON) {
            timeChanged = true;

            // Log cache statistics periodically only (avoid frame spam).
            if (currentFrameTime != null && (currentFrameHits + currentFrameMisses) > 0) {
                double frameHitRate =
                        100.0 * currentFrameHits / (currentFrameHits + currentFrameMisses);
                // Log only every 3600 frames (~60 seconds at 60 FPS) regardless of hit rate
                // The 23% hit rate is normal for this cache since we query 13 bodies but only 3 are
                // satellites
                if (time.getFrameNumber() % 3600 == 0) {
                    LOGGER.fine(
                            String.format(
                                    "Frame %d: %d hits, %d misses (%.1f%% hit rate) | Cache size: %d",
                                    currentFrameTime.getFrameNumber(),
                                    currentFrameHits,
                                    currentFrameMisses,
                                    frameHitRate,
                                    frameCache.size()));
                }
            }

            // Clear cache when time changes
            // When paused, cache persists across frames since time is constant
            frameCache.clear();

            // Reset per-frame counters
            currentFrameHits = 0;
            currentFrameMisses = 0;
            currentFrameTime = time;
        }

        // Lookup by body ID — safe because cache clears on any JD change
        FramedState cached = frameCache.get(bodyId);
        if (cached != null) {
            statistics.recordFrameHit();
            currentFrameHits++;
            return cached;
        }

        // Calculate and cache
        long startNanos = System.nanoTime();
        FramedState calculated = calculator.calculate(time);
        long elapsedNanos = System.nanoTime() - startNanos;

        frameCache.put(bodyId, calculated);
        statistics.recordMiss(elapsedNanos);
        currentFrameMisses++;
        statistics.updateCacheSizes(frameCache.size(), 0);

        return calculated;
    }

    @Override
    public void clearFrame() {
        // Log final stats for the frame being cleared
        if (currentFrameTime != null && (currentFrameHits + currentFrameMisses) > 0) {
            double frameHitRate =
                    100.0 * currentFrameHits / (currentFrameHits + currentFrameMisses);
            LOGGER.fine(
                    String.format(
                            "Clearing cache - Frame %d had %.1f%% hit rate (%d hits, %d misses)",
                            currentFrameTime.getFrameNumber(),
                            frameHitRate,
                            currentFrameHits,
                            currentFrameMisses));
        }

        frameCache.clear();
        currentFrameTime = null;
        currentFrameHits = 0;
        currentFrameMisses = 0;
        statistics.updateCacheSizes(0, 0);
    }

    @Override
    public void onTimeJump(double deltaJD) {
        if (Math.abs(deltaJD) > JUMP_THRESHOLD) {
            LOGGER.fine(String.format("Time jump detected: %.2f days - clearing cache", deltaJD));
            clearFrame();
            // In future phases, pre-calculate new time window here
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }
}
