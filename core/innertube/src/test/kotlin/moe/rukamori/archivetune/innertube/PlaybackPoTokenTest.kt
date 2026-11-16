package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.models.YouTubeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPoTokenTest {
    private val web = YouTubeClient.WEB_REMIX
    private val url = "https://rr1.googlevideo.com/videoplayback?itag=251"
    private val auth = PlaybackAuthState(visitorData = "visitor").withVideoPoToken("video-a", "token-a")

    @Test
    fun generatedTokenIsUsedForBothPlayerAndGvsOfItsVideo() {
        assertEquals("token-a", PlayerClient.resolvePlayerPoToken(web, null, auth, "video-a"))
        assertEquals("$url&pot=token-a", PlayerClient.appendGvsPoToken(url, web, auth, "video-a"))
    }

    @Test
    fun generatedTokenCannotBeUsedForAnotherVideoOrWithoutAVideoId() {
        assertNull(PlayerClient.resolvePlayerPoToken(web, null, auth, "video-b"))
        assertEquals(url, PlayerClient.appendGvsPoToken(url, web, auth, "video-b"))
        assertNull(auth.resolvePlayerPoToken(web))
        assertNull(auth.resolveGvsPoToken(web))
    }

    @Test
    fun clientsWithoutWebTokensDoNotReceiveAGeneratedToken() {
        assertNull(auth.resolvePlayerPoToken(YouTubeClient.VISIONOS, videoId = "video-a"))
        assertEquals(url, PlayerClient.appendGvsPoToken(url, YouTubeClient.VISIONOS, auth, "video-a"))
    }

    @Test
    fun existingStreamTokenIsNotReplaced() {
        val signedUrl = "$url&pot=server-token"
        assertEquals(signedUrl, PlayerClient.appendGvsPoToken(signedUrl, web, auth, "video-a"))
    }

    @Test
    fun manuallyConfiguredTokensKeepTheirExistingUnboundContract() {
        val manual = PlaybackAuthState(
            poTokenGvs = "manual-gvs",
            poTokenPlayer = "manual-player",
            webClientPoTokenEnabled = true,
        )
        assertEquals("manual-player", manual.resolvePlayerPoToken(web))
        assertEquals("manual-player", manual.resolvePlayerPoToken(web, videoId = "video-b"))
        assertEquals("manual-gvs", manual.resolveGvsPoToken(web))
        assertEquals("manual-gvs", manual.resolveGvsPoToken(web, "video-b"))
        assertEquals("explicit", auth.resolvePlayerPoToken(web, "explicit", "video-b"))
    }

    @Test
    fun legacyTokenRemainsAvailableWhenGeneratedTokenBelongsToAnotherVideo() {
        val withLegacy = auth.copy(poToken = "manual-legacy")
        assertEquals("manual-legacy", withLegacy.resolvePlayerPoToken(web, videoId = "video-b"))
        assertEquals("manual-legacy", withLegacy.resolveGvsPoToken(web, "video-b"))
    }

    @Test
    fun invalidatingAVideoKeepsLoginSessionAndOtherVideoTokens() {
        val loggedIn = auth.copy(cookie = "SAPISID=test", dataSyncId = "account", poToken = "manual")
        val cleared = loggedIn.withoutVideoPoTokens("video-a")
        assertEquals(
            PlaybackAuthState(
                cookie = "SAPISID=test",
                visitorData = "visitor",
                dataSyncId = "account",
                poToken = "manual",
                webClientPoTokenEnabled = true,
            ),
            cleared,
        )
        assertEquals(loggedIn, loggedIn.withoutVideoPoTokens("video-b"))
    }
}
