package com.jda.orrery.graphics.layers;

import com.jda.orrery.graphics.core.DrawContext;

/** Base interface for renderable layers. */
public interface Layer {
    String getName();

    void setName(String name);

    boolean isEnabled();

    void setEnabled(boolean enabled);

    void render(DrawContext dc);

    void dispose(DrawContext dc);
}
