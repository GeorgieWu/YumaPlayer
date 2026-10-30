package moe.rukamori.archivetune.ui.screens.login

import android.net.Uri
import moe.rukamori.archivetune.utils.YouTubeLoginDiagnostics

internal fun isYouTubeLoginHost(host: String?): Boolean =
    host.equals("youtube.com", ignoreCase = true) || host?.endsWith(".youtube.com", ignoreCase = true) == true

internal fun isYouTubeMusicStorePage(host: String?, path: String?, packageName: String?): Boolean =
    host.equals("play.google.com", ignoreCase = true) &&
        path?.trimEnd('/') == "/store/apps/details" &&
        packageName == "com.google.android.apps.youtube.music"

/** Keep login in the WebView without opening apps or the Music install page. */
internal fun shouldBlockYouTubeLoginNavigation(uri: Uri): Boolean {
    if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) {
        YouTubeLoginDiagnostics.event("external_navigation_blocked")
        return true
    }

    val musicStorePage = isYouTubeMusicStorePage(uri.host, uri.path, uri.getQueryParameter("id"))
    if (musicStorePage) {
        YouTubeLoginDiagnostics.event("store_navigation_blocked target=youtube-music")
    }
    return musicStorePage
}
