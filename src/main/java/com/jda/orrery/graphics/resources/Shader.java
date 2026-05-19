package com.jda.orrery.graphics.resources;

import static org.lwjgl.opengl.GL20.*;

import com.jda.orrery.core.logging.Logging;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** Compiled shader program with cached uniform/attribute locations. */
public class Shader {
    private static final Logger LOGGER = Logging.logger(Shader.class);

    private final String id;
    private int program = -1;
    private boolean valid = false;

    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Map<String, Integer> attributeLocations = new HashMap<>();

    /**
     * Load shader by name: resolves to `/shaders/{name}.vert` and `/shaders/{name}.frag` on the
     * classpath.
     */
    public Shader(String name) {
        this(name, "/shaders/" + name + ".vert", "/shaders/" + name + ".frag");
    }

    /** Load shader from explicit vertex and fragment source paths (classpath). */
    public Shader(String id, String vertexPath, String fragmentPath) {
        this.id = id;
        loadFromPaths(vertexPath, fragmentPath);
    }

    private void loadFromPaths(String vertexPath, String fragmentPath) {
        String vertexSource = loadShaderSource(vertexPath);
        String fragmentSource = loadShaderSource(fragmentPath);

        if (vertexSource == null || fragmentSource == null) {
            LOGGER.severe("Failed to load shader sources: " + id);
            return;
        }

        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource, vertexPath);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource, fragmentPath);

        if (vertexShader == -1 || fragmentShader == -1) {
            if (vertexShader != -1) glDeleteShader(vertexShader);
            if (fragmentShader != -1) glDeleteShader(fragmentShader);
            return;
        }

        program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            LOGGER.severe("Failed to link shader " + id + ": " + glGetProgramInfoLog(program));
            glDeleteProgram(program);
            program = -1;
        } else {
            valid = true;
            LOGGER.fine("Loaded shader: " + id);
        }

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private String loadShaderSource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.severe("Shader source not found: " + path);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error loading shader source: " + path, e);
            return null;
        }
    }

    private int compileShader(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String typeStr = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            LOGGER.severe(
                    "Failed to compile "
                            + typeStr
                            + " shader "
                            + name
                            + ": "
                            + glGetShaderInfoLog(shader));
            glDeleteShader(shader);
            return -1;
        }
        return shader;
    }

    public void use() {
        if (!valid) {
            LOGGER.warning("Attempting to use invalid shader: " + id);
            return;
        }
        glUseProgram(program);
    }

    public static void unuse() {
        glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        if (!valid) return -1;
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public int getAttributeLocation(String name) {
        if (!valid) return -1;
        return attributeLocations.computeIfAbsent(
                name,
                n -> {
                    int location = glGetAttribLocation(program, n);
                    if (location == -1) {
                        LOGGER.warning("Attribute not found in shader " + id + ": " + n);
                    }
                    return location;
                });
    }

    public Shader setUniform(String name, int value) {
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform1i(loc, value);
        return this;
    }

    public Shader setUniform(String name, float value) {
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform1f(loc, value);
        return this;
    }

    public Shader setUniform(String name, boolean value) {
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform1i(loc, value ? 1 : 0);
        return this;
    }

    public Shader setUniform(String name, Vector2f value) {
        if (value == null) return this;
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform2f(loc, value.x, value.y);
        return this;
    }

    public Shader setUniform(String name, Vector3f value) {
        if (value == null) return this;
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform3f(loc, value.x, value.y, value.z);
        return this;
    }

    public Shader setUniform(String name, Vector4f value) {
        if (value == null) return this;
        int loc = getUniformLocation(name);
        if (loc != -1) glUniform4f(loc, value.x, value.y, value.z, value.w);
        return this;
    }

    public Shader setUniform(String name, Matrix3f value) {
        if (value == null) return this;
        int loc = getUniformLocation(name);
        if (loc == -1) return this;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            value.get(buffer);
            glUniformMatrix3fv(loc, false, buffer);
        }
        return this;
    }

    public Shader setUniform(String name, Matrix4f value) {
        if (value == null) return this;
        int loc = getUniformLocation(name);
        if (loc == -1) return this;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            value.get(buffer);
            glUniformMatrix4fv(loc, false, buffer);
        }
        return this;
    }

    public String getId() {
        return id;
    }

    public boolean isValid() {
        return valid && program != -1;
    }

    public int getProgram() {
        return program;
    }

    public int getProgramId() {
        return program;
    }

    public void dispose() {
        if (program != -1) {
            glDeleteProgram(program);
            program = -1;
            valid = false;
            uniformLocations.clear();
            attributeLocations.clear();
            LOGGER.fine("Disposed shader: " + id);
        }
    }
}
