package com.jda.orrery.graphics.ubo;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

import com.jda.orrery.core.logging.Logging;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

/**
 * Uniform Buffer Object for celestial body rendering.
 *
 * Single UBO updated per body. Uses std140
 * layout for cross-platform compatibility.
 *
 * Memory layout (std140 rules): - mat4: 64 bytes (4x vec4, each 16-byte aligned) - mat3: 48
 * bytes (3x vec4, each 16-byte aligned, last component unused) - vec4: 16 bytes - vec3: 16 bytes
 * (padded to vec4) - float: 4 bytes (but often padded to 16 for alignment)
 *
 * Total size: 384 bytes per body
 */
public class CelestialUBO {
    private static final Logger LOGGER = Logging.logger(CelestialUBO.class);

    // std140 layout constants
    private static final int MAT4_SIZE = 64; // 4x4 matrix
    private static final int MAT3_SIZE = 48; // 3x3 matrix (3x vec4 with padding)
    private static final int VEC4_SIZE = 16; // vec4 or vec3 padded
    private static final int VEC3_SIZE = 16; // vec3 always padded to vec4
    private static final int FLOAT_SIZE = 4; // single float

    // Offsets in bytes (std140 layout)
    // Matrices (all 16-byte aligned)
    private static final int OFFSET_MODEL_MATRIX = 0; // mat4: offset 0, size 64
    private static final int OFFSET_VIEW_MATRIX = 64; // mat4: offset 64, size 64
    private static final int OFFSET_PROJ_MATRIX = 128; // mat4: offset 128, size 64
    private static final int OFFSET_MVP_MATRIX = 192; // mat4: offset 192, size 64
    private static final int OFFSET_NORMAL_MATRIX = 256; // mat3: offset 256, size 48

    // Vectors (all padded to 16 bytes in std140)
    private static final int OFFSET_SUN_POSITION = 304; // vec3: offset 304, size 16
    private static final int OFFSET_ALBEDO = 320; // vec3: offset 320, size 16
    private static final int OFFSET_EMISSION = 336; // vec3: offset 336, size 16

    // Scalars (packed together after vectors)
    private static final int OFFSET_ROUGHNESS = 352; // float: offset 352
    private static final int OFFSET_METALLIC = 356; // float: offset 356
    private static final int OFFSET_EMISSION_STRENGTH = 360; // float: offset 360
    private static final int OFFSET_IS_EMISSIVE = 364; // float: offset 364

    // Per-body geometry: vec4(equatorialRadius, polarRadius, ringInner, ringOuter)
    // Visual-space radii (after ScaleManager). ringInner/ringOuter in planet-radii units;
    // both 0 indicates the body has no rings (shader gates on bodyGeometry.w > 0).
    private static final int OFFSET_BODY_GEOMETRY = 368; // vec4: offset 368, size 16

    // Total size must be multiple of 16 for std140
    public static final int BUFFER_SIZE = 384; // 368 + 16 (body geometry vec4)

    // Buffer resources
    private final ByteBuffer dataBuffer;
    private int uboId = -1;
    private int bindingPoint = 0;

    // Cached data for change detection
    private final Matrix4f lastModelMatrix = new Matrix4f();
    private final Matrix4f lastViewMatrix = new Matrix4f();
    private boolean needsUpdate = true;

    /**
     * Create a new CelestialUBO.
     *
     * @param bindingPoint The binding point for this UBO (typically 0 for celestial data)
     */
    public CelestialUBO(int bindingPoint) {
        this.bindingPoint = bindingPoint;
        this.dataBuffer = BufferUtils.createByteBuffer(BUFFER_SIZE);

        // Initialize UBO
        createUBO();
    }

    /** Create the OpenGL UBO. */
    private void createUBO() {
        uboId = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, uboId);

        // Allocate storage with dynamic draw hint (updated frequently)
        glBufferData(GL_UNIFORM_BUFFER, BUFFER_SIZE, GL_DYNAMIC_DRAW);

