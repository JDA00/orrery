package com.jda.orrery.camera.states;

import com.jda.orrery.camera.CameraContext;
import com.jda.orrery.camera.CameraInterpolator;
import com.jda.orrery.camera.CameraState;
import com.jda.orrery.camera.CameraStateMachine;
import com.jda.orrery.model.CelestialBody;
import javafx.geometry.Point3D;
import javafx.scene.input.KeyCode;
import javafx.scene.transform.Rotate;

import com.jda.orrery.camera.CameraInterpolator.EasingType;

import static com.jda.orrery.camera.CameraInterpolator.lerp;
import static com.jda.orrery.camera.CameraInterpolator.lerpAngle;

/**
 * Transition state that smoothly moves the camera from one position to another.
 * Enhanced with near-side positioning and distance-based duration.
 */

public class CameraTransitionState implements CameraState {
    private CameraContext context;
    private final CameraState targetState;
    private final CelestialBody targetBody;
    private CelestialBody sourceBody;

    // Force exact position flag for reset functionality
    private final boolean forceExactPosition;

    // Animation parameters
    private double transitionTime = 0;
    private double transitionDuration; // Now calculated dynamically

    // Duration calculation constants
    private static final double BASE_DURATION = 1.0;
    private static final double CLOSE_DURATION = 1.15;
    private static final double MEDIUM_BASE = 1.2;
    private static final double MEDIUM_MULTIPLIER = 0.006;

    private static final double STANDARD_BASE = 1.5;
    private static final double STANDARD_MULTIPLIER = 0.002;

    private static final double DRAMATIC_BASE = 1.8;
    private static final double DRAMATIC_MULTIPLIER = 0.003;

    private static final double LONG_STANDARD_BASE = 2.0;
    private static final double LONG_STANDARD_MULTIPLIER = 0.0015;

    private static final double LONG_DRAMATIC_BASE = 2.5;
    private static final double LONG_DRAMATIC_MULTIPLIER = 0.002;

    private static final double MAX_DURATION = 4.0;

    // Distance thresholds
    private static final double VERY_CLOSE = 20;
    private static final double CLOSE = 50;
    private static final double MEDIUM = 100;
    private static final double FAR = 300;

    // Starting position and orientation
    private Point3D startPosition;
    private double startYaw, startPitch;

    // Target position and orientation (calculated)
    private Point3D targetPosition;
    private double targetYaw, targetPitch;

    // For calculating target view
    private double targetOrbitDistance;
    private double targetOrbitAngle = 45;
    private double targetOrbitElevation = 30;

    // Flag to ensure we set exact values once
    private boolean finalPositionSet = false;

    /**
     * Standard constructor for optimised transitions
     */
    public CameraTransitionState(CameraState targetState, CelestialBody targetBody) {
        this(targetState, targetBody, false);
    }

    /**
     * Constructor with force exact position option
     */
    public CameraTransitionState(CameraState targetState, CelestialBody targetBody, boolean forceExactPosition) {
        this.targetState = targetState;
        this.targetBody = targetBody;
        this.forceExactPosition = forceExactPosition;
    }

    @Override
    public void enter(CameraContext context, CameraStateMachine stateMachine) {
        this.context = context;

        // Query the previous state for source body
        CameraState previousState = stateMachine.getPreviousState();
        if (previousState != null) {
            previousState.getTargetBody().ifPresent(body -> this.sourceBody = body);
        }

        // Capture current camera position
        startPosition = new Point3D(
                context.camera.getTranslateX(),
                context.camera.getTranslateY(),
                context.camera.getTranslateZ()
        );

        // Calculate current camera orientation
        calculateCurrentOrientation();

        // Calculate approach angle for target body
        if (targetBody != null) {
            if (forceExactPosition && targetState instanceof AttachedState) {
                // For forced exact position, use the exact angles from the target state
                AttachedState attachedTarget = (AttachedState) targetState;
                targetOrbitAngle = attachedTarget.getInitialOrbitAngle();
                targetOrbitElevation = attachedTarget.getInitialOrbitElevation();
                targetOrbitDistance = attachedTarget.getInitialOrbitDistance();
                System.out.printf("Forcing exact position - Angle: %.1f°, Elevation: %.1f°, Distance: %.1f%n",
                        targetOrbitAngle, targetOrbitElevation, targetOrbitDistance);
            } else {
                // Normal optimised transition
                calculateOptimalApproachAngle();
            }
            calculateTargetPosition();
        } else {
            // Transitioning to free view
            targetPosition = new Point3D(0, 0, FreeViewState.getDefaultDistance());
            targetYaw = 0;
            targetPitch = -30;
        }

        // Calculate distance-based duration
        calculateTransitionDuration();

        // Clear world transforms to avoid conflicts
        context.world.getTransforms().clear();
        context.camera.getTransforms().clear();

        // Debug output
        System.out.println("=== TRANSITION START ===");
        System.out.println("From: " + startPosition);
        System.out.println("To: " + targetPosition);
        System.out.printf("Start angles - Yaw: %.1f, Pitch: %.1f%n", startYaw, startPitch);
        System.out.printf("Target angles - Yaw: %.1f, Pitch: %.1f%n", targetYaw, targetPitch);
    }

