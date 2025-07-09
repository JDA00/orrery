package com.jda.orrery.view;

import javafx.geometry.Point3D;
import javafx.scene.shape.TriangleMesh;

/**
 * Generates a tube mesh along a given path of 3D points.
 * Creates a cylindrical mesh with configurable radius and detail level.
 */
public class TubeMeshGenerator {
    private final int radialSegments;
    private final double tubeRadius;

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

        TriangleMesh mesh = new TriangleMesh();
        int pathSegments = pathPoints.length - 1;

        // Generate vertices and texture coordinates
        generateVertices(mesh, pathPoints, pathSegments);

        // Generate faces
        generateFaces(mesh, pathSegments, closed);

        return mesh;
    }

    private void generateVertices(TriangleMesh mesh, Point3D[] pathPoints, int pathSegments) {
        // For each point along the path
        for (int i = 0; i < pathPoints.length; i++) {
            Point3D center = pathPoints[i];

            // Calculate tangent vector (direction of the path at this point)
            Point3D tangent = calculateTangent(pathPoints, i);

            // Generate a perpendicular frame (normal and binormal vectors)
            Point3D normal = calculateNormal(tangent);
            Point3D binormal = tangent.crossProduct(normal).normalize();

            // Generate ring of vertices around this point
            for (int j = 0; j < radialSegments; j++) {
                double angle = (2.0 * Math.PI * j) / radialSegments;

                // Calculate position on the ring
                // normal * cos(angle) + binormal * sin(angle) gives us a circle
                Point3D offset = normal.multiply(Math.cos(angle))
                        .add(binormal.multiply(Math.sin(angle)))
                        .multiply(tubeRadius);

                Point3D vertex = center.add(offset);

                // Add vertex position (x, y, z)
                mesh.getPoints().addAll(
                        (float)vertex.getX(),
                        (float)vertex.getY(),
                        (float)vertex.getZ()
                );

                // Add texture coordinates (u, v)
                // U goes around the tube (0-1), V goes along the length (0-1)
                float u = (float)j / (float)radialSegments;
                float v = (float)i / (float)pathSegments;
                mesh.getTexCoords().addAll(u, v);
            }
        }
    }

    private Point3D calculateTangent(Point3D[] points, int index) {
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

    private Point3D calculateNormal(Point3D tangent) {
        // We need a vector perpendicular to the tangent
        // Since orbits are mostly in the XZ plane, we use Y-up as reference
        Point3D up = new Point3D(0, 1, 0);

        // If tangent is too close to up vector (rare for orbits), use different reference
        if (Math.abs(tangent.dotProduct(up)) > 0.99) {
            up = new Point3D(1, 0, 0);
        }

        // Cross product gives us a perpendicular vector
        return tangent.crossProduct(up).normalize();
    }

    /**
     * Generate faces connecting the vertices into triangles.
     * This is where we define how vertices connect to form the tube surface.
     */
    private void generateFaces(TriangleMesh mesh, int pathSegments, boolean closed) {
        // Number of segments along the path to process
        int segments = closed ? pathSegments : pathSegments - 1;

        // For each segment along the path
        for (int i = 0; i < segments; i++) {
            // Next segment index (wraps to 0 if closed)
            int nextI = (i + 1) % pathSegments;

            // For each segment around the tube
            for (int j = 0; j < radialSegments; j++) {
                // Next radial segment (wraps around the tube)
                int nextJ = (j + 1) % radialSegments;

                // Calculate vertex indices
                // Each ring has 'radialSegments' vertices
                // Vertex index = ring_index * radialSegments + position_in_ring
                int v1 = i * radialSegments + j;        // Current ring, current position
                int v2 = i * radialSegments + nextJ;    // Current ring, next position
                int v3 = nextI * radialSegments + j;    // Next ring, current position
                int v4 = nextI * radialSegments + nextJ; // Next ring, next position

                // First triangle (v1, v2, v4) - upper right triangle
                mesh.getFaces().addAll(
                        v1, v1,  // vertex index, texture coordinate index
                        v2, v2,
                        v4, v4
                );

                // Second triangle (v1, v4, v3) - lower left triangle
                mesh.getFaces().addAll(
                        v1, v1,
                        v4, v4,
                        v3, v3
                );
            }
        }
    }
}