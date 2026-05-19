package com.jda.orrery.graphics.core;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.logging.Logging;
import java.util.logging.Logger;

/** OpenGL utility functions for error checking and resource management. */
public class GLUtilities {
    private static final Logger LOGGER = Logging.logger(GLUtilities.class);

    /**
     * Check for OpenGL errors and log them
     *
     * @param location Description of where the check is happening
     * @return true if an error occurred
     */
    public static boolean checkGLError(String location) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorString = getErrorString(error);
            LOGGER.severe("OpenGL Error at " + location + ": " + errorString);
            return true;
        }
        return false;
    }

    /** Convert GL error code to string */
    private static String getErrorString(int error) {
        switch (error) {
            case GL_INVALID_ENUM:
                return "GL_INVALID_ENUM";
            case GL_INVALID_VALUE:
                return "GL_INVALID_VALUE";
            case GL_INVALID_OPERATION:
                return "GL_INVALID_OPERATION";
            case GL_STACK_OVERFLOW:
                return "GL_STACK_OVERFLOW";
            case GL_STACK_UNDERFLOW:
                return "GL_STACK_UNDERFLOW";
            case GL_OUT_OF_MEMORY:
                return "GL_OUT_OF_MEMORY";
            case GL_INVALID_FRAMEBUFFER_OPERATION:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default:
                return "Unknown error: 0x" + Integer.toHexString(error);
        }
    }

    /** Clear all GL errors (useful after initialization) */
    public static void clearGLErrors() {
        while (glGetError() != GL_NO_ERROR) {
            // Just consume the errors
        }
    }

    /** Safe integer query */
    public static int getInteger(int pname) {
        return glGetInteger(pname);
    }

    /** Get maximum texture size */
    public static int getMaxTextureSize() {
        return getInteger(GL_MAX_TEXTURE_SIZE);
    }

    /** Get maximum viewport dimensions */
    public static int[] getMaxViewportDims() {
        int[] dims = new int[2];
        glGetIntegerv(GL_MAX_VIEWPORT_DIMS, dims);
        return dims;
    }

    /** Log OpenGL capabilities */
    public static void logGLCapabilities() {
        LOGGER.info("OpenGL Capabilities:");
        LOGGER.info("  Version: " + glGetString(GL_VERSION));
        LOGGER.info("  Vendor: " + glGetString(GL_VENDOR));
        LOGGER.info("  Renderer: " + glGetString(GL_RENDERER));
        LOGGER.info("  GLSL Version: " + glGetString(GL_SHADING_LANGUAGE_VERSION));
        LOGGER.info("  Max Texture Size: " + getMaxTextureSize());

        int[] viewport = getMaxViewportDims();
        LOGGER.info("  Max Viewport: " + viewport[0] + "x" + viewport[1]);
    }
}
