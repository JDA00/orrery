package com.jda.orrery.controller;

import com.jda.orrery.model.Planet;
import com.jda.orrery.model.SolarSystem;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;

public class OrreryController {
    // Constants
    private static final double CAMERA_MIN_DISTANCE = -30;
    private static final double CAMERA_MAX_DISTANCE = -1500;
    private static final double CAMERA_DEFAULT_DISTANCE = -600;
    private static final double CAMERA_NEAR_CLIP = 0.1;
    private static final double CAMERA_FAR_CLIP = 5000;
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

    private Group root3D;
    private Group world;
    private PerspectiveCamera camera;
    private SolarSystem solarSystem;
    private Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

    // Variables to store previous mouse position and rotation angles
    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;

    @FXML
    public void initialize() {
        setup3DScene();
        setupUIControls();
    }

    /**
     * Configures the 3D scene graph and attaches it to the SubScene.
     */
    private void setup3DScene() {
        root3D = new Group();
        world = new Group();

        // Add rotation transforms for camera movement
        world.getTransforms().addAll(rotateX, rotateY);

        // Create Sun
        Sphere sun = new Sphere(20);
        PhongMaterial sunMaterial = new PhongMaterial();

        Image sunTexture = new Image(Objects.requireNonNull(getClass().getResource("/textures/sun.png")).toExternalForm());
        if (sunTexture.getWidth() > 0) {
            sunMaterial.setDiffuseMap(sunTexture);
        } else {
            sunMaterial.setDiffuseColor(Color.YELLOW);
        }

        sun.setMaterial(sunMaterial);
        world.getChildren().add(sun);

        // Initialize the solar system (planets, orbits)
        solarSystem = new SolarSystem();
        world.getChildren().add(solarSystem.getPlanetSystem());

        // Configure camera
        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(CAMERA_DEFAULT_DISTANCE);
        camera.setNearClip(CAMERA_NEAR_CLIP);
        camera.setFarClip(CAMERA_FAR_CLIP);
        camera.setFieldOfView(60);

        // Create SubScene dynamically with anti-aliasing
        SubScene orrerySubScene = new SubScene(root3D, subSceneContainer.getWidth(), subSceneContainer.getHeight(), true, SceneAntialiasing.BALANCED);

        orrerySubScene.setFill(Color.BLACK);
        orrerySubScene.setCamera(camera);

        // Bind SubScene size to Pane
        orrerySubScene.widthProperty().bind(subSceneContainer.widthProperty());
        orrerySubScene.heightProperty().bind(subSceneContainer.heightProperty());

        // Attach the world to the root 3D group
        root3D.getChildren().add(world);

        // Lighting
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(0);
        light.setTranslateY(-50);
        light.setTranslateZ(-200);

        AmbientLight ambientLight = new AmbientLight(Color.gray(0.4));

        root3D.getChildren().addAll(light, ambientLight);

        // Add the SubScene to the Pane
        subSceneContainer.getChildren().add(orrerySubScene);
    }

    /**
     * Sets up event handlers for UI buttons.
     */
    private void setupUIControls() {
        // Set button actions
        toggleOrbitsButton.setOnAction(event -> toggleOrbits());
        zoomInButton.setOnAction(event -> zoomIn());
        zoomOutButton.setOnAction(event -> zoomOut());
        resetCameraButton.setOnAction(event -> resetCamera());

        // Zoom with mouse scroll
        rootPane.setOnScroll(event -> {
            double newZ;

            if (event.getDeltaY() > 0) {
                // Zoom in
                newZ = camera.getTranslateZ() / ZOOM_FACTOR;
            } else {
                // Zoom out
                newZ = camera.getTranslateZ() * ZOOM_FACTOR;
            }

            // Keep zoom within reasonable limits
            if (newZ > CAMERA_MAX_DISTANCE && newZ < CAMERA_MIN_DISTANCE) {
                camera.setTranslateZ(newZ);
            }
        });

        rootPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            }
        });

        rootPane.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;

                rotateY.setAngle(anchorAngleY + deltaX * ROTATION_SENSITIVITY);
                rotateX.setAngle(anchorAngleX + deltaY * ROTATION_SENSITIVITY);
            }
        });
    }

    /**
     * Toggles visibility of planet orbits.
     */
    @FXML
    private void toggleOrbits() {
        solarSystem.toggleOrbitVisibility();
    }

    @FXML
    private void zoomIn() {
        double newZ = camera.getTranslateZ() / ZOOM_FACTOR;
        if (newZ > CAMERA_MAX_DISTANCE && newZ < CAMERA_MIN_DISTANCE) {
            camera.setTranslateZ(newZ);
        }
    }

    @FXML
    private void zoomOut() {
        double newZ = camera.getTranslateZ() * ZOOM_FACTOR;
        if (newZ > CAMERA_MAX_DISTANCE && newZ < CAMERA_MIN_DISTANCE) {
            camera.setTranslateZ(newZ);
        }
    }

    private void resetCamera() {
        // Reset zoom
        camera.setTranslateZ(CAMERA_DEFAULT_DISTANCE);

        rotateX.setAngle(0);
        rotateY.setAngle(0);

        // Update the anchor angles
        anchorAngleX = 0;
        anchorAngleY = 0;
    }

    public void shutdown() {
        if (solarSystem != null) {
            solarSystem.stop();
        }
    }

    public static class OrreryApp extends javafx.application.Application {
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