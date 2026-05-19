package com.jda.orrery.graphics.geometry;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.logging.Logging;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.lwjgl.system.MemoryUtil;

/**
 * Sphere mesh generator supporting multiple topologies.
 *
 * Features: - Multiple topology types (icosphere, UV sphere) - Proper seam handling for
 * equirectangular textures - Shared vertex data for memory efficiency - LOD support for
 * distance-based quality
 */
public class SphereMesh {
    private static final Logger LOGGER = Logging.logger(SphereMesh.class);

    // Quality tiers.
    public enum Quality {
        LOW(16, 32), // 512 triangles - distant asteroids
        MEDIUM(32, 64), // 2048 triangles - moons
        HIGH(90, 180), // 16,200 vertices - planets
        ULTRA(180, 360); // 64,800 vertices - hero bodies (e.g., Earth close-up)

        public final int latitudeSegments; // For UV sphere
        public final int longitudeSegments; // For UV sphere
        public final int subdivisions; // For icosphere

        Quality(int lat, int lon) {
            this.latitudeSegments = lat;
            this.longitudeSegments = lon;
            this.subdivisions = (ordinal() + 1); // 1-4 subdivisions
        }
    }

    // OpenGL resources
    private int vao = -1;
    private int vbo = -1;
    private int ebo = -1;
    private int indexCount = 0;

    // Mesh properties
    private final MeshTopology topology;
    private final Quality quality;

    // Mesh data before GPU upload
    private List<Float> vertices;
    private List<Integer> indices;

    /** Create a sphere mesh with specified topology and quality. */
    public SphereMesh(MeshTopology topology, Quality quality) {
        this.topology = topology;
        this.quality = quality;

        LOGGER.fine(String.format("Creating %s sphere mesh at %s quality", topology, quality));

        generateMesh();
        uploadToGPU();
    }

    /** Generate mesh data based on topology. */
    private void generateMesh() {
        vertices = new ArrayList<>();
        indices = new ArrayList<>();

        switch (topology) {
            case ICOSPHERE:
                generateIcosphere();
                break;
            case UV_SPHERE:
                generateUVSphere();
                break;
            default:
                // Fallback to icosphere for unimplemented topologies
                LOGGER.warning("Topology " + topology + " not implemented, using icosphere");
                generateIcosphere();
        }

        LOGGER.fine(
                String.format(
                        "Generated mesh: %d vertices, %d indices",
                        vertices.size() / 8, indices.size()));
    }

    /**
     * Generate icosphere mesh using subdivided icosahedron. Best for cube map textures and uniform
     * distribution. Body-Fixed Convention: Z-up (north pole), X through prime meridian.
     */
    private void generateIcosphere() {
        // Golden ratio for icosahedron
        float t = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);

        // Initial icosahedron vertices (12 vertices)
        // Body-fixed convention: Z points through north pole, X through prime meridian
        // Transform from Y-up to Z-up: (x,y,z) -> (x,z,y)
        addIcosphereVertex(-1, 0, t); // Was (-1, t, 0)
        addIcosphereVertex(1, 0, t); // Was (1, t, 0)
        addIcosphereVertex(-1, 0, -t); // Was (-1, -t, 0)
        addIcosphereVertex(1, 0, -t); // Was (1, -t, 0)

        addIcosphereVertex(0, t, -1); // Was (0, -1, t)
        addIcosphereVertex(0, t, 1); // Was (0, 1, t)
        addIcosphereVertex(0, -t, -1); // Was (0, -1, -t)
        addIcosphereVertex(0, -t, 1); // Was (0, 1, -t)

        addIcosphereVertex(t, -1, 0); // Was (t, 0, -1)
        addIcosphereVertex(t, 1, 0); // Was (t, 0, 1)
        addIcosphereVertex(-t, -1, 0); // Was (-t, 0, -1)
        addIcosphereVertex(-t, 1, 0); // Was (-t, 0, 1)

        // Initial icosahedron faces (20 triangles)
        List<Integer> faces = new ArrayList<>();

        // 5 faces around vertex 0
        addTriangle(faces, 0, 11, 5);
        addTriangle(faces, 0, 5, 1);
        addTriangle(faces, 0, 1, 7);
        addTriangle(faces, 0, 7, 10);
        addTriangle(faces, 0, 10, 11);

        // 5 adjacent faces
        addTriangle(faces, 1, 5, 9);
        addTriangle(faces, 5, 11, 4);
        addTriangle(faces, 11, 10, 2);
        addTriangle(faces, 10, 7, 6);
        addTriangle(faces, 7, 1, 8);

