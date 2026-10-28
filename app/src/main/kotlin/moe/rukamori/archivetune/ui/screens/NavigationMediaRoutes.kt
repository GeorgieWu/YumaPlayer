/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.ui.screens.album.SpotifyAlbumScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistAlbumsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistItemsScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistScreen
import moe.rukamori.archivetune.ui.screens.artist.ArtistSongsScreen
import moe.rukamori.archivetune.ui.screens.library.SpotifyLikedSongsScreen
import moe.rukamori.archivetune.ui.screens.playlist.AutoPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.CachePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.LocalPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.OnlinePlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.SpotifyPlaylistScreen
import moe.rukamori.archivetune.ui.screens.playlist.TopPlaylistScreen
import androidx.compose.runtime.CompositionLocalProvider
import moe.rukamori.archivetune.ui.component.LocalNavAnimatedVisibilityScope

@OptIn(ExperimentalMaterial3Api::class)
internal fun NavGraphBuilder.mediaNavigationRoutes(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            AlbumScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            OnlinePlaylistScreen(navController)
        }
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "spotify_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            SpotifyPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "spotify_album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            SpotifyAlbumScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            CachePlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            TopPlaylistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }
}

internal fun encodeNavigationParam(value: String): String =
    java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name())
        .replace("+", "%20")

internal fun buildYouTubeBrowseRoute(
    browseId: String,
    params: String? = null,
): String {
    val encodedBrowseId = encodeNavigationParam(browseId)
    val trimmedParams = params?.takeIf { it.isNotBlank() }
    return if (trimmedParams != null) {
        "youtube_browse/$encodedBrowseId?params=${encodeNavigationParam(trimmedParams)}"
    } else {
        "youtube_browse/$encodedBrowseId"
    }
}

internal fun buildBrowseRoute(
    browseId: String,
    params: String? = null,
): String {
    val encodedBrowseId = encodeNavigationParam(browseId)
    val validParams = params?.takeIf { it.isNotBlank() }
    return if (validParams != null) {
        "browse/$encodedBrowseId?params=${encodeNavigationParam(validParams)}"
    } else {
        "browse/$encodedBrowseId"
    }
}
