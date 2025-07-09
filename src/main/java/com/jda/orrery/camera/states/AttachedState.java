package com.jda.orrery.camera.states;

import com.jda.orrery.camera.CameraContext;
import com.jda.orrery.camera.CameraInterpolator;
import com.jda.orrery.camera.CameraState;
import com.jda.orrery.camera.CameraStateMachine;
import com.jda.orrery.model.CelestialBody;
import javafx.scene.input.KeyCode;
import javafx.scene.transform.Rotate;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Camera state for following a celestial body.
 * Camera remains in world space and tracks the body's position.
 */
public class AttachedState implements CameraState {
    private CameraContext context;
    private final CelestialBody targetBody;

    // Camera positioning
    private double orbitDistance;
    private double orbitAngle = 45;      // Horizontal angle around body
    private double orbitElevation = 30;  // Vertical angle above body

    // Smooth camera movement
    private double targetOrbitAngle = 45;
    private double targetOrbitElevation = 30;
    private double targetOrbitDistance;

    // Zoom limits based on body size
    private double minDistance;
    private double maxDistance;

    // Optional initial camera parameters
    private final Double initialOrbitAngle;
    private final Double initialOrbitElevation;
    private final Double initialOrbitDistance;

    /**
     * Create attached state with default camera position
     */
    public AttachedState(CelestialBody targetBody) {
        this.targetBody = targetBody;
        this.initialOrbitAngle = null;
        this.initialOrbitElevation = null;
        this.initialOrbitDistance = null;
    }

    /**
     * Create attached state with calculated optimal initial camera position
     * Used by transitions to position camera on the "near side" of the target body
     */
    public AttachedState(CelestialBody targetBody, double angle, double elevation) {
        this.targetBody = targetBody;
        this.initialOrbitAngle = angle;
        this.initialOrbitElevation = elevation;
        this.initialOrbitDistance = null; // Will use default calculation
    }

    /**
     * Create attached state with specific initial camera position
     */
    public AttachedState(CelestialBody targetBody, double angle, double elevation, double distance) {
        this.targetBody = targetBody;
        this.initialOrbitAngle = angle;
        this.initialOrbitElevation = elevation;
        this.initialOrbitDistance = distance;
    }

    @Override
    public void enter(CameraContext context, CameraStateMachine stateMachine) {

        this.context = context;

        // Calculate appropriate distances based on body size
        double bodyRadius = targetBody.getRadius();

        // Special handling for the Sun - allow much further zoom out
        if (targetBody.getName().equals("Sun") || targetBody.getName().equals("sun")) {
            this.minDistance = Math.max(bodyRadius * 3, 20);
            this.maxDistance = 800;  // Much further for system overview
        } else {
            this.minDistance = Math.max(bodyRadius * 3, 20);
            this.maxDistance = Math.max(bodyRadius * 100, 1000);
        }

        // Set initial values
        if (initialOrbitAngle != null) {
            this.orbitAngle = initialOrbitAngle;
            this.targetOrbitAngle = initialOrbitAngle;
        } else {
            this.orbitAngle = 45;
            this.targetOrbitAngle = 45;
        }

        if (initialOrbitElevation != null) {
            this.orbitElevation = initialOrbitElevation;
            this.targetOrbitElevation = initialOrbitElevation;
        } else {
            this.orbitElevation = 30;
            this.targetOrbitElevation = 30;
        }

        if (initialOrbitDistance != null) {
            this.orbitDistance = initialOrbitDistance;
            this.targetOrbitDistance = initialOrbitDistance;
        } else {
            this.orbitDistance = Math.max(bodyRadius * 10, 50);
            this.targetOrbitDistance = this.orbitDistance;
        }

        // CRITICAL: Clear ALL transforms from both world and camera
        context.world.getTransforms().clear();
        context.camera.getTransforms().clear();

        // Reset camera position to origin first
        context.camera.setTranslateX(0);
        context.camera.setTranslateY(0);
        context.camera.setTranslateZ(0);

        // Log for debugging
        System.out.println("Attached to " + targetBody.getName() +
                " at radius " + bodyRadius +
                ", initial distance: " + orbitDistance +
                ", angle: " + orbitAngle +
                ", elevation: " + orbitElevation);

        // Position camera to look at body from initial angle
        updateCameraPosition();
    }

    @Override
    public void update(double deltaTime, CameraStateMachine stateMachine) {
        // Use CameraInterpolator for consistent smoothing
        orbitAngle = CameraInterpolator.smoothAngle(
                orbitAngle, targetOrbitAngle,
                CameraInterpolator.SMOOTH_ROTATION, deltaTime
        );

        orbitElevation = CameraInterpolator.smooth(
                orbitElevation, targetOrbitElevation,
                CameraInterpolator.SMOOTH_ROTATION, deltaTime
        );

        orbitDistance = CameraInterpolator.smooth(
                orbitDistance, targetOrbitDistance,
                CameraInterpolator.SMOOTH_ZOOM, deltaTime
        );

        // Update camera position to follow the body
        updateCameraPosition();
    }

