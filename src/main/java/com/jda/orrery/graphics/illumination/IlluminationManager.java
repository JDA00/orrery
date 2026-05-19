package com.jda.orrery.graphics.illumination;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.core.DrawContext;
import com.jda.orrery.graphics.resources.Shader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.joml.Vector3f;

/**
 * Scene illumination manager.
 *
 * Manages scene lighting through named profiles (Scientific, Cinematic, etc.).
 *
 * Principles: - Profiles define rendering intent; shaders implement algorithms, not policy - All
 * configuration external to shaders - Active profile is logged for reproducibility
 */
public class IlluminationManager {
    private static final Logger LOGGER = Logging.logger(IlluminationManager.class);

    // Current active profile
    private IlluminationProfile activeProfile;

    // Available profiles
    private final Map<String, IlluminationProfile> profiles = new HashMap<>();

    // Sun properties
    private static final Vector3f SUN_COLOR = new Vector3f(1.0f, 0.95f, 0.8f); // D65 adjusted

    /** Illumination profile defining how scene lighting is calculated. Immutable. */
    public static class IlluminationProfile {
        public static final int TONEMAP_REINHARD = 0;
        public static final int TONEMAP_ACES = 1;

        public final String name;

        // Blend between physical and artistic
        public final float physicalWeight; // 0-1: Weight of physical accuracy
        public final float artisticWeight; // 0-1: Weight of artistic visibility

        // Artistic lighting parameters
        public final float falloffExponent; // Controls distance falloff (0.1=gentle, 2.0=harsh)
        public final float brightnessBoost; // Overall brightness multiplier

        // Clamping for visibility
        public final float minimumIntensity; // Never darker than this (0-1)
        public final float maximumIntensity; // Never brighter than this (prevent overexposure)

        // Ambient contribution
        public final Vector3f ambientColor; // Base illumination color
        public final float ambientStrength; // Ambient contribution (0-1)

        // Post-FX
        public final float exposure; // Exposure multiplier consumed by the tonemap pass
        public final float bloomStrength; // 0 disables bloom
        public final float bloomThreshold; // luminance contribution threshold (Step 4)
        public final int toneMapOperator; // TONEMAP_REINHARD or TONEMAP_ACES
        public final float contrastLift; // pow exponent applied after tonemap

        private IlluminationProfile(Builder builder) {
            this.name = builder.name;
            this.physicalWeight = builder.physicalWeight;
            this.artisticWeight = builder.artisticWeight;
            this.falloffExponent = builder.falloffExponent;
            this.brightnessBoost = builder.brightnessBoost;
            this.minimumIntensity = builder.minimumIntensity;
            this.maximumIntensity = builder.maximumIntensity;
            this.ambientColor = new Vector3f(builder.ambientColor);
            this.ambientStrength = builder.ambientStrength;
            this.exposure = builder.exposure;
            this.bloomStrength = builder.bloomStrength;
            this.bloomThreshold = builder.bloomThreshold;
            this.toneMapOperator = builder.toneMapOperator;
            this.contrastLift = builder.contrastLift;

            // Validate weights sum to approximately 1
            float totalWeight = physicalWeight + artisticWeight;
            if (Math.abs(totalWeight - 1.0f) > 0.01f) {
                LOGGER.warning(
                        String.format(
                                "Profile '%s' weights don't sum to 1.0: %.2f", name, totalWeight));
            }
        }

        public static Builder builder(String name) {
            return new Builder(name);
        }

        /** Builder for illumination profiles. Provides sensible defaults. */
        public static class Builder {
            private final String name;
            private float physicalWeight = 0.5f;
            private float artisticWeight = 0.5f;
            private float falloffExponent = 0.5f;
            private float brightnessBoost = 1.0f;
            private float minimumIntensity = 0.1f;
            private float maximumIntensity = 2.0f;
            private Vector3f ambientColor = new Vector3f(0.05f, 0.05f, 0.07f);
            private float ambientStrength = 0.1f;
            private float exposure = 1.0f;
            private float bloomStrength = 0.0f;
            private float bloomThreshold = 1.0f;
            private int toneMapOperator = TONEMAP_REINHARD;
            private float contrastLift = 1.0f;

            public Builder(String name) {
                this.name = name;
            }

            public Builder physicalWeight(float weight) {
                this.physicalWeight = Math.max(0, Math.min(1, weight));
                return this;
            }

            public Builder artisticWeight(float weight) {
                this.artisticWeight = Math.max(0, Math.min(1, weight));
                return this;
            }

            public Builder falloffExponent(float exponent) {
                this.falloffExponent = Math.max(0.1f, Math.min(2.0f, exponent));
                return this;
            }

            public Builder brightnessBoost(float boost) {
                this.brightnessBoost = Math.max(0.1f, Math.min(10.0f, boost));
                return this;
            }

            public Builder minimumIntensity(float min) {
                this.minimumIntensity = Math.max(0, Math.min(1, min));
                return this;
            }

            public Builder maximumIntensity(float max) {
                this.maximumIntensity = Math.max(1, Math.min(10, max));
                return this;
            }

