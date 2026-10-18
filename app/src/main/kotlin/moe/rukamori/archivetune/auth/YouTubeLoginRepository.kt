/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.ContentCountryKey
import moe.rukamori.archivetune.constants.ContentLanguageKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.clearPlaybackAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.resolveYouTubeContentLocale
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeLoginRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun completeLogin(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    val normalizedCookie = cookie.trim()
                    check(hasYouTubeLoginCookie(normalizedCookie)) { "YouTube login cookie is missing" }

                    val authState =
                        PlaybackAuthState(
                            cookie = normalizedCookie,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                        ).normalized()
                    val preferences = context.dataStore.data.first()
                    val requestLocale = resolveYouTubeContentLocale(preferences[ContentCountryKey], preferences[ContentLanguageKey])
                    // Verify the captured cookie independently of the currently active account.
                    val accountInfo = YouTube.accountInfo(authState.copy(dataSyncId = null), requestLocale).getOrThrow()
                    persistLoginSession(authState, accountInfo)
                    context.dataStore.data.first().toPlaybackAuthState().also { YouTube.authState = it }
                }
            }

        suspend fun switchSavedAccount(account: SavedAccount): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    check(hasYouTubeLoginCookie(account.innerTubeCookie)) { "Saved account login cookie is missing" }

                    val authState =
                        PlaybackAuthState(
                            cookie = account.innerTubeCookie,
                            visitorData = account.visitorData,
                            dataSyncId = account.dataSyncId,
                        ).normalized()
                    context.dataStore.edit { preferences ->
                        preferences.clearPlaybackAuthSession()
                        preferences[InnerTubeCookieKey] = authState.cookie.orEmpty()
                        authState.visitorData?.let { preferences[VisitorDataKey] = it }
                        authState.dataSyncId?.let { preferences[DataSyncIdKey] = it }
                        preferences[AccountNameKey] = account.name
                        preferences[AccountEmailKey] = account.email
                        preferences[AccountChannelHandleKey] = account.channelHandle
                        preferences[YtmSyncKey] = account.ytmSync
                        preferences[SelectedYtmPlaylistsKey] = account.selectedYtmPlaylists
                    }

                    context.dataStore.data
                        .first()
                        .toPlaybackAuthState()
                        .also { YouTube.authState = it }
                }
            }

        private suspend fun persistLoginSession(authState: PlaybackAuthState, accountInfo: AccountInfo) {
            context.dataStore.edit { preferences ->
                preferences.clearPlaybackAuthSession()
                preferences[InnerTubeCookieKey] = authState.cookie.orEmpty()
                authState.visitorData?.let { preferences[VisitorDataKey] = it }
                authState.dataSyncId?.let { preferences[DataSyncIdKey] = it }
                preferences[AccountNameKey] = accountInfo.name
                preferences[AccountEmailKey] = accountInfo.email.orEmpty()
                preferences[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
            }
        }
    }

class CompleteYouTubeLoginUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<PlaybackAuthState> =
            repository.completeLogin(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
    }

class SwitchSavedYouTubeAccountUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(account: SavedAccount): Result<PlaybackAuthState> = repository.switchSavedAccount(account)
    }

private suspend inline fun <T> runCatchingPreservingCancellation(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        Result.failure(throwable)
    }
