package com.jda.orrery.graphics.layers;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import com.jda.orrery.core.logging.Logging;
import com.jda.orrery.graphics.core.DrawContext;
import com.jda.orrery.graphics.resources.Shader;
import java.nio.FloatBuffer;
import java.util.logging.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

/**
 * Debug layer that renders coordinate system axes and debugging visualizations. Uses the basic
 * shader with solid colors.
 *
 * Helps visualize: - XYZ axes at origin (Red=X, Green=Y, Blue=Z) - Ecliptic plane grid (Gray
 * grid in XZ plane where planets orbit) - Light direction arrow (Yellow arrow showing sun light
 * from +X) - Camera position indicator (Magenta cross at camera location)
 *
 * This helps identify: - If the solar system is in the XZ plane (ecliptic) as expected - If the
 * camera's "up" is aligned with Y axis - If the light is actually coming from the side (+X) as set
 * - Any coordinate system mismatches
 */
public class DebugAxisLayer implements Layer {
    private static final Logger LOGGER = Logging.logger(DebugAxisLayer.class);

    private String name = "Debug Axis Layer";
    private boolean enabled = true;
    private float axisLength = 200.0f;

    // OpenGL resources for each axis
    private int xAxisVAO = -1, xAxisVBO = -1;
    private int yAxisVAO = -1, yAxisVBO = -1;
    private int zAxisVAO = -1, zAxisVBO = -1;

    // Grid resources
    private int gridVAO = -1, gridVBO = -1;
    private int gridLineCount = 0;

    // Light direction arrow
    private int lightArrowVAO = -1, lightArrowVBO = -1;

    // Camera indicator
    private int cameraVAO = -1, cameraVBO = -1;

    private Shader shader;
    private boolean initialized = false;
    private boolean hasLoggedAxes = false;

    // Pre-allocated matrices so the axis layer doesn't leak allocations
    // at 60+ FPS — every work matrix is built once and reused.
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f mvpMatrix = new Matrix4f();
    private final Matrix4f cameraModelMatrix = new Matrix4f();
    private final Matrix4f cameraMvpMatrix = new Matrix4f();

