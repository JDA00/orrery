package com.jda.orrery.view;

import javafx.scene.shape.TriangleMesh;

/**
 * Generates sphere meshes with texture mapping.
 */
public class SphereMeshGenerator {

    // Constants for clarity and maintainability
    private static final int VERTICES_PER_POINT = 3;      // x, y, z coordinates
    private static final int TEXCOORDS_PER_POINT = 2;     // u, v coordinates
    private static final int INDICES_PER_TRIANGLE = 6;    // 3 vertex indices + 3 texture indices
    private static final int TRIANGLES_PER_QUAD = 2;      // Each quad becomes 2 triangles

    // Math constants
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Quality presets for different viewing scenarios
     * Easy to extend with new quality levels
     */
    public enum Quality {
        LOW(32, 64, "Far viewing - basic quality"),
        MEDIUM(64, 128, "Normal viewing distance"),
        HIGH(128, 256, "Close inspection"),
        ULTRA(256, 512, "Maximum quality - no visible seams");

        private final int latSegments;
        private final int lonSegments;
        private final String description;

        Quality(int latSegments, int lonSegments, String description) {
            this.latSegments = latSegments;
            this.lonSegments = lonSegments;
            this.description = description;
        }

        public int getLatSegments() { return latSegments; }
        public int getLonSegments() { return lonSegments; }
        public String getDescription() { return description; }
    }

    /**
     * Sphere generation parameters - pre-calculated for efficiency
     */
    private static class SphereParameters {
        final double radius;
        final int latSegments;
        final int lonSegments;
        final int verticesPerRing;
        final int totalVertices;
        final int totalQuads;
        final int totalTriangles;

        SphereParameters(double radius, int latSegments, int lonSegments) {
            this.radius = radius;
            this.latSegments = latSegments;
            this.lonSegments = lonSegments;
            this.verticesPerRing = lonSegments + 1; // +1 for seamless wrapping
            this.totalVertices = (latSegments + 1) * verticesPerRing;
            this.totalQuads = latSegments * lonSegments;
            this.totalTriangles = totalQuads * TRIANGLES_PER_QUAD;
        }
    }

    /**
     * Generate a sphere mesh with specified quality and radius
     */
    public static TriangleMesh generate(double radius, Quality quality) {
        return generate(radius, quality.getLatSegments(), quality.getLonSegments());
    }

    /**
     * Generate a sphere mesh with custom segment counts
     */
    public static TriangleMesh generate(double radius, int latSegments, int lonSegments) {
        SphereParameters params = new SphereParameters(radius, latSegments, lonSegments);

        TriangleMesh mesh = new TriangleMesh();

        generateVerticesAndTexCoords(mesh, params);
        generateFaces(mesh, params);

        return mesh;
    }

