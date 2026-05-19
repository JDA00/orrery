package com.jda.orrery.graphics.textures;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL43.*;

import com.jda.orrery.core.logging.Logging;
import java.util.logging.Logger;

/**
 * Texture-array facade for celestial body textures. Sits behind
 * {@link HybridTextureBackend}, exposes a single entry point for the renderer to request
 * textures, and validates GPU capabilities and memory budget at startup.
 */
public final class TextureArraySystem {
    private static final Logger LOGGER = Logging.logger(TextureArraySystem.class);

    public static final int PDS_TILE_SIZE = 256; // Standard tile size
    public static final int MAX_VIRTUAL_SIZE = 32768; // 32K max dimension
    public static final int MAX_LAYERS = 256;

    // Scientific data layer indices
    public static final class Layers {
        public static final int VISUAL_RGB = 0; // Human-visible
        public static final int VISUAL_NIGHT = 1; // Night lights/aurora
        public static final int INFRARED_THERMAL = 2; // Temperature
        public static final int INFRARED_NEAR = 3; // Composition
        public static final int RADAR_BACKSCATTER = 4; // Surface roughness
        public static final int ELEVATION = 5; // Height map
        public static final int GRAVITY_ANOMALY = 6; // Science data
        public static final int MAGNETIC_FIELD = 7; // Science data

        // Derived data products
        public static final int MINERALOGY = 10; // From spectral analysis
        public static final int WATER_ICE = 11; // From multiple sources
        public static final int ATMOSPHERE = 12; // Density/composition

        // Temporal sequences (Mars seasons, Io volcanism)
        public static final int TEMPORAL_START = 20;
        public static final int TEMPORAL_COUNT = 12; // Monthly for one year
    }

    // Texture backend implementation
    private final TextureBackend backend;

    /**
     * Create and initialize the texture system. Requires an active OpenGL context; call during
     * application GL init phase. Fails fast if critical resources are unavailable.
     */
    public TextureArraySystem() {
        LOGGER.info("Initializing HybridTextureBackend");
        backend = new HybridTextureBackend();
        backend.initialize();

        performStartupValidation();

        LOGGER.info("Planetary texture system initialized: " + backend.getClass().getSimpleName());
    }

    /** Validate GPU capabilities and backend state at startup. */
    private void performStartupValidation() {
        LOGGER.info("Performing startup validation...");

        // Verify OpenGL state
        int maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        int maxArrayLayers = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS);
        int maxTextureUnits = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);

        LOGGER.info(
                String.format(
                        "GPU Capabilities: MaxSize=%d, MaxLayers=%d, MaxUnits=%d",
                        maxTextureSize, maxArrayLayers, maxTextureUnits));

        if (maxTextureSize < 2048) {
            throw new IllegalStateException(
                    "GPU does not meet minimum requirements (2048x2048 textures)");
        }

        if (maxArrayLayers < 32) {
            throw new IllegalStateException("GPU does not support enough array layers (need 32+)");
        }

        // Verify backend initialization
        if (!backend.validate()) {
            throw new IllegalStateException("Texture backend validation failed");
        }

        // Memory check
        long availableMemory = backend.getAvailableMemory();
        if (availableMemory < GPUCapabilities.GPUTier.LOW.targetMemory) {
            throw new IllegalStateException("Insufficient GPU memory: " + availableMemory);
        }

        LOGGER.info(
                "Validation complete. Available GPU memory: "
                        + (availableMemory / 1024 / 1024)
                        + " MB");
    }

    /**
     * Request texture data for a planet at specific quality. This is the main interface for the
     * renderer.
     *
     * @param planetId Planet identifier
     * @param viewContext Current camera/time/quality context
     * @return Texture handle for binding
     */
    public TextureHandle requestTexture(String planetId, ViewContext viewContext) {
        return backend.requestTexture(planetId, viewContext);
    }

    /** Get backend type for shader selection. */
    public BackendType getBackendType() {
        return backend.getType();
    }

    /** Clean shutdown. */
    /**
     * Update the texture system. Should be called each frame from the render thread. Uses
     * polymorphic dispatch - no instanceof checks needed.
     *
     * @param deltaTime Time since last update in seconds
     */
    public void update(float deltaTime) {
        if (backend != null) {
            backend.update(deltaTime);
        }
    }

    public void shutdown() {
        if (backend != null) {
            backend.dispose();
        }
    }

    /** Backend types for shader specialization. */
    public enum BackendType {
        SPARSE, // GL 4.3+ sparse arrays
        IMMUTABLE, // GL 4.2+ immutable storage
        LEGACY // GL 3.3+ traditional arrays
    }

    /**
     * Interface for different texture backends. Allows graceful degradation across GPU generations.
     */
    interface TextureBackend {
        void initialize();

        boolean validate();

        long getAvailableMemory();

        TextureHandle requestTexture(String planetId, ViewContext context);

        BackendType getType();

        void update(float deltaTime); // Polymorphic update for main thread tasks

        void dispose();
    }

    /** View context for LOD decisions. Passed to backend for quality selection. */
    public static class ViewContext {
        // Mutable fields for render-loop reuse (JOML/LWJGL convention).
        public double cameraDistance; // In AU - changes per body
        public double pixelsPerDegree; // Screen resolution - constant per frame
        public double timeJD; // For temporal data - constant per frame
        public boolean scientificMode; // Prioritize accuracy - constant per session
        public int requiredLayers; // Bit flags for layers - constant per frame
        public double bodyRadius; // Body radius in km - changes per body
        public double fovDegrees; // Camera field of view - constant per frame

        // Full constructor with all fields
        public ViewContext(
                double cameraDistance,
                double pixelsPerDegree,
                double timeJD,
                boolean scientificMode,
                int requiredLayers,
                double bodyRadius,
                double fovDegrees) {
            this.cameraDistance = cameraDistance;
            this.pixelsPerDegree = pixelsPerDegree;
            this.timeJD = timeJD;
            this.scientificMode = scientificMode;
            this.requiredLayers = requiredLayers;
            this.bodyRadius = bodyRadius;
            this.fovDegrees = fovDegrees;
        }

        // Update methods for render loop (mirrors JOML's set() methods).
        /** Update frame-constant values once per frame. */
        public void updateFrame(double pixelsPerDegree, double timeJD, double fovDegrees) {
            this.pixelsPerDegree = pixelsPerDegree;
            this.timeJD = timeJD;
            this.fovDegrees = fovDegrees;
        }

        /** Update body-specific values per body. */
        public void updateBody(double cameraDistance, double bodyRadius) {
            this.cameraDistance = cameraDistance;
            this.bodyRadius = bodyRadius;
        }

        // Backward compatibility constructor
        public ViewContext(
                double cameraDistance,
                double pixelsPerDegree,
                double timeJD,
                boolean scientificMode,
                int requiredLayers) {
            this(
                    cameraDistance,
                    pixelsPerDegree,
                    timeJD,
                    scientificMode,
                    requiredLayers,
                    0.0,
                    60.0);
        }

        // No-args constructor for pre-allocation pattern
        public ViewContext() {
            // Default values - will be updated before use
            this(0.0, 1.0, 0.0, false, 1, 0.0, 60.0);
        }
    }

    /**
     * Handle to a texture for renderer use. Always valid (never null) with explicit state tracking
     * so progressive loading avoids NPEs.
     */
    public static final class TextureHandle {
        public enum State {
            NOT_LOADED, // Texture not yet loaded, use fallback color
            LOADING, // Currently loading in background
            READY, // Texture loaded and ready to use
            ERROR // Failed to load, permanent fallback
        }

        public final State state;
        public final int textureId;
        public final int arrayLayer;
        public final BackendType backendType;
        public final float scaleU; // Texture coordinate scale for variable resolution
        public final float scaleV;
        public final org.joml.Vector3f fallbackColor;
        public final String bodyId;
        public final boolean usesCompressedFormat; // sRGB BC3/BC7

        // Original constructor for backward compatibility - assumes READY state
        public TextureHandle(int textureId, int arrayLayer, BackendType backendType) {
            this(textureId, arrayLayer, backendType, 1.0f, 1.0f);
        }

        // Full constructor with scale factors - assumes READY state
        public TextureHandle(
                int textureId,
                int arrayLayer,
                BackendType backendType,
                float scaleU,
                float scaleV) {
            this(textureId, arrayLayer, backendType, scaleU, scaleV, "");
        }

        // Full constructor with body ID
        public TextureHandle(
                int textureId,
                int arrayLayer,
                BackendType backendType,
                float scaleU,
                float scaleV,
                String bodyId) {
            this(textureId, arrayLayer, backendType, scaleU, scaleV, bodyId, false);
        }

        // Full constructor with compression format flag
        public TextureHandle(
                int textureId,
                int arrayLayer,
                BackendType backendType,
                float scaleU,
                float scaleV,
                String bodyId,
                boolean usesCompressedFormat) {
            this.state = State.READY;
            this.textureId = textureId;
            this.arrayLayer = arrayLayer;
            this.backendType = backendType;
            this.scaleU = scaleU;
            this.scaleV = scaleV;
            this.fallbackColor = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
            this.bodyId = bodyId != null ? bodyId : "";
            this.usesCompressedFormat = usesCompressedFormat;
        }

        // Constructor for loading/not-loaded states
        public TextureHandle(
                State state,
                String bodyId,
                org.joml.Vector3f fallbackColor,
                BackendType backendType) {
            this(state, bodyId, fallbackColor, backendType, false);
        }

        // Constructor for loading/not-loaded states with compression flag
        public TextureHandle(
                State state,
                String bodyId,
                org.joml.Vector3f fallbackColor,
                BackendType backendType,
                boolean usesCompressedFormat) {
            this.state = state;
            this.textureId = 0; // No texture yet
            this.arrayLayer = -1;
            this.backendType = backendType;
            this.scaleU = 1.0f;
            this.scaleV = 1.0f;
            this.fallbackColor = fallbackColor;
            this.bodyId = bodyId;
            this.usesCompressedFormat = usesCompressedFormat;
        }

        // Constructor for placeholder with reserved slot
        public TextureHandle(
                State state,
                String bodyId,
                org.joml.Vector3f fallbackColor,
                int reservedTextureId,
                int reservedLayer,
                BackendType backendType,
                float scaleU,
                float scaleV) {
            this(
                    state,
                    bodyId,
                    fallbackColor,
                    reservedTextureId,
                    reservedLayer,
                    backendType,
                    scaleU,
                    scaleV,
                    false);
        }

        // Constructor for placeholder with reserved slot and compression flag
        public TextureHandle(
                State state,
                String bodyId,
                org.joml.Vector3f fallbackColor,
                int reservedTextureId,
                int reservedLayer,
                BackendType backendType,
                float scaleU,
                float scaleV,
                boolean usesCompressedFormat) {
            this.state = state;
            this.textureId = reservedTextureId;
            this.arrayLayer = reservedLayer;
            this.backendType = backendType;
            this.scaleU = scaleU;
            this.scaleV = scaleV;
            this.fallbackColor = fallbackColor;
            this.bodyId = bodyId;
            this.usesCompressedFormat = usesCompressedFormat;
        }

        public boolean isReady() {
            return textureId > 0 && arrayLayer >= 0;
        }

        public boolean hasError() {
            return state == State.ERROR;
        }

        // Getters for compatibility with existing code
        public State getState() {
            return state;
        }

        public int getTextureId() {
            return textureId;
        }

        public int getArrayLayer() {
            return arrayLayer;
        }

        public BackendType getBackendType() {
            return backendType;
        }

        public float getScaleU() {
            return scaleU;
        }

        public float getScaleV() {
            return scaleV;
        }

        public org.joml.Vector3f getFallbackColor() {
            return fallbackColor;
        }

        public String getBodyId() {
            return bodyId;
        }

        public boolean usesCompressedFormat() {
            return usesCompressedFormat;
        }
    }
}