    /**
     * Calculate optimal approach angle to minimize camera movement
     * This positions the camera on the "near side" of the target body
     */
    private void calculateOptimalApproachAngle() {
        // Get current camera position
        double camX = context.camera.getTranslateX();
        double camY = context.camera.getTranslateY();
        double camZ = context.camera.getTranslateZ();

        // Get target body position
        double bodyX = targetBody.getBodyGroup().getTranslateX();
        double bodyY = targetBody.getBodyGroup().getTranslateY();
        double bodyZ = targetBody.getBodyGroup().getTranslateZ();

        // Calculate angle from target body to current camera position
        // This gives us the "near side" angle
        double dx = camX - bodyX;
        double dz = camZ - bodyZ;

        // Calculate the angle in the horizontal plane
        double angleRad = Math.atan2(dx, dz);
        targetOrbitAngle = Math.toDegrees(angleRad);

        // For elevation, blend between current and default based on distance
        // If we're far away (like from Sun), gradually move to standard elevation
        double currentDistance = Math.sqrt(dx * dx + (camY - bodyY) * (camY - bodyY) + dz * dz);
        double bodyRadius = targetBody.getRadius();
        double optimalDistance = Math.max(bodyRadius * 10, 50);

        // If coming from far away, use a compromise elevation
        if (currentDistance > optimalDistance * 5) {
            targetOrbitElevation = 45; // Higher elevation for better approach
        } else {
            targetOrbitElevation = 30; // Standard elevation
        }

        System.out.printf("Optimal approach - Angle: %.1f°, Elevation: %.1f°%n",
                targetOrbitAngle, targetOrbitElevation);
    }

    /**
     * Calculate transition duration based on distance
     * Now with better handling for different transition types
     */
    private void calculateTransitionDuration() {
        double distance = startPosition.distance(targetPosition);

        // Check if we're transitioning from a bird's-eye view (high elevation)
        boolean fromBirdsEye = Math.abs(startPitch) > 60; // Pitch > 60° means looking mostly down
        boolean toBirdsEye = Math.abs(targetPitch) > 60;

        // Different duration calculations based on distance ranges and transition type
        if (distance < VERY_CLOSE) {
            // Very close transitions - quick but comfortable
            transitionDuration = BASE_DURATION;
        } else if (distance < CLOSE) {
            // Close transitions - slightly slower
            transitionDuration = CLOSE_DURATION;
        } else if (distance < MEDIUM) {
            // Medium-close transitions - scale gently
            transitionDuration = MEDIUM_BASE + (distance - CLOSE) * MEDIUM_MULTIPLIER;
        } else if (distance < FAR) {
            // Medium distances
            if (fromBirdsEye || toBirdsEye) {
                // Dramatic view changes need more time
                transitionDuration = DRAMATIC_BASE + (distance * DRAMATIC_MULTIPLIER);
            } else {
                // Standard transitions
                transitionDuration = STANDARD_BASE + (distance * STANDARD_MULTIPLIER);
            }
        } else {
            // Long distances - especially from/to bird's eye view
            if (fromBirdsEye || toBirdsEye) {
                // Extra time for dramatic perspective changes
                transitionDuration = LONG_DRAMATIC_BASE + (distance * LONG_DRAMATIC_MULTIPLIER);
            } else {
                transitionDuration = LONG_STANDARD_BASE + (distance * LONG_STANDARD_MULTIPLIER);
            }
            // Cap at maximum
            transitionDuration = Math.min(transitionDuration, MAX_DURATION);
        }

        // Debug output
        System.out.printf("Distance: %.1f units, Duration: %.2f seconds%s%n",
                distance, transitionDuration,
                (fromBirdsEye || toBirdsEye) ? " (dramatic view change)" : "");
    }