    // Pre-allocated vectors for colors (avoid allocating Vector4f each frame)
    private final Vector4f redColor = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    private final Vector4f greenColor = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    private final Vector4f blueColor = new Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
    private final Vector4f grayColor = new Vector4f(0.3f, 0.3f, 0.3f, 0.5f);
    private final Vector4f yellowColor = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);
    private final Vector4f magentaColor = new Vector4f(1.0f, 0.0f, 1.0f, 1.0f);
    private final Vector3f cameraPos = new Vector3f();

    @Override
    public void render(DrawContext dc) {
        if (!enabled) return;

        // Initialize on first render (when GL context is available)
        if (!initialized) {
            initialize(dc);
            initialized = true;
        }

        if (shader == null || !shader.isValid()) {
            LOGGER.warning("Debug shader not available");
            return;
        }

        // Save state
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST); // Render on top

        // Use shader
        shader.use();

        // Build MVP into pre-allocated matrices.
        dc.getView().fillViewMatrix(viewMatrix);
        dc.getView().fillProjectionMatrix(projectionMatrix);
        modelMatrix.identity(); // Identity for world axes

        // Build MVP: Projection * View * Model
        mvpMatrix.set(projectionMatrix).mul(viewMatrix).mul(modelMatrix);

        shader.setUniform("mvpMatrix", mvpMatrix);

        // Note: macOS only supports line width of 1.0 in core profile
        // glLineWidth(3.0f);  // This causes GL_INVALID_VALUE on macOS

        // Draw X axis - Red (use pre-allocated color)
        shader.setUniform("color", redColor);
        glBindVertexArray(xAxisVAO);
        glDrawArrays(GL_LINES, 0, 2);

        // Draw Y axis - Green (use pre-allocated color)
        shader.setUniform("color", greenColor);
        glBindVertexArray(yAxisVAO);
        glDrawArrays(GL_LINES, 0, 2);

        // Draw Z axis - Blue (use pre-allocated color)
        shader.setUniform("color", blueColor);
        glBindVertexArray(zAxisVAO);
        glDrawArrays(GL_LINES, 0, 2);

        glBindVertexArray(0);

        // Draw axis labels using points at the ends
        // Note: glPointSize may not be supported on all platforms in core profile
        // glPointSize(10.0f);  // This can cause GL_INVALID_VALUE on some systems

        // X end point (reuse red color)
        shader.setUniform("color", redColor);
        glBindVertexArray(xAxisVAO);
        glDrawArrays(GL_POINTS, 1, 1); // Just the end point

        // Y end point (reuse green color)
        shader.setUniform("color", greenColor);
        glBindVertexArray(yAxisVAO);
        glDrawArrays(GL_POINTS, 1, 1);

        // Z end point (reuse blue color)
        shader.setUniform("color", blueColor);
        glBindVertexArray(zAxisVAO);
        glDrawArrays(GL_POINTS, 1, 1);

        glBindVertexArray(0);

        // Draw ecliptic grid (XZ plane) in gray (use pre-allocated color)
        if (gridVAO != -1) {
            shader.setUniform("color", grayColor);
            glBindVertexArray(gridVAO);
            glDrawArrays(GL_LINES, 0, gridLineCount * 2);
            glBindVertexArray(0);
        }

        // Draw light direction arrow in yellow (use pre-allocated color)
        if (lightArrowVAO != -1) {
            shader.setUniform("color", yellowColor);
            glBindVertexArray(lightArrowVAO);
            glDrawArrays(GL_LINES, 0, 10); // 5 lines * 2 vertices
            glBindVertexArray(0);
        }

        // Draw camera indicator in magenta at camera position
        if (cameraVAO != -1 && dc.getView() != null) {
            // Get camera position from view; fill the pre-allocated vector.
            cameraPos.set(
                    (float) dc.getView().getEyeX(),
                    (float) dc.getView().getEyeY(),
                    (float) dc.getView().getEyeZ());

            // Create transform matrix for camera indicator using pre-allocated matrices
            cameraModelMatrix.identity().translation(cameraPos);
            cameraMvpMatrix.set(projectionMatrix).mul(viewMatrix).mul(cameraModelMatrix);

            shader.setUniform("mvpMatrix", cameraMvpMatrix);
            shader.setUniform("color", magentaColor);
            glBindVertexArray(cameraVAO);
            glDrawArrays(GL_LINES, 0, 6); // 3 lines * 2 vertices
            glBindVertexArray(0);

            // Restore original MVP for subsequent rendering
            shader.setUniform("mvpMatrix", mvpMatrix);
        }

        // Log axis info once
        if (!hasLoggedAxes) {
            LOGGER.info("Debug Visualization:");
            LOGGER.info("  Red=X axis, Green=Y axis, Blue=Z axis");
            LOGGER.info("  Gray grid = Ecliptic plane (XZ)");
            LOGGER.info("  Yellow arrow = Light direction (from +X)");
            LOGGER.info("  Magenta cross = Camera position");
            hasLoggedAxes = true;
        }

        // Restore state
        if (depthTest) {
            glEnable(GL_DEPTH_TEST);
        }
    }

    private void initialize(DrawContext dc) {
        LOGGER.info("Initializing debug axis layer");

        // Load basic shader
        try {
            shader = new Shader("basic");
            LOGGER.info("Debug axis shader loaded");
        } catch (Exception e) {
            LOGGER.severe("Failed to load debug axis shader: " + e.getMessage());
            return;
        }

        // Create axis geometry
        createAxisGeometry();

        // Create ecliptic grid
        createEclipticGrid();

        // Create light direction arrow
        createLightArrow();

        // Create camera indicator
        createCameraIndicator();
    }

    private void createAxisGeometry() {
        // The basic shader expects: position, normal, texCoord
        // We'll provide dummy normals and texcoords

        // X axis data
        float[] xAxisData = {
            // position          normal         texcoord
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f, // Origin
            axisLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f // End
        };

        // Y axis data
        float[] yAxisData = {
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f, // Origin
            0.0f,
            axisLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            1.0f,
            0.0f // End
        };

        // Z axis data
        float[] zAxisData = {
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f, // Origin
            0.0f,
            0.0f,
            axisLength,
            1.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f // End
        };

        // Create VAOs and VBOs for each axis
        int[] xResult = createAxisVAO(xAxisData);
        xAxisVAO = xResult[0];
        xAxisVBO = xResult[1];
        int[] yResult = createAxisVAO(yAxisData);
        yAxisVAO = yResult[0];
        yAxisVBO = yResult[1];
        int[] zResult = createAxisVAO(zAxisData);
        zAxisVAO = zResult[0];
        zAxisVBO = zResult[1];

        LOGGER.info("Debug axis geometry created");
    }

    private int[] createAxisVAO(float[] data) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data).flip();

        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES; // 3 pos + 3 normal + 2 texcoord

        // Position attribute (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // Normal attribute (location 1) - dummy but required by shader
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // TexCoord attribute (location 2) - dummy but required by shader
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);

        return new int[] {vao, vbo};
    }

    private void createEclipticGrid() {
        // Create a grid in the XZ plane (ecliptic plane)
        int gridSize = 10;
        float spacing = 50.0f;
        float extent = gridSize * spacing;

        // Calculate number of lines: (gridSize*2 + 1) lines in each direction
        int linesPerAxis = gridSize * 2 + 1;
        gridLineCount = linesPerAxis * 2; // X and Z directions

        // Each line has 2 vertices, each vertex has 8 floats
        float[] gridData = new float[gridLineCount * 2 * 8];
        int index = 0;

        // Lines parallel to X axis
        for (int i = -gridSize; i <= gridSize; i++) {
            float z = i * spacing;
            // Start point
            gridData[index++] = -extent; // x
            gridData[index++] = 0.0f; // y
            gridData[index++] = z; // z
            gridData[index++] = 0.0f; // normal x
            gridData[index++] = 1.0f; // normal y
            gridData[index++] = 0.0f; // normal z
            gridData[index++] = 0.0f; // texcoord u
            gridData[index++] = 0.0f; // texcoord v

            // End point
            gridData[index++] = extent; // x
            gridData[index++] = 0.0f; // y
            gridData[index++] = z; // z
            gridData[index++] = 0.0f; // normal x
            gridData[index++] = 1.0f; // normal y
            gridData[index++] = 0.0f; // normal z
            gridData[index++] = 1.0f; // texcoord u
            gridData[index++] = 0.0f; // texcoord v
        }

        // Lines parallel to Z axis
        for (int i = -gridSize; i <= gridSize; i++) {
            float x = i * spacing;
            // Start point
            gridData[index++] = x; // x
            gridData[index++] = 0.0f; // y
            gridData[index++] = -extent; // z
            gridData[index++] = 0.0f; // normal x
            gridData[index++] = 1.0f; // normal y
            gridData[index++] = 0.0f; // normal z
            gridData[index++] = 0.0f; // texcoord u
            gridData[index++] = 0.0f; // texcoord v

            // End point
            gridData[index++] = x; // x
            gridData[index++] = 0.0f; // y
            gridData[index++] = extent; // z
            gridData[index++] = 0.0f; // normal x
            gridData[index++] = 1.0f; // normal y
            gridData[index++] = 0.0f; // normal z
            gridData[index++] = 1.0f; // texcoord u
            gridData[index++] = 0.0f; // texcoord v
        }

        int[] gridResult = createAxisVAO(gridData);
        gridVAO = gridResult[0];
        gridVBO = gridResult[1];
        LOGGER.info("Ecliptic grid created");
    }

    private void createLightArrow() {
        // Create an arrow pointing from origin along +X axis (where light comes from)
        float arrowLength = 150.0f;
        float arrowHeadSize = 20.0f;

        // Arrow consists of a line and a cone-like head (simplified as 3 lines)
        float[] arrowData = {
            // Main arrow line
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f, // Origin
            arrowLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f, // Tip

            // Arrow head - top
            arrowLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            arrowLength - arrowHeadSize,
            arrowHeadSize * 0.5f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f,

            // Arrow head - bottom
            arrowLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            arrowLength - arrowHeadSize,
            -arrowHeadSize * 0.5f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f,

            // Arrow head - front
            arrowLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            arrowLength - arrowHeadSize,
            0.0f,
            arrowHeadSize * 0.5f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f,

            // Arrow head - back
            arrowLength,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            arrowLength - arrowHeadSize,
            0.0f,
            -arrowHeadSize * 0.5f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f
        };

        int[] arrowResult = createAxisVAO(arrowData);
        lightArrowVAO = arrowResult[0];
        lightArrowVBO = arrowResult[1];
        LOGGER.info("Light direction arrow created");
    }

    private void createCameraIndicator() {
        // Create a simple cross/asterisk shape to mark camera position
        // This will be dynamically positioned based on camera location
        float size = 10.0f;

        float[] cameraData = {
            // X line
            -size,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            size,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f,

            // Y line
            0.0f,
            -size,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            size,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f,

            // Z line
            0.0f,
            0.0f,
            -size,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            size,
            0.0f,
            1.0f,
            0.0f,
            1.0f,
            0.0f
        };

        int[] cameraResult = createAxisVAO(cameraData);
        cameraVAO = cameraResult[0];
        cameraVBO = cameraResult[1];
        LOGGER.info("Camera indicator created");
    }

    @Override
    public void dispose(DrawContext dc) {
        if (xAxisVBO != -1) {
            glDeleteBuffers(xAxisVBO);
            xAxisVBO = -1;
        }
        if (xAxisVAO != -1) {
            glDeleteVertexArrays(xAxisVAO);
            xAxisVAO = -1;
        }
        if (yAxisVBO != -1) {
            glDeleteBuffers(yAxisVBO);
            yAxisVBO = -1;
        }
        if (yAxisVAO != -1) {
            glDeleteVertexArrays(yAxisVAO);
            yAxisVAO = -1;
        }
        if (zAxisVBO != -1) {
            glDeleteBuffers(zAxisVBO);
            zAxisVBO = -1;
        }
        if (zAxisVAO != -1) {
            glDeleteVertexArrays(zAxisVAO);
            zAxisVAO = -1;
        }
        if (gridVBO != -1) {
            glDeleteBuffers(gridVBO);
            gridVBO = -1;
        }
        if (gridVAO != -1) {
            glDeleteVertexArrays(gridVAO);
            gridVAO = -1;
        }
        if (lightArrowVBO != -1) {
            glDeleteBuffers(lightArrowVBO);
            lightArrowVBO = -1;
        }
        if (lightArrowVAO != -1) {
            glDeleteVertexArrays(lightArrowVAO);
            lightArrowVAO = -1;
        }
        if (cameraVBO != -1) {
            glDeleteBuffers(cameraVBO);
            cameraVBO = -1;
        }
        if (cameraVAO != -1) {
            glDeleteVertexArrays(cameraVAO);
            cameraVAO = -1;
        }

        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        initialized = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Debug axis layer " + (enabled ? "enabled" : "disabled"));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public void setAxisLength(float length) {
        this.axisLength = length;
        // Would need to recreate geometry if changing after initialization
        if (initialized) {
            LOGGER.warning("Cannot change axis length after initialization");
        }
    }
}
