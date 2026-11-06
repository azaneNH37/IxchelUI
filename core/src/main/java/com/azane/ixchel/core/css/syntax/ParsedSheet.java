package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/**
 * A parsed stylesheet: the kind it declares, {@code null} when it declares none, its leading
 * {@code @link}s, its style rules in source order, nested ones expanded, its {@code @keyframes} and
 * its {@code @font-face}s.
 */
public record ParsedSheet(Kind kind, List<Link> links, List<Rule> rules, List<Keyframes> keyframes,
                          List<FontFace> fontFaces) {
    /** What a css file declares itself to be with its first statement. */
    public enum Kind {
        /** {@code @stylesheet;}: the styles of one page or component, linked from its view. */
        STYLESHEET,
        /** {@code @partial;}: a reusable piece, linked from stylesheets and partials. */
        PARTIAL
    }

    /** A leading {@code @link "href" [as alias];}; {@code alias} is {@code null} when not written. */
    public record Link(String href, String alias, SourcePos pos) {
    }

    /** {@code @keyframes name { … }}: the keyframe blocks in source order. */
    public record Keyframes(String name, List<Keyframe> frames, SourcePos pos) {
    }

    /** One keyframe block: its offsets in {@code [0, 1]} ({@code from} is 0, {@code to} 1) and declarations. */
    public record Keyframe(List<Float> offsets, List<Rule.Declaration> declarations, SourcePos pos) {
    }

    /**
     * {@code @font-face { font-family: <family>; src: url(<src>); }}: the font file {@code src} is
     * what {@code font-family} lists as {@code family}. {@code src} is the href as written until the
     * linker resolves it to a file id.
     */
    public record FontFace(String family, String src, SourcePos pos) {
    }
}
