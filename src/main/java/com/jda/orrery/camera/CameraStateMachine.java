package com.jda.orrery.camera;

/**
 * Manages camera state transitions and updates.
 */
public class CameraStateMachine {
    private CameraState currentState;
    private CameraState previousState;
    private final CameraContext context;

    public CameraStateMachine(CameraContext context) {
        this.context = context;
    }

    public void setState(CameraState newState) {
        if (currentState != null) {
            currentState.exit();
            previousState = currentState;  // Store previous state
        }

        currentState = newState;
        currentState.enter(context, this);  // Pass 'this'

    }

    public void update(double deltaTime) {
        if (currentState != null) {
            currentState.update(deltaTime, this);
        }
    }

    // Delegate input to current state
    public void handleMouseDrag(double deltaX, double deltaY) {
        if (currentState != null) {
            currentState.handleMouseDrag(deltaX, deltaY);
        }
    }

    public void handleScroll(double deltaZoom) {
        if (currentState != null) {
            currentState.handleScroll(deltaZoom);
        }
    }

    public CameraState getPreviousState() {
        return previousState;
    }
}