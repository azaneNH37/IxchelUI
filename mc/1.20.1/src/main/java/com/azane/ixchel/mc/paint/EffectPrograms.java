package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.effect.Effect;
import com.azane.ixchel.core.effect.EffectCode;
import com.azane.ixchel.core.effect.ParamType;
import com.azane.ixchel.core.paint.RoundRect;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The programs of the {@code .ixeffect}s documents link: the file's body between the library in
 * {@code shaders/effect.glsl} and a {@code main} for its kind, its params as uniforms. A document's
 * effects are {@linkplain #compile compiled} when it loads, which reports a GLSL error as
 * {@code effect/compile-failed} at the body's line. A program is kept per host and effect id and
 * rebuilt when the file's text changes; the old one is closed once the new one compiles, so a
 * document kept after a failed reload still draws. Programs are named {@code <modid>_ixchel_fx<n>},
 * a new {@code n} per build, as vanilla keeps compiled shaders by name. Render thread only.
 */
final class EffectPrograms {
    private static final Map<String, EffectPrograms> BY_HOST = new HashMap<>();
    /** The line of a GLSL compiler message: {@code 0(12)} (NVIDIA) or {@code 0:12} (Mesa, AMD, Intel). */
    private static final Pattern ERROR_LINE = Pattern.compile("\\b0[(:](\\d+)");
    private static final String LIBRARY = library();
    /** Where the pointer is when there is none: far from every element. */
    private static final float AWAY = -1e6f;

    private final String prefix;
    /** The last program of each effect id that compiled. */
    private final Map<String, Program> byId = new HashMap<>();
    /** The last build of each effect id, when it failed and nothing compiled since. */
    private final Map<String, Program> failed = new HashMap<>();
    private final Matrix4f matrix = new Matrix4f();
    private int built;

    /** A build of {@code source}: its program, or the compile error it failed with. */
    private record Program(String source, ShaderInstance shader, SourcePos errorPos, String error) {
    }

    private EffectPrograms(String modid) {
        this.prefix = modid + "_ixchel_";
    }

    static EffectPrograms forHost(String modid) {
        return BY_HOST.computeIfAbsent(modid, EffectPrograms::new);
    }

    /** Builds the programs of {@code effects} whose text changed and reports each that does not compile. */
    void compile(Collection<Effect> effects, Diagnostics diag) {
        for (Effect e : effects) {
            Program good = byId.get(e.id());
            if (good != null && good.source.equals(e.source())) {
                continue;
            }
            Program p = failed.get(e.id());
            if (p == null || !p.source.equals(e.source())) {
                p = build(e);
            }
            if (p.shader == null) {
                failed.put(e.id(), p);
                diag.report(EffectCode.COMPILE_FAILED, p.errorPos, p.error);
            } else {
                if (good != null) {
                    good.shader.close();
                }
                byId.put(e.id(), p);
                failed.remove(e.id());
            }
        }
    }

    /** The last program of {@code e}'s file that compiled; {@code null} when none did. */
    ShaderInstance program(Effect e) {
        Program p = byId.get(e.id());
        return p == null ? null : p.shader;
    }

    /**
     * Sets what the body sees: the params from {@code values}, lengths turned to physical pixels, the
     * size and radii of {@code shape}, the time and the pointer at {@code (mouseX, mouseY)} in the
     * shape's GUI coordinates, NaN for none.
     */
    void bind(ShaderInstance s, Effect e, float[] values, RoundRect shape, float scale, float time, float mouseX, float mouseY) {
        List<Effect.Param> params = e.params();
        int at = 0;
        for (int i = 0; i < params.size(); i++) {
            Effect.Param p = params.get(i);
            if (p.type() == ParamType.COLOR) {
                s.safeGetUniform(p.uniform()).set(values[at], values[at + 1], values[at + 2], values[at + 3]);
            } else {
                s.safeGetUniform(p.uniform()).set(p.type() == ParamType.LENGTH ? values[at] * scale : values[at]);
            }
            at += p.type().components();
        }
        RoundRect.Radii r = shape.radii();
        s.safeGetUniform("IxSize").set(shape.width() * scale, shape.height() * scale);
        s.safeGetUniform("IxRadius").set(Math.min(r.topLeftX(), r.topLeftY()) * scale, Math.min(r.topRightX(), r.topRightY()) * scale,
                Math.min(r.bottomRightX(), r.bottomRightY()) * scale, Math.min(r.bottomLeftX(), r.bottomLeftY()) * scale);
        s.safeGetUniform("ix_scale").set(scale);
        s.safeGetUniform("ix_time").set(time);
        boolean none = Float.isNaN(mouseX);
        s.safeGetUniform("ix_mouse").set(none ? AWAY : mouseX * scale, none ? AWAY : mouseY * scale);
        boolean inside = !none && mouseX >= 0 && mouseY >= 0 && mouseX <= shape.width() && mouseY <= shape.height();
        s.safeGetUniform("ix_mouse_inside").set(inside ? 1f : 0f);
    }

    /** Draws a box effect, {@link #bind bound}, over {@code shape} through {@code pose} at depth {@code z}. */
    void drawBox(ShaderInstance s, RoundRect shape, Matrix4f pose, float z) {
        FilterShaders.apply(s, 0);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        float x0 = shape.x(), y0 = shape.y(), x1 = x0 + shape.width(), y1 = y0 + shape.height();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x0, y0, z).uv(0, 0).endVertex();
        buffer.vertex(pose, x0, y1, z).uv(0, shape.height()).endVertex();
        buffer.vertex(pose, x1, y1, z).uv(shape.width(), shape.height()).endVertex();
        buffer.vertex(pose, x1, y0, z).uv(shape.width(), 0).endVertex();
        FilterShaders.draw(s, buffer.end());
        RenderSystem.defaultBlendFunc();
    }

    /** Sets a layer effect's map from the pass's fragment coordinates to element-local physical pixels. */
    void local(ShaderInstance s, Affine m) {
        matrix.set(m.a(), m.b(), 0, 0, m.c(), m.d(), 0, 0, 0, 0, 1, 0, m.e(), m.f(), 0, 1);
        s.safeGetUniform("IxLocal").set(matrix);
    }

    private Program build(Effect e) {
        String name = prefix + "fx" + built++;
        boolean layer = e.kind() == Effect.Kind.LAYER;
        byte[] json = json(e, name, layer).getBytes(StandardCharsets.UTF_8);
        String fragment = fragment(e, layer);
        byte[] fsh = fragment.getBytes(StandardCharsets.UTF_8);
        ResourceProvider provider = id -> {
            String path = id.getPath();
            String file = path.substring(path.lastIndexOf('/') + 1);
            if (file.equals(name + ".json")) {
                return Optional.of(FilterShaders.resource(json));
            }
            if (file.equals(name + ".fsh")) {
                return Optional.of(FilterShaders.resource(fsh));
            }
            return FilterShaders.open(path, prefix);
        };
        try {
            return new Program(e.source(), new ShaderInstance(provider, name, DefaultVertexFormat.POSITION_TEX), null, null);
        } catch (IOException ex) {
            String log = String.valueOf(ex.getMessage());
            Matcher m = ERROR_LINE.matcher(log);
            if (!m.find()) {
                return new Program(e.source(), null, new SourcePos(e.id(), 1, 1), "the effect did not compile: " + log);
            }
            // From the line reference on: vanilla prefixes the driver's line with the json and program names.
            int lineEnd = log.indexOf('\n', m.end());
            String message = log.substring(m.start(), lineEnd < 0 ? log.length() : lineEnd).trim();
            return new Program(e.source(), null, new SourcePos(e.id(), Integer.parseInt(m.group(1)), 1), message);
        }
    }

    private String json(Effect e, String name, boolean layer) {
        StringBuilder sb = new StringBuilder("{\"vertex\":\"").append(prefix).append(layer ? "pass" : "gui")
                .append("\",\"fragment\":\"").append(name).append("\",")
                .append(layer ? "\"samplers\":[{\"name\":\"Sampler0\"}]," : "")
                .append("\"attributes\":[\"Position\",\"UV0\"],\"uniforms\":[");
        if (layer) {
            uniform(sb, "IxLocal", 16).append(',');
            uniform(sb, "Limit", 4).append(',');
        } else {
            uniform(sb, "ModelViewMat", 16).append(',');
            uniform(sb, "ProjMat", 16).append(',');
        }
        uniform(sb, "ix_time", 1).append(',');
        uniform(sb, "ix_mouse", 2).append(',');
        uniform(sb, "ix_mouse_inside", 1).append(',');
        uniform(sb, "ix_scale", 1).append(',');
        uniform(sb, "IxSize", 2).append(',');
        uniform(sb, "IxRadius", 4);
        for (Effect.Param p : e.params()) {
            uniform(sb.append(','), p.uniform(), p.type().components());
        }
        return sb.append("]}").toString();
    }

    /** A {@code float} uniform of {@code count} components, or a {@code mat4} (identity) for 16. */
    private static StringBuilder uniform(StringBuilder sb, String name, int count) {
        sb.append("{\"name\":\"").append(name).append("\",\"type\":\"").append(count == 16 ? "matrix4x4" : "float")
                .append("\",\"count\":").append(count).append(",\"values\":[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ",").append(count == 16 && i % 5 == 0 ? "1.0" : "0.0");
        }
        return sb.append("]}");
    }

    private static String fragment(Effect e, boolean layer) {
        StringBuilder sb = new StringBuilder("#version 150\n").append(LIBRARY);
        if (layer) {
            sb.append("""
                    uniform sampler2D Sampler0;
                    uniform vec4 Limit;
                    uniform mat4 IxLocal;
                    // The layer's own color at uv, premultiplied.
                    vec4 ix_content(vec2 uv) {
                        return texture(Sampler0, clamp(uv, Limit.xy, Limit.zw));
                    }
                    """);
        }
        for (Effect.Param p : e.params()) {
            sb.append("uniform ").append(p.type().glslType()).append(' ').append(p.uniform()).append(";\n");
        }
        // GLSL 1.50: the line after "#line n" is line n + 1.
        sb.append("#line ").append(e.bodyLine() - 1).append('\n').append(e.body()).append('\n');
        if (layer) {
            sb.append("""
                    void main() {
                        fragColor = effect(IxLayer((IxLocal * vec4(gl_FragCoord.xy, 0.0, 1.0)).xy, IxSize, IxRadius, texCoord));
                    }
                    """);
        } else {
            sb.append("""
                    void main() {
                        IxBox b = IxBox(texCoord * ix_scale, IxSize, IxRadius);
                        float d = ix_sd_round_rect(b.pos, b.size, b.radius);
                        fragColor = effect(b) * clamp(0.5 - d, 0.0, 1.0);
                    }
                    """);
        }
        return sb.toString();
    }

    private static String library() {
        try (InputStream in = EffectPrograms.class.getResourceAsStream("shaders/effect.glsl")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
