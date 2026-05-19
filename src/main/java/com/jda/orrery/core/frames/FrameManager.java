package com.jda.orrery.core.frames;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.core.math.Vec3d;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Caches frame-to-frame transformation matrices and delegates the actual transforms to a
 * {@link FrameKernel}. The cache is keyed by frame pair (and ephemeris time, for time-dependent
 * transforms); cached matrices are shared and must not be mutated by callers.
 */
public class FrameManager {

    private static final Logger LOGGER = Logging.logger(FrameManager.class);

    // Current frame kernel
    private FrameKernel kernel;

    // Cache for transformation matrices
    // Key format: "fromFrame->toFrame@et" for time-dependent
    // Key format: "fromFrame->toFrame" for fixed transformations
    private final Map<String, Matrix3d> transformCache = new ConcurrentHashMap<>();

    // Maximum cache size before clearing
    private static final int MAX_CACHE_SIZE = 1000;

    public FrameManager(FrameKernel kernel) {
        this.kernel = kernel;
        LOGGER.info("FrameManager initialized with " + kernel.getName() + " kernel");
    }

    /**
     * Swap in a different frame kernel; clears the transform cache.
     *
     * @param newKernel The new kernel to use
     */
    public void setKernel(FrameKernel newKernel) {
        if (newKernel == null) {
            throw new IllegalArgumentException("Kernel cannot be null");
        }

        this.kernel = newKernel;
        this.transformCache.clear(); // Clear cache when kernel changes
        LOGGER.info("FrameManager switched to " + newKernel.getName() + " kernel");
    }

    /**
     * Transform a framed state into the target reference frame. The result is written into the
     * caller-supplied output vectors so the hot path stays allocation-free. The state's
     * isRelative flag is preserved implicitly — the caller reconstructs any wrapper they need
     * from the output vectors.
     *
     * @param state The state to transform
     * @param targetFrame The desired reference frame
     * @param posOut Output vector for transformed position (will be modified)
     * @param velOut Output vector for transformed velocity (will be modified)
     * @throws IllegalArgumentException if transformation is not available
     */
    public void transformInto(
            FramedState state, String targetFrame, Vector3d posOut, Vector3d velOut) {
        // Quick return if already in target frame
        if (state.getFrame().equals(targetFrame)) {
            Vec3d pos = state.getPosition();
            Vec3d vel = state.getVelocity();
            posOut.set(pos.x, pos.y, pos.z);
            velOut.set(vel.x, vel.y, vel.z);
            return;
        }

        // Get transformation matrix
        Matrix3d transform = getTransformMatrix(state.getFrame(), targetFrame, state.getET());

        if (transform == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "No transformation available from %s to %s",
                            state.getFrame(), targetFrame));
        }

        // Copy state vectors to output vectors
        Vec3d pos = state.getPosition();
        Vec3d vel = state.getVelocity();
        posOut.set(pos.x, pos.y, pos.z);
        velOut.set(vel.x, vel.y, vel.z);

        // Transform in place; output is written into the caller's vectors.
        transform.transform(posOut);
        transform.transform(velOut);
    }

    /**
     * Get the transformation matrix between two frames.
     *
     * <b>Contract:</b> the returned matrix is shared cache state and MUST NOT be mutated by the
     * caller. Callers needing a mutable copy should pass their own matrix to {@link
     * #getTransformMatrixInto}.
     *
     * @param fromFrame Source frame
     * @param toFrame Target frame
     * @param et Ephemeris time (for time-dependent transformations)
     * @return Cached transformation matrix (do not mutate), or null if not available
     */
    public Matrix3d getTransformMatrix(String fromFrame, String toFrame, double et) {
        boolean timeDependent = kernel.isTimeDependent(fromFrame, toFrame);

        String cacheKey =
                timeDependent
                        ? String.format("%s->%s@%.1f", fromFrame, toFrame, et)
                        : String.format("%s->%s", fromFrame, toFrame);

        Matrix3d cached = transformCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Cache miss: fetch from kernel and store. The kernel returns a fresh
        // matrix, so no extra defensive copy is needed here.
        Matrix3d transform = kernel.getTransform(fromFrame, toFrame, et);

        if (transform != null) {
            if (transformCache.size() >= MAX_CACHE_SIZE) {
                LOGGER.fine("Transform cache cleared (reached " + MAX_CACHE_SIZE + " entries)");
                transformCache.clear();
            }
            transformCache.put(cacheKey, transform);
        }

        return transform;
    }

    /**
     * Get transformation matrix between frames WITHOUT allocation. Writes the result into the
     * provided matrix to avoid allocation.
     *
     * @param fromFrame Source frame
     * @param toFrame Target frame
     * @param et Ephemeris time (for time-dependent transformations)
     * @param result Matrix to write result into (will be modified)
     * @return The result matrix for chaining, or null if not available
     */
    public Matrix3d getTransformMatrixInto(
            String fromFrame, String toFrame, double et, Matrix3d result) {
        // Check if transformation is time-dependent
        boolean timeDependent = kernel.isTimeDependent(fromFrame, toFrame);

        // Create cache key
        String cacheKey =
                timeDependent
                        ? String.format("%s->%s@%.1f", fromFrame, toFrame, et)
                        : String.format("%s->%s", fromFrame, toFrame);

        // Check cache
        Matrix3d cached = transformCache.get(cacheKey);
        if (cached != null) {
            result.set(cached); // Copy into provided matrix - NO ALLOCATION
            return result;
        }

        // Get transformation from kernel
        Matrix3d transform = kernel.getTransform(fromFrame, toFrame, et);

        if (transform != null) {
            // Cache the result (if cache not too large)
            if (transformCache.size() < MAX_CACHE_SIZE) {
                transformCache.put(cacheKey, new Matrix3d(transform));
            } else {
                // Clear cache if it's getting too large
                LOGGER.fine("Transform cache cleared (reached " + MAX_CACHE_SIZE + " entries)");
                transformCache.clear();
                transformCache.put(cacheKey, new Matrix3d(transform));
            }
            result.set(transform); // Copy into provided matrix
            return result;
        }

        return null; // No transformation available
    }

    /**
     * Check if a transformation is available between two frames.
     *
     * @param fromFrame Source frame
     * @param toFrame Target frame
     * @return true if transformation is available
     */
    public boolean canTransform(String fromFrame, String toFrame) {
        return kernel.canTransform(fromFrame, toFrame);
    }

    /** Clear the transformation cache. Useful when time jumps occur or memory needs to be freed. */
    public void clearCache() {
        transformCache.clear();
        LOGGER.fine("Transform cache cleared manually");
    }

    /** Get information about the current kernel. */
    public String getKernelName() {
        return kernel.getName();
    }

    /** Get cache statistics for debugging. */
    public int getCacheSize() {
        return transformCache.size();
    }
}
