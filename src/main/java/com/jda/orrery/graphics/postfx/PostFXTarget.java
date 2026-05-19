package com.jda.orrery.graphics.postfx;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_MAX_SAMPLES;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorageMultisample;

import com.jda.orrery.core.logging.Logging;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * Multisample HDR offscreen target with a single-sample resolve.
 *
 * Scene rendering binds the multisample FBO (RGBA16F color + DEPTH_COMPONENT32F depth
 * renderbuffers). After all layers render, {@link #resolve()} blits both attachments into
 * single-sample textures that the post-FX pass samples. The depth resolve is sampleable for future
 * occlusion queries (e.g. lens-flare).
 */
public class PostFXTarget {
    private static final Logger LOGGER = Logging.logger(PostFXTarget.class);

    private int width;
    private int height;
    private int requestedSampleCount;
    private int sampleCount;

    private int msFBO;
    private int msColorRBO;
    private int msDepthRBO;

    private int resolveFBO;
    private int resolvedColorTex;
    private int resolvedDepthTex;

    private boolean valid;

    public PostFXTarget(int width, int height, int sampleCount) {
        this.requestedSampleCount = sampleCount;
        allocate(width, height);
    }

    /** Re-allocate at a new size, retrying the originally requested sample count. */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "PostFXTarget dimensions must be positive: " + width + "x" + height);
        }
        deallocate();
        allocate(width, height);
    }

    private void allocate(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "PostFXTarget dimensions must be positive: " + width + "x" + height);
        }

        int maxSamples = glGetInteger(GL_MAX_SAMPLES);
        int effectiveSamples = Math.max(1, Math.min(requestedSampleCount, maxSamples));
        if (effectiveSamples < requestedSampleCount) {
            LOGGER.warning(
                    String.format(
                            "Requested %d× MSAA, GPU max is %d×; clamping to %d×",
                            requestedSampleCount, maxSamples, effectiveSamples));
        }

        this.width = width;
        this.height = height;
        this.sampleCount = effectiveSamples;

        // --- Multisample FBO: color + depth renderbuffers ---
        msColorRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msColorRBO);
        glRenderbufferStorageMultisample(
                GL_RENDERBUFFER, effectiveSamples, GL_RGBA16F, width, height);

        msDepthRBO = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msDepthRBO);
        glRenderbufferStorageMultisample(
                GL_RENDERBUFFER, effectiveSamples, GL_DEPTH_COMPONENT32F, width, height);

        msFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, msFBO);
        glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msColorRBO);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msDepthRBO);

        int msStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (msStatus != GL_FRAMEBUFFER_COMPLETE) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            throw new IllegalStateException(
                    "PostFXTarget multisample FBO incomplete: 0x" + Integer.toHexString(msStatus));
        }

        // --- Resolve FBO: color + depth textures (sampleable) ---
        resolvedColorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolvedColorTex);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA16F,
                width,
                height,
                0,
                GL_RGBA,
                GL_FLOAT,
                (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        resolvedDepthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, resolvedDepthTex);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT32F,
                width,
                height,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        resolveFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFBO);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, resolvedColorTex, 0);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, resolvedDepthTex, 0);

        int resolveStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (resolveStatus != GL_FRAMEBUFFER_COMPLETE) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            throw new IllegalStateException(
                    "PostFXTarget resolve FBO incomplete: 0x" + Integer.toHexString(resolveStatus));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        valid = true;
    }

    /** Bind the multisample FBO for scene rendering. */
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, msFBO);
    }

    /**
     * Resolve the multisample FBO into the single-sample resolve textures. Caller is responsible
     * for unbinding to the default framebuffer afterward via {@link #unbindToDefault()}.
     */
    public void resolve() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msFBO);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFBO);

        // Explicit read/draw buffer state on color blit — defends against changes elsewhere
        // and against future multi-attachment FBOs. Ignored by the depth blit.
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_LINEAR);

        glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
    }

    /** Bind the default framebuffer (0). */
    public void unbindToDefault() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int getColorTexture() {
        return resolvedColorTex;
    }

    public int getDepthTexture() {
        return resolvedDepthTex;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public boolean isValid() {
        return valid;
    }

    private void deallocate() {
        if (msFBO != 0) {
            glDeleteFramebuffers(msFBO);
            msFBO = 0;
        }
        if (resolveFBO != 0) {
            glDeleteFramebuffers(resolveFBO);
            resolveFBO = 0;
        }
        if (msColorRBO != 0) {
            glDeleteRenderbuffers(msColorRBO);
            msColorRBO = 0;
        }
        if (msDepthRBO != 0) {
            glDeleteRenderbuffers(msDepthRBO);
            msDepthRBO = 0;
        }
        if (resolvedColorTex != 0) {
            glDeleteTextures(resolvedColorTex);
            resolvedColorTex = 0;
        }
        if (resolvedDepthTex != 0) {
            glDeleteTextures(resolvedDepthTex);
            resolvedDepthTex = 0;
        }
        valid = false;
    }

    public void dispose() {
        deallocate();
    }
}
