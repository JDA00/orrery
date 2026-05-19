#version 410 core

layout(location = 0) in vec3 vertexPosition;
layout(location = 1) in vec3 vertexNormal;     // Now required by vertex format
layout(location = 2) in vec2 vertexTexCoord;   // Now required by vertex format

uniform mat4 mvpMatrix;

void main() {
    gl_Position = mvpMatrix * vec4(vertexPosition, 1.0);
}
