package com.jda.orrery.graphics.geometry;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL46;
import org.lwjgl.system.MemoryUtil;

/** Ring geometry mesh for planetary ring systems (e.g. Saturn). Rendered as indexed triangles. */
public class RingMesh {
    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int vertexCount;
    private final float innerRadius;
    private final float outerRadius;
    private boolean disposed = false;

    public RingMesh(float innerRadius, float outerRadius, int segments) {
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;

        // Create vertices for ring geometry
        // We need 2 vertices per segment (inner and outer edge)
        int verticesPerRing = segments + 1; // +1 to close the ring
        int totalVertices = verticesPerRing * 2;

        FloatBuffer vertices =
                MemoryUtil.memAllocFloat(totalVertices * 8); // pos(3) + normal(3) + params(2)
        IntBuffer indices = MemoryUtil.memAllocInt(segments * 6); // 2 triangles per segment

        try {
            // Generate vertices
            for (int i = 0; i <= segments; i++) {
                float angle = (float) (2.0 * Math.PI * i / segments);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                // U coordinate goes around the ring (0 to 1)
                float u = (float) i / segments;

                // Inner edge vertex (ring in XY plane, Z is rotation axis in body-fixed)
                vertices.put(cos * innerRadius); // x
                vertices.put(sin * innerRadius); // y
                vertices.put(0.0f); // z (ring in XY plane)
                vertices.put(0.0f); // normal x
                vertices.put(0.0f); // normal y
                vertices.put(1.0f); // normal z (ring normal points along Z)
                // Convert UV to parametric for shader: theta = v * PI, phi = u * 2PI
                vertices.put(0.0f * 3.141592654f); // theta (v=0 for inner edge)
                vertices.put(u * 6.283185307f); // phi (u around ring)

                // Outer edge vertex (ring in XY plane, Z is rotation axis in body-fixed)
                vertices.put(cos * outerRadius); // x
                vertices.put(sin * outerRadius); // y
                vertices.put(0.0f); // z (ring in XY plane)
                vertices.put(0.0f); // normal x
                vertices.put(0.0f); // normal y
                vertices.put(1.0f); // normal z (ring normal points along Z)
                // Convert UV to parametric for shader: theta = v * PI, phi = u * 2PI
                vertices.put(1.0f * 3.141592654f); // theta (v=1 for outer edge)
                vertices.put(u * 6.283185307f); // phi (u around ring)
            }

            // Generate indices for triangle strip
            for (int i = 0; i < segments; i++) {
                int innerCurrent = i * 2;
                int outerCurrent = i * 2 + 1;
                int innerNext = (i + 1) * 2;
                int outerNext = (i + 1) * 2 + 1;

                // First triangle
                indices.put(innerCurrent);
                indices.put(outerCurrent);
                indices.put(innerNext);

                // Second triangle
                indices.put(innerNext);
                indices.put(outerCurrent);
                indices.put(outerNext);
            }

            vertices.flip();
            indices.flip();

            // Create VAO
            vao = GL46.glGenVertexArrays();
            GL46.glBindVertexArray(vao);

            // Create VBO
            vbo = GL46.glGenBuffers();
            GL46.glBindBuffer(GL46.GL_ARRAY_BUFFER, vbo);
            GL46.glBufferData(GL46.GL_ARRAY_BUFFER, vertices, GL46.GL_STATIC_DRAW);

            // Create EBO
            ebo = GL46.glGenBuffers();
            GL46.glBindBuffer(GL46.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL46.glBufferData(GL46.GL_ELEMENT_ARRAY_BUFFER, indices, GL46.GL_STATIC_DRAW);

            // Set vertex attributes to match shader expectations
            // Location 0: Position (3 floats)
            GL46.glVertexAttribPointer(0, 3, GL46.GL_FLOAT, false, 8 * Float.BYTES, 0);
            GL46.glEnableVertexAttribArray(0);

            // Location 1: Normal (3 floats)
            GL46.glVertexAttribPointer(
                    1, 3, GL46.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
            GL46.glEnableVertexAttribArray(1);

            // Location 2: Parameters (2 floats) - theta and phi
            GL46.glVertexAttribPointer(
                    2, 2, GL46.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
            GL46.glEnableVertexAttribArray(2);

            GL46.glBindVertexArray(0);

            this.vertexCount = indices.limit();

        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
        }
    }

    public void render() {
        GL46.glBindVertexArray(vao);
        GL46.glDrawElements(GL46.GL_TRIANGLES, vertexCount, GL46.GL_UNSIGNED_INT, 0);
        GL46.glBindVertexArray(0);
    }

    public void dispose() {
        if (disposed) return;
        GL46.glDeleteBuffers(vbo);
        GL46.glDeleteBuffers(ebo);
        GL46.glDeleteVertexArrays(vao);
        disposed = true;
    }

    public float getInnerRadius() {
        return innerRadius;
    }

    public float getOuterRadius() {
        return outerRadius;
    }
}
