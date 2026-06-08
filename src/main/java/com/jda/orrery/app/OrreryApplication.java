package com.jda.orrery.app;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.core.FrameController;
import java.nio.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

/**
 * Main application using LWJGL.
 *
 * Uses ApplicationContext for dependency injection instead of singletons. All components are
 * explicitly created and wired through the context.
 */
public class OrreryApplication {
    private static final Logger LOGGER = Logging.logger(OrreryApplication.class);

    private static final int INITIAL_WIDTH = 1920;
    private static final int INITIAL_HEIGHT = 1080;
    private static final String WINDOW_TITLE = "Orrery 3D Visualization - LWJGL";

    // Set via -Porrery.debug=true or -Pdebug on the Gradle command line.
    private static final boolean DEBUG_MODE =
            Boolean.parseBoolean(System.getProperty("orrery.debug", "false"));

    // Set via -Dorrery.fullscreen=false or -Pwindowed on the Gradle command line.
    private static final boolean START_FULLSCREEN =
            Boolean.parseBoolean(System.getProperty("orrery.fullscreen", "true"));

    private long window;
    private ApplicationContext context;
    private FrameController frameController;
    private Thread glThread;
    private long lastFrameTime;

    // Window mode state for the fullscreen toggle (F11, or Cmd+Ctrl+F on macOS).
    private boolean fullscreen;
    private boolean windowedGeometryValid;
    private int windowedX, windowedY, windowedWidth, windowedHeight;

    public void run() {
        LOGGER.info("Starting Orrery LWJGL application");
        LOGGER.info("Java Version: " + System.getProperty("java.version"));
        LOGGER.info(
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        LOGGER.info("LWJGL Version: " + org.lwjgl.Version.getVersion());

        // Store the thread that will own the GL context
        glThread = Thread.currentThread();

        try {
            init();
            loop();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error during startup", e);
        } finally {
            cleanup();
        }
    }

    private void init() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Request OpenGL 4.1 core profile (maximum for macOS)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE); // Required on macOS

