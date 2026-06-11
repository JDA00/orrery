package com.jda.orrery.graphics.core;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.frames.FrameManager;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.core.time.TimeManager;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.domain.astronomy.Planet;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.graphics.api.View;
import com.jda.orrery.graphics.camera.OrbitCamera;
import com.jda.orrery.graphics.geometry.MeshLibrary;
import com.jda.orrery.graphics.postfx.PostFXTarget;
import java.util.logging.Logger;
import org.lwjgl.system.Platform;

/** Frame controller for LWJGL version. Manages render loop and input handling. */
public class FrameController {
    private static final Logger LOGGER = Logging.logger(FrameController.class);

    // Scroll zoom sensitivity. Mouse wheels deliver discrete +/-1 detents while Mac
    // trackpads stream small fractional deltas at high frequency, so one constant
    // cannot suit both. Platform default; override with -PzoomSensitivity on the
    // Gradle command line until a proper user settings file exists.
    private static final double ZOOM_SENSITIVITY = resolveZoomSensitivity();

    private static double resolveZoomSensitivity() {
        String override = System.getProperty("orrery.zoomSensitivity");
        if (override != null) {
            try {
                double value = Double.parseDouble(override);
                if (value > 0) {
                    return value;
                }
                LOGGER.warning("orrery.zoomSensitivity must be positive: " + override);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid orrery.zoomSensitivity: " + override);
            }
        }
        return Platform.get() == Platform.MACOSX ? 1.0 : 5.0;
    }

    // Middle-mouse dolly drag sensitivity: ~600 px of drag per decade of
    // distance. Override with -PdollySensitivity on the Gradle command line.
    private static final double DOLLY_SENSITIVITY = resolveDollySensitivity();

    private static double resolveDollySensitivity() {
        String override = System.getProperty("orrery.dollySensitivity");
        if (override != null) {
            try {
                double value = Double.parseDouble(override);
                if (value > 0) {
                    return value;
                }
                LOGGER.warning("orrery.dollySensitivity must be positive: " + override);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid orrery.dollySensitivity: " + override);
            }
        }
        return 0.83;
    }

    // Zoom glide length, tunable for feel alongside sensitivity.
    private static final float ZOOM_SMOOTHING = resolveZoomSmoothing();

    private static float resolveZoomSmoothing() {
        String override = System.getProperty("orrery.zoomSmoothing");
        if (override != null) {
            try {
                float value = Float.parseFloat(override);
                if (value > 0 && value < 1) {
                    return value;
                }
                LOGGER.warning("orrery.zoomSmoothing must be in (0, 1): " + override);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid orrery.zoomSmoothing: " + override);
            }
        }
        return 0.85f;
    }

    private long window;
    private final DrawContext drawContext;
    private final SceneController sceneController;
    private View view;
    private final TimeManager timeManager;
    private final MeshLibrary meshLibrary;

    // Frame timing
    private long lastFrameTime;
    private int frameCount = 0;
    private TimeContext lastTimeContext = null;

    // Input state
    private double lastMouseX, lastMouseY;
    private boolean isDragging;
    private boolean isZoomDragging;

    // Planet cycling for camera focus
    private int currentBodyIndex = -1; // -1 = Sun, 0-7 = planets
    private CelestialBody trackedBody = null; // The actual body being tracked

    private PostFXTarget postFXTarget;

    /**
     * Create frame controller with injected dependencies.
     *
     * @param drawContext Draw context for rendering
     * @param sceneController Scene controller for layer management
     * @param timeManager Time manager for simulation time
     */
    public FrameController(
            DrawContext drawContext,
            SceneController sceneController,
            TimeManager timeManager,
            MeshLibrary meshLibrary,
            FrameManager frameManager) {
        this.drawContext = drawContext;
        this.sceneController = sceneController;
        this.timeManager = timeManager;
        this.meshLibrary = meshLibrary;
        OrbitCamera camera = new OrbitCamera(frameManager);
        camera.setZoomSmoothing(ZOOM_SMOOTHING);
        this.view = camera;
    }

    public void setPostFXTarget(PostFXTarget target) {
        this.postFXTarget = target;
    }

    public void init(long window) {
        this.window = window;

        LOGGER.info("Initializing FrameController");

        // Get window size for viewport
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);

        // Configure draw context (already injected)
        drawContext.setView(view);
        drawContext.setSceneController(sceneController);
        drawContext.setViewportWidth(width[0]);
        drawContext.setViewportHeight(height[0]);

