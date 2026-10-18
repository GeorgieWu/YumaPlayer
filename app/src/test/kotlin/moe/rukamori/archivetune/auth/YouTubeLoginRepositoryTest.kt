package moe.rukamori.archivetune.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.ContentCountryKey
import moe.rukamori.archivetune.constants.ContentLanguageKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.PoTokenKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.utils.AUTOMATIC_YOUTUBE_CONTENT_REGION
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.dataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class YouTubeLoginRepositoryTest {
    private val context = mockk<Context>()
    private val store = mockk<DataStore<Preferences>>()
    private val preferences = MutableStateFlow<Preferences>(
        preferencesOf(
            InnerTubeCookieKey to "SAPISID=old-cookie",
            AccountNameKey to "Previous account",
            DataSyncIdKey to "old-account-id",
            VisitorDataKey to "old-visitor",
            PoTokenKey to "old-token",
            ContentCountryKey to "TW",
            ContentLanguageKey to "zh",
        ),
    )

    @Before
    fun setUp() {
        mockkStatic("moe.rukamori.archivetune.utils.DataStoreKt")
        every { context.dataStore } returns store
        every { store.data } returns preferences
        coEvery { store.updateData(any()) } coAnswers {
            val transform = firstArg<suspend (Preferences) -> Preferences>()
            transform(preferences.value).also { preferences.value = it }
        }
        mockkObject(YouTube)
        every { YouTube.authState = any() } just Runs
        coEvery { YouTube.accountInfo(any(), any()) } returns Result.success(AccountInfo("New account", "", "", null))
        coEvery { YouTube.accountInfo() } returns Result.failure(IllegalStateException("Network unavailable"))
        coEvery { YouTube.accountDataSyncId() } returns Result.failure(IllegalStateException("Network unavailable"))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun cookieIsVerifiedWithSelectedCountryBeforeSaving() = runTest {
        val authState = YouTubeLoginRepository(context).completeLogin("SAPISID=new-cookie", null, null).getOrThrow()

        assertEquals("SAPISID=new-cookie", authState.cookie)
        assertEquals("SAPISID=new-cookie", preferences.value[InnerTubeCookieKey])
        assertNull(preferences.value[DataSyncIdKey])
        assertNull(preferences.value[VisitorDataKey])
        assertEquals("New account", preferences.value[AccountNameKey])
        assertNull(preferences.value[PoTokenKey])
        coVerify(exactly = 0) { YouTube.accountInfo() }
        coVerify(exactly = 1) {
            YouTube.accountInfo(match { it.cookie == "SAPISID=new-cookie" && it.dataSyncId == null }, YouTubeLocale("TW", "zh"))
        }
        coVerify(exactly = 0) { YouTube.accountDataSyncId() }
    }

    @Test
    fun availableWebViewContextIsSavedWithCookie() = runTest {
        val authState = YouTubeLoginRepository(context)
            .completeLogin("SAPISID=new-cookie", "visitor", "12345||").getOrThrow()

        assertEquals("visitor", authState.visitorData)
        assertEquals("12345", authState.dataSyncId)
        assertEquals("visitor", preferences.value[VisitorDataKey])
        assertEquals("12345", preferences.value[DataSyncIdKey])
        coVerify(exactly = 0) { YouTube.accountInfo() }
        coVerify(exactly = 1) { YouTube.accountInfo(match { it.dataSyncId == null }, any()) }
        coVerify(exactly = 0) { YouTube.accountDataSyncId() }
    }

    @Test
    fun savedAccountWithoutDataSyncIdCanBeUsedOffline() = runTest {
        val account = SavedAccount("saved", "Saved account", "", "", "SAPISID=saved-cookie", "", "", false, "")
        val authState = YouTubeLoginRepository(context).switchSavedAccount(account).getOrThrow()

        assertEquals("SAPISID=saved-cookie", authState.cookie)
        assertEquals("Saved account", preferences.value[AccountNameKey])
        assertNull(preferences.value[DataSyncIdKey])
        coVerify(exactly = 0) { YouTube.accountInfo() }
        coVerify(exactly = 0) { YouTube.accountInfo(any(), any()) }
        coVerify(exactly = 0) { YouTube.accountDataSyncId() }
    }

    @Test
    fun failedVerificationPreservesPreviousSession() = runTest {
        coEvery { YouTube.accountInfo(any(), any()) } returns Result.failure(IllegalStateException("Network unavailable"))

        val result = YouTubeLoginRepository(context).completeLogin("SAPISID=new-cookie", "visitor", null)

        assertTrue(result.isFailure)
        assertEquals("SAPISID=old-cookie", preferences.value[InnerTubeCookieKey])
        assertEquals("old-account-id", preferences.value[DataSyncIdKey])
        assertEquals("Previous account", preferences.value[AccountNameKey])
        assertEquals("old-token", preferences.value[PoTokenKey])
        verify(exactly = 0) { YouTube.authState = any() }
    }

    @Test
    fun explicitAutomaticRegionOmitsCountryDuringVerification() = runTest {
        preferences.value = preferencesOf(
            ContentCountryKey to AUTOMATIC_YOUTUBE_CONTENT_REGION,
            ContentLanguageKey to "zh",
        )
        YouTubeLoginRepository(context).completeLogin("SAPISID=new-cookie", null, null).getOrThrow()
        coVerify(exactly = 1) { YouTube.accountInfo(any(), YouTubeLocale(null, "zh")) }
    }

    @Test
    fun cancelledVerificationDoesNotSaveSessionOrBecomeLoginFailure() = runTest {
        coEvery { YouTube.accountInfo(any(), any()) } returns Result.failure(CancellationException("Cancelled"))

        try {
            YouTubeLoginRepository(context).completeLogin("SAPISID=new-cookie", null, null)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals("SAPISID=old-cookie", preferences.value[InnerTubeCookieKey])
            verify(exactly = 0) { YouTube.authState = any() }
        }
    }
}
