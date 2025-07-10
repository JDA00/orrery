package com.jda.orrery.view;

import javafx.geometry.Point3D;
import javafx.scene.shape.TriangleMesh;

/**
 * Generates a tube mesh along a given path of 3D points.
 * Uses parallel transport method for consistent, smooth tube surfaces.
 */
public class TubeMeshGenerator {

    // Constants for clarity and maintainability
    private static final int VERTICES_PER_POINT = 3;      // x, y, z coordinates
    private static final int TEXCOORDS_PER_POINT = 2;     // u, v coordinates
    private static final int INDICES_PER_TRIANGLE = 6;    // 3 vertex indices + 3 texture indices
    private static final int TRIANGLES_PER_QUAD = 2;      // Each quad becomes 2 triangles

    // Math constants
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double PARALLEL_TRANSPORT_THRESHOLD = 0.99; // When tangents are too similar

    // Default reference vectors for initial normal calculation
    private static final Point3D[] REFERENCE_VECTORS = {
            new Point3D(0, 1, 0),  // Y-up (preferred for horizontal paths)
            new Point3D(1, 0, 0),  // X-right (fallback)
            new Point3D(0, 0, 1)   // Z-forward (last resort)
    };

    private final int radialSegments;
    private final double tubeRadius;

    /**
     * Tube generation parameters - pre-calculated for efficiency
     */
    private static class TubeParameters {
        final Point3D[] pathPoints;
        final int pathSegments;
        final int radialSegments;
        final double tubeRadius;
        final int verticesPerRing;
        final int totalVertices;
        final int totalQuads;
        final int totalTriangles;
        final boolean closed;

        TubeParameters(Point3D[] pathPoints, int radialSegments, double tubeRadius, boolean closed) {
            this.pathPoints = pathPoints;
            this.pathSegments = pathPoints.length - 1;
            this.radialSegments = radialSegments;
            this.tubeRadius = tubeRadius;
            this.verticesPerRing = radialSegments;
            this.totalVertices = pathPoints.length * radialSegments;
            this.totalQuads = (closed ? pathSegments : pathSegments - 1) * radialSegments;
            this.totalTriangles = totalQuads * TRIANGLES_PER_QUAD;
            this.closed = closed;
        }
    }

    /**
     * Frame information for a point along the path
     */
    private static class PathFrame {
        final Point3D tangent;
        final Point3D normal;
        final Point3D binormal;

        PathFrame(Point3D tangent, Point3D normal, Point3D binormal) {
            this.tangent = tangent;
            this.normal = normal;
            this.binormal = binormal;
        }
    }

    /**
     * Ring vertex information
     */
    private static class RingVertex {
        final Point3D position;
        final Point3D textureCoord;

        RingVertex(Point3D position, Point3D textureCoord) {
            this.position = position;
            this.textureCoord = textureCoord;
        }
    }

    /**
     * Creates a new tube mesh generator.
     */
    public TubeMeshGenerator(int radialSegments, double tubeRadius) {
        this.radialSegments = radialSegments;
        this.tubeRadius = tubeRadius;
    }

    /**
     * Generate a tube mesh along the given path.
     */
    public TriangleMesh generateTube(Point3D[] pathPoints, boolean closed) {
        if (!isValidPath(pathPoints)) {
            throw new IllegalArgumentException("Invalid path: must have at least 2 points");
        }

        TubeParameters params = new TubeParameters(pathPoints, radialSegments, tubeRadius, closed);

        TriangleMesh mesh = new TriangleMesh();

        generateVerticesAndTexCoords(mesh, params);
        generateFaces(mesh, params);

        return mesh;
    }

    /**
     * Validate the input path
     */
    private boolean isValidPath(Point3D[] pathPoints) {
        return pathPoints != null && pathPoints.length >= 2;
    }

