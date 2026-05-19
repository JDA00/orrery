package com.jda.orrery.graphics.pipeline;

import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

/**
 * Visual rendering profiles for celestial bodies.
 *
 * Separates scientific data (pure, in {@link com.jda.orrery.graphics.materials.MaterialCatalog})
 * from artistic visualization (tuned here for LDR displays, monitor gamut, etc).
 */
public class VisualProfile {

    /**
     * Visual adjustments for specific bodies. These multiply with the scientific material values
     * for artistic control.
     */
    public static class BodyProfile {
        public final Vector3f colorTint; // Multiply with emission/albedo
        public final float brightnessScale; // Overall brightness adjustment
        public final float saturationBoost; // Increase color saturation
        public final float textureBlend; // How much texture vs base color (0-1)

        public BodyProfile(
                Vector3f colorTint,
                float brightnessScale,
                float saturationBoost,
                float textureBlend) {
            this.colorTint = colorTint;
            this.brightnessScale = brightnessScale;
            this.saturationBoost = saturationBoost;
            this.textureBlend = textureBlend;
        }
    }

    // Preset profiles for different body types
    private static final Map<String, BodyProfile> PROFILES = new HashMap<>();

    static {
        // Sun: Emphasize yellow/orange for visual impact
        // Scientific color is (1.0, 0.95, 0.8) - nearly white
        // Visual tint brings out the yellows for recognition
        PROFILES.put(
                "sun",
                new BodyProfile(
                        new Vector3f(1.0f, 0.9f, 0.7f), // Warm yellow tint (gentler than before)
                        1.2f, // Increase brightness (was too dim at 0.8)
                        1.1f, // Slight saturation boost
                        1.0f // Full texture detail (show sunspots)
                        ));

        // Earth: Slightly boost blues for "blue marble" effect
        PROFILES.put(
                "earth",
                new BodyProfile(
                        new Vector3f(1.0f, 1.0f, 1.05f), // Slight blue enhancement
                        1.0f, // Normal brightness
                        1.1f, // Slight saturation boost
                        1.0f // Full texture detail
                        ));

        // Mars: Enhance the red for recognition
        PROFILES.put(
                "mars",
                new BodyProfile(
                        new Vector3f(1.1f, 0.9f, 0.85f), // Boost reds
                        1.0f, // Normal brightness
                        1.2f, // More saturated
                        1.0f // Full texture detail
                        ));

        // Jupiter: Enhance the bands
        PROFILES.put(
                "jupiter",
                new BodyProfile(
                        new Vector3f(1.0f, 0.95f, 0.9f), // Slight warm tint
                        1.0f, // Normal brightness
                        1.15f, // Enhance band contrast
                        1.0f // Full texture detail
                        ));

        // Default profile for other bodies
        BodyProfile defaultProfile =
                new BodyProfile(
                        new Vector3f(1.0f, 1.0f, 1.0f), // No tint
                        1.0f, // Normal brightness
                        1.0f, // Normal saturation
                        1.0f // Full texture detail
                        );

        // Apply default to remaining planets
        PROFILES.put("mercury", defaultProfile);
        PROFILES.put("venus", defaultProfile);
        PROFILES.put("saturn", defaultProfile);
        PROFILES.put("uranus", defaultProfile);
        PROFILES.put("neptune", defaultProfile);
        PROFILES.put("moon", defaultProfile);
    }

    /**
     * Get visual profile for a body.
     *
     * @param bodyId Body identifier (must be lowercase)
     * @return Visual profile, or default if not found
     */
    public static BodyProfile getProfile(String bodyId) {
        // Expect lowercase body IDs (caller normalizes) to avoid allocation.
        return PROFILES.getOrDefault(
                bodyId, new BodyProfile(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f, 1.0f, 1.0f));
    }

    /**
     * Apply visual profile to a color. Used for emission or albedo adjustment.
     *
     * @param baseColor Scientific color from MaterialCatalog
     * @param profile Visual profile to apply
     * @return Visually adjusted color
     */
    public static Vector3f applyProfile(Vector3f baseColor, BodyProfile profile) {
        // Apply color tint
        Vector3f tinted =
                new Vector3f(
                        baseColor.x * profile.colorTint.x,
                        baseColor.y * profile.colorTint.y,
                        baseColor.z * profile.colorTint.z);

        // Apply saturation boost
        if (profile.saturationBoost != 1.0f) {
            float luminance = 0.2126f * tinted.x + 0.7152f * tinted.y + 0.0722f * tinted.z;
            tinted.x = luminance + (tinted.x - luminance) * profile.saturationBoost;
            tinted.y = luminance + (tinted.y - luminance) * profile.saturationBoost;
            tinted.z = luminance + (tinted.z - luminance) * profile.saturationBoost;
        }

        // Apply brightness scale
        tinted.mul(profile.brightnessScale);

        return tinted;
    }
}
