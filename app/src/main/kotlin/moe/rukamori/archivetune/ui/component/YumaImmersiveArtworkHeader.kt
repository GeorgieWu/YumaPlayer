/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalAnimationsDisabled

@Composable
fun YumaImmersiveArtworkHeader(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    height: Dp = ImmersiveArtworkTokens.BackdropHeight,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    disableBlur: Boolean = false,
    artwork: (@Composable () -> Unit)? = null,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val isBlurActive = !disableBlur && !animationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val rawProgress = progress()
                    val p = if (animationsDisabled) 0f else rawProgress
                    if (p > 0f) {
                        translationY = ImmersiveArtworkTokens.KinematicsParallaxFactor * p * size.height
                        val zoom = 1f + ImmersiveArtworkTokens.ScrollCoverZoom * p
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

                        if (isBlurActive) {
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
                    val rawProgress = progress()
                    val p = if (animationsDisabled) 0f else rawProgress
                    if (p > 0f) {
                        drawRect(
                            ImmersiveArtworkTokens.ArtOverlayScrim.copy(
                                alpha = (ImmersiveArtworkTokens.ScrollCoverDim * p).coerceIn(0f, 1f),
                            ),
                        )
                    }
                },
        ) {
            if (artwork != null) {
                artwork()
            } else if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bottomBlend = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.65f to surfaceColor.copy(alpha = 0.60f),
                        0.85f to surfaceColor.copy(alpha = 0.90f),
                        1.0f to surfaceColor,
                        startY = 0f,
                        endY = size.height,
                    )
                    onDrawBehind {
                        drawRect(bottomBlend)
                    }
                },
        )
    }
}
