package com.jda.orrery.core.math;

import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Immutable 3D vector with double precision for astronomical calculations. Provides seamless
 * conversion to LWJGL/JOML vector types for rendering.
 *
 * This class serves as the foundation for all astronomical position calculations, maintaining
 * precision across vast astronomical distances converting to float precision at the final rendering
 * stage.
 */
public final class Vec3d {
    /** The zero vector (0, 0, 0) */
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    /** Unit vector along X axis (1, 0, 0) */
    public static final Vec3d UNIT_X = new Vec3d(1, 0, 0);

    /** Unit vector along Y axis (0, 1, 0) */
    public static final Vec3d UNIT_Y = new Vec3d(0, 1, 0);

    /** Unit vector along Z axis (0, 0, 1) - Up in our astronomical coordinate system */
    public static final Vec3d UNIT_Z = new Vec3d(0, 0, 1);

    /** X component */
    public final double x;

    /** Y component */
    public final double y;

    /** Z component */
    public final double z;

    /**
     * Constructs a new Vec3d with the specified components.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Constructs a Vec3d from a JOML Vector3f.
     *
     * @param v the source vector
     * @return a new Vec3d with the same components
     */
    public static Vec3d fromVector3f(Vector3f v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    /**
     * Constructs a Vec3d from a JOML Vector3d.
     *
     * @param v the source vector
     * @return a new Vec3d with the same components
     */
    public static Vec3d fromVector3d(Vector3d v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    /**
     * Creates a Vec3d from spherical coordinates. Useful for positioning objects in orbit.
     *
     * @param radius distance from origin
     * @param azimuth angle in XY plane from X axis (radians)
     * @param elevation angle from XY plane (radians)
     * @return a new Vec3d in Cartesian coordinates
     */
    public static Vec3d fromSpherical(double radius, double azimuth, double elevation) {
        double cosElev = Math.cos(elevation);
        return new Vec3d(
                radius * cosElev * Math.cos(azimuth),
                radius * cosElev * Math.sin(azimuth),
                radius * Math.sin(elevation));
    }

    /**
     * Adds another vector to this one.
     *
     * @param other the vector to add
     * @return a new Vec3d representing the sum
     */
    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Subtracts another vector from this one.
     *
     * @param other the vector to subtract
     * @return a new Vec3d representing the difference
     */
    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiplies this vector by a scalar.
     *
     * @param scalar the multiplication factor
     * @return a new Vec3d scaled by the given factor
     */
    public Vec3d multiply(double scalar) {
        return new Vec3d(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Divides this vector by a scalar.
     *
     * @param scalar the division factor
     * @return a new Vec3d divided by the given factor
     * @throws IllegalArgumentException if scalar is zero
     */
    public Vec3d divide(double scalar) {
        if (scalar == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return new Vec3d(x / scalar, y / scalar, z / scalar);
    }

    /**
     * Computes the dot product with another vector.
     *
     * @param other the other vector
     * @return the dot product
     */
    public double dot(Vec3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Computes the cross product with another vector.
     *
     * @param other the other vector
     * @return a new Vec3d perpendicular to both vectors
     */
    public Vec3d cross(Vec3d other) {
        return new Vec3d(
                y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    /** Returns the length (magnitude) of this vector. */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Returns the squared length of this vector. More efficient than length() when comparing
     * distances.
     *
     * @return the squared length
     */
    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Returns a normalized (unit length) version of this vector.
     *
     * @return a new Vec3d with length 1
     * @throws IllegalArgumentException if this is the zero vector
     */
    public Vec3d normalize() {
        double len = length();
        if (len == 0) {
            throw new IllegalArgumentException("Cannot normalize zero vector");
        }
        return divide(len);
    }

    /**
     * Returns a normalized version of this vector, or the zero vector if length is zero.
     *
     * @return a new normalized Vec3d or zero vector
     */
    public Vec3d normalizeOrZero() {
        double len = length();
        if (len == 0) {
            return ZERO;
        }
        return divide(len);
    }

    /**
     * Computes the distance to another point.
     *
     * @param other the other point
     * @return the Euclidean distance
     */
    public double distanceTo(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Computes the squared distance to another point. More efficient than distanceTo() when
     * comparing distances.
     *
     * @param other the other point
     * @return the squared Euclidean distance
     */
    public double distanceSquaredTo(Vec3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Linearly interpolates between this vector and another.
     *
     * @param other the target vector
     * @param t interpolation parameter (0 = this, 1 = other)
     */
    public Vec3d lerp(Vec3d other, double t) {
        return new Vec3d(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
    }

    /**
     * Rotates this vector around the Z axis (astronomical "up").
     *
     * @param angleRadians rotation angle in radians
     */
    public Vec3d rotateZ(double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vec3d(x * cos - y * sin, x * sin + y * cos, z);
    }

    /**
     * Converts to a single-precision JOML Vector3f for rendering.
     *
     * <b>Precision note:</b> float has ~7 significant decimal digits, so values with magnitude
     * greater than ~{@code 1e7} lose sub-metre precision at astronomical scales. Intended for GPU
     * upload at the rendering boundary only — all domain calculations should stay in double.
     *
     * @return a new Vector3f with the same components
     */
    public Vector3f toVector3f() {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    /**
     * Converts to a double-precision JOML Vector3d.
     *
     * @return a new Vector3d with the same components
     */
    public Vector3d toVector3d() {
        return new Vector3d(x, y, z);
    }

    /**
     * Negates this vector.
     *
     * @return a new Vec3d pointing in the opposite direction
     */
    public Vec3d negate() {
        return new Vec3d(-x, -y, -z);
    }

    /**
     * Computes the angle between this vector and another in radians.
     *
     * @param other the other vector
     * @return angle in radians [0, PI]
     */
    public double angleTo(Vec3d other) {
        double dot = this.dot(other);
        double lenProduct = this.length() * other.length();
        if (lenProduct == 0) {
            return 0;
        }
        // Clamp to avoid numerical errors with acos
        double cosAngle = Math.max(-1, Math.min(1, dot / lenProduct));
        return Math.acos(cosAngle);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vec3d)) return false;
        Vec3d other = (Vec3d) obj;
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && Double.compare(z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x);
        bits = 31 * bits + Double.doubleToLongBits(y);
        bits = 31 * bits + Double.doubleToLongBits(z);
        return (int) (bits ^ (bits >>> 32));
    }

    @Override
    public String toString() {
        return String.format("Vec3d[%.6f, %.6f, %.6f]", x, y, z);
    }

    /**
     * Returns a string representation with the specified number of decimal places.
     *
     * @param decimals number of decimal places
     */
    public String toString(int decimals) {
        String format =
                String.format("Vec3d[%%.%df, %%.%df, %%.%df]", decimals, decimals, decimals);
        return String.format(format, x, y, z);
    }
}
