package com.jda.orrery.core.frames;

import static org.junit.jupiter.api.Assertions.*;

import com.jda.orrery.core.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Round-trip and contract tests for {@link FrameManager} backed by {@link BuiltInFrameKernel}.
 *
 * Focus: - A → B → A should preserve components to near-machine precision (rotation matrices are
 * orthogonal; numerical error comes only from repeated float/double operations). - The allocating
 * {@link FrameManager#getTransformMatrix} and the zero-alloc {@link
 * FrameManager#getTransformMatrixInto} must produce equal matrices. - The "returned matrix is
 * cached; do not mutate" contract documented on {@link FrameManager#getTransformMatrix} is
 * preserved.
 */
public class FrameManagerTest {

    /** Tight tolerance for small-magnitude round-trips (unit-scale inputs). */
    private static final double EPSILON = 1e-12;

    /** Relative tolerance for large (astronomical) magnitudes. */
    private static final double RELATIVE_EPSILON = 1e-11;

    /** Ephemeris time at J2000 epoch (0 seconds past J2000). */
    private static final double ET_J2000 = 0.0;

    private FrameManager frameManager;

    @BeforeEach
    void setUp() {
        // Fresh instance per test so the private transformCache can't leak
        // state between tests.
        frameManager = new FrameManager(new BuiltInFrameKernel());
    }

    // ────────────────────────────────────────────────────────────────────
    // Round-trip tests: the headline feature
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("J2000 → ECLIPJ2000 → J2000 preserves position")
    void roundTrip_J2000_ECLIPJ2000_preservesPosition() {
        FramedState original = stateAt(new Vec3d(1.5, 2.3, -0.7), Vec3d.ZERO, FrameNames.J2000);

        FramedState ecliptic = transformToFrame(original, FrameNames.ECLIPJ2000);
        FramedState back = transformToFrame(ecliptic, FrameNames.J2000);

        assertVec3dEquals(original.getPosition(), back.getPosition(), EPSILON);
    }

    @Test
    @DisplayName("J2000 → OPENGL_RENDER → J2000 preserves position")
    void roundTrip_J2000_OPENGL_RENDER_preservesPosition() {
        FramedState original = stateAt(new Vec3d(0.8, -1.2, 0.5), Vec3d.ZERO, FrameNames.J2000);

        FramedState rendered = transformToFrame(original, FrameNames.OPENGL_RENDER);
        FramedState back = transformToFrame(rendered, FrameNames.J2000);

        assertVec3dEquals(original.getPosition(), back.getPosition(), EPSILON);
    }

    @Test
    @DisplayName("ECLIPJ2000 → OPENGL_RENDER → ECLIPJ2000 preserves position (via-J2000 path)")
    void roundTrip_ECLIPJ2000_OPENGL_RENDER_preservesPosition() {
        FramedState original =
                stateAt(new Vec3d(2.1, 0.9, -1.3), Vec3d.ZERO, FrameNames.ECLIPJ2000);

        FramedState rendered = transformToFrame(original, FrameNames.OPENGL_RENDER);
        FramedState back = transformToFrame(rendered, FrameNames.ECLIPJ2000);

        assertVec3dEquals(original.getPosition(), back.getPosition(), EPSILON);
    }

    @Test
    @DisplayName("round-trip preserves velocity (same matrix applies to both)")
    void roundTrip_preservesVelocity() {
        Vec3d position = new Vec3d(1.0, 0.0, 0.0);
        Vec3d velocity = new Vec3d(0.02, -0.01, 0.03);
        FramedState original = stateAt(position, velocity, FrameNames.J2000);

        FramedState ecliptic = transformToFrame(original, FrameNames.ECLIPJ2000);
        FramedState back = transformToFrame(ecliptic, FrameNames.J2000);

        assertVec3dEquals(original.getVelocity(), back.getVelocity(), EPSILON);
    }

    @ParameterizedTest(name = "round-trip preserves position at magnitude {0}")
    @ValueSource(doubles = {1.0, 100.0, 10000.0})
    @DisplayName("round-trip preserves position across astronomical magnitudes")
    void roundTrip_atAstronomicalMagnitudes(double magnitude) {
        Vec3d original = new Vec3d(magnitude * 0.7, magnitude * 0.5, magnitude * 0.3);
        FramedState state = stateAt(original, Vec3d.ZERO, FrameNames.J2000);

        FramedState ecliptic = transformToFrame(state, FrameNames.ECLIPJ2000);
        FramedState back = transformToFrame(ecliptic, FrameNames.J2000);

        // Absolute error scales with magnitude; use a relative tolerance.
        double tolerance = magnitude * RELATIVE_EPSILON;
        assertVec3dEquals(original, back.getPosition(), tolerance);
    }

    // ────────────────────────────────────────────────────────────────────
    // Obliquity sanity check: is the rotation actually applied correctly?
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("J2000 → ECLIPJ2000 rotates +Y by the IAU obliquity")
    void obliquityRotation_rotatesYAxis() {
        // JOML's rotationX(θ) applied to (0, 1, 0) yields (0, cos θ, sin θ).
        // A round-trip would hide a wrong rotation angle; pin the absolute values too.
        double obl = Math.toRadians(23.4392911);
        FramedState original = stateAt(new Vec3d(0.0, 1.0, 0.0), Vec3d.ZERO, FrameNames.J2000);

        FramedState ecliptic = transformToFrame(original, FrameNames.ECLIPJ2000);

        Vec3d expected = new Vec3d(0.0, Math.cos(obl), Math.sin(obl));
        assertVec3dEquals(expected, ecliptic.getPosition(), EPSILON);
    }

    // ────────────────────────────────────────────────────────────────────
    // Identity transforms
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("J2000 → J2000 returns input unchanged")
    void identityTransform_J2000_returnsInputExactly() {
        FramedState original = stateAt(new Vec3d(3.14, -2.72, 1.41), Vec3d.ZERO, FrameNames.J2000);

        FramedState result = transformToFrame(original, FrameNames.J2000);

        // Implementation short-circuits when source == target; components are
        // exactly equal, not merely close.
        assertEquals(original.getPosition().x, result.getPosition().x);
        assertEquals(original.getPosition().y, result.getPosition().y);
        assertEquals(original.getPosition().z, result.getPosition().z);
    }

    @Test
    @DisplayName("OPENGL_RENDER → OPENGL_RENDER returns input unchanged")
    void identityTransform_OPENGL_RENDER_returnsInputExactly() {
        FramedState original =
                stateAt(new Vec3d(1.0, 2.0, 3.0), Vec3d.ZERO, FrameNames.OPENGL_RENDER);

        FramedState result = transformToFrame(original, FrameNames.OPENGL_RENDER);

        assertEquals(original.getPosition().x, result.getPosition().x);
        assertEquals(original.getPosition().y, result.getPosition().y);
        assertEquals(original.getPosition().z, result.getPosition().z);
    }

    // ────────────────────────────────────────────────────────────────────
    // Matrix API equivalence: the two matrix accessors must agree
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTransformMatrix() and getTransformMatrixInto() return equal matrices")
    void getTransformMatrix_andMatrixInto_agree() {
        Matrix3d allocating =
                frameManager.getTransformMatrix(FrameNames.J2000, FrameNames.ECLIPJ2000, ET_J2000);
        Matrix3d target = new Matrix3d();
        Matrix3d intoResult =
                frameManager.getTransformMatrixInto(
                        FrameNames.J2000, FrameNames.ECLIPJ2000, ET_J2000, target);

        assertNotNull(allocating);
        assertNotNull(intoResult);
        assertSame(target, intoResult, "getTransformMatrixInto should return the provided matrix");
        assertMatrix3dEquals(allocating, target, EPSILON);
    }

    // ────────────────────────────────────────────────────────────────────
    // Cache contract (from slice 2c javadoc: "returned matrix is cached;
    // do not mutate")
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTransformMatrix returns the same cached instance on repeated calls")
    void getTransformMatrix_returnsCachedReferenceOnSecondCall() {
        Matrix3d first =
                frameManager.getTransformMatrix(FrameNames.J2000, FrameNames.ECLIPJ2000, ET_J2000);
        Matrix3d second =
                frameManager.getTransformMatrix(FrameNames.J2000, FrameNames.ECLIPJ2000, ET_J2000);

        assertSame(
                first,
                second,
                "Cache hit should return the same Matrix3d instance (no defensive copy)");
    }

    @Test
    @DisplayName("getTransformMatrix returns null for unavailable transforms")
    void getTransformMatrix_unavailableTransform_returnsNull() {
        Matrix3d result =
                frameManager.getTransformMatrix("NOT_A_REAL_FRAME", FrameNames.J2000, ET_J2000);

        assertNull(result);
    }

    // ────────────────────────────────────────────────────────────────────
    // ICRF aliasing — ICRF is treated as J2000 (sub-microarcsec difference
    // is within our tolerance)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ICRF → ECLIPJ2000 matches J2000 → ECLIPJ2000")
    void ICRF_treatedAsJ2000_forKnownTransforms() {
        Vec3d position = new Vec3d(0.5, 1.2, -0.9);

        FramedState viaICRF =
                transformToFrame(
                        stateAt(position, Vec3d.ZERO, FrameNames.ICRF), FrameNames.ECLIPJ2000);
        FramedState viaJ2000 =
                transformToFrame(
                        stateAt(position, Vec3d.ZERO, FrameNames.J2000), FrameNames.ECLIPJ2000);

        assertVec3dEquals(viaJ2000.getPosition(), viaICRF.getPosition(), EPSILON);
    }

    // ────────────────────────────────────────────────────────────────────
    // Error handling — unavailable transforms throw
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("transformInto() throws IllegalArgumentException for unknown target frame")
    void transformInto_unknownFrame_throwsIllegalArgumentException() {
        FramedState state = stateAt(new Vec3d(1, 0, 0), Vec3d.ZERO, FrameNames.J2000);
        Vector3d posOut = new Vector3d();
        Vector3d velOut = new Vector3d();

        assertThrows(
                IllegalArgumentException.class,
                () -> frameManager.transformInto(state, "NOT_A_REAL_FRAME", posOut, velOut));
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private static FramedState stateAt(Vec3d position, Vec3d velocity, String frame) {
        return new FramedState(position, velocity, frame, ET_J2000);
    }

    /**
     * Allocating convenience for tests only. Production code uses {@link
     * FrameManager#transformInto} with caller-supplied scratch vectors.
     */
    private FramedState transformToFrame(FramedState state, String targetFrame) {
        Vector3d posOut = new Vector3d();
        Vector3d velOut = new Vector3d();
        frameManager.transformInto(state, targetFrame, posOut, velOut);
        return state.inFrame(
                targetFrame,
                new Vec3d(posOut.x, posOut.y, posOut.z),
                new Vec3d(velOut.x, velOut.y, velOut.z));
    }

    private static void assertVec3dEquals(Vec3d expected, Vec3d actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance, "x component");
        assertEquals(expected.y, actual.y, tolerance, "y component");
        assertEquals(expected.z, actual.z, tolerance, "z component");
    }

    private static void assertMatrix3dEquals(Matrix3d expected, Matrix3d actual, double tolerance) {
        assertEquals(expected.m00, actual.m00, tolerance, "m00");
        assertEquals(expected.m01, actual.m01, tolerance, "m01");
        assertEquals(expected.m02, actual.m02, tolerance, "m02");
        assertEquals(expected.m10, actual.m10, tolerance, "m10");
        assertEquals(expected.m11, actual.m11, tolerance, "m11");
        assertEquals(expected.m12, actual.m12, tolerance, "m12");
        assertEquals(expected.m20, actual.m20, tolerance, "m20");
        assertEquals(expected.m21, actual.m21, tolerance, "m21");
        assertEquals(expected.m22, actual.m22, tolerance, "m22");
    }
}
