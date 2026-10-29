package com.azane.ixchel.core.binding;

/**
 * The event an {@code on:} handler runs for, seen by its statements as {@code event}: the pointer
 * {@code x}/{@code y} in document GUI pixels (NaN for key and form events), how far a
 * {@code mousemove} moved ({@code movementX}/{@code movementY}, 0 otherwise), and the wheel
 * {@code delta} of a {@code wheel} event, positive away from the user as GLFW reports it.
 */
public record EventInfo(float x, float y, float movementX, float movementY, double delta) {
}
