package moe.rukamori.archivetune.auth

import android.content.Context
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.YouTubeLoginDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class YouTubeLoginRepositoryTest {
    private val repository = YouTubeLoginRepository(mockk<Context>())

    @Before
    fun setUp() {
        mockkObject(YouTube, YouTubeLoginDiagnostics)
        every { YouTube.authState = any() } just Runs
        every { YouTubeLoginDiagnostics.failure(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun verifiesWithFullResolvedSnapshotAndPropagatesAccountFailure() = runTest {
        val verifiedState = slot<PlaybackAuthState>()
        val failure = IllegalStateException("Test account verification failure")
        coEvery { YouTube.accountDataSyncId() } returns Result.success("test-channel||test-user")
        coEvery { YouTube.accountInfo(capture(verifiedState)) } returns Result.failure(failure)

        val result = repository.completeLogin("SAPISID=test-cookie", "test-visitor", null)

        assertSame(failure, result.exceptionOrNull())
        assertEquals("SAPISID=test-cookie", verifiedState.captured.cookie)
        assertEquals("test-visitor", verifiedState.captured.visitorData)
        assertEquals("test-channel||test-user", verifiedState.captured.dataSyncId)
        coVerify(exactly = 1) { YouTube.accountInfo(any()) }
        verify(exactly = 1) { YouTubeLoginDiagnostics.failure("account_verify", failure) }
        verify(exactly = 1) { YouTubeLoginDiagnostics.failure(any(), any()) }
    }

    @Test
    fun normalizesTheResolvedNetworkIdBeforeVerification() = runTest {
        val verifiedState = slot<PlaybackAuthState>()
        coEvery { YouTube.accountDataSyncId() } returns Result.success("test-user%7C%7C")
        coEvery { YouTube.accountInfo(capture(verifiedState)) } returns Result.failure(IllegalStateException("Test verification failure"))

        repository.completeLogin("SAPISID=test-cookie", "test-visitor", "old-page-id")

        assertEquals("test-user", verifiedState.captured.dataSyncId)
        assertEquals("SAPISID=test-cookie", verifiedState.captured.cookie)
        assertEquals("test-visitor", verifiedState.captured.visitorData)
    }

    @Test
    fun missingIdStillPreventsLoginAndDoesNotRunAccountVerification() = runTest {
        coEvery { YouTube.accountDataSyncId() } returns Result.failure(IllegalStateException("Test missing context"))
        val result = repository.completeLogin("SAPISID=test-cookie", null, null)
        assertTrue(result.exceptionOrNull() is MissingYouTubeDataSyncIdException)
        coVerify(exactly = 0) { YouTube.accountInfo(any()) }
        verify(exactly = 1) { YouTubeLoginDiagnostics.failure("datasync_lookup", any()) }
    }

    @Test
    fun cancelledLookupPropagatesWithoutVerificationOrFailureLog() = runTest {
        val cancellation = CancellationException("Test cancellation")
        coEvery { YouTube.accountDataSyncId() } returns Result.failure(cancellation)

        try {
            repository.completeLogin("SAPISID=test-cookie", null, null)
            fail("Expected cancellation")
        } catch (failure: CancellationException) {
            // Coroutine stack-trace recovery can copy the exception across withContext.
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it === cancellation })
        }

        coVerify(exactly = 0) { YouTube.accountInfo(any()) }
        verify(exactly = 0) { YouTubeLoginDiagnostics.failure(any(), any()) }
    }
}
