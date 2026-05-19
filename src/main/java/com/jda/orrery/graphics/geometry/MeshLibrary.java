package com.jda.orrery.graphics.geometry;

import com.jda.orrery.core.logging.Logging;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared mesh library.
 *
 * Manages shared mesh instances to minimize memory usage and rendering cost.
 *
 * Features: - Lazy loading of meshes on first request - Shared instances across all celestial
 * bodies - Automatic quality selection based on view distance
 */
public class MeshLibrary {
    private static final Logger LOGGER = Logging.logger(MeshLibrary.class);

    private final Map<MeshTopology, Map<SphereMesh.Quality, SphereMesh>> meshCache;

    private int cacheMisses = 0;

    public MeshLibrary() {
        meshCache = new EnumMap<>(MeshTopology.class);

        for (MeshTopology topology : MeshTopology.values()) {
            meshCache.put(topology, new EnumMap<>(SphereMesh.Quality.class));
        }

        LOGGER.info("MeshLibrary initialized");
    }

    /**
     * Get or create a sphere mesh with specified topology and quality.
     *
     * @param topology Mesh topology type
     * @param quality Mesh quality level
     * @return Shared mesh instance
     */
    public SphereMesh get(MeshTopology topology, SphereMesh.Quality quality) {
        Map<SphereMesh.Quality, SphereMesh> topologyCache = meshCache.get(topology);

        SphereMesh mesh = topologyCache.get(quality);
        if (mesh == null) {
            // Create new mesh
            cacheMisses++;
            LOGGER.fine(
                    String.format(
                            "Creating new %s mesh at %s quality (cache miss #%d)",
                            topology, quality, cacheMisses));

            mesh = new SphereMesh(topology, quality);
            topologyCache.put(quality, mesh);
        }

        return mesh;
    }

    /**
     * Pick a mesh by texture mapping (cube vs. equirectangular) and screen coverage.
     *
     * @param isCubeMap Whether using cube map texture
     * @param isEquirectangular Whether using equirectangular texture
     * @param screenSize Approximate size on screen (0-1)
     */
    public SphereMesh getAutoQuality(
            boolean isCubeMap, boolean isEquirectangular, float screenSize) {
        // Select topology based on texture type
        MeshTopology topology = MeshTopology.getRecommended(isCubeMap, isEquirectangular);

        // Select quality based on screen size.
        SphereMesh.Quality quality;
        if (screenSize > 0.5f) {
            quality = SphereMesh.Quality.ULTRA; // Hero object
        } else if (screenSize > 0.2f) {
            quality = SphereMesh.Quality.HIGH; // Prominent planet
        } else if (screenSize > 0.05f) {
            quality = SphereMesh.Quality.MEDIUM; // Visible moon
        } else {
            quality = SphereMesh.Quality.LOW; // Distant asteroid
        }

        return get(topology, quality);
    }

    /**
     * Preload commonly used meshes to avoid stuttering. Called during initialization for smooth
     * runtime performance.
     */
    public void preloadCommon() {
        LOGGER.info("Preloading common meshes...");

        // Only preload UV_SPHERE meshes — always used for equirectangular textures.
        // CelestialRenderer is hard-coded to use UV_SPHERE for proper texture mapping
        get(MeshTopology.UV_SPHERE, SphereMesh.Quality.HIGH); // Planets and Moon
        get(MeshTopology.UV_SPHERE, SphereMesh.Quality.MEDIUM); // Distant bodies

        LOGGER.info(String.format("Preloaded %d meshes", getTotalMeshCount()));
    }

    /** Dispose all cached meshes and free GPU resources. */
    public void dispose() {
        LOGGER.info("Disposing MeshLibrary...");

        int disposed = 0;
        for (Map<SphereMesh.Quality, SphereMesh> topologyCache : meshCache.values()) {
            for (SphereMesh mesh : topologyCache.values()) {
                mesh.dispose();
                disposed++;
            }
            topologyCache.clear();
        }

        LOGGER.info("Disposed " + disposed + " meshes.");
    }

    /** Get total number of cached meshes. */
    public int getTotalMeshCount() {
        int count = 0;
        for (Map<SphereMesh.Quality, SphereMesh> topologyCache : meshCache.values()) {
            count += topologyCache.size();
        }
        return count;
    }
}
