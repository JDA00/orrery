package com.jda.orrery.graphics.textures;

import com.jda.orrery.core.logging.Logging;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Texture system metrics for monitoring and debugging. Provides user-visible statistics about
 * texture loading, memory usage, and cache performance.
 */
public class TextureMetrics {
    private static final Logger LOGGER = Logging.logger(TextureMetrics.class);

    // Counters
    private final AtomicLong totalTexturesLoaded = new AtomicLong();
    private final AtomicLong totalMemoryUsed = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicInteger arraysCreated = new AtomicInteger();

    // Detailed tracking
    private final Map<String, BodyMetrics> bodyMetrics = new ConcurrentHashMap<>();
    private final Map<TextureResolver.TextureLayer, LayerMetrics> layerMetrics =
            new EnumMap<>(TextureResolver.TextureLayer.class);

    // Timing
    private long startTime = System.currentTimeMillis();
    private volatile long lastUpdateTime = System.currentTimeMillis();

    // Frame rate tracking (optional, set by external system)
    private volatile double currentFPS = 0.0;

    /** Per-body metrics. */
    private static class BodyMetrics {
        final String bodyId;
        final AtomicLong memoryUsed = new AtomicLong();
        final AtomicInteger textureCount = new AtomicInteger();
        final AtomicInteger maxResolution = new AtomicInteger();

        BodyMetrics(String bodyId) {
            this.bodyId = bodyId;
        }

        void recordTexture(int resolution, long bytes) {
            memoryUsed.addAndGet(bytes);
            textureCount.incrementAndGet();
            maxResolution.updateAndGet(current -> Math.max(current, resolution));
        }
    }

    /** Per-layer metrics. */
    private static class LayerMetrics {
        final TextureResolver.TextureLayer layer;
        final AtomicLong memoryUsed = new AtomicLong();
        final AtomicInteger textureCount = new AtomicInteger();

        LayerMetrics(TextureResolver.TextureLayer layer) {
            this.layer = layer;
        }

        void recordTexture(long bytes) {
            memoryUsed.addAndGet(bytes);
            textureCount.incrementAndGet();
        }
    }

    public TextureMetrics() {
        // Initialize layer metrics
        for (TextureResolver.TextureLayer layer : TextureResolver.TextureLayer.values()) {
            layerMetrics.put(layer, new LayerMetrics(layer));
        }
    }

    /** Record texture load event. */
    public void recordTextureLoad(
            String body, TextureResolver.TextureLayer layer, int resolution, long bytes) {
        totalTexturesLoaded.incrementAndGet();
        totalMemoryUsed.addAndGet(bytes);
        lastUpdateTime = System.currentTimeMillis();

        // Update body metrics
        bodyMetrics.computeIfAbsent(body, BodyMetrics::new).recordTexture(resolution, bytes);

        // Update layer metrics
        layerMetrics.get(layer).recordTexture(bytes);

        LOGGER.fine(
                String.format(
                        "Loaded %s %s @ %dk: %.1f MB",
                        body, layer, resolution / 1024, bytes / 1048576.0));
    }

    /** Record array creation. */
    public void recordArrayCreation(TextureResolver.TextureLayer layer, int size, int layers) {
        arraysCreated.incrementAndGet();
        long bytes = (long) size * size * 4 * layers;
        totalMemoryUsed.addAndGet(bytes);

        LOGGER.info(
                String.format(
                        "Created %s array: %dx%d, %d layers = %.1f MB",
                        layer, size, size, layers, bytes / 1048576.0));
    }

    /** Record cache hit. */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /** Record cache miss. */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    /** Set current FPS (called by render loop). */
    public void setCurrentFPS(double fps) {
        this.currentFPS = fps;
    }

