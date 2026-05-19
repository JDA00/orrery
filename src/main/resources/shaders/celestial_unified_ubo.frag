#version 410 core

// Unified Celestial Fragment Shader with UBO
// Single shader for ALL bodies using Uniform Buffer Objects
// Reduces uniform calls by grouping all per-body data

// Inputs from vertex shader
in vec3 fragPosition;  // View space position
in vec3 fragNormal;    // View space normal
in vec2 fragTexCoord;  // UV coordinates
in vec3 fragModelPos;  // Model space position for rings

// Uniform Buffer Object for all per-body data (std140 layout)
// Note: macOS OpenGL 4.1 doesn't support explicit binding, set from application
layout(std140) uniform CelestialData {
    mat4 modelMatrix;      // Body orientation in world (64 bytes)
    mat4 viewMatrix;       // Camera transform (64 bytes)
    mat4 projectionMatrix; // Perspective projection (64 bytes)
    mat4 mvpMatrix;        // Pre-computed MVP (64 bytes)
    mat3 normalMatrix;     // Pre-computed normal transform (48 bytes)
    vec3 sunPosition;      // Sun position in view space (16 bytes with padding)
    vec3 albedo;           // Material albedo color (16 bytes with padding)
    vec3 emission;         // Emission color (16 bytes with padding)
    vec4 materialParams;   // roughness, metallic, emissionStrength, isEmissive packed
    // Per-body geometry (visual-space): equatorial radius, polar radius,
    // ring inner radius (planet-radii units, 0 if no rings), ring outer radius.
    // Shader gates "this body has rings" on bodyGeometry.w > 0.
    vec4 bodyGeometry;
} celestial;

// Remaining uniforms — shared across frames

// Illumination (from SceneIllumination) - rarely changes
uniform vec3 sunColor;
uniform float sunIntensity;
uniform vec3 ambientColor;
uniform float ambientStrength;

// Illumination profile - set once per session
uniform struct IlluminationProfile {
    float physicalWeight;
    float artisticWeight;
    float falloffExponent;
    float brightnessBoost;
    float minIntensity;
    float maxIntensity;
} illumination;

// Body type - could be moved to UBO if needed
uniform int bodyType;  // 0=rocky, 1=gas, 2=icy
uniform int bodyId;    // CelestialBodyId ordinal (SUN=0, MERCURY=1, …, MOON=9)

// Texture - changes per body but less frequently than transforms
uniform sampler2DArray textureArray;
uniform int textureLayer;
uniform vec2 texCoordScale;
uniform bool hasTexture;
uniform bool isRing;  // Special flag for ring rendering

// For ring shadow calculation (planet center / spin axis in view space).
// Per-body radii (equatorial, polar) and ring extent live in
// celestial.bodyGeometry (see UBO declaration above).
uniform vec3 planetPositionView;
uniform vec3 planetAxisView;
uniform int ringTextureLayer;        // Ring texture layer (-1 if body has no rings)
uniform float atmosphericRefraction; // Limb refraction in radians (penumbra contribution)

// Ring-specific optical properties from MaterialCatalog.
// Note: per-region opticalDepth is derived from texture-color analysis in the
// ring branch (see ring optical-depth block below). The catalog's normal-incidence optical
// depth is retained as material provenance but not consumed by the shader.
uniform float ringForwardG;          // Henyey-Greenstein g for forward scatter
uniform float ringBackwardG;         // Henyey-Greenstein g for backscatter
uniform float ringParticleMix;       // Mix ratio: 0=all large, 1=all small
uniform float saturnshineAlbedo;     // Saturn's albedo for reflected light

// Output
out vec4 fragColor;

// Phase functions
//
// Lommel-Seeliger / Lambert mix in BRDF form (no cos(incidence) baked in).
// The caller supplies NdotL exactly once via the outer rendering-equation
// multiplication (Lo = ... * NdotL), avoiding NdotL² double-counting.
// Preserves the regolith limb/terminator brightening characteristic of the Moon.
float LunarLambertBRDF(vec3 L, vec3 V, vec3 N) {
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float lommelBRDF = 1.0 / (NdotL + NdotV + 0.001);
    return mix(1.0, lommelBRDF, 0.5);
}

// Henyey-Greenstein phase function for particle scattering
// g < 0: backscattering, g > 0: forward scattering, g = 0: isotropic
float HenyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    float num = 1.0 - g2;
    float denom = pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);
    return num / (4.0 * 3.14159265 * denom);
}

// Rayleigh phase function for small ice crystals
float RayleighPhase(float cosTheta) {
    return (3.0 / (16.0 * 3.14159265)) * (1.0 + cosTheta * cosTheta);
}

// HG-based atmospheric phase + edge-brightening approximation.
float AtmosphericPhase(vec3 L, vec3 V, vec3 N, float anisotropy) {
    // Henyey-Greenstein for forward/back scattering
    float cosTheta = dot(L, -V);
    float phase = HenyeyGreenstein(cosTheta, anisotropy);

    // Edge brightening from grazing angle scattering
    float NdotV = max(dot(N, V), 0.0);
    float edgeFactor = pow(1.0 - NdotV, 2.0);
    float edgeBrightening = mix(1.0, 1.5, edgeFactor * 0.25);

    return phase * edgeBrightening;
}

// Fresnel-Schlick approximation
vec3 FresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

// GGX/Trowbridge-Reitz normal distribution
float DistributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;

    float num = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = 3.14159265 * denom * denom;

    return num / denom;
}

// Geometry function (Smith's Schlick-GGX)
float GeometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float num = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return num / denom;
}

float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = GeometrySchlickGGX(NdotV, roughness);
    float ggx1 = GeometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

// Opposition surge — shadow-hiding component only.
//
// Two physically-distinct mechanisms are typically modelled together
// for regolith bodies:
//   - Coherent backscatter: ~2° wave-optics interference peak, ampli-
//     tude inversely scaled by albedo (the original term used
//     mix(0.4, 0.05, albedo)).
//   - Shadow hiding: ~20° geometric inter-grain shadowing.
//
// In real disc-integrated photometry the CB term contributes a measured
// brightening at small phase angles. In this shader, however,
// `phaseAngle = acos(dot(L, V))` is computed per fragment — so the
// narrow CB Gaussian gets locally excited wherever fragment-to-sun and
// fragment-to-camera align, painting a circular highlight at the sub-
// solar point on each rocky body rather than reproducing a disc-level
// brightening. The CB term has therefore been removed for orrery-scale
// rendering. If physically-correct disc-integrated opposition behaviour
// is ever needed, it should be evaluated once per body (using a body-
// level Sun-body-camera phase angle) rather than per fragment.
//
// Shadow hiding is kept: at ~20° angular width it's gentle enough to
// read as surface shading variation rather than a painted spot, and
// still preserves the qualitative "near-full body brighter than half-
// phase body" behaviour that opposition photometry captures.
float OppositionSurge(float phaseAngle, float albedo) {
    float shWidth = 0.35; // ~20 degrees in radians
    float shAmplitude = mix(0.25, 0.1, albedo); // Stronger for rough dark surfaces
    float shadowHiding = shAmplitude / (1.0 + phaseAngle / shWidth);
    return 1.0 + shadowHiding;
}

// Saturnshine phase function: Lambertian disk-integrated phase Φ(α) for a
// uniform-albedo sphere viewed at phase α, with a smooth proximity taper
// to zero at the geometric crescent-visibility cutoff.
//
//   Φ(α) = (sin α + (π − α) cos α) / π
//
// Replaces the hard-cutoff `max(0.0, cos α)` clamp at α = 90° with the
// physically correct smooth gradient (Φ ≈ 0.318 at the terminator, falling
// to 0 at α = π). The proximity taper brings Φ to zero at α = π − asin(R/d)
// where the visible cap of Saturn from a ring fragment in shadow no longer
// extends past the terminator — beyond that angle, Saturnshine is
// geometrically zero regardless of phase.
//
// Args:
//   planetToSun: unit direction from planet center toward sun
//   toPlanetDir: unit direction from fragment toward planet center
//   ratio:       planetRadius / saturnDistance (sets the geometric cutoff)
float SaturnshinePhase(vec3 planetToSun, vec3 toPlanetDir, float ratio) {
    float cosAlpha = dot(planetToSun, -toPlanetDir);
    float alpha = acos(clamp(cosAlpha, -1.0, 1.0));
    const float SHINE_INV_PI = 0.31830988618;
    float phase = max(0.0,
        (sin(alpha) + (3.14159265 - alpha) * cosAlpha) * SHINE_INV_PI);
    float angularRadius = asin(clamp(ratio, 0.0, 0.999));
    float crescentCutoff = 3.14159265 - angularRadius;
    return phase * (1.0 - smoothstep(crescentCutoff - 0.2, crescentCutoff, alpha));
}

// Guarded normalize. Returns the supplied fallback at the singular point
// where the input vector has effectively zero length. The 1e-12 threshold
// corresponds to |v| ≈ 1e-6, well below any geometry that should appear
// in view space. Cost matches normalize() in the non-degenerate case.
vec3 safeNormalize(vec3 v, vec3 fallback) {
    float len2 = dot(v, v);
    return len2 > 1e-12 ? v * inversesqrt(len2) : fallback;
}

