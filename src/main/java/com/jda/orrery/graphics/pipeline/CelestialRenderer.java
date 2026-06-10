package com.jda.orrery.graphics.pipeline;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;

import com.jda.orrery.core.frames.*;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.math.Vec3d;
import com.jda.orrery.core.time.TimeContext;
import com.jda.orrery.domain.astronomy.CelestialBody;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.domain.astronomy.catalog.BodyData;
import com.jda.orrery.domain.astronomy.catalog.CelestialCatalog;
import com.jda.orrery.graphics.celestial.ScaleManager;
import com.jda.orrery.graphics.core.DrawContext;
import com.jda.orrery.graphics.geometry.MeshLibrary;
import com.jda.orrery.graphics.geometry.MeshTopology;
import com.jda.orrery.graphics.geometry.RingMesh;
import com.jda.orrery.graphics.geometry.SphereMesh;
import com.jda.orrery.graphics.materials.MaterialCatalog;
import com.jda.orrery.graphics.materials.MaterialProperties;
import com.jda.orrery.graphics.resources.ResourceManager;
import com.jda.orrery.graphics.resources.Shader;
import com.jda.orrery.graphics.textures.TextureArraySystem;
import com.jda.orrery.graphics.ubo.CelestialUBO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;

/**
 * Renders the Sun, planets, and Moon each frame via the unified UBO shader. Pre-allocates
 * per-frame work vectors and matrices so the render loop avoids heap allocation.
 */
public class CelestialRenderer {
    private static final Logger LOGGER = Logging.logger(CelestialRenderer.class);

    // Saturnshine day-side reflectance fallback if the ring-bearing body has no
    // catalog material (unreachable for Saturn; neutral gray keeps the rings'
    // umbra fill plausible rather than black).
    private static final Vector3f SATURNSHINE_COLOR_FALLBACK = new Vector3f(0.5f, 0.5f, 0.5f);

    // Dependencies & resources

    private final MeshLibrary meshLibrary;
    private final TextureArraySystem textureSystem;
    private final ResourceManager resourceManager;
    private final BuiltInFrameKernel builtInFrameKernel;
    private final FrameManager frameManager;

    private Shader uboShader;
    private CelestialUBO celestialUbo;
    private SphereMesh sphereMesh;
    private RingMesh saturnRings;

    // Frame state — updated once per frame

    private static class FrameState {
        // Matrices from camera (set once per frame)
        final Matrix4f viewMatrix = new Matrix4f();
        final Matrix4f projMatrix = new Matrix4f();
        final Matrix4d viewMatrixDouble = new Matrix4d();
        final Matrix4d projMatrixDouble = new Matrix4d();

        // Sun position for lighting (calculated once per frame)
        final Vector3f sunPositionView = new Vector3f();
        final Vector4d sunPosWorld = new Vector4d();
        final Vector4d sunPosView = new Vector4d();

        // Camera state
        Vec3d cameraWorldPos = null;

        // Time state
        double julianDate = 0.0;
        boolean isPaused = false;

        // View parameters for LOD
        double screenHeight = 0.0;
        double fovDegrees = 0.0;
        double pixelsPerDegree = 0.0;

        // Static transforms (never change)
        Matrix3d j2000ToRender = null;
    }

    private final FrameState frameState = new FrameState();

    // Transform work objects — used while building per-body transforms

    private static class TransformWork {
        // Position work vectors
        final Vector3d workPosition = new Vector3d();
        final Vector3d workVelocity = new Vector3d();
        final Vector3d scaledPosition = new Vector3d();

        // Satellite-specific work vectors (pre-allocated, reused per frame)
        final Vector3d satelliteRelativeOffset = new Vector3d();
        final Vector3d satelliteOffsetTransformed = new Vector3d();
        final Vector3d satelliteVelocityWork = new Vector3d(); // For transform API

        // Transform matrices
        final Matrix4d modelMatrixDouble = new Matrix4d();
        final Matrix4f modelMatrix = new Matrix4f();

        // Rotation work
        final Matrix4d rotation4d = new Matrix4d();
        final Matrix3d workRotation = new Matrix3d();

        // Pre-allocated transform for satellite offset
        final Matrix3d offsetTransform = new Matrix3d();
    }

    private final TransformWork transformWork = new TransformWork();

    // Body position cache — pre-allocated, reset each frame

    /**
     * Zero-allocation cache for body positions within a frame. Uses enum-based indexing for O(1)
     * lookups instead of string comparisons.
     */
    private static class BodyPositionCache {
        // Direct array indexing by enum ordinal (max 12 known bodies + buffer)
        private final Vector3d[] scaledPositions = new Vector3d[16];
        private final boolean[] hasPosition = new boolean[16];

        BodyPositionCache() {
            for (int i = 0; i < scaledPositions.length; i++) {
                scaledPositions[i] = new Vector3d();
            }
        }

        void reset() {
            // Just clear flags, no allocations
            Arrays.fill(hasPosition, false);
        }

        void store(CelestialBodyId bodyEnum, Vector3d scaledPos) {
            if (bodyEnum != CelestialBodyId.UNKNOWN) {
                int index = bodyEnum.ordinal();
                if (index < scaledPositions.length) {
                    scaledPositions[index].set(scaledPos);
                    hasPosition[index] = true;
                }
            }
        }

