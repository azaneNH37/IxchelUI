#version 150

// The filtered backdrop drawn back inside a box with shaped corners. texCoord is the position in the box in GUI
// pixels; the backdrop texel is the one under the fragment: Region is the snapshot's rectangle in the
// bound target's pixels and UvScale its extent in the texture. Radii: top-left x, y, top-right x, y;
// bottom-right x, y, bottom-left x, y. Corners: the corner shapes in the same corner order (0 round,
// 1 bevel, 2 notch, 3 scoop, 4 square). Opaque 1 when the snapshot came from a target whose alpha is
// not coverage, such as the main framebuffer.
uniform sampler2D Sampler0;
uniform vec4 Region;
uniform vec2 UvScale;
uniform vec2 Size;
uniform vec4 RadiiTop;
uniform vec4 RadiiBottom;
uniform vec4 Corners;
uniform float Opaque;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 p = texCoord;
    bool left = p.x < Size.x * 0.5;
    bool top = p.y < Size.y * 0.5;
    vec2 r = top ? (left ? RadiiTop.xy : RadiiTop.zw) : (left ? RadiiBottom.zw : RadiiBottom.xy);
    vec2 corner = vec2(left ? r.x : Size.x - r.x, top ? r.y : Size.y - r.y);
    vec2 q = (p - corner) / max(r, vec2(1e-4));
    bool inCorner = r.x > 0.0 && r.y > 0.0 && (left ? p.x < corner.x : p.x > corner.x) && (top ? p.y < corner.y : p.y > corner.y);
    float shape = top ? (left ? Corners.x : Corners.y) : (left ? Corners.w : Corners.z);
    // d: 1 on the corner's edge, growing outward, in units of the radius.
    vec2 a = abs(q);
    float d = shape < 0.5 ? length(a)
            : shape < 1.5 ? a.x + a.y
            : shape < 2.5 ? 1.0 + min(a.x, a.y)
            : shape < 3.5 ? 2.0 - length(1.0 - a)
            : 0.0;
    float aa = max(fwidth(d), 1e-4);
    float coverage = inCorner ? clamp((1.0 - d) / aa + 0.5, 0.0, 1.0) : 1.0;
    vec2 uv = (gl_FragCoord.xy - Region.xy) / Region.zw * UvScale;
    vec4 c = texture(Sampler0, uv);
    c = mix(c, vec4(c.rgb, 1.0), Opaque);
    fragColor = c * coverage;
}
