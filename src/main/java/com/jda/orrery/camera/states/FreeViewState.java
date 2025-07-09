package com.jda.orrery.camera.states;

import com.jda.orrery.camera.CameraContext;
import com.jda.orrery.camera.CameraInterpolator;
import com.jda.orrery.camera.CameraState;
import com.jda.orrery.camera.CameraStateMachine;
import javafx.scene.input.KeyCode;
import javafx.scene.transform.Rotate;

/**
 * Free-flying camera controlled by mouse.
 * Maintains the bird's eye view behavior from original implementation.
 */
public class FreeViewState implements CameraState {
    private static final double ROTATION_SENSITIVITY = 0.2;  // Reduced from 0.3
    private static final double ZOOM_FACTOR = 1.1;           // Reduced from 1.25
    private static final double MIN_DISTANCE = -30;
    private static final double MAX_DISTANCE = -1500;
    private static final double DEFAULT_DISTANCE = -600;

    private CameraContext context;

    // Current rotation state
    private double azimuthAngle = 0.0;
    private double polarAngle = -90.0;  // Start looking down

    // Target rotation state (for smoothing)
    private double targetAzimuthAngle = 0.0;
    private double targetPolarAngle = -90.0;

    // Current and target zoom
    private double currentZoom;
    private double targetZoom;

    // Rotation transforms (moved from OrreryView)
    private final Rotate azimuthRotate = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate polarRotate = new Rotate(-90, Rotate.X_AXIS);

    @Override
    public void enter(CameraContext context, CameraStateMachine stateMachine) {
        this.context = context;

        // Clear any camera transforms from attached state
        context.camera.getTransforms().clear();

        // Clear world transforms first
        context.world.getTransforms().clear();

        // Apply rotations to world (maintaining original behavior)
        context.world.getTransforms().addAll(polarRotate, azimuthRotate);

        // Reset camera to default position
        context.camera.setTranslateX(0);
        context.camera.setTranslateY(0);
        context.camera.setTranslateZ(DEFAULT_DISTANCE);

        // Initialize zoom values
        currentZoom = DEFAULT_DISTANCE;
        targetZoom = DEFAULT_DISTANCE;

        System.out.println("Entered FreeViewState - Camera at Z: " + DEFAULT_DISTANCE);
    }

    @Override
    public void update(double deltaTime, CameraStateMachine stateMachine) {
        // Use CameraInterpolator for consistent smoothing
        azimuthAngle = CameraInterpolator.smoothAngle(
                azimuthAngle, targetAzimuthAngle,
                CameraInterpolator.SMOOTH_ROTATION, deltaTime
        );

        polarAngle = CameraInterpolator.smooth(
                polarAngle, targetPolarAngle,
                CameraInterpolator.SMOOTH_ROTATION, deltaTime
        );

        currentZoom = CameraInterpolator.smooth(
                currentZoom, targetZoom,
                CameraInterpolator.SMOOTH_ZOOM, deltaTime
        );

        // Apply the smoothed values
        azimuthRotate.setAngle(azimuthAngle);
        polarRotate.setAngle(polarAngle);
        context.camera.setTranslateZ(currentZoom);
    }

    @Override
    public void exit() {
        // Clear world transforms when leaving free view
        context.world.getTransforms().clear();
        System.out.println("Exited FreeViewState");
    }

    @Override
    public void handleMouseDrag(double deltaX, double deltaY) {
        // Update target azimuth (turntable rotation around Y)
        targetAzimuthAngle -= deltaX * ROTATION_SENSITIVITY;

        // Update target polar angle (tilt) with clamping
        targetPolarAngle += deltaY * ROTATION_SENSITIVITY;
        targetPolarAngle = Math.max(-175, Math.min(-5, targetPolarAngle));
    }

    @Override
    public void handleScroll(double deltaZoom) {
        double newZ;

        if (deltaZoom > 0) {
            newZ = targetZoom / ZOOM_FACTOR;
        } else {
            newZ = targetZoom * ZOOM_FACTOR;
        }

        // Keep zoom within limits
        if (newZ > MAX_DISTANCE && newZ < MIN_DISTANCE) {
            targetZoom = newZ;
        }
    }

    @Override
    public void handleKeyPress(KeyCode key) {
        // Free view doesn't handle keys - let OrreryController handle body selection
    }

    @Override
    public String getName() {
        return "Free Camera";
    }

    public static double getDefaultDistance() {
        return DEFAULT_DISTANCE;
    }
}