            public Builder ambientColor(float r, float g, float b) {
                this.ambientColor = new Vector3f(r, g, b);
                return this;
            }

            public Builder ambientStrength(float strength) {
                this.ambientStrength = Math.max(0, Math.min(1, strength));
                return this;
            }

            public Builder exposure(float exposure) {
                this.exposure = Math.max(0.1f, Math.min(10.0f, exposure));
                return this;
            }

            public Builder bloomStrength(float strength) {
                this.bloomStrength = Math.max(0.0f, Math.min(5.0f, strength));
                return this;
            }

            public Builder bloomThreshold(float threshold) {
                this.bloomThreshold = Math.max(0.0f, Math.min(10.0f, threshold));
                return this;
            }

            public Builder toneMapOperator(int operator) {
                this.toneMapOperator = (operator == TONEMAP_ACES) ? TONEMAP_ACES : TONEMAP_REINHARD;
                return this;
            }

            public Builder contrastLift(float lift) {
                this.contrastLift = Math.max(0.5f, Math.min(1.5f, lift));
                return this;
            }

            public IlluminationProfile build() {
                return new IlluminationProfile(this);
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "IlluminationProfile[%s: phys=%.2f, art=%.2f, falloff=%.2f, min=%.2f]",
                    name, physicalWeight, artisticWeight, falloffExponent, minimumIntensity);
        }
    }

    /** Preset illumination profiles. */
    public static final class Profiles {
        /**
         * Scientific accuracy profile. Used for research, measurements, and validation. Prioritizes
         * physical correctness with minimal artistic adjustment.
         */
        public static final IlluminationProfile SCIENTIFIC =
                IlluminationProfile.builder("Scientific")
                        .physicalWeight(0.9f) // 90% physical accuracy
                        .artisticWeight(0.1f) // 10% visibility enhancement
                        .falloffExponent(2.0f) // True inverse square law
                        .brightnessBoost(1.0f) // No artificial boost
                        .minimumIntensity(0.01f) // Very dark shadows (realistic space)
                        .maximumIntensity(1.5f) // Limited HDR range
                        .ambientColor(0.01f, 0.01f, 0.015f) // Minimal starlight
                        .ambientStrength(0.05f) // Very subtle
                        .exposure(1.0f)
                        .bloomStrength(0.0f)
                        .bloomThreshold(1.0f)
                        .toneMapOperator(IlluminationProfile.TONEMAP_REINHARD)
                        .contrastLift(1.0f)
                        .build();

        /**
         * Cinematic profile for public outreach. Used for videos, presentations, and public
         * displays. Prioritizes beauty and visibility over strict accuracy.
         */
        public static final IlluminationProfile CINEMATIC =
                IlluminationProfile.builder("Cinematic")
                        .physicalWeight(0.4f) // 40% physical for realism
                        .artisticWeight(0.6f) // 60% artistic for visibility
                        .falloffExponent(0.65f) // Even gentler for better distant visibility
                        .brightnessBoost(5.0f) // Much stronger direct sun
                        .minimumIntensity(0.15f) // Slightly brighter shadows for visibility
                        .maximumIntensity(6.0f) // Higher ceiling for sun
                        .ambientColor(0.02f, 0.02f, 0.03f) // Minimal starlight
                        .ambientStrength(0.1f) // Balanced ambient preserves contrast
                        .exposure(1.5f)
                        .bloomStrength(0.35f)
                        .bloomThreshold(1.0f)
                        .toneMapOperator(IlluminationProfile.TONEMAP_ACES)
                        .contrastLift(0.95f)
                        .build();

        /**
         * Educational profile for teaching. Balance between accuracy and visibility for classroom
         * use.
         */
        public static final IlluminationProfile EDUCATION =
                IlluminationProfile.builder("Education")
                        .physicalWeight(0.5f) // 50/50 balance
                        .artisticWeight(0.5f)
                        .falloffExponent(1.2f) // More realistic falloff
                        .brightnessBoost(1.5f) // Better for classroom visibility
                        .minimumIntensity(0.3f) // Ensure all bodies visible
                        .maximumIntensity(2.0f) // Prevent washout
                        .ambientColor(0.05f, 0.05f, 0.06f) // Less ambient
                        .ambientStrength(0.25f) // Balanced ambient for visibility
                        .exposure(1.2f)
                        .bloomStrength(0.15f)
                        .bloomThreshold(1.0f)
                        .toneMapOperator(IlluminationProfile.TONEMAP_REINHARD)
                        .contrastLift(0.97f)
                        .build();

        /** Print publication profile. High contrast for books and papers. */
        public static final IlluminationProfile PRINT =
                IlluminationProfile.builder("Print")
                        .physicalWeight(0.4f)
                        .artisticWeight(0.6f)
                        .falloffExponent(0.4f) // Gentle for visibility
                        .brightnessBoost(1.3f) // Compensate for paper
                        .minimumIntensity(0.4f) // No pure blacks (ink saving)
                        .maximumIntensity(1.6f) // No pure whites
                        .ambientColor(0.2f, 0.2f, 0.2f) // Gray ambient
                        .ambientStrength(0.2f) // Higher for print clarity
                        .exposure(1.0f)
                        .bloomStrength(0.0f)
                        .bloomThreshold(1.0f)
                        .toneMapOperator(IlluminationProfile.TONEMAP_REINHARD)
                        .contrastLift(0.92f)
                        .build();

