package com.jda.orrery.graphics.core;

/** Tracks frame rendering statistics for performance monitoring. */
public class FrameStatistics {
    private long frameCount = 0;
    private long frameStartTime;
    private long lastFrameTime;
    private double fps = 0;
    private double frameTime = 0;

    // For averaging
    private static final int FPS_SAMPLE_SIZE = 60;
    private double[] fpsSamples = new double[FPS_SAMPLE_SIZE];
    private double[] frameTimeSamples = new double[FPS_SAMPLE_SIZE];
    private int sampleIndex = 0;

    // Additional statistics for better reporting
    private double avgFrameTime = 0;
    private double minFrameTime = Double.MAX_VALUE;
    private double maxFrameTime = 0;
    private double frameTimeStdDev = 0;
    private int spikesOver16ms = 0;
    private int spikesOver33ms = 0;
    private long lastResetTime = System.nanoTime();

    /** Call at the beginning of each frame */
    public void beginFrame() {
        frameStartTime = System.nanoTime();

        if (lastFrameTime > 0) {
            long elapsed = frameStartTime - lastFrameTime;
            frameTime = elapsed / 1_000_000.0; // Convert to milliseconds

            // Track frame time samples for averaging
            frameTimeSamples[sampleIndex] = frameTime;

            // Track spikes
            if (frameTime > 16.67) spikesOver16ms++; // 60 FPS threshold
            if (frameTime > 33.33) spikesOver33ms++; // 30 FPS threshold

            // Update min/max
            if (frameTime < minFrameTime) minFrameTime = frameTime;
            if (frameTime > maxFrameTime) maxFrameTime = frameTime;

            // Instant FPS
            double instantFps = 1_000_000_000.0 / elapsed;

            // Average FPS over samples
            fpsSamples[sampleIndex] = instantFps;
            sampleIndex = (sampleIndex + 1) % FPS_SAMPLE_SIZE;

            // Calculate averages
            double fpsSum = 0;
            double frameTimeSum = 0;
            int validSamples = 0;

            for (int i = 0; i < FPS_SAMPLE_SIZE; i++) {
                if (fpsSamples[i] > 0) {
                    fpsSum += fpsSamples[i];
                    frameTimeSum += frameTimeSamples[i];
                    validSamples++;
                }
            }

            if (validSamples > 0) {
                fps = fpsSum / validSamples;
                avgFrameTime = frameTimeSum / validSamples;

                // Calculate standard deviation for frame times
                double variance = 0;
                for (int i = 0; i < FPS_SAMPLE_SIZE; i++) {
                    if (frameTimeSamples[i] > 0) {
                        double diff = frameTimeSamples[i] - avgFrameTime;
                        variance += diff * diff;
                    }
                }
                frameTimeStdDev = Math.sqrt(variance / validSamples);
            }
        }

        lastFrameTime = frameStartTime;
    }

    /** Call at the end of each frame */
    public void endFrame() {
        frameCount++;
    }

    /** Get total number of frames rendered */
    public long getFrameCount() {
        return frameCount;
    }

    /** Get current frames per second (averaged) */
    public double getFramesPerSecond() {
        return fps;
    }

    /** Get time taken for last frame in milliseconds */
    public double getFrameTime() {
        return frameTime;
    }

    /** Get average frame time over sample window */
    public double getAverageFrameTime() {
        return avgFrameTime;
    }

    /** Get minimum frame time since last reset */
    public double getMinFrameTime() {
        return minFrameTime == Double.MAX_VALUE ? 0 : minFrameTime;
    }

    /** Get maximum frame time since last reset */
    public double getMaxFrameTime() {
        return maxFrameTime;
    }

    /** Get frame time standard deviation */
    public double getFrameTimeStdDev() {
        return frameTimeStdDev;
    }

    /** Get 99th percentile frame time (approximation using max) */
    public double get99thPercentileFrameTime() {
        // Simple approximation - in practice you'd track this properly
        return maxFrameTime;
    }

    /** Get count of frames that exceeded 16.67ms (60 FPS threshold) */
    public int getSpikesOver16ms() {
        return spikesOver16ms;
    }

    /** Get count of frames that exceeded 33.33ms (30 FPS threshold) */
    public int getSpikesOver33ms() {
        return spikesOver33ms;
    }

    /** Get time since last reset in seconds */
    public double getTimeSinceReset() {
        return (System.nanoTime() - lastResetTime) / 1_000_000_000.0;
    }

    /** Reset all statistics */
    public void reset() {
        frameCount = 0;
        frameStartTime = 0;
        lastFrameTime = 0;
        fps = 0;
        frameTime = 0;
        avgFrameTime = 0;
        minFrameTime = Double.MAX_VALUE;
        maxFrameTime = 0;
        frameTimeStdDev = 0;
        spikesOver16ms = 0;
        spikesOver33ms = 0;
        fpsSamples = new double[FPS_SAMPLE_SIZE];
        frameTimeSamples = new double[FPS_SAMPLE_SIZE];
        sampleIndex = 0;
        lastResetTime = System.nanoTime();
    }
}
