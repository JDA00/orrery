package com.jda.orrery.app;

import com.jda.orrery.core.frames.BuiltInFrameKernel;
import com.jda.orrery.core.frames.FrameManager;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.time.TimeManager;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.domain.ephemeris.AnalyticalEphemerisProvider;
import com.jda.orrery.domain.ephemeris.EphemerisProvider;
import com.jda.orrery.domain.ephemeris.cache.EphemerisCache;
import com.jda.orrery.domain.ephemeris.cache.SimpleFrameCache;
import com.jda.orrery.graphics.core.DrawContext;
import com.jda.orrery.graphics.core.FrameController;
import com.jda.orrery.graphics.core.SceneController;
import com.jda.orrery.graphics.geometry.MeshLibrary;
import com.jda.orrery.graphics.illumination.IlluminationManager;
import com.jda.orrery.graphics.layers.DebugAxisLayer;
import com.jda.orrery.graphics.layers.DirectSolarSystemLayer;
import com.jda.orrery.graphics.pipeline.CelestialRenderer;
import com.jda.orrery.graphics.postfx.PostFXPipeline;
import com.jda.orrery.graphics.postfx.PostFXTarget;
import com.jda.orrery.graphics.resources.ResourceManager;
import com.jda.orrery.graphics.textures.TextureArraySystem;
import java.util.logging.Logger;

/**
 * Central application context managing component lifecycle and dependencies.
 *
 * Responsibilities: - Component initialization in correct order - Dependency graph configuration
 * - OpenGL resource management (must be called from GL thread) - Clean shutdown sequence
 *
 * Initialization Flow: 1. Constructor: Create components (CPU-side) 2. initializeGL():
 * Initialize OpenGL resources (GPU-side) 3. processFrame(): Main render loop 4. shutdown(): Clean
 * resource disposal
 *
 * Thread Safety: Constructor and shutdown are thread-safe. OpenGL methods (initializeGL,
 * processFrame) must be called from GL thread.
 */
public class ApplicationContext {

    private static final Logger LOGGER = Logging.logger(ApplicationContext.class);

    // Core components
    private final TimeManager timeManager;
    private final DrawContext drawContext;

    // Domain components
    private final SolarSystem solarSystem;
    private final EphemerisProvider ephemerisProvider;
    private final EphemerisCache ephemerisCache;

    // Graphics components
    private final FrameController frameController;
    private final SceneController sceneController;
    private final MeshLibrary meshLibrary;
    private final ResourceManager resourceManager;
    private final BuiltInFrameKernel builtInFrameKernel;
    private final FrameManager frameManager;
    private final IlluminationManager illuminationManager;

    // GL-dependent components (initialized in initializeGL())
    private TextureArraySystem textureArraySystem;
    private CelestialRenderer celestialRenderer;
    private PostFXTarget postFXTarget;
    private PostFXPipeline postFXPipeline;

    // Configuration
    private final ApplicationConfig config;

    // Lifecycle tracking
    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;

    /** Application configuration. */
    public static class ApplicationConfig {
        public final int windowWidth;
        public final int windowHeight;
        public final boolean vsync;
        public final boolean debug;
        public final long textureCacheMemoryMB;
        public final int textureLoaderThreads;
        public final double targetFPS;

        public ApplicationConfig(
                int windowWidth,
                int windowHeight,
                boolean vsync,
                boolean debug,
                long textureCacheMemoryMB,
                int textureLoaderThreads,
                double targetFPS) {
            this.windowWidth = windowWidth;
            this.windowHeight = windowHeight;
            this.vsync = vsync;
            this.debug = debug;
            this.textureCacheMemoryMB = textureCacheMemoryMB;
            this.textureLoaderThreads = textureLoaderThreads;
            this.targetFPS = targetFPS;
        }

        /** Create default configuration. */
        public static ApplicationConfig defaults() {
            return new ApplicationConfig(
                    1920,
                    1080, // Window size
                    true, // VSync
                    false, // Debug
                    512, // Texture cache memory (MB)
                    2, // Texture loader threads
                    60.0 // Target FPS
                    );
        }
    }

    /**
     * Create application context with configuration.
     *
     * @param config Application configuration
     */
    public ApplicationContext(ApplicationConfig config) {
        this.config = config;

        LOGGER.info(
                "Initializing ApplicationContext with config: "
                        + String.format(
                                "%dx%d, vsync=%b, debug=%b, textureMem=%dMB",
                                config.windowWidth,
                                config.windowHeight,
                                config.vsync,
                                config.debug,
                                config.textureCacheMemoryMB));

        // Initialize core components
        this.timeManager = new TimeManager();
        this.drawContext = new DrawContext();

        // Initialize domain components
        this.ephemerisProvider = new AnalyticalEphemerisProvider();
        this.ephemerisCache = new SimpleFrameCache();
        this.solarSystem = new SolarSystem(ephemerisProvider, ephemerisCache);

        // Initialize graphics components
        this.meshLibrary = new MeshLibrary();
        this.resourceManager = new ResourceManager();
        this.builtInFrameKernel = new BuiltInFrameKernel();
        this.frameManager = new FrameManager(builtInFrameKernel);
        this.illuminationManager = new IlluminationManager();

        // Initialize scene management
        this.sceneController = new SceneController(frameManager, illuminationManager);
        this.frameController =
                new FrameController(
                        drawContext, sceneController, timeManager, meshLibrary, frameManager);

        // GL-dependent components initialized in initializeGL()
        this.textureArraySystem = null;
        this.celestialRenderer = null;
        this.postFXTarget = null;
        this.postFXPipeline = null;

        // Configure component connections
        configureComponentGraph();

        initialized = true;
        LOGGER.info("ApplicationContext initialized successfully");
    }

