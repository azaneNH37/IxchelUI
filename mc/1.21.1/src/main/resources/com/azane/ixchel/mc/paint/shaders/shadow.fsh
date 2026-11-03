#version 150

// The shadow of drop-shadow(): the (blurred) image's coverage, moved and tinted with Color, premultiplied.
uniform sampler2D Sampler0;
uniform vec4 Color;
uniform vec2 Offset;
uniform vec4 Limit;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = Color * texture(Sampler0, clamp(texCoord - Offset, Limit.xy, Limit.zw)).a;
}
