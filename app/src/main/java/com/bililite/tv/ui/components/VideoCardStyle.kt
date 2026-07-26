package com.bililite.tv.ui.components

/**
 * Visual style variant for [VideoCard].
 *
 * - [Standard] — current production appearance (warm gold focus, gray body).
 *   This is the default and is pixel-identical to the pre-Cinematic version.
 * - [Cinematic] — cold blue filmic appearance using the glass system tokens
 *   (translucent body, FocusRing cold-cyan edge). No scale animation, no
 *   extra glow shadow, no raw hex.
 *
 * Callers that do not pass a `style` parameter get [Standard] with zero
 * visual / layout / focus change.
 */
enum class VideoCardStyle {
    Standard,
    Cinematic
}
