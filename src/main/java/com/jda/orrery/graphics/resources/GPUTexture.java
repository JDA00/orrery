package com.jda.orrery.graphics.resources;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import com.jda.orrery.core.logging.Logging;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * RAII wrapper for GPU texture resources.
 *
 * Ensures proper cleanup of GPU resources using AutoCloseable pattern. This prevents GPU memory
 * leaks even if exceptions occur during initialization.
 *
 * Usage:
 *
 * <pre>
 * try (GPUTexture texture = new GPUTexture()) {
 *     texture.bind();
 *     // ... use texture ...
 * } // Automatically cleaned up
 * </pre>
 *
 * Thread-safety: disposal is thread-safe; GL operations must be called from the thread that owns
 * the GL context.
 */
public class GPUTexture implements AutoCloseable {
    private static final Logger LOGGER = Logging.logger(GPUTexture.class);

    private volatile int textureId;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final String debugName;
    private final StackTraceElement[] creationStack;

    /** Create a new GPU texture. Generates a texture ID immediately. */
    public GPUTexture() {
        this(null);
    }

    /**
     * Create a new GPU texture with a debug name.
     *
     * @param debugName Name for debugging (e.g., "earth_diffuse")
     */
    public GPUTexture(String debugName) {
        this.debugName = debugName;
        this.textureId = glGenTextures();

        // Capture creation stack for leak detection
        if (LOGGER.isLoggable(java.util.logging.Level.FINE)) {
            this.creationStack = Thread.currentThread().getStackTrace();
        } else {
            this.creationStack = null;
        }

        if (textureId == 0) {
            throw new IllegalStateException(
                    "Failed to generate texture ID"
                            + (debugName != null ? " for " + debugName : ""));
        }

        LOGGER.fine(
                "Created GPU texture "
                        + textureId
                        + (debugName != null ? " (" + debugName + ")" : ""));
    }

    /**
     * Wrap an existing texture ID. Takes ownership of the texture for cleanup.
     *
     * @param textureId Existing texture ID
     * @param debugName Name for debugging
     */
    public GPUTexture(int textureId, String debugName) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("Invalid texture ID: " + textureId);
        }
        this.textureId = textureId;
        this.debugName = debugName;
        this.creationStack = null;
    }

    /**
     * Get the OpenGL texture ID.
     *
     * @return Texture ID or 0 if disposed
     */
    public int getId() {
        return textureId;
    }

    /**
     * Bind this texture to the current texture unit.
     *
     * @throws IllegalStateException if texture has been disposed
     */
    public void bind() {
        if (disposed.get()) {
            throw new IllegalStateException(
                    "Texture has been disposed" + (debugName != null ? ": " + debugName : ""));
        }
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Bind this texture to a specific texture unit.
     *
     * @param unit Texture unit (0-31 typically)
     * @throws IllegalStateException if texture has been disposed
     */
    public void bind(int unit) {
        if (disposed.get()) {
            throw new IllegalStateException(
                    "Texture has been disposed" + (debugName != null ? ": " + debugName : ""));
        }
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /** Unbind any texture from the current unit. Static method as it doesn't depend on instance. */
    public static void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Check if this texture has been disposed.
     *
     * @return true if disposed
     */
    public boolean isDisposed() {
        return disposed.get();
    }

    /**
     * Dispose of GPU resources. Safe to call multiple times. Must be called from thread with GL
     * context.
     */
    @Override
    public void close() {
        if (disposed.compareAndSet(false, true)) {
            if (textureId != 0) {
                glDeleteTextures(textureId);
                LOGGER.fine(
                        "Disposed GPU texture "
                                + textureId
                                + (debugName != null ? " (" + debugName + ")" : ""));
                textureId = 0;
            }
        }
    }

    /** Finalizer for leak detection. Logs a warning if texture wasn't properly disposed. */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!disposed.get() && textureId != 0) {
                LOGGER.warning(
                        "GPU texture "
                                + textureId
                                + " leaked"
                                + (debugName != null ? " (" + debugName + ")" : "")
                                + ". Remember to call close() or use try-with-resources.");

                // Log creation stack if available
                if (creationStack != null && LOGGER.isLoggable(java.util.logging.Level.FINE)) {
                    StringBuilder sb = new StringBuilder("Texture created at:\n");
                    for (int i = 2; i < Math.min(creationStack.length, 10); i++) {
                        sb.append("  at ").append(creationStack[i]).append("\n");
                    }
                    LOGGER.fine(sb.toString());
                }

                // Note: We CANNOT call glDeleteTextures here as we may not have GL context
                // This is a leak but better than crashing
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public String toString() {
        return String.format(
                "GPUTexture[id=%d, disposed=%s%s]",
                textureId, disposed.get(), debugName != null ? ", name=" + debugName : "");
    }
}
