package com.jda.orrery.graphics.postfx;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

import com.jda.orrery.graphics.illumination.IlluminationManager;
import com.jda.orrery.graphics.illumination.IlluminationManager.IlluminationProfile;
import com.jda.orrery.graphics.resources.Shader;

/**
 * Post-processing orchestrator. In Step 3 it runs a stubbed bloom pass (no-op) and a tonemap pass
 * that composites the resolved scene color with the (currently 1×1 black) bloom output and writes
 * to the default framebuffer.
 */
public class PostFXPipeline {
    private final PostFXTarget target;
    private final IlluminationManager illuminationManager;

    private Shader tonemapShader;
    private FullScreenTriangle fullScreenTriangle;
    private BloomPipeline bloomPipeline;

    public PostFXPipeline(PostFXTarget target, IlluminationManager illuminationManager) {
        this.target = target;
        this.illuminationManager = illuminationManager;
    }

    public void initialize() {
        tonemapShader =
                new Shader(
                        "postfx_tonemap",
                        "/shaders/postfx_fullscreen.vert",
                        "/shaders/postfx_tonemap.frag");
        if (!tonemapShader.isValid()) {
            throw new IllegalStateException(
                    "PostFXPipeline: failed to compile/link postfx_tonemap");
        }

        fullScreenTriangle = new FullScreenTriangle();
        fullScreenTriangle.initialize();

        bloomPipeline = new BloomPipeline();
        bloomPipeline.initialize(target.getWidth(), target.getHeight());
    }

    public void execute() {
        bloomPipeline.execute();

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_BLEND);

        IlluminationProfile profile = illuminationManager.getActiveProfile();

        tonemapShader.use();
        tonemapShader.setUniform("u_exposure", profile.exposure);
        tonemapShader.setUniform("u_bloomStrength", profile.bloomStrength);
        tonemapShader.setUniform("u_toneMapOp", profile.toneMapOperator);
        tonemapShader.setUniform("u_contrastLift", profile.contrastLift);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, target.getColorTexture());
        tonemapShader.setUniform("u_sceneHDR", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomPipeline.getBloomTexture());
        tonemapShader.setUniform("u_bloom", 1);

        fullScreenTriangle.draw();

        // Reset to texture unit 0 so subsequent code that doesn't set glActiveTexture
        // operates on the conventional default.
        glActiveTexture(GL_TEXTURE0);

        // Restore GL state. GL_BLEND must be re-enabled — DrawContext enables it once at
        // init and ring rendering relies on it being sticky across frames.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_BLEND);
    }

    public void dispose() {
        if (tonemapShader != null) {
            tonemapShader.dispose();
            tonemapShader = null;
        }
        if (fullScreenTriangle != null) {
            fullScreenTriangle.dispose();
            fullScreenTriangle = null;
        }
        if (bloomPipeline != null) {
            bloomPipeline.dispose();
            bloomPipeline = null;
        }
    }
}
