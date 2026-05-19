package com.jda.orrery.graphics.textures.loaders;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.textures.TextureData;
import com.jda.orrery.graphics.textures.TextureResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Registry for texture format loaders. Manages all available loaders and selects the appropriate
 * one based on file format.
 */
public class FormatLoaderRegistry {
    private static final Logger LOGGER = Logging.logger(FormatLoaderRegistry.class);

    private final List<TextureFormatLoader> loaders;

    public FormatLoaderRegistry() {
        this.loaders = new ArrayList<>();

        // Register default loaders
        registerDefaultLoaders();
    }

    private void registerDefaultLoaders() {
        // Register in order of preference (high fidelity first).
        // KTX2Loader exists but is not yet implemented — intentionally unregistered.
        register(new ASTCLoader()); // Hardware-accelerated on Apple/ARM
        register(new DDSLoader()); // BC1/BC2/BC3 compressed
        register(new PNGLoader()); // Uncompressed fallback

        LOGGER.info("Registered " + loaders.size() + " texture format loaders");
    }

    /** Register a format loader. */
    public void register(TextureFormatLoader loader) {
        loaders.add(loader);
        LOGGER.fine("Registered loader: " + loader.getName());
    }

    /** Get loader for a specific format. */
    public TextureFormatLoader getLoader(TextureResolver.TextureFormat format) {
        for (TextureFormatLoader loader : loaders) {
            if (loader.canLoad(format)) {
                return loader;
            }
        }

        LOGGER.warning("No loader found for format: " + format);
        return null;
    }

    /** Get loader for a file extension. */
    public TextureFormatLoader getLoaderForFile(String filename) {
        String extension = getExtension(filename);

        for (TextureFormatLoader loader : loaders) {
            if (loader.canLoad(extension)) {
                return loader;
            }
        }

        LOGGER.warning("No loader found for file: " + filename);
        return null;
    }

    /** Load texture using appropriate loader. */
    public TextureData loadTexture(TextureResolver.ResolvedTexture resolved) {

        TextureFormatLoader loader = getLoader(resolved.format);
        if (loader == null) {
            LOGGER.severe("No loader for format: " + resolved.format);
            return null;
        }

        try {
            return loader.loadSync(resolved.path, resolved);
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to load texture "
                            + resolved.path
                            + " with "
                            + loader.getName()
                            + ": "
                            + e.getMessage());
            return null;
        }
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot);
        }
        return "";
    }

    /** Get list of supported extensions. */
    public List<String> getSupportedExtensions() {
        List<String> extensions = new ArrayList<>();
        extensions.add(".astc");
        extensions.add(".dds");
        extensions.add(".png");
        extensions.add(".jpg");
        return extensions;
    }
}
