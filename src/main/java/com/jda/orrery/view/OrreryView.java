package com.jda.orrery.view;

import com.jda.orrery.camera.CameraContext;
import com.jda.orrery.camera.CameraStateMachine;
import com.jda.orrery.camera.states.FreeViewState;
import com.jda.orrery.model.SolarSystem;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.*;
import javafx.scene.paint.Color;

public class OrreryView {
    // Camera constants
    private static final double CAMERA_MIN_DISTANCE = -30;
    private static final double CAMERA_MAX_DISTANCE = -1500;
    private static final double CAMERA_DEFAULT_DISTANCE = -600;
    private static final double CAMERA_NEAR_CLIP = 0.1;
    private static final double CAMERA_FAR_CLIP = 5000;

    // Core components
    private final Group root3D = new Group();
    private final Group world = new Group();
    private PerspectiveCamera camera;
    private SubScene subScene;

    // State machine
    private CameraStateMachine cameraStateMachine;
    private AnimationTimer cameraUpdateTimer;

    /**
     * Creates the 3D scene with camera and lighting
     */
    public SubScene createScene(double width, double height, SolarSystem solarSystem) {
        // Add solar system to world
        world.getChildren().add(solarSystem.getPlanetSystem());

        // Setup camera
        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(CAMERA_DEFAULT_DISTANCE);
        camera.setNearClip(CAMERA_NEAR_CLIP);
        camera.setFarClip(CAMERA_FAR_CLIP);
        camera.setFieldOfView(60);

        // Setup camera state machine
        CameraContext cameraContext = new CameraContext(camera, world);
        cameraStateMachine = new CameraStateMachine(cameraContext);

        // Create SubScene
        subScene = new SubScene(root3D, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);

        // Setup lighting
        PointLight light = new PointLight(Color.LIGHTYELLOW);
        light.setTranslateX(0);
        light.setTranslateY(0);
        light.setTranslateZ(0);

        AmbientLight ambientLight = new AmbientLight(Color.gray(0.2));

        // Add everything to root
        root3D.getChildren().addAll(world, light, ambientLight);

        // Start camera update timer
        startCameraUpdateTimer();

        return subScene;
    }

    /**
     * Starts the animation timer that updates camera states
     */
    private void startCameraUpdateTimer() {
        cameraUpdateTimer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                cameraStateMachine.update(deltaTime);
                lastUpdate = now;
            }
        };
        cameraUpdateTimer.start();
    }

    /**
     * Binds the SubScene size to the container size
     */
    public void bindSize(ReadOnlyDoubleProperty width, ReadOnlyDoubleProperty height) {
        subScene.widthProperty().bind(width);
        subScene.heightProperty().bind(height);
    }

    /**
     * Stops the camera update timer (call this on shutdown)
     */
    public void stop() {
        if (cameraUpdateTimer != null) {
            cameraUpdateTimer.stop();
        }
    }

    // Getters for external access

    public CameraStateMachine getCameraStateMachine() {
        return cameraStateMachine;
    }

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