    /** Get detailed statistics string for logging. */
    public String getDetailedStatistics() {
        long total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? 100.0 * cacheHits.get() / total : 0;
        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Texture System Statistics ===\n");
        sb.append(
                String.format(
                        "Uptime: %d:%02d:%02d\n",
                        elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60));
        sb.append(
                String.format(
                        "Memory: %.2f GB used\n",
                        totalMemoryUsed.get() / (1024.0 * 1024.0 * 1024.0)));
        sb.append(String.format("Textures: %d loaded\n", totalTexturesLoaded.get()));
        sb.append(String.format("Arrays: %d created\n", arraysCreated.get()));
        sb.append(
                String.format(
                        "Cache: %.1f%% hit rate (%d hits, %d misses)\n",
                        hitRate, cacheHits.get(), cacheMisses.get()));

        // Top memory users
        sb.append("\nTop 5 Memory Users:\n");
        bodyMetrics.values().stream()
                .sorted((a, b) -> Long.compare(b.memoryUsed.get(), a.memoryUsed.get()))
                .limit(5)
                .forEach(
                        metrics -> {
                            sb.append(
                                    String.format(
                                            "  %s: %.1f MB (%d textures, max %dk)\n",
                                            metrics.bodyId,
                                            metrics.memoryUsed.get() / 1048576.0,
                                            metrics.textureCount.get(),
                                            metrics.maxResolution.get() / 1024));
                        });

        // Layer breakdown
        sb.append("\nTextures by Layer:\n");
        layerMetrics.values().stream()
                .filter(m -> m.textureCount.get() > 0)
                .forEach(
                        metrics -> {
                            sb.append(
                                    String.format(
                                            "  %s: %d textures, %.1f MB\n",
                                            metrics.layer,
                                            metrics.textureCount.get(),
                                            metrics.memoryUsed.get() / 1048576.0));
                        });

        return sb.toString();
    }

    /** Get compact status line for HUD display. */
    public String getStatusLine() {
        long total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? 100.0 * cacheHits.get() / total : 0;

        return String.format(
                "Textures: %.1fGB | Cache: %.0f%% | Arrays: %d | FPS: %.0f",
                totalMemoryUsed.get() / (1024.0 * 1024.0 * 1024.0),
                hitRate,
                arraysCreated.get(),
                currentFPS);
    }

    /** Get simple statistics summary. */
    public String getSummary() {
        long total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? 100.0 * cacheHits.get() / total : 0;

        return String.format(
                "Textures: %d loaded, %.1f GB, %.0f%% cache hits",
                totalTexturesLoaded.get(),
                totalMemoryUsed.get() / (1024.0 * 1024.0 * 1024.0),
                hitRate);
    }

    /** Log detailed statistics. */
    public void logDetailedStatistics() {
        LOGGER.info(getDetailedStatistics());
    }

    /** Log periodic summary (less verbose). */
    public void logSummary() {
        LOGGER.info(getSummary());
    }

    /** Reset all metrics. */
    public void reset() {
        totalTexturesLoaded.set(0);
        totalMemoryUsed.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        arraysCreated.set(0);
        bodyMetrics.clear();
        layerMetrics
                .values()
                .forEach(
                        m -> {
                            m.memoryUsed.set(0);
                            m.textureCount.set(0);
                        });
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;

        LOGGER.info("Texture metrics reset");
    }

    /** Get metrics for a specific body. */
    public BodyStatistics getBodyStatistics(String bodyId) {
        BodyMetrics metrics = bodyMetrics.get(bodyId);
        if (metrics == null) {
            return new BodyStatistics(bodyId, 0, 0, 0);
        }

        return new BodyStatistics(
                bodyId,
                metrics.memoryUsed.get(),
                metrics.textureCount.get(),
                metrics.maxResolution.get());
    }

    public static class BodyStatistics {
        public final String bodyId;
        public final long memoryUsed;
        public final int textureCount;
        public final int maxResolution;

        BodyStatistics(String bodyId, long memory, int count, int maxRes) {
            this.bodyId = bodyId;
            this.memoryUsed = memory;
            this.textureCount = count;
            this.maxResolution = maxRes;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s: %d textures, %.1f MB, max %dk",
                    bodyId, textureCount, memoryUsed / 1048576.0, maxResolution / 1024);
        }
    }

    // Getters for individual metrics

    public long getTotalTexturesLoaded() {
        return totalTexturesLoaded.get();
    }

    public long getTotalMemoryUsed() {
        return totalMemoryUsed.get();
    }

    public double getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? 100.0 * cacheHits.get() / total : 0;
    }

    public int getArraysCreated() {
        return arraysCreated.get();
    }

    public double getCurrentFPS() {
        return currentFPS;
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
}
