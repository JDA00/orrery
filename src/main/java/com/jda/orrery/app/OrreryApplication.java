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

    private long window;
    private ApplicationContext context;
    private FrameController frameController;
    private Thread glThread;
    private long lastFrameTime;

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

        // Create window
        window = glfwCreateWindow(INITIAL_WIDTH, INITIAL_HEIGHT, WINDOW_TITLE, NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        // Setup callbacks
        setupCallbacks();

        // Center window on screen
        centerWindow();

        // Make OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync to cap at monitor refresh rate
        glfwSwapInterval(1); // VSync enabled

        // Make window visible
        glfwShowWindow(window);

        // This is critical for LWJGL's interoperation with GLFW OpenGL context
        GL.createCapabilities();

        // Setup debug callbacks if available
        if (DEBUG_MODE) {
            setupDebugCallbacks();
        }

        // Log GL capabilities (includes version, vendor, renderer, GLSL version, and limits)
        com.jda.orrery.graphics.core.GLUtilities.logGLCapabilities();

        // Create application context with configuration
        ApplicationContext.ApplicationConfig config =
                new ApplicationContext.ApplicationConfig(
                        INITIAL_WIDTH,
                        INITIAL_HEIGHT,
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

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2);
            }
        }
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
