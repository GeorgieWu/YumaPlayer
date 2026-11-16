package moe.rukamori.archivetune.playback.resolvers

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSpecSupportTest {
    @Test
    fun extractorDoesNotBypassVideoBindingThroughRawTokenFields() {
        val auth = PlaybackAuthState(visitorData = "visitor").withVideoPoToken("video-a", "token-a")

        assertEquals("token-a", auth.resolveExtractorPoToken("video-a"))
        assertEquals("token-a", auth.resolveExtractorGvsToken("video-a"))
        assertNull(auth.resolveExtractorPoToken("video-b"))
        assertNull(auth.resolveExtractorGvsToken("video-b"))
    }

    @Test
    fun manuallyConfiguredExtractorTokensRemainUsable() {
        val auth = PlaybackAuthState(poTokenGvs = "manual-gvs", poTokenPlayer = "manual-player")

        assertEquals("manual-gvs", auth.resolveExtractorPoToken("video-a"))
        assertEquals("manual-gvs", auth.resolveExtractorGvsToken("video-a"))
    }
}
