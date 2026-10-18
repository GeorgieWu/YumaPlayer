package moe.rukamori.archivetune.innertube

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import moe.rukamori.archivetune.innertube.models.Context
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeClientLocaleTest {
    @Test
    fun automaticRegionKeepsLanguage() {
        val context = YouTubeClient.WEB_REMIX.toContext(YouTubeLocale(null, "zh"), null, null)
        assertNull(context.client.gl)
        assertEquals("zh", context.client.hl)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun omittedRegionIsAbsentFromRequestJson() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val context = YouTubeClient.WEB_REMIX.toContext(YouTubeLocale(null, "zh"), null, null)
        val client = json.encodeToJsonElement(Context.serializer(), context).jsonObject.getValue("client").jsonObject
        assertFalse(client.containsKey("gl"))
    }

    @Test
    fun supportedMusicRegionsArePreserved() {
        for (country in listOf("US", "TW", "HK", "JP")) {
            val context = YouTubeClient.WEB_REMIX.toContext(YouTubeLocale(country, "zh-CN"), null, null)
            assertEquals(country, context.client.gl)
            assertEquals("zh-CN", context.client.hl)
        }
    }

    @Test
    fun generalYouTubeClientKeepsItsRegion() {
        val context = YouTubeClient.WEB.toContext(YouTubeLocale("CN", "zh"), null, null)
        assertEquals("CN", context.client.gl)
    }
}
