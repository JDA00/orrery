package com.jda.orrery.graphics.layers;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.domain.astronomy.SolarSystem;
import com.jda.orrery.graphics.core.DrawContext;
import com.jda.orrery.graphics.pipeline.CelestialRenderer;
import java.util.logging.Logger;

/**
 * Iterates the live {@link SolarSystem} bodies once per frame and dispatches them to the
 * {@link CelestialRenderer}. There is no scene graph or visitor — bodies go straight to the
 * renderer in iteration order.
 */
public class DirectSolarSystemLayer implements Layer {
    private static final Logger LOGGER = Logging.logger(DirectSolarSystemLayer.class);

    private final SolarSystem solarSystem;
    private final CelestialRenderer renderer;

    private String name = "DirectSolarSystemLayer";
    private boolean enabled = true;

    /**
     * Create a new direct rendering layer for the solar system.
     *
     * @param solarSystem The solar system to render
     * @param renderer The celestial renderer (injected; application-owned)
     */
    public DirectSolarSystemLayer(SolarSystem solarSystem, CelestialRenderer renderer) {
        this.solarSystem = solarSystem;
        this.renderer = renderer;

        LOGGER.info("DirectSolarSystemLayer created");
    }

    /** Initialize the layer. Called once when the layer is added to the scene controller. */
    public void init(DrawContext dc) {
        LOGGER.info("=== Initializing DirectSolarSystemLayer Direct Rendering) ===");

        // Initialize with no fallbacks. The renderer requires celestial_unified_ubo;
        // if it's not available, fail fast rather than degrade silently.
        try {
            renderer.initializeDirectRendering();
        } catch (RuntimeException e) {
            LOGGER.severe(
                    "FATAL: Cannot initialize renderer - required shader missing: "
                            + e.getMessage());
            throw e;
        }

        // Log initial body count
        int bodyCount =
                1
                        + // Sun
                        solarSystem.getPlanets().size()
                        + solarSystem.getSatellites().size();
        LOGGER.info(
                String.format("Direct renderer initialized with %d celestial bodies", bodyCount));
    }

    /**
     * Render the solar system using direct submission.
     *
     * @param dc Draw context with matrices and state
     */
    @Override
    public void render(DrawContext dc) {
        if (!enabled) return;

        // Set solar system in context for other systems to access
        dc.setSolarSystem(solarSystem);

        // Update all body positions for current time
        // Bodies update their own positions when getState() is called

        // Direct render all bodies (culling and sorting handled internally)
        renderer.renderSolarSystem(solarSystem, dc);
    }

    /** Dispose of resources when layer is removed. */
    @Override
    public void dispose(DrawContext dc) {
        // Renderer is owned by ApplicationContext and disposed there;
        // this layer only holds a reference.
        LOGGER.info("DirectSolarSystemLayer disposed");
    }

    /**
     * Get the renderer for external access (e.g., picking).
     *
     * @return The celestial renderer
     */
    public CelestialRenderer getRenderer() {
        return renderer;
    }

    // Layer interface implementation

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (enabled) {
            LOGGER.info("DirectSolarSystemLayer enabled");
        } else {
            LOGGER.info("DirectSolarSystemLayer disabled");
        }
    }
}
