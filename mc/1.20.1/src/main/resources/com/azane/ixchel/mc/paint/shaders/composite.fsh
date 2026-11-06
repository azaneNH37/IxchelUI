#version 150

// A premultiplied layer at an opacity, times its mask's alpha and its clip shape's coverage. Under 1
// composites it over white first, so that where it is transparent a MIN blend equation (darken)
// keeps what is under it.
//
// Masked 1: Sampler1 is the mask, at texCoord * MaskScale. ClipKind: 0 none, 1 the rounded box Box
// (x, y, width, height) with elliptic corners RadiiTop (top-left x, y, top-right x, y) and
// RadiiBottom (bottom-right x, y, bottom-left x, y), 2 the polygon of the first PointCount Points by
// the nonzero rule, 3 by the evenodd rule. The shape is in local coordinates: LocalX and LocalY map
// (texCoord, 1) there.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Opacity;
uniform float Under;
uniform float Masked;
uniform vec2 MaskScale;
uniform float ClipKind;
uniform vec3 LocalX;
uniform vec3 LocalY;
uniform vec4 Box;
uniform vec4 RadiiTop;
uniform vec4 RadiiBottom;
uniform float PointCount;
// ClipPath.MAX_POINTS.
uniform vec2 Points[64];

in vec2 texCoord;
out vec4 fragColor;

// The signed distance from p to the box's edge, negative inside; near enough an elliptic corner's.
float boxDistance(vec2 p) {
    vec2 lo = Box.xy;
    vec2 hi = Box.xy + Box.zw;
    vec2 mid = (lo + hi) * 0.5;
    bool left = p.x < mid.x;
    bool top = p.y < mid.y;
    vec2 r = top ? (left ? RadiiTop.xy : RadiiTop.zw) : (left ? RadiiBottom.zw : RadiiBottom.xy);
    if (r.x > 0.0 && r.y > 0.0) {
        vec2 q = p - vec2(left ? lo.x + r.x : hi.x - r.x, top ? lo.y + r.y : hi.y - r.y);
        if ((left ? q.x < 0.0 : q.x > 0.0) && (top ? q.y < 0.0 : q.y > 0.0)) {
            // The ellipse's implicit function over the length of its gradient.
            vec2 u = q / r;
            float k = length(u);
            return (k - 1.0) * k / max(length(u / r), 1e-6);
        }
    }
    vec2 d = max(lo - p, p - hi);
    return max(d.x, d.y);
}

// The signed distance from p to the polygon's edges, negative inside by its fill rule.
float polygonDistance(vec2 p, bool evenOdd) {
    int n = int(PointCount + 0.5);
    float d = 1e20;
    int winding = 0;
    for (int i = 0; i < 64; i++) {
        if (i >= n) {
            break;
        }
        vec2 a = Points[i];
        vec2 b = Points[i + 1 < n ? i + 1 : 0];
        vec2 e = b - a;
        vec2 w = p - a;
        vec2 off = w - e * clamp(dot(w, e) / max(dot(e, e), 1e-12), 0.0, 1.0);
        d = min(d, dot(off, off));
        float cross = e.x * w.y - e.y * w.x;
        if (a.y <= p.y) {
            if (b.y > p.y && cross > 0.0) {
                winding++;
            }
        } else if (b.y <= p.y && cross < 0.0) {
            winding--;
        }
    }
    bool inside = evenOdd ? (winding & 1) != 0 : winding != 0;
    return inside ? -sqrt(d) : sqrt(d);
}

void main() {
    vec4 c = texture(Sampler0, texCoord) * Opacity;
    if (Masked > 0.5) {
        c *= texture(Sampler1, texCoord * MaskScale).a;
    }
    if (ClipKind > 0.5) {
        vec3 t = vec3(texCoord, 1.0);
        vec2 p = vec2(dot(LocalX, t), dot(LocalY, t));
        float sd = ClipKind < 1.5 ? boxDistance(p) : polygonDistance(p, ClipKind > 2.5);
        // Local units per window pixel, so the edge fades over one pixel at any transform.
        float px = max(0.5 * (length(dFdx(p)) + length(dFdy(p))), 1e-6);
        c *= clamp(0.5 - sd / px, 0.0, 1.0);
    }
    fragColor = c + vec4(Under * (1.0 - c.a));
}
