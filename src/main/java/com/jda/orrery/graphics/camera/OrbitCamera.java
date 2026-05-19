package com.jda.orrery.graphics.camera;

import com.jda.orrery.core.frames.FrameManager;
import com.jda.orrery.core.frames.FrameNames;
import com.jda.orrery.core.frames.FramedState;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.graphics.api.View;
import com.jda.orrery.graphics.celestial.ScaleManager;
import com.jda.orrery.graphics.core.DrawContext;
import java.util.logging.Logger;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Arcball orbit camera. Uses dirty flags to avoid redundant matrix work, reuses pre-allocated
 * JOML scratch, and receives delta time from the render loop so it makes no system calls.
 */
public class OrbitCamera implements View {
    private static final Logger LOGGER = Logging.logger(OrbitCamera.class);

    private final FrameManager frameManager;

    // Camera parameters
    private float heading = 0.0f; // Yaw in degrees
    private float pitch = 45.0f; // Pitch in degrees
    private float distance = 500.0f; // Distance from target
    private float logDistance = 2.699f; // log10(500) - stored in log space

    // Target values for smooth interpolation
    private float targetHeading = 0.0f;
    private float targetPitch = 45.0f;
    private float targetLogDistance = 2.699f;
    private static final float SENSITIVITY = 1.0f;
    private static final float ZOOM_SPEED = 1.0f; // Reduced for finer control

    // Momentum system - now enabled with improved physics
    private static final boolean MOMENTUM_ENABLED = true; // Re-enabled with better implementation

    // Frame-rate independent smoothing
    // 0 = instant response, 1 = never moves
    private static final float ROTATION_SMOOTHING = 0.9f; // More momentum for panning
    private static final float ZOOM_SMOOTHING = 0.85f; // Smooth zoom

    // Pre-computed natural logs for fast exponential approximation (2-3x faster than Math.pow)
    private static final float LN_ROTATION_SMOOTHING =
            (float) Math.log(ROTATION_SMOOTHING); // -0.1053...
    private static final float LN_ZOOM_SMOOTHING = (float) Math.log(ZOOM_SMOOTHING); // -0.1625...

    // Velocity-based momentum with half-life decay
    private static final float VELOCITY_HALF_LIFE = 0.55f; // Longer decay for more coast
    private static final float LN_2 = 0.693147f; // Natural log of 2 (pre-computed)

    // Mathematical constants
    private static final double LN_10 = 2.302585092994046; // Natural log of 10 for base conversion
    private static final float EPSILON = 1e-6f; // Small value for floating point comparisons
    private static final double POSITION_THRESHOLD =
            0.0001; // Threshold for position change detection

    // Momentum for natural coast
    private float headingVelocity = 0.0f;
    private float pitchVelocity = 0.0f;
    private float timeSinceInput = 999.0f; // Time in seconds since last input

    // JOML matrices and vectors
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Vector3f position = new Vector3f();
    private final Vector3f target = new Vector3f(0, 0, 0);
    private final Vector3f up = new Vector3f(0, 1, 0);

    // Dirty flags - only recalculate when needed
    private boolean viewDirty = true;
    private boolean projectionDirty = true;
    private boolean distanceDirty = false;

    // Viewport
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    // Projection parameters
    private float fovDegrees = 60.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 100000.0f;

    // Constraints
    private static final float MIN_DISTANCE = 1.0f;
    private static final float MAX_DISTANCE = 10000.0f;
    private static final float MIN_PITCH = -89.0f; // Avoid gimbal lock
    private static final float MAX_PITCH = 89.0f;

    // Target tracking for camera
    // Camera should NOT query ephemeris - it just tracks a position
    private final Vector3f targetPosition = new Vector3f(); // Target position to look at
    private boolean hasTarget = false; // Whether we have a valid target

    // Cached world position (for camera-relative rendering)
    // JOML Vector3d is mutable; reused across calls instead of allocating per use.
    private final Vector3d cameraWorldPosition = new Vector3d(0, 0, 0);

    // Diagnostic frame counter
    private int frameCount = 0;

    // Simulation state for momentum control
    private boolean simulationPaused = false;
    private double simulationSpeed = 1.0;

    // Track last time value to detect when time actually changes
    private double lastTimeJD = Double.NaN;
    // Track last body to detect body switches (needed when paused)
    private CelestialBody lastTrackedBody = null;

