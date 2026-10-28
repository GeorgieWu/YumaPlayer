/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */


package moe.rukamori.archivetune.ui.component

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private const val ContainerOpenDamping = 0.88f
private const val ContainerOpenStiffness = 150f
private const val ContainerCloseMillis = 560
private val ContainerCloseEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

@OptIn(ExperimentalSharedTransitionApi::class)
private val ContainerBoundsTransform = BoundsTransform { initial, target ->
    if (target.width < initial.width) {
        tween(ContainerCloseMillis, easing = ContainerCloseEasing)
    } else {
        spring(
            dampingRatio = ContainerOpenDamping,
            stiffness = ContainerOpenStiffness,
            visibilityThreshold = Rect.VisibilityThreshold,
        )
    }
}

private object ContainerTransformRegistry {
    val sourceWidths = HashMap<String, Float>()
    val sourceRadii = HashMap<String, Dp>()
    var targetWidth = 0f
}

@OptIn(ExperimentalSharedTransitionApi::class)
private class MorphingCornerClip(
    private val key: String,
    private val startRadius: Dp,
    private val endRadiusPx: () -> Float,
) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        state: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val from = ContainerTransformRegistry.sourceWidths[key]
        val to = ContainerTransformRegistry.targetWidth
        val t = if (from != null && to > from + 1f) {
            ((bounds.width - from) / (to - from)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val start = ContainerTransformRegistry.sourceRadii[key] ?: startRadius
        val radius = lerp(with(density) { start.toPx() }, endRadiusPx(), t)
        path.rewind()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }
}

@Composable
private fun rememberMorphingCornerClip(key: String, startRadius: Dp): MorphingCornerClip {
    val view = LocalView.current
    return remember(key, startRadius, view) {
        MorphingCornerClip(key, startRadius) { screenCornerRadiusPx(view) }
    }
}

private fun screenCornerRadiusPx(view: View): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) roundedCornerRadiusPx(view) else 0f

@RequiresApi(Build.VERSION_CODES.S)
private fun roundedCornerRadiusPx(view: View): Float =
    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

/** Навешивается на карточку в списке/сетке (Source) */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformSource(
    key: String?,
    cornerRadius: Dp = 16.dp,
): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, cornerRadius)

    return with(sharedScope) {
        this@containerTransformSource
            .onPlaced {
                ContainerTransformRegistry.sourceWidths[key] = it.size.width.toFloat()
                ContainerTransformRegistry.sourceRadii[key] = cornerRadius
            }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                enter = fadeIn(tween(220, delayMillis = 100)),
                exit = fadeOut(tween(220, delayMillis = 40)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                clipInOverlayDuringTransition = clip,
            )
    }
}

/** Навешивается на корневой контейнер экрана альбома/плейлиста (Target) */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformTarget(
    key: String?,
    sourceCornerRadius: Dp = 16.dp,
): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, sourceCornerRadius)

    return with(sharedScope) {
        this@containerTransformTarget
            .onSizeChanged { ContainerTransformRegistry.targetWidth = it.width.toFloat() }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                enter = fadeIn(tween(280)),
                exit = fadeOut(tween(200, delayMillis = 120)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                zIndexInOverlay = 1f,
                clipInOverlayDuringTransition = clip,
            )
    }
}