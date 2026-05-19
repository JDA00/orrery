package com.jda.orrery.graphics.postfx;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Empty VAO + {@code gl_VertexID}-driven fullscreen triangle. macOS strict core profile requires a
 * VAO bound for any draw call; the vertex shader synthesises the three clip-space corners from
 * {@code gl_VertexID} alone, so no VBO is needed.
 */
public class FullScreenTriangle {
    private int vao = 0;

    public void initialize() {
        vao = glGenVertexArrays();
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    public void dispose() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}