        // OpenGL initialization
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_MULTISAMPLE);
        // Space is black. Pixel writes are subject to GL_FRAMEBUFFER_SRGB, so any
        // non-zero channel here would be interpreted as linear and brightened on write.
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Initialize mesh library (preload common meshes at startup)
        meshLibrary.preloadCommon();
        LOGGER.info("Initialized mesh library with common meshes");

        // Set initial TimeContext before scene init so the lighting system
        // has time data available when it initializes.
        // First call to advanceTime will return current Julian Date with delta=0
        TimeContext initialTime = timeManager.advanceTime(System.nanoTime());
        drawContext.setTimeContext(initialTime);
        LOGGER.info(
                "Set initial TimeContext (JD="
                        + initialTime.getJulianDateTDB()
                        + ") for scene initialization");

        // Initialize scene controller (now with valid TimeContext)
        sceneController.init(drawContext);

        // Set up initial view
        view.setViewport(0, 0, width[0], height[0]);

        // Match PostFXTarget to the real framebuffer size (differs from config on Retina).
        if (postFXTarget != null && width[0] > 0 && height[0] > 0) {
            postFXTarget.resize(width[0], height[0]);
        }

        // Initial camera focus on the Sun.
        // Scene layer creation now happens in ApplicationContext.initializeGL().
        SolarSystem solarSystem = drawContext.getSolarSystem();
        if (solarSystem == null) {
            throw new IllegalStateException(
                    "Solar system must be set on DrawContext before FrameController.init()");
        }
        trackedBody = solarSystem.getSun();
        currentBodyIndex = -1;
        LOGGER.info("Camera initially tracking: Sun");
        LOGGER.info(
                "Controls: SPACE=pause, ,/.=speed down/up, 1-4=preset speeds, N=now, J=J2000, R=reset view");
        LOGGER.info("Controls: LEFT/RIGHT arrows=cycle through planets");

        lastFrameTime = System.currentTimeMillis();
    }

    /**
     * Process a single frame with injected dependencies. This is the new preferred method for
     * rendering.
     *
     * Single time authority per frame — this is the only place where time advances in the entire
     * application.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void processFrame(double deltaTime) {
        // Update timing - THIS IS THE SINGLE TIME AUTHORITY
        long currentNanos = System.nanoTime();
        long currentTime = System.currentTimeMillis();

        // Advance time and get snapshot for this frame
        TimeContext timeContext = timeManager.advanceTime(currentNanos);

        // SMART CACHE CLEARING: Only clear when time actually changes!
        // This is a key optimization - when paused or at same time, keep cache
        boolean timeChanged = false;
        if (lastTimeContext == null) {
            timeChanged = true;
        } else {
            double timeDelta =
                    Math.abs(timeContext.getJulianDateTDB() - lastTimeContext.getJulianDateTDB());
            // Use tighter epsilon for better pause stability (1e-15 instead of 1e-9)
            timeChanged = timeDelta > 1e-15;

            // Debug: Verify pause stability (should never drift now)
            if (timeManager.isPaused()) {
                if (timeChanged) {
                    // This should never happen with fixed architecture
                    LOGGER.severe(
                            String.format(
                                    "[CRITICAL] Time drift while paused! Frame %d: Delta: %.15f JD",
                                    frameCount, timeDelta));
                    LOGGER.severe(
                            String.format(
                                    "[CRITICAL] Old JD: %.15f, New JD: %.15f",
                                    lastTimeContext.getJulianDateTDB(),
                                    timeContext.getJulianDateTDB()));
                    LOGGER.severe(
                            "This indicates multiple time advancement calls - check for duplicate timeManager.advanceTime()");
                } else if (frameCount % 60 == 0) {
                    LOGGER.fine(
                            String.format(
                                    "[FRAME-PAUSED] Frame %d: Time frozen at JD %.15f",
                                    frameCount, timeContext.getJulianDateTDB()));
                }
            }
        }

        if (timeChanged) {
            lastTimeContext = timeContext;
        }

        drawContext.setFrameTimeStamp(currentTime);
        drawContext.setDeltaTime(deltaTime);
        drawContext.setTimeContext(timeContext);

        // Set tracked body in DrawContext; the renderer updates camera position.
        drawContext.setTrackedBody(trackedBody);

        // Pass simulation state to camera for momentum control
        if (view instanceof OrbitCamera) {
            OrbitCamera orbitCam = (OrbitCamera) view;
            orbitCam.setSimulationState(timeManager.isPaused(), timeManager.getSimulationSpeed());
        }

        // Update view (camera handles its own momentum now)
        view.apply(drawContext);

        // Render scene
        sceneController.drawFrame(drawContext);

        // Update frame counter
        frameCount++;

        lastFrameTime = currentTime;
    }

    public void dispose() {
        LOGGER.info("Disposing FrameController");

        if (sceneController != null) {
            sceneController.dispose(drawContext);
        }
    }

    // GLFW Callbacks

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) {
            switch (key) {
                case GLFW_KEY_R:
                    if (view instanceof OrbitCamera) {
                        ((OrbitCamera) view).reset();
                    }
                    break;
                case GLFW_KEY_SPACE:
                    // Toggle pause/resume
                    boolean wasPaused = timeManager.isPaused();
                    LOGGER.fine(
                            String.format(
                                    "[FRAME-PAUSE] SPACE pressed: was %s, setting to %s",
                                    wasPaused ? "PAUSED" : "RUNNING",
                                    wasPaused ? "RUNNING" : "PAUSED"));
                    timeManager.setPaused(!wasPaused);
                    // Clear last time context to force cache update on resume
                    if (wasPaused) {
                        LOGGER.fine(
                                "[FRAME-PAUSE] Clearing lastTimeContext to force cache update on resume");
                        lastTimeContext = null;
                    }
                    break;
                case GLFW_KEY_COMMA:
                    // Slow down time
                    double currentSpeed = timeManager.getSimulationSpeed();
                    double newSpeed = currentSpeed / 2.0;
                    if (newSpeed >= 0.0625) { // Minimum 1/16x speed
                        timeManager.setSimulationSpeed(newSpeed);
                        LOGGER.fine("Time speed: " + newSpeed + "x");
                    }
                    break;
                case GLFW_KEY_PERIOD:
                    // Speed up time
                    currentSpeed = timeManager.getSimulationSpeed();
                    newSpeed = currentSpeed * 2.0;
                    if (newSpeed <= 1048576.0) { // Maximum ~3 years per second
                        timeManager.setSimulationSpeed(newSpeed);
                        LOGGER.fine("Time speed: " + newSpeed + "x");
                    }
                    break;
                case GLFW_KEY_SLASH:
                    // Reset to real-time
                    timeManager.setSimulationSpeed(1.0);
                    LOGGER.fine("Time speed reset to 1.0x");
                    break;
                case GLFW_KEY_N:
                    // Reset to current real-world time
                    timeManager.resetToNow();
                    lastTimeContext = null; // Force cache clear on next frame
                    break;
                case GLFW_KEY_J:
                    // Jump to J2000.0 epoch
                    timeManager.resetToJ2000();
                    lastTimeContext = null; // Force cache clear on next frame
                    break;
                case GLFW_KEY_1:
                    // Real-time (1 second = 1 second)
                    timeManager.setSimulationSpeed(1.0);
                    LOGGER.fine("Time speed: real-time (1.0x)");
                    break;
                case GLFW_KEY_2:
                    // 1 day per second
                    timeManager.setSimulationSpeed(86400.0);
                    LOGGER.fine("Time speed: 1 day/sec");
                    break;
                case GLFW_KEY_3:
                    // 1 week per second
                    timeManager.setSimulationSpeed(604800.0);
                    LOGGER.fine("Time speed: 1 week/sec");
                    break;
                case GLFW_KEY_4:
                    // 1 month per second
                    timeManager.setSimulationSpeed(2592000.0);
                    LOGGER.fine("Time speed: 1 month/sec");
                    break;
                case GLFW_KEY_5:
                    // 1 year per second
                    timeManager.setSimulationSpeed(31536000.0);
                    LOGGER.fine("Time speed: 1 year/sec");
                    break;
                case GLFW_KEY_LEFT:
                    // Cycle to previous planet
                    cycleToPreviousPlanet();
                    break;
                case GLFW_KEY_RIGHT:
                    // Cycle to next planet
                    cycleToNextPlanet();
                    break;
                case GLFW_KEY_C:
                    // C key available for future use
                    break;
            }
        }
    }

    /**
     * Cycle camera focus to the next planet. Order: Sun -> Mercury -> Venus -> Earth -> Mars ->
     * Jupiter -> Saturn -> Uranus -> Neptune -> Sun
     */
    private void cycleToNextPlanet() {
        if (drawContext == null || drawContext.getSolarSystem() == null) {
            return;
        }

        currentBodyIndex++;
        if (currentBodyIndex > 7) { // After Neptune (index 7), wrap to Sun
            currentBodyIndex = -1;
        }

        updateCameraFocus();
    }

    /** Cycle camera focus to the previous planet. */
    private void cycleToPreviousPlanet() {
        if (drawContext == null || drawContext.getSolarSystem() == null) {
            return;
        }

        currentBodyIndex--;
        if (currentBodyIndex < -1) { // Before Sun, wrap to Neptune
            currentBodyIndex = 7; // Neptune is at index 7
        }

        updateCameraFocus();
    }

    /** Update camera focus based on current body index. */
    private void updateCameraFocus() {
        SolarSystem solarSystem = drawContext.getSolarSystem();
        CelestialBody targetBody = null;

        if (currentBodyIndex == -1) {
            // Focus on Sun
            targetBody = solarSystem.getSun();
        } else {
            // Focus on planet by index
            java.util.List<Planet> planets = solarSystem.getPlanets();
            if (currentBodyIndex < planets.size()) {
                targetBody = planets.get(currentBodyIndex);
            }
        }

        // Update the tracked body reference
        trackedBody = targetBody;

        // Log the change
        if (targetBody != null) {
            LOGGER.info("Camera now tracking: " + targetBody.getName());
        } else {
            LOGGER.info("Camera tracking cleared");
        }

        // Notify camera about body switch
        if (view instanceof OrbitCamera) {
            ((OrbitCamera) view).notifyBodySwitch(targetBody);
        }
    }

    public void mouseButtonCallback(long window, int button, int action, int mods) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS && !isZoomDragging) {
                isDragging = true;
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                lastMouseX = xpos[0];
                lastMouseY = ypos[0];
            } else if (action == GLFW_RELEASE) {
                isDragging = false;
            }
        } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            if (action == GLFW_PRESS && !isDragging) {
                startDollyDrag(window);
            } else if (action == GLFW_RELEASE && isZoomDragging) {
                endDollyDrag(window);
            }
        }
    }

    /**
     * Begin a middle-mouse dolly drag: cursor captured for unlimited travel, raw motion where
     * supported.
     */
    private void startDollyDrag(long window) {
        isZoomDragging = true;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }
        // Read position after capture — GLFW switches to virtual coordinates.
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(window, xpos, ypos);
        lastMouseX = xpos[0];
        lastMouseY = ypos[0];
        if (view instanceof OrbitCamera) {
            ((OrbitCamera) view).setDollyActive(true);
        }
    }

    /** End a dolly drag and restore the cursor. */
    private void endDollyDrag(long window) {
        isZoomDragging = false;
        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
        }
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        if (view instanceof OrbitCamera) {
            ((OrbitCamera) view).setDollyActive(false);
        }
    }

    public void cursorPosCallback(long window, double xpos, double ypos) {
        if (isZoomDragging) {
            double dy = ypos - lastMouseY;

            if (view instanceof OrbitCamera) {
                OrbitCamera orbitCam = (OrbitCamera) view;

                // FOV-scaled like scrollCallback; drag up = push in.
                double zoomInput = dy * DOLLY_SENSITIVITY * (orbitCam.getFieldOfView() / 60.0);
                orbitCam.zoom((float) zoomInput);
            }

            lastMouseX = xpos;
            lastMouseY = ypos;
        } else if (isDragging) {
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;

            if (view instanceof OrbitCamera) {
                OrbitCamera orbitCam = (OrbitCamera) view;
                orbitCam.rotate((float) (dx * 0.5), (float) (dy * 0.5));
            }

            lastMouseX = xpos;
            lastMouseY = ypos;
        }
    }

    public void scrollCallback(long window, double xoffset, double yoffset) {
        if (view instanceof OrbitCamera) {
            OrbitCamera orbitCam = (OrbitCamera) view;

            // Linear input; zoom() converts to log space internally
            double zoomInput = -yoffset * 5.0 * ZOOM_SENSITIVITY;

            // Scale by current FOV for consistent angular change
            double fovFactor = orbitCam.getFieldOfView() / 60.0;
            zoomInput *= fovFactor;

            orbitCam.zoom((float) zoomInput);
        }
    }

    public void framebufferSizeCallback(long window, int width, int height) {
        glViewport(0, 0, width, height);
        drawContext.setViewportWidth(width);
        drawContext.setViewportHeight(height);
        view.setViewport(0, 0, width, height);

        if (postFXTarget != null && width > 0 && height > 0) {
            postFXTarget.resize(width, height);
        }

        LOGGER.fine(String.format("Viewport resized to %dx%d", width, height));
    }
}
