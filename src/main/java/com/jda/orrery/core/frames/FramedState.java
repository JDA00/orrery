package com.jda.orrery.core.frames;

import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import java.util.Objects;

/**
 * A state vector (position and velocity) tagged with its reference frame. Every position or
 * velocity is associated with an explicit frame to avoid coordinate confusion. Immutable —
 * transformations create new instances.
 */
public class FramedState {

    private final Vec3d position; // Position in AU (absolute or relative)
    private final Vec3d velocity; // Velocity in AU/day (absolute or relative)
    private final String frame; // Reference frame name (SPICE convention)
    private final double et; // Ephemeris time (seconds since J2000)
    private final double accuracy; // Accuracy in arcseconds (optional)
    private final boolean isRelative; // true if position/velocity are relative to parent

    /**
     * Create a framed state with all parameters.
     *
     * @param position Position vector in AU (absolute or relative)
     * @param velocity Velocity vector in AU/day (absolute or relative)
     * @param frame Reference frame name (e.g., "J2000", "ECLIPJ2000")
     * @param et Ephemeris time in seconds since J2000
     * @param accuracy Estimated accuracy in arcseconds
     * @param isRelative true if position/velocity are relative to parent body
     */
    public FramedState(
            Vec3d position,
            Vec3d velocity,
            String frame,
            double et,
            double accuracy,
            boolean isRelative) {
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.velocity = Objects.requireNonNull(velocity, "Velocity cannot be null");
        this.frame = Objects.requireNonNull(frame, "Frame cannot be null");
        this.et = et;
        this.accuracy = accuracy;
        this.isRelative = isRelative;
    }

    /**
     * Create an absolute framed state (most common case).
     *
     * @param position Absolute position vector in AU
     * @param velocity Absolute velocity vector in AU/day
     * @param frame Reference frame name
     * @param et Ephemeris time in seconds since J2000
     * @param accuracy Estimated accuracy in arcseconds
     */
    public FramedState(Vec3d position, Vec3d velocity, String frame, double et, double accuracy) {
        this(position, velocity, frame, et, accuracy, false);
    }

    /** Create a framed state without accuracy information. */
    public FramedState(Vec3d position, Vec3d velocity, String frame, double et) {
        this(position, velocity, frame, et, Double.NaN);
    }

    /** Create from TimeContext (convenience constructor). */
    public FramedState(Vec3d position, Vec3d velocity, String frame, TimeContext time) {
        this(position, velocity, frame, time.getEphemerisTime(), Double.NaN);
    }

    /** Create a new state with updated position/velocity but same frame. */
    public FramedState withState(Vec3d newPosition, Vec3d newVelocity) {
        return new FramedState(newPosition, newVelocity, frame, et, accuracy, isRelative);
    }

    /**
     * Create a new state in a different frame (for use by FrameManager). Note: isRelative flag is
     * preserved as coordinates maintain their nature.
     */
    public FramedState inFrame(String newFrame, Vec3d transformedPos, Vec3d transformedVel) {
        return new FramedState(transformedPos, transformedVel, newFrame, et, accuracy, isRelative);
    }

    // Accessors

    public Vec3d getPosition() {
        return position;
    }

    public Vec3d getVelocity() {
        return velocity;
    }

    public String getFrame() {
        return frame;
    }

    public double getET() {
        return et;
    }

    public double getAccuracy() {
        return accuracy;
    }

    /**
     * Check if this state contains relative coordinates.
     *
     * @return true if position/velocity are relative to parent body
     */
    public boolean isRelative() {
        return isRelative;
    }

    /** Check if this state is in a specific frame. */
    public boolean isInFrame(String frameToCheck) {
        return frame.equals(frameToCheck);
    }

    /** Get the distance from origin in AU. */
    public double getDistanceAU() {
        return position.length();
    }

    /** Get the speed in AU/day. */
    public double getSpeedAUPerDay() {
        return velocity.length();
    }

    @Override
    public String toString() {
        return String.format(
                "FramedState[frame=%s, pos=(%.6f, %.6f, %.6f) AU, vel=(%.6f, %.6f, %.6f) AU/day, et=%.1f]",
                frame, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z, et);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FramedState)) return false;

        FramedState other = (FramedState) obj;
        return position.equals(other.position)
                && velocity.equals(other.velocity)
                && frame.equals(other.frame)
                && Double.compare(et, other.et) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, velocity, frame, et);
    }
}
