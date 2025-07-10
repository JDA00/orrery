package com.jda.orrery.model;

import com.jda.orrery.view.SphereMeshGenerator;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;

import java.net.URL;

public abstract class CelestialBody {
    protected final Group bodyGroup;
    protected final MeshView bodySphere;
    protected final PhongMaterial material;
    protected final Rotate axialTilt;
    private final String name;
    private final double radius;

    // Planet material configuration enum
    private enum PlanetMaterial {
        EARTH(Color.gray(0.05), 4.0, Color.WHITE, "Applied maximum brightness boost"),
        VENUS(Color.gray(0.03), 2.0, null, null),
        DEFAULT(Color.BLACK, 0.0, null, null);

        private final Color specularColor;
        private final double specularPower;
        private final Color diffuseColor;
        private final String debugMessage;

        PlanetMaterial(Color specularColor, double specularPower, Color diffuseColor, String debugMessage) {
            this.specularColor = specularColor;
            this.specularPower = specularPower;
            this.diffuseColor = diffuseColor;
            this.debugMessage = debugMessage;
        }

        // Find appropriate material by filename
        static PlanetMaterial fromFilename(String filename) {
            String lower = filename.toLowerCase();

            if (lower.contains("earth")) return EARTH;
            if (lower.contains("venus")) return VENUS;

            return DEFAULT;
        }

        // Apply this material configuration
        void applyTo(PhongMaterial material, String bodyName) {
            material.setSpecularColor(specularColor);
            material.setSpecularPower(specularPower);

            if (diffuseColor != null) {
                material.setDiffuseColor(diffuseColor);
            }

            if (debugMessage != null) {
                System.out.println("[" + bodyName + "] " + debugMessage);
            }
        }
    }

    public CelestialBody(String name, double radius, String textureFile, double tiltAngle) {
        this.name = name;
        this.radius = radius;

        // Create custom sphere
        TriangleMesh sphereMesh = SphereMeshGenerator.generate(radius, SphereMeshGenerator.Quality.ULTRA);
        bodySphere = new MeshView(sphereMesh);

        material = new PhongMaterial();

        // Set a default color based on body type
        Color defaultColor = getDefaultColor(textureFile);
        material.setDiffuseColor(defaultColor);

        // Load texture if specified
        if (textureFile != null && !textureFile.isEmpty()) {
            loadTexture(textureFile);
        }

        // Configure sphere rendering
        bodySphere.setCullFace(CullFace.BACK);  // Only render front faces
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
        if (!isValidTextureFile(textureFile)) return;

        Image texture = loadTextureResource(textureFile);
        if (texture == null) return;

        applyTextureToMaterial(texture);
        configureMaterialProperties(textureFile, texture);
        setupProgressListener(texture, textureFile);
    }

    // Extracted validation method
    private boolean isValidTextureFile(String textureFile) {
        if (textureFile == null || textureFile.trim().isEmpty()) {
            System.out.println("[" + name + "] No texture specified, using default color");
            return false;
        }
        return true;
    }

    // Separate resource loading
    private Image loadTextureResource(String textureFile) {
        String texturePath = "/textures/" + textureFile;

        try {
            URL textureUrl = getClass().getResource(texturePath);
            if (textureUrl == null) {
                System.out.println("[" + name + "] Texture not found: " + textureFile + " - using default color");
                return null;
            }

            boolean loadInBackground = !isSunTexture(textureFile);
            Image texture = new Image(textureUrl.toExternalForm(), loadInBackground);

            if (texture.isError()) {
                System.err.println("[" + name + "] Failed to load texture: " + textureFile);
                return null;
            }

            return texture;
        } catch (Exception e) {
            System.err.println("[" + name + "] Exception loading texture " + textureFile + ": " + e.getMessage());
            return null;
        }
    }

    // Texture application
    private void applyTextureToMaterial(Image texture) {
        material.setDiffuseMap(texture);
    }

    // Material configuration dispatch
    private void configureMaterialProperties(String textureFile, Image texture) {
        if (isSunTexture(textureFile)) {
            configureSunMaterial(texture);
        } else {
            configurePlanetMaterial(textureFile);
        }
    }

    private boolean isSunTexture(String textureFile) {
        return name.toLowerCase().contains("sun") || textureFile.toLowerCase().contains("sun");
    }

    private void configureSunMaterial(Image texture) {
        material.setSelfIlluminationMap(texture);
        material.setSpecularColor(Color.BLACK);
        material.setSpecularPower(0.0);
        System.out.println("[" + name + "] Applied self-illumination");
    }

    private void configurePlanetMaterial(String textureFile) {
        PlanetMaterial planetMaterial = PlanetMaterial.fromFilename(textureFile);
        planetMaterial.applyTo(material, name);
    }

    private void setupProgressListener(Image texture, String textureFile) {
        texture.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            if (newProgress.doubleValue() == 1.0 && !texture.isError()) {
                System.out.println("[" + name + "] Loaded texture: " + textureFile +
                        " (" + (int)texture.getWidth() + "x" + (int)texture.getHeight() + ")");
            }
        });
    }

    protected abstract Color getDefaultColor(String textureFile);

    public String getName() {
        return name;
    }

    public Group getBodyGroup() {
        return bodyGroup;
    }

    public double getTiltAngle() {
        return axialTilt.getAngle();
    }

    public double getRadius() {
        return this.radius;  // Return stored radius
    }
}