package com.jda.orrery.controller;

import com.jda.orrery.camera.states.AttachedState;
import com.jda.orrery.camera.states.CameraTransitionState;
import com.jda.orrery.model.CelestialBody;
import com.jda.orrery.model.SolarSystem;
import com.jda.orrery.view.OrreryView;
import javafx.fxml.FXML;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;


/**
 * FXML Controller for the Orrery application.
 * Handles user interactions and coordinates between the model and view.
 */
public class OrreryController {

    @FXML
    private BorderPane rootPane;

    @FXML
    private Pane subSceneContainer;

    @FXML
    private Button toggleOrbitsButton, zoomInButton, zoomOutButton, resetCameraButton;

    @FXML
    private HBox bottomControlPanel;

    private Label currentBodyLabel;

    private OrreryView orreryView;
    private SolarSystem solarSystem;

    // Camera attachment state
    private int currentBodyIndex = 0; // Start at 0 (Sun) instead of -1
    private static final String[] BODY_NAMES = {
            "Sun",
            "Mercury",
            "Venus",
            "Earth",
            "Mars",
            "Jupiter",
            "Saturn",
            "Uranus",
            "Neptune"
    };

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

        // Add current body label
        setupBodyLabel();

        // Setup keyboard controls
        setupKeyboardControls();

        // Start attached to Sun with bird's-eye view
        CelestialBody sun = solarSystem.getCelestialBody(0);

        // Create AttachedState with bird's-eye view parameters
        AttachedState sunState = new AttachedState(sun, -135.0, 85.0, 600.0);
        orreryView.getCameraStateMachine().setState(sunState);
        currentBodyIndex = 0;
        updateBodyLabel();
    }

    private void setupBodyLabel() {
        // Use a Button instead of Label
        Button labelButton = new Button("Following: Sun");
        labelButton.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-color: transparent; " +
                        "-fx-padding: 4 8 4 8;"
        );
        labelButton.setFocusTraversable(false);
        labelButton.setMouseTransparent(true); // Can't be clicked

        currentBodyLabel = new Label();
        labelButton.textProperty().bind(currentBodyLabel.textProperty());
        currentBodyLabel.setText("Following: Sun");

        ToolBar toolbar = (ToolBar) rootPane.getTop();
        toolbar.getItems().add(labelButton);
    }

    private void setupKeyboardControls() {
        // Request focus for keyboard events
        rootPane.setFocusTraversable(true);

        // Handle key press events
        rootPane.setOnKeyPressed(this::handleKeyPress);

        // Ensure the pane can receive focus
        rootPane.requestFocus();
    }

    private void handleKeyPress(KeyEvent event) {
        KeyCode code = event.getCode();

        if (code == KeyCode.LEFT) {
            cycleCameraLeft();
            event.consume();
        } else if (code == KeyCode.RIGHT) {
            cycleCameraRight();
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            // Escape returns to Sun (with bird's eye view)
            returnToSun();
            event.consume();
        }
    }

    private void cycleCameraLeft() {
        int bodyCount = solarSystem.getCelestialBodyCount();

        if (currentBodyIndex == 0) {
            // From Sun, go to last body (Neptune)
            currentBodyIndex = bodyCount - 1;
        } else {
            // Go to previous body
            currentBodyIndex--;
        }

        transitionToBody();
    }

    private void cycleCameraRight() {
        int bodyCount = solarSystem.getCelestialBodyCount();

        if (currentBodyIndex == bodyCount - 1) {
            // From last body, go back to Sun
            currentBodyIndex = 0;
        } else {
            // Go to next body
            currentBodyIndex++;
        }

        transitionToBody();
    }


    private void transitionToBody() {
        if (currentBodyIndex >= 0 && currentBodyIndex < solarSystem.getCelestialBodyCount()) {
            CelestialBody body = solarSystem.getCelestialBody(currentBodyIndex);

            // The transition will calculate optimal angles and pass them along
            AttachedState targetState;
            if (currentBodyIndex == 0) {
                // Sun gets bird's-eye view parameters
                targetState = new AttachedState(body, -135.0, 85.0, 600.0);
            } else {
                // For other bodies, let the transition calculate optimal approach
                // We pass a basic AttachedState and the transition will create
                // a new one with calculated angles
                targetState = new AttachedState(body);
            }

            // Create and set the transition state
            CameraTransitionState transitionState = new CameraTransitionState(targetState, body);
            orreryView.getCameraStateMachine().setState(transitionState);

            updateBodyLabel();
        }
    }

    private void returnToSun() {
        // Returns to Sun with default bird's eye view
        if (currentBodyIndex != 0) {
            currentBodyIndex = 0;
            transitionToBody();
        } else {
            // If already at Sun, reset to default view
            resetCamera();
        }
    }

    private void updateBodyLabel() {
        currentBodyLabel.setText("Following: " + BODY_NAMES[currentBodyIndex]);
    }

    private void setupUIControls() {
        // Set button actions
        toggleOrbitsButton.setOnAction(event -> toggleOrbits());
        zoomInButton.setOnAction(event -> zoomIn());
        zoomOutButton.setOnAction(event -> zoomOut());
        resetCameraButton.setOnAction(event -> resetCamera());

        // Zoom with mouse scroll - DELEGATE TO STATE MACHINE
        rootPane.setOnScroll(event -> {
            orreryView.getCameraStateMachine().handleScroll(event.getDeltaY());
        });

        // Mouse press handler
        rootPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();

                // Ensure focus for keyboard events
                rootPane.requestFocus();
            }
        });

        // Mouse drag handler - DELEGATE TO STATE MACHINE
        rootPane.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;

                // Delegate to state machine
                orreryView.getCameraStateMachine().handleMouseDrag(deltaX, deltaY);

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
        // Use state machine for zooming
        orreryView.getCameraStateMachine().handleScroll(1.0);
    }

    @FXML
    private void zoomOut() {
        // Use state machine for zooming
        orreryView.getCameraStateMachine().handleScroll(-1.0);
    }

    private void resetCamera() {
        // Force return to exact starting bird's eye view
        currentBodyIndex = 0;

        CelestialBody sun = solarSystem.getCelestialBody(0);

        // Create the bird's eye state with exact parameters
        AttachedState sunState = new AttachedState(sun, -135.0, 85.0, 600.0);

        // Always use transition with force exact position for consistent animation
        // The true parameter forces the transition to use exact angles instead of optimising
        CameraTransitionState transitionState = new CameraTransitionState(sunState, sun, true);
        orreryView.getCameraStateMachine().setState(transitionState);

        updateBodyLabel();

        // Ensure focus returns to the main pane for keyboard input
        rootPane.requestFocus();
    }

    public void shutdown() {
        solarSystem.stop();
        orreryView.stop();
    }
}