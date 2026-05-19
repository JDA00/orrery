#version 410 core

// Unified celestial vertex shader with UBO.
// Single shader for all celestial bodies; per-body data is uploaded as one
// uniform buffer object per draw call instead of dozens of individual uniforms.

// Vertex attributes
layout(location = 0) in vec3 aPosition;    // Body-fixed position
layout(location = 1) in vec3 aNormal;      // Body-fixed normal
layout(location = 2) in vec2 aParams;      // (theta, phi) parametric coordinates

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
    vec4 bodyGeometry;     // (equatorialRadius, polarRadius, ringInner, ringOuter)
} celestial;

// Outputs to fragment shader
out vec3 fragPosition;         // View space position for lighting
out vec3 fragNormal;           // View space normal for lighting
out vec2 fragTexCoord;         // UV coordinates [0,1] x [0,1]
out vec3 fragModelPos;         // Model space position for ring radial calculations

void main() {
    // Convert parametric (theta, phi) to UV (body-fixed, view-independent).
    float theta = aParams.x;  // 0 to π (north to south)
    float phi = aParams.y;    // 0 to 2π (prime meridian around)
    
    fragTexCoord = vec2(phi / 6.283185307, theta / 3.141592654);

    // Pass model-space position for rings (camera-independent).
    fragModelPos = aPosition;

    // Use the pre-computed MVP matrix for final position.
    gl_Position = celestial.mvpMatrix * vec4(aPosition, 1.0);

    // View-space position computed the same way the MVP matrix was built on the CPU.
    // First transform to world space, then to view space
    // This matches how the MVP was calculated on CPU
    vec4 worldPos = celestial.modelMatrix * vec4(aPosition, 1.0);
    vec4 viewPos = celestial.viewMatrix * worldPos;
    fragPosition = viewPos.xyz;

    // Transform normal using the pre-computed normal matrix.
    // Normal matrix = (ModelView)^-1^T for correct view-space lighting
    fragNormal = normalize(celestial.normalMatrix * aNormal);
}