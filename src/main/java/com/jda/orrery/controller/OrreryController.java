package com.jda.orrery.controller;

import com.jda.orrery.model.SolarSystem;
import com.jda.orrery.view.OrreryView;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.io.IOException;

public class OrreryController {
    // Constants
    private static final double ZOOM_FACTOR = 1.25;
    private static final double ROTATION_SENSITIVITY = 0.3;
    private static final int WINDOW_WIDTH = 3840;
    private static final int WINDOW_HEIGHT = 2160;
    private static final int MIN_WINDOW_WIDTH = 1920;
    private static final int MIN_WINDOW_HEIGHT = 1080;

    @FXML
    private BorderPane rootPane;

    @FXML
    private Pane subSceneContainer;

    @FXML
    private Button toggleOrbitsButton, zoomInButton, zoomOutButton, resetCameraButton;

    private OrreryView orreryView;
    private SolarSystem solarSystem;
    private Rotate rotateX;
    private Rotate rotateY;

    // Variables to store previous mouse position and rotation angles
    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;

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

        // Get rotation references from view
        rotateX = orreryView.getRotateX();
        rotateY = orreryView.getRotateY();

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
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            }
        });

        // Mouse drag handler
        rootPane.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;

                rotateY.setAngle(anchorAngleY + deltaX * ROTATION_SENSITIVITY);
                rotateX.setAngle(anchorAngleX + deltaY * ROTATION_SENSITIVITY);
            }
        });
    }

    @FXML
    private void toggleOrbits() {
        solarSystem.toggleOrbitVisibility();
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
        rotateX.setAngle(0);
        rotateY.setAngle(0);

        // Update anchor angles
        anchorAngleX = 0;
        anchorAngleY = 0;
    }

    public void shutdown() {
        if (solarSystem != null) {
            solarSystem.stop();
        }
    }

    // Inner Application class
    public static class OrreryApp extends Application {
        private OrreryController controller;

        @Override
        public void start(Stage primaryStage) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orrery.fxml"));
                BorderPane root = loader.load();

                controller = loader.getController();

                Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

                // Add CSS styling
                String css = getClass().getResource("/css/style.css").toExternalForm();
                scene.getStylesheets().add(css);

                primaryStage.setTitle("3D Orrery");
                primaryStage.setScene(scene);
                primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
                primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);
                primaryStage.setMaximized(true);

                primaryStage.setOnCloseRequest(event -> {
                    if (controller != null) {
                        controller.shutdown();
                    }
                });

                primaryStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
}