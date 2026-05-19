package com.jda.orrery.graphics.materials;

import org.joml.Vector3f;

/**
 * Material properties for celestial bodies.
 *
 * Encapsulates physically-based rendering (PBR) parameters derived from published scientific
 * measurements (missions, observatories, IAU). Values are scientific, not artistic.
 */
public class MaterialProperties {
    // PBR base properties
    public final Vector3f albedo; // Bond albedo (energy balance)
    public final float geometricAlbedo; // Geometric albedo (opposition)
    public final float roughness; // Surface roughness (0=smooth, 1=rough)
    public final float metallic; // Metallic property (always 0 for planets)

    // Emission properties (for stars and active bodies)
    public final Vector3f emission; // Emission color
    public final float emissionStrength; // Emission multiplier

    // Ring-specific optical properties (0 values for non-ring bodies)
    public final float opticalDepthNormal; // Optical depth at normal incidence
    public final float forwardScatteringG; // Henyey-Greenstein g for forward scatter
    public final float backwardScatteringG; // Henyey-Greenstein g for backscatter
    public final float particleMixRatio; // Mix ratio: 0=all large, 1=all small particles
    public final float saturnshineAlbedo; // Saturn's albedo for reflected light

    // Atmospheric properties (per-body)
    public final float
            atmosphericRefractionRad; // Limb refraction in radians (penumbra contribution)

    // Metadata for traceability
    public final String source; // Data source (e.g., "NASA Cassini")
    public final String lastUpdated; // Last update date (ISO format)
    public final String notes; // Scientific notes

    /** Private constructor - use builder pattern. */
    private MaterialProperties(Builder builder) {
        this.albedo = new Vector3f(builder.albedo);
        this.geometricAlbedo = builder.geometricAlbedo;
        this.roughness = builder.roughness;
        this.metallic = builder.metallic;
        this.emission = new Vector3f(builder.emission);
        this.emissionStrength = builder.emissionStrength;
        this.opticalDepthNormal = builder.opticalDepthNormal;
        this.forwardScatteringG = builder.forwardScatteringG;
        this.backwardScatteringG = builder.backwardScatteringG;
        this.particleMixRatio = builder.particleMixRatio;
        this.saturnshineAlbedo = builder.saturnshineAlbedo;
        this.atmosphericRefractionRad = builder.atmosphericRefractionRad;
        this.source = builder.source;
        this.lastUpdated = builder.lastUpdated;
        this.notes = builder.notes;
    }

    /** Create a new builder for material properties. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MaterialProperties following NASA data standards.
     *
     * Provides sensible defaults based on typical asteroid properties, ensuring all materials
     * are physically plausible.
     */
    public static class Builder {
        private Vector3f albedo = new Vector3f(0.1f, 0.1f, 0.1f); // Dark asteroid default
        private float geometricAlbedo = -1.0f; // -1 means "use bond albedo"
        private float roughness = 0.5f; // Medium roughness
        private float metallic = 0.0f;
        private Vector3f emission = new Vector3f(0.0f, 0.0f, 0.0f);
        private float emissionStrength = 0.0f;

        // Ring-specific defaults (0 for non-ring bodies)
        private float opticalDepthNormal = 0.0f;
        private float forwardScatteringG = 0.0f;
        private float backwardScatteringG = 0.0f;
        private float particleMixRatio = 0.0f;
        private float saturnshineAlbedo = 0.0f;

        // Atmospheric defaults (0 for airless bodies)
        private float atmosphericRefractionRad = 0.0f;

        private String source = "";
        private String lastUpdated = "";
        private String notes = "";

        /**
         * Set bond albedo (total reflected energy). This is the primary albedo used for energy
         * balance.
         */
        public Builder albedo(float r, float g, float b) {
            this.albedo.set(r, g, b);
            return this;
        }

        /** Set bond albedo from single value (grayscale). */
        public Builder albedo(float value) {
            this.albedo.set(value, value, value);
            return this;
        }

        /**
         * Set geometric albedo (reflectance at opposition). Often higher than bond albedo due to
         * opposition effect.
         */
        public Builder geometricAlbedo(float value) {
            this.geometricAlbedo = value;
            return this;
        }

        /**
         * Set surface roughness for PBR. 0 = perfectly smooth (rare) 1 = completely rough (most
         * rocky bodies)
         */
        public Builder roughness(float value) {
            this.roughness = Math.max(0.0f, Math.min(1.0f, value));
            return this;
        }

