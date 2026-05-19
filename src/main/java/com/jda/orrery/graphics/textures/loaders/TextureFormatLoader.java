package com.jda.orrery.graphics.textures.loaders;

import com.jda.orrery.graphics.textures.TextureData;
import com.jda.orrery.graphics.textures.TextureResolver;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for texture format loaders. Each format (PNG, KTX2, DDS) implements this
 * interface.
 */
public interface TextureFormatLoader {

    /** Check if this loader can handle the given format. */
    boolean canLoad(TextureResolver.TextureFormat format);

    /** Check if this loader can handle the given file extension. */
    boolean canLoad(String extension);

    /** Load texture asynchronously. Returns a future that completes with the texture data. */
    CompletableFuture<TextureData> loadAsync(Path file, TextureResolver.ResolvedTexture metadata);

    /** Load texture synchronously (for critical textures). */
    TextureData loadSync(Path file, TextureResolver.ResolvedTexture metadata);

    /** Get the name of this loader for logging. */
    String getName();
}
