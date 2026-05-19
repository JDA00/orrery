package com.jda.orrery.graphics.geometry;

/**
 * Mesh topology types for celestial body rendering.
 *
 * Different topologies optimize for different texture mapping strategies; selection is based on
 * body type and available texture formats.
 */
public enum MeshTopology {
    /**
     * Icosphere topology - subdivided icosahedron.
     *
     * Uniform triangle distribution with no polar distortion; suits cube-map textures and physics
     * calculations. Equirectangular textures have seam issues with this topology.
     */
    ICOSPHERE,

    /**
     * UV Sphere topology - latitude/longitude grid.
     *
     * Natural equirectangular mapping with seam handling via duplicated vertices. Polar triangles
     * degenerate; triangle sizes are non-uniform.
     */
    UV_SPHERE,

    /** Reserved for future use; not currently wired. */
    GEOSPHERE,

    /** Reserved for future use; not currently wired. */
    ADAPTIVE_SPHERE;

    /**
     * Get recommended topology for a texture mapping type.
     *
     * @param isCubeMap Whether texture is cube mapped
     * @param isEquirectangular Whether texture is equirectangular
     */
    public static MeshTopology getRecommended(boolean isCubeMap, boolean isEquirectangular) {
        if (isCubeMap) {
            return ICOSPHERE; // Best for cube maps
        } else if (isEquirectangular) {
            return UV_SPHERE; // Best for equirectangular
        } else {
            return ICOSPHERE; // Default for simple textures
        }
    }

    /** Check if topology supports per-vertex UV coordinates. */
    public boolean supportsVertexUV() {
        return this == UV_SPHERE; // Only UV sphere has natural UV mapping
    }

    /** Check if topology requires per-fragment UV calculation. */
    public boolean requiresFragmentUV() {
        return this == ICOSPHERE; // Icosphere needs per-fragment for equirectangular
    }
}
