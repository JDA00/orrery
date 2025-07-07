package com.jda.orrery.view;

import com.jda.orrery.model.SolarSystem;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;

public class OrreryView {
    // Constants
    private static final double CAMERA_MIN_DISTANCE = -30;
    private static final double CAMERA_MAX_DISTANCE = -1500;
    private static final double CAMERA_DEFAULT_DISTANCE = -600;
    private static final double CAMERA_NEAR_CLIP = 0.1;
    private static final double CAMERA_FAR_CLIP = 5000;

    private final Group root3D = new Group();
    private final Group world = new Group();
    private PerspectiveCamera camera;
    private SubScene subScene;

    // Store the complete orientation
    private Transform worldTransform = new Rotate(0, Rotate.Y_AXIS);

    public SubScene createScene(double width, double height, SolarSystem solarSystem) {
        // Apply the initial transform to the world
        world.getTransforms().add(worldTransform);

        // Add solar-system
        world.getChildren().add(solarSystem.getPlanetSystem());

        // Setup camera
        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(CAMERA_DEFAULT_DISTANCE);
        camera.setNearClip(CAMERA_NEAR_CLIP);
        camera.setFarClip(CAMERA_FAR_CLIP);
        camera.setFieldOfView(60);

        // Create SubScene with antialiasing
        subScene = new SubScene(root3D, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);

        // Setup lighting
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(0);
        light.setTranslateY(-50);
        light.setTranslateZ(-200);

        AmbientLight ambientLight = new AmbientLight(Color.gray(0.4));

        // Add everything to root
        root3D.getChildren().addAll(world, light, ambientLight);

        return subScene;
    }

    public void bindSize(ReadOnlyDoubleProperty width, ReadOnlyDoubleProperty height) {
        subScene.widthProperty().bind(width);
        subScene.heightProperty().bind(height);
    }

    /**
     * Apply a rotation delta to the current world transform.
     * This accumulates rotations without gimbal-lock.
     */
    public void applyRotation(double deltaX, double deltaY) {
        // Rotate around the world Y-axis regardless of current orientation
        Rotate yRotation = new Rotate(-deltaX, 0, 0, 0, Rotate.Y_AXIS);

        // Get the current right vector (X-axis in view space)
        Point3D rightAxis = worldTransform.transform(1, 0, 0).normalize();

        // Create rotation around the view-space horizontal axis
        Rotate xRotation = new Rotate(deltaY, 0, 0, 0, rightAxis);

        // Apply rotations: Y rotation first, then X rotation
        worldTransform = yRotation.createConcatenation(xRotation).createConcatenation(worldTransform);

        // Update the world's transform
        world.getTransforms().clear();
        world.getTransforms().add(worldTransform);
    }

    /**
     * Reset the world orientation to default
     */
    public void resetOrientation() {
        worldTransform = new Rotate(0, Rotate.Y_AXIS);
        world.getTransforms().clear();
        world.getTransforms().add(worldTransform);
    }

    // Getters for controller access
    public PerspectiveCamera getCamera() {
        return camera;
    }

    public double getCameraMinDistance() {
        return CAMERA_MIN_DISTANCE;
    }

    public double getCameraMaxDistance() {
        return CAMERA_MAX_DISTANCE;
    }

    public double getCameraDefaultDistance() {
        return CAMERA_DEFAULT_DISTANCE;
    }
}