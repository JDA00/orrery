package com.jda.orrery.graphics.core;

import com.jda.orrery.core.frames.FrameManager;
import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.illumination.IlluminationManager;
import com.jda.orrery.graphics.layers.Layer;
import com.jda.orrery.graphics.postfx.PostFXPipeline;
import com.jda.orrery.graphics.postfx.PostFXTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Scene controller for LWJGL version. Manages layers and render order. */
public class SceneController {
    private static final Logger LOGGER = Logging.logger(SceneController.class);

    protected List<Layer> layers = new ArrayList<>();
    protected boolean animationEnabled = true;
    protected long frameCount = 0;

    private final FrameManager frameManager;
    protected final IlluminationManager illuminationManager;

    private PostFXTarget postFXTarget;
    private PostFXPipeline postFXPipeline;

    public SceneController(FrameManager frameManager, IlluminationManager illuminationManager) {
        this.frameManager = frameManager;
        this.illuminationManager = illuminationManager;
    }

    /** Inject the post-FX target and pipeline. Called once after GL init. */
    public void attachPostFX(PostFXTarget target, PostFXPipeline pipeline) {
        this.postFXTarget = target;
        this.postFXPipeline = pipeline;
    }

    public void init(DrawContext dc) {
        LOGGER.info("Initializing SceneController");
        illuminationManager.initialize(dc);
        dc.setIlluminationManager(illuminationManager);
        LOGGER.info(
                "IlluminationManager active profile: "
                        + illuminationManager.getActiveProfile().name);
    }

    public void drawFrame(DrawContext dc) {
        postFXTarget.bind();
        try {
            dc.beginFrame();
            illuminationManager.update(dc);
            for (int i = 0, n = layers.size(); i < n; i++) {
                Layer layer = layers.get(i);
                if (layer.isEnabled()) {
                    layer.render(dc);
                }
            }
            postFXTarget.resolve();
            postFXTarget.unbindToDefault();
            postFXPipeline.execute();
        } finally {
            postFXTarget.unbindToDefault();
        }
        dc.endFrame();
        frameCount++;
    }

    public void dispose(DrawContext dc) {
        LOGGER.info("Disposing SceneController");

        for (int i = 0, n = layers.size(); i < n; i++) {
            layers.get(i).dispose(dc);
        }
    }

    public void addLayer(Layer layer) {
        if (layer != null && !layers.contains(layer)) {
            layers.add(layer);
            LOGGER.fine("Added layer: " + layer.getName());
        }
    }

    public void removeLayer(Layer layer) {
        layers.remove(layer);
    }

    public List<Layer> getLayers() {
        return new ArrayList<>(layers);
    }

    public void toggleAnimation() {
        animationEnabled = !animationEnabled;
        LOGGER.info("Animation " + (animationEnabled ? "enabled" : "disabled"));
    }

    public boolean isAnimationEnabled() {
        return animationEnabled;
    }
}
