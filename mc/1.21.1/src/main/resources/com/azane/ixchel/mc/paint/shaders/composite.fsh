#version 150

// A premultiplied layer at an opacity. Under 1 composites it over white first, so that where it is
// transparent a MIN blend equation (darken) keeps what is under it.
uniform sampler2D Sampler0;
uniform float Opacity;
uniform float Under;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord) * Opacity;
    fragColor = c + vec4(Under * (1.0 - c.a));
}
