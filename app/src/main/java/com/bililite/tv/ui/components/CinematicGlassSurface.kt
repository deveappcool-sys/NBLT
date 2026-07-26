package com.bililite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bililite.tv.theme.TvColors
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * UI-RG-01 — the single shared cinematic glass surface.
 *
 * Every glass element in the app (Rail / Panel / Card / Control) is rendered through
 * this one implementation so the glass language stays consistent. Four visual layers
 * are always present (no plain dark card is allowed):
 *
 *  1. Translucent body  — per-variant alpha, background faintly shows through.
 *  2. Diagonal gradient — top-left slightly bright, bottom-right slightly dark.
 *  3. Outer edge        — normal: dark CardBorder + low-alpha bright inner line
 *                             (never a single plain gray line);
 *                             focused: 3.dp FocusRing painted over the glass,
 *                             body stays glassy (no solid cyan/gold block).
 *  4. Inner highlight    — very weak top highlight, focus/click irrelevant.
 *  5. Outer glow        — focused only, a colored (never black) soft halo.
 *
 * The component never adds focusable / clickable / pointerInput nodes; focus wiring
 * is always supplied by the caller through [modifier]. Geometry (size, padding,
 * spacing) is never changed here. No Modifier.blur is used.
 */
enum class GlassVariant {
    Rail,
    Panel,
    Card,
    Control
}

/**
 * Non-destructive visual overrides for [CinematicGlassSurface].
 *
 * Every field defaults to `null`, which means "use the current GlassVariant default".
 * Setting a field to a non-null value overrides the corresponding visual layer
 * without changing the variant's semantic role (focus wiring, sizing, layout,
 * content wrapper all remain untouched).
 *
 * Usage example (outside this file):
 * ```
 * CinematicGlassSurface(
 *     variant = GlassVariant.Rail,
 *     visualOverrides = GlassVisualOverrides(
 *         bodyColor = Color(0x0AFFFFFF),
 *         outerEdgeColor = Color(0x1AFFFFFF),
 *         focusedGlowColor = Color(0x1AFFFFFF)
 *     )
 * )
 * ```
 */
@Immutable
data class GlassVisualOverrides(
    /** Overrides the translucent body fill. Maps to Layer 1. */
    val bodyColor: Color? = null,
    /** Overrides the diagonal gradient top-left color. Maps to Layer 2. */
    val gradientTopColor: Color? = null,
    /** Overrides the diagonal gradient bottom-right color. Maps to Layer 2. */
    val gradientBottomColor: Color? = null,
    /** Overrides the inner highlight top color. Maps to Layer 3. */
    val innerHighlightColor: Color? = null,
    /** Overrides the normal-state outer edge color. Maps to Layer 4. */
    val outerEdgeColor: Color? = null,
    /** Overrides the normal-state inner bright edge color. Maps to Layer 4. */
    val innerEdgeColor: Color? = null,
    /** Overrides the normal-state edge width. Default: 1.dp. */
    val normalEdgeWidth: Dp? = null,
    /** Overrides the focused-state edge width. Default: 3.dp. */
    val focusedEdgeWidth: Dp? = null,
    /** Overrides the focused-state edge color. Maps to Layer 4 (focused). */
    val focusedEdgeColor: Color? = null,
    /** Overrides the focused glow shadow elevation. Default: 6.dp. */
    val focusedGlowElevation: Dp? = null,
    /** Overrides the focused glow color. Maps to Layer 5. */
    val focusedGlowColor: Color? = null
)

private data class GlassStyle(
    val body: Color,
    val gradient: Brush,
    val innerHighlight: Brush,
    val edgeNormal: Color,
    val edgeFocused: Color,
    val glow: Color?
)