        if (DEBUG_MODE) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        }

        // MSAA disabled - was killing performance on integrated graphics
        glfwWindowHint(GLFW_SAMPLES, 0);

        // Start with focus
        glfwWindowHint(GLFW_FOCUSED, GLFW_TRUE);

        // Don't auto-minimize the fullscreen window on focus loss; keep rendering
        // while the user looks elsewhere.
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_FALSE);

        // Create window. Passing a monitor creates a GLFW fullscreen window;
        // matching the current desktop video mode is GLFW's "windowed full screen"
        // idiom: no display mode switch, native resolution on any monitor.
        // Fullscreen windows ignore the GLFW_VISIBLE hint and appear immediately,
        // so a cleared frame is presented right after context setup (below) to
        // avoid flashing an undefined buffer. A hidden-until-ready startup is not
        // possible: glfwSetWindowMonitor on Win32 force-shows the window
        // (SWP_SHOWWINDOW) without activating it, which looks worse.
        GLFWVidMode vidmode = getPrimaryVideoMode();
        if (START_FULLSCREEN && vidmode != null) {
            // Hints are sticky, so keep these scoped to this single creation.
            glfwWindowHint(GLFW_RED_BITS, vidmode.redBits());
            glfwWindowHint(GLFW_GREEN_BITS, vidmode.greenBits());
            glfwWindowHint(GLFW_BLUE_BITS, vidmode.blueBits());
            glfwWindowHint(GLFW_REFRESH_RATE, vidmode.refreshRate());
            window =
                    glfwCreateWindow(
                            vidmode.width(),
                            vidmode.height(),
                            WINDOW_TITLE,
                            glfwGetPrimaryMonitor(),
                            NULL);
            fullscreen = true;
        } else {
            if (START_FULLSCREEN) {
                LOGGER.warning("Fullscreen requested but no primary monitor; starting windowed");
            }
            window = glfwCreateWindow(INITIAL_WIDTH, INITIAL_HEIGHT, WINDOW_TITLE, NULL, NULL);
            fullscreen = false;
        }
        if (window == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        // Setup callbacks
        setupCallbacks();

        // Center window on screen (fullscreen windows are positioned by the monitor)
        if (!fullscreen) {
            centerWindow();
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync to cap at monitor refresh rate
        glfwSwapInterval(1); // VSync enabled

        // Make window visible
        glfwShowWindow(window);

        // This is critical for LWJGL's interoperation with GLFW OpenGL context
        GL.createCapabilities();

        // Present a cleared frame immediately so the window shows clean black
        // during initialization instead of an undefined buffer.
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glfwSwapBuffers(window);

        // Setup debug callbacks if available
        if (DEBUG_MODE) {
            setupDebugCallbacks();
        }

        // Log GL capabilities (includes version, vendor, renderer, GLSL version, and limits)
        com.jda.orrery.graphics.core.GLUtilities.logGLCapabilities();

        // Create application context with configuration, sized to the real
        // framebuffer so PostFX targets aren't allocated at a throwaway size.
        // FrameController.init() remains the final authority on framebuffer size.
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        glfwGetFramebufferSize(window, fbWidth, fbHeight);
        ApplicationContext.ApplicationConfig config =
                new ApplicationContext.ApplicationConfig(
                        fbWidth[0] > 0 ? fbWidth[0] : INITIAL_WIDTH,
                        fbHeight[0] > 0 ? fbHeight[0] : INITIAL_HEIGHT,
                        false, // vsync - DISABLED (was causing VSync timing jitter)
                        DEBUG_MODE,
                        512, // texture cache memory MB
                        2, // texture loader threads
                        60.0 // target FPS
                        );

        // Initialize application context (dependency injection)
        try {
            context = new ApplicationContext(config);

            // Initialize OpenGL resources from context
            context.initializeGL();

            // Get frame controller from context
            frameController = context.getFrameController();
            frameController.init(window);

            // Initialize timing
            lastFrameTime = System.nanoTime();

            // Warmup phase: Render a few frames to trigger JIT and shader compilation
            LOGGER.info("Running warmup frames...");
            for (int i = 0; i < 60; i++) {
                context.processFrame(0.016); // Simulate 60 FPS
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
            LOGGER.info("Warmup complete");

            LOGGER.info("Application context initialized successfully");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize application context: " + e.getMessage());
            throw new IllegalStateException("Initialization failed", e);
        }
    }

    private void setupDebugCallbacks() {
        GLCapabilities caps = GL.getCapabilities();

        // KHR_debug available in OpenGL 4.3+ or as an extension
        if (caps.GL_KHR_debug || caps.OpenGL43) {
            LOGGER.info("Debug context available, installing debug callback");

            glEnable(GL_DEBUG_OUTPUT);
            glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);

            GLDebugMessageCallback debugCallback =
                    GLDebugMessageCallback.create(
                            (source, type, id, severity, length, message, userParam) -> {
                                String msg = GLDebugMessageCallback.getMessage(length, message);

                                switch (severity) {
                                    case GL_DEBUG_SEVERITY_HIGH:
                                        LOGGER.severe("GL ERROR: " + msg);
                                        break;
                                    case GL_DEBUG_SEVERITY_MEDIUM:
                                        LOGGER.warning("GL WARNING: " + msg);
                                        break;
                                    case GL_DEBUG_SEVERITY_LOW:
                                        LOGGER.info("GL INFO: " + msg);
                                        break;
                                    case GL_DEBUG_SEVERITY_NOTIFICATION:
                                        LOGGER.fine("GL DEBUG: " + msg);
                                        break;
                                }
                            });

            glDebugMessageCallback(debugCallback, NULL);

            // Filter out notifications by default
            glDebugMessageControl(
                    GL_DONT_CARE,
                    GL_DONT_CARE,
                    GL_DEBUG_SEVERITY_NOTIFICATION,
                    (IntBuffer) null,
                    false);
        } else {
            LOGGER.info("Debug context not available on this system");
        }
    }

    private void setupCallbacks() {
        // Key callback with error handling
        glfwSetKeyCallback(
                window,
                (window, key, scancode, action, mods) -> {
                    try {
                        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                            glfwSetWindowShouldClose(window, true);
                        }
                        boolean toggleChord =
                                (key == GLFW_KEY_F11 && action == GLFW_PRESS)
                                        || (key == GLFW_KEY_F
                                                && action == GLFW_PRESS
                                                && (mods & (GLFW_MOD_SUPER | GLFW_MOD_CONTROL))
                                                        == (GLFW_MOD_SUPER | GLFW_MOD_CONTROL));
                        if (toggleChord) {
                            toggleFullscreen();
                            return;
                        }
                        if (frameController != null) {
                            frameController.keyCallback(window, key, scancode, action, mods);
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Error in key callback: " + e.getMessage());
                    }
                });

        // Mouse button callback
        glfwSetMouseButtonCallback(
                window,
                (window, button, action, mods) -> {
                    try {
                        if (frameController != null) {
                            frameController.mouseButtonCallback(window, button, action, mods);
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Error in mouse button callback: " + e.getMessage());
                    }
                });

        // Cursor position callback
        glfwSetCursorPosCallback(
                window,
                (window, xpos, ypos) -> {
                    try {
                        if (frameController != null) {
                            frameController.cursorPosCallback(window, xpos, ypos);
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Error in cursor position callback: " + e.getMessage());
                    }
                });

        // Scroll callback
        glfwSetScrollCallback(
                window,
                (window, xoffset, yoffset) -> {
                    try {
                        if (frameController != null) {
                            frameController.scrollCallback(window, xoffset, yoffset);
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Error in scroll callback: " + e.getMessage());
                    }
                });

        // Window resize callback
        glfwSetFramebufferSizeCallback(
                window,
                (window, width, height) -> {
                    try {
                        if (context != null) {
                            context.onResize(width, height);
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Error in framebuffer size callback: " + e.getMessage());
                    }
                });

        // Window focus callback
        glfwSetWindowFocusCallback(
                window,
                (window, focused) -> {
                    LOGGER.fine("Window focus: " + focused);
                });
    }

    private void centerWindow() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = getPrimaryVideoMode();
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2);
            }
        }
    }

    /** Current video mode of the primary monitor, or null if unavailable. */
    private static GLFWVidMode getPrimaryVideoMode() {
        long monitor = glfwGetPrimaryMonitor();
        return monitor != NULL ? glfwGetVideoMode(monitor) : null;
    }

    /**
     * Toggle between fullscreen and windowed. Returning to windowed restores the remembered
     * geometry; if the app started fullscreen there is none, so fall back to INITIAL_WIDTH x
     * INITIAL_HEIGHT centered on the current primary monitor. The monitor switch fires the
     * framebuffer-size callback, which drives viewport, camera and PostFX resizing.
     */
    private void toggleFullscreen() {
        GLFWVidMode vidmode = getPrimaryVideoMode();
        if (vidmode == null) {
            LOGGER.warning("Cannot toggle fullscreen: no primary monitor");
            return;
        }

        if (!fullscreen) {
            // Remember windowed geometry for the return trip.
            try (MemoryStack stack = stackPush()) {
                IntBuffer pX = stack.mallocInt(1);
                IntBuffer pY = stack.mallocInt(1);
                IntBuffer pWidth = stack.mallocInt(1);
                IntBuffer pHeight = stack.mallocInt(1);
                glfwGetWindowPos(window, pX, pY);
                glfwGetWindowSize(window, pWidth, pHeight);
                windowedX = pX.get(0);
                windowedY = pY.get(0);
                windowedWidth = pWidth.get(0);
                windowedHeight = pHeight.get(0);
                windowedGeometryValid = true;
            }
            glfwSetWindowMonitor(
                    window,
                    glfwGetPrimaryMonitor(),
                    0,
                    0,
                    vidmode.width(),
                    vidmode.height(),
                    vidmode.refreshRate());
            fullscreen = true;
            LOGGER.info("Fullscreen at " + vidmode.width() + "x" + vidmode.height());
        } else {
            int width = INITIAL_WIDTH;
            int height = INITIAL_HEIGHT;
            int x = (vidmode.width() - width) / 2;
            int y = (vidmode.height() - height) / 2;
            if (windowedGeometryValid) {
                width = windowedWidth;
                height = windowedHeight;
                x = windowedX;
                y = windowedY;
            }
            glfwSetWindowMonitor(window, NULL, x, y, width, height, GLFW_DONT_CARE);
            fullscreen = false;
            LOGGER.info("Windowed at " + width + "x" + height);
        }

        // Swap interval is per-context; some drivers reset it on monitor changes.
        glfwSwapInterval(1);
    }

    private void loop() {
        // Verify we're on the correct thread
        if (Thread.currentThread() != glThread) {
            throw new IllegalStateException(
                    "OpenGL calls must be made from the thread that created the context");
        }

        // Run rendering loop until user closes window
        while (!glfwWindowShouldClose(window)) {
            // Poll for window events
            glfwPollEvents();

            try {
                // Calculate delta time
                long currentTime = System.nanoTime();
                double deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = currentTime;

                // Process frame through context (handles time, textures, rendering)
                context.processFrame(deltaTime);

                // glGetError() intentionally disabled in the render loop; debug mode re-enables it.
                // This synchronous call causes CPU-GPU sync and kills performance (1-5ms per frame)
                // Using asynchronous debug callbacks instead (see setupDebugCallbacks)

                // Swap buffers
                glfwSwapBuffers(window);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during render", e);
                // Continue running unless it's a critical error
            }
        }
    }

    private void cleanup() {
        LOGGER.info("Shutting down Orrery application");

        // Shutdown application context (handles all component cleanup,
        // including FrameController and renderer disposal in correct order).
        if (context != null) {
            try {
                context.shutdown();
            } catch (Exception e) {
                LOGGER.severe("Error shutting down application context: " + e.getMessage());
            }
        }

        // Free window callbacks and destroy window
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }

        // Terminate GLFW and free error callback
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    public static void main(String[] args) {
        // Set system properties for better macOS compatibility
        System.setProperty("java.awt.headless", "true");

        new OrreryApplication().run();
    }
}
