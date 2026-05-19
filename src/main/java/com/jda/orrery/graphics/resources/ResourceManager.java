package com.jda.orrery.graphics.resources;

import com.jda.orrery.core.logging.Logging;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Shader manager.
 *
 * No resource hierarchies, no weak references, no reference counting — shaders are loaded once
 * and kept for the application lifetime.
 */
public class ResourceManager {
    private static final Logger LOGGER = Logging.logger(ResourceManager.class);

    // Shaders live for application lifetime.
    private final Map<String, Shader> shaderCache = new ConcurrentHashMap<>();

    public ResourceManager() {
        LOGGER.info("ResourceManager initialized");
    }

    /**
     * Get or load a shader. Loaded once, cached for the app lifetime.
     *
     * @param name Shader name (without extension)
     * @return Shader instance (cached or newly loaded)
     */
    public Shader getShader(String name) {
        return shaderCache.computeIfAbsent(name, this::loadShader);
    }

    /**
     * Load a shader from resources.
     *
     * @param name Shader name
     */
    private Shader loadShader(String name) {
        Shader shader = new Shader(name);
        if (shader.isValid()) {
            LOGGER.info("Loaded shader: " + name);
        } else {
            LOGGER.severe("Failed to load shader: " + name);
        }
        return shader;
    }

    /** Dispose all shaders. Should be called on application shutdown. */
    public void dispose() {
        for (Shader shader : shaderCache.values()) {
            shader.dispose();
        }
        shaderCache.clear();
        LOGGER.info("ResourceManager disposed");
    }
}
