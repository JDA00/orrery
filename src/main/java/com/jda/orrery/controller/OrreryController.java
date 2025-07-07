package com.jda.orrery.controller;

import com.jda.orrery.model.SolarSystem;
import com.jda.orrery.view.OrreryView;
import javafx.fxml.FXML;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;

/**
 * FXML Controller for the Orrery application.
 * Handles user interactions and coordinates between the model and view.
 */
public class OrreryController {
    // Constants
    private static final double ZOOM_FACTOR = 1.25;
    private static final double ROTATION_SENSITIVITY = 0.3;

    @FXML
    private BorderPane rootPane;

    @FXML
    private Pane subSceneContainer;

    @FXML
    private Button toggleOrbitsButton, zoomInButton, zoomOutButton, resetCameraButton;

    private OrreryView orreryView;
    private SolarSystem solarSystem;

    // Variables to store previous mouse position
    private double anchorX, anchorY;

    @FXML
    public void initialize() {
        // Initialize model and view
        orreryView = new OrreryView();
        solarSystem = new SolarSystem();

        // Create the 3D scene through the view
        SubScene subScene = orreryView.createScene(
                subSceneContainer.getWidth(),
                subSceneContainer.getHeight(),
                solarSystem
        );

        // Bind SubScene size to container
        orreryView.bindSize(
                subSceneContainer.widthProperty(),
                subSceneContainer.heightProperty()
        );

        // Add SubScene to container
        subSceneContainer.getChildren().add(subScene);

        // Setup UI controls
        setupUIControls();
    }

    private void setupUIControls() {
        // Set button actions
        toggleOrbitsButton.setOnAction(event -> toggleOrbits());
        zoomInButton.setOnAction(event -> zoomIn());
        zoomOutButton.setOnAction(event -> zoomOut());
        resetCameraButton.setOnAction(event -> resetCamera());

        // Zoom with mouse scroll
        rootPane.setOnScroll(event -> {
            double currentZ = orreryView.getCamera().getTranslateZ();
            double newZ;

            if (event.getDeltaY() > 0) {
                newZ = currentZ / ZOOM_FACTOR;
            } else {
                newZ = currentZ * ZOOM_FACTOR;
            }

            // Keep zoom within limits
            if (newZ > orreryView.getCameraMaxDistance() &&
                    newZ < orreryView.getCameraMinDistance()) {
                orreryView.getCamera().setTranslateZ(newZ);
            }
        });

        // Mouse press handler
        rootPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();
            }
        });

        // Mouse drag handler using delta rotations
        rootPane.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;

                // Apply the rotation deltas
                orreryView.applyRotation(
                        deltaX * ROTATION_SENSITIVITY,
                        deltaY * ROTATION_SENSITIVITY
                );

                // Update anchor position for next frame
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();
            }
        });
    }

    @FXML
    private void toggleOrbits() {
        if (solarSystem != null) {
            solarSystem.toggleOrbitVisibility();
        }
    }

    @FXML
    private void zoomIn() {
        double currentZ = orreryView.getCamera().getTranslateZ();
        double newZ = currentZ / ZOOM_FACTOR;
        if (newZ > orreryView.getCameraMaxDistance() &&
                newZ < orreryView.getCameraMinDistance()) {
            orreryView.getCamera().setTranslateZ(newZ);
        }
    }

    @FXML
    private void zoomOut() {
        double currentZ = orreryView.getCamera().getTranslateZ();
        double newZ = currentZ * ZOOM_FACTOR;
        if (newZ > orreryView.getCameraMaxDistance() &&
                newZ < orreryView.getCameraMinDistance()) {
            orreryView.getCamera().setTranslateZ(newZ);
        }
    }

    private void resetCamera() {
        // Reset zoom
        orreryView.getCamera().setTranslateZ(orreryView.getCameraDefaultDistance());

        // Reset rotation
        orreryView.resetOrientation();
    }

    public void shutdown() {
        if (solarSystem != null) {
            solarSystem.stop();
        }
    }
}