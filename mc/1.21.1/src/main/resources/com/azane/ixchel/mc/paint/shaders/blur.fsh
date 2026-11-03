#version 150

// One direction of a Gaussian blur of a premultiplied image; Sigma in texels. Wide kernels sample
// sparser, at most 97 taps, relying on linear filtering in between.
uniform sampler2D Sampler0;
uniform vec2 Step;
uniform float Sigma;
uniform vec4 Limit;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float spacing = max(1.0, Sigma / 16.0);
    int r = int(ceil(3.0 * Sigma / spacing));
    float k = -0.5 / (Sigma * Sigma);
    vec4 sum = vec4(0.0);
    float weight = 0.0;
    for (int i = -r; i <= r; i++) {
        float d = float(i) * spacing;
        float w = exp(k * d * d);
        sum += texture(Sampler0, clamp(texCoord + Step * d, Limit.xy, Limit.zw)) * w;
        weight += w;
    }
    fragColor = sum / weight;
}
