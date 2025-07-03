package com.jda.orrery.controller;

import com.jda.orrery.model.Planet;
import com.jda.orrery.model.SolarSystem;
import javafx.animation.RotateTransition;
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

    @FXML
    private BorderPane rootPane; // Main UI container

    @FXML
    private Pane subSceneContainer; // Now using a Pane instead of SubScene


    @FXML
    private Button toggleOrbitsButton, zoomInButton, zoomOutButton, resetCameraButton;

    @FXML private Pane orbitOverlay;


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

    private double cameraDistance = -300; // Default camera position




    /**
     * Initializes the UI and 3D world.
     */
    @FXML
    public void initialize() {
        setup3DScene();
        setupUIControls();

        // Ensure orbitOverlay is empty before adding new paths
        orbitOverlay.getChildren().clear();

        for (Planet planet : solarSystem.getPlanets()) {
            if (planet.getOrbitRadius() > 0) {
                Path orbitPath = drawOrbitPath(
                        subSceneContainer.getWidth() / 2, // Center X
                        subSceneContainer.getHeight() / 2, // Center Y
                        planet.getOrbitRadius()
                );
                orbitOverlay.getChildren().add(orbitPath);
                System.out.println("Orbit path added for planet at radius: " + planet.getOrbitRadius());
            }
        }
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
        if (sunTexture.getWidth() > 0) { // Ensure texture loaded correctly
            sunMaterial.setDiffuseMap(sunTexture);
        } else {
            sunMaterial.setDiffuseColor(Color.YELLOW); // Fallback if texture fails
        }


        sun.setMaterial(sunMaterial);
        world.getChildren().add(sun); // Add sun to the world

        // Initialize the solar system (planets, orbits)
        solarSystem = new SolarSystem();
        world.getChildren().add(solarSystem.getPlanetSystem());

        // Configure camera - set clipping planes to prevent objects from disappearing
        camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-400); // Start at a reasonable distance
        camera.setNearClip(0.1);  // Objects can be very close to the camera
        camera.setFarClip(5000);  // Objects won't disappear even when zooming out


        // Create SubScene dynamically
        SubScene orrerySubScene = new SubScene(root3D, subSceneContainer.getWidth(), subSceneContainer.getHeight(), true, SceneAntialiasing.BALANCED);


        orrerySubScene.setFill(Color.BLACK);
        orrerySubScene.setCamera(camera);

        // Bind SubScene size to Pane
        orrerySubScene.widthProperty().bind(subSceneContainer.widthProperty());
        orrerySubScene.heightProperty().bind(subSceneContainer.heightProperty());

        // Attach the world to the root 3D group
        root3D.getChildren().add(world);

        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(0);
        light.setTranslateY(-50);
        light.setTranslateZ(-200);

        AmbientLight ambientLight = new AmbientLight(Color.LIGHTGRAY);

        root3D.getChildren().addAll(light, ambientLight);

        for (Planet planet : solarSystem.getPlanets()) {
            if (planet.getOrbitRadius() > 0) { // Sun has no orbit
                Path orbitPath = drawOrbitPath(subSceneContainer.getWidth() / 2, subSceneContainer.getHeight() / 2, planet.getOrbitRadius());
                orbitOverlay.getChildren().add(orbitPath);
            }
        }



        // Add the SubScene to the Pane
        subSceneContainer.getChildren().add(orrerySubScene);
    }






    /**
     * Sets up event handlers for UI buttons.
     */
    private void setupUIControls() {
        toggleOrbitsButton.setOnAction(event -> orbitOverlay.setVisible(!orbitOverlay.isVisible()));

        zoomInButton.setOnAction(event -> zoomIn());
        zoomOutButton.setOnAction(event -> zoomOut());
        resetCameraButton.setOnAction(event -> resetCamera());
        rootPane.setOnScroll(event -> {
            double zoomFactor = 1.25; // Increase this for stronger zoom steps
            double newZ;

            if (event.getDeltaY() > 0) {
                // Stronger zoom-in effect by adjusting step size
                newZ = camera.getTranslateZ() - (camera.getTranslateZ() * (zoomFactor - 1) * 1.5); // Increase multiplier for stronger effect
            } else {
                // Zoom out remains the same
                newZ = camera.getTranslateZ() * zoomFactor;
            }

            // Keep zoom within reasonable limits
            if (newZ > -1500 && newZ < -30) {
                camera.setTranslateZ(newZ);
                adjustOrbitLineThickness();
            }
        });

        rootPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) { // Left mouse button
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            }
        });

        rootPane.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) { // Left mouse button
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;

                // Adjust sensitivity (lower values = smoother rotation)
                rotateY.setAngle(anchorAngleY + deltaX * 0.3); // Rotate left/right
                rotateX.setAngle(anchorAngleX - deltaY * 0.3); // Rotate up/down
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

    private void adjustOrbitLineThickness() {
        if (solarSystem == null) return; // Ensure solar system is initialized

        double scaleFactor = Math.abs(camera.getTranslateZ()) / 300.0; // Adjust factor based on zoom
        double newStrokeWidth = Math.max(0.5, 2.0 / scaleFactor); // Keep width within a range

        for (Node node : solarSystem.getOrbitLines().getChildren()) {
            if (node instanceof Path) {
                ((Path) node).setStrokeWidth(newStrokeWidth);
            }
        }
    }


    private Path drawOrbitPath(double centerX, double centerY, double radius) {
        Path path = new Path();
        int segments = 180; // More segments = smoother orbit
        double angleStep = 360.0 / segments;

        double startX = centerX + radius * Math.cos(0);
        double startY = centerY + radius * Math.sin(0);
        path.getElements().add(new MoveTo(startX, startY));

        for (int i = 1; i <= segments; i++) {
            double angle = Math.toRadians(i * angleStep);
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            path.getElements().add(new LineTo(x, y));
        }

        path.setStroke(Color.LIGHTGRAY);
        path.setFill(null);
        path.setStrokeWidth(1.2);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeType(StrokeType.CENTERED);
        path.setSmooth(true);

        return path;
    }



    /**
     * Moves the camera closer to the scene.
     */
    @FXML
    private void zoomIn() {
        double zoomFactor = 1.15; // Same rate for both directions
        double newZ = camera.getTranslateZ() / zoomFactor;
        if (newZ > -1500 && newZ < -30) { // Keep within limits
            camera.setTranslateZ(newZ);
        }
    }

    /**
     * Moves the camera further from the scene.
     */
    @FXML
    private void zoomOut() {
        double zoomFactor = 1.15; // Same rate for both directions
        double newZ = camera.getTranslateZ() * zoomFactor; // Multiply when zooming out
        if (newZ > -1500 && newZ < -30) { // Keep within limits
            camera.setTranslateZ(newZ);
        }
    }

    /**
     * Resets the camera to its default position.
     */
    private void resetCamera() {
        camera.setTranslateZ(cameraDistance);
    }

    /**
     * Launches the JavaFX application.
     */
    public static class OrreryApp extends javafx.application.Application {
        @Override
        public void start(Stage primaryStage) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orrery.fxml"));
                BorderPane root = loader.load();

                // Increase window size
                Scene scene = new Scene(root, 3840, 2160); // Set a larger scene size
                primaryStage.setTitle("3D Orrery");
                primaryStage.setScene(scene);
                primaryStage.setMinWidth(1920); // Prevent shrinking too much
                primaryStage.setMinHeight(1080);
                primaryStage.setMaximized(true); // Start in maximized mode
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
