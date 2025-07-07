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

        // Load texture with improved approach
        loadTexture(textureFile);

        // Ensure texture wraps correctly
        bodySphere.setCullFace(CullFace.NONE);
        bodySphere.setDrawMode(DrawMode.FILL);
        bodySphere.setMaterial(material);

        axialTilt = new Rotate(tiltAngle, Rotate.Z_AXIS);
        bodySphere.getTransforms().add(axialTilt);

        bodyGroup = new Group();
        bodyGroup.getChildren().add(bodySphere);
    }

    private void loadTexture(String textureFile) {
        String texturePath = "/textures/" + textureFile;
        System.out.println("Loading texture for celestial body: " + texturePath);

        try {
            URL textureUrl = getClass().getResource(texturePath);
            if (textureUrl != null) {
                // The second parameter 'true' enables background loading
                // JavaFX will cache this URL automatically
                Image texture = new Image(textureUrl.toExternalForm(), true);

                if (texture.isError()) {
                    System.err.println("ERROR: Failed to load texture -> " + texturePath);
                    texture.getException().printStackTrace();
                    return;
                }

                // Check if texture is already loaded
                if (texture.getProgress() == 1.0) {
                    material.setDiffuseMap(texture);
                    System.out.println("SUCCESS: Texture loaded immediately -> " + texturePath);
                } else {
                    // Set up listener for async load completion
                    texture.progressProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() == 1.0) {
                            if (!texture.isError()) {
                                material.setDiffuseMap(texture);
                                System.out.println("SUCCESS: Texture loaded async -> " + texturePath);
                            } else {
                                System.err.println("ERROR: Texture loading failed -> " + texturePath);
                                texture.getException().printStackTrace();
                            }
                        }
                    });
                }
            } else {
                System.err.println("ERROR: Texture resource not found -> " + texturePath);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load texture -> " + texturePath + " : " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected abstract Color getDefaultColor(String textureFile);

    public Group getBodyGroup() {
        return bodyGroup;
    }
}