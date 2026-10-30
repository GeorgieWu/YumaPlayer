package moe.rukamori.archivetune.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import moe.rukamori.archivetune.utils.YouTubeLoginDiagnostics
import java.net.URISyntaxException

internal enum class LoginNavigationAction { WebView, Block, ExternalApp }

internal fun isYouTubeLoginHost(host: String?): Boolean =
    host.equals("youtube.com", ignoreCase = true) || host?.endsWith(".youtube.com", ignoreCase = true) == true

internal fun isYouTubeMusicStorePage(host: String?, path: String?, packageName: String?): Boolean =
    host.equals("play.google.com", ignoreCase = true) &&
        path?.trimEnd('/') == "/store/apps/details" &&
        packageName == "com.google.android.apps.youtube.music"

private fun Uri.targetsYouTubeMusicStore(): Boolean =
    (scheme.equals("https", true) || scheme.equals("http", true)) &&
        isYouTubeMusicStorePage(host, path, getQueryParameter("id"))

internal fun loginNavigationAction(
    scheme: String?,
    mainFrame: Boolean,
    gesture: Boolean,
    redirect: Boolean,
    musicStorePage: Boolean = false,
): LoginNavigationAction =
    when {
        musicStorePage -> LoginNavigationAction.Block
        scheme.equals("https", true) || scheme.equals("http", true) -> LoginNavigationAction.WebView
        !mainFrame || !gesture || redirect -> LoginNavigationAction.Block
        else -> LoginNavigationAction.ExternalApp
    }

/** Keep automatic app/store promotions from replacing the page supplying login context. */
internal fun handleYouTubeLoginNavigation(
    context: Context,
    request: WebResourceRequest,
    onUnavailable: () -> Unit,
): Boolean {
    val uri = request.url
    val musicStorePage = uri.targetsYouTubeMusicStore()
    when (loginNavigationAction(uri.scheme, request.isForMainFrame, request.hasGesture(), request.isRedirect, musicStorePage)) {
        LoginNavigationAction.WebView -> return false
        LoginNavigationAction.Block -> {
            YouTubeLoginDiagnostics.event(
                if (musicStorePage) "store_navigation_blocked target=youtube-music"
                else "external_navigation_blocked automatic=true",
            )
            return true
        }
        LoginNavigationAction.ExternalApp -> Unit
    }

    try {
        val target =
            if (uri.scheme.equals("intent", true)) {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        if (target.data?.targetsYouTubeMusicStore() == true) {
            YouTubeLoginDiagnostics.event("store_navigation_blocked target=youtube-music")
            return true
        }
        // Rebuild rather than trusting actions, components, selectors, flags or extras from the page.
        val intent = Intent(Intent.ACTION_VIEW, target.data).apply {
            setPackage(target.`package`)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        YouTubeLoginDiagnostics.event("external_navigation_opened user_gesture=true")
    } catch (_: ActivityNotFoundException) {
        YouTubeLoginDiagnostics.event("external_navigation_unavailable reason=no_handler")
        onUnavailable()
    } catch (_: URISyntaxException) {
        YouTubeLoginDiagnostics.event("external_navigation_unavailable reason=invalid_intent")
        onUnavailable()
    } catch (_: SecurityException) {
        YouTubeLoginDiagnostics.event("external_navigation_unavailable reason=permission")
        onUnavailable()
    }
    return true
}
