package com.nblt.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * UI-RG-03C — the single unified cinematic navigation icon set, retuned for
 * the cold-blue glass side rail so all nine icons read as one family.
 *
 * Shared technical contract:
 *  - viewport 32×32
 *  - stroke width 2.0f
 *  - StrokeCap.Round, StrokeJoin.Round
 *  - outline-only (stroke); no fills, no shadow, no glow inside the vector
 *  - optical center near (16, 16); main paths kept within ~6–26 safe range
 *  - displayed at 28.dp and tinted at the call site by the side rail
 *    (normal / selected / focused / disabled are color-only and live in the rail)
 *
 * Each icon is intentionally drawn as a tight silhouette so 28.dp output
 * remains crisp on Android TV at sofa distance.
 *
 *  - HOME      rounded house outline: roof, body, arched door
 *  - RECOMMEND 2×2 grid of equal rounded squares (category / app-drawer language)
 *  - POPULAR   rising trend polyline (5 waypoints, clear upward direction)
 *  - LIVE      TV screen outline with centered play triangle (preserved)
 *  - SEARCH    enlarged magnifier ring + handle, balanced visual mass
 *  - DYNAMIC   balanced pulse / waveform polyline
 *  - HISTORY   clock face + hour/minute hands
 *  - MY        head circle + open shoulders curve
 *  - SETTINGS  outer ring + center hole + 6 simplified radial teeth
 */
private const val VP = 32f
private const val STROKE_W = 2.0f

private fun navIcon(path: String): ImageVector =
    ImageVector.Builder(
        name = "nav",
        defaultWidth = 32.dp,
        defaultHeight = 32.dp,
        viewportWidth = VP,
        viewportHeight = VP
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        stroke = SolidColor(Color.Black),
        fill = SolidColor(Color.Transparent),
        strokeLineWidth = STROKE_W,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()

object CinematicNavIcons {
    val HOME: ImageVector = navIcon(
        "M6.5 14.5 L16 6 L25.5 14.5 " +
        "M9.5 13.5 L9.5 25 L22.5 25 L22.5 13.5 " +
        "M14 25 L14 20.5 Q14 19 15.5 19 L16.5 19 Q18 19 18 20.5 L18 25"
    )

    val RECOMMEND: ImageVector = navIcon(
        "M9 7.5 H13 Q14.5 7.5 14.5 9 V13 Q14.5 14.5 13 14.5 H9 Q7.5 14.5 7.5 13 V9 Q7.5 7.5 9 7.5 Z " +
        "M19 7.5 H23 Q24.5 7.5 24.5 9 V13 Q24.5 14.5 23 14.5 H19 Q17.5 14.5 17.5 13 V9 Q17.5 7.5 19 7.5 Z " +
        "M9 17.5 H13 Q14.5 17.5 14.5 19 V23 Q14.5 24.5 13 24.5 H9 Q7.5 24.5 7.5 23 V19 Q7.5 17.5 9 17.5 Z " +
        "M19 17.5 H23 Q24.5 17.5 24.5 19 V23 Q24.5 24.5 23 24.5 H19 Q17.5 24.5 17.5 23 V19 Q17.5 17.5 19 17.5 Z"
    )

    val POPULAR: ImageVector = navIcon(
        "M5 24 L11 19 L17 22 L23 13 L27 8"
    )

    val LIVE: ImageVector = navIcon(
        "M8 9.5 H24 a2 2 0 0 1 2 2 V20.5 a2 2 0 0 1 -2 2 H8 a2 2 0 0 1 -2 -2 V11.5 a2 2 0 0 1 2 -2 Z " +
        "M14 13 L20 16 L14 19 Z"
    )

    val SEARCH: ImageVector = navIcon(
        "M14 14 m-7 0 a7 7 0 1 0 14 0 a7 7 0 1 0 -14 0 " +
        "M19 19 L25 25"
    )

    val DYNAMIC: ImageVector = navIcon(
        "M4 16 H8 L12 9 L16 23 L20 9 L24 16 H28"
    )

    val HISTORY: ImageVector = navIcon(
        "M16 16 m-9 0 a9 9 0 1 0 18 0 a9 9 0 1 0 -18 0 " +
        "M16 10.5 V16 L20 18"
    )

    val MY: ImageVector = navIcon(
        "M16 11 m-5 0 a5 5 0 1 0 10 0 a5 5 0 1 0 -10 0 " +
        "M7 25.5 C7 20.5 10 19 16 19 C22 19 25 20.5 25 25.5"
    )

    val SETTINGS: ImageVector = navIcon(
        "M26 16 L23.5 19 L23 23 L19 23.5 L16 26 L13 23.5 L9 23 L8.5 19 L6 16 L8.5 13 L9 9 L13 8.5 L16 6 L19 8.5 L23 9 L23.5 13 Z " +
        "M16 16 m-3.5 0 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0"
    )
}