    // Minimum distance enforcement (set during body switch)
    private float enforceMinDistance = 0.0f;

    // Track last body's visual radius for proportional zoom
    // Initialize to Sun's visual radius since Sun is always first body
    // Sun uses ScaleManager formula: log10(radius/earthRadius) * 3.0
    // Sun: log10(695700/6371) * 3.0 = log10(109.2) * 3.0 = 6.11
    private float lastBodyVisualRadius = 6.11f;

    // Pre-allocated; reused per frame
    private final Vector3d workPosition = new Vector3d();
    private final Vector3d workVelocity = new Vector3d();
    private final Vector3d workScaledPosition = new Vector3d(); // For scaled output

    // Cached Vec3d returned by getCameraWorldPosition(); updated when the camera moves.
    // Only recreate when position ACTUALLY changes (not every frame)
    private Vec3d cachedCameraWorldPosition = new Vec3d(0, 0, 0); // Pre-allocated
    private double lastCachedX = 0.0;
    private double lastCachedY = 0.0;
    private double lastCachedZ = 0.0;

    public OrbitCamera(FrameManager frameManager) {
        this.frameManager = frameManager;
    }

    @Override
    public void apply(DrawContext dc) {
        // Pull tracked body position BEFORE view matrix calculation —
        // eliminates the 1-frame lag that causes judder.
        CelestialBody trackedBody = dc.getTrackedBody();
        if (trackedBody != null) {
            updateTrackingPosition(trackedBody, dc.getTimeContext());
        }

        // Get delta time from draw context (no system calls!)
        float deltaTime = (float) dc.getDeltaTime();

        // Update camera state (now with correct position)
        update(deltaTime);

        // Apply to context
        dc.setView(this);
    }

