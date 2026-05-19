#version 410 core

out vec2 v_texCoord;

void main() {
    vec2 pos = vec2((gl_VertexID & 1) << 2, (gl_VertexID & 2) << 1) - 1.0;
    gl_Position = vec4(pos, 0.0, 1.0);
    v_texCoord = pos * 0.5 + 0.5;
}
