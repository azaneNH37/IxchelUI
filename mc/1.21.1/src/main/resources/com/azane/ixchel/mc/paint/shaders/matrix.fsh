#version 150

// CSS filter color functions as one affine map of the unpremultiplied color.
uniform sampler2D Sampler0;
uniform mat4 ColorMatrix;
uniform vec4 ColorOffset;
uniform vec4 Limit;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, clamp(texCoord, Limit.xy, Limit.zw));
    vec4 u = c.a > 0.0 ? vec4(c.rgb / c.a, c.a) : vec4(0.0);
    vec4 r = clamp(ColorMatrix * u + ColorOffset, 0.0, 1.0);
    fragColor = vec4(r.rgb * r.a, r.a);
}
