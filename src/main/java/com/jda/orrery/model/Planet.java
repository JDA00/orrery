package com.jda.orrery.model;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

import java.net.URL;

public class Planet extends CelestialBody {
    private final double orbitRadius;
    private final double orbitPeriod;
    private final double orbitEccentricity;

    // Pre-calculate orbit parameters
    private final double semiMajorAxis;
    private final double semiMinorAxis;
    private final double centerXOffset;
    private final double angularVelocity;

    // Ring support
    private MeshView ringMesh;

    public Planet(String name, double radius, String textureFile, double orbitRadius,
                  double orbitPeriod, double orbitEccentricity, double tiltAngle) {
        super(name, radius, textureFile, tiltAngle);

        this.orbitRadius = orbitRadius;
        this.orbitPeriod = orbitPeriod;
        this.orbitEccentricity = orbitEccentricity;

        this.semiMajorAxis = orbitRadius;
        this.semiMinorAxis = semiMajorAxis * Math.sqrt(1 - orbitEccentricity * orbitEccentricity);
        this.centerXOffset = semiMajorAxis * orbitEccentricity;
        this.angularVelocity = (2 * Math.PI) / orbitPeriod;

        updatePosition(0);
    }

    @Override
    protected Color getDefaultColor(String textureFile) {
        switch (textureFile.toLowerCase()) {
            case "mercury.png":
                return Color.DARKGRAY;
            case "venus.png":
                return Color.LIGHTYELLOW;
            case "earth.png":
                return Color.LIGHTBLUE;
            case "mars.png":
                return Color.ORANGERED;
            case "jupiter.png":
                return Color.ORANGE;
            case "saturn.png":
                return Color.GOLD;
            case "uranus.png":
                return Color.LIGHTCYAN;
            case "neptune.png":
                return Color.DARKBLUE;
            default:
                return Color.GRAY;
        }
    }

    public void updatePosition(double time) {
        // Use pre-calculated angular velocity
        double angle = angularVelocity * time;

        // Calculate position in X-Z plane (horizontal plane with Y as vertical)
        double x = semiMajorAxis * Math.cos(angle) - centerXOffset;
        double z = semiMinorAxis * Math.sin(angle);

        bodyGroup.setTranslateX(x);
        bodyGroup.setTranslateY(0);  // Planets orbit in horizontal plane
        bodyGroup.setTranslateZ(z);
    }

    public void addRings(String ringTextureFile, double innerRadius, double outerRadius) {
        // Create a ring-shaped mesh
        TriangleMesh mesh = new TriangleMesh();

        int segments = 64; // Number of segments for circle

        // Generate vertices for inner and outer circles
        float[] points = new float[segments * 2 * 3]; // 2 circles * 3 coordinates each
        float[] texCoords = new float[segments * 2 * 2]; // 2 circles * 2 tex coords each

        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // Inner circle vertices in X-Z plane (Y=0 for horizontal)
            int innerIdx = i * 3;
            points[innerIdx] = cos * (float) innerRadius;      // X
            points[innerIdx + 1] = 0;                          // Y = 0 (horizontal plane)
            points[innerIdx + 2] = sin * (float) innerRadius;  // Z

            // Outer circle vertices in X-Z plane (Y=0 for horizontal)
            int outerIdx = (segments + i) * 3;
            points[outerIdx] = cos * (float) outerRadius;      // X
            points[outerIdx + 1] = 0;                          // Y = 0 (horizontal plane)
            points[outerIdx + 2] = sin * (float) outerRadius;  // Z

            // Texture coordinates - keep V constant around the ring and vary U based on the radius
            // U represents the radial position (0 = inner, 1 = outer)
            // V can vary around the circumference to create some variation
            float v = (float) i / (float) segments;

            // Inner circle texture coords
            texCoords[i * 2] = 0.0f;  // U = 0 for inner radius
            texCoords[i * 2 + 1] = v;  // V varies around circumference

            // Outer circle texture coords (right side of texture strip)
            texCoords[(segments + i) * 2] = 1.0f;  // U = 1 for outer radius
            texCoords[(segments + i) * 2 + 1] = v;  // V varies around circumference
        }

        mesh.getPoints().addAll(points);
        mesh.getTexCoords().addAll(texCoords);

        // Create faces connecting inner and outer circles
        int[] faces = new int[segments * 12]; // 2 triangles per segment * 6 values per triangle

        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            // First triangle
            faces[i * 12] = i;                    // inner vertex
            faces[i * 12 + 1] = i;                // texture coord
            faces[i * 12 + 2] = segments + i;      // outer vertex
            faces[i * 12 + 3] = segments + i;      // texture coord
            faces[i * 12 + 4] = segments + next;   // next outer vertex
            faces[i * 12 + 5] = segments + next;   // texture coord

            // Second triangle
            faces[i * 12 + 6] = i;                 // inner vertex
            faces[i * 12 + 7] = i;                 // texture coord
            faces[i * 12 + 8] = segments + next;   // next outer vertex
            faces[i * 12 + 9] = segments + next;   // texture coord
            faces[i * 12 + 10] = next;            // next inner vertex
            faces[i * 12 + 11] = next;            // texture coord
        }

        mesh.getFaces().addAll(faces);

        ringMesh = new MeshView(mesh);

        // Create material with the ring texture
        PhongMaterial ringMaterial = new PhongMaterial();
        try {
            URL textureUrl = getClass().getResource("/textures/" + ringTextureFile);
            if (textureUrl != null) {
                Image ringTexture = new Image(textureUrl.toExternalForm(), true);
                ringMaterial.setDiffuseMap(ringTexture);
                // For transparency to work properly
                ringMaterial.setSpecularColor(Color.TRANSPARENT);
            }
        } catch (Exception e) {
            System.err.println("Failed to load ring texture: " + e.getMessage());
        }

        ringMesh.setMaterial(ringMaterial);
        ringMesh.setCullFace(CullFace.NONE); // Show both sides

        double planetTiltAngle = axialTilt.getAngle();

        // Match the planet's tilt axis (Z-axis)
        Rotate ringTilt = new Rotate(planetTiltAngle, Rotate.Z_AXIS);

        ringMesh.getTransforms().add(ringTilt);

        // Add to the planet's group
        bodyGroup.getChildren().add(ringMesh);
    }

    public double getOrbitRadius() {
        return orbitRadius;
    }

    public double getOrbitEccentricity() {
        return orbitEccentricity;
    }

    public double getOrbitPeriod() {
        return orbitPeriod;
    }

    // Delegates to parent method
    public Group getPlanetGroup() {
        return getBodyGroup();
    }
}