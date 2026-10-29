/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object ImmersiveArtworkTokens {
    val BackdropHeight: Dp = 380.dp

    const val ScrollCoverZoom = 0.22f
    const val ScrollCoverSoftBy = 0.55f
    const val ScrollCoverFadeFrom = 0.35f
    const val ScrollCoverFadeTo = 0.90f
    const val ScrollCoverDim = 0.22f
    const val ScrollCoverSoftRadius = 30f
    const val KinematicsParallaxFactor = -0.8f
    const val TransformOriginX = 0.5f
    const val TransformOriginY = 0.35f

    val ArtOverlayScrim = Color(0xFF000000)
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
