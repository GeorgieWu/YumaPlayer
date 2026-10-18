package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.constants.SYSTEM_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class YouTubeContentRegionTest {
    @Test
    fun missingAndLegacyRegionsUseVisibleUsDefault() {
        for (value in listOf(null, SYSTEM_DEFAULT, "system", "CN", "")) {
            assertEquals("US", resolveYouTubeContentRegion(value))
            assertEquals("US", youTubeContentRegionCode(value))
        }
        assertFalse(YouTubeContentRegions.containsKey("CN"))
    }

    @Test
    fun explicitSupportedRegionsArePreserved() {
        for (value in listOf("TW", "JP", "HK", "US")) {
            assertEquals(value, resolveYouTubeContentRegion(value))
            assertEquals(value, youTubeContentRegionCode(value))
        }
    }

    @Test
    fun explicitAutomaticSelectionOmitsCountry() {
        assertEquals(AUTOMATIC_YOUTUBE_CONTENT_REGION, resolveYouTubeContentRegion(AUTOMATIC_YOUTUBE_CONTENT_REGION))
        assertNull(youTubeContentRegionCode(AUTOMATIC_YOUTUBE_CONTENT_REGION))
    }

    @Test
    fun selectedCountryAndLanguageAreIndependentOfDeviceLocale() {
        assertEquals(
            YouTubeLocale("TW", "ja"),
            resolveYouTubeContentLocale("TW", "ja", Locale.US),
        )
        for (language in listOf(null, SYSTEM_DEFAULT, "system", "")) {
            assertEquals(
                YouTubeLocale(null, "zh"),
                resolveYouTubeContentLocale(AUTOMATIC_YOUTUBE_CONTENT_REGION, language, Locale.CHINA),
            )
        }
    }

    @Test
    fun defaultLocaleUsesUsWithSupportedSystemLanguageOrEnglish() {
        assertEquals(YouTubeLocale("US", "zh"), resolveYouTubeContentLocale(systemLocale = Locale.CHINA))
        assertEquals(YouTubeLocale("US", "en"), resolveYouTubeContentLocale(systemLocale = Locale.ROOT))
    }
}
