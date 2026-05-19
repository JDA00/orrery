package com.jda.orrery.graphics.textures;

import com.jda.orrery.core.logging.Logging;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Texture loading and eviction priority system.
 *
 * Priority is derived from: - Body importance (Sun always maximum as emissive) - Visibility
 * state - Distance from camera - Screen coverage
 */
public class TexturePrioritySystem {
    private static final Logger LOGGER = Logging.logger(TexturePrioritySystem.class);

    /**
     * Base priorities for known bodies. These are the starting values before dynamic adjustments.
     */
    private static final Map<String, Integer> BASE_PRIORITIES =
            Map.of(
                    "sun", 10000, // Maximum - emissive, always visible
                    "earth", 9000, // Primary observation target
                    "moon", 8500, // Earth companion
                    "mars", 8000, // Secondary observation target
                    "jupiter", 7000, // Gas giant - often targeted
                    "saturn", 7000, // Gas giant with rings
                    "venus", 6000, // Inner planet
                    "mercury", 6000, // Inner planet
                    "uranus", 5000, // Outer planet
                    "neptune", 5000 // Outer planet
                    );

    /**
     * Bodies that are pinned in memory and never evicted. Sun is always pinned as it's emissive and
     * always visible.
     */
    private final Set<String> pinnedBodies;

    /** Constructor initializes pinned bodies. */
    public TexturePrioritySystem() {
        this.pinnedBodies = new HashSet<>();
        this.pinnedBodies.add("sun"); // Sun is always pinned

        LOGGER.fine("TexturePrioritySystem initialized with pinned bodies: " + pinnedBodies);
    }

    /**
     * Calculate dynamic priority for a body based on current view context.
     *
     * @param bodyId The body identifier
     * @param context View context with camera and visibility information
     * @return Calculated priority (higher = more important)
     */
    public int calculate(String bodyId, TextureArraySystem.ViewContext context) {
        if (bodyId == null) return 0;

        String lowerBodyId = bodyId.toLowerCase();

        // Sun gets absolute maximum priority
        if ("sun".equals(lowerBodyId)) {
            return Integer.MAX_VALUE;
        }

        // Start with base priority
        int base = BASE_PRIORITIES.getOrDefault(lowerBodyId, 3000);

        // Distance factor - inverse square law approximation
        // Closer objects get higher priority
        double distanceAU = context.cameraDistance;
        int distanceFactor = calculateDistanceFactor(distanceAU);

        // Screen coverage factor - objects taking more screen space are higher priority
        double pixelsPerDegree = context.pixelsPerDegree;
        int screenFactor = calculateScreenFactor(pixelsPerDegree);

        // Scientific mode bonus - higher quality for analysis
        int scientificBonus = context.scientificMode ? 1000 : 0;

        // Calculate total priority
        int totalPriority = base + distanceFactor + screenFactor + scientificBonus;

        // Log high-priority calculations for debugging
        if (totalPriority > 8000) {
            LOGGER.finest(
                    String.format(
                            "Priority for %s: base=%d, distance=%d, screen=%d, scientific=%d, total=%d",
                            bodyId,
                            base,
                            distanceFactor,
                            screenFactor,
                            scientificBonus,
                            totalPriority));
        }

        return totalPriority;
    }

    /**
     * Calculate priority with explicit visibility flag.
     *
     * @param bodyId The body identifier
     * @param isVisible Whether the body is currently visible
     * @param distanceAU Distance from camera in AU
     * @param screenPixels Approximate pixels on screen
     */
    public int calculate(String bodyId, boolean isVisible, double distanceAU, double screenPixels) {
        if (bodyId == null) return 0;

        String lowerBodyId = bodyId.toLowerCase();

        // Sun gets absolute maximum priority
        if ("sun".equals(lowerBodyId)) {
            return Integer.MAX_VALUE;
        }

        // Start with base priority
        int base = BASE_PRIORITIES.getOrDefault(lowerBodyId, 3000);

        // Visibility factor - huge boost if visible
        int visibilityFactor = isVisible ? 5000 : 0;

        // Distance factor
        int distanceFactor = calculateDistanceFactor(distanceAU);

        // Screen coverage factor
        int screenFactor = (int) (screenPixels / 10.0); // Scale down to reasonable range

        return base + visibilityFactor + distanceFactor + screenFactor;
    }

    /**
     * Calculate distance-based priority factor. Uses inverse square law with logarithmic dampening
     * for extreme distances.
     *
     * @param distanceAU Distance in astronomical units
     * @return Distance priority factor (0-2000)
     */
    private int calculateDistanceFactor(double distanceAU) {
        if (distanceAU <= 0.001) {
            return 2000; // Very close - maximum priority
        }

        // Inverse square law with dampening
        double factor = 1000.0 / (distanceAU * distanceAU);

        // Logarithmic dampening for very distant objects
        if (distanceAU > 10.0) {
            factor *= Math.max(0.1, 1.0 / Math.log10(distanceAU));
        }

        // Clamp to reasonable range
        return (int) Math.min(2000, Math.max(0, factor));
    }

    /**
     * Calculate screen coverage priority factor.
     *
     * @param pixelsPerDegree Screen resolution metric
     * @return Screen coverage factor (0-1000)
     */
    private int calculateScreenFactor(double pixelsPerDegree) {
        // Objects covering more screen space get higher priority
        return (int) Math.min(1000, pixelsPerDegree * 2);
    }

    /**
     * Check if a body is pinned (never evicted).
     *
     * @param bodyId The body identifier
     * @return true if the body should never be evicted
     */
    public boolean isPinned(String bodyId) {
        return pinnedBodies.contains(bodyId.toLowerCase());
    }

    /**
     * Add a body to the pinned list. Used for temporarily pinning important bodies.
     *
     * @param bodyId The body to pin
     */
    public void pinBody(String bodyId) {
        if (bodyId != null) {
            pinnedBodies.add(bodyId.toLowerCase());
            LOGGER.info("Pinned texture for: " + bodyId);
        }
    }

    /**
     * Remove a body from the pinned list. Note: Sun can never be unpinned.
     *
     * @param bodyId The body to unpin
     */
    public void unpinBody(String bodyId) {
        if (bodyId != null && !"sun".equals(bodyId.toLowerCase())) {
            pinnedBodies.remove(bodyId.toLowerCase());
            LOGGER.info("Unpinned texture for: " + bodyId);
        }
    }

    /**
     * Get the set of pinned bodies.
     *
     * @return Copy of pinned bodies set
     */
    public Set<String> getPinnedBodies() {
        return new HashSet<>(pinnedBodies);
    }

    /**
     * Get base priority for a body (without dynamic adjustments).
     *
     * @param bodyId The body identifier
     * @return Base priority value
     */
    public int getBasePriority(String bodyId) {
        if (bodyId == null) return 0;

        String lowerBodyId = bodyId.toLowerCase();
        if ("sun".equals(lowerBodyId)) {
            return Integer.MAX_VALUE;
        }

        return BASE_PRIORITIES.getOrDefault(lowerBodyId, 3000);
    }
}