    /** Create with default configuration. */
    public ApplicationContext() {
        this(ApplicationConfig.defaults());
    }

    /**
     * Configure connections between components. This establishes the runtime dependency graph after
     * all components are created.
     */
    private void configureComponentGraph() {
        // Set solar system first so it's available to all components
        drawContext.setSolarSystem(solarSystem);

        // Connect scene management to draw context
        drawContext.setSceneController(sceneController);

        // Scene layers will be registered during scene initialization

        LOGGER.fine("Component graph configured with solar system");
    }

    /** Initialize OpenGL resources. Must be called from thread with GL context. */
    public void initializeGL() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }

        LOGGER.info("Initializing OpenGL resources");

        // Detect GPU capabilities first
        com.jda.orrery.graphics.textures.GPUCapabilities.getGPUInfo();

        // Initialize texture system (requires GL context)
        this.textureArraySystem = new TextureArraySystem();

        // Initialize renderer now that texture system is ready
        this.celestialRenderer =
                new CelestialRenderer(
                        meshLibrary,
                        textureArraySystem,
                        resourceManager,
                        builtInFrameKernel,
                        frameManager);

        // Post-FX target + pipeline. Sized from config; FrameController.init() corrects
        // to the real framebuffer size before the first frame.
        this.postFXTarget = new PostFXTarget(config.windowWidth, config.windowHeight, 4);
        this.postFXPipeline = new PostFXPipeline(postFXTarget, illuminationManager);
        this.postFXPipeline.initialize();

        frameController.setPostFXTarget(postFXTarget);
        sceneController.attachPostFX(postFXTarget, postFXPipeline);

        // Compose scene: create layers and register them with the scene controller.
        // Done here (not in FrameController) so composition lives in one place.
        DirectSolarSystemLayer solarSystemLayer =
                new DirectSolarSystemLayer(solarSystem, celestialRenderer);
        solarSystemLayer.init(drawContext);
        sceneController.addLayer(solarSystemLayer);

        DebugAxisLayer debugAxisLayer = new DebugAxisLayer();
        debugAxisLayer.setAxisLength(200.0f);
        debugAxisLayer.setEnabled(false);
        sceneController.addLayer(debugAxisLayer);

        LOGGER.info("OpenGL resources initialized");
    }

    /**
     * Process one frame. Must be called from thread with GL context.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public void processFrame(double deltaTime) {
        if (shutdown) {
            return;
        }

        // Single time authority
        // Time advancement happens ONLY in FrameController.processFrame()
        // This prevents double advancement and time drift issues

        // Process texture system main thread tasks
        // This is critical - textures load async but must upload on main thread
        try {
            if (textureArraySystem != null) {
                textureArraySystem.update((float) deltaTime);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to update texture system: " + e.getMessage());
        }

        // Update and render frame (includes time advancement)
        frameController.processFrame(deltaTime);
    }

    /**
     * Handle window resize.
     *
     * @param width New window width
     * @param height New window height
     */
    public void onResize(int width, int height) {
        // Delegate to FrameController which manages the view
        frameController.framebufferSizeCallback(0, width, height);
    }

    /**
     * Shutdown and cleanup all resources. Must be called on application exit. Disposes components
     * in reverse dependency order: scene (frameController → sceneController → layers) first, then
     * the GL resources they referenced, then supporting systems.
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }

        shutdown = true;
        LOGGER.info("Shutting down ApplicationContext");

        // Scene teardown: layers dispose before the renderer they reference.
        if (frameController != null) {
            frameController.dispose();
        }

        // GL resource disposal in reverse initialization order.
        if (postFXPipeline != null) {
            postFXPipeline.dispose();
        }
        if (postFXTarget != null) {
            postFXTarget.dispose();
        }
        if (celestialRenderer != null) {
            celestialRenderer.dispose();
        }
        if (textureArraySystem != null) {
            textureArraySystem.shutdown();
        }
        if (meshLibrary != null) {
            meshLibrary.dispose();
        }
        if (resourceManager != null) {
            resourceManager.dispose();
        }

        LOGGER.info("ApplicationContext shutdown complete");
    }

    // Component accessors

    public TimeManager getTimeManager() {
        return timeManager;
    }

    public DrawContext getDrawContext() {
        return drawContext;
    }

    public SolarSystem getSolarSystem() {
        return solarSystem;
    }

    public FrameController getFrameController() {
        return frameController;
    }

    public SceneController getSceneController() {
        return sceneController;
    }

    public ApplicationConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized && !shutdown;
    }
}