        /** Set metallic property. Should always be 0 for celestial bodies (they're dielectrics). */
        public Builder metallic(float value) {
            this.metallic = Math.max(0.0f, Math.min(1.0f, value));
            return this;
        }

        /** Set emission color for self-luminous bodies. */
        public Builder emission(float r, float g, float b) {
            this.emission.set(r, g, b);
            return this;
        }

        /** Set emission strength. 0 = no emission >0 = self-luminous (stars, volcanic bodies) */
        public Builder emissionStrength(float strength) {
            this.emissionStrength = Math.max(0.0f, strength);
            return this;
        }

        /** Set data source for scientific traceability. */
        public Builder source(String source) {
            this.source = source != null ? source : "Unknown";
            return this;
        }

        /** Set last update date (ISO format recommended). */
        public Builder lastUpdated(String date) {
            this.lastUpdated = date != null ? date : "";
            return this;
        }

        /** Add scientific notes about the material. */
        public Builder notes(String notes) {
            this.notes = notes != null ? notes : "";
            return this;
        }

        /**
         * Set ring optical depth at normal incidence. Based on Cassini UVIS/VIMS occultation data.
         */
        public Builder opticalDepth(float normal) {
            this.opticalDepthNormal = Math.max(0.0f, normal);
            return this;
        }

        /**
         * Set Henyey-Greenstein scattering parameters for rings. forwardG: g parameter for small
         * particles (typically 0.6) backwardG: g parameter for large particles (typically -0.65)
         * mixRatio: fraction of small particles (0-1)
         */
        public Builder ringScattering(float forwardG, float backwardG, float mixRatio) {
            this.forwardScatteringG = Math.max(-1.0f, Math.min(1.0f, forwardG));
            this.backwardScatteringG = Math.max(-1.0f, Math.min(1.0f, backwardG));
            this.particleMixRatio = Math.max(0.0f, Math.min(1.0f, mixRatio));
            return this;
        }

        /**
         * Set Saturnshine parameter for ring illumination in shadow. saturnAlbedo: Saturn's bond
         * albedo (typically 0.342). Inter-ring scattering is computed analytically in-shader and is
         * no longer a tunable material parameter.
         */
        public Builder saturnshine(float saturnAlbedo) {
            this.saturnshineAlbedo = Math.max(0.0f, Math.min(1.0f, saturnAlbedo));
            return this;
        }

        /**
         * Set atmospheric refraction at the limb in radians.
         *
         * Bending of grazing sunlight by the body's atmosphere broadens the shadow penumbra cast
         * onto rings (and other receivers) beyond the geometric solar contribution. Saturn: ~0.007
         * rad (Lindal 1985, Voyager radio occultation; Schinder 2011, Cassini). Airless bodies: 0.
         */
        public Builder atmosphericRefraction(float radians) {
            this.atmosphericRefractionRad = Math.max(0.0f, radians);
            return this;
        }

        /** Build the immutable MaterialProperties. */
        public MaterialProperties build() {
            // If geometric albedo not set, use bond albedo
            if (geometricAlbedo < 0) {
                geometricAlbedo = (albedo.x + albedo.y + albedo.z) / 3.0f;
            }
            return new MaterialProperties(this);
        }
    }

    /** Check if this material is emissive. */
    public boolean isEmissive() {
        return emissionStrength > 0.0f;
    }

    /** Get average albedo as single value. */
    public float getAverageAlbedo() {
        return (albedo.x + albedo.y + albedo.z) / 3.0f;
    }

    /** Check if this is a ring material (has optical depth > 0). */
    public boolean isRing() {
        return opticalDepthNormal > 0.0f;
    }

    /**
     * Calculate effective optical depth for a given viewing angle. Used for rings viewed at oblique
     * angles.
     */
    public float getEffectiveOpticalDepth(float cosViewAngle) {
        if (!isRing()) return 0.0f;
        float safeCos = Math.max(0.01f, Math.abs(cosViewAngle));
        return opticalDepthNormal / safeCos;
    }

    @Override
    public String toString() {
        return String.format(
                "MaterialProperties[albedo=(%.3f,%.3f,%.3f), roughness=%.2f, emission=%.2f, source=%s]",
                albedo.x, albedo.y, albedo.z, roughness, emissionStrength, source);
    }
}
