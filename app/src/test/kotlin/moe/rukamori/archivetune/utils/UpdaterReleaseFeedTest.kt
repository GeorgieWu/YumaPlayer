package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class UpdaterReleaseFeedTest {
    private val fork = UpdateSource(isFork = true)
    private val apk = "app-${BuildConfig.DISTRIBUTION}-${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-release.apk"

    @Test
    fun `workflow prerelease yields the exact APK URL including for a saved canary channel`() {
        val json = JSONArray().put(release(100004301)).put(release(100004201)).toString()
        for (isCanary in listOf(false, true)) {
            val releases = Updater.parseReleasesJson(json, isCanary, fork)
            assertEquals(2, releases.size)
            val latest = releases.first()
            assertEquals("1.2.0-RC.1-fork.100004301", Updater.getReleaseVersionName(latest))
            assertEquals(
                "https://github.com/GeorgieWu/YumaPlayer/releases/download/fork-v1.2.0-RC.1-fork.100004301/$apk",
                latest.downloadUrl,
            )
            assertTrue(latest.prerelease)
        }
    }

    @Test
    fun `drafts missing APKs and upstream cache records are excluded`() {
        val draft = release(100004401).put("draft", true)
        val noApk = release(100004402).put("assets", JSONArray())
        val wrongVariant = release(100004403, "app-foss-tv-x86-release.apk")
        val oldCache = release(100004404).put("assets", JSONArray()).put(
            "download_url",
            "https://github.com/MuwMx/YumaPlayer/releases/download/v1.2.0/$apk",
        )
        val json = JSONArray().put(draft).put(noApk).put(wrongVariant).put(oldCache).put(release(100004301)).toString()
        val selected = Updater.parseReleasesJson(json, updateSource = fork)
        assertEquals(listOf("fork-v1.2.0-RC.1-fork.100004301"), selected.map { it.tagName })
    }

    @Test
    fun `fork cache records retain their selected asset URL`() {
        val cached = release(100004301)
        val url = cached.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        cached.remove("assets")
        cached.put("download_url", url)
        val releases = Updater.parseReleasesJson(JSONArray().put(cached).toString(), updateSource = fork)
        assertEquals(url, releases.single().downloadUrl)
    }

    @Test
    fun `upstream release selection still excludes prereleases`() {
        assumeFalse(BuildConfig.FORK_UPDATES)
        val prerelease = ReleaseInfo("v2.0.0-RC.1", "RC", null, "2026-11-09", "", prerelease = true)
        val stable = ReleaseInfo("v1.2.0", "Stable", null, "2026-11-08", "")
        assertEquals(stable, Updater.findLatestRelease(listOf(prerelease, stable)))
        assertTrue(Updater.isUpdateAvailable("1.2.1", "1.2.0"))
    }

    private fun release(code: Int, assetName: String = apk): JSONObject {
        val version = "1.2.0-RC.1-fork.$code"
        val tag = "fork-v$version"
        return JSONObject().apply {
            put("tag_name", tag)
            put("name", "YumaPlayer fork $version")
            put("prerelease", true)
            put("draft", false)
            put("published_at", "2026-11-09T00:00:00Z")
            put("html_url", "${fork.releasesUrl()}/tag/$tag")
            put("assets", JSONArray().put(JSONObject().apply {
                put("name", assetName)
                put("browser_download_url", "${fork.releasesUrl()}/download/$tag/$assetName")
            }))
        }
    }
}
