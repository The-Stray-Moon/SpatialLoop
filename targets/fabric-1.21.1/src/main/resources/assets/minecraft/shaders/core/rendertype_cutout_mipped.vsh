#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float CurveRadius;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;


/**
 * Camera-relative world position -> sphere of radius CurveRadius.
 * The horizontal arc length d is the polar angle d / CurveRadius, so walking
 * one block along the surface stays one block; the surface drops by
 * R * (1 - cos(theta)). Uses only Position + ChunkOffset, which vanilla fills
 * with sectionOrigin - cameraPos in world axes.
 */
vec3 spatialloop_bend(vec3 pos) {
    float d = length(pos.xz);
    if (d < 1.0e-4 || abs(CurveRadius) <= 1.0) {
        return pos;
    }
    float theta = d / CurveRadius;
    float scale = sin(theta) / theta;
    return vec3(pos.x * scale, pos.y - CurveRadius * (1.0 - cos(theta)), pos.z * scale);
}

void main() {
    vec3 pos = spatialloop_bend(Position + ChunkOffset);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
