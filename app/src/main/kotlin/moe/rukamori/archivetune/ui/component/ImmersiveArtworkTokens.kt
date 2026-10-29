/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object ImmersiveArtworkTokens {
    val BackdropHeight: Dp = 520.dp

    const val ScrollCoverFollow = 0.20f
    const val ScrollCoverZoom = 0.22f
    const val ScrollCoverSoftBy = 0.55f
    const val ScrollCoverFadeFrom = 0.35f
    const val ScrollCoverFadeTo = 0.90f
    const val ScrollCoverDim = 0.22f
    const val ScrollCoverSoftRadius = 30f
    const val TransformOriginX = 0.5f
    const val TransformOriginY = 0.35f

    val ArtOverlayScrim = Color(0xFF000000)
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun Modifier.heroScrollCoverMotion(
    scrollProgress: () -> Float,
    animationsDisabled: Boolean,
    edgeBlur: Boolean,
): Modifier = this
    .graphicsLayer {
        val rawProgress = scrollProgress()
        val p = if (animationsDisabled) 0f else rawProgress
        if (p > 0f) {
            translationY = p * size.height * ImmersiveArtworkTokens.ScrollCoverFollow
            val zoom = 1f + p * ImmersiveArtworkTokens.ScrollCoverZoom
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(
                ImmersiveArtworkTokens.TransformOriginX,
                ImmersiveArtworkTokens.TransformOriginY,
            )
            alpha = (1f - smoothstep(
                ImmersiveArtworkTokens.ScrollCoverFadeFrom,
                ImmersiveArtworkTokens.ScrollCoverFadeTo,
                p,
            )).coerceIn(0f, 1f)

            if (edgeBlur) {
                val soft = smoothstep(0f, ImmersiveArtworkTokens.ScrollCoverSoftBy, p)
                val r = soft * ImmersiveArtworkTokens.ScrollCoverSoftRadius
                renderEffect = if (r > 0.5f) {
                    BlurEffectCache.get(r, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            } else {
                renderEffect = null
            }
        } else {
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            renderEffect = null
        }
    }
    .drawWithContent {
        drawContent()
        val rawProgress = scrollProgress()
        val p = if (animationsDisabled) 0f else rawProgress
        if (p > 0f) {
            drawRect(
                ImmersiveArtworkTokens.ArtOverlayScrim.copy(
                    alpha = (ImmersiveArtworkTokens.ScrollCoverDim * p).coerceIn(0f, 1f),
                ),
            )
        }
    }
