// What an .ixeffect body sees besides its params, before the body in every effect program. Positions,
// sizes and radii are physical pixels from the element's top-left corner, y down; colors are
// premultiplied. A corner radius is the smaller of the CSS corner's two.
uniform float ix_time;         // seconds, wrapping every hour
uniform vec2 ix_mouse;         // the pointer, far away when there is none
uniform float ix_mouse_inside; // 1 when the pointer is over the element's border box, else 0
uniform float ix_scale;        // physical pixels per GUI pixel
uniform vec2 IxSize;
uniform vec4 IxRadius;         // top-left, top-right, bottom-right, bottom-left

in vec2 texCoord;
out vec4 fragColor;

struct IxBox {
    vec2 pos;
    vec2 size;
    vec4 radius;
};

struct IxLayer {
    vec2 pos;
    vec2 size;
    vec4 radius;
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
