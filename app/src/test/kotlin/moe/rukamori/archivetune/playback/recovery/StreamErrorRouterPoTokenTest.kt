package moe.rukamori.archivetune.playback.recovery

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.engine.ytStreamCacheKey
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamErrorRouterPoTokenTest {
    private val webClientKey = StreamClientUtils.resolveRequestProfile(clientParam = "WEB_REMIX").clientKey
    private val baseAuth = PlaybackAuthState(cookie = "SAPISID=test", visitorData = "visitor", dataSyncId = "account", webClientPoTokenEnabled = true)
    private var auth = baseAuth.withVideoPoToken("video-a", "old-token")
    private var current = MediaItem.Builder().setMediaId("video-a").build()
    private val actions = mockk<PlaybackRecoveryEngine.PlayerActions>(relaxed = true)
    private val cacheOps = mockk<PlaybackRecoveryEngine.CacheOps>(relaxed = true)
    private val events = mutableListOf<String>()
    private val router = StreamErrorRouter(
        scope = CoroutineScope(Dispatchers.Unconfined),
        playerActions = actions,
        cacheOps = cacheOps,
        networkState = mockk(relaxed = true),
        loginPrompt = mockk(relaxed = true),
        networkStallReviver = mockk(relaxed = true),
    )

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.getStackTraceString(any()) } answers { firstArg<Throwable>().stackTraceToString() }
        StreamUrlCache.clearPlaybackAuthCaches()
        mockkObject(YouTube, BotGuardTokenGenerator)
        every { YouTube.currentPlaybackAuthState() } answers { auth }
        every { YouTube.authState = any() } answers { auth = firstArg() }
        every { actions.currentMediaItem } answers { current }
        every { actions.registerRetryAttempt("video-a") } returns true
        every { actions.prepare() } answers { events += "prepare" }
        every { cacheOps.getCachedFailedPlaybackUrl(any(), any()) } returns null
        every { cacheOps.getCachedExtractorFailedPlaybackUrl(any(), any()) } returns null
        coEvery { BotGuardTokenGenerator.invalidatePlayerToken("video-a") } answers { events += "invalidate" }
    }

    @After
    fun tearDown() {
        StreamUrlCache.clearPlaybackAuthCaches()
        unmockkObject(YouTube, BotGuardTokenGenerator)
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun youtube403InvalidatesOnlyTheFailedVideosTokensBeforeTheExistingRetry() {
        assertTrue(router.retryPlaybackAfterStreamFailure("video-a", false, response(403)))

        assertEquals(listOf("invalidate", "prepare"), events)
        assertEquals(baseAuth, auth)
        verify(exactly = 1) { actions.registerRetryAttempt("video-a") }
        verify { cacheOps.invalidatePlaybackUrlCache("video-a") }
        verify { cacheOps.removeExtractorPlaybackUrl("video-a") }
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidateAll() }
    }

    @Test
    fun youtube403RemovesTheFailedUrlButKeepsClientBackoffAcrossTokenRefresh() {
        val failedUrl = "https://rr1.googlevideo.com/videoplayback?c=WEB_REMIX&itag=251"
        StreamUrlCache.putCachedStreamUrl("video-a", failedUrl, 251, 300, auth.streamCacheFingerprint)
        StreamUrlCache.putCachedStreamUrl("video-b", failedUrl, 251, 300, auth.streamCacheFingerprint)
        val failedCache = StreamUrlCache.getCachedStreamUrl("video-a")!!.toAuthScopedCacheValue()
        every { cacheOps.getCachedFailedPlaybackUrl("video-a", failedUrl) } returns failedCache

        // Prefetching another video must not make A look expired or hide its client failure.
        auth = baseAuth.withVideoPoToken("video-b", "token-b")
        assertTrue(router.retryPlaybackAfterStreamFailure("video-a", false, response(403)))

        assertNull(StreamUrlCache.getCachedStreamUrl("video-a"))
        assertNotNull(StreamUrlCache.getValidCachedStreamUrl("video-b", auth.streamCacheFingerprint))
        assertTrue(StreamUrlCache.isStreamClientTemporarilyBlocked("video-a", webClientKey, auth.streamCacheFingerprint))
        auth = auth.withVideoPoToken("video-a", "new-token")
        assertTrue(StreamUrlCache.isStreamClientTemporarilyBlocked("video-a", webClientKey, auth.streamCacheFingerprint))
        assertFalse(StreamUrlCache.isStreamClientTemporarilyBlocked("video-b", webClientKey, auth.streamCacheFingerprint))
        assertFalse(StreamUrlCache.isStreamClientTemporarilyBlocked("video-a", webClientKey, auth.copy(visitorData = "other").streamCacheFingerprint))
    }

    @Test
    fun youtube403KeepsClientBackoffAfterClearingTheCurrentVideosToken() {
        assertTrue(router.retryPlaybackAfterStreamFailure("video-a", false, response(403)))

        assertEquals(baseAuth, auth)
        assertTrue(StreamUrlCache.isStreamClientTemporarilyBlocked("video-a", webClientKey, auth.streamCacheFingerprint))
        auth = auth.withVideoPoToken("video-a", "new-token")
        assertTrue(StreamUrlCache.isStreamClientTemporarilyBlocked("video-a", webClientKey, auth.streamCacheFingerprint))
    }

    @Test
    fun recoveryKeepsAnotherVideosTokens() {
        auth = baseAuth.withVideoPoToken("video-b", "token-b")
        val otherVideoAuth = auth

        router.retryPlaybackAfterStreamFailure("video-a", false, response(403))

        assertEquals(otherVideoAuth, auth)
        coVerify(exactly = 1) { BotGuardTokenGenerator.invalidatePlayerToken("video-a") }
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidatePlayerToken("video-b") }
    }

    @Test
    fun spotifyQueueItemInvalidatesTheFailedYouTubeStreamButRetriesTheQueueItem() {
        val queueId = "spotify:track-a"
        current = MediaItem.Builder().setMediaId(queueId).build()
        every { actions.registerRetryAttempt(queueId) } returns true

        assertTrue(router.retryPlaybackAfterStreamFailure(queueId, false, response(403, key = ytStreamCacheKey("video-a"))))

        assertEquals(listOf("invalidate", "prepare"), events)
        assertEquals(baseAuth, auth)
        verify(exactly = 1) { actions.registerRetryAttempt(queueId) }
        verify { cacheOps.getCachedFailedPlaybackUrl("video-a", any()) }
        verify { cacheOps.invalidatePlaybackUrlCache("video-a") }
        verify { cacheOps.removeExtractorPlaybackUrl("video-a") }
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidatePlayerToken(queueId) }
    }

    @Test
    fun exhaustedRetryDoesNotResetTokensOrAddAnotherRetry() {
        every { actions.registerRetryAttempt("video-a") } returns false

        assertFalse(router.retryPlaybackAfterStreamFailure("video-a", false, response(403)))

        assertTrue(events.isEmpty())
        assertEquals("old-token", auth.poTokenPlayer)
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidatePlayerToken(any()) }
    }

    @Test
    fun fullyCachedPlaybackDoesNotResetTokensOrRetry() {
        assertFalse(router.retryPlaybackAfterStreamFailure("video-a", true, response(403)))

        assertTrue(events.isEmpty())
        verify(exactly = 0) { actions.registerRetryAttempt(any()) }
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidatePlayerToken(any()) }
    }

    @Test
    fun otherHttpFailuresAndNonYouTubeStreamsKeepTheirExistingRecovery() {
        router.retryPlaybackAfterStreamFailure("video-a", false, response(410))
        router.retryPlaybackAfterStreamFailure("video-a", false, response(403, "audio.example.com"))

        assertEquals(listOf("prepare", "prepare"), events)
        assertEquals("old-token", auth.poTokenPlayer)
        coVerify(exactly = 0) { BotGuardTokenGenerator.invalidatePlayerToken(any()) }
    }

    @Test
    fun switchingTracksDuringInvalidationDoesNotPrepareTheNewTrack() {
        coEvery { BotGuardTokenGenerator.invalidatePlayerToken("video-a") } answers {
            current = MediaItem.Builder().setMediaId("video-b").build()
        }

        router.retryPlaybackAfterStreamFailure("video-a", false, response(403))

        verify(exactly = 0) { actions.prepare() }
    }

    private fun response(code: Int, host: String = "rr1.googlevideo.com", key: String? = null): HttpDataSource.InvalidResponseCodeException {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "https://$host/videoplayback?c=WEB_REMIX&itag=251"
        every { uri.host } returns host
        val spec = DataSpec.Builder().setUri(uri).setKey(key).build()
        return HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), spec, byteArrayOf())
    }
}