    /**
     * Update camera state with proper dirty flag management. Only recalculates what has changed.
     */
    public void update(float deltaTime) {
        // Increment frame counter properly
        frameCount++;

        // Update time since input
        timeSinceInput += deltaTime;

        // Frame-rate independent smoothing via exponential decay.
        if (MOMENTUM_ENABLED) {
            // Calculate frame-independent interpolation factors
            // OPTIMIZATION: Use exp(ln(base) * exponent) instead of pow(base, exponent)
            // This is 2-3x faster than Math.pow() with identical results
            float rotationLerp = 1.0f - (float) Math.exp(LN_ROTATION_SMOOTHING * deltaTime * 60.0f);
            float zoomLerp = 1.0f - (float) Math.exp(LN_ZOOM_SMOOTHING * deltaTime * 60.0f);

            // Smoothly interpolate camera angles towards targets
            float headingDiff = targetHeading - heading;
            float pitchDiff = targetPitch - pitch;

            // Handle angle wrap-around for heading
            if (headingDiff > 180) headingDiff -= 360;
            if (headingDiff < -180) headingDiff += 360;

            // Apply smooth interpolation
            heading += headingDiff * rotationLerp;
            pitch += pitchDiff * rotationLerp;
            logDistance += (targetLogDistance - logDistance) * zoomLerp;

            // Mark dirty if we moved
            if (Math.abs(headingDiff) > 0.001f || Math.abs(pitchDiff) > 0.001f) {
                viewDirty = true;
            }
            if (Math.abs(targetLogDistance - logDistance) > 0.0001f) {
                distanceDirty = true;
            }
        } else {
            // No smoothing - direct assignment
            heading = targetHeading;
            pitch = targetPitch;
            logDistance = targetLogDistance;
        }

        // VELOCITY-BASED MOMENTUM with half-life decay
        if (MOMENTUM_ENABLED && (headingVelocity != 0 || pitchVelocity != 0)) {
            // Check if momentum should be active
            // Lower threshold for simulation speed, smaller deadzone for input
            boolean momentumActive =
                    !simulationPaused
                            && simulationSpeed <= 100.0
                            && // More permissive
                            timeSinceInput > 0.05f; // Smaller deadzone

            if (momentumActive) {
                // Apply velocity to target position
                targetHeading += headingVelocity * deltaTime;
                targetPitch += pitchVelocity * deltaTime;

                // Frame-independent decay using half-life
                // This ensures consistent decay regardless of framerate
                float decay = (float) Math.exp(-LN_2 * deltaTime / VELOCITY_HALF_LIFE);
                headingVelocity *= decay;
                pitchVelocity *= decay;

                // Kill tiny velocities to prevent infinite micro-movements
                if (Math.abs(headingVelocity) < 0.01f) headingVelocity = 0;
                if (Math.abs(pitchVelocity) < 0.01f) pitchVelocity = 0;

                viewDirty = true;
            } else {
                // Clear momentum when conditions aren't met
                headingVelocity = 0;
                pitchVelocity = 0;
            }
        }

        // Normalize angles after any changes
        while (heading < 0) heading += 360;
        while (heading >= 360) heading -= 360;
        while (targetHeading < 0) targetHeading += 360;
        while (targetHeading >= 360) targetHeading -= 360;
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        targetPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, targetPitch));

        // Enforce minimum distance AFTER smoothing — otherwise smoothing can pull the camera inside
        // a planet.
        if (enforceMinDistance > 0.0f) {
            float minLogDistance = (float) Math.log10(enforceMinDistance);

            // Force BOTH current and target to respect minimum
            // This prevents any possibility of getting inside
            if (logDistance < minLogDistance) {
                logDistance = minLogDistance;
                distanceDirty = true;
                viewDirty = true; // Force immediate update
            }
            if (targetLogDistance < minLogDistance) {
                targetLogDistance = minLogDistance;
            }

            // Also update the actual distance immediately if needed
            // This ensures the very next frame uses the correct distance
            float currentDistance = (float) Math.exp(LN_10 * logDistance);
            if (currentDistance < enforceMinDistance) {
                distance = enforceMinDistance;
                logDistance = minLogDistance;
                targetLogDistance = minLogDistance;
                distanceDirty = false; // Already updated
                viewDirty = true;
            }
        }

        // Update distance from log (only when dirty)
        if (distanceDirty) {
            // OPTIMIZATION: Use exp(ln(10) * x) instead of pow(10, x)
            // 2-3x faster than Math.pow
            distance = (float) Math.exp(LN_10 * logDistance);
            distanceDirty = false;
        }

        // Update view matrix when dirty (camera has moved)
        // View matrix always updates when dirty, regardless of pause state.
        // Body positions are cached and don't need recalculation.
        if (viewDirty) {
            updateViewMatrix();
            viewDirty = false;
        }

        // Update projection matrix (only when dirty)
        if (projectionDirty) {
            updateProjectionMatrix();
            projectionDirty = false;
        }
    }

    private void updateViewMatrix() {
        // Camera-relative rendering: keep view matrix transforms small to
        // avoid float precision loss at astronomical distances.

        // Calculate eye position relative to center using spherical coordinates
        float headingRad = (float) Math.toRadians(heading);
        float pitchRad = (float) Math.toRadians(pitch);

        // Spherical to Cartesian (camera orbits around target)
        // Eye position relative to target at origin
        float relativeX = distance * (float) (Math.sin(headingRad) * Math.cos(pitchRad));
        float relativeY = distance * (float) Math.sin(pitchRad);
        float relativeZ = distance * (float) (Math.cos(headingRad) * Math.cos(pitchRad));

        // Use target position if we have one
        if (hasTarget) {

            // Calculate actual camera world position
            position.set(
                    targetPosition.x + relativeX,
                    targetPosition.y + relativeY,
                    targetPosition.z + relativeZ);

            // Target is at the specified position
            target.set(targetPosition);

            // Build a pure rotation view matrix with camera at origin —
            // gives us numerical stability at large distances.

            // Calculate normalized look direction
            float lookX = -relativeX;
            float lookY = -relativeY;
            float lookZ = -relativeZ;

            // Normalize to create pure rotation matrix
            float lookLength = (float) Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
            if (lookLength > EPSILON) {
                lookX /= lookLength;
                lookY /= lookLength;
                lookZ /= lookLength;
            } else {
                // Fallback to looking down -Z
                lookX = 0.0f;
                lookY = 0.0f;
                lookZ = -1.0f;
            }

            // Build pure rotation view matrix with camera at origin
            viewMatrix.setLookAt(
                    0.0f,
                    0.0f,
                    0.0f, // Camera at origin
                    lookX,
                    lookY,
                    lookZ, // Look direction
                    up.x,
                    up.y,
                    up.z // Up vector
                    );
        } else {
            // No target - center on origin
            targetPosition.zero();
            target.zero();
            position.set(relativeX, relativeY, relativeZ);

            // Standard view matrix
            viewMatrix.setLookAt(
                    relativeX,
                    relativeY,
                    relativeZ, // Eye
                    0.0f,
                    0.0f,
                    0.0f, // Target at origin
                    up.x,
                    up.y,
                    up.z // Up
                    );
        }

        // Update world position for camera-relative rendering
        // Must be the actual camera position (targetPosition + relative offset), not the target.
        // NOT just the targetPosition! The renderer uses this to position objects relative to
        // the camera. If we used targetPosition alone, the renderer would place objects
        // incorrectly, causing rendering errors or a blank screen.
        // The actual position = targetPosition + spherical coordinate offset (relativeX/Y/Z)
        cameraWorldPosition.set(position.x, position.y, position.z);

        // Update cached position only if it changed significantly
        // This avoids allocations when camera hasn't actually moved
        if (Math.abs(cameraWorldPosition.x - lastCachedX) > POSITION_THRESHOLD
                || Math.abs(cameraWorldPosition.y - lastCachedY) > POSITION_THRESHOLD
                || Math.abs(cameraWorldPosition.z - lastCachedZ) > POSITION_THRESHOLD) {
            // Position changed - create new immutable Vec3d
            cachedCameraWorldPosition =
                    new Vec3d(cameraWorldPosition.x, cameraWorldPosition.y, cameraWorldPosition.z);
            lastCachedX = cameraWorldPosition.x;
            lastCachedY = cameraWorldPosition.y;
            lastCachedZ = cameraWorldPosition.z;
        }
    }

    private void updateProjectionMatrix() {
        float aspect = (float) viewportWidth / (float) viewportHeight;
        projectionMatrix.setPerspective(
                (float) Math.toRadians(fovDegrees), aspect, nearPlane, farPlane);
    }

    /**
     * Calculate momentum scale based on distance. Less momentum when close (for precision), more
     * when far (for cinematic panning).
     */
    private float getMomentumScale() {
        // Adaptive scaling based on camera distance
        // Matches typical astronomical scale transitions
        if (distance < 10.0f) {
            // Surface level - minimal momentum for precise inspection
            return 0.1f;
        } else if (distance < 100.0f) {
            // Moon/satellite orbit distance - reduced momentum
            return 0.3f;
        } else if (distance < 1000.0f) {
            // Planetary orbit distance - moderate momentum
            return 0.6f;
        } else {
            // Solar system scale - full cinematic momentum
            return 1.0f;
        }
    }

    // Input methods

    public void rotate(float deltaHeading, float deltaPitch) {
        // Apply sensitivity
        float dx = deltaHeading * SENSITIVITY * 1.2f; // More sensitive horizontally
        float dy = deltaPitch * SENSITIVITY * 0.8f;

        if (MOMENTUM_ENABLED) {
            // With momentum enabled, only update target angles (not current angles)
            // This allows the smoothing system to interpolate towards the target
            targetHeading += dx;
            targetPitch += dy;

            // Clamp target pitch
            targetPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, targetPitch));

            // Get adaptive momentum scale based on distance
            float momentumScale = getMomentumScale();

            // Set velocity for "coast from last input" momentum
            // Velocity in degrees/second for frame-independent application
            // Scale by distance for context-appropriate feel
            headingVelocity = dx * 5.0f * momentumScale;
            pitchVelocity = dy * 5.0f * momentumScale;

            // Clamp velocities to reasonable limits (degrees/second)
            headingVelocity = Math.max(-180.0f, Math.min(180.0f, headingVelocity));
            pitchVelocity = Math.max(-90.0f, Math.min(90.0f, pitchVelocity));
        } else {
            // Without momentum, apply directly to both current and target
            heading += dx;
            targetHeading += dx;

            pitch += dy;
            targetPitch += dy;

            // Clamp pitch
            pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
            targetPitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, targetPitch));
        }

        // Reset input timer
        timeSinceInput = 0;
        viewDirty = true;
    }

    public void zoom(float delta) {
        float zoomDelta = delta * ZOOM_SPEED * 0.002f;

        if (MOMENTUM_ENABLED) {
            // With momentum, only update target (smoothing will interpolate)
            targetLogDistance += zoomDelta;

            // Clamp target to valid range
            float minLog = (float) Math.log10(MIN_DISTANCE);
            float maxLog = (float) Math.log10(MAX_DISTANCE);
            targetLogDistance = Math.max(minLog, Math.min(maxLog, targetLogDistance));
        } else {
            // Without momentum, apply directly to both
            logDistance += zoomDelta;
            targetLogDistance += zoomDelta;

            // Clamp to valid range
            float minLog = (float) Math.log10(MIN_DISTANCE);
            float maxLog = (float) Math.log10(MAX_DISTANCE);

            logDistance = Math.max(minLog, Math.min(maxLog, logDistance));
            targetLogDistance = Math.max(minLog, Math.min(maxLog, targetLogDistance));
        }

        timeSinceInput = 0;
        viewDirty = true;
        distanceDirty = true;
    }

    public void reset() {
        heading = targetHeading = 0;
        pitch = targetPitch = 45;
        logDistance = targetLogDistance = 2.699f;
        distance = 500;
        headingVelocity = pitchVelocity = 0;

        viewDirty = true;
        distanceDirty = true;

        LOGGER.info("Camera reset to default position");
    }

    // Target tracking

    /**
     * Update tracking position from tracked body (pulled from ephemeris).
     *
     * @param trackedBody The body to track
     * @param timeContext Current time context
     */
    private void updateTrackingPosition(CelestialBody trackedBody, TimeContext timeContext) {
        if (timeContext == null) {
            LOGGER.warning("[CAMERA] No time context for tracking update");
            return;
        }

        // Check if we need to update position (time changed OR body changed)
        double currentTimeJD = timeContext.getJulianDateTDB();
        boolean timeChanged =
                Double.isNaN(lastTimeJD) || Math.abs(currentTimeJD - lastTimeJD) > 1e-10;
        boolean bodyChanged = (lastTrackedBody != trackedBody);

        if (!timeChanged && !bodyChanged) {
            // Neither time nor body changed - skip update to avoid redundant ephemeris calls
            return;
        }

        // Update tracking state
        lastTimeJD = currentTimeJD;
        lastTrackedBody = trackedBody;

        // Get ephemeris state - this MUST hit the cache for consistency with renderer
        // The cache keys on frame number, so we need the exact same TimeContext
        FramedState state = trackedBody.getState(timeContext);
        if (state == null) {
            LOGGER.warning(
                    "[CAMERA] No state available for tracked body: " + trackedBody.getName());
            return;
        }

        if (frameCount % 60 == 0) {
            Vec3d rawPos = state.getPosition();
            LOGGER.finest(
                    String.format(
                            "[CAMERA] Tracking %s at frame %d (TimeContext frame: %d)",
                            trackedBody.getName(), frameCount, timeContext.getFrameNumber()));
            LOGGER.finest(
                    String.format(
                            "[CAMERA] Raw position: (%.6f, %.6f, %.6f) AU",
                            rawPos.x, rawPos.y, rawPos.z));
        }

        // Reuse pre-allocated work vectors for the transform and scale.
        if (!state.isInFrame(FrameNames.OPENGL_RENDER)) {
            frameManager.transformInto(
                    state,
                    FrameNames.OPENGL_RENDER,
                    workPosition,
                    workVelocity // Class fields, not local!
                    );
            // workPosition now contains the transformed position
        } else {
            // Copy position to workPosition for consistent handling
            Vec3d pos = state.getPosition();
            workPosition.set(pos.x, pos.y, pos.z);
        }

        // Apply visual scaling: compute distance, derive scale, apply to position
        double distanceAU =
                Math.sqrt(
                        workPosition.x * workPosition.x
                                + workPosition.y * workPosition.y
                                + workPosition.z * workPosition.z);

        if (distanceAU > 0.001) { // Avoid division by zero
            // Get visual distance using the same method
            double visualDist = ScaleManager.getVisualDistance(distanceAU);
            double scale = visualDist / distanceAU;

            // Apply scale to position
            workScaledPosition.set(
                    workPosition.x * scale, workPosition.y * scale, workPosition.z * scale);

            if (frameCount % 60 == 0) {
                LOGGER.finest(
                        String.format(
                                "[CAMERA] Scaled position: (%.6f, %.6f, %.6f) visual units",
                                workScaledPosition.x, workScaledPosition.y, workScaledPosition.z));
            }

            if (timeContext.isPaused() && frameCount % 60 == 0) {
                LOGGER.finest(
                        String.format(
                                "[CAMERA-PAUSED] %s position: (%.10f, %.10f, %.10f)",
                                trackedBody.getName(),
                                workScaledPosition.x,
                                workScaledPosition.y,
                                workScaledPosition.z));
            }
        } else {
            // At origin
            workScaledPosition.set(0, 0, 0);
        }

        // Update position
        setTargetPosition(
                (float) workScaledPosition.x,
                (float) workScaledPosition.y,
                (float) workScaledPosition.z);
    }

    /**
     * Set the target position for the camera to look at. This should be called by the renderer each
     * frame with the body's current position.
     *
     * @param x Target X position in visual units
     * @param y Target Y position in visual units
     * @param z Target Z position in visual units
     */
    public void setTargetPosition(float x, float y, float z) {
        // Direct update without smoothing - just set the position
        // The smoothing was causing drift even when position wasn't changing

        if (!hasTarget
                || Math.abs(x - targetPosition.x) > POSITION_THRESHOLD
                || Math.abs(y - targetPosition.y) > POSITION_THRESHOLD
                || Math.abs(z - targetPosition.z) > POSITION_THRESHOLD) {

            targetPosition.set(x, y, z);
            hasTarget = true;
            viewDirty = true;

            if (frameCount % 60 == 0) {
                LOGGER.finest(
                        String.format("[CAMERA] Target position: (%.6f, %.6f, %.6f)", x, y, z));
            }
        }
    }

    /** Clear the target, causing camera to look at origin. */
    public void clearTarget() {
        targetPosition.zero();
        hasTarget = false;
        viewDirty = true;

        // Reset momentum when clearing target
        headingVelocity = 0.0f;
        pitchVelocity = 0.0f;
        timeSinceInput = 999.0f;
    }

    /**
     * Notify camera that we've switched to a different body. This should only be called by
     * FrameController when user explicitly switches bodies.
     *
     * @param targetBody The celestial body we're switching to (can be null)
     */
    public void notifyBodySwitch(CelestialBody targetBody) {
        // Clear momentum to prevent spinning when switching bodies
        headingVelocity = 0.0f;
        pitchVelocity = 0.0f;
        timeSinceInput = 999.0f;

        // Reset smoothed position to prevent jumping
        hasTarget = false; // Will reinitialize smoothed position on next update

        // Enforce minimum distance based on body radius
        if (targetBody != null) {
            // Get body radius in km
            double radiusKm = targetBody.getRadius();

            // Get the visual radius using ScaleManager
            // This applies logarithmic compression for gas giants
            String bodyType = targetBody.getName().toLowerCase();
            float visualRadius = (float) ScaleManager.getVisualRadius(radiusKm, bodyType);

            // Very minimal distance just to prevent clipping inside the mesh
            // With angular size preservation, this should rarely be needed
            float multiplier = 1.1f; // Just 10% outside the surface as a safety net

            float minDistance = visualRadius * multiplier;

            // Check both current AND target distances — the smoothing path can briefly violate the
            // minimum.
            // Because smoothing interpolates from current toward target
            // Calculate actual distances ONCE and reuse
            float actualDistance = (float) Math.exp(LN_10 * logDistance);
            float targetDistance = (float) Math.exp(LN_10 * targetLogDistance);

            // ALWAYS store the NEW minimum distance for THIS body
            enforceMinDistance = minDistance;

            // Apply angular size preservation when switching between different sized bodies
            if (lastBodyVisualRadius > 0.1f
                    && Math.abs(lastBodyVisualRadius - visualRadius) > 0.01f) {
                applyAngularSizePreservation(visualRadius, actualDistance, minDistance);
            }

            // Only enforce minimum distance if angular size preservation didn't already handle it
            // This should be rare - only when starting from a very unusual position
            if (lastBodyVisualRadius < 0.1f
                    || Math.abs(lastBodyVisualRadius - visualRadius) < 0.01f) {
                // No need to recalculate - already computed above
                // No previous body or same size - check minimum distance
                if (actualDistance < minDistance || targetDistance < minDistance) {
                    // Calculate new log distance with some extra margin
                    float newLogDistance =
                            (float) (Math.log10(minDistance * 1.05f)); // Add 5% extra margin

                    // Update ALL distance variables to bypass smoothing completely
                    logDistance = newLogDistance;
                    targetLogDistance = newLogDistance;
                    distance = minDistance * 1.05f;

                    // Clear dirty flag since we've updated everything
                    distanceDirty = false;
                    viewDirty = true; // Force view matrix update

                    LOGGER.fine(
                            String.format(
                                    "[CAMERA] FORCED to min distance %.1f to stay outside %s (was actual:%.1f, target:%.1f)",
                                    minDistance * 1.05f,
                                    targetBody.getName(),
                                    actualDistance,
                                    targetDistance));
                }
            }

            // Store this body's visual radius for next switch
            lastBodyVisualRadius = visualRadius;
        } else {
            // Clear enforcement when no body is tracked
            enforceMinDistance = 0.0f;
            LOGGER.fine("[CAMERA] Body switch - no target body");
        }
    }

    /**
     * Apply angular size preservation when switching bodies. Maintains the apparent size of objects
     * in the field of view.
     *
     * @param visualRadius The visual radius of the new body
     * @param actualDistance The current camera distance
     * @param minDistance The minimum allowed distance for safety
     */
    private void applyAngularSizePreservation(
            float visualRadius, float actualDistance, float minDistance) {
        // Calculate angular size of the previous body (in radians)
        // Angular size = 2 * arctan(radius / distance)
        // This represents the angle the object subtends in our view
        float lastAngularSize = 2.0f * (float) Math.atan(lastBodyVisualRadius / actualDistance);

        // Calculate the distance needed to maintain the same angular size for the new body
        // Rearranging: distance = radius / tan(angularSize / 2)
        float proportionalDistance = visualRadius / (float) Math.tan(lastAngularSize / 2.0f);

        // Safety checks to ensure reasonable distances
        // Don't get closer than 2x the radius
        float safeMinimum = visualRadius * 2.0f;
        if (proportionalDistance < safeMinimum) {
            proportionalDistance = safeMinimum;
        }

        // For large bodies, don't get closer than 4x radius
        if (visualRadius > 5.0f) {
            float comfortableMax = visualRadius * 4.0f;
            if (proportionalDistance < comfortableMax) {
                proportionalDistance = comfortableMax;
            }
        }

        // Ensure we don't go below the hard minimum
        if (proportionalDistance >= minDistance) {
            // Apply the calculated distance
            float newLogDistance = (float) Math.log10(proportionalDistance);
            logDistance = newLogDistance;
            targetLogDistance = newLogDistance;
            distance = proportionalDistance;
            distanceDirty = false;
            viewDirty = true;
        }
    }

    /**
     * Set the simulation state for momentum control. Disable momentum when paused or at high
     * speeds.
     *
     * @param paused Whether simulation is paused
     * @param speed Simulation speed multiplier (1.0 = real-time)
     */
    public void setSimulationState(boolean paused, double speed) {
        this.simulationPaused = paused;
        this.simulationSpeed = speed;
    }

    // View interface implementation

    @Override
    public void fillViewMatrix(Matrix4f output) {
        output.set(viewMatrix); // No allocation, just copy
    }

    @Override
    public void fillProjectionMatrix(Matrix4f output) {
        output.set(projectionMatrix); // No allocation, just copy
    }

    @Override
    public void fillViewMatrixDouble(Matrix4d output) {
        output.set(viewMatrix); // JOML handles conversion efficiently
    }

    @Override
    public void fillProjectionMatrixDouble(Matrix4d output) {
        output.set(projectionMatrix); // JOML handles conversion efficiently
    }

    @Override
    public double getFieldOfView() {
        return fovDegrees;
    }

    @Override
    public double getEyeX() {
        return position.x;
    }

    @Override
    public double getEyeY() {
        return position.y;
    }

    @Override
    public double getEyeZ() {
        return position.z;
    }

    @Override
    public Vec3d getCameraWorldPosition() {
        // Returns the cached Vec3d; refreshed in updateViewMatrix() when the camera moves.
        // This eliminates 3 allocations per frame (called 3x by CelestialRenderer)
        return cachedCameraWorldPosition != null ? cachedCameraWorldPosition : Vec3d.ZERO;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        this.viewportWidth = Math.max(1, width);
        this.viewportHeight = Math.max(1, height);
        projectionDirty = true;
    }

    // Getters for camera state

    public float getDistance() {
        return distance;
    }

    public float getHeading() {
        return heading;
    }

    public float getPitch() {
        return pitch;
    }
}
