package com.bililite.tv.ui.search

import androidx.compose.ui.unit.dp
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.GlassVisualOverrides

/**
 * UI-RG-06C — shared Control-glass overrides for the search page.
 *
 * Replaces the default GlassVariant.Control appearance (which reads as a
 * metallic button on TV) with a low-contrast, cold-blue-black translucent
 * look whose surface variation is barely perceptible in normal state.
 *
 * All values reference existing TvColors tokens only — no raw hex or new
 * tokens are introduced.
 */
internal val SearchControlGlassOverrides = GlassVisualOverrides(
    // Body: from default ~0.60 down to 0.34 — no longer reads as solid metal.
    bodyColor = TvColors.Glass.BodyControl.copy(alpha = 0.34f),
    // Gradient: from default ~0.20/0.25 down to ~0.025/0.045 — nearly invisible.
    gradientTopColor = TvColors.Glass.GradTop.copy(alpha = 0.025f),
    gradientBottomColor = TvColors.Glass.GradBottom.copy(alpha = 0.045f),
    // Inner highlight: from default ~0.14 down to ~0.025 — barely visible.
    innerHighlightColor = TvColors.Glass.InnerTop.copy(alpha = 0.025f),
    // Normal outer edge: from full-opacity CardBorder down to 0.34 — dark, not gray-white.
    outerEdgeColor = TvColors.CardBorder.copy(alpha = 0.34f),
    // Normal inner edge: from default ~0.30 down to ~0.045 — nearly invisible.
    innerEdgeColor = TvColors.Glass.EdgeBright.copy(alpha = 0.045f),
    normalEdgeWidth = 1.dp,
    // Focused edge: from default 3.dp down to 2.5.dp — crisp but not chunky.
    focusedEdgeWidth = 2.5.dp,
    focusedEdgeColor = TvColors.FocusRing,
    // Focused glow: from default 6.dp down to 4.dp — tight and controlled.
    focusedGlowElevation = 4.dp,
    focusedGlowColor = TvColors.Glass.GlowFocus
)
