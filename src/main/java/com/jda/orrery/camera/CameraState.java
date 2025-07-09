package com.jda.orrery.camera;

import com.jda.orrery.model.CelestialBody;
import javafx.scene.input.KeyCode;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Interface for camera behavior states.
 * Each state handles its own input and update logic.
 */
public interface CameraState {
    /**
     * Called when entering this state
     */
    void enter(CameraContext context, CameraStateMachine stateMachine);

    /**
     * Update the camera (called each frame)
     */
    void update(double deltaTime, CameraStateMachine stateMachine);

    /**
     * Called when exiting this state
     */
    void exit();

    // Input handlers
    void handleMouseDrag(double deltaX, double deltaY);

    void handleScroll(double deltaZoom);

    void handleKeyPress(KeyCode key);

    /**
     * Get name for this state
     */
    String getName();

    /**
     * Query methods for state transfer - states override what they need to share
     */
    default Optional<CelestialBody> getTargetBody() {
        return Optional.empty();
    }

    default OptionalDouble getOrbitAngle() {
        return OptionalDouble.empty();
    }

    default OptionalDouble getOrbitElevation() {
        return OptionalDouble.empty();
    }
}