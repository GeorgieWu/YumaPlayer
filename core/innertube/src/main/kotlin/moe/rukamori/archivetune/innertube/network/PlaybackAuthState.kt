/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.network

import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.innertube.utils.sha1
import java.util.Locale

data class PlaybackAuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val poTokenGvs: String? = null,
    val poTokenPlayer: String? = null,
    val webClientPoTokenEnabled: Boolean = false,
    // Generated tokens are video-bound; null keeps manually configured tokens unbound.
    val poTokenGvsVideoId: String? = null,
    val poTokenPlayerVideoId: String? = null,
) {
    val hasLoginCookie: Boolean
        get() = hasYouTubeLoginCookie(cookie)

    val hasPlaybackLoginContext: Boolean
        get() = hasLoginCookie && !dataSyncId.isNullOrBlank()

    val delegatedSessionId: String?
        get() = dataSyncId
            ?.takeIf { it.contains("||") }
            ?.substringBefore("||")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val sessionId: String?
        get() = if (hasPlaybackLoginContext) dataSyncId else visitorData

    val fingerprint: String
        get() =
            sha1(
                listOf(
                    cookie.orEmpty(),
                    visitorData.orEmpty(),
                    dataSyncId.orEmpty(),
                    poToken.orEmpty(),
                    poTokenGvs.orEmpty(),
                    poTokenPlayer.orEmpty(),
                    webClientPoTokenEnabled.toString(),
                    poTokenGvsVideoId.orEmpty(),
                    poTokenPlayerVideoId.orEmpty(),
                ).joinToString(separator = "\u0000"),
            )

    /** Session and manual configuration identity, independent of generated video tokens. */
    val streamCacheFingerprint: String
        get() =
            sha1(
                listOf(
                    cookie.orEmpty(),
                    visitorData.orEmpty(),
                    dataSyncId.orEmpty(),
                    poToken.orEmpty(),
                    poTokenGvs.takeIf { poTokenGvsVideoId == null }.orEmpty(),
                    poTokenPlayer.takeIf { poTokenPlayerVideoId == null }.orEmpty(),
                    webClientPoTokenEnabled.toString(),
                ).joinToString(separator = "\u0000"),
            )

    fun normalized(): PlaybackAuthState =
        copy(
            cookie = cookie.normalizeAuthValue(),
            visitorData = visitorData.normalizeAuthValue(),
            dataSyncId = dataSyncId.normalizeDataSyncId(),
            poToken = poToken.normalizeAuthValue(),
            poTokenGvs = poTokenGvs.normalizeAuthValue(),
            poTokenPlayer = poTokenPlayer.normalizeAuthValue(),
            poTokenGvsVideoId = poTokenGvsVideoId.normalizeAuthValue(),
            poTokenPlayerVideoId = poTokenPlayerVideoId.normalizeAuthValue(),
        )

    fun withVideoPoToken(videoId: String, token: String): PlaybackAuthState =
        copy(
            poTokenGvs = token,
            poTokenGvsVideoId = videoId,
            poTokenPlayer = token,
            poTokenPlayerVideoId = videoId,
            webClientPoTokenEnabled = true,
        )

    fun withoutVideoPoTokens(videoId: String): PlaybackAuthState =
        copy(
            poTokenGvs = poTokenGvs.takeUnless { poTokenGvsVideoId == videoId },
            poTokenGvsVideoId = poTokenGvsVideoId.takeUnless { it == videoId },
            poTokenPlayer = poTokenPlayer.takeUnless { poTokenPlayerVideoId == videoId },
            poTokenPlayerVideoId = poTokenPlayerVideoId.takeUnless { it == videoId },
        )

    fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String? = null,
        videoId: String? = null,
    ): String? {
        val explicit = explicitPoToken.normalizeAuthValue()
        if (explicit != null) return explicit
        if (!webClientPoTokenEnabled) return null
        if (!needsServiceIntegrity(client)) return null
        return poTokenPlayer?.takeIf { poTokenPlayerVideoId == null || poTokenPlayerVideoId == videoId } ?: poToken
    }

    fun resolveGvsPoToken(client: YouTubeClient? = null, videoId: String? = null): String? {
        if (client != null && !needsServiceIntegrity(client)) return null
        if (!webClientPoTokenEnabled) return null
        return poTokenGvs?.takeIf { poTokenGvsVideoId == null || poTokenGvsVideoId == videoId } ?: poToken
    }

    companion object {
        val EMPTY = PlaybackAuthState()

        fun needsServiceIntegrity(client: YouTubeClient): Boolean {
            val name = client.clientName.uppercase(Locale.US)
            return name == "WEB" ||
                name == "WEB_REMIX" ||
                name == "WEB_CREATOR" ||
                name == "MWEB" ||
                name == "WEB_EMBEDDED_PLAYER" ||
                name == "TVHTML5" ||
                name == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" ||
                name == "TVHTML5_SIMPLY"
        }
    }
}

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? {
    val normalized = this.normalizeAuthValue()?.decodePercentEscapes()?.trim() ?: return null
    if (normalized.isBlank() || normalized == "||") return null
    return if (normalized.endsWith("||")) {
        normalized.substringBefore("||").trim().takeIf(String::isNotBlank)
    } else {
        normalized
    }
}

private fun String.decodePercentEscapes(): String {
    if (!contains('%')) return this

    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val high = Character.digit(this[index + 1], 16)
            val low = Character.digit(this[index + 2], 16)
            if (high >= 0 && low >= 0) {
                builder.append(((high shl 4) + low).toChar())
                index += 3
                continue
            }
        }
        builder.append(char)
        index += 1
    }
    return builder.toString()
}
