package moe.rukamori.archivetune.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackAuthStateFingerprintTest {
    private val session = PlaybackAuthState(
        cookie = "SAPISID=test",
        visitorData = "visitor",
        dataSyncId = "account",
        webClientPoTokenEnabled = true,
    )

    @Test
    fun generatedTokenLifecycleDoesNotChangeCacheIdentity() {
        val videoA = session.withVideoPoToken("video-a", "token-a")
        val states = listOf(
            videoA,
            videoA.withVideoPoToken("video-b", "token-b"),
            videoA.withVideoPoToken("video-a", "refreshed-token"),
            videoA.copy(poTokenGvsVideoId = "video-b", poTokenPlayerVideoId = "video-b"),
            videoA.withoutVideoPoTokens("video-a"),
        )

        states.forEach { assertEquals(session.streamCacheFingerprint, it.streamCacheFingerprint) }
        assertNotEquals(session.fingerprint, videoA.fingerprint)
        states.drop(1).forEach { assertNotEquals(videoA.fingerprint, it.fingerprint) }
    }

    @Test
    fun changingSessionOrManualConfigurationInvalidatesCacheIdentity() {
        val changedStates = listOf(
            session.copy(cookie = "SAPISID=other"),
            session.copy(visitorData = "other-visitor"),
            session.copy(dataSyncId = "other-account"),
            session.copy(poToken = "manual-legacy"),
            session.copy(poTokenGvs = "manual-gvs"),
            session.copy(poTokenPlayer = "manual-player"),
            session.copy(webClientPoTokenEnabled = false),
        )

        changedStates.forEach { assertNotEquals(session.streamCacheFingerprint, it.streamCacheFingerprint) }
    }

    @Test
    fun changingOrRemovingExistingManualTokensInvalidatesCacheIdentity() {
        val manual = session.copy(poToken = "legacy", poTokenGvs = "gvs", poTokenPlayer = "player")
        val changedStates = listOf(
            manual.copy(poToken = "new-legacy"),
            manual.copy(poTokenGvs = "new-gvs"),
            manual.copy(poTokenPlayer = "new-player"),
            manual.copy(poToken = null),
            manual.copy(poTokenGvs = null),
            manual.copy(poTokenPlayer = null),
            manual.copy(webClientPoTokenEnabled = false),
        )

        changedStates.forEach { assertNotEquals(manual.streamCacheFingerprint, it.streamCacheFingerprint) }
    }

    @Test
    fun generatedTokensDoNotHideRemainingManualConfiguration() {
        val generated = session.copy(poToken = "legacy").withVideoPoToken("video-a", "token-a")
        val mixed = generated.copy(poTokenGvs = "manual-gvs", poTokenGvsVideoId = null)

        assertNotEquals(generated.streamCacheFingerprint, generated.copy(poToken = "new-legacy").streamCacheFingerprint)
        assertNotEquals(mixed.streamCacheFingerprint, mixed.copy(poTokenGvs = "new-gvs").streamCacheFingerprint)
        assertEquals(mixed.streamCacheFingerprint, mixed.copy(poTokenPlayer = "new-generated-token").streamCacheFingerprint)
    }
}
