package moe.rukamori.archivetune.utils

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.data.repository.UpdateRepositoryImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateRepositoryImplTest {
    private val version = "1.2.0-RC.1-fork.100004301"
    private val downloadUrl = "https://github.com/GeorgieWu/YumaPlayer/releases/download/fork-v$version/app-gms-mobile-arm64-release.apk"
    private val release = ReleaseInfo("fork-v$version", version, null, "2026-11-09", "", true, downloadUrl = downloadUrl)

    @Before
    fun setUp() {
        mockkObject(Updater)
        every { Updater.getReleaseVersionName(release) } returns version
        every { Updater.getCanaryReleaseVersionName(release) } returns version
        every { Updater.isUpdateAvailable(version, any()) } returns true
        every { Updater.getLatestDownloadUrl() } returns downloadUrl
        every { Updater.getLatestCanaryDownloadUrl() } returns downloadUrl
    }

    @After
    fun tearDown() {
        unmockkObject(Updater)
    }

    @Test
    fun `forced stable checks select a fresh release before exposing its download URL`() = runTest {
        coEvery { Updater.getLatestReleaseInfo(forceRefresh = true) } returns Result.success(release)
        val update = UpdateRepositoryImpl().forceCheckForUpdates(UpdateChannel.STABLE).first()
        assertEquals(version, update?.versionName)
        assertEquals(downloadUrl, update?.updateUrl)
        coVerify(exactly = 1) { Updater.getLatestReleaseInfo(forceRefresh = true) }
    }

    @Test
    fun `saved canary channels also refresh the selected release`() = runTest {
        coEvery { Updater.getLatestCanaryReleaseInfo(forceRefresh = true) } returns Result.success(release)
        val update = UpdateRepositoryImpl().forceCheckForUpdates(UpdateChannel.DAILY_NIGHTLY).first()
        assertEquals(downloadUrl, update?.updateUrl)
        coVerify(exactly = 1) { Updater.getLatestCanaryReleaseInfo(forceRefresh = true) }
    }
}
