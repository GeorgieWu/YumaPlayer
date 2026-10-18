/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.auth.CompleteYouTubeLoginUseCase
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import timber.log.Timber
import javax.inject.Inject

sealed interface LoginScreenState {
    data object Loading : LoginScreenState

    data object Success : LoginScreenState

    data object Empty : LoginScreenState

    data object Error : LoginScreenState
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val completeYouTubeLogin: CompleteYouTubeLoginUseCase,
    ) : ViewModel() {
        private val _screenState = MutableStateFlow<LoginScreenState>(LoginScreenState.Empty)
        val screenState: StateFlow<LoginScreenState> = _screenState.asStateFlow()

        private var latestVisitorData: String? = null
        private var latestDataSyncId: String? = null
        private var loginJob: Job? = null
        private var activeCookie: String? = null

        fun onVisitorDataExtracted(visitorData: String?) {
            if (_screenState.value is LoginScreenState.Success) return
            val normalized = visitorData.normalizeAuthValue() ?: return
            latestVisitorData = normalized
        }

        fun onDataSyncIdExtracted(dataSyncId: String?) {
            val normalized = dataSyncId.normalizeDataSyncId() ?: return
            if (latestDataSyncId == normalized) return
            if (_screenState.value is LoginScreenState.Success) return

            latestDataSyncId = normalized
        }

        fun onCookiesCaptured(cookie: String?) {
            val normalizedCookie = cookie.normalizeAuthValue() ?: return
            startLogin(normalizedCookie)
        }

        fun retryLogin() {
            if (_screenState.value is LoginScreenState.Error) activeCookie?.let(::startLogin)
        }

        private fun startLogin(
            normalizedCookie: String,
        ) {
            if (_screenState.value is LoginScreenState.Success) return
            if (loginJob?.isActive == true && activeCookie == normalizedCookie) return

            activeCookie = normalizedCookie
            loginJob?.cancel()
            loginJob =
                viewModelScope.launch {
                    _screenState.value = LoginScreenState.Loading
                    completeYouTubeLogin(
                        cookie = normalizedCookie,
                        visitorData = latestVisitorData,
                        dataSyncId = latestDataSyncId,
                    ).onSuccess {
                        _screenState.value = LoginScreenState.Success
                    }.onFailure { throwable ->
                        Timber.e(throwable, "Failed to complete YouTube login")
                        _screenState.value = LoginScreenState.Error
                    }
                }
        }
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