private fun styleFor(variant: GlassVariant): GlassStyle {
    val g = TvColors.Glass
    val gradient = Brush.linearGradient(
        0f to g.GradTop,
        0.5f to Color.Transparent,
        1f to g.GradBottom
    )
    val inner = Brush.verticalGradient(
        0f to g.InnerTop,
        0.45f to Color.Transparent
    )
    return when (variant) {
        GlassVariant.Control -> GlassStyle(
            body = g.BodyControl,
            gradient = gradient,
            innerHighlight = inner,
            edgeNormal = TvColors.CardBorder,
            edgeFocused = TvColors.FocusRing,
            glow = g.GlowFocus
        )
        GlassVariant.Card -> GlassStyle(
            body = g.BodyCard,
            gradient = gradient,
            innerHighlight = inner,
            edgeNormal = TvColors.CardBorder,
            edgeFocused = TvColors.FocusRing,
            glow = g.GlowFocus
        )
        GlassVariant.Panel -> GlassStyle(
            body = g.BodyPanel,
            gradient = gradient,
            innerHighlight = inner,
            edgeNormal = TvColors.CardBorder,
            edgeFocused = TvColors.FocusRing,
            glow = g.GlowFocusSoft
        )
        GlassVariant.Rail -> GlassStyle(
            body = g.BodyRail,
            gradient = gradient,
            innerHighlight = inner,
            edgeNormal = TvColors.CardBorder,
            edgeFocused = TvColors.FocusRing,
            glow = null
        )
    }
}

@Composable
fun CinematicGlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant,
    focused: Boolean = false,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.Center,
    // Optional weak accent tint layered on the glass body when focused
    // (e.g. a soft gold tint for the primary search action). Kept weak on purpose.
    accentTintOnFocus: Color? = null,
    /** Optional per-call-site visual overrides. `null` leaves every layer at its GlassVariant default. */
    visualOverrides: GlassVisualOverrides? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val style = styleFor(variant)

    // Resolve each visual parameter.
    // null in visualOverrides → fall back to the current GlassVariant default.
    val resolvedBody = visualOverrides?.bodyColor ?: style.body
    val resolvedGradient = Brush.linearGradient(
        0f to (visualOverrides?.gradientTopColor ?: TvColors.Glass.GradTop),
        0.5f to Color.Transparent,
        1f to (visualOverrides?.gradientBottomColor ?: TvColors.Glass.GradBottom)
    )
    val resolvedInnerHighlight = Brush.verticalGradient(
        0f to (visualOverrides?.innerHighlightColor ?: TvColors.Glass.InnerTop),
        0.45f to Color.Transparent
    )
    val resolvedOuterEdgeNormal = visualOverrides?.outerEdgeColor ?: style.edgeNormal
    val resolvedInnerEdge = visualOverrides?.innerEdgeColor ?: TvColors.Glass.EdgeBright
    val resolvedNormalEdgeWidth = visualOverrides?.normalEdgeWidth ?: 1.dp
    val resolvedFocusedEdgeWidth = visualOverrides?.focusedEdgeWidth ?: 3.dp
    val resolvedFocusedEdgeColor = visualOverrides?.focusedEdgeColor ?: style.edgeFocused
    val resolvedGlowElevation = visualOverrides?.focusedGlowElevation ?: 6.dp
    // focusedGlowColor null → inherit variant default (may be null for Rail).
    val resolvedGlow = visualOverrides?.focusedGlowColor ?: style.glow

    Box(
        modifier = modifier
            .then(
                if (focused && resolvedGlow != null) {
                    Modifier.shadow(
                        elevation = resolvedGlowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = resolvedGlow,
                        spotColor = resolvedGlow
                    )
                } else {
                    Modifier
                }
            )
            .background(resolvedBody, shape)
            .background(resolvedGradient, shape)
            .background(resolvedInnerHighlight, shape)
            .then(
                if (focused && accentTintOnFocus != null) {
                    Modifier.background(accentTintOnFocus, shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (focused) {
                    Modifier.border(resolvedFocusedEdgeWidth, resolvedFocusedEdgeColor, shape)
                } else {
                    Modifier.border(resolvedNormalEdgeWidth, resolvedOuterEdgeNormal, shape)
                }
            ),
        contentAlignment = contentAlignment
    ) {
        // Layer 4/3 normal edge: a low-alpha bright inner line paired with the
        // dark CardBorder applied on the outer Box. No new focusable node.
        if (!focused) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(resolvedNormalEdgeWidth)
                    .border(resolvedNormalEdgeWidth, resolvedInnerEdge, shape)
            )
        }
        // Content wrapper: does NOT force-fill. It sizes to its own content so the
        // root Box (which only carries the caller's modifier) never expands to the
        // full incoming max constraint when the caller omits an explicit height
        // (e.g. an input/button sized only by `weight(1f)` in a Row). Padding
        // applies to content only, never to the edge ring. Decorative layers fill
        // the root Box via `matchParentSize()` / `.background(...)` on the root.
        Box(
            Modifier
                .padding(contentPadding),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}