    @Override
    public void exit() {
        // Clear camera transforms when leaving
        context.camera.getTransforms().clear();
    }

    @Override
    public void handleMouseDrag(double deltaX, double deltaY) {
        // Reduced sensitivity (was 0.5)
        double rotationSensitivity = 0.2;

        // Mouse drag orbits around the body - update target angles
        targetOrbitAngle -= deltaX * rotationSensitivity;
        targetOrbitElevation -= deltaY * rotationSensitivity;  // Negative for inverted camera control

        // Clamp elevation to prevent flipping
        targetOrbitElevation = Math.max(-85, Math.min(85, targetOrbitElevation));
    }

    @Override
    public void handleScroll(double deltaZoom) {
        // Zoom toward/away from body - update target distance
        double zoomFactor = deltaZoom > 0 ? 0.9 : 1.1;
        targetOrbitDistance = Math.max(minDistance, Math.min(maxDistance, targetOrbitDistance * zoomFactor));
    }

    @Override
    public void handleKeyPress(KeyCode key) {
        // Let controller handle body switching
    }

    @Override
    public String getName() {
        return "Following: " + targetBody.getName();
    }

    private void updateCameraPosition() {
        // Get body's position directly from its transform
        // This is more reliable than localToScene
        double bodyX = targetBody.getBodyGroup().getTranslateX();
        double bodyY = targetBody.getBodyGroup().getTranslateY();
        double bodyZ = targetBody.getBodyGroup().getTranslateZ();

        // Debug output - less verbose
        if (Math.random() < 0.01) { // Only log occasionally
            System.out.printf("%s position: [%.2f, %.2f, %.2f]%n",
                    targetBody.getName(), bodyX, bodyY, bodyZ);
        }

        // Calculate camera position on sphere around body
        double radAngle = Math.toRadians(orbitAngle);
        double radElev = Math.toRadians(orbitElevation);

        // Calculate offset from body
        double offsetX = orbitDistance * Math.cos(radElev) * Math.sin(radAngle);
        double offsetY = -orbitDistance * Math.sin(radElev);
        double offsetZ = orbitDistance * Math.cos(radElev) * Math.cos(radAngle);

        // Set camera position
        double camX = bodyX + offsetX;
        double camY = bodyY + offsetY;
        double camZ = bodyZ + offsetZ;

        context.camera.setTranslateX(camX);
        context.camera.setTranslateY(camY);
        context.camera.setTranslateZ(camZ);

        // Clear any existing rotations first
        context.camera.getTransforms().clear();

        // Calculate look-at rotation
        lookAt(camX, camY, camZ, bodyX, bodyY, bodyZ);
    }


    public double getInitialOrbitAngle() {
        return initialOrbitAngle != null ? initialOrbitAngle : 45.0;
    }

    public double getInitialOrbitElevation() {
        return initialOrbitElevation != null ? initialOrbitElevation : 30.0;
    }

    public double getInitialOrbitDistance() {
        if (initialOrbitDistance != null) {
            return initialOrbitDistance;
        } else {
            // Calculate default based on body radius
            double bodyRadius = targetBody.getRadius();
            return Math.max(bodyRadius * 10, 50);
        }
    }

    @Override
    public Optional<CelestialBody> getTargetBody() {
        return Optional.of(targetBody);
    }

    @Override
    public OptionalDouble getOrbitAngle() {
        return OptionalDouble.of(orbitAngle);  // Use current smoothed angle
    }

    @Override
    public OptionalDouble getOrbitElevation() {
        return OptionalDouble.of(orbitElevation);  // Use current smoothed elevation
    }

    private void lookAt(double fromX, double fromY, double fromZ,
                        double toX, double toY, double toZ) {
        // Calculate direction vector from camera to target
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;

        // Calculate distance
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (distance < 0.001) return; // Too close, avoid divide by zero

        // Normalise direction
        dx /= distance;
        dy /= distance;
        dz /= distance;

        // Calculate yaw (rotation around Y axis)
        // atan2 takes (opposite, adjacent) - for looking from camera to target
        double yaw = Math.toDegrees(Math.atan2(dx, dz));

        // Calculate pitch (rotation around X axis)
        // Negative because JavaFX Y goes down but we want to look up when dy is positive
        double pitch = Math.toDegrees(Math.asin(-dy));

        // Apply rotations in correct order
        Rotate yawRotate = new Rotate(yaw, Rotate.Y_AXIS);
        Rotate pitchRotate = new Rotate(pitch, Rotate.X_AXIS);

        // Apply to camera
        context.camera.getTransforms().addAll(yawRotate, pitchRotate);

        // Debug output - less verbose
        if (Math.random() < 0.01) { // Only log occasionally
            System.out.printf("Camera look-at: yaw=%.1f, pitch=%.1f, distance=%.1f%n",
                    yaw, pitch, distance);
        }
    }
}