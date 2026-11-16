package moe.rukamori.archivetune.utils.resolver.fetcher

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.playback.PlaybackTiming
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.potoken.PoTokenResult
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackMetadataFetcherTest {
    private val initialAuth = PlaybackAuthState(cookie = "SAPISID=test", visitorData = "visitor", dataSyncId = "account")
    private var currentAuth = initialAuth
    private val requests = mutableListOf<Pair<String, PlaybackAuthState>>()
    private val playerTokens = mutableListOf<String?>()
    private val response = mockk<PlayerResponse>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(YouTube, BotGuardTokenGenerator, PlaybackStreamUrlDecipher, PlaybackTiming)
        every { YouTube.currentPlaybackAuthState() } answers { currentAuth }
        every { YouTube.authState = any() } answers { currentAuth = firstArg() }
        every { PlaybackTiming.nowNanos() } returns 0L
        every { PlaybackTiming.log(any(), any(), any(), any(), any(), any()) } returns Unit
        every { response.videoDetails } returns null
        coEvery { PlaybackStreamUrlDecipher.getSignatureTimestampOrNull(any()) } returns null
        coEvery { BotGuardTokenGenerator.mintToken(any(), "visitor") } answers {
            PoTokenResult("video-token-${firstArg<String>()}", "session-token")
        }
        coEvery { YouTube.player(any(), any(), any(), any(), any(), any(), any()) } answers {
            requests += firstArg<String>() to arg<PlaybackAuthState>(6)
            playerTokens += arg<String?>(4)
            Result.success(response)
        }
    }

    @After
    fun tearDown() {
        unmockkObject(YouTube, BotGuardTokenGenerator, PlaybackStreamUrlDecipher, PlaybackTiming)
    }

    @Test
    fun playbackAndMetadataOnlyUseTheRequestedVideosTokenForPlayerAndGvs() = runBlocking {
        val playback = PlaybackMetadataFetcher.fetchMetadataForPlayback("video-a", null, null, initialAuth, true, 1L)
        PlaybackMetadataFetcher.fetchMetadataOnly("video-b")

        assertEquals("video-token-video-a", playback.authState.resolveGvsPoToken(YouTubeClient.WEB_REMIX, "video-a"))
        assertEquals(listOf("video-a", "video-b"), requests.map { it.first })
        assertEquals(listOf("video-token-video-a", "video-token-video-b"), playerTokens)
        requests.forEach { (videoId, state) ->
            assertEquals("video-token-$videoId", state.resolvePlayerPoToken(YouTubeClient.WEB_REMIX, videoId = videoId))
            assertEquals("video-token-$videoId", state.resolveGvsPoToken(YouTubeClient.WEB_REMIX, videoId))
        }
        assertNull(currentAuth.resolveGvsPoToken(YouTubeClient.WEB_REMIX, "video-a"))
        coVerify(exactly = 1) { BotGuardTokenGenerator.mintToken("video-a", "visitor") }
        coVerify(exactly = 1) { BotGuardTokenGenerator.mintToken("video-b", "visitor") }
    }

    @Test
    fun failedMintDoesNotSendAnotherVideosToken() = runBlocking {
        currentAuth = initialAuth.withVideoPoToken("video-a", "token-a")
        coEvery { BotGuardTokenGenerator.mintToken("video-b", "visitor") } returns null

        PlaybackMetadataFetcher.fetchMetadataOnly("video-b")

        assertNull(playerTokens.single())
        assertNull(requests.single().second.resolveGvsPoToken(YouTubeClient.WEB_REMIX, "video-b"))
        assertEquals("token-a", currentAuth.resolveGvsPoToken(YouTubeClient.WEB_REMIX, "video-a"))
    }

    @Test
    fun aMintStartedBeforeAnAccountChangeDoesNotOverwriteTheNewSession() = runBlocking {
        val newSession = PlaybackAuthState(visitorData = "other-visitor")
        coEvery { BotGuardTokenGenerator.mintToken("video-a", "visitor") } answers {
            currentAuth = newSession
            PoTokenResult("video-token", "session-token")
        }

        PlaybackMetadataFetcher.fetchMetadataOnly("video-a", authState = initialAuth)

        assertEquals(newSession, currentAuth)
        assertEquals("video-token", requests.single().second.poTokenGvs)
    }

    @Test
    fun mintCancellationDoesNotContinueToAPlayerRequest() = runBlocking {
        coEvery { BotGuardTokenGenerator.mintToken(any(), any()) } throws CancellationException("cancelled")

        val failure = runCatching { PlaybackMetadataFetcher.fetchMetadataOnly("video-a") }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(requests.isEmpty())
    }
}
