package moe.rukamori.archivetune.ui.component

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.theme.extractSeedColor
import moe.rukamori.archivetune.ui.theme.generateDarkColorSchemeFromSeed
import moe.rukamori.archivetune.ui.theme.generateLightColorSchemeFromSeed
import moe.rukamori.archivetune.utils.reportException

val LocalArtworkSurfaceColor = compositionLocalOf<State<Color>?> { null }

@Immutable
data class ArtworkBackgroundState(
    val targetSurface: Color,
    val animatedColor: State<Color>,
    val isActive: Boolean = true,
) {
    val backgroundColor: Color get() = animatedColor.value
    val surfaceColor: Color get() = targetSurface
}

private val artworkSurfaceCache = LruCache<String, Color>(64)

@Composable
fun rememberArtworkBackground(
    artworkModel: Any?,
    enabled: Boolean = true,
): ArtworkBackgroundState {
    val defaultSurface = MaterialTheme.colorScheme.surface
    val isDarkTheme = defaultSurface.luminance() < 0.5f
    val animationsDisabled = LocalAnimationsDisabled.current
    val context = LocalContext.current

    val cacheKey = remember(artworkModel, isDarkTheme) {
        if (artworkModel != null) {
            "surface_v2_${if (isDarkTheme) "d" else "l"}:$artworkModel"
        } else {
            null
        }
    }

    val initialColor = remember(cacheKey, enabled, defaultSurface) {
        if (enabled && cacheKey != null) {
            artworkSurfaceCache.get(cacheKey) ?: defaultSurface
        } else {
            defaultSurface
        }
    }

    var targetSurface by remember(artworkModel, enabled, isDarkTheme, defaultSurface) {
        mutableStateOf(initialColor)
    }

    LaunchedEffect(artworkModel, enabled, isDarkTheme, defaultSurface, context) {
        if (!enabled || artworkModel == null) {
            return@LaunchedEffect
        }
        val cached = cacheKey?.let(artworkSurfaceCache::get)
        if (cached != null) {
            targetSurface = cached
            return@LaunchedEffect
        }
        try {
            val result = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(artworkModel)
                    .size(128, 128)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request)
            }
            when (result) {
                is SuccessResult -> {
                    val seedColor = withContext(Dispatchers.Default) {
                        extractSeedColor(result.image.toBitmap())
                    }
                    val scheme = withContext(Dispatchers.Default) {
                        if (isDarkTheme) {
                            generateDarkColorSchemeFromSeed(seedColor)
                        } else {
                            generateLightColorSchemeFromSeed(seedColor)
                        }
                    }
                    val surface = scheme.surface
                    if (cacheKey != null) {
                        artworkSurfaceCache.put(cacheKey, surface)
                    }
                    targetSurface = surface
                }
                is ErrorResult -> {
                    reportException(result.throwable)
                    targetSurface = defaultSurface
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            targetSurface = defaultSurface
        }
    }

    val isFirstExtraction = initialColor == defaultSurface && targetSurface != defaultSurface

    val animatedColor = animateColorAsState(
        targetValue = if (enabled) targetSurface else Color.Transparent,
        animationSpec = if (animationsDisabled || isFirstExtraction) {
            snap()
        } else {
            tween(durationMillis = 400, easing = FastOutSlowInEasing)
        },
        label = "artworkBackground",
    )

    return remember(targetSurface, animatedColor, enabled) {
        ArtworkBackgroundState(
            targetSurface = targetSurface,
            animatedColor = animatedColor,
            isActive = enabled,
        )
    }
}

fun Modifier.artworkBackground(artworkBackground: ArtworkBackgroundState): Modifier =
    if (artworkBackground.isActive) {
        this.drawBehind {
            drawRect(artworkBackground.animatedColor.value)
        }
    } else {
        this
    }

@Composable
fun ProvideArtworkSurface(
    artworkBackground: ArtworkBackgroundState,
    content: @Composable () -> Unit,
) {
    if (artworkBackground.isActive && artworkBackground.targetSurface != Color.Unspecified) {
        CompositionLocalProvider(
            LocalArtworkSurfaceColor provides artworkBackground.animatedColor,
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surface = artworkBackground.targetSurface),
                content = content,
            )
        }
    } else {
        content()
    }
}