        // Bind to the specified binding point
        glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);

        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        LOGGER.fine("Created CelestialUBO with ID " + uboId + " at binding point " + bindingPoint);
    }

    /** Update all matrices at once (most common operation). */
    public void updateMatrices(
            Matrix4f model, Matrix4f view, Matrix4f projection, Matrix4f mvp, Matrix3f normal) {
        dataBuffer.position(OFFSET_MODEL_MATRIX);
        model.get(dataBuffer);

        dataBuffer.position(OFFSET_VIEW_MATRIX);
        view.get(dataBuffer);

        dataBuffer.position(OFFSET_PROJ_MATRIX);
        projection.get(dataBuffer);

        dataBuffer.position(OFFSET_MVP_MATRIX);
        mvp.get(dataBuffer);

        // Normal matrix (mat3 in std140 = 3x vec4)
        dataBuffer.position(OFFSET_NORMAL_MATRIX);
        writeMatrix3AsStd140(normal, dataBuffer);

        needsUpdate = true;
    }

    /** Update material properties. */
    public void updateMaterial(
            Vector3f albedo,
            float roughness,
            float metallic,
            Vector3f emission,
            float emissionStrength,
            boolean isEmissive) {
        dataBuffer.position(OFFSET_ALBEDO);
        dataBuffer.putFloat(albedo.x);
        dataBuffer.putFloat(albedo.y);
        dataBuffer.putFloat(albedo.z);
        dataBuffer.putFloat(0); // Padding for vec3 → vec4

        dataBuffer.position(OFFSET_EMISSION);
        dataBuffer.putFloat(emission.x);
        dataBuffer.putFloat(emission.y);
        dataBuffer.putFloat(emission.z);
        dataBuffer.putFloat(0); // Padding for vec3 → vec4

        dataBuffer.position(OFFSET_ROUGHNESS);
        dataBuffer.putFloat(roughness);

        dataBuffer.position(OFFSET_METALLIC);
        dataBuffer.putFloat(metallic);

        dataBuffer.position(OFFSET_EMISSION_STRENGTH);
        dataBuffer.putFloat(emissionStrength);

        dataBuffer.position(OFFSET_IS_EMISSIVE);
        dataBuffer.putFloat(isEmissive ? 1.0f : 0.0f);

        needsUpdate = true;
    }

    /**
     * Update per-body geometry: equatorial / polar radii (visual-space) and ring inner / outer
     * extent (planet-radii units, 0 if no rings).
     *
     * Must be called for every draw — the UBO is one buffer shared across pass 0 (bodies) and
     * pass 1 (rings), so leaving this stale would let a ring draw read whatever the last body
     * wrote.
     */
    public void updateBodyGeometry(
            float equatorialRadius, float polarRadius, float ringInner, float ringOuter) {
        dataBuffer.position(OFFSET_BODY_GEOMETRY);
        dataBuffer.putFloat(equatorialRadius);
        dataBuffer.putFloat(polarRadius);
        dataBuffer.putFloat(ringInner);
        dataBuffer.putFloat(ringOuter);

        needsUpdate = true;
    }

    /** Update sun position (for lighting). */
    public void updateSunPosition(Vector3f sunPosition) {
        dataBuffer.position(OFFSET_SUN_POSITION);
        dataBuffer.putFloat(sunPosition.x);
        dataBuffer.putFloat(sunPosition.y);
        dataBuffer.putFloat(sunPosition.z);
        dataBuffer.putFloat(0); // Padding for vec3 → vec4

        needsUpdate = true;
    }

    /** Write a Matrix3f in std140 layout (3 rows of vec4). */
    private void writeMatrix3AsStd140(Matrix3f mat, ByteBuffer buffer) {
        // Row 0: m00, m01, m02, padding
        buffer.putFloat(mat.m00());
        buffer.putFloat(mat.m01());
        buffer.putFloat(mat.m02());
        buffer.putFloat(0.0f); // Padding to vec4

        // Row 1: m10, m11, m12, padding
        buffer.putFloat(mat.m10());
        buffer.putFloat(mat.m11());
        buffer.putFloat(mat.m12());
        buffer.putFloat(0.0f); // Padding to vec4

        // Row 2: m20, m21, m22, padding
        buffer.putFloat(mat.m20());
        buffer.putFloat(mat.m21());
        buffer.putFloat(mat.m22());
        buffer.putFloat(0.0f); // Padding to vec4
    }

    /**
     * Upload the buffer to GPU if needed. Call this once per body before drawing. Uses buffer
     * orphaning to avoid GPU synchronization stalls when rendering multiple bodies per frame.
     */
    public void upload() {
        if (!needsUpdate) {
            return;
        }

        glBindBuffer(GL_UNIFORM_BUFFER, uboId);

        // Optimization: Buffer orphaning
        // This tells the driver to allocate new memory for the buffer,
        // allowing the GPU to continue using the old buffer while we write to the new one.
        // This prevents stalls when updating UBO for multiple bodies.
        glBufferData(GL_UNIFORM_BUFFER, BUFFER_SIZE, GL_DYNAMIC_DRAW);

        dataBuffer.position(0);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, dataBuffer);

        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        needsUpdate = false;
    }

    /**
     * Bind this UBO for rendering. The shader must have a corresponding uniform block at the same
     * binding point.
     */
    public void bind() {
        glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);
    }

    /**
     * Link this UBO to a shader's uniform block. Required for macOS OpenGL 4.1 which doesn't
     * support explicit binding in GLSL.
     *
     * @param shaderProgramId The shader program ID
     * @param blockName The name of the uniform block in the shader (e.g., "CelestialData")
     */
    public void linkToShader(int shaderProgramId, String blockName) {
        // Get the uniform block index from the shader
        int blockIndex = glGetUniformBlockIndex(shaderProgramId, blockName);
        if (blockIndex != GL_INVALID_INDEX) {
            // Link the uniform block to our binding point
            glUniformBlockBinding(shaderProgramId, blockIndex, bindingPoint);
            LOGGER.fine(
                    "Linked UBO to shader block '"
                            + blockName
                            + "' at binding point "
                            + bindingPoint);
        } else {
            LOGGER.warning(
                    "Uniform block '"
                            + blockName
                            + "' not found in shader program "
                            + shaderProgramId);
        }
    }

    /** Get the binding point for shader configuration. */
    public int getBindingPoint() {
        return bindingPoint;
    }

    /** Clean up resources. */
    public void dispose() {
        if (uboId != -1) {
            glDeleteBuffers(uboId);
            uboId = -1;
        }
    }

    /** Check if UBO is valid. */
    public boolean isValid() {
        return uboId != -1;
    }
}
