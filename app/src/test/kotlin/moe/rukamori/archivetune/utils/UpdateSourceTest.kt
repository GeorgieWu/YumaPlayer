package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourceTest {
    private val fork = UpdateSource(isFork = true)

    @Test
    fun `fork uses one repository for all saved channels and ci-build history`() {
        assertEquals("GeorgieWu/YumaPlayer", fork.repository())
        assertEquals(fork.repository(), fork.repository(isCanary = true))
        assertEquals(
            "https://api.github.com/repos/GeorgieWu/YumaPlayer/releases?per_page=30",
            fork.releasesApiUrl(30),
        )
        assertEquals(fork.releasesApiUrl(30), fork.releasesApiUrl(30, isCanary = true))
        assertEquals("https://github.com/GeorgieWu/YumaPlayer/releases", fork.releasePageUrl())
        assertEquals(fork.releasePageUrl(), fork.releasePageUrl(isCanary = true))
        assertEquals(
            "https://api.github.com/repos/GeorgieWu/YumaPlayer/commits?sha=ci-build&per_page=30",
            fork.commitsApiUrl(30, ""),
        )
    }

    @Test
    fun `non fork builds retain upstream feeds and cache keys`() {
        val upstream = UpdateSource(isFork = false)
        assertEquals("MuwMx/YumaPlayer", upstream.repository())
        assertEquals("MuwMx/YumaCanary", upstream.repository(isCanary = true))
        assertEquals("https://github.com/MuwMx/YumaPlayer/releases/latest", upstream.releasePageUrl())
        assertEquals("https://github.com/MuwMx/YumaCanary/releases/latest", upstream.releasePageUrl(isCanary = true))
        assertEquals(
            "https://api.github.com/repos/MuwMx/YumaPlayer/commits?per_page=30",
            upstream.commitsApiUrl(30, ""),
        )
        for (key in listOf("github_releases_json", "github_releases_etag", "github_releases_last_checked_at", "github_releases_fingerprint")) {
            assertEquals(key, upstream.cacheKey(key))
            assertEquals("fork_$key", fork.cacheKey(key))
        }
    }

    @Test
    fun `fork updates compare installable codes across upstream version changes and reruns`() {
        val installed = "1.2.0-RC.1-fork.100004201"
        assertTrue(fork.isForkUpdateAvailable("1.2.0-RC.1-fork.100004202", installed))
        assertTrue(fork.isForkUpdateAvailable("1.1.0-fork.100004301", installed))
        assertFalse(fork.isForkUpdateAvailable("2.0.0-fork.100004101", installed))
        assertFalse(fork.isForkUpdateAvailable(installed, installed))
        assertTrue(fork.isSameForkVersion("fork-v$installed", installed))
        assertFalse(fork.isSameForkVersion("1.2.0-RC.1-fork.100004202", installed))
    }

    @Test
    fun `unrelated or invalid versions cannot become fork updates`() {
        val installed = "1.2.0-RC.1-fork.100004201"
        for (version in listOf("v9.0.0", "canary.2026.11.09", "1.2.0-fork.invalid", "1.2.0-fork.99999999", "1.2.0-fork.2147483648")) {
            assertNull(fork.forkVersionCode(version))
            assertFalse(fork.isForkUpdateAvailable(version, installed))
            assertFalse(fork.isForkUpdateAvailable(installed, version))
        }
    }

    @Test
    fun `published fork prereleases are ordered by installable code not upload date or semver`() {
        val older = release("2.0.0-fork.100004201").copy(publishedAt = "2026-11-10T00:00:00Z")
        val latest = release("1.2.0-RC.1-fork.100004301")
        assertEquals(listOf(latest, older), fork.forkReleases(listOf(older, latest)))
        assertTrue(latest.prerelease)
        assertEquals(listOf(latest.copy(prerelease = false)), fork.forkReleases(listOf(latest.copy(prerelease = false))))
    }

    @Test
    fun `missing artifacts and upstream releases are never selected`() {
        val valid = release("1.2.0-RC.1-fork.100004301")
        val missingAsset = release("1.2.0-fork.100004401").copy(downloadUrl = null)
        val upstreamAsset = valid.copy(downloadUrl = "https://github.com/MuwMx/YumaPlayer/releases/download/v1.2.0/app-gms-mobile-arm64-release.apk")
        val unrelatedTag = valid.copy(tagName = "v1.2.0")
        assertEquals(listOf(valid), fork.forkReleases(listOf(missingAsset, upstreamAsset, unrelatedTag, valid)))
        assertTrue(fork.forkReleases(listOf(missingAsset)).isEmpty())
    }

    private fun release(version: String): ReleaseInfo {
        val tag = "fork-v$version"
        return ReleaseInfo(
            tagName = tag,
            name = "YumaPlayer fork $version",
            body = "GMS · mobile · arm64-v8a",
            publishedAt = "2026-11-09T00:00:00Z",
            htmlUrl = "${fork.releasesUrl()}/tag/$tag",
            prerelease = true,
            downloadUrl = "${fork.releasesUrl()}/download/$tag/app-gms-mobile-arm64-release.apk",
        )
    }
}