        /** Engineering review profile. Flat lighting to see all details. */
        public static final IlluminationProfile ENGINEERING =
                IlluminationProfile.builder("Engineering")
                        .physicalWeight(0.1f) // Mostly ignore physics
                        .artisticWeight(0.9f) // Maximum visibility
                        .falloffExponent(0.1f) // Almost flat
                        .brightnessBoost(1.5f) // Bright overall
                        .minimumIntensity(0.7f) // Very bright shadows
                        .maximumIntensity(1.3f) // Compressed range
                        .ambientColor(0.3f, 0.3f, 0.3f)
                        .ambientStrength(0.3f) // High ambient
                        .exposure(1.5f)
                        .bloomStrength(0.0f)
                        .bloomThreshold(1.0f)
                        .toneMapOperator(IlluminationProfile.TONEMAP_REINHARD)
                        .contrastLift(1.0f)
                        .build();
    }

    public IlluminationManager() {
        // Initialize with default profiles
        profiles.put("Scientific", Profiles.SCIENTIFIC);
        profiles.put("Cinematic", Profiles.CINEMATIC);
        profiles.put("Education", Profiles.EDUCATION);
        profiles.put("Print", Profiles.PRINT);
        profiles.put("Engineering", Profiles.ENGINEERING);

        // Default to cinematic for visual appeal
        activeProfile = Profiles.CINEMATIC;

        LOGGER.info("IlluminationManager initialized with profile: " + activeProfile.name);
    }

    /**
     * Set the active illumination profile.
     *
     * @param profile The profile to activate
     */
    public void setProfile(IlluminationProfile profile) {
        if (profile == null) {
            LOGGER.warning("Attempted to set null profile, ignoring");
            return;
        }

        this.activeProfile = profile;
        LOGGER.info("Switched to illumination profile: " + profile);
    }

    /**
     * Set profile by name.
     *
     * @param profileName Name of the profile
     * @return true if profile was found and set
     */
    public boolean setProfile(String profileName) {
        IlluminationProfile profile = profiles.get(profileName);
        if (profile != null) {
            setProfile(profile);
            return true;
        }
        LOGGER.warning("Profile not found: " + profileName);
        return false;
    }

    /** Initialize the illumination manager. */
    public void initialize(DrawContext dc) {
        LOGGER.info("IlluminationManager ready with profile: " + activeProfile.name);
    }

    /** Per-frame hook. Currently a no-op — the renderer owns sun position transforms. */
    public void update(DrawContext dc) {}

    /** Apply illumination to the current shader. */
    public void apply(DrawContext dc) {
        Shader currentShader = dc.getCurrentShader();
        if (currentShader != null && currentShader.isValid()) {
            applyToShader(currentShader);
        }
    }

    /**
     * Apply illumination parameters to shader uniforms.
     *
     * @param shader The shader to configure
     */
    public void applyToShader(Shader shader) {
        // IlluminationManager only sets profile and color parameters.
        // Position and direction uniforms are handled by the renderer so they
        // get the correct coordinate-frame transforms.

        // Profile parameters as structured uniform
        shader.setUniform("illumination.physicalWeight", activeProfile.physicalWeight);
        shader.setUniform("illumination.artisticWeight", activeProfile.artisticWeight);
        shader.setUniform("illumination.falloffExponent", activeProfile.falloffExponent);
        shader.setUniform("illumination.brightnessBoost", activeProfile.brightnessBoost);
        shader.setUniform("illumination.minIntensity", activeProfile.minimumIntensity);
        shader.setUniform("illumination.maxIntensity", activeProfile.maximumIntensity);

        // Light colors and intensities (NOT positions)
        shader.setUniform("sunColor", SUN_COLOR);
        shader.setUniform("sunIntensity", 1.0f); // Base intensity (modified by profile)
        shader.setUniform("lightColor", SUN_COLOR); // Legacy compatibility

        // Ambient
        shader.setUniform("ambientColor", activeProfile.ambientColor);
        shader.setUniform("ambientStrength", activeProfile.ambientStrength);

        // Lighting mode flags
        shader.setUniform("lightingEnabled", true);

        // For old shaders expecting lightingRealism
        float realism =
                activeProfile.physicalWeight
                        / (activeProfile.physicalWeight + activeProfile.artisticWeight);
        shader.setUniform("lightingRealism", realism);

        // Do not set sunPosition, sunPositionAU, lightDirection, or sunDirection here —
        // CelestialRenderer owns those uniforms (with the right coordinate transforms).
    }

    /** Get current active profile. */
    public IlluminationProfile getActiveProfile() {
        return activeProfile;
    }

    /** Get available profile names. */
    public String[] getProfileNames() {
        return profiles.keySet().toArray(new String[0]);
    }
}