        // 5 faces around vertex 3
        addTriangle(faces, 3, 9, 4);
        addTriangle(faces, 3, 4, 2);
        addTriangle(faces, 3, 2, 6);
        addTriangle(faces, 3, 6, 8);
        addTriangle(faces, 3, 8, 9);

        // 5 adjacent faces
        addTriangle(faces, 4, 9, 5);
        addTriangle(faces, 2, 4, 11);
        addTriangle(faces, 6, 2, 10);
        addTriangle(faces, 8, 6, 7);
        addTriangle(faces, 9, 8, 1);

        // Subdivide to increase resolution
        for (int i = 0; i < quality.subdivisions; i++) {
            faces = subdivideIcosphere(faces);
        }

        indices = faces;
    }

    /**
     * Generate UV sphere with proper seam handling. Best for equirectangular textures. Stores
     * parametric coordinates (theta, phi) so the vertex shader can compute UVs.
     *
     * Body-fixed convention: - Z-axis points through north pole - X-axis points through prime
     * meridian at equator - Y-axis completes right-handed system (90° east of prime meridian)
     *
     * Texture alignment: - U=0 at prime meridian, U=1 at 360° longitude - V=0 at north pole, V=1
     * at south pole
     */
    private void generateUVSphere() {
        int latSegments = quality.latitudeSegments;
        int lonSegments = quality.longitudeSegments;

        // Generate vertices in body-fixed coordinates
        for (int lat = 0; lat <= latSegments; lat++) {
            // theta: angle from +Z axis (0 at north pole, π at south pole)
            float theta = (float) (lat * Math.PI / latSegments);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);

            for (int lon = 0; lon <= lonSegments; lon++) {
                // phi: angle around Z axis from +X (longitude from prime meridian)
                // Starts at 0 (prime meridian) and goes to 2π (360°)
                float phi = (float) (lon * 2.0 * Math.PI / lonSegments);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                // Position on unit sphere (body-fixed convention)
                float x = sinTheta * cosPhi; // X points through prime meridian
                float y = sinTheta * sinPhi; // Y points 90° east of prime meridian
                float z = cosTheta; // Z points through north pole

                // Store parametric coordinates; the vertex shader computes UVs.
                addUVSphereVertex(x, y, z, theta, phi);

                // Seam handling: duplicate vertices at the seam to prevent
                // texture wraparound artifacts.
                if (lon == lonSegments) {
                    // This vertex is at phi=2π, also add one at phi=0
                    // for triangles that wrap around
                    // (Implementation handled in index generation)
                }
            }
        }

        // Generate indices (two triangles per quad)
        for (int lat = 0; lat < latSegments; lat++) {
            for (int lon = 0; lon < lonSegments; lon++) {
                int current = lat * (lonSegments + 1) + lon;
                int next = current + lonSegments + 1;

                // First triangle
                indices.add(current);
                indices.add(next);
                indices.add(current + 1);

                // Second triangle
                indices.add(current + 1);
                indices.add(next);
                indices.add(next + 1);
            }
        }
    }

    /**
     * Add vertex for icosphere with parametric coordinates. Computes and stores (theta, phi) for
     * shader UV calculation. Body-fixed convention: Z-up (north pole), X through prime meridian.
     */
    private void addIcosphereVertex(float x, float y, float z) {
        // Normalize to unit sphere
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        x /= length;
        y /= length;
        z /= length;

        // Position (body-fixed coordinates)
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);

        // Normal (same as position for unit sphere, body-fixed frame)
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);

        // Parametric coordinates (theta, phi) in body-fixed frame
        // theta: angle from +Z axis (0 at north pole)
        // phi: angle around Z axis from +X (prime meridian)
        float theta = (float) Math.acos(z); // acos(z) gives angle from +Z
        float phi = (float) Math.atan2(y, x);
        if (phi < 0) phi += 2.0f * (float) Math.PI; // Normalize to [0, 2π]

        vertices.add(theta);
        vertices.add(phi);
    }

    /**
     * Add vertex for UV sphere with parametric coordinates. Stores (theta, phi) for shader UV
     * calculation. Body-fixed convention: vertices and normals in body-fixed frame.
     */
    private void addUVSphereVertex(float x, float y, float z, float theta, float phi) {
        // Position (body-fixed coordinates)
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);

        // Normal (same as position for unit sphere, body-fixed frame)
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);

        // Parametric coordinates (theta, phi) instead of UV
        // These are used by the shader to compute UV coordinates
        // independent of any coordinate transformations
        vertices.add(theta);
        vertices.add(phi);
    }

    /** Add triangle to face list. */
    private void addTriangle(List<Integer> faces, int v1, int v2, int v3) {
        faces.add(v1);
        faces.add(v2);
        faces.add(v3);
    }

    /** Subdivide icosphere faces for higher resolution. */
    private List<Integer> subdivideIcosphere(List<Integer> faces) {
        List<Integer> newFaces = new ArrayList<>();
        Map<Long, Integer> midpointCache = new HashMap<>();

        for (int i = 0; i < faces.size(); i += 3) {
            int v1 = faces.get(i);
            int v2 = faces.get(i + 1);
            int v3 = faces.get(i + 2);

            // Get midpoints
            int m1 = getMidpoint(v1, v2, midpointCache);
            int m2 = getMidpoint(v2, v3, midpointCache);
            int m3 = getMidpoint(v3, v1, midpointCache);

            // Create 4 new triangles
            addTriangle(newFaces, v1, m1, m3);
            addTriangle(newFaces, v2, m2, m1);
            addTriangle(newFaces, v3, m3, m2);
            addTriangle(newFaces, m1, m2, m3);
        }

        return newFaces;
    }

    /** Get or create midpoint between two vertices. */
    private int getMidpoint(int v1, int v2, Map<Long, Integer> cache) {
        // Create unique key for edge
        long key = ((long) Math.min(v1, v2) << 32) | Math.max(v1, v2);

        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        // Get vertex positions (8 floats per vertex: pos, normal, uv)
        float x1 = vertices.get(v1 * 8);
        float y1 = vertices.get(v1 * 8 + 1);
        float z1 = vertices.get(v1 * 8 + 2);

        float x2 = vertices.get(v2 * 8);
        float y2 = vertices.get(v2 * 8 + 1);
        float z2 = vertices.get(v2 * 8 + 2);

        // Calculate midpoint
        float mx = (x1 + x2) / 2.0f;
        float my = (y1 + y2) / 2.0f;
        float mz = (z1 + z2) / 2.0f;

        // Add new vertex
        addIcosphereVertex(mx, my, mz);

        // Cache and return index
        int index = (vertices.size() / 8) - 1;
        cache.put(key, index);
        return index;
    }

    /** Upload mesh data to GPU. */
    private void uploadToGPU() {
        // Use heap allocation for large buffers instead of stack
        // Stack allocation fails for high-quality meshes with many vertices
        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(vertices.size());
        IntBuffer indexBuffer = MemoryUtil.memAllocInt(indices.size());

        try {
            // Prepare vertex buffer
            for (Float f : vertices) {
                vertexBuffer.put(f);
            }
            vertexBuffer.flip();

            // Prepare index buffer
            for (Integer i : indices) {
                indexBuffer.put(i);
            }
            indexBuffer.flip();

            // Create VAO
            vao = glGenVertexArrays();
            glBindVertexArray(vao);

            // Create VBO
            vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

            // Position attribute (location = 0)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            // Normal attribute (location = 1)
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            // Parametric coordinate attribute (location = 2) — theta/phi for shader UV calc
            glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
            glEnableVertexAttribArray(2);

            // Create EBO
            ebo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

            indexCount = indices.size();

            glBindVertexArray(0);

            LOGGER.fine(String.format("Uploaded mesh to GPU: VAO=%d, %d indices", vao, indexCount));
        } finally {
            // Free the native buffers; allocated outside the JVM heap.
            MemoryUtil.memFree(vertexBuffer);
            MemoryUtil.memFree(indexBuffer);
        }

        // Clear CPU data after GPU upload (optional memory optimization)
        vertices = null;
        indices = null;
    }

    /**
     * Bind this mesh for rendering.
     *
     * @return Number of indices to draw
     */
    public int bind() {
        glBindVertexArray(vao);
        return indexCount;
    }

    /** Unbind after rendering. */
    public void unbind() {
        glBindVertexArray(0);
    }

    /** Draw this mesh. */
    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    /** Dispose GPU resources. */
    public void dispose() {
        if (vao != -1) {
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            glDeleteBuffers(ebo);
            vao = -1;
            vbo = -1;
            ebo = -1;
            indexCount = 0;
            LOGGER.fine("Disposed sphere mesh GPU resources");
        }
    }

    /** Get the topology of this mesh. */
    public MeshTopology getTopology() {
        return topology;
    }

    /** Get the quality level of this mesh. */
    public Quality getQuality() {
        return quality;
    }

    /** Get the number of triangles in this mesh. */
    public int getTriangleCount() {
        return indexCount / 3;
    }
}
