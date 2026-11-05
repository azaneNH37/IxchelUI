// What an .ixeffect body sees besides its params, before the body in every effect program. Positions,
// sizes and radii are physical pixels from the element's top-left corner, y down; colors are
// premultiplied. A corner radius is the smaller of the CSS corner's two; a corner shape is its
// corner-shape: 0 round, 1 bevel, 2 notch, 3 scoop, 4 square.
uniform float ix_time;         // seconds, wrapping every hour
uniform vec2 ix_mouse;         // the pointer, far away when there is none
uniform float ix_mouse_inside; // 1 when the pointer is over the element's border box, else 0
uniform float ix_scale;        // physical pixels per GUI pixel
uniform vec2 IxSize;
uniform vec4 IxRadius;         // top-left, top-right, bottom-right, bottom-left
uniform vec4 IxCorner;         // the corner shapes, in the same order

in vec2 texCoord;
out vec4 fragColor;

struct IxBox {
    vec2 pos;
    vec2 size;
    vec4 radius;
    vec4 corner;
};

struct IxLayer {
    vec2 pos;
    vec2 size;
    vec4 radius;
    vec4 corner;
    vec2 uv; // where ix_content() reads this fragment's own color
};

// Signed distance from p to the edge of the rounded box at the origin: negative inside.
float ix_sd_round_rect(vec2 p, vec2 size, vec4 radius) {
    vec2 h = size * 0.5;
    vec2 q = p - h;
    float r = q.y < 0.0 ? (q.x < 0.0 ? radius.x : radius.y) : (q.x < 0.0 ? radius.w : radius.z);
    r = min(r, min(h.x, h.y));
    vec2 d = abs(q) - h + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

// Signed distance (exact for round, close enough to antialias the others) from p to the edge of the
// box at the origin with shaped corners: negative inside.
float ix_sd_shape_rect(vec2 p, vec2 size, vec4 radius, vec4 corner) {
    vec2 h = size * 0.5;
    vec2 q = p - h;
    int i = q.y < 0.0 ? (q.x < 0.0 ? 0 : 1) : (q.x < 0.0 ? 3 : 2);
    float r = min(radius[i], min(h.x, h.y));
    float shape = corner[i];
    vec2 a = abs(q) - h;
    float box = length(max(a, 0.0)) + min(max(a.x, a.y), 0.0);
    // k: from the corner's inner point toward the box corner, both positive inside its radius box.
    vec2 k = a + r;
    if (r <= 0.0 || shape > 3.5 || k.x <= 0.0 || k.y <= 0.0) {
        return box;
    }
    if (shape < 0.5) {
        return length(k) - r;
    }
    if (shape < 1.5) {
        return max(box, (k.x + k.y - r) * 0.70710678);
    }
    if (shape < 2.5) {
        return max(box, min(k.x, k.y));
    }
    return max(box, r - length(k - vec2(r)));
}

float ix_sd_box(IxBox b) {
    return ix_sd_shape_rect(b.pos, b.size, b.radius, b.corner);
}

float ix_sd_box(IxLayer l) {
    return ix_sd_shape_rect(l.pos, l.size, l.radius, l.corner);
}

// p moved to the center of the GUI pixel it lies in, so an effect can draw on the GUI pixel grid.
vec2 ix_snap(vec2 p) {
    return (floor(p / ix_scale) + 0.5) * ix_scale;
}

// How far round the box p is, by angle from the center: 0 at the top middle, growing clockwise to 1.
float ix_perimeter(vec2 p, vec2 size) {
    vec2 q = p - size * 0.5;
    return fract(atan(q.x, -q.y) / 6.28318531 + 1.0);
}

float ix_perimeter(IxBox b) {
    return ix_perimeter(b.pos, b.size);
}

float ix_perimeter(IxLayer l) {
    return ix_perimeter(l.pos, l.size);
}

// 1 at phase 0 (and 1), falling smoothly to 0 at width either side.
float ix_pulse(float phase, float width) {
    return 1.0 - smoothstep(0.0, width, min(phase, 1.0 - phase));
}

float ix_hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Value noise in 0..1 with a lattice point at each integer.
float ix_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(ix_hash(i), ix_hash(i + vec2(1.0, 0.0)), u.x),
               mix(ix_hash(i + vec2(0.0, 1.0)), ix_hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Four octaves of ix_noise, in 0..1.
float ix_fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * ix_noise(p);
        p = p * 2.0 + vec2(17.0, 31.0);
        a *= 0.5;
    }
    return v / 0.9375;
}
