package com.jda.orrery.domain.ephemeris.cache;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics tracking for ephemeris cache performance.
 *
 * Thread-safe implementation for concurrent access from multiple threads. Provides metrics for
 * monitoring cache effectiveness and optimization.
 */
public class CacheStatistics {

    // Hit counters
    private final AtomicInteger frameHits = new AtomicInteger(0);
    private final AtomicInteger temporalHits = new AtomicInteger(0);
    private final AtomicInteger misses = new AtomicInteger(0);

    // Timing metrics
    private final AtomicLong totalCalculationTimeNanos = new AtomicLong(0);
    private final AtomicInteger calculationCount = new AtomicInteger(0);

    // Memory metrics
    private int frameCacheSize = 0;
    private int temporalCacheSize = 0;

    /** Record a frame cache hit. */
    public void recordFrameHit() {
        frameHits.incrementAndGet();
    }

    /** Record a temporal cache hit. */
    public void recordTemporalHit() {
        temporalHits.incrementAndGet();
    }

    /**
     * Record a cache miss requiring calculation.
     *
     * @param calculationTimeNanos Time taken to calculate in nanoseconds
     */
    public void recordMiss(long calculationTimeNanos) {
        misses.incrementAndGet();
        totalCalculationTimeNanos.addAndGet(calculationTimeNanos);
        calculationCount.incrementAndGet();
    }

    /**
     * Update cache size metrics.
     *
     * @param frameSize Current frame cache size
     * @param temporalSize Current temporal cache size
     */
    public void updateCacheSizes(int frameSize, int temporalSize) {
        this.frameCacheSize = frameSize;
        this.temporalCacheSize = temporalSize;
    }

    /** Get frame cache hit rate as percentage. */
    public double getFrameHitRate() {
        int total = getTotalRequests();
        return total > 0 ? (100.0 * frameHits.get() / total) : 0.0;
    }

    /** Get temporal cache hit rate as percentage. */
    public double getTemporalHitRate() {
        int total = getTotalRequests();
        return total > 0 ? (100.0 * temporalHits.get() / total) : 0.0;
    }

    /** Get cache miss rate as percentage. */
    public double getMissRate() {
        int total = getTotalRequests();
        return total > 0 ? (100.0 * misses.get() / total) : 0.0;
    }

    /** Get average calculation time in milliseconds. */
    public double getAverageCalculationTimeMs() {
        int count = calculationCount.get();
        if (count == 0) return 0.0;
        return totalCalculationTimeNanos.get() / (count * 1_000_000.0);
    }

    /** Get total number of cache requests. */
    public int getTotalRequests() {
        return frameHits.get() + temporalHits.get() + misses.get();
    }

    /** Get current frame cache size. */
    public int getFrameCacheSize() {
        return frameCacheSize;
    }

    /** Get current temporal cache size. */
    public int getTemporalCacheSize() {
        return temporalCacheSize;
    }

    /** Reset all statistics. */
    public void reset() {
        frameHits.set(0);
        temporalHits.set(0);
        misses.set(0);
        totalCalculationTimeNanos.set(0);
        calculationCount.set(0);
        frameCacheSize = 0;
        temporalCacheSize = 0;
    }

    /** Get formatted statistics string for logging. */
    @Override
    public String toString() {
        return String.format(
                "Cache Stats: Frame=%.1f%%, Temporal=%.1f%%, Miss=%.1f%%, "
                        + "AvgCalc=%.2fms, Sizes[F=%d,T=%d]",
                getFrameHitRate(),
                getTemporalHitRate(),
                getMissRate(),
                getAverageCalculationTimeMs(),
                frameCacheSize,
                temporalCacheSize);
    }
}
