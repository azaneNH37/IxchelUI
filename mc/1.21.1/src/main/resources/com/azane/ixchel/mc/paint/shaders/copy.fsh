#version 150

// The image as it is, for drawing it over a pass's output.
uniform sampler2D Sampler0;
uniform vec4 Limit;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, clamp(texCoord, Limit.xy, Limit.zw));
}
