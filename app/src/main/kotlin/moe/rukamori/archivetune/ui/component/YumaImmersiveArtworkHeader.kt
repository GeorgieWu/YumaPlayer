/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalAnimationsDisabled

@Composable
fun YumaImmersiveArtworkHeader(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    thumbnailUrl: String? = null,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    disableBlur: Boolean = false,
    artwork: (@Composable () -> Unit)? = null,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val isBlurActive = !disableBlur && !animationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val animatedSurfaceColor = LocalArtworkSurfaceColor.current
    val currentColor = animatedSurfaceColor?.value ?: surfaceColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .heroScrollCoverMotion(
                    scrollProgress = progress,
                    animationsDisabled = animationsDisabled,
                    edgeBlur = isBlurActive,
                ),
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

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bottomScrim = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.40f to Color.Transparent,
                        0.70f to currentColor.copy(alpha = 0.55f),
                        0.88f to currentColor.copy(alpha = 0.90f),
                        1.0f to currentColor,
                        startY = 0f,
                        endY = size.height,
                    )
                    onDrawBehind {
                        drawRect(bottomScrim)
                    }
                },
        )
    }
}