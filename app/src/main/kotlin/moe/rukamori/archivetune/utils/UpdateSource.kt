package moe.rukamori.archivetune.utils

internal class UpdateSource(val isFork: Boolean) {
    fun repository(isCanary: Boolean = false): String = when {
        isFork -> "GeorgieWu/YumaPlayer"
        isCanary -> "MuwMx/YumaCanary"
        else -> "MuwMx/YumaPlayer"
    }

    fun releasesUrl(isCanary: Boolean = false): String =
        "https://github.com/${repository(isCanary)}/releases"

    fun releasePageUrl(isCanary: Boolean = false): String =
        releasesUrl(isCanary) + if (isFork) "" else "/latest"

    fun releasesApiUrl(perPage: Int, isCanary: Boolean = false): String =
        "https://api.github.com/repos/${repository(isCanary)}/releases?per_page=$perPage"

    fun commitsApiUrl(count: Int, branch: String): String {
        val targetBranch = branch.ifBlank { if (isFork) "ci-build" else "" }
        val query = if (targetBranch.isBlank()) "per_page=$count" else "sha=$targetBranch&per_page=$count"
        return "https://api.github.com/repos/${repository()}/commits?$query"
    }

    fun cacheKey(upstreamKey: String): String = if (isFork) "fork_$upstreamKey" else upstreamKey

    fun forkVersionCode(version: String): Int? =
        ForkVersionCode.find(version.trim())?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 100_000_000..2_100_000_000 }

    fun isSameForkVersion(a: String, b: String): Boolean {
        val code = forkVersionCode(a) ?: return false
        return code == forkVersionCode(b)
    }

    fun isForkUpdateAvailable(latest: String, current: String): Boolean {
        val latestCode = forkVersionCode(latest) ?: return false
        val currentCode = forkVersionCode(current) ?: return false
        return latestCode > currentCode
    }

    fun forkReleases(releases: List<ReleaseInfo>): List<ReleaseInfo> =
        releases.mapNotNull { release ->
            val code = forkVersionCode(release.tagName) ?: return@mapNotNull null
            if (!release.tagName.startsWith("fork-v") ||
                release.downloadUrl?.startsWith("${releasesUrl()}/download/${release.tagName}/") != true
            ) {
                return@mapNotNull null
            }
            code to release
        }.sortedByDescending { it.first }.map { it.second }

    private companion object {
        val ForkVersionCode = Regex("""-fork\.(\d+)$""")
    }
}