void main() {
    // Sample texture (format-agnostic; OpenGL handles sRGB).
    vec4 texColor = vec4(1.0);
    if (hasTexture && textureLayer >= 0) {
        vec3 texCoord;
        if (isRing) {
            // For rings, sample the radial profile texture.
            // V coordinate (0=inner, 1=outer) maps to texture X axis.
            // Force base LOD: the ring texture is aspect-distorted (wide radial,
            // short tangential), so auto-selected mipmaps blur the radial color
            // bands (C → B → A rings) into each other at shallow viewing angles.
            // Clamp the radial coord: the bucket uses GL_REPEAT on S for planet
            // longitudes, but rings are bounded radial profiles — wrap at the
            // inner/outer edge would bleed A-ring content into the C/D edge.
            float ringV = clamp(fragTexCoord.y, 0.001, 0.999);
            texCoord = vec3(ringV, 0.5, float(textureLayer));
            texColor = textureLod(textureArray, texCoord, 0.0);
            // Texture-author intent: alpha encodes ring presence. The inner
            // gap between Saturn's atmosphere and the D ring is authored as
            // (1,1,1, 0), which the lighting math otherwise reads as bright
            // white. Discard transparent fragments to honour that intent.
            if (texColor.a < 0.01) discard;
        } else {
            // Normal texture sampling for planets/moons
            texCoord = vec3(fragTexCoord * texCoordScale, float(textureLayer));
            texColor = texture(textureArray, texCoord);
        }
    }
    
    vec3 finalColor;
    
    // Extract material parameters from packed vec4
    float roughness = celestial.materialParams.x;
    float metallic = celestial.materialParams.y;
    float emissionStrength = celestial.materialParams.z;
    float isEmissive = celestial.materialParams.w;
    
    // Use float comparison for emissive flag
    if (isEmissive > 0.5) {
        // Emissive path (Sun).
        // Solar photosphere rendering based on a 5778K blackbody.

        // Base emission from material (D65 solar spectrum approximation)
        vec3 starColor = celestial.emission * emissionStrength;

        // Apply texture for surface features (sunspots, granulation)
        vec3 surfaceColor = starColor * texColor.rgb;

        // Limb darkening: Sun appears darker at edges due to optical depth
        // Based on solar observations, using Eddington approximation
        vec3 N = normalize(fragNormal);
        vec3 V = normalize(-fragPosition);
        float NdotV = max(dot(N, V), 0.0);

        // Wavelength-dependent limb darkening (Neckel & Labs 1994):
        // u_R=0.45, u_G=0.6, u_B=0.75. Stronger blue darkening shifts
        // the limb toward orange-red while the disc center stays whiter.
        // Eddington's law form unchanged, applied per-channel:
        //   I(μ)/I(0) = 1 - u·(1 - μ), where μ = cos(θ_emergence).
        // For a sphere viewed externally, μ = NdotV directly — no sqrt.
        // (Earlier sqrt() variant softened the curve and made the limb
        // ~20-28% brighter than Eddington predicts at near-limb angles.)
        vec3 u = vec3(0.45, 0.6, 0.75);
        float mu = max(NdotV, 0.001);
        vec3 limbDarkening = 1.0 - u * (1.0 - mu);

        // Apply limb darkening to surface
        finalColor = surfaceColor * limbDarkening;

        // Add subtle chromosphere glow at limb for realism
        float limb = 1.0 - NdotV;
        vec3 chromosphereGlow = vec3(1.0, 0.3, 0.1) * pow(limb, 3.0) * 0.2;
        finalColor += chromosphereGlow * emissionStrength;

    } else if (isRing) {
        // Ring path — Saturn's rings, ice-particle scattering.

        // Local aliases for the per-body UBO geometry. These read once from
        // the UBO and let the rest of the ring path keep reading them by name
        // (planetRadius, ringInnerRadius, ringOuterRadius) without scattered
        // celestial.bodyGeometry references. The GLSL compiler inlines.
        float planetRadius = celestial.bodyGeometry.x;
        float planetPolarRadius = celestial.bodyGeometry.y;
        float ringInnerRadius = celestial.bodyGeometry.z;
        float ringOuterRadius = celestial.bodyGeometry.w;

        // Ring-specific vectors (all in VIEW space)
        vec3 L = normalize(celestial.sunPosition - fragPosition);
        vec3 V = normalize(-fragPosition);

        // Three-normal strategy for proper ring lighting

        // The ring normal in MODEL space is always (0,0,1) — rings lie in the XY plane.
        vec3 ringNormalModel = vec3(0.0, 0.0, 1.0);

        // Transform ring normal to WORLD space (aligned with Saturn's rotation axis).
        vec3 ringNormalWorld = normalize(mat3(celestial.modelMatrix) * ringNormalModel);

        // Transform to VIEW space for calculations.
        vec3 ringNormalView = normalize(mat3(celestial.viewMatrix) * ringNormalWorld);

        // Track actual viewing side (are we looking at top or bottom of ring?)
        bool viewingFromAbove = dot(ringNormalView, V) > 0.0;

        // Create VIEWER-FACING normal for specular/rendering (always faces camera)
        vec3 viewerFacingNormal = viewingFromAbove ? ringNormalView : -ringNormalView;

        // Use the ring normal for solar elevation calculations
        // This should be constant relative to Saturn, not changing with camera
        vec3 projectionNormal = ringNormalView;

        // Calculate radial direction in MODEL SPACE for camera-independence.
        // In model space, Saturn is at origin (0,0,0) and ring is in XY plane
        // This avoids coordinate space mixing issues between scaled positions
        vec3 modelRadial = normalize(vec3(fragModelPos.x, fragModelPos.y, 0.0));

        // Transform model-space radial to view space for lighting.
        // Use the model-view matrix to transform the direction vector
        mat3 modelViewMatrix = mat3(celestial.viewMatrix * celestial.modelMatrix);
        vec3 radialInPlane = normalize(modelViewMatrix * modelRadial);

        // Project radial and light directions onto the ring plane.
        // radialProjected is computed first so lightInPlane can fall back
        // to a guaranteed-planar vector at the L ∥ projectionNormal
        // singularity (sun perpendicular to ring plane); a world-axis
        // fallback would inject a non-planar vector into radialDotLight
        // and break the ring-lighting math.
        vec3 radialProjected = radialInPlane - projectionNormal * dot(radialInPlane, projectionNormal);
        radialProjected = safeNormalize(radialProjected, radialInPlane);

        vec3 lightInPlane = L - projectionNormal * dot(L, projectionNormal);
        lightInPlane = safeNormalize(lightInPlane, radialProjected);

        // Calculate lighting using the projected directions.
        float radialDotLight = dot(radialProjected, lightInPlane);

        // Track light side using consistent normal
        bool lightFromAbove = dot(projectionNormal, L) > 0.0;

        // Calculate NdotV for view-dependent effects (always positive with viewer-facing normal)
        float NdotV = max(dot(viewerFacingNormal, V), 0.0);

        // Get density early since we need it for elevation calculations
        float density = texColor.a;

        // Rings are a flat plane - they receive full sunlight everywhere except:
        // Where Saturn casts a shadow.
        // When solar elevation is low (inter-particle shadowing).
        // The flat ring plane doesn't have self-shadowing like a sphere would

        // For rings, we always start with full illumination potential
        // The actual darkness comes from Saturn's shadow and solar elevation effects
        // NOT from which side we're viewing from
        float NdotL = 1.0;  // Rings are flat - always fully lit unless shadowed

        // Empirical: ring brightness varies dramatically with solar elevation.
        // At equinox (B' = 0°), rings are nearly invisible due to inter-particle shadowing.
        // The effect gradually reduces as the sun rises above the ring plane.

        // Calculate solar elevation using stable view space coordinates
        // Reuse the ring normal calculated above
        vec3 ringNormalViewStable = ringNormalView;  // Already in stable view space

        // Sun direction at this ring fragment (in view space)
        vec3 sunDirAtFragment = normalize(celestial.sunPosition - fragPosition);

        // Solar elevation is the sine of the angle between sun and ring plane
        // This is stable because both vectors are consistently in view space
        float solarElevation = abs(dot(ringNormalViewStable, sunDirAtFragment));

        // Cassini observed that rings remain somewhat dark up to about 20-30° elevation,
        // then brighten gradually, reaching full brightness around 60-70°
        // Use a power curve for more realistic transition based on actual measurements
        float elevationAngleDeg = asin(solarElevation) * 57.2958;  // Convert to degrees

        // Model based on Cassini photometry: dramatic darkening at low angles
        // At 0°: ~10% brightness (nearly invisible)
        // At 10°: ~40% brightness
        // At 30°: ~70% brightness
        // At 60°+: ~100% brightness
        float elevationFactor = pow(solarElevation, 0.4);  // Power curve for gradual transition

        // Apply elevation darkening based on ring density
        // Dense B ring shows stronger effect than sparse C ring
        // BUT don't apply this to NdotL - it will compound with shadow darkness
        float minBrightness = mix(0.4, 0.1, density);  // 60% brightness for C ring, 10% for B ring at equinox
        float elevationDarkening = mix(minBrightness, 1.0, elevationFactor);
        // We'll apply this later to avoid compounding with shadow

        // Calculate Saturn's shadow first to determine if we're in direct sunlight.
        // Ray-ELLIPSOID intersection: Saturn is the most oblate planet (a/c ≈ 1.108),
        // so the umbra projected on the rings is elliptical, not circular.
        // We work in "ellipsoid space" where the planet becomes a unit sphere by
        // scaling perpendicular components by 1/eq and parallel (along spin
        // axis) by 1/polar. For non-oblate bodies (polar == eq) this degenerates
        // to the original ray-sphere math.
        float planetShadow = 1.0;

        // Sun direction taken from PLANET center, not the fragment. The real
        // sun is at ~10⁹ km from Saturn while the rings are ~10⁵ km wide;
        // rays are parallel to within 10⁻⁵ rad, so all ring fragments see
        // effectively the same toSun. Per-fragment toSun would let the
        // renderer's scale-compressed view-space coordinates distort the
        // shadow into a divergent cone (point-source artifact) instead of
        // the correct cylinder. `L` elsewhere in this branch stays
        // per-fragment — that's correct for lighting (phase angle, falloff).
        vec3 toSun = normalize(celestial.sunPosition - planetPositionView);

        // World-space displacement to planet center
        vec3 toPlanet = planetPositionView - fragPosition;

        // Decompose into axial (along spin axis) and perpendicular components.
        vec3 spinAxis = planetAxisView;
        float toPlanetAxial = dot(toPlanet, spinAxis);
        vec3  toPlanetPerp  = toPlanet - spinAxis * toPlanetAxial;
        float toSunAxial    = dot(toSun, spinAxis);
        vec3  toSunPerp     = toSun - spinAxis * toSunAxial;

        // Transform to ellipsoid space (planet becomes unit sphere). Skip the
        // re-normalization for toPlanet (it's a position) but normalize the
        // ray direction since perpendicular and parallel got scaled differently.
        float invEq    = 1.0 / planetRadius;
        float invPolar = 1.0 / max(planetPolarRadius, 1e-6);
        vec3 toPlanet_e = toPlanetPerp * invEq + spinAxis * (toPlanetAxial * invPolar);
        vec3 toSunRaw_e = toSunPerp    * invEq + spinAxis * (toSunAxial    * invPolar);
        vec3 toSun_e    = normalize(toSunRaw_e);

        float tc_e = dot(toPlanet_e, toSun_e);  // distance along ray to closest approach
        if (tc_e > 0.0) {  // Planet is forward of fragment relative to sun
            float distToCenter_e = length(toPlanet_e - toSun_e * tc_e);

            // Penumbra physics on the rings has two contributions:
            //   - Sun's angular radius at Saturn (9.58 AU): 4.85e-4 rad
            //     (R_sun / d = 696,000 / 1.433e9). Geometric, very narrow.
            //   - Saturn's atmosphere: limb refraction broadens the umbra
            //     edge. ~0.4° (Lindal 1985 / Schinder 2011, visible cloud
            //     tops). Sourced from MaterialProperties.atmosphericRefractionRad.
            // In ellipsoid space the unit "sphere" radius is 1, so the
            // penumbra is α = sunAngularRadius + atmosphericRefraction directly
            // (in radians, dimensionless after the 1/R scale).
            float sunAngularRadius = 0.000487;
            float penumbraWidth = sunAngularRadius + atmosphericRefraction;

            if (distToCenter_e < 1.0 - penumbraWidth) {
                // Full umbra - complete shadow
                planetShadow = 0.0;
            } else if (distToCenter_e < 1.0 + penumbraWidth) {
                // Soft penumbra transition
                float t = (distToCenter_e - (1.0 - penumbraWidth)) / (2.0 * penumbraWidth);
                planetShadow = smoothstep(0.0, 1.0, t);
            }
        }

        // Phase angle for scattering calculations. Phase-function physics is
        // captured downstream in the lit/unlit `diffuseFactor` branch
        // (OppositionSurge for lit reflectance; HenyeyGreenstein·exp(-τ/μ)
        // for unlit transmission), so no separate phaseFunction variable
        // here.
        float cosPhase = dot(L, -V);
        float subsurface = 0.0;
        float glowIntensity = 0.0;

        // Particle size ratios from texture RGB encoding
        // R: Large particles (>5cm), B: Small (<1cm)
        // Used by the post-branch sizeColorShift below.
        float largeRatio = texColor.r;
        float smallRatio = texColor.b;

        // Subsurface scattering and glow only in direct sunlight. In shadow,
        // these stay at zero — indirect umbra/penumbra fill is produced by
        // the dedicated saturnshineLight, ringshineLight, and forward-
        // ScatterFill blocks below (Power-of-Ten Rule 1 — no redundant
        // shadow-fill paths).
        if (planetShadow > 0.5) {
            // Subsurface scattering for illuminated rings.
            // Coefficient bumped from 0.4 -> 0.7 to compensate for the lit-side
            // brightness drop when wrap-Lambert was replaced with Lommel-Seeliger
            // (Group 4 minimum re-tune; final tuning deferred).
            float thickness = 1.0 - density * 0.5;
            float scatterAmount = smoothstep(-0.5, 0.5, radialDotLight);
            subsurface = (0.2 + scatterAmount * 0.7) * thickness;

            // Forward scattering glow when backlit
            float backlight = max(0.0, dot(V, L));
            glowIntensity = pow(backlight, 2.0) * (1.0 - density) * 0.4;
        }

        // ---------------------------------------------------------------
        // Lit / unlit branching (Gap E).
        //
        // Lit side  : camera and sun on same face of ring plane → reflectance
        //             dominates. Constant baseline + opposition surge; the
        //             geometric attenuation (solar elevation, view angle) is
        //             delegated to the existing Cassini-fit empirical curves
        //             below (elevationDarkening + viewDarkening). Stacking an
        //             analytical Lommel-Seeliger / Hapke slab term on top
        //             would double-count those curves and collapse lit-ring
        //             brightness toward zero at near-equinox geometry.
        //
        // Unlit side: camera and sun on opposite faces → forward-scattered
        //             transmittance through the slab (backlit Saturn
        //             appearance, e.g. Cassini PIA17172).
        //             Beer-Lambert with cos(incidence) obliquity, using the
        //             per-region optical depth (computed below) so C / A / B
        //             rings differentiate properly when backlit.
        //             Opposition surge is NOT applied here — surge is a
        //             reflection-geometry effect (coherent backscatter +
        //             shadow hiding) that requires source and observer on
        //             the same side of the scattering medium.
        //
        // Approach: empirical Cassini curves drive absolute brightness;
        // analytical formulas are used only where they capture physics the
        // empirical curves do not.
        // ---------------------------------------------------------------

        // Per-region optical depth (Cassini-derived ranges) — moved here from
        // its original position later in the chain so the unlit branch can
        // reference it. Lit branch consumes the same value below.
        float ringRegion = length(texColor.rgb - vec3(0.7, 0.65, 0.5));
        float opticalDepth;
        if (ringRegion < 0.2) {        // B ring (dense, warm colored)
            opticalDepth = mix(1.5, 2.5, density);
        } else if (ringRegion < 0.4) { // A ring
            opticalDepth = mix(0.4, 0.6, density);
        } else {                       // C ring or Cassini division
            opticalDepth = mix(0.05, 0.12, density);
        }

        bool litSide = (lightFromAbove == viewingFromAbove);
        float diffuseFactor;
        if (litSide) {
            // Lit-side reflectance baseline + opposition surge (Déau et al.
            // 2013, Cuzzi et al. 2017). Geometric attenuation handled by the
            // Cassini-fit elevationDarkening + viewDarkening curves below.
            float ringPhaseAngle = acos(clamp(cosPhase, -1.0, 1.0));
            float surge = OppositionSurge(ringPhaseAngle,
                (texColor.r + texColor.g + texColor.b) / 3.0);
            diffuseFactor = surge;
        } else {
            // Unlit-side transmission. Per-region τ from above gives proper
            // C / A / B differentiation under backlit geometry: C ring
            // transmits ~80% (τ≈0.1), B ring near-opaque (τ≈2.0).
            // cosI floored at 0.2 prevents exp(-τ/very-small) underflow
            // at near-equinox. Result floor at 0.05 lets dense regions
            // (B ring) read as proper silhouette while keeping all
            // regions visibly distinguishable from absolute black.
            float cosI = max(abs(dot(ringNormalView, L)), 0.2);
            float trans = exp(-opticalDepth / cosI);
            float phaseHG = HenyeyGreenstein(cosPhase, ringForwardG);
            diffuseFactor = max(0.05, trans * phaseHG * 4.0);
        }

        // Ice material properties — Saturn's rings are 95–99% water ice with a warm, creamy cast.
        vec3 ringColor = texColor.rgb;

        // Apply particle size-based color variation
        // Large particles (R channel): warmer, golden tones
        // Small particles (B channel): cooler, bluer tones
        // largeRatio / smallRatio lifted to outer scope above the
        // planetShadow > 0.5 branch.
        vec3 sizeColorShift = vec3(1.0, 0.95, 0.85) * largeRatio +
                              vec3(0.9, 0.92, 1.0) * smallRatio;

        // Ring color from corrected MaterialCatalog with particle size effects
        // Match Saturn's body albedo mixing (70% strength) for consistent shadow brightness
        vec3 baseColor = ringColor * mix(vec3(1.0), celestial.albedo, 0.7) * mix(vec3(1.0), sizeColorShift, 0.3);

        // Distance falloff - match Saturn body's calculation exactly
        float distance = length(celestial.sunPosition - fragPosition);
        float distanceAU = distance * 0.1;  // Normalize to AU scale
        float falloff = sunIntensity / (distanceAU * distanceAU);  // Same as Saturn body
        falloff = clamp(falloff, illumination.minIntensity, illumination.maxIntensity);  // Use same clamp

        // Basic diffuse with lit/unlit branching factor (replaces wrappedDiffuse)
        vec3 diffuse = baseColor * diffuseFactor;

        // Scattering effects (already reduced in calculation above)
        vec3 scatter = baseColor * subsurface * 0.5;  // Further reduced
        vec3 glow = baseColor * glowIntensity * 0.2;   // Minimal glow

        // Very subtle warm specular highlights. safeNormalize falls back
        // to V at the L = -V degeneracy (sun and camera on opposite sides
        // of fragment) so NdotH = dot(viewerFacingNormal, V) ≪ 1 in
        // practice and pow(NdotH, 512) suppresses the specular at the
        // singular point rather than spiking it (which fallback to N
        // would do).
        vec3 H = safeNormalize(L + V, V);
        float NdotH = max(dot(viewerFacingNormal, H), 0.0);
        float spec = pow(NdotH, 512.0) * density * 0.008;
        vec3 specular = vec3(1.0, 0.94, 0.88) * spec;  // Don't pre-multiply by shadow

        // Saturn's shadow calculation moved earlier in the code.
        // Per-region opticalDepth (Beer-Lambert τ for ring material) is
        // now computed above the lit/unlit branch so both paths share it.

        // Viewing angle darkening: rings appear darker when viewed edge-on
        // This is because we're looking through more particles (increased optical depth)
        float viewAngleFactor = abs(dot(ringNormalView, V));  // cos(θ) where θ is angle from normal

        // When nearly edge-on (viewAngleFactor ~0), darken significantly
        // When face-on (viewAngleFactor ~1), no darkening
        // Use a curve that matches Cassini observations
        float viewDarkening = mix(0.3, 1.0, pow(viewAngleFactor, 0.3));  // Edge-on is 30% brightness

        // Slab transmission to observer (Beer-Lambert through ring particles).
        // Consumed by ringshine assembly and the forwardScatterFill multi-
        // scatter floor — both indirect-light sources have to exit through
        // the slab to reach the camera. (Saturnshine handles its own
        // slab transfer via Chandrasekhar reflected/transmitted forms
        // below — see Fix 1.) μ_view floor at 0.1 prevents
        // exp(-τ/very-small) underflow at edge-on viewing.
        float muView = max(viewAngleFactor, 0.1);
        float slabTransmit = exp(-opticalDepth / muView);

        // PROPERLY SEPARATED LIGHTING EFFECTS

        // Direct sunlight (NOT pre-multiplied by shadow — would double-darken).
        vec3 directLight = (diffuse + specular) * falloff;

        // Scattering effects.
        vec3 scatteredLight = (scatter + glow) * falloff;

        // Shadow envelope. Phase-angle physics is captured upstream in the
        // lit/unlit `diffuseFactor` branch (OppositionSurge for lit
        // reflectance; HenyeyGreenstein·exp(-τ/μ) for unlit transmission) — a
        // separate post-multiply by mix(0.95, 1.05, phaseFunction) here would
        // double-count the phase function and is removed (the prior form was a
        // vestigial artifact from before the lit/unlit split, and `mix` was
        // also unclamped — HG values exceed 1 in narrow scattering peaks, so
        // the post-multiply was extrapolating outside [0.95, 1.05]).
        //
        // shadowedLight tracks `planetShadow` directly. `planetShadow` is
        // already a smoothstep over the ray-ellipsoid penumbra width (see
        // shadow block above), so wrapping it in another smoothstep(0, 0.1, …)
        // saturates aggressively and breaks the (1 − shadowedLight) gates
        // downstream that drive Saturnshine / ringshine / forwardScatterFill.
        // Collapsing the gate fixes the partition for the indirect-light
        // paths that actually evaluate in the penumbra:
        //   - saturnshineLight:   source-gated planetShadow < 0.9; smooth
        //                         contribution through most of the penumbra.
        //   - ringshineLight:     source-gated by ringshineGate (smoothstep
        //                         around 0.15) for performance — see ringshine
        //                         block. Stays narrow but smoothly tapered.
        //   - forwardScatterFill: indirectIncident = saturnshineRaw +
        //                         ringshineRaw, so its Saturnshine-sourced
        //                         portion contributes through the broader
        //                         band, and the ringshine-sourced portion
        //                         remains narrow.
        //
        // Direct-light envelope is also fixed, but litContribution is then
        // multiplied downstream by finalViewDarkening · litElevation (final-
        // combination block), both keyed on planetShadow. The effective
        // direct envelope is therefore the product of three planetShadow-
        // keyed factors, not just · planetShadow alone.
        float shadowedLight = planetShadow;
        vec3 litContribution = (directLight + scatteredLight) * shadowedLight;

        // Saturnshine contribution (Saturn's reflected sunlight on rings).
        //
        // Two-stage model:
        //   (a) Raw flux at the ring fragment: Lambertian disc-integrated
        //       phase Φ(α) modulated by Saturn's solid angle Ω, Bond albedo,
        //       and an empirical day-side reflectance color.
        //         E_received = (E_sun · A_bond · Φ(α) · Ω) / π
        //       - A_bond is Saturn's Bond albedo (0.342, Voyager/Cassini)
        //       - Φ(α) is the SaturnshinePhase helper (Lambertian disk with
        //         crescent-visibility taper at the geometric cutoff)
        //       - Ω is the exact disc solid angle 2π(1 − cos α) where
        //         sin α = R/d.
        //
        //   (b) Slab radiative transfer via Chandrasekhar-style single-
        //       scattering (after Chandrasekhar 1960; Cuzzi et al. 2002,
        //       2009; Hedman et al. 2013). Replaces the previous
        //       nearFraction + farFraction · slabTransmit blanket Beer-
        //       Lambert split with the proper analytic forms (Liou 2002
        //       §6.4; Sobolev 1975 Ch. 5):
        //
        //         Reflected (same-face source/observer):
        //           R = μ₀/(μ + μ₀) · [1 − exp(−τ(1/μ + 1/μ₀))]
        //
        //         Transmitted (opposite-face):
        //           T = μ₀/(μ − μ₀) · [exp(−τ/μ) − exp(−τ/μ₀)]   (μ ≠ μ₀)
        //
        //       Both forms always evaluate ≥ 0 (correlated signs of
        //       (μ − μ₀) and the bracket). At μ = μ₀ the L'Hôpital limit
        //       is T = (τ/μ) · exp(−τ/μ); we switch to it on
        //       |μ − μ₀| < 0.01 since denominator-only clamping would
        //       zero the numerator simultaneously and underestimate
        //       transmission across the resonance band.
        //
        //       ω₀ ≈ 0.95 = single-scattering albedo of water ice
        //       (Cassini UVIS). Slab particles' phase function P(α_scat)
        //       is now evaluated at the Saturnshine scattering geometry
        //       (incoming from Saturn, outgoing to viewer) using the
        //       same two-population HG mix as the lit branch and ring-
        //       shine — large particles backscatter, small forward-
        //       scatter, catalog ringParticleMix sets the balance.
        //       For the typical Cassini umbra observation geometry
        //       (sun behind viewer ≈ 180° backscatter), large particles
        //       dominate and HG_back peaks. The 4π factor converts our
        //       sr⁻¹-normalized HG to the Cuzzi/Liou ∫P dΩ = 4π
        //       convention required by the formulas above.
        //
        //       μ₀ = Saturnshine incidence cosine ≈ |solarElevation|.
        //       Saturn's lit-hemisphere centroid sits at elevation B'
        //       above the ring plane; at equinox B' = 0 the centroid is
        //       in-plane, so Saturnshine grazes the slab and goes to
        //       zero — geometrically correct (μ₀ → 0 ⇒ R, T → 0).
        //       0.05 floor to keep the formulas finite.
        //       μ = view cosine ≡ viewAngleFactor.
        //
        //       The lit-hemisphere face split (topFraction = 0.5 + 0.5·
        //       sin B') is unchanged — it still distributes Saturnshine
        //       flux between the two faces. What changes is the exit-
        //       path treatment: near-face fraction reflects (R) and
        //       far-face fraction transmits (T), both with τ-dependent
        //       attenuation built into the analytic formulas rather
        //       than applied as a Beer-Lambert post-multiply. This
        //       drives the Cassini-observed C >> A > B umbra ordering:
        //       T's exp(−τ/μ₀) suppresses optically thick regions while
        //       R's bracket saturates near 1 for thick slabs but is
        //       gated by the small μ₀/(μ + μ₀) prefactor.
        //
        // saturnshineRaw is preserved (without baseColor or shadow envelope)
        // so the multi-scatter floor below can source from incident flux.
        vec3 saturnshineRaw = vec3(0.0);
        float saturnshineGeom = 0.0;
        if (planetShadow < 0.9) {
            vec3 toPlanetDir = normalize(planetPositionView - fragPosition);
            vec3 planetToSun = normalize(celestial.sunPosition - planetPositionView);
            float saturnDistance = length(planetPositionView - fragPosition);
            float ratio = planetRadius / saturnDistance;
            float saturnPhase = SaturnshinePhase(planetToSun, toPlanetDir, ratio);

            float saturnSolidAngle = (ratio < 1.0)
                ? 6.28318530718 * (1.0 - sqrt(1.0 - ratio * ratio))
                : 6.28318530718;

            // Saturn's day-side reflectance color (Cassini ISS measurements)
            vec3 saturnColor = vec3(0.47, 0.44, 0.36);

            const float INV_PI = 0.31830988618;
            saturnshineRaw = sunColor * saturnColor * saturnshineAlbedo * saturnPhase
                           * saturnSolidAngle * falloff * INV_PI;

            // Chandrasekhar single-scattering slab transfer.
            const float SINGLE_SCATTER_ALBEDO_ICE = 0.95;
            float mu  = max(viewAngleFactor,           0.05);
            float mu0 = max(abs(solarElevation),       0.05);

            // Reflected: positive in all geometries.
            float chandraReflect  = mu0 / (mu + mu0)
                                  * (1.0 - exp(-opticalDepth * (1.0/mu + 1.0/mu0)));

            // Transmitted: switch to the L'Hôpital analytic limit at μ ≈ μ₀.
            //   lim_{μ→μ₀} μ₀/(μ−μ₀) · [exp(−τ/μ) − exp(−τ/μ₀)]
            //     = μ₀ · (τ/μ₀²) · exp(−τ/μ₀) = (τ/μ) · exp(−τ/μ)  at μ = μ₀
            float chandraTransmit;
            float muDiff = mu - mu0;
            if (abs(muDiff) < 0.01) {
                chandraTransmit = (opticalDepth / mu) * exp(-opticalDepth / mu);
            } else {
                chandraTransmit = mu0 / muDiff
                                * (exp(-opticalDepth/mu) - exp(-opticalDepth/mu0));
            }

            // Two-face split: lit-hemisphere bias on solar elevation.
            // Same linear ramp as before; τ-dependence has moved into R, T.
            float sunElev = dot(ringNormalView, sunDirAtFragment);
            float topFraction = 0.5 + 0.5 * clamp(sunElev, -1.0, 1.0);
            float nearFraction = viewingFromAbove ? topFraction : (1.0 - topFraction);
            float farFraction  = 1.0 - nearFraction;

            // Slab particles' single-scattering phase function at the
            // Saturnshine scattering geometry. Incoming direction = light
            // from Saturn (-toPlanetDir); outgoing = view direction V.
            //   cosScatter = dot(-toPlanetDir, V) ≡ -dot(toPlanetDir, V)
            // clamp guards numerical drift outside [-1, 1] which would
            // corrupt HG's denominator.
            //
            // Two-population HG mix matches the lit branch and ringshine —
            // large particles backscatter (g_back), small particles
            // forward-scatter (g_fwd); catalog-supplied ringParticleMix
            // sets the population balance.
            //
            // HenyeyGreenstein returns sr⁻¹ values normalized so ∫P dΩ = 1;
            // the Cuzzi et al. 2002 / Liou §6.4 Chandrasekhar form uses P
            // normalized to ∫P dΩ = 4π. Multiply by 4π to convert.
            float cosScatter = clamp(-dot(toPlanetDir, V), -1.0, 1.0);
            float largePhase = HenyeyGreenstein(cosScatter, ringBackwardG);
            float smallPhase = HenyeyGreenstein(cosScatter, ringForwardG);
            const float FOUR_PI = 12.56637061;
            float slabPhase = mix(largePhase, smallPhase, ringParticleMix) * FOUR_PI;

            saturnshineGeom = SINGLE_SCATTER_ALBEDO_ICE
                            * slabPhase
                            * (nearFraction * chandraReflect
                             + farFraction  * chandraTransmit);
        }
        // Visible Saturnshine. (1 - shadowedLight) gates to umbra/inner-
        // penumbra; matches the forwardScatterFill and ringshine envelopes.
        vec3 saturnshineLight = saturnshineRaw * saturnshineGeom * baseColor
                              * (1.0 - shadowedLight);

        // Ambient light — always present, as on Saturn's body.
        // This ensures shadow regions are never completely black
        vec3 ambient = ambientColor * ambientStrength * baseColor;

        // Inter-ring scattering (ringshine) via analytical disc integration.
        // Each shadowed fragment receives radiance from N stratified lit-ring
        // samples spanning the C-ring inner edge to A-ring outer edge:
        //   E = Σ (sunColor·albedo·density·cos(elevation)·HG(θ_scatter)·dA·falloff) / d²
        // Samples are gated by sun-visibility (sample not in Saturn's umbra)
        // and receiver-visibility (Saturn does not occlude fragment→sample).
        //
        // Ring particles modelled as point scatterers with Henyey-Greenstein
        // phase function. The cos(emit) factor for slab-to-slab in-plane
        // radiative transfer is omitted: a thin Lambertian slab has zero
        // coplanar form factor (both fragment and samples lie in the ring
        // plane, so any (sample→fragment) direction is orthogonal to the
        // ring normal). Treating each sample as a volumetric scatterer with
        // 1/d² falloff and HG phase — the conventional real-time approach
        // for ring-particle inter-scattering.
        //
        // Approximations: single bounce; multi-scattering ignored; stratified
        // centre-of-cell sampling at 12 azimuth × 3 radial = 36 samples;
        // HG mix uses the catalog particle ratio uniformly (not per-sample).
        //
        // Replaces the previous empirical
        //   ringshineLight = baseColor * ringshineContribution * falloff * 0.02
        // where ringshineContribution was a hand-tuned scalar without
        // physical grounding.
        // Ringshine is evaluated only in deep umbra / inner penumbra for
        // performance — 36 dependent textureLod calls per fragment, not
        // globally affordable. After the shadowedLight = planetShadow
        // collapse, a hard `if (planetShadow < 0.15)` cutoff would step
        // ringshineLight from ~85% of its max down to 0 at the threshold
        // (the (1 − shadowedLight) envelope is ≈ 0.85 there). smoothstep
        // around 0.15 (band 0.12 → 0.20) replaces the hard edge with a
        // taper; multiplied into the assembly below, it smoothly fades
        // ringshine at the band edges.
        float ringshineGate = 1.0 - smoothstep(0.12, 0.20, planetShadow);
        vec3 ringshineRaw = vec3(0.0);
        if (ringshineGate > 0.0) {
            const int N_RADIAL = 3;
            const int N_AZIMUTH = 12;
            const float TWO_PI = 6.28318530718;

            // Consistent ring-plane basis in view space, derived from
            // model-space axes so all samples share one basis regardless of
            // fragment azimuth. Don't reuse the per-fragment radialInPlane —
            // each fragment would otherwise integrate over a different basis.
            mat3 modelToView = mat3(celestial.viewMatrix * celestial.modelMatrix);
            vec3 ringX = normalize(modelToView * vec3(1.0, 0.0, 0.0));
            vec3 ringY = normalize(modelToView * vec3(0.0, 1.0, 0.0));

            float dr_norm = (ringOuterRadius - ringInnerRadius) / float(N_RADIAL);
            float dphi = TWO_PI / float(N_AZIMUTH);
            float planetRadiusSq = planetRadius * planetRadius;

            for (int j = 0; j < N_RADIAL; j++) {
                float r_norm = ringInnerRadius + (float(j) + 0.5) * dr_norm;
                // r_view = r_norm · planetRadius assumes the model matrix
                // scales the unit-mesh by planetRadius. If the scaling
                // pipeline ever decouples mesh scale from the planetRadius
                // uniform, this is the first thing to check when the
                // integration looks spatially wrong.
                float r_view = r_norm * planetRadius;
                float dr_view = dr_norm * planetRadius;

                // Texture fetch hoisted out of the azimuth loop: the ring
                // texture is radial (samples on the U axis indexed by
                // r_norm; V axis is constant 0.5), so all azimuth samples in
                // this radial bin share one reflectance. Cuts texture fetches
                // from N_RADIAL·N_AZIMUTH = 36 to N_RADIAL = 3 per fragment.
                float v_tex = clamp((r_norm - ringInnerRadius) /
                                    (ringOuterRadius - ringInnerRadius),
                                    0.001, 0.999);
                vec4 sampleTex = textureLod(textureArray,
                                             vec3(v_tex, 0.5,
                                                  float(textureLayer)),
                                             2.0);
                float dA = r_view * dr_view * dphi;

                for (int i = 0; i < N_AZIMUTH; i++) {
                    float phi = (float(i) + 0.5) * dphi;
                    float cp = cos(phi);
                    float sp = sin(phi);

                    vec3 samplePos = planetPositionView
                                   + ringX * (r_view * cp)
                                   + ringY * (r_view * sp);

                    vec3 fragToSample = samplePos - fragPosition;
                    float distSq = dot(fragToSample, fragToSample);
                    float dist = sqrt(distSq);
                    if (dist < 0.01 * planetRadius) continue;
                    vec3 fragToSampleDir = fragToSample / dist;

                    // Receiver visibility: does Saturn occlude fragment→sample?
                    vec3 fragToCenter = planetPositionView - fragPosition;
                    float t_recv = dot(fragToCenter, fragToSampleDir);
                    if (t_recv > 0.0 && t_recv < dist) {
                        vec3 closest = fragToCenter - fragToSampleDir * t_recv;
                        if (dot(closest, closest) < planetRadiusSq) continue;
                    }

                    // Sun visibility: is the sample itself in Saturn's umbra?
                    vec3 sampleToSun = celestial.sunPosition - samplePos;
                    float sunDist = length(sampleToSun);
                    sampleToSun /= sunDist;
                    vec3 sampleToCenter = planetPositionView - samplePos;
                    float t_sun = dot(sampleToCenter, sampleToSun);
                    if (t_sun > 0.0) {
                        vec3 closest = sampleToCenter - sampleToSun * t_sun;
                        if (dot(closest, closest) < planetRadiusSq) continue;
                    }

                    // Slab projected lit area. abs() handles double-sided
                    // rings (samples can illuminate either face).
                    float cosIncidence = abs(dot(sampleToSun, ringNormalView));

                    // Henyey-Greenstein phase: scattering angle is
                    // sun→sample→fragment. Two-population mix matches the
                    // lit-ring path's particle physics (catalog: large
                    // g≈-0.65 backscatter, small g≈+0.6 forward). For typical
                    // umbra geometry (lit samples at azimuths ±90° from sun
                    // direction relative to fragment), scatter angles are
                    // mostly in the forward regime; small particles dominate
                    // single-bounce contribution despite being 30% by mix.
                    float cosScatter = dot(sampleToSun, fragToSampleDir);
                    float largePhase = HenyeyGreenstein(cosScatter, ringBackwardG);
                    float smallPhase = HenyeyGreenstein(cosScatter, ringForwardG);
                    float phase = mix(largePhase, smallPhase, ringParticleMix);

                    // Sample irradiance approximated by fragment's falloff:
                    // sample-vs-fragment sun-distance differential is
                    // O(10⁻⁵) at Saturn distances. sampleTex/dA hoisted to
                    // the outer (radial) loop above.
                    vec3 contribution = falloff * sunColor *
                                        sampleTex.rgb * sampleTex.a *
                                        cosIncidence * phase * dA / distSq;

                    ringshineRaw += contribution;
                }
            }

        }
        // Ringshine assembly: integrated raw flux × fragment reflectance
        // × slab transmission to observer × source gate × shadow envelope.
        //
        // The slabTransmit factor is the dominant photometric differen-
        // tiator: C ring (τ ≈ 0.1) preserves ~80% of integrated flux while
        // B ring (τ ≈ 2.0) attenuates to ~2%. Combined with Saturnshine's
        // Chandrasekhar transmitted form, this drives the Cassini-observed
        // C >> A > B umbra ordering.
        // ringshineGate · (1 − shadowedLight) is intentionally a smooth-
        // source-gate × global-indirect-envelope product — the gate
        // tapers ringshine across the (0.12, 0.20) band; the global
        // envelope tapers across the full penumbra.
        vec3 ringshineLight = ringshineRaw * ringshineGate * baseColor
                            * slabTransmit * (1.0 - shadowedLight);

        // forwardScatterFill: multi-scatter from indirect light.
        //
        // In the umbra, no direct sunlight reaches the slab — the previous
        // formulation that used directLight as a brightness reference was
        // unphysical (the Chandrasekhar single-scatter formula it imple-
        // mented is correct, but it was being fed a fictitious source).
        //
        // Replacement: source the multi-scatter from incident indirect
        // illumination (Saturnshine + ringshine raw fluxes). A fraction
        // (1 - exp(-τ)) of incident flux interacts with the slab; ω₀ of
        // that re-emerges via multi-bounce, approximately isotropically:
        //
        //   I_multi ≈ ω₀ · (1 - exp(-τ)) · I_indirect_incident · scale
        //                   · (0.5 + 0.5 · slabTransmit)
        //
        // The exit factor (0.5 + 0.5 · slabTransmit) accounts for isotropic
        // re-emission: half exits the near face (no attenuation) and half
        // exits the far face (Beer-Lambert attenuated). Without it the
        // model would over-deposit B-ring multi-scatter; with it, B/C ratio
        // tightens to physical expectation.
        //
        // The (1 - exp(-τ)) deposit factor is τ-positive — would invert
        // photometric ordering on its own. But visible Saturnshine has its
        // own τ-dependent slab transfer (Chandrasekhar reflected/trans-
        // mitted) and ringshineLight carries the slabTransmit factor;
        // both are larger in absolute magnitude, so the umbra net stays
        // C-favored. Multi-scatter is a fill, not a primary contributor.
        //
        // The 0.15 scale absorbs the convention gap between the 1-norma-
        // lized HG phase function used in this shader and the 4π-norma-
        // lized Chandrasekhar form, plus pipeline-specific flux scaling.
        // Tuned visually.
        const float SINGLE_SCATTER_ALBEDO = 0.95;  // water ice (Cassini UVIS)
        // ringshineRaw multiplied by ringshineGate so the ringshine-sourced
        // portion of forwardScatterFill tapers smoothly at the gate's outer
        // edge instead of stepping off when the integration cuts out at
        // planetShadow ≥ 0.20. saturnshineRaw is gated by its own
        // planetShadow < 0.9 envelope upstream; no extra factor needed.
        vec3 indirectIncident = saturnshineRaw + ringshineRaw * ringshineGate;
        float multiScatterFraction = SINGLE_SCATTER_ALBEDO
                                   * (1.0 - exp(-opticalDepth))
                                   * 0.15;
        vec3 forwardScatterFill = baseColor * indirectIncident * multiScatterFraction
                                * (0.5 + 0.5 * slabTransmit)
                                * (1.0 - shadowedLight);

        // Final combination.
        // Apply viewDarkening only when NOT in shadow to prevent double darkening
        // In shadow, visibility comes from ambient/Saturnshine, not view angle
        float finalViewDarkening = mix(1.0, viewDarkening, planetShadow);

        // Apply elevation darkening ONLY to lit areas, not shadows
        // In shadow (planetShadow=0): no elevation darkening (=1.0)
        // In light (planetShadow=1): full elevation darkening
        float litElevation = mix(1.0, elevationDarkening, planetShadow);

        finalColor = litContribution * finalViewDarkening * litElevation +
                    forwardScatterFill +
                    saturnshineLight +
                    ambient +
                    ringshineLight;

    } else {
        // Non-emissive path — planets and moons.

        // All calculations in VIEW space (camera at origin)
        vec3 N = normalize(fragNormal);
        vec3 L = normalize(celestial.sunPosition - fragPosition);  // Light direction
        vec3 V = normalize(-fragPosition);                         // View direction (camera at 0,0,0)
        // Halfway vector. safeNormalize falls back to V at the L = -V
        // degeneracy; non-emissive specular is gated by NdotL elsewhere
        // so the choice rarely matters here, but the fallback is consistent
        // with the ring branch.
        vec3 H = safeNormalize(L + V, V);

        // Basic dot products
        float NdotL = max(dot(N, L), 0.0);
        float NdotV = max(dot(N, V), 0.0);
        float NdotH = max(dot(N, H), 0.0);
        float VdotH = max(dot(V, H), 0.0);

        // Phase angle for opposition effects
        float phaseAngle = acos(clamp(dot(L, V), -1.0, 1.0));

        // Distance-based falloff with proper AU scaling
        float distance = length(celestial.sunPosition - fragPosition);
        // Normalize distance to approximate AU units for consistent falloff
        // This scale factor accounts for the visual scaling in ScaleManager
        float distanceAU = distance * 0.1;  // Adjust based on scene scale

        // Optimized falloff calculation
        float falloff;
        if (abs(illumination.falloffExponent - 2.0) < 0.01) {
            // Use multiplication for inverse square (faster)
            falloff = sunIntensity / (distanceAU * distanceAU);
        } else if (abs(illumination.falloffExponent - 1.0) < 0.01) {
            // Linear falloff
            falloff = sunIntensity / distanceAU;
        } else {
            // General case with artistic falloff
            float physicalFalloff = sunIntensity / (distanceAU * distanceAU);
            float artisticFalloff = illumination.brightnessBoost * sunIntensity /
                                   pow(distanceAU, illumination.falloffExponent);
            falloff = mix(artisticFalloff, physicalFalloff,
                         illumination.physicalWeight);
        }
        falloff = clamp(falloff, illumination.minIntensity, illumination.maxIntensity);

        // Material properties
        // Apply albedo to properly differentiate planetary brightness
        vec3 albedo = texColor.rgb * mix(vec3(1.0), celestial.albedo, 0.7);
        vec3 F0 = vec3(0.04); // Standard dielectric F0 (IOR 1.5)
        F0 = mix(F0, albedo, metallic); // Metals use albedo as F0

        // Determine atmospheric scattering behavior
        bool hasAtmosphericScattering = false;
        float atmosphericRoughness = roughness;
        float anisotropy = 0.0;

        if (bodyType == 1) {
            // Gas giants — ammonia ice crystal scattering (Jupiter, Saturn)
            // Larger ice particles (~50-150 μm) → slight forward peak.
            hasAtmosphericScattering = true;
            atmosphericRoughness = max(roughness, 0.6);  // Force broad specular
            anisotropy = 0.1;
        } else if (bodyType == 2) {
            // Ice giants — methane ice scattering (Uranus, Neptune)
            // Smaller particles (~10-50 μm) and deeper haze layers; near-
            // isotropic phase, broader specular than the ammonia-ice gas
            // giants. Replaces the previous fragile `roughness < 0.1`
            // heuristic that switched anisotropy on a tunable parameter.
            hasAtmosphericScattering = true;
            atmosphericRoughness = max(roughness, 0.7);
            anisotropy = 0.0;
        } else if (bodyId == 2) {
            // Venus - sulfuric acid droplets
            hasAtmosphericScattering = true;
            atmosphericRoughness = max(roughness, 0.5);
            anisotropy = 0.25;  // edge brightening
        }

        // Calculate specular based on surface type
        vec3 kS, kD;
        float NDF, G;
        vec3 F;

        if (hasAtmosphericScattering) {
            // ATMOSPHERIC PATH - Phase function based

            // Use atmospheric roughness for broader lobe
            NDF = DistributionGGX(N, H, atmosphericRoughness);
            G = GeometrySmith(N, V, L, atmosphericRoughness);

            // Calculate phase-modulated Fresnel
            float atmosphericPhase = AtmosphericPhase(L, V, N, anisotropy);
            F = FresnelSchlick(VdotH, F0) * 0.2 * atmosphericPhase;

            // Reduce specular contribution based on scattering
            float scatteringReduction = mix(0.1, 0.3, smoothstep(0.0, 0.3, roughness));
            kS = F * scatteringReduction;

            // More diffuse for atmospheric bodies
            kD = vec3(1.0) - kS * 0.3;  // Less energy to specular

        } else {
            // SOLID SURFACE PATH - Standard PBR
            NDF = DistributionGGX(N, H, roughness);
            G = GeometrySmith(N, V, L, roughness);
            F = FresnelSchlick(VdotH, F0);

            kS = F;
            kD = vec3(1.0) - kS;
            kD *= 1.0 - metallic;
        }

        // Energy conservation
        kD = clamp(kD, 0.0, 1.0);

        // Calculate specular for all bodies
        vec3 specular;
        vec3 numerator = NDF * G * kS;  // kS includes atmospheric effects
        float denominator = 4.0 * NdotV * NdotL + 0.0001;

        // Scale based on surface type and profile
        float specularScale = mix(0.5, 1.0, illumination.physicalWeight);

        if (bodyType == 0 && !hasAtmosphericScattering) {
            // Rough rocky surfaces (Mercury, Moon, Mars) get reduced
            // specular. (Earth is also bodyType=0, !atmospheric, but is
            // zeroed entirely below — see the bodyId == 3 block.)
            specularScale *= 0.5;
        }

        if (bodyId == 3) {
            // Single-texture Earth has no ocean/cloud masks, so GGX would glint
            // over land and clouds as well as water. Disable specular until the
            // asset pipeline can provide separate ocean and cloud coverage.
            specularScale = 0.0;
        }

        specular = (numerator / denominator) * specularScale;

        // Add subtle rim glow for atmospheric bodies. The outer rendering-equation
        // multiplication (`Lo = (diffuse + specular) * sunColor * falloff * NdotL`)
        // already applies sunColor and falloff once — don't pre-multiply them here.
        // (Earlier double-multiply made the rim term `rim · sunColor² · falloff² · NdotL`,
        // which collapsed to ~zero at outer-planet distances where `falloff² ≈ 1.2e-4`.)
        if (hasAtmosphericScattering) {
            float rimIntensity = pow(1.0 - NdotV, 3.0) * 0.02;
            specular += vec3(rimIntensity);
        }

        // Diffuse BRDF with body-specific enhancements.
        //
        // Pattern note: the diffuse term holds *only* the BRDF (albedo/π plus
        // body-specific modulation). The cos(incidence) term in the rendering
        // equation is applied ONCE, by the outer multiplication in `Lo` below
        // (Lo = ... * NdotL). Don't embed NdotL here — doing so doubles up
        // with the outer multiplication and gives NdotL² fall-off, dimming
        // bodies sharply near the terminator.
        vec3 diffuse = vec3(0.0);
        if (bodyType == 0) { // Rocky / cloud-top / atmospheric bodies (Moon, Mercury, Venus, Earth, Mars, Europa)
            if (bodyId == 2) {
                // Venus: sulfuric-acid cloud tops, NOT regolith. Lommel-
                // Seeliger limb brightening would be wrong (it models photon
                // transport in granular media); opposition surge would be
                // wrong (no inter-grain shadow hiding or coherent backscatter
                // on a smooth cloud deck — Mallama et al. 2006 fit Venus's
                // phase curve as Lambertian at small phase angles).
                // Karkoschka 1994 Minnaert k ≈ 0.9 captures the slight
                // observed cloud-top limb darkening (Cassini ISS / Venus
                // Express). The (k+1)/2 prefactor normalises disc-integrated
                // brightness to match Lambert at disc center.
                const float VENUS_MINNAERT_K    = 0.9;
                const float VENUS_MINNAERT_NORM = (VENUS_MINNAERT_K + 1.0) * 0.5;
                float vkm1 = VENUS_MINNAERT_K - 1.0;
                diffuse = (kD * albedo / 3.14159265)
                        * VENUS_MINNAERT_NORM
                        * pow(max(NdotV, 0.1), vkm1)
                        * pow(max(NdotL, 0.1), vkm1);
            } else if (bodyId == 3) {
                // Earth uses pure Lambert. Lommel-Seeliger limb brightening
                // is regolith physics and wrong for oceans / clouds / atmos-
                // phere; opposition surge requires granular media. Clouds
                // are roughly Lambertian, oceans and land closer to
                // Lambertian than to any limb regime. The visible blue limb
                // halo is the Rayleigh rim term below; layering Minnaert
                // here would double-count it. Specular is zeroed for Earth
                // in the specularScale block. Atmospheric softening of the
                // terminator is handled by the wrap branch.
                //
                // The texture's natural latitude bands (Sahara -> Mediter-
                // ranean -> equatorial Africa) are visible under uniform
                // Lambert shading and would normally be visually masked by
                // cloud cover. Resolving that requires a separate cloud-
                // mask texture layer mixed over the surface BRDF — this
                // shader has no such layer, so the band is a known asset
                // limitation rather than a lighting bug.
                diffuse = kD * albedo / 3.14159265;
            } else {
                // Lommel-Seeliger / Lambert mix in BRDF form. The outer
                // multiplication by NdotL turns this into reflectance equivalent
                // to the original LunarLambert formulation, applied without
                // double-counting.
                float lunarBRDF = LunarLambertBRDF(L, V, N);
                float surge = OppositionSurge(phaseAngle, length(albedo));
                float surgeScale = mix(1.0, 0.7, illumination.artisticWeight);
                diffuse = (kD * albedo / 3.14159265) * lunarBRDF * (1.0 + (surge - 1.0) * surgeScale);
            }
        } else if (bodyType == 1) { // Gas giants (Jupiter, Saturn)
            // Pure Lambert for gas giants (no solid surface scattering)
            diffuse = (kD * albedo / 3.14159265);
            // < 3% limb effect in the visible spectrum.
            float limb = pow(1.0 - NdotV, 2.0) * 0.025;
            diffuse *= (1.0 + limb);
        } else if (bodyType == 2) { // Ice giants (Uranus, Neptune)
            // Minnaert reflectance (Karkoschka 1998, k ≈ 0.6 at visible λ).
            // The full Minnaert form I/F = (k+1)/2 · ω₀ · μ₀^k · μ^(k-1) is
            // assembled by the diffuse term holding μ^(k-1) · μ₀^(k-1) and
            // the outer Lo multiplication by NdotL = μ₀ supplying the final
            // μ₀ to give μ₀^k. The (k+1)/2 prefactor (= 0.8 for k = 0.6)
            // normalises disc-integrated brightness to match observed
            // geometric albedo — without it, the limb-redistribution leaves
            // disc center matched to Lambert and the integral overbright.
            // The 0.1 clamp stays within Karkoschka's validated angular
            // range.
            const float MINNAERT_K = 0.6;
            const float MINNAERT_NORM = (MINNAERT_K + 1.0) * 0.5;
            float km1 = MINNAERT_K - 1.0;
            diffuse = (kD * albedo / 3.14159265)
                    * MINNAERT_NORM
                    * pow(max(NdotV, 0.1), km1)
                    * pow(max(NdotL, 0.1), km1);
        }

        // Atmospheric rim lighting - body specific only
        vec3 rimColor = vec3(0.0);
        // Only bodies with significant atmospheres get rim lighting
        if (bodyType == 1) { // Gas giants have thick atmospheres
            float fresnel = pow(1.0 - NdotV, 3.0);
            rimColor = sunColor * fresnel * 0.015;
        } else if (bodyType == 2) { // Ice giants — deeper photochemical haze
            // Voyager 2 imagery shows more prominent limb halos on Uranus
            // and Neptune than Jupiter or Saturn — methane / hydrocarbon
            // photochemistry produces a thicker stratospheric haze layer
            // that scatters more strongly at the limb.
            float fresnel = pow(1.0 - NdotV, 3.0);
            rimColor = sunColor * fresnel * 0.025;
        }
        // Earth-specific atmospheric rim (Rayleigh-blue limb tint)
        if (bodyId == 3) {
            float fresnel = pow(1.0 - NdotV, 4.0);
            rimColor = vec3(0.4, 0.6, 1.0) * sunColor * fresnel * 0.04;
        }

        // Combine direct lighting with atmospheric wrap where appropriate.
        vec3 Lo;

        if (bodyType == 1 || bodyType == 2 || bodyId == 2) {
            // Gas giants, ice giants, and Venus: pure rendering equation,
            // no NdotL wrap. Diffuse encodes body-type-specific limb
            // behaviour (Lambert + slight brightening for gas; Minnaert
            // k=0.6 for ice giants; Minnaert k=0.9 for Venus's smooth
            // sulfuric-acid cloud tops). Standard rendering equation:
            // Li × BRDF × cos(θ).
            Lo = (diffuse + specular) * sunColor * falloff * NdotL;
        } else {
            // Bodies with surface terminators where atmospheric forward-
            // scatter softens the geometric shadow line. Listed in
            // descending atmospheric thickness for readability.
            float wrapAmount = 0.0;
            if (bodyId == 3) {
                // Earth (thick atmosphere — forward-scatter softens the
                // geometric terminator. 0.1 ≈ 2× Mars given Earth's
                // substantially thicker atmospheric column.)
                wrapAmount = 0.1;
            } else if (bodyId == 4) {
                // Mars (thin atmosphere)
                wrapAmount = 0.05;
            }
            // Mercury, Moon (airless) and Venus (routed to the no-wrap
            // branch above) remain at 0.0.

            // Apply wrap lighting where appropriate
            float wrappedNdotL = NdotL;
            if (wrapAmount > 0.0) {
                wrappedNdotL = max(0.0, (NdotL + wrapAmount) / (1.0 + wrapAmount));
            }

            Lo = (diffuse + specular) * sunColor * falloff * wrappedNdotL;
        }

        // Ring shadow cast onto the body (the equatorial band).
        // Gated on bodyGeometry.w > 0 (this body has rings) AND
        // ringTextureLayer >= 0 (ring texture is bound to this draw).
        //
        // 5-tap solar-disc integration gives a soft penumbra at the ring's
        // edges (between A/B/C rings and at the gap divisions). Each tap
        // tests a slightly-offset sun direction; chord weighting and limb
        // darkening (Schneegans 2022 §3.2, u₁=0.6 for visible) bias the
        // sum toward the brighter centre of the solar disc.
        // Beer-Lambert with 1/cos(i) obliquity correction makes the shadow
        // a thin equatorial line at equinox and a wide diagonal at solstice.
        float ringShadow = 1.0;
        if (celestial.bodyGeometry.w > 0.0 && ringTextureLayer >= 0) {
            float ringInner = celestial.bodyGeometry.z;
            float ringOuter = celestial.bodyGeometry.w;
            float planetRadiusBody = celestial.bodyGeometry.x;
            vec3 N_ring = planetAxisView;
            vec3 toCenter = planetPositionView - fragPosition;

            // Build a tap-offset axis perpendicular to L
            vec3 anyAxis = abs(L.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
            vec3 tapAxis = normalize(cross(L, anyAxis));
            const float SUN_ANG_RAD = 0.000487; // Sun's angular radius at Saturn

            float visAccum = 0.0;
            float wAccum = 0.0;
            // 5 taps: x ∈ {-1, -0.5, 0, +0.5, +1}
            for (int i = -2; i <= 2; i++) {
                float x = float(i) * 0.5;
                float chord = sqrt(max(0.0, 1.0 - x * x));
                float limbDark = 1.0 - 0.6 * (1.0 - chord);
                float w = chord * limbDark;
                vec3 LSample = normalize(L + tapAxis * (x * SUN_ANG_RAD));
                float denom = dot(LSample, N_ring);
                float vis = 1.0;
                // Explicit divisor guard: at equinox the ring
                // plane is edge-on to the sun and denom -> 0. Skipping the
                // term is geometrically correct (shadow collapses to a line).
                if (abs(denom) > 1e-6) {
                    float t = dot(toCenter, N_ring) / denom;
                    if (t > 0.0) {
                        vec3 hit = fragPosition + LSample * t;
                        float rNorm = length(hit - planetPositionView) / planetRadiusBody;
                        if (rNorm > ringInner && rNorm < ringOuter) {
                            float u = (rNorm - ringInner) / (ringOuter - ringInner);
                            // τ profile keeps coarse C/B/A separation but adds
                            // microstructure inside each zone. Pieces:
                            //  - material smoothstep preserves true alpha gaps
                            //  - densityCurve = pow(α, 1.25) shapes the ramp
                            //  - smooth zoneBias replaces hard u-thresholds:
                            //      innerSparse pulls C/D-like inner toward 0.45
                            //      denseMiddle pushes B core toward 1.25
                            //      outerRing softens A toward 0.85
                            //  - alphaDetail amplifies small alpha differences
                            //    where they exist — chiefly C, A, and gap
                            //    transitions. Dense B alpha stddev is only
                            //    ~0.024 across u 0.45..0.60, so this term
                            //    cannot deliver visible striation in B alone.
                            //  - rgbDetail injects the missing dense-B variation
                            //    using same-sample RGB luminance, where the
                            //    asset actually encodes fine ring structure
                            //    (B-band luminance stddev ~0.032, ~33% stronger
                            //    than alpha). Continuous mix, not a classifier;
                            //    no extra texture fetch.
                            //  - τ soft compression (tauLimit=0.75) preserves
                            //    ordering above the limit instead of clipping:
                            //    tau' = K·(1 - exp(-tau/K)) asymptotes to K.
                            //  - cosi floor (0.10) softens grazing-angle blow-up.
                            //  - vis soft lift (mix toward 1.0 by 0.04) replaces
                            //    a hard max() floor. Smaller lift than before
                            //    because rgbDetail now delivers in-band variation
                            //    so we don't need to compensate with brightness.
                            vec4 ringSample = textureLod(textureArray,
                                                          vec3(clamp(u, 0.001, 0.999), 0.5,
                                                               float(ringTextureLayer)),
                                                          0.0);
                            float density = ringSample.a;
                            float material = smoothstep(0.02, 0.16, density);
                            float densityCurve = pow(density, 1.25);
                            float tau = densityCurve * 0.75 * material;

                            float innerSparse = 1.0 - smoothstep(0.08, 0.32, u);
                            float denseMiddle = smoothstep(0.30, 0.45, u)
                                              * (1.0 - smoothstep(0.62, 0.72, u));
                            float outerRing = smoothstep(0.66, 0.78, u);
                            float zoneBias = mix(1.0, 0.45, innerSparse)
                                           * mix(1.0, 1.25, denseMiddle)
                                           * mix(1.0, 0.85, outerRing);
                            tau *= zoneBias;

                            float alphaDetail = mix(0.85, 1.15, smoothstep(0.12, 0.90, density));
                            float lum = dot(ringSample.rgb, vec3(0.299, 0.587, 0.114));
                            float rgbDetail = mix(0.90, 1.10, smoothstep(0.12, 0.55, lum));
                            tau *= alphaDetail * rgbDetail;

                            float tauLimit = 0.75;
                            tau = tauLimit * (1.0 - exp(-tau / tauLimit));

                            float cosi = max(abs(denom), 0.10);
                            vis = exp(-tau / cosi); // Beer-Lambert
                            vis = mix(vis, 1.0, 0.04);
                        }
                    }
                }
                visAccum += w * vis;
                wAccum += w;
            }
            ringShadow = visAccum / max(wAccum, 1e-6);
        }
        Lo *= ringShadow;

        // Simple ambient - space has no sky/ground, just starlight
        // Balanced for visibility while preserving contrast
        vec3 ambient = ambientColor * ambientStrength * albedo;

        // Final combination with rim lighting
        finalColor = Lo + ambient + rimColor * falloff;
    }

    // Calculate final alpha based on body type
    float finalAlpha;
    if (isRing) {
        // For rings, calculate transparency based on density and viewing angle
        float density = texColor.a;

        // Recalculate ring normal for alpha calculation (needs to be in scope)
        vec3 ringNormalModel = vec3(0.0, 0.0, 1.0);
        vec3 ringNormalWorld = normalize(mat3(celestial.modelMatrix) * ringNormalModel);
        vec3 ringNormalForAlpha = normalize(mat3(celestial.viewMatrix) * ringNormalWorld);

        // Angle-dependent opacity approximation: more particles in line-
        // of-sight at edge-on viewing → higher apparent opacity.
        vec3 V_alpha = normalize(-fragPosition);
        float viewAngle = abs(dot(ringNormalForAlpha, V_alpha));  // cos(θ) where θ is angle from normal

        // Per-region optical depths reflect particle-size distribution:
        // - Purple areas (B ring): lack small particles (<5 cm), high density of large particles.
        // - Green/blue areas: mix of particle sizes, more scattering.
        // - Gaps/divisions: very low particle density.

        // The texture alpha already encodes particle density information
        // Use both density and color to determine realistic opacity
        float particleDensity = texColor.a;
        float colorVariance = length(texColor.rgb - vec3(0.5)) * 2.0; // Color deviation from gray

        // High density + low color variance = dense B ring sections (mostly opaque)
        // Medium density + color variance = A ring (semi-transparent)
        // Low density = C ring or gaps (transparent)
        float baseOpacity = mix(0.1, 0.95, pow(particleDensity, 0.5));

        // Adjust for particle size distribution (encoded in color)
        // Uniform gray = large particles only, less scattering
        // Colored = mixed particle sizes, more scattering
        finalAlpha = baseOpacity * mix(0.8, 1.0, 1.0 - colorVariance * 0.3);

        // Angle-dependent opacity: more opaque when viewed edge-on
        // Limit the amplification to prevent excessive opacity at shallow angles
        float angleCorrection = 1.0 / max(viewAngle, 0.3);  // Gentler curve
        finalAlpha = min(finalAlpha * mix(1.0, angleCorrection, 0.5), 0.85);  // Lower max opacity

        // Alpha encodes geometry/material opacity only; shadowing is
        // applied in lighting, not alpha.
    } else {
        // For non-ring objects, use texture alpha (usually 1.0)
        finalAlpha = texColor.a;
    }

    fragColor = vec4(finalColor, finalAlpha);
}
