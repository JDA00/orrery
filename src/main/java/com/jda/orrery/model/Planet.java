package com.jda.orrery.model;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Rotate;

import java.io.File;
import java.util.Objects;

public class Planet {
    private final Group planetGroup;
    private final Sphere planetSphere;
    private final double orbitRadius;
    private final double orbitPeriod;
    private final double orbitEccentricity;
    private final Rotate axialTilt;
    private final PhongMaterial material;

    public Planet(double radius, String textureFile, double orbitRadius, double orbitPeriod, double orbitEccentricity, double tiltAngle) {
        this.orbitRadius = orbitRadius;
        this.orbitPeriod = orbitPeriod;
        this.orbitEccentricity = orbitEccentricity;

        planetSphere = new Sphere(radius);
        material = new PhongMaterial();

        // Debugging: Print the expected texture path
        String texturePath = "/textures/" + textureFile;
        System.out.println("Checking resource path: " + getClass().getResource(texturePath));

        if (getClass().getResource(texturePath) != null) {
            loadTexture(texturePath);
        } else {

            // JavaFX can't find the texture in resources
            File file = new File("src/main/resources/textures/" + textureFile);
            System.out.println("Trying absolute path: " + file.getAbsolutePath());

            if (file.exists()) {
                loadTexture(file.toURI().toString());
            } else {
                System.out.println("ERROR: Texture file not found -> " + file.getAbsolutePath());
                material.setDiffuseColor(Color.BLUE);  // Default if texture fails
            }
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

    /**
     * Loads the texture asynchronously and only applies it when fully loaded.
     */
    private void loadTexture(String path) {
        try {
            System.out.println("Loading texture: " + path);

            Image texture;

            if (path.startsWith("file:")) {
                // Load using URI for absolute paths
                texture = new Image(path, true);
            } else {
                // Load using InputStream for classpath resources
                texture = new Image(Objects.requireNonNull(getClass().getResource(path)).toExternalForm(), true);
            }

            texture.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() == 1.0) { // Texture is fully loaded
                    System.out.println("SUCCESS: Texture loaded -> " + path);
                    System.out.println("Texture Dimensions: " + texture.getWidth() + " x " + texture.getHeight());

                    // Ensure the material updates properly
                    material.setDiffuseMap(texture);
//                    planetSphere.setMaterial(null);  // Force JavaFX to refresh material
                    planetSphere.setMaterial(material);
                }
            });
        } catch (Exception e) {
            System.out.println("Exception loading texture: " + e.getMessage());
        }
    }


    public void updatePosition(double time) {
        double angle = (2 * Math.PI * time) / orbitPeriod;

        // ✅ Corrected elliptical orbit using semi-major and semi-minor axes
        double semiMajorAxis = orbitRadius;
        double semiMinorAxis = semiMajorAxis * Math.sqrt(1 - orbitEccentricity * orbitEccentricity);

        // ✅ Account for center offset due to eccentricity
        double centerXOffset = semiMajorAxis * orbitEccentricity;

        // ✅ Corrected elliptical trajectory (Kepler's Laws)
        double x = semiMajorAxis * Math.cos(angle) - centerXOffset;
        double y = semiMinorAxis * Math.sin(angle);
        double z = 0; // Keep Z constant for now

        planetGroup.setTranslateX(x);
        planetGroup.setTranslateY(y);
        planetGroup.setTranslateZ(z);
    }




    public Group getPlanetGroup() {
        return planetGroup;
    }

    public double getOrbitRadius() {
        return orbitRadius;
    }

    public double getOrbitPeriod() { return orbitPeriod;}

    public double getOrbitEccentricity() { return orbitEccentricity;}
}
