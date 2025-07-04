package com.jda.orrery.model;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Rotate;

import java.io.InputStream;

public class Planet {
    private final Group planetGroup;
    private final Sphere planetSphere;
    private final double orbitRadius;
    private final double orbitPeriod;
    private final double orbitEccentricity;
    private final Rotate axialTilt;
    private final PhongMaterial material;

    // Pre-calculate orbit parameters
    private final double semiMajorAxis;
    private final double semiMinorAxis;
    private final double centerXOffset;
    private final double angularVelocity;

    public Planet(double radius, String textureFile, double orbitRadius, double orbitPeriod, double orbitEccentricity, double tiltAngle) {
        this.orbitRadius = orbitRadius;
        this.orbitPeriod = orbitPeriod;
        this.orbitEccentricity = orbitEccentricity;

        this.semiMajorAxis = orbitRadius;
        this.semiMinorAxis = semiMajorAxis * Math.sqrt(1 - orbitEccentricity * orbitEccentricity);
        this.centerXOffset = semiMajorAxis * orbitEccentricity;
        this.angularVelocity = (2 * Math.PI) / orbitPeriod;

        planetSphere = new Sphere(radius);
        material = new PhongMaterial();

        // Set a default color based on planet
        Color defaultColor = getDefaultColor(textureFile);
        material.setDiffuseColor(defaultColor);

        // Load texture synchronously to ensure it's applied
        String texturePath = "/textures/" + textureFile;
        System.out.println("Loading texture for planet: " + texturePath);

        try {
            // Use getResourceAsStream for more reliable loading
            InputStream textureStream = getClass().getResourceAsStream(texturePath);
            if (textureStream != null) {
                Image texture = new Image(textureStream);

                // Wait for texture to load if necessary
                if (texture.getWidth() > 0) {
                    material.setDiffuseMap(texture);
                    System.out.println("SUCCESS: Texture loaded immediately -> " + texturePath);
                } else {
                    // If not loaded immediately, set up listener
                    texture.progressProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() == 1.0) {
                            material.setDiffuseMap(texture);
                            planetSphere.setMaterial(material); // Re-apply material
                            System.out.println("SUCCESS: Texture loaded async -> " + texturePath);
                        }
                    });
                }
                textureStream.close();
            } else {
                System.err.println("ERROR: Texture resource not found -> " + texturePath);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load texture -> " + texturePath + " : " + e.getMessage());
        }

        // Ensure texture wraps correctly
        planetSphere.setCullFace(CullFace.NONE);
        planetSphere.setDrawMode(DrawMode.FILL);
        planetSphere.setMaterial(material);

        axialTilt = new Rotate(tiltAngle, Rotate.Z_AXIS);
        planetSphere.getTransforms().add(axialTilt);

        planetGroup = new Group();
        planetGroup.getChildren().add(planetSphere);

        updatePosition(0);
    }

    private Color getDefaultColor(String textureFile) {
        switch (textureFile.toLowerCase()) {
            case "sun.png":
                return Color.YELLOW;
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
        if (orbitRadius == 0) return; // Sun doesn't orbit

        // Use pre-calculated angular velocity
        double angle = angularVelocity * time;

        // Use pre-calculated orbit parameters
        double x = semiMajorAxis * Math.cos(angle) - centerXOffset;
        double y = semiMinorAxis * Math.sin(angle);

        planetGroup.setTranslateX(x);
        planetGroup.setTranslateY(y);
        planetGroup.setTranslateZ(0);
    }

    public Group getPlanetGroup() {
        return planetGroup;
    }

    public double getOrbitRadius() {
        return orbitRadius;
    }

    public double getOrbitPeriod() {
        return orbitPeriod;
    }

    public double getOrbitEccentricity() {
        return orbitEccentricity;
    }
}