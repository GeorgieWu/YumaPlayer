package moe.rukamori.archivetune.viewmodels

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.auth.CompleteYouTubeLoginUseCase
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val completeLogin = mockk<CompleteYouTubeLoginUseCase>()
    private val cookie = "SAPISID=test-cookie"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun verifiedCookieCompletesLoginWithoutRequiringDataSyncId() = runTest(dispatcher) {
        coEvery { completeLogin(cookie, null, null) } returns Result.success(PlaybackAuthState(cookie = cookie))
        val viewModel = LoginViewModel(completeLogin)
        viewModel.onCookiesCaptured(cookie)
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        viewModel.onDataSyncIdExtracted("another-account")
        viewModel.onCookiesCaptured(cookie)
        advanceUntilIdle()
        coVerify(exactly = 1) { completeLogin(cookie, null, null) }
    }

    @Test
    fun availableWebViewContextIsIncludedInSavedLogin() = runTest(dispatcher) {
        val authState = PlaybackAuthState(cookie, "visitor", "12345")
        coEvery { completeLogin(cookie, "visitor", "12345") } returns Result.success(authState)
        val viewModel = LoginViewModel(completeLogin)
        viewModel.onVisitorDataExtracted("visitor")
        viewModel.onDataSyncIdExtracted("12345||")
        viewModel.onCookiesCaptured(cookie)
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        coVerify(exactly = 1) { completeLogin(cookie, "visitor", "12345") }
    }

    @Test
    fun failedVerificationCanBeRetriedWithCapturedCookie() = runTest(dispatcher) {
        coEvery { completeLogin(cookie, null, null) } returnsMany listOf(
            Result.failure(IllegalStateException("Verification failed")),
            Result.success(PlaybackAuthState(cookie = cookie)),
        )
        val viewModel = LoginViewModel(completeLogin)
        viewModel.onCookiesCaptured(cookie)
        advanceUntilIdle()
        assertTrue(viewModel.screenState.value is LoginScreenState.Error)

        viewModel.retryLogin()
        advanceUntilIdle()
        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        coVerify(exactly = 2) { completeLogin(cookie, null, null) }
    }

    @Test
    fun repeatedCookieDoesNotStartConcurrentVerification() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<PlaybackAuthState>>()
        coEvery { completeLogin(cookie, null, null) } coAnswers { pending.await() }
        val viewModel = LoginViewModel(completeLogin)

        viewModel.onCookiesCaptured(cookie)
        runCurrent()
        viewModel.onCookiesCaptured(cookie)
        runCurrent()
        coVerify(exactly = 1) { completeLogin(cookie, null, null) }

        pending.complete(Result.success(PlaybackAuthState(cookie = cookie)))
        advanceUntilIdle()
        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
    }

    @Test
    fun newCookieReplacesPendingVerification() = runTest(dispatcher) {
        val newCookie = "SAPISID=new-cookie"
        coEvery { completeLogin(cookie, null, null) } coAnswers { awaitCancellation() }
        coEvery { completeLogin(newCookie, null, null) } returns Result.success(PlaybackAuthState(cookie = newCookie))
        val viewModel = LoginViewModel(completeLogin)

        viewModel.onCookiesCaptured(cookie)
        runCurrent()
        viewModel.onCookiesCaptured(newCookie)
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is LoginScreenState.Success)
        coVerify(exactly = 1) { completeLogin(newCookie, null, null) }
    }
}