    private void calculateCurrentOrientation() {
        // First try to get actual camera transforms
        if (context.camera.getTransforms().size() >= 2) {
            try {
                Rotate yawRotate = (Rotate) context.camera.getTransforms().get(0);
                Rotate pitchRotate = (Rotate) context.camera.getTransforms().get(1);
                startYaw = yawRotate.getAngle();
                startPitch = pitchRotate.getAngle();

                System.out.printf("Using camera transform angles - Yaw: %.1f, Pitch: %.1f%n",
                        startYaw, startPitch);
                return;
            } catch (Exception e) {
                // Fall through to calculation method
            }
        }

        // Calculate based on camera position and what it's looking at
        double camX = context.camera.getTranslateX();
        double camY = context.camera.getTranslateY();
        double camZ = context.camera.getTranslateZ();

        CelestialBody currentBody = sourceBody;
        double targetX, targetY, targetZ;

        if (currentBody != null) {
            targetX = currentBody.getBodyGroup().getTranslateX();
            targetY = currentBody.getBodyGroup().getTranslateY();
            targetZ = currentBody.getBodyGroup().getTranslateZ();
            System.out.printf("Looking at body: %s at [%.1f, %.1f, %.1f]%n",
                    currentBody.getName(), targetX, targetY, targetZ);
        } else {
            targetX = 0;
            targetY = 0;
            targetZ = 0;
            System.out.println("No target body, assuming looking at origin");
        }

        // Calculate direction from camera to target
        double dx = targetX - camX;
        double dy = targetY - camY;
        double dz = targetZ - camZ;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > 0.001) {
            startYaw = Math.toDegrees(Math.atan2(dx, dz));
            startPitch = Math.toDegrees(Math.asin(-dy / distance));

            System.out.printf("Camera at [%.1f, %.1f, %.1f], looking at [%.1f, %.1f, %.1f]%n",
                    camX, camY, camZ, targetX, targetY, targetZ);
            System.out.printf("Direction vector: [%.2f, %.2f, %.2f], distance: %.1f%n",
                    dx, dy, dz, distance);
            System.out.printf("Calculated start angles - Yaw: %.1f, Pitch: %.1f%n",
                    startYaw, startPitch);
        } else {
            System.out.println("Camera at target position, using defaults");
            startYaw = 0;
            startPitch = 0;
        }
    }

    private void calculateTargetPosition() {
        // Get body's CURRENT position
        double bodyX = targetBody.getBodyGroup().getTranslateX();
        double bodyY = targetBody.getBodyGroup().getTranslateY();
        double bodyZ = targetBody.getBodyGroup().getTranslateZ();

        // Use specified distance or calculate appropriate viewing distance
        if (targetOrbitDistance == 0) {
            double bodyRadius = targetBody.getRadius();
            targetOrbitDistance = Math.max(bodyRadius * 10, 50);
        }

        // Calculate camera position on sphere around body
        double radAngle = Math.toRadians(targetOrbitAngle);
        double radElev = Math.toRadians(targetOrbitElevation);

        double offsetX = targetOrbitDistance * Math.cos(radElev) * Math.sin(radAngle);
        double offsetY = -targetOrbitDistance * Math.sin(radElev);
        double offsetZ = targetOrbitDistance * Math.cos(radElev) * Math.cos(radAngle);

        targetPosition = new Point3D(
                bodyX + offsetX,
                bodyY + offsetY,
                bodyZ + offsetZ
        );

        // Calculate target orientation to look at body
        double dx = bodyX - targetPosition.getX();
        double dy = bodyY - targetPosition.getY();
        double dz = bodyZ - targetPosition.getZ();

        targetYaw = Math.toDegrees(Math.atan2(dx, dz));
        targetPitch = Math.toDegrees(Math.asin(-dy / targetOrbitDistance));
    }

    @Override
    public void update(double deltaTime, CameraStateMachine stateMachine) {
        transitionTime += deltaTime;

        // Calculate progress (0 to 1)
        double progress = Math.min(transitionTime / transitionDuration, 1.0);

        // Use CameraInterpolator for easing
        double positionProgress = CameraInterpolator.ease(progress, EasingType.EASE_IN_OUT_CUBIC);

        // Choose rotation easing based on the type of movement
        EasingType rotationType;
        if (Math.abs(targetYaw - startYaw) > 90) {
            // Large rotation changes - use gentler easing
            rotationType = EasingType.EASE_IN_OUT_QUAD;
        } else {
            // Smaller rotations - use snappier easing
            rotationType = EasingType.EASE_OUT_QUAD;
        }
        double rotationProgress = CameraInterpolator.ease(progress, rotationType);

        // If we have a moving target body, update target position each frame
        if (targetBody != null) {
            calculateTargetPosition();
        }

        // Interpolate position with cubic easing
        double x = lerp(startPosition.getX(), targetPosition.getX(), positionProgress);
        double y = lerp(startPosition.getY(), targetPosition.getY(), positionProgress);
        double z = lerp(startPosition.getZ(), targetPosition.getZ(), positionProgress);

        // Interpolate orientation with gentler/snappier easing
        double yaw = lerpAngle(startYaw, targetYaw, rotationProgress);
        double pitch = lerp(startPitch, targetPitch, rotationProgress);

        // When we're complete, set EXACT target values
        if (progress >= 1.0 && !finalPositionSet) {
            x = targetPosition.getX();
            y = targetPosition.getY();
            z = targetPosition.getZ();
            yaw = targetYaw;
            pitch = targetPitch;
            finalPositionSet = true;

            System.out.println("=== TRANSITION COMPLETE ===");
            System.out.printf("Final position: [%.2f, %.2f, %.2f]%n", x, y, z);
            System.out.printf("Final angles - Yaw: %.1f, Pitch: %.1f%n", yaw, pitch);
        }

        context.camera.setTranslateX(x);
        context.camera.setTranslateY(y);
        context.camera.setTranslateZ(z);

        // Clear and apply rotations
        context.camera.getTransforms().clear();
        context.camera.getTransforms().addAll(
                new Rotate(yaw, Rotate.Y_AXIS),
                new Rotate(pitch, Rotate.X_AXIS)
        );

        // Debug output during transition - now shows both progress values
        if ((progress > 0 && progress < 0.05) || (progress > 0.45 && progress < 0.55) || (progress > 0.95)) {
            System.out.printf("Transition: %.1f%% - Pos: [%.1f, %.1f, %.1f], Yaw: %.1f, Pitch: %.1f (PosEase: %.2f, RotEase: %.2f)%n",
                    progress * 100, x, y, z, yaw, pitch, positionProgress, rotationProgress);
        }

        // Check if transition is complete
        if (finalPositionSet && progress >= 1.0) {
            // Pass the calculated angles to the target state if it's an AttachedState
            if (targetState instanceof AttachedState && targetBody != null) {
                // If we forced exact position, use the target state as-is
                if (forceExactPosition) {
                    stateMachine.setState(targetState);
                } else {
                    // Normal transition - create new state with calculated angles
                    AttachedState attachedState = new AttachedState(targetBody,
                            targetOrbitAngle,
                            targetOrbitElevation);
                    stateMachine.setState(attachedState);
                }
            } else {
                stateMachine.setState(targetState);
            }
        }
    }

    @Override
    public void exit() {
        System.out.println("Exiting CameraTransitionState");
    }

    @Override
    public void handleMouseDrag(double deltaX, double deltaY) {
        // Optionally allow camera control during transition
        // Or ignore input during transition
    }

    @Override
    public void handleScroll(double deltaZoom) {
        // Optionally allow zoom during transition
        // Or ignore input during transition
    }

    @Override
    public void handleKeyPress(KeyCode key) {
        // Allow interrupting transition with ESC
        if (key == KeyCode.ESCAPE) {
            // Force completion
            transitionTime = transitionDuration;
        }
    }

    @Override
    public String getName() {
        return "Transitioning...";
    }

}