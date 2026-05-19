package com.jda.orrery.graphics.core;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;

import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.graphics.api.View;
import com.jda.orrery.graphics.illumination.IlluminationManager;
import com.jda.orrery.graphics.resources.Shader;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draw context for LWJGL version. No longer needs GL object parameter since LWJGL uses static
 * methods.
 */
public class DrawContext {
    protected View view;
    protected SceneController sceneController;

    // Viewport
    protected int viewportX;
    protected int viewportY;
    protected int viewportWidth;
    protected int viewportHeight;

    // Timing
    protected long frameTimeStamp;
    protected double deltaTime; // Time since last frame in seconds

    // Pre-allocated; reused each frame.
    private final Matrix4f tempViewMatrix = new Matrix4f();
    private final Matrix4f tempProjMatrix = new Matrix4f();
    protected FrameStatistics frameStatistics;
    protected TimeContext timeContext;

    // Render state
    protected boolean pickingMode;
    // Space is black. glClear writes are subject to GL_FRAMEBUFFER_SRGB, so any
    // non-zero channel here is interpreted as linear light and encoded on write.
    protected Vector4f clearColor = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    // Lighting
    protected IlluminationManager illuminationManager;
    protected Shader currentShader;

    // Solar system reference (for lighting)
    protected SolarSystem solarSystem;

    // Camera tracking: the body the camera follows this frame.
    protected CelestialBody trackedBody;

    public DrawContext() {
        this.frameStatistics = new FrameStatistics();

        // Configure OpenGL defaults
        configureDefaultState();
    }

    protected void configureDefaultState() {
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        // Enable back face culling
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Set clear color
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Fragment shader outputs linear light values; the framebuffer encodes to sRGB
        // at write time. Alpha blending for rings then happens correctly in linear space.
        glEnable(GL_FRAMEBUFFER_SRGB);
    }

    /** Begin a rendering frame */
    public void beginFrame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        frameStatistics.beginFrame();
    }

    /** End a rendering frame */
    public void endFrame() {
        frameStatistics.endFrame();

        // Log performance periodically (every 600 frames = ~10 seconds at 60 FPS)
        if (frameStatistics.getFrameCount() % 600 == 0) {
            double fps = frameStatistics.getFramesPerSecond();
            double avgFrameTime = frameStatistics.getAverageFrameTime();
            double minFrameTime = frameStatistics.getMinFrameTime();
            double maxFrameTime = frameStatistics.getMaxFrameTime();
            double stdDev = frameStatistics.getFrameTimeStdDev();
            int spikes16 = frameStatistics.getSpikesOver16ms();
            int spikes33 = frameStatistics.getSpikesOver33ms();
            double timeSinceReset = frameStatistics.getTimeSinceReset();

            // Main performance summary - use AVERAGE frame time for consistency
            getLogger()
                    .info(
                            String.format(
                                    "Performance: %.1f FPS (avg: %.1fms, min: %.1fms, max: %.1fms, σ: %.1fms)",
                                    fps, avgFrameTime, minFrameTime, maxFrameTime, stdDev));

            // Report spikes if any occurred
            if (spikes16 > 0) {
                double spike16Percent = (spikes16 * 100.0) / frameStatistics.getFrameCount();
                double spike33Percent = (spikes33 * 100.0) / frameStatistics.getFrameCount();
                getLogger()
                        .info(
                                String.format(
                                        "Frame spikes: %d (%.1f%%) >16ms, %d (%.1f%%) >33ms over %.1fs",
                                        spikes16,
                                        spike16Percent,
                                        spikes33,
                                        spike33Percent,
                                        timeSinceReset));
            }

            // Warning based on AVERAGE performance, not last frame
            if (avgFrameTime > 20.0) {
                getLogger()
                        .warning(
                                String.format(
                                        "Average performance below 50 FPS (%.1fms avg frame time)",
                                        avgFrameTime));
            }

            // Reset statistics after reporting for fresh data
            frameStatistics.reset();
        }
    }

    // Getters and setters

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public SceneController getSceneController() {
        return sceneController;
    }

    public void setSceneController(SceneController sceneController) {
        this.sceneController = sceneController;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public void setViewportWidth(int viewportWidth) {
        this.viewportWidth = viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportHeight(int viewportHeight) {
        this.viewportHeight = viewportHeight;
    }

    public long getFrameTimeStamp() {
        return frameTimeStamp;
    }

    public void setFrameTimeStamp(long frameTimeStamp) {
        this.frameTimeStamp = frameTimeStamp;
    }

    public double getDeltaTime() {
        return deltaTime;
    }

    public void setDeltaTime(double deltaTime) {
        this.deltaTime = deltaTime;
    }

    /**
     * Get pre-allocated view matrix. Caller should not modify this matrix.
     *
     * @return Pre-allocated view matrix
     */
    public Matrix4f getViewMatrixDirect() {
        if (view != null) {
            view.fillViewMatrix(tempViewMatrix);
        }
        return tempViewMatrix;
    }

    /**
     * Get pre-allocated projection matrix. Caller should not modify this matrix.
     *
     * @return Pre-allocated projection matrix
     */
    public Matrix4f getProjectionMatrixDirect() {
        if (view != null) {
            view.fillProjectionMatrix(tempProjMatrix);
        }
        return tempProjMatrix;
    }

    public boolean isPickingMode() {
        return pickingMode;
    }

    public void setPickingMode(boolean pickingMode) {
        this.pickingMode = pickingMode;
    }

    public TimeContext getTimeContext() {
        return timeContext;
    }

    public void setTimeContext(TimeContext timeContext) {
        this.timeContext = timeContext;
    }

    public IlluminationManager getIlluminationManager() {
        return illuminationManager;
    }

    public void setIlluminationManager(IlluminationManager illuminationManager) {
        this.illuminationManager = illuminationManager;
    }

    public Shader getCurrentShader() {
        return currentShader;
    }

    public void setCurrentShader(Shader shader) {
        this.currentShader = shader;
    }

    public SolarSystem getSolarSystem() {
        return solarSystem;
    }

    public void setSolarSystem(SolarSystem solarSystem) {
        this.solarSystem = solarSystem;
    }

    /**
     * Get the celestial body that the camera should track.
     *
     * @return The tracked body, or null if no tracking
     */
    public CelestialBody getTrackedBody() {
        return trackedBody;
    }

    /**
     * Set the celestial body for the camera to track.
     *
     * @param trackedBody The body to track, or null to stop tracking
     */
    public void setTrackedBody(CelestialBody trackedBody) {
        this.trackedBody = trackedBody;
    }

    private static java.util.logging.Logger getLogger() {
        return com.jda.orrery.core.logging.Logging.logger(DrawContext.class);
    }
}
