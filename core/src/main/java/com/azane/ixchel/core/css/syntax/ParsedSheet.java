package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.List;

/** A parsed stylesheet: its style rules in source order, nested ones expanded, and its {@code @keyframes}. */
public record ParsedSheet(List<Rule> rules, List<Keyframes> keyframes) {

    /** {@code @keyframes name { … }}: the keyframe blocks in source order. */
    public record Keyframes(String name, List<Keyframe> frames, SourcePos pos) {
    }

    /** One keyframe block: its offsets in {@code [0, 1]} ({@code from} is 0, {@code to} 1) and declarations. */
    public record Keyframe(List<Float> offsets, List<Rule.Declaration> declarations, SourcePos pos) {
    }
}
