package moe.rukamori.archivetune.viewmodels

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.auth.CompleteYouTubeLoginUseCase
import moe.rukamori.archivetune.auth.MissingYouTubeDataSyncIdException
import moe.rukamori.archivetune.auth.SaveYouTubePoTokenUseCase
import moe.rukamori.archivetune.auth.UpdateYouTubeLoginContextUseCase
import moe.rukamori.archivetune.auth.YouTubeLoginSession
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val completeLogin = mockk<CompleteYouTubeLoginUseCase>()
    private val updateContext = mockk<UpdateYouTubeLoginContextUseCase>()
    private val savePoToken = mockk<SaveYouTubePoTokenUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { updateContext(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun visitorCookiesDoNotStartLoginButAuthenticatedCookiesDo() = runTest(dispatcher) {
        coEvery { completeLogin(any(), any(), any()) } returns Result.success(session())
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("VISITOR_INFO1_LIVE=test-visitor; PREF=test-pref")
        advanceUntilIdle()
        assertEquals(LoginScreenState.Empty, viewModel.screenState.value)
        coVerify(exactly = 0) { completeLogin(any(), any(), any()) }

        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        advanceUntilIdle()
        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        coVerify(exactly = 1) { completeLogin("SAPISID=test-cookie", any(), any()) }
    }

    @Test
    fun idArrivingDuringLookupRetriesWithTheNewCandidate() = runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<YouTubeLoginSession>>()
        coEvery { completeLogin("SAPISID=test-cookie", any(), null) } coAnswers { lookup.await() }
        coEvery { completeLogin("SAPISID=test-cookie", any(), "test-user") } returns Result.success(session())
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        runCurrent()
        assertEquals(LoginScreenState.Loading, viewModel.screenState.value)

        viewModel.onDataSyncIdExtracted("test-user||")
        runCurrent()
        lookup.complete(Result.failure(MissingYouTubeDataSyncIdException()))
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        coVerify(exactly = 1) { completeLogin("SAPISID=test-cookie", any(), null) }
        coVerify(exactly = 1) { completeLogin("SAPISID=test-cookie", any(), "test-user") }
    }

    @Test
    fun idArrivingAfterMissingIdErrorAlsoRetries() = runTest(dispatcher) {
        coEvery { completeLogin("SAPISID=test-cookie", any(), null) } returns Result.failure(MissingYouTubeDataSyncIdException())
        coEvery { completeLogin("SAPISID=test-cookie", any(), "test-user") } returns Result.success(session())
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        advanceUntilIdle()
        assertEquals(LoginScreenState.Error(LoginError.MissingDataSyncId), viewModel.screenState.value)

        viewModel.onDataSyncIdExtracted("test-user")
        advanceUntilIdle()
        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
    }

    @Test
    fun missingOrUnchangedIdDoesNotCauseAnUnboundedRetry() = runTest(dispatcher) {
        coEvery { completeLogin(any(), any(), any()) } returns Result.failure(MissingYouTubeDataSyncIdException())
        val viewModel = viewModel()
        viewModel.onDataSyncIdExtracted("test-user")
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        advanceUntilIdle()
        assertEquals(LoginScreenState.Error(LoginError.MissingDataSyncId), viewModel.screenState.value)
        coVerify(exactly = 1) { completeLogin(any(), any(), any()) }
    }

    @Test
    fun lateIdDoesNotTurnAnUnrelatedFailureIntoSuccess() = runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<YouTubeLoginSession>>()
        coEvery { completeLogin(any(), any(), any()) } coAnswers { lookup.await() }
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        runCurrent()
        viewModel.onDataSyncIdExtracted("test-user")
        runCurrent()
        lookup.complete(Result.failure(IllegalStateException("Test account verification failure")))
        advanceUntilIdle()
        assertEquals(LoginScreenState.Error(LoginError.LoginFailed), viewModel.screenState.value)
        coVerify(exactly = 1) { completeLogin(any(), any(), any()) }
    }

    @Test
    fun repeatedCookieCallbacksKeepOneInFlightAttempt() = runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<YouTubeLoginSession>>()
        coEvery { completeLogin(any(), any(), any()) } coAnswers { lookup.await() }
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        runCurrent()
        viewModel.onCookiesCaptured("SAPISID=test-cookie")
        runCurrent()
        coVerify(exactly = 1) { completeLogin(any(), any(), any()) }
        lookup.complete(Result.failure(MissingYouTubeDataSyncIdException()))
        advanceUntilIdle()
        assertEquals(LoginScreenState.Error(LoginError.MissingDataSyncId), viewModel.screenState.value)
    }

    @Test
    fun newCookieCancelsTheOldLookupWithoutOverwritingNewSuccess() = runTest(dispatcher) {
        val oldLookup = CompletableDeferred<Result<YouTubeLoginSession>>()
        var oldLookupCancelled = false
        coEvery { completeLogin("SAPISID=old-cookie", any(), any()) } coAnswers {
            try {
                oldLookup.await()
            } finally {
                oldLookupCancelled = true
            }
        }
        coEvery { completeLogin("SAPISID=new-cookie", any(), any()) } returns Result.success(session())
        val viewModel = viewModel()
        viewModel.onCookiesCaptured("SAPISID=old-cookie")
        runCurrent()
        viewModel.onCookiesCaptured("SAPISID=new-cookie")
        advanceUntilIdle()
        assertTrue(oldLookupCancelled)
        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
    }

    private fun viewModel() = LoginViewModel(completeLogin, updateContext, savePoToken)

    private fun session() =
        YouTubeLoginSession(
            authState = PlaybackAuthState(cookie = "SAPISID=test-cookie", dataSyncId = "test-user"),
            accountName = "Test Account",
            accountEmail = "",
            accountChannelHandle = "",
        )
}