    /**
     * Generate all vertices and texture coordinates for the tube
     */
    private void generateVerticesAndTexCoords(TriangleMesh mesh, TubeParameters params) {
        float[] points = new float[params.totalVertices * VERTICES_PER_POINT];
        float[] texCoords = new float[params.totalVertices * TEXCOORDS_PER_POINT];

        // Calculate path frames using parallel transport
        PathFrame[] frames = calculatePathFrames(params.pathPoints);

        int pointIndex = 0;
        int texIndex = 0;

        // Generate vertices for each point along the path
        for (int pathIndex = 0; pathIndex < params.pathPoints.length; pathIndex++) {
            Point3D center = params.pathPoints[pathIndex];
            PathFrame frame = frames[pathIndex];

            // Generate ring of vertices around this point
            for (int radialIndex = 0; radialIndex < params.radialSegments; radialIndex++) {
                RingVertex vertex = calculateRingVertex(center, frame, radialIndex, pathIndex, params);

                // Add vertex position
                addVertexToArray(points, pointIndex, vertex.position);
                pointIndex += VERTICES_PER_POINT;

                // Add texture coordinates
                addTexCoordToArray(texCoords, texIndex, vertex.textureCoord);
                texIndex += TEXCOORDS_PER_POINT;
            }
        }

        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);
    }

    /**
     * Calculate path frames using parallel transport method
     */
    private PathFrame[] calculatePathFrames(Point3D[] pathPoints) {
        PathFrame[] frames = new PathFrame[pathPoints.length];

        // Calculate tangent vectors for all points
        Point3D[] tangents = calculateTangentVectors(pathPoints);

        // Calculate initial normal using best reference vector
        Point3D initialNormal = calculateInitialNormal(tangents[0]);

        // Transport normal along the path
        Point3D[] normals = transportNormalsAlongPath(tangents, initialNormal);

        // Calculate binormal vectors and create frames
        for (int i = 0; i < pathPoints.length; i++) {
            Point3D binormal = tangents[i].crossProduct(normals[i]).normalize();
            frames[i] = new PathFrame(tangents[i], normals[i], binormal);
        }

        return frames;
    }

    /**
     * Calculate tangent vectors for all points along the path
     */
    private Point3D[] calculateTangentVectors(Point3D[] pathPoints) {
        Point3D[] tangents = new Point3D[pathPoints.length];

        for (int i = 0; i < pathPoints.length; i++) {
            tangents[i] = calculateTangentAtPoint(pathPoints, i);
        }

        return tangents;
    }

    /**
     * Calculate tangent vector at a specific point
     */
    private Point3D calculateTangentAtPoint(Point3D[] points, int index) {
        Point3D tangent;

        if (index == 0) {
            // At start, use forward difference
            tangent = points[1].subtract(points[0]);
        } else if (index == points.length - 1) {
            // At end, use backward difference
            tangent = points[index].subtract(points[index - 1]);
        } else {
            // In middle, use central difference for smoother results
            tangent = points[index + 1].subtract(points[index - 1]).multiply(0.5);
        }

        return tangent.normalize();
    }

    /**
     * Calculate initial normal vector using the best reference vector
     */
    private Point3D calculateInitialNormal(Point3D tangent) {
        // Try each reference vector until we find one that's not parallel to the tangent
        for (Point3D reference : REFERENCE_VECTORS) {
            double dot = Math.abs(tangent.dotProduct(reference));
            if (dot < PARALLEL_TRANSPORT_THRESHOLD) {
                return tangent.crossProduct(reference).normalize();
            }
        }

        // Fallback (should never happen with our reference vectors)
        return new Point3D(1, 0, 0);
    }

    /**
     * Transport normal vectors along the path using parallel transport
     */
    private Point3D[] transportNormalsAlongPath(Point3D[] tangents, Point3D initialNormal) {
        Point3D[] normals = new Point3D[tangents.length];
        normals[0] = initialNormal;

        for (int i = 1; i < tangents.length; i++) {
            normals[i] = transportNormal(normals[i - 1], tangents[i - 1], tangents[i]);
        }

        return normals;
    }

    /**
     * Transport a normal vector from one tangent to another
     */
    private Point3D transportNormal(Point3D prevNormal, Point3D prevTangent, Point3D currentTangent) {
        // Calculate the rotation axis (perpendicular to both tangents)
        Point3D rotationAxis = prevTangent.crossProduct(currentTangent);

        // If tangents are nearly parallel, no rotation needed
        if (rotationAxis.magnitude() < 1e-6) {
            return prevNormal;
        }

        rotationAxis = rotationAxis.normalize();

        // Calculate rotation angle
        double cosAngle = prevTangent.dotProduct(currentTangent);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle)); // Clamp to avoid numerical errors
        double angle = Math.acos(cosAngle);

        // Rotate the normal around the rotation axis
        return rotateVector(prevNormal, rotationAxis, angle);
    }

    /**
     * Rotate a vector around an axis by a given angle using Rodrigues' rotation formula
     */
    private Point3D rotateVector(Point3D vector, Point3D axis, double angle) {
        double cosAngle = Math.cos(angle);
        double sinAngle = Math.sin(angle);

        // Rodrigues' rotation formula: v' = v*cos(θ) + (k×v)*sin(θ) + k*(k·v)*(1-cos(θ))
        Point3D crossProduct = axis.crossProduct(vector);
        double dotProduct = axis.dotProduct(vector);

        return vector.multiply(cosAngle)
                .add(crossProduct.multiply(sinAngle))
                .add(axis.multiply(dotProduct * (1 - cosAngle)));
    }

    /**
     * Calculate vertex position and texture coordinates for a ring vertex
     */
    private RingVertex calculateRingVertex(Point3D center, PathFrame frame, int radialIndex, int pathIndex, TubeParameters params) {
        double angle = TWO_PI * radialIndex / params.radialSegments;

        // Calculate offset from center using normal and binormal
        Point3D offset = frame.normal.multiply(Math.cos(angle))
                .add(frame.binormal.multiply(Math.sin(angle)))
                .multiply(params.tubeRadius);

        Point3D position = center.add(offset);

        // Calculate texture coordinates
        float u = (float)radialIndex / (float)params.radialSegments;
        float v = (float)pathIndex / (float)params.pathSegments;
        Point3D textureCoord = new Point3D(u, v, 0);

        return new RingVertex(position, textureCoord);
    }

    /**
     * Add vertex position to points array
     */
    private void addVertexToArray(float[] points, int index, Point3D position) {
        points[index] = (float)position.getX();
        points[index + 1] = (float)position.getY();
        points[index + 2] = (float)position.getZ();
    }

    /**
     * Add texture coordinate to texCoords array
     */
    private void addTexCoordToArray(float[] texCoords, int index, Point3D textureCoord) {
        texCoords[index] = (float)textureCoord.getX();
        texCoords[index + 1] = (float)textureCoord.getY();
    }

    /**
     * Generate triangle faces for the tube
     */
    private void generateFaces(TriangleMesh mesh, TubeParameters params) {
        int[] faces = new int[params.totalTriangles * INDICES_PER_TRIANGLE];
        int faceIndex = 0;

        int segmentsToProcess = params.closed ? params.pathSegments : params.pathSegments - 1;

        for (int pathIndex = 0; pathIndex < segmentsToProcess; pathIndex++) {
            int nextPathIndex = (pathIndex + 1) % params.pathSegments;

            for (int radialIndex = 0; radialIndex < params.radialSegments; radialIndex++) {
                int nextRadialIndex = (radialIndex + 1) % params.radialSegments;

                // Calculate vertex indices for the quad
                int v1 = pathIndex * params.radialSegments + radialIndex;
                int v2 = pathIndex * params.radialSegments + nextRadialIndex;
                int v3 = nextPathIndex * params.radialSegments + radialIndex;
                int v4 = nextPathIndex * params.radialSegments + nextRadialIndex;

                // Add two triangles for this quad
                faceIndex = addTriangleToFaces(faces, faceIndex, v1, v2, v4);
                faceIndex = addTriangleToFaces(faces, faceIndex, v1, v4, v3);
            }
        }

        mesh.getFaces().addAll(faces);
    }

    /**
     * Add a triangle to the faces array
     * Each triangle requires 6 indices: 3 vertex indices + 3 corresponding texture coordinate indices
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
}