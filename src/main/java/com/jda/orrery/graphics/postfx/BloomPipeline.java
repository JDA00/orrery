package com.jda.orrery.graphics.postfx;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * Stubbed bloom pipeline. Step 3 owns a 1×1 black RGBA16F texture so the tonemap shader can
 * unconditionally sample {@code u_bloom} without undefined behaviour. Step 4 replaces the internals
 * with a real mip-chain bloom (brightpass + downsample/upsample blurs).
 */
public class BloomPipeline {
    private int bloomTexture = 0;

    public void initialize(int sceneWidth, int sceneHeight) {
        bloomTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, bloomTexture);

        // Explicit zero upload — OpenGL 4.1 has no glClearTexImage and glTexImage2D with null
        // data leaves contents undefined. Profiles with non-zero bloomStrength will sample
        // this every frame.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer zero = stack.mallocFloat(4);
            zero.put(0.0f).put(0.0f).put(0.0f).put(1.0f).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, 1, 1, 0, GL_RGBA, GL_FLOAT, zero);
        }

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /** No-op in Step 3. */
    public void execute() {}

    /** 1×1 doesn't change with viewport. */
    public void resize(int sceneWidth, int sceneHeight) {}

    public int getBloomTexture() {
        return bloomTexture;
    }

    public void dispose() {
        if (bloomTexture != 0) {
            glDeleteTextures(bloomTexture);
            bloomTexture = 0;
        }
    }
}
