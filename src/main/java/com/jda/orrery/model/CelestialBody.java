package com.jda.orrery.model;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

import java.net.URL;

public abstract class CelestialBody {
    protected final Group bodyGroup;
    protected final Sphere bodySphere;
    protected final PhongMaterial material;
    protected final Rotate axialTilt;

    public CelestialBody(double radius, String textureFile, double tiltAngle) {
        bodySphere = new Sphere(radius);

        // Fix orientation
        Rotate fixOrientation = new Rotate(90, Rotate.X_AXIS);
        bodySphere.getTransforms().add(fixOrientation);

        material = new PhongMaterial();

        // Set a default color based on body type
        Color defaultColor = getDefaultColor(textureFile);
        material.setDiffuseColor(defaultColor);

        // Load texture if specified
        if (textureFile != null && !textureFile.isEmpty()) {
            loadTexture(textureFile);
        }

        // Configure sphere rendering
        bodySphere.setCullFace(CullFace.NONE);
        bodySphere.setDrawMode(DrawMode.FILL);
        bodySphere.setMaterial(material);

        // Apply axial tilt
        axialTilt = new Rotate(tiltAngle, Rotate.Z_AXIS);
        bodySphere.getTransforms().add(axialTilt);

        // Create group container
        bodyGroup = new Group();
        bodyGroup.getChildren().add(bodySphere);
    }

    private void loadTexture(String textureFile) {
        String texturePath = "/textures/" + textureFile;

        try {
            URL textureUrl = getClass().getResource(texturePath);
            if (textureUrl == null) {
                return; // Use default color
            }

            // Load texture asynchronously
            Image texture = new Image(textureUrl.toExternalForm(), true);
            material.setDiffuseMap(texture);

        } catch (Exception e) {
            // Silent fallback to default color
        }
    }

    protected abstract Color getDefaultColor(String textureFile);

    public Group getBodyGroup() {
        return bodyGroup;
    }

    public double getTiltAngle() {
        return axialTilt.getAngle();
    }

    public double getRadius() {
        return bodySphere.getRadius();
    }
}