        Vector3d get(CelestialBodyId bodyEnum) {
            if (bodyEnum != CelestialBodyId.UNKNOWN) {
                int index = bodyEnum.ordinal();
                if (index < hasPosition.length && hasPosition[index]) {
                    return scaledPositions[index];
                }
            }
            return null;
        }
    }

    private final BodyPositionCache positionCache = new BodyPositionCache();

    // Render work objects — used while uploading per-body GPU state

    private static class RenderWork {
        // Combined matrices for GPU
        final Matrix4f modelViewMatrix = new Matrix4f();
        final Matrix4f mvpMatrix = new Matrix4f();
        final Matrix3f normalMatrix = new Matrix3f();

        // Double precision work matrices (for updateGPUState)
        final Matrix4d modelViewDouble = new Matrix4d();
        final Matrix4d mvpDouble = new Matrix4d();
        final Matrix4d modelMatrixDouble = new Matrix4d();

        // Material/texture work
        final Vector3f fallbackColor = new Vector3f(0.5f, 0.5f, 0.5f);
        final Vector2f texCoordScale = new Vector2f(1.0f, 1.0f);

        // Ring material work vectors (reused to avoid allocations)
        final Vector3f ringAlbedo = new Vector3f();
        final Vector3f ringEmission = new Vector3f();

        // Planet position for ring shadow calculation
        final Vector3d planetPosWorld = new Vector3d();
        final Vector3d planetPosViewDouble = new Vector3d();
        final Vector3f planetPosView = new Vector3f();

        // Spin-axis scratch vectors
        // J2000 -> render frame -> view frame transformation chain.
        final Vector3d spinAxisJ2000 = new Vector3d();
        final Vector3d spinAxisRender = new Vector3d();
        final Vector3f planetAxisView = new Vector3f();

        // Body geometry for UBO upload (eq, polar, ringInner, ringOuter) — visual-space.
        // Computed in buildTransform, consumed in updateGPUState/renderRings.
        final Vector4f bodyGeometry = new Vector4f();
    }

    private final RenderWork renderWork = new RenderWork();

    // Reusable collections & contexts

    private final List<CelestialBody> bodiesList = new ArrayList<>(30);
    private boolean bodiesCollected = false;
    private final TextureArraySystem.ViewContext viewContext = new TextureArraySystem.ViewContext();

    // Statistics
    private long frameCount = 0;
    private int bodiesRendered = 0;

    // Performance profiling
    private long prepareTime = 0;
    private long transformTime = 0;
    private long gpuTime = 0;
    private long drawTime = 0;

    // Initialization

    public CelestialRenderer(
            MeshLibrary meshLibrary,
            TextureArraySystem textureSystem,
            ResourceManager resourceManager,
            BuiltInFrameKernel builtInFrameKernel,
            FrameManager frameManager) {
        this.meshLibrary = meshLibrary;
        this.textureSystem = textureSystem;
        this.resourceManager = resourceManager;
        this.builtInFrameKernel = builtInFrameKernel;
        this.frameManager = frameManager;
        LOGGER.info("CelestialRenderer initialized");
    }

    public void initializeDirectRendering() {
        // Load UBO shader
        this.uboShader = resourceManager.getShader("celestial_unified_ubo");

        if (this.uboShader == null || !this.uboShader.isValid()) {
            throw new IllegalStateException(
                    "FATAL: UBO shader 'celestial_unified_ubo' is required for precision.");
        }

        // Create UBO
        this.celestialUbo = new CelestialUBO(0);
        this.celestialUbo.linkToShader(this.uboShader.getProgramId(), "CelestialData");

        // Get sphere mesh
        this.sphereMesh = meshLibrary.get(MeshTopology.UV_SPHERE, SphereMesh.Quality.HIGH);

        // Create Saturn's ring mesh from BodyData (single source of truth).
        // The catalog values (currently 1.1 / 2.2) are an artistic compression of
        // the real D-ring/A-ring extent (1.105 / 2.27) for visual clarity.
        BodyData saturnData = CelestialCatalog.SATURN;
        if (saturnData == null || !saturnData.hasRings()) {
            throw new IllegalStateException("Saturn catalog entry must define ring extent");
        }
        this.saturnRings =
                new RingMesh(
                        saturnData.ringInnerRadius().floatValue(),
                        saturnData.ringOuterRadius().floatValue(),
                        128);

        // Cache static transforms
        frameState.j2000ToRender =
                builtInFrameKernel.getStaticTransform(FrameNames.J2000, FrameNames.OPENGL_RENDER);

        LOGGER.info("CelestialRenderer initialized - Ready for rendering");
    }

    // Main render pipeline