    /**
     * Generate all vertices and texture coordinates for the sphere
     */
    private static void generateVerticesAndTexCoords(TriangleMesh mesh, SphereParameters params) {
        float[] points = new float[params.totalVertices * VERTICES_PER_POINT];
        float[] texCoords = new float[params.totalVertices * TEXCOORDS_PER_POINT];

        int pointIndex = 0;
        int texIndex = 0;

        // Generate vertices from top to bottom (latitude), left to right (longitude)
        for (int lat = 0; lat <= params.latSegments; lat++) {
            LatitudeInfo latInfo = calculateLatitudeInfo(lat, params.latSegments);

            for (int lon = 0; lon <= params.lonSegments; lon++) {
                LongitudeInfo lonInfo = calculateLongitudeInfo(lon, params.lonSegments);

                // Calculate 3D position
                VertexPosition position = calculateVertexPosition(params.radius, latInfo, lonInfo);
                addVertexToArray(points, pointIndex, position);
                pointIndex += VERTICES_PER_POINT;

                // Calculate UV coordinates
                TextureCoordinates texCoord = calculateTextureCoordinates(lat, lon, params);
                addTexCoordToArray(texCoords, texIndex, texCoord);
                texIndex += TEXCOORDS_PER_POINT;
            }
        }

        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);
    }

    /**
     * Calculate latitude-related trigonometric values
     */
    private static LatitudeInfo calculateLatitudeInfo(int lat, int latSegments) {
        double theta = Math.PI * lat / latSegments; // 0 (top) to PI (bottom)
        return new LatitudeInfo(theta, Math.sin(theta), Math.cos(theta));
    }

    /**
     * Calculate longitude-related trigonometric values
     */
    private static LongitudeInfo calculateLongitudeInfo(int lon, int lonSegments) {
        double phi = TWO_PI * lon / lonSegments; // 0 to 2*PI
        return new LongitudeInfo(phi, Math.sin(phi), Math.cos(phi));
    }

    /**
     * Calculate 3D vertex position on sphere surface
     */
    private static VertexPosition calculateVertexPosition(double radius, LatitudeInfo latInfo, LongitudeInfo lonInfo) {
        float x = (float)(radius * latInfo.sinTheta * lonInfo.cosPhi);
        float y = (float)(radius * latInfo.cosTheta);
        float z = (float)(radius * latInfo.sinTheta * lonInfo.sinPhi);
        return new VertexPosition(x, y, z);
    }

    /**
     * Calculate UV texture coordinates with proper mapping
     */
    private static TextureCoordinates calculateTextureCoordinates(int lat, int lon, SphereParameters params) {
        float u = (float)lon / (float)params.lonSegments;
        // Flip V coordinate: JavaFX expects V=0 at top, V=1 at bottom
        float v = 1.0f - (float)lat / (float)params.latSegments;
        return new TextureCoordinates(u, v);
    }

    /**
     * Add vertex position to points array
     */
    private static void addVertexToArray(float[] points, int index, VertexPosition position) {
        points[index] = position.x;
        points[index + 1] = position.y;
        points[index + 2] = position.z;
    }

    /**
     * Add texture coordinate to texCoords array
     */
    private static void addTexCoordToArray(float[] texCoords, int index, TextureCoordinates texCoord) {
        texCoords[index] = texCoord.u;
        texCoords[index + 1] = texCoord.v;
    }

    /**
     * Generate triangle faces for the sphere
     */
    private static void generateFaces(TriangleMesh mesh, SphereParameters params) {
        int[] faces = new int[params.totalTriangles * INDICES_PER_TRIANGLE];
        int faceIndex = 0;

        for (int lat = 0; lat < params.latSegments; lat++) {
            for (int lon = 0; lon < params.lonSegments; lon++) {
                QuadVertices quad = calculateQuadVertices(lat, lon, params);

                // Add two triangles for this quad
                faceIndex = addTriangleToFaces(faces, faceIndex, quad.v0, quad.v1, quad.v2);
                faceIndex = addTriangleToFaces(faces, faceIndex, quad.v1, quad.v3, quad.v2);
            }
        }

        mesh.getFaces().addAll(faces);
    }

    /**
     * Calculate the four vertex indices for a quad
     */
    private static QuadVertices calculateQuadVertices(int lat, int lon, SphereParameters params) {
        int v0 = lat * params.verticesPerRing + lon;           // Current
        int v1 = lat * params.verticesPerRing + (lon + 1);     // Right
        int v2 = (lat + 1) * params.verticesPerRing + lon;     // Below
        int v3 = (lat + 1) * params.verticesPerRing + (lon + 1); // Below-right

        return new QuadVertices(v0, v1, v2, v3);
    }

    /**
     * Add a triangle to the faces array
     */
    private static int addTriangleToFaces(int[] faces, int startIndex, int v0, int v1, int v2) {
        // Triangle vertex 0
        faces[startIndex] = v0;
        faces[startIndex + 1] = v0;  // Use same index for texture coordinate

        // Triangle vertex 1
        faces[startIndex + 2] = v1;
        faces[startIndex + 3] = v1;

        // Triangle vertex 2
        faces[startIndex + 4] = v2;
        faces[startIndex + 5] = v2;

        return startIndex + INDICES_PER_TRIANGLE;
    }

    // Data classes for clean parameter passing
    private static class LatitudeInfo {
        final double theta, sinTheta, cosTheta;
        LatitudeInfo(double theta, double sinTheta, double cosTheta) {
            this.theta = theta; this.sinTheta = sinTheta; this.cosTheta = cosTheta;
        }
    }

    private static class LongitudeInfo {
        final double phi, sinPhi, cosPhi;
        LongitudeInfo(double phi, double sinPhi, double cosPhi) {
            this.phi = phi; this.sinPhi = sinPhi; this.cosPhi = cosPhi;
        }
    }

    private static class VertexPosition {
        final float x, y, z;
        VertexPosition(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
        }
    }

    private static class TextureCoordinates {
        final float u, v;
        TextureCoordinates(float u, float v) {
            this.u = u; this.v = v;
        }
    }

    private static class QuadVertices {
        final int v0, v1, v2, v3;
        QuadVertices(int v0, int v1, int v2, int v3) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2; this.v3 = v3;
        }
    }
}