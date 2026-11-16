/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver.fetcher

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.getPlaybackPlayerResponseOrThrow
import moe.rukamori.archivetune.utils.isInvalidPlaybackLoginContextFailure
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.resolver.PlaybackAuthCoordinator
import moe.rukamori.archivetune.utils.resolver.PlaybackClientSelector
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import timber.log.Timber

internal data class PlaybackMetadataResult(
    val playerResponse: PlayerResponse,
    val expectedDurationMs: Long?,
    val authState: PlaybackAuthState,
    val canUseLoggedInPlayback: Boolean,
    val metadataClient: YouTubeClient,
)

internal object PlaybackMetadataFetcher {
    private const val TAG = "PlaybackMetadataFetcher"

    suspend fun fetchMetadataForPlayback(
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        initialAuthState: PlaybackAuthState,
        initialCanUseLoggedInPlayback: Boolean,
        fetchAttemptId: Long,
    ): PlaybackMetadataResult {
        var authState = initialAuthState
        var canUseLoggedInPlayback = initialCanUseLoggedInPlayback

        val metadataClient = PlaybackClientSelector.resolveMetadataClient(authState)
        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "METADATA_CLIENT_SELECTED",
                attemptId = fetchAttemptId,
                mediaId = videoId,
                extra = "client=${PlaybackClientSelector.describeClient(metadataClient)}",
            )
        }

        Timber.tag(TAG).i("Fetching metadata response using client: ${metadataClient.clientName}")

        if (metadataClient.useWebPoTokens) {
            authState = mintPoTokens(videoId, authState)
        }
        var metadataPoToken = authState.resolvePlayerPoToken(metadataClient, videoId = videoId)

        val metadataStartNanos = if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()
        } else 0L
        var metadataResult =
            YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = metadataClient,
                signatureTimestamp = signatureTimestamp,
                poToken = metadataPoToken,
                setLogin = true,
                authState = authState,
            )
        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "METADATA_FETCH_END",
                attemptId = fetchAttemptId,
                mediaId = videoId,
                startNanos = metadataStartNanos,
                extra = "client=${PlaybackClientSelector.describeClient(metadataClient)} ok=${metadataResult.isSuccess}",
            )
        }
        val metadataFailure = metadataResult.exceptionOrNull()
        if (metadataFailure != null && canUseLoggedInPlayback && metadataFailure.isInvalidPlaybackLoginContextFailure()) {
            Timber.tag(TAG).w(
                metadataFailure,
                "Logged-in playback context is stale for %s; retrying metadata with visitor playback",
                videoId,
            )
            authState =
                PlaybackAuthCoordinator.ensureVisitorDataReady(
                    videoId = videoId,
                    authState = authState.copy(dataSyncId = null).withoutVideoPoTokens(videoId).normalized(),
                    forceRefresh = true,
                    reason = "stale logged-in playback context",
                )
            canUseLoggedInPlayback = false
            YouTube.authState = authState
            StreamUrlCache.clearPlaybackAuthCaches()

            if (metadataClient.useWebPoTokens) {
                authState = mintPoTokens(videoId, authState)
            }
            metadataPoToken = authState.resolvePlayerPoToken(metadataClient, videoId = videoId)

            metadataResult =
                YouTube.player(
                    videoId = videoId,
                    playlistId = playlistId,
                    client = metadataClient,
                    signatureTimestamp = signatureTimestamp,
                    poToken = metadataPoToken,
                    setLogin = true,
                    authState = authState,
                )
        }

        val metadataPlayerResponse = metadataResult.getPlaybackPlayerResponseOrThrow(videoId, authState)
        val expectedDurationMs =
            metadataPlayerResponse.videoDetails
                ?.lengthSeconds
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.times(1000L)

        return PlaybackMetadataResult(
            playerResponse = metadataPlayerResponse,
            expectedDurationMs = expectedDurationMs,
            authState = authState,
            canUseLoggedInPlayback = canUseLoggedInPlayback,
            metadataClient = metadataClient,
        )
    }

    suspend fun fetchMetadataOnly(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> {
        Timber.tag(TAG).i("Fetching metadata player response for videoId: $videoId")

        val signatureTimestamp = PlaybackStreamUrlDecipher.getSignatureTimestampOrNull(videoId)
        val currentAuthState =
            if (PlaybackClientSelector.MAIN_CLIENT.useWebPoTokens) mintPoTokens(videoId, authState) else authState
        val poToken = currentAuthState.resolvePlayerPoToken(PlaybackClientSelector.MAIN_CLIENT, videoId = videoId)

        return YouTube
            .player(
                videoId = videoId,
                playlistId = playlistId,
                client = PlaybackClientSelector.MAIN_CLIENT,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = true,
                authState = currentAuthState,
            ).onSuccess { Timber.tag(TAG).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(TAG).e(it, "Failed to fetch metadata") }
    }

    private suspend fun mintPoTokens(videoId: String, authState: PlaybackAuthState): PlaybackAuthState {
        val sessionId = authState.visitorData ?: return authState
        val tokenResult = try {
            BotGuardTokenGenerator.mintToken(videoId, sessionId)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "PoToken generation failed for %s", videoId)
            null
        } ?: return authState

        val updatedAuthState = authState.withVideoPoToken(videoId, tokenResult.playerToken)
        val currentAuthState = YouTube.currentPlaybackAuthState()
        if (
            currentAuthState.cookie == authState.cookie &&
            currentAuthState.visitorData == authState.visitorData &&
            currentAuthState.dataSyncId == authState.dataSyncId
        ) {
            YouTube.authState = currentAuthState.withVideoPoToken(videoId, tokenResult.playerToken)
        }
        return updatedAuthState
    }
}