    /**
     * Main entry point - render the solar system. Clear, linear flow: prepare → collect → sort →
     * render
     */
    public void renderSolarSystem(SolarSystem solarSystem, DrawContext dc) {
        if (solarSystem == null || dc == null) return;

        long frameStart = System.nanoTime();

        // Prepare frame state (once per frame).
        long t0 = System.nanoTime();
        prepareFrame(dc, solarSystem);
        prepareTime = System.nanoTime() - t0;

        // Collect and sort bodies once — SolarSystem is immutable.
        if (!bodiesCollected) {
            collectBodies(solarSystem);
            sortBodies();
            bodiesCollected = true;
        }

        // Reset position cache for this frame.
        positionCache.reset();

        // Render in two passes for proper transparency (opaque then ring).
        bodiesRendered = 0;
        transformTime = 0;
        gpuTime = 0;
        drawTime = 0;

        // Pass 1: Render opaque objects (planets and sun)
        // Pass 2: Render transparent objects (rings only)
        // This ensures correct alpha blending without any allocations
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0, n = bodiesList.size(); i < n; i++) {
                CelestialBody body = bodiesList.get(i);
                if (pass == 0) {
                    renderBody(body, dc, false);
                } else {
                    renderBodyRings(body, dc);
                }
            }
        }

        // Update statistics
        frameCount++;
    }

    /** STEP 1: Prepare all frame-constant state. */
    private void prepareFrame(DrawContext dc, SolarSystem solarSystem) {
        // Time state
        TimeContext timeContext = dc.getTimeContext();
        frameState.julianDate = timeContext != null ? timeContext.getJulianDateTDB() : 0.0;
        frameState.isPaused = timeContext != null && timeContext.isPaused();

        // Camera state - cache this ONCE
        frameState.cameraWorldPos = dc.getView().getCameraWorldPosition();

        // View matrices - keep in double precision only
        // Float conversion delayed until GPU upload in updateGPUState()
        dc.getView().fillViewMatrixDouble(frameState.viewMatrixDouble);
        dc.getView().fillProjectionMatrixDouble(frameState.projMatrixDouble);

        // Calculate sun position and transform to view space
        calculateSunPosition(solarSystem.getSun(), dc);
        frameState.viewMatrixDouble.transform(frameState.sunPosWorld, frameState.sunPosView);
        frameState.sunPositionView.set(
                (float) frameState.sunPosView.x,
                (float) frameState.sunPosView.y,
                (float) frameState.sunPosView.z);

        // View parameters for LOD
        frameState.screenHeight = dc.getViewportHeight();
        frameState.fovDegrees = dc.getView().getFieldOfView();
        frameState.pixelsPerDegree = frameState.screenHeight / frameState.fovDegrees;

        // Update ViewContext for texture LOD (once per frame)
        viewContext.updateFrame(
                frameState.pixelsPerDegree, frameState.julianDate, frameState.fovDegrees);
        viewContext.scientificMode = false;
        viewContext.requiredLayers = 1;
    }

    /**
     * Calculate sun position in world space (camera-relative). USES DOUBLE PRECISION for accuracy.
     */
    private void calculateSunPosition(CelestialBody sun, DrawContext dc) {
        if (sun == null) {
            frameState.sunPosWorld.set(0, 0, 0, 1.0);
            return;
        }

        // Get sun state (will be cached in ephemeris)
        FramedState sunState = sun.getState(dc.getTimeContext());
        if (sunState == null) {
            frameState.sunPosWorld.set(0, 0, 0, 1.0);
            return;
        }

        // Transform writes into the caller's matrix; double precision.
        frameManager.transformInto(
                sunState,
                FrameNames.OPENGL_RENDER,
                transformWork.workPosition,
                transformWork.workVelocity);

        // Scale sun position IN DOUBLE PRECISION
        ScaleManager.scaleBodyPositionInto(
                sun, transformWork.workPosition, transformWork.scaledPosition);

        // Apply camera-relative positioning IN DOUBLE PRECISION
        // Critical for avoiding jitter
        if (frameState.cameraWorldPos != null) {
            // Use Vec3d for double precision subtraction
            double camX = frameState.cameraWorldPos.x;
            double camY = frameState.cameraWorldPos.y;
            double camZ = frameState.cameraWorldPos.z;

            frameState.sunPosWorld.set(
                    transformWork.scaledPosition.x - camX,
                    transformWork.scaledPosition.y - camY,
                    transformWork.scaledPosition.z - camZ,
                    1.0);
        } else {
            frameState.sunPosWorld.set(
                    transformWork.scaledPosition.x,
                    transformWork.scaledPosition.y,
                    transformWork.scaledPosition.z,
                    1.0);
        }
    }

    /** STEP 2: Collect all bodies for rendering. */
    private void collectBodies(SolarSystem solarSystem) {
        bodiesList.clear();

        if (solarSystem.getSun() != null) {
            bodiesList.add(solarSystem.getSun());
        }
        bodiesList.addAll(solarSystem.getPlanets());
        bodiesList.addAll(solarSystem.getSatellites());
    }

    /**
     * Partition the body list so the Sun (emissive) renders last. Full depth sorting would
     * require expensive ephemeris calls.
     */
    private void sortBodies() {
        if (bodiesList.size() <= 1) return;

        // Just ensure sun renders last (it's emissive)
        CelestialBody sun = null;
        int sunIndex = -1;

        for (int i = 0; i < bodiesList.size(); i++) {
            // Fast enum lookup instead of string comparison
            CelestialBodyId bodyId = CelestialBodyId.fromString(bodiesList.get(i).getId());
            if (bodyId == CelestialBodyId.SUN) {
                sun = bodiesList.get(i);
                sunIndex = i;
                break;
            }
        }

        // Move sun to end if found
        if (sunIndex >= 0 && sunIndex < bodiesList.size() - 1) {
            bodiesList.remove(sunIndex);
            bodiesList.add(sun);
        }
    }

    /**
     * Reset cached body list. SolarSystem is currently static (bodies added only
     * at construction), so this is unused — provided for future dynamic body-set
     * changes (e.g., Galilean moons, Titan).
     */
    void invalidateBodyList() {
        bodiesCollected = false;
    }

    /**
     * STEP 4: Render a single body with clear sub-steps.
     *
     * @param renderRings if false, skip ring rendering for this pass
     */
    private void renderBody(CelestialBody body, DrawContext dc, boolean renderRings) {
        // Use the original ID; toLowerCase() would allocate.
        // The body ID should already be in the correct case
        String bodyId = body.getId();

        // Get enum once for this body - reuse throughout method
        CelestialBodyId bodyEnum = CelestialBodyId.fromString(bodyId);

        // 4.1: Get body state (ephemeris)
        FramedState bodyState = body.getState(dc.getTimeContext());
        if (bodyState == null) {
            return; // Skip body without state
        }

        // 4.2: Build transform matrix
        long t1 = System.nanoTime();
        Matrix4f modelMatrix = buildTransform(body, bodyState, dc);
        transformTime += (System.nanoTime() - t1);

        // 4.3: Calculate distance for LOD
        double distanceAU = calculateDistance(bodyState, frameState.cameraWorldPos);
        viewContext.updateBody(distanceAU, body.getRadius());

        // 4.4: Setup materials and texture
        MaterialProperties material = MaterialCatalog.getMaterial(bodyId);
        TextureArraySystem.TextureHandle texture =
                textureSystem.requestTexture(bodyId, viewContext);

        // 4.5: Update GPU state
        long t2 = System.nanoTime();
        updateGPUState(modelMatrix, material, texture, bodyEnum, dc);

        // 4.5a: Ring-shadow plumbing for ring-bearing bodies (Saturn).
        // For pass 0 (body draws) the body fragment shader needs to know:
        //  - The ring extent and planet radii (already in celestial.bodyGeometry).
        //  - The ring texture layer (for sampling ring opacity).
        //  - The body's spin axis and center in view space (for ray-plane intersect).
        //  - The atmospheric refraction (broadens the ring's penumbra cast on body).
        if (bodyEnum.hasRings()) {
            String ringId = bodyEnum.getRingTextureId();
            TextureArraySystem.TextureHandle ringTexture =
                    ringId != null ? textureSystem.requestTexture(ringId, viewContext) : null;
            int ringLayer =
                    (ringTexture != null && ringTexture.isReady())
                            ? ringTexture.getArrayLayer()
                            : -1;
            uboShader.setUniform("ringTextureLayer", ringLayer);
            uboShader.setUniform(
                    "atmosphericRefraction",
                    material != null ? material.atmosphericRefractionRad : 0.0f);
            uploadShadowUniforms(body.getId(), transformWork.scaledPosition);
        } else {
            uboShader.setUniform("ringTextureLayer", -1);
            uboShader.setUniform("atmosphericRefraction", 0.0f);
        }
        gpuTime += (System.nanoTime() - t2);

        // 4.6: Draw
        long t3 = System.nanoTime();
        sphereMesh.draw();
        drawTime += (System.nanoTime() - t3);

        // 4.7: Skip rings in first pass - they'll be rendered in second pass
        // This is handled by renderBodyRings() method

        bodiesRendered++;
    }

    /**
     * Render only the rings of a body (second pass for transparency). Zero-allocation method that
     * reuses existing state.
     */
    private void renderBodyRings(CelestialBody body, DrawContext dc) {
        String bodyId = body.getId();
        CelestialBodyId bodyEnum = CelestialBodyId.fromString(bodyId);

        // Only process bodies that have rings
        if (!bodyEnum.hasRings()) {
            return;
        }

        // Get cached body position and transform from first pass
        Vector3d bodyPos = positionCache.get(bodyEnum);
        if (bodyPos == null) {
            // Body wasn't rendered in first pass, need its state
            FramedState bodyState = body.getState(dc.getTimeContext());
            if (bodyState == null) return;

            // Build transform and cache position (same as first pass)
            Matrix4f modelMatrix = buildTransform(body, bodyState, dc);
            bodyPos = positionCache.get(bodyEnum);
            if (bodyPos == null) return;
        }

        // Build simple model matrix for ring position (reuse work matrices)
        double visualRadius = ScaleManager.getVisualRadius(body.getRadius(), body.getId());

        Matrix4f ringMatrix = transformWork.modelMatrix; // Reuse work matrix
        if (frameState.cameraWorldPos != null) {
            double relX = bodyPos.x - frameState.cameraWorldPos.x;
            double relY = bodyPos.y - frameState.cameraWorldPos.y;
            double relZ = bodyPos.z - frameState.cameraWorldPos.z;

            transformWork
                    .modelMatrixDouble
                    .identity()
                    .translate(relX, relY, relZ)
                    .scale(visualRadius);
        } else {
            transformWork
                    .modelMatrixDouble
                    .identity()
                    .translate(bodyPos.x, bodyPos.y, bodyPos.z)
                    .scale(visualRadius);
        }
        ringMatrix.set(transformWork.modelMatrixDouble);

        // Get ring texture
        String ringId = bodyEnum.getRingTextureId();
        if (ringId != null) {
            TextureArraySystem.TextureHandle ringTexture =
                    textureSystem.requestTexture(ringId, viewContext);
            if (ringTexture != null) {
                renderRings(body, ringMatrix, ringTexture, dc);
            }
        }
    }

    /** Calculate distance from camera to body. USES DOUBLE PRECISION throughout for accuracy. */
    private double calculateDistance(FramedState bodyState, Vec3d cameraPos) {
        Vec3d bodyPos = bodyState.getPosition();
        if (cameraPos == null) {
            // Use double precision for magnitude calculation
            double x = bodyPos.x;
            double y = bodyPos.y;
            double z = bodyPos.z;
            return Math.sqrt(x * x + y * y + z * z);
        }

        // Delta calculation - critical for LOD stability
        double dx = bodyPos.x - cameraPos.x;
        double dy = bodyPos.y - cameraPos.y;
        double dz = bodyPos.z - cameraPos.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Handle absolute position transformation for planets/sun. Separated for clarity and
     * reusability.
     */
    private void handleAbsolutePosition(CelestialBody body, FramedState state) {
        // Transform absolute position to render frame
        frameManager.transformInto(
                state,
                FrameNames.OPENGL_RENDER,
                transformWork.workPosition,
                transformWork.workVelocity);
        // Scale the position
        ScaleManager.scaleBodyPositionInto(
                body, transformWork.workPosition, transformWork.scaledPosition);
    }

    /** Build transform matrix */
    private Matrix4f buildTransform(CelestialBody body, FramedState state, DrawContext dc) {
        // Check if this is a satellite with relative coordinates
        if (body.isSatellite() && state.isRelative()) {
            // Get parent's ALREADY COMPUTED scaled position from cache
            CelestialBody parent = body.getParentOrNull();
            if (parent != null) {
                CelestialBodyId parentEnum = CelestialBodyId.fromString(parent.getId());
                Vector3d parentScaledPos = positionCache.get(parentEnum);

                if (parentScaledPos != null) {
                    // Get the relative offset directly from the state
                    Vec3d relativeOffset = state.getPosition(); // Already relative!

                    // Copy offset to work vector
                    transformWork.satelliteOffsetTransformed.set(
                            relativeOffset.x, relativeOffset.y, relativeOffset.z);

                    // Transform relative offset to render frame.
                    Matrix3d transform =
                            frameManager.getTransformMatrixInto(
                                    state.getFrame(), // Source frame (ECLIPJ2000)
                                    FrameNames.OPENGL_RENDER, // Target frame
                                    state.getET(),
                                    transformWork.offsetTransform // Pre-allocated matrix
                                    );

                    if (transform != null) {
                        transform.transform(transformWork.satelliteOffsetTransformed);
                    }

                    // Get distance multiplier for this satellite
                    double distanceMultiplier =
                            ScaleManager.getSatelliteDistanceMultiplier(body.getId());

                    // Combine parent position + scaled offset
                    transformWork.scaledPosition.set(
                            parentScaledPos.x
                                    + transformWork.satelliteOffsetTransformed.x
                                            * distanceMultiplier,
                            parentScaledPos.y
                                    + transformWork.satelliteOffsetTransformed.y
                                            * distanceMultiplier,
                            parentScaledPos.z
                                    + transformWork.satelliteOffsetTransformed.z
                                            * distanceMultiplier);
                } else {
                    // Parent not in cache - fallback to absolute position
                    handleAbsolutePosition(body, state);
                }
            } else {
                // No parent found - fallback to absolute position
                handleAbsolutePosition(body, state);
            }
        } else {
            // Normal path for planets/sun or absolute coordinates
            handleAbsolutePosition(body, state);
        }

        // Cache this body's scaled position so its children can reference it without recomputing.
        CelestialBodyId bodyEnum = CelestialBodyId.fromString(body.getId());
        positionCache.store(bodyEnum, transformWork.scaledPosition);

        // Build transform matrix with camera-relative positioning.
        // Compute body geometry: visual equatorial / polar radii preserve the
        // physical oblateness ratio through ScaleManager's compression.
        computeBodyGeometry(body, renderWork.bodyGeometry);
        double visualRadius = renderWork.bodyGeometry.x;
        double visualPolar = renderWork.bodyGeometry.y;

        // T (translate) — scale is applied AFTER rotation below so that for
        // oblate bodies the polar squash happens in body-fixed coordinates.
        if (frameState.cameraWorldPos != null) {
            // Camera-relative math must stay in double precision (non-negotiable at AU scale).
            // This prevents precision loss that causes stuttering
            double relX = transformWork.scaledPosition.x - frameState.cameraWorldPos.x;
            double relY = transformWork.scaledPosition.y - frameState.cameraWorldPos.y;
            double relZ = transformWork.scaledPosition.z - frameState.cameraWorldPos.z;

            transformWork.modelMatrixDouble.identity().translate(relX, relY, relZ);
        } else {
            transformWork
                    .modelMatrixDouble
                    .identity()
                    .translate(
                            transformWork.scaledPosition.x,
                            transformWork.scaledPosition.y,
                            transformWork.scaledPosition.z);
        }

        // Apply IAU rotation, then non-uniform body-fixed scale.
        // Final matrix: M = T * R * S_body. Vertex transform v' = M * v evaluates
        // inside-out: S_body scales in body-fixed coordinates first (polar
        // squash along the spin axis Z), R rotates body-fixed -> world,
        // T translates. Order matters for tilted bodies — uniform scale was
        // commutative, non-uniform is not.
        Matrix3d rotation =
                IAURotationModels.getBodyFixedToJ2000(body.getId(), frameState.julianDate);
        if (rotation != null && frameState.j2000ToRender != null) {

            // Texture convention adjustment: The PNG textures start at 180° longitude
            // instead of 0° longitude (prime meridian). This is a common convention
            // for planet textures where the anti-meridian is at the texture center.
            // Apply 180° rotation to align the IAU prime meridian with the texture.
            // This rotateZ(π) is around body-fixed Z and commutes with the
            // non-uniform scale (X and Y share the same factor).
            transformWork.workRotation.set(frameState.j2000ToRender).mul(rotation);
            transformWork.workRotation.rotateZ(Math.PI);
            transformWork.rotation4d.identity().set3x3(transformWork.workRotation);
            transformWork.modelMatrixDouble.mul(transformWork.rotation4d);
        }

        // S_body — applied AFTER rotation so squash is in body-fixed coords.
        // For spherical bodies polar == equatorial: degenerates to uniform scale.
        transformWork.modelMatrixDouble.scale(visualRadius, visualRadius, visualPolar);

        // Convert to float and return
        transformWork.modelMatrix.set(transformWork.modelMatrixDouble);
        return transformWork.modelMatrix;
    }

    /**
     * Update GPU state and uniforms. FIXED: Use double precision for critical matrix operations.
     */
    private void updateGPUState(
            Matrix4f modelMatrix,
            MaterialProperties material,
            TextureArraySystem.TextureHandle texture,
            CelestialBodyId bodyEnum,
            DrawContext dc) {
        // Use shader
        uboShader.use();

        // Convert modelMatrix to double (reuse work matrix)
        renderWork.modelMatrixDouble.set(modelMatrix);

        // ModelView = View * Model (in double precision)
        renderWork
                .modelViewDouble
                .set(frameState.viewMatrixDouble)
                .mul(renderWork.modelMatrixDouble);
        // MVP = Projection * ModelView (in double precision)
        renderWork.mvpDouble.set(frameState.projMatrixDouble).mul(renderWork.modelViewDouble);

        // Convert to float for GPU
        renderWork.modelViewMatrix.set(renderWork.modelViewDouble);

        renderWork.mvpMatrix.set(renderWork.mvpDouble);
        // Normal matrix from ModelView
        renderWork.modelViewMatrix.normal(renderWork.normalMatrix);

        // Convert double matrices to float only now when needed for GPU
        frameState.viewMatrix.set(frameState.viewMatrixDouble);
        frameState.projMatrix.set(frameState.projMatrixDouble);

        // Update UBO
        celestialUbo.updateMatrices(
                modelMatrix,
                frameState.viewMatrix,
                frameState.projMatrix,
                renderWork.mvpMatrix,
                renderWork.normalMatrix);

        if (material != null) {
            celestialUbo.updateMaterial(
                    material.albedo,
                    material.roughness,
                    material.metallic,
                    material.emission,
                    material.emissionStrength,
                    material.isEmissive());
        } else {
            celestialUbo.updateMaterial(
                    renderWork.fallbackColor, 0.5f, 0.0f, renderWork.fallbackColor, 0.0f, false);
        }

        celestialUbo.updateSunPosition(frameState.sunPositionView);
        // Per-body geometry (eq, polar, ringInner, ringOuter) — populated by
        // buildTransform into renderWork.bodyGeometry. Must be uploaded for
        // BOTH passes (the UBO is one buffer; pass 1's ring draws would otherwise
        // read whatever pass 0 last wrote).
        celestialUbo.updateBodyGeometry(
                renderWork.bodyGeometry.x, renderWork.bodyGeometry.y,
                renderWork.bodyGeometry.z, renderWork.bodyGeometry.w);
        celestialUbo.upload();
        celestialUbo.bind();

        // Set body type - now O(1) enum check instead of string comparisons
        uboShader.setUniform("bodyType", bodyEnum.getShaderBodyType());
        uboShader.setUniform("bodyId", bodyEnum.getId());

        // Apply lighting
        if (dc != null && dc.getIlluminationManager() != null) {
            dc.setCurrentShader(uboShader);
            dc.getIlluminationManager().apply(dc);
        }

        // Set texture
        if (texture != null && texture.isReady()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, texture.getTextureId());
            uboShader.setUniform("textureArray", 0);
            uboShader.setUniform("hasTexture", true);
            uboShader.setUniform("textureLayer", texture.getArrayLayer());
            renderWork.texCoordScale.set(texture.getScaleU(), texture.getScaleV());
            uboShader.setUniform("texCoordScale", renderWork.texCoordScale);
            uboShader.setUniform("isRing", false); // Reset ring flag for normal bodies
        } else {
            uboShader.setUniform("hasTexture", false);
            uboShader.setUniform("isRing", false); // Reset ring flag
            if (material != null) {
                uboShader.setUniform("fallbackColor", material.albedo);
            } else {
                uboShader.setUniform("fallbackColor", renderWork.fallbackColor);
            }
        }
    }

    /**
     * Compute visual-space body geometry (eq, polar, ringInner, ringOuter) from BodyData and
     * ScaleManager, writing into the supplied output vector. Zero allocation — assumes the body's
     * id matches a catalog entry.
     */
    private void computeBodyGeometry(CelestialBody body, Vector4f out) {
        double equatorialRadius = body.getRadius();
        double visualRadius = ScaleManager.getVisualRadius(equatorialRadius, body.getId());
        BodyData data = CelestialCatalog.getByCode(body.getId());
        double polarRadius = data != null ? data.polarRadiusOrEquatorial() : equatorialRadius;
        double scaleFactor = equatorialRadius > 0.0 ? visualRadius / equatorialRadius : 1.0;
        double visualPolar = polarRadius * scaleFactor;
        double ringInner = (data != null && data.hasRings()) ? data.ringInnerRadius() : 0.0;
        double ringOuter = (data != null && data.hasRings()) ? data.ringOuterRadius() : 0.0;
        out.set((float) visualRadius, (float) visualPolar, (float) ringInner, (float) ringOuter);
    }

    /**
     * Upload per-body shadow uniforms: planetPositionView (camera-relative, in view space) and
     * planetAxisView (the body's spin axis in view space, normalized).
     *
     * Used by both the body-draw path (Group 3 ring shadow on body) and the ring-draw path
     * (Saturn's analytic shadow on rings + ringshine). Zero allocation — uses scratch vectors on
     * renderWork and frame state.
     */
    private void uploadShadowUniforms(String bodyId, Vector3d bodyPos) {
        // planetPositionView: camera-relative world -> view space
        if (frameState.cameraWorldPos != null) {
            renderWork.planetPosWorld.set(
                    bodyPos.x - frameState.cameraWorldPos.x,
                    bodyPos.y - frameState.cameraWorldPos.y,
                    bodyPos.z - frameState.cameraWorldPos.z);
        } else {
            renderWork.planetPosWorld.set(bodyPos.x, bodyPos.y, bodyPos.z);
        }
        frameState.viewMatrixDouble.transformPosition(
                renderWork.planetPosWorld, renderWork.planetPosViewDouble);
        renderWork.planetPosView.set(
                (float) renderWork.planetPosViewDouble.x,
                (float) renderWork.planetPosViewDouble.y,
                (float) renderWork.planetPosViewDouble.z);
        uboShader.setUniform("planetPositionView", renderWork.planetPosView);

        // planetAxisView: J2000 -> render frame -> view frame (direction transform)
        if (IAURotationModels.getSpinAxisJ2000(
                        bodyId, frameState.julianDate, renderWork.spinAxisJ2000)
                != null) {
            if (frameState.j2000ToRender != null) {
                frameState.j2000ToRender.transform(
                        renderWork.spinAxisJ2000, renderWork.spinAxisRender);
            } else {
                renderWork.spinAxisRender.set(renderWork.spinAxisJ2000);
            }
            // Direction-only transform (4th component implicitly 0): rotation only
            frameState.viewMatrixDouble.transformDirection(renderWork.spinAxisRender);
            renderWork.planetAxisView.set(
                    (float) renderWork.spinAxisRender.x,
                    (float) renderWork.spinAxisRender.y,
                    (float) renderWork.spinAxisRender.z);
            renderWork.planetAxisView.normalize();
            uboShader.setUniform("planetAxisView", renderWork.planetAxisView);
        }
    }

    /**
     * Render rings for a planet that has them. Creates a separate transform matrix without texture
     * alignment rotation. Zero allocation - texture handle passed from renderBody().
     */
    private void renderRings(
            CelestialBody body,
            Matrix4f planetModelMatrix,
            TextureArraySystem.TextureHandle ringTexture,
            DrawContext dc) {
        if (uboShader == null || !uboShader.isValid() || saturnRings == null) {
            return; // Can't render without shader and mesh
        }

        // Build ring-specific transform matrix WITHOUT texture alignment rotation
        // Get body position from cache
        CelestialBodyId bodyEnum = CelestialBodyId.fromString(body.getId());
        Vector3d bodyPos = positionCache.get(bodyEnum);
        if (bodyPos == null) {
            return; // Can't render without position
        }

        // Build ring transform matrix with camera-relative positioning
        // The ring mesh already has the correct proportions (1.0x inner, 2.3x outer)
        double ringScale = ScaleManager.getVisualRadius(body.getRadius(), body.getId());

        if (frameState.cameraWorldPos != null) {
            double relX = bodyPos.x - frameState.cameraWorldPos.x;
            double relY = bodyPos.y - frameState.cameraWorldPos.y;
            double relZ = bodyPos.z - frameState.cameraWorldPos.z;

            transformWork.modelMatrixDouble.identity().translate(relX, relY, relZ).scale(ringScale);
        } else {
            transformWork
                    .modelMatrixDouble
                    .identity()
                    .translate(bodyPos.x, bodyPos.y, bodyPos.z)
                    .scale(ringScale);
        }

        // Apply IAU rotation WITHOUT the texture alignment Z rotation
        Matrix3d rotation =
                IAURotationModels.getBodyFixedToJ2000(body.getId(), frameState.julianDate);
        if (rotation != null && frameState.j2000ToRender != null) {
            // Apply only the IAU rotation and frame transform, no texture alignment
            transformWork.workRotation.set(frameState.j2000ToRender).mul(rotation);
            // NO rotateZ(Math.PI) for rings!
            transformWork.rotation4d.identity().set3x3(transformWork.workRotation);
            transformWork.modelMatrixDouble.mul(transformWork.rotation4d);
        }

        // Convert to float for GPU (reuse existing work matrix)
        transformWork.modelMatrix.set(transformWork.modelMatrixDouble);

        // Use the unified UBO shader
        uboShader.use();

        // Pre-compute matrices for precision - convert to float only now
        frameState.viewMatrix.set(frameState.viewMatrixDouble);
        frameState.projMatrix.set(frameState.projMatrixDouble);
        renderWork.mvpMatrix.set(frameState.projMatrix);
        renderWork.mvpMatrix.mul(frameState.viewMatrix);
        renderWork.mvpMatrix.mul(transformWork.modelMatrix);

        // Calculate normal matrix using RING'S transform, not planet's!
        renderWork.modelViewMatrix.set(frameState.viewMatrix);
        renderWork.modelViewMatrix.mul(transformWork.modelMatrix); // Use ring's matrix!
        renderWork.modelViewMatrix.normal(renderWork.normalMatrix);

        // Update UBO with RING transform, not planet transform
        celestialUbo.updateMatrices(
                transformWork.modelMatrix,
                frameState.viewMatrix,
                frameState.projMatrix,
                renderWork.mvpMatrix,
                renderWork.normalMatrix);

        // Get ring-specific material properties.
        MaterialProperties ringMaterial = MaterialCatalog.getMaterial("saturn_rings");
        // Reuse the pre-allocated work vectors.
        renderWork.ringAlbedo.set(ringMaterial.albedo);
        renderWork.ringEmission.set(ringMaterial.emission);
        celestialUbo.updateMaterial(
                renderWork.ringAlbedo,
                ringMaterial.roughness,
                ringMaterial.metallic,
                renderWork.ringEmission,
                ringMaterial.emissionStrength,
                false);

        celestialUbo.updateSunPosition(frameState.sunPositionView);

        // Per-body geometry — must be re-uploaded for the ring pass too,
        // since the UBO is one buffer shared across passes. The geometry is
        // the parent body's (Saturn's), not the ring's.
        computeBodyGeometry(body, renderWork.bodyGeometry);
        celestialUbo.updateBodyGeometry(
                renderWork.bodyGeometry.x, renderWork.bodyGeometry.y,
                renderWork.bodyGeometry.z, renderWork.bodyGeometry.w);

        // Upload and bind UBO
        celestialUbo.upload();
        celestialUbo.bind();

        // Set texture uniforms for texture array
        uboShader.setUniform("textureArray", 0);
        uboShader.setUniform("textureLayer", ringTexture.getArrayLayer());
        renderWork.texCoordScale.set(ringTexture.getScaleU(), ringTexture.getScaleV());
        uboShader.setUniform("texCoordScale", renderWork.texCoordScale);
        uboShader.setUniform("hasTexture", true);
        uboShader.setUniform("isRing", true); // Enable ring texture sampling mode

        // ringTextureLayer is consumed by the body-shader path (Group 3); the
        // ring path itself uses textureLayer. Set both to the ring's layer
        // for consistency — ring fragments don't read ringTextureLayer.
        uboShader.setUniform("ringTextureLayer", ringTexture.getArrayLayer());

        // Pass ring-specific optical properties to shader.
        // ringMaterial.opticalDepthNormal is retained on MaterialProperties as
        // catalog provenance but not uploaded — the shader classifies per-region
        // optical depth from the radial coordinate instead.
        uboShader.setUniform("ringForwardG", ringMaterial.forwardScatteringG);
        uboShader.setUniform("ringBackwardG", ringMaterial.backwardScatteringG);
        uboShader.setUniform("ringParticleMix", ringMaterial.particleMixRatio);
        uboShader.setUniform("saturnshineAlbedo", ringMaterial.saturnshineAlbedo);

        // Atmospheric refraction broadens the planet's penumbra cast on the rings.
        // Saturn-specific value lives on the saturn (body) material, not the ring.
        // The same body material supplies the Saturnshine day-side reflectance
        // color, keeping it in the catalog rather than duplicated in the shader.
        MaterialProperties bodyMaterial = MaterialCatalog.getMaterial(body.getId());
        uboShader.setUniform(
                "atmosphericRefraction",
                bodyMaterial != null ? bodyMaterial.atmosphericRefractionRad : 0.0f);
        uboShader.setUniform(
                "saturnshineColor",
                bodyMaterial != null ? bodyMaterial.albedo : SATURNSHINE_COLOR_FALLBACK);

        // Set body type uniform (0 = rocky/ring)
        uboShader.setUniform("bodyType", 0);

        // Shadow uniforms (planetPositionView + planetAxisView). Used by the
        // ring shader's analytic Saturn-on-ring shadow + the ringshine
        // integration. Identical pattern to the body-draw call site.
        uploadShadowUniforms(body.getId(), bodyPos);

        // Bind ring texture array
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, ringTexture.getTextureId());

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Disable depth writes for transparent objects (required to avoid blocking later opaques).
        // This allows objects behind the ring to render properly
        glDepthMask(false);

        // Disable backface culling to see both sides of the ring
        glDisable(GL_CULL_FACE);

        // Render the ring mesh
        saturnRings.render();

        // Restore state
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glDepthMask(true); // Re-enable depth writes
    }

    /** Log statistics for performance analysis. */
    public void logStatistics() {
        LOGGER.info(
                String.format(
                        "CelestialRenderer Statistics:\n"
                                + "  Frames: %d\n"
                                + "  Bodies rendered: %d",
                        frameCount, bodiesRendered));
    }

    /**
     * Release GL resources owned by this renderer. The UBO shader and sphere mesh are NOT disposed
     * here — they're owned by ResourceManager and MeshLibrary respectively.
     */
    public void dispose() {
        logStatistics();
        if (celestialUbo != null) {
            celestialUbo.dispose();
        }
        if (saturnRings != null) {
            saturnRings.dispose();
        }
        LOGGER.info("CelestialRenderer disposed");
    }
}
