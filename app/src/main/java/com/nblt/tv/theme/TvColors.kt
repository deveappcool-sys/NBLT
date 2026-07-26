package com.nblt.tv.theme

import androidx.compose.ui.graphics.Color

object TvColors {
    val BackgroundTop = Color(0xFF121414)
    val BackgroundBottom = Color(0xFF080909)
    val Surface = Color(0xFF1A1D1C)
    val SurfaceElevated = Color(0xFF242826)
    val SurfaceSoft = Color(0xCC202321)
    val Accent = Color(0xFFE84A3C)
    val AccentSoft = Color(0x33E84A3C)
    val FocusAccent = Color(0xFFFFC857)
    val FocusAccentSoft = Color(0x33FFC857)
    val NavBarTrack = Color(0xB3141716)
    val NavPillSelected = AccentSoft
    val NavPillSelectedText = Accent
    val NavPillNormal = Color.Transparent
    val TextPrimary = Color(0xFFF5F4F1)
    val TextSecondary = Color(0xFFC2C2BC)
    val TextMuted = Color(0xFF858985)
    val FocusBorder = FocusAccent
    val CardBorder = Color(0xFF343836)
    val OverlayDark = Color(0xCC000000)
    val OverlayGradientStart = Color(0x00000000)
    val OverlayGradientEnd = Color(0xCC000000)

    // === UI-R1 new tokens for CinematicSideRail and future migration ===

    // Brand & action
    val Primary = Color(0xFFD9A55F)
    val PrimaryStrong = Color(0xFFE5B872)
    val PrimarySoft = Color(0x33D9A55F)

    // Focus & glow
    val FocusRing = Color(0xFF4EC0E4)
    val FocusRingSoft = Color(0x664EC0E4)

    // Progress
    val ProgressTrack = Color(0xFF1F2A33)
    val ProgressFill = Color(0xFF4EC0E4)

    // Card glow
    val CardFocusGlow = Color(0x1A4EC0E4)

    // Background (target values for UI-R2 migration)
    val BackgroundDark = Color(0xFF06090D)
    val BackgroundDarkTop = Color(0xFF0B0F14)

    // Surface variants
    val SurfaceDark = Color(0xFF10151C)
    val SurfaceDarkElevated = Color(0xFF172029)
    val SurfaceGlass = Color(0xB3121721)

    // Divider
    val DividerLine = Color(0xFF1B232C)

    // Text additions
    val TextOnPrimary = Color(0xFF0B0F14)

    // Status
    val Danger = Color(0xFFE25555)
    val DangerSoft = Color(0x33E25555)
    val Success = Color(0xFF7BC383)

    // Side rail specific
    val SideRailBackground = Color(0xCC0B0F14)
    val SideRailBorder = Color(0xFF222B36)

    // === UI-RG-01 glass system tokens (additive only, existing tokens unchanged) ===
    object Glass {
        // Translucent body fills — alpha varies per variant so depth differs.
        // Background must faintly show through; Panel deeper than Control; Rail most transparent.
        val BodyControl = Color(0x99141A21)   // small controls,  alpha ~0.60
        val BodyCard    = Color(0x8C182028)   // info cards,     alpha ~0.55
        val BodyPanel   = Color(0xB010161D)   // big panels,     alpha ~0.69 (deeper)
        val BodyRail    = Color(0x590E141A)   // side rail,      alpha ~0.35 (most transparent)

        // Diagonal gradient: top-left slightly bright -> bottom-right slightly dark (restrained).
        val GradTop    = Color(0x33FFFFFF)
        val GradBottom = Color(0x40000000)
        // Top inner highlight (very weak).
        val InnerTop   = Color(0x24FFFFFF)

        // Normal outer edge: a low-alpha bright line paired with the dark CardBorder part.
        val EdgeBright = Color(0x4DFFFFFF)

        // Focused outer glow — colored, never a black shadow block.
        val GlowFocus     = Color(0x334EC0E4)
        val GlowFocusSoft = Color(0x1A4EC0E4)
    }
}
