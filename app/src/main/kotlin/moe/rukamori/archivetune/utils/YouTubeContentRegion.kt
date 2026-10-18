package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.CountryCodeToName
import moe.rukamori.archivetune.constants.LanguageCodeToName
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import java.util.Locale

const val AUTOMATIC_YOUTUBE_CONTENT_REGION = "auto"
const val DEFAULT_YOUTUBE_CONTENT_REGION = "US"

// Music account and browse requests reject CN with HTTP 400.
val YouTubeContentRegions = CountryCodeToName.filterKeys { it != "CN" }

fun resolveYouTubeContentRegion(value: String?): String =
    value?.takeIf { it == AUTOMATIC_YOUTUBE_CONTENT_REGION || it in YouTubeContentRegions }
        ?: DEFAULT_YOUTUBE_CONTENT_REGION

fun youTubeContentRegionCode(value: String?): String? =
    resolveYouTubeContentRegion(value).takeUnless { it == AUTOMATIC_YOUTUBE_CONTENT_REGION }

fun resolveYouTubeContentLocale(
    country: String? = null,
    language: String? = null,
    systemLocale: Locale = Locale.getDefault(),
): YouTubeLocale =
    YouTubeLocale(
        gl = youTubeContentRegionCode(country),
        hl = language?.takeIf { it in LanguageCodeToName }
            ?: systemLocale.language.takeIf { it in LanguageCodeToName }
            ?: systemLocale.toLanguageTag().replace("-Hant", "").takeIf { it in LanguageCodeToName }
            ?: "en",
    )
