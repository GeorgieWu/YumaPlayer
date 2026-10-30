package moe.rukamori.archivetune.utils

import io.ktor.client.plugins.ResponseException
import moe.rukamori.archivetune.BuildConfig
import timber.log.Timber

/** Debug-only metadata. Never log URLs, cookies, IDs, response bodies or exception messages. */
internal object YouTubeLoginDiagnostics {
    const val TAG = "YouTubeLogin"

    fun event(message: String) {
        if (BuildConfig.DEBUG) Timber.tag(TAG).d(message)
    }

    fun failure(stage: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) return
        val causes = generateSequence(throwable) { it.cause }.take(6).toList()
        val types = causes.joinToString("/") { it.javaClass.simpleName }
        val status = causes.filterIsInstance<ResponseException>().firstOrNull()?.response?.status?.value
        event("failure stage=$stage types=$types http_status=${status ?: "none"}")
    }
}
