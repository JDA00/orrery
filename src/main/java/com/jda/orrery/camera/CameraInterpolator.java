package com.jda.orrery.camera;

import javafx.geometry.Point3D;

/**
 * Unified interpolation system for all camera movements.
 * Provides both continuous smoothing and progress-based easing.
 */
public class CameraInterpolator {

    // Standard smoothing factors for consistency
    public static final double SMOOTH_ROTATION = 0.15;
    public static final double SMOOTH_POSITION = 0.2;
    public static final double SMOOTH_ZOOM = 0.2;

    /**
     * Continuous exponential smoothing for values that continuously track a target.
     * Frame-rate independent when deltaTime is provided.
     */
    public static double smooth(double current, double target, double smoothness, double deltaTime) {
        // Frame-rate independent smoothing
        double adjustedSmoothness = 1.0 - Math.pow(1.0 - smoothness, deltaTime * 60.0);
        return current + (target - current) * adjustedSmoothness;
    }


    public static Point3D smoothPoint3D(Point3D current, Point3D target, double smoothness, double deltaTime) {
        double x = smooth(current.getX(), target.getX(), smoothness, deltaTime);
        double y = smooth(current.getY(), target.getY(), smoothness, deltaTime);
        double z = smooth(current.getZ(), target.getZ(), smoothness, deltaTime);
        return new Point3D(x, y, z);
    }

    /**
     * Smooth angles with proper wrapping for 360-degree values.
     */
    public static double smoothAngle(double current, double target, double smoothness, double deltaTime) {
        // Handle angle wrapping
        double difference = target - current;
        while (difference > 180) difference -= 360;
        while (difference < -180) difference += 360;

        return smooth(current, current + difference, smoothness, deltaTime);
    }

    /**
     * Progress-based easing for fixed-duration animations (0 to 1).
     */
    public static double ease(double progress, EasingType type) {
        // Clamp progress to valid range
        progress = Math.max(0, Math.min(1, progress));

        switch (type) {
            case LINEAR:
                return progress;
            case EASE_IN_OUT_CUBIC:
                return easeInOutCubic(progress);
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(progress);
            case EASE_OUT_QUAD:
                return easeOutQuad(progress);
            case EASE_OUT_CUBIC:
                return easeOutCubic(progress);
            default:
                return progress;
        }
    }

    /**
     * Update progress for fixed-duration animations.
     */
    public static double updateProgress(double currentProgress, double duration, double deltaTime) {
        if (duration <= 0) return 1.0;
        double increment = deltaTime / duration;
        return Math.min(currentProgress + increment, 1.0);
    }

    // Easing functions
    private static double easeInOutCubic(double t) {
        if (t < 0.5) {
            return 4 * t * t * t;
        } else {
            double p = 2 * t - 2;
            return 1 + p * p * p / 2;
        }
    }

    private static double easeInOutQuad(double t) {
        if (t < 0.5) {
            return 2 * t * t;
        } else {
            return 1 - Math.pow(-2 * t + 2, 2) / 2;
        }
    }

    private static double easeOutQuad(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private static double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    /**
     * Easing types available for progress-based animations.
     */
    public enum EasingType {
        LINEAR,
        EASE_IN_OUT_CUBIC,  // Smooth acceleration and deceleration
        EASE_IN_OUT_QUAD,   // Gentler than cubic
        EASE_OUT_QUAD,      // Fast start, slow finish
        EASE_OUT_CUBIC      // More dramatic slow-down
    }

    /**
     * Linear interpolation between two values based on progress.
     */
    public static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    /**
     * Linear interpolation between two angles with proper wrapping.
     */
    public static double lerpAngle(double start, double end, double t) {
        // Handle angle wrapping (e.g., interpolating from 350° to 10°)
        double difference = end - start;

        // Wrap to [-180, 180]
        while (difference > 180) difference -= 360;
        while (difference < -180) difference += 360;

        return start + difference * t;
    }

    public static Point3D lerpPoint3D(Point3D start, Point3D end, double t) {
        double x = lerp(start.getX(), end.getX(), t);
        double y = lerp(start.getY(), end.getY(), t);
        double z = lerp(start.getZ(), end.getZ(), t);
        return new Point3D(x, y, z);
    }
}