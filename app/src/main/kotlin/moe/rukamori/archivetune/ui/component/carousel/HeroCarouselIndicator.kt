/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun HeroCarouselIndicator(
    pageCount: Int,
    currentPage: Int,
    animationsDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    val normalizedCurrent = currentPage.mod(pageCount)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = HeroCarouselTokens.ArtOverlayTextTertiary

    val targetProgress = normalizedCurrent.toFloat()
    val animatedProgress = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (animationsDisabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroIndicatorProgress",
    )

    Box(
        modifier = modifier
            .heightIn(min = HeroCarouselTokens.MinTouchTarget)
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = "${normalizedCurrent + 1} / $pageCount"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(HeroCarouselTokens.ArtOverlayButtonSecondarySurface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val density = LocalDensity.current
            val dotH = with(density) { 6.dp.toPx() }
            val inactiveW = with(density) { 6.dp.toPx() }
            val activeW = with(density) { 18.dp.toPx() }
            val space = with(density) { 6.dp.toPx() }
            val totalW = activeW + (inactiveW + space) * (pageCount - 1)

            Canvas(
                modifier = Modifier.size(
                    width = with(density) { totalW.toDp() },
                    height = 6.dp,
                )
            ) {
                val progress = animatedProgress.value
                var currentX = 0f
                for (i in 0 until pageCount) {
                    val distance = abs(progress - i)
                    val expansion = (1f - distance.coerceIn(0f, 1f))
                    val w = inactiveW + (activeW - inactiveW) * expansion
                    val color = lerp(inactiveColor, activeColor, expansion)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(currentX, 0f),
                        size = Size(w, dotH),
                        cornerRadius = CornerRadius(dotH / 2f, dotH / 2f),
                    )
                    currentX += w + space
                }
            }
        }
    }
}
