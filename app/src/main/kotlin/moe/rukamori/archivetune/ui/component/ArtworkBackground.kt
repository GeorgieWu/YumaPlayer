package moe.rukamori.archivetune.ui.component

import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.home.effects.LocalHomeBackgroundStyle
import moe.rukamori.archivetune.ui.theme.extractSeedColor
import moe.rukamori.archivetune.ui.theme.generateDarkColorSchemeFromSeed
import moe.rukamori.archivetune.ui.theme.generateLightColorSchemeFromSeed
import moe.rukamori.archivetune.utils.reportException

@Immutable
data class ArtworkBackgroundState(
    val backgroundColor: Color,
    val surfaceColor: Color,
    val isNoneMode: Boolean,
)

private val artworkSurfaceCache = LruCache<String, Color>(64)

@Composable
fun rememberArtworkBackground(artworkModel: Any?): ArtworkBackgroundState {
    val backgroundStyle = LocalHomeBackgroundStyle.current.style
    val isNoneMode = backgroundStyle == HomeBackgroundStyle.OFF
    val defaultSurface = MaterialTheme.colorScheme.surface
    val isDarkTheme = defaultSurface.luminance() < 0.5f
    val context = LocalContext.current

    val cacheKey = remember(artworkModel, isDarkTheme) {
        if (artworkModel != null) {
            "${if (isDarkTheme) "d" else "l"}:$artworkModel"
        } else {
            null
        }
    }

    val initialColor = remember(cacheKey, isNoneMode, defaultSurface) {
        if (isNoneMode && cacheKey != null) {
            artworkSurfaceCache.get(cacheKey) ?: defaultSurface
        } else {
            defaultSurface
        }
    }

    var derivedSurface by remember(artworkModel, isNoneMode, isDarkTheme, defaultSurface) {
        mutableStateOf(initialColor)
    }

    LaunchedEffect(artworkModel, isNoneMode, isDarkTheme, defaultSurface, context) {
        if (!isNoneMode || artworkModel == null) {
            return@LaunchedEffect
        }
        val cachedSurface = cacheKey?.let(artworkSurfaceCache::get)
        if (cachedSurface != null) {
            derivedSurface = cachedSurface
            return@LaunchedEffect
        }
        try {
            val result = withContext(Dispatchers.IO) {
                val request =
                    ImageRequest.Builder(context)
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
                    derivedSurface = surface
                }
                is ErrorResult -> {
                    reportException(result.throwable)
                    derivedSurface = defaultSurface
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            derivedSurface = defaultSurface
        }
    }

    return remember(derivedSurface, isNoneMode, defaultSurface) {
        ArtworkBackgroundState(
            backgroundColor = if (isNoneMode) derivedSurface else Color.Transparent,
            surfaceColor = if (isNoneMode) derivedSurface else defaultSurface,
            isNoneMode = isNoneMode,
        )
    }
}

@Composable
fun ProvideArtworkSurface(
    artworkBackground: ArtworkBackgroundState,
    content: @Composable () -> Unit,
) {
    if (artworkBackground.isNoneMode && artworkBackground.surfaceColor != Color.Unspecified) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(surface = artworkBackground.surfaceColor),
            content = content,
        )
    } else {
        content()
    }
}
