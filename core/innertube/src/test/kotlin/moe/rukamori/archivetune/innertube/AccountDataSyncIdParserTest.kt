package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDataSyncIdParserTest {
    @Test
    fun readsYouTubeLowercaseResponseKey() {
        assertEquals("test-primary", response("\"datasyncId\":\"test-primary||\"").findMainAppWebDataSyncId())
    }

    @Test
    fun preservesCamelCaseCompatibilityAndSecondaryChannelContext() {
        assertEquals("test-channel||test-user", response("\"dataSyncId\":\"test-channel||test-user\"").findMainAppWebDataSyncId())
    }

    @Test
    fun canonicalKeyTakesPriorityOverLegacyAlias() {
        assertEquals("test-primary", response("\"datasyncId\":\"test-primary||\",\"dataSyncId\":\"legacy\"").findMainAppWebDataSyncId())
    }

    @Test
    fun unusableCanonicalValueDoesNotHideUsableAlias() {
        assertEquals("test-alias", response("\"datasyncId\":\"||\",\"dataSyncId\":\"test-alias%7C%7C\"").findMainAppWebDataSyncId())
    }

    @Test
    fun missingOrInvalidContextDoesNotInventAnId() {
        listOf("{}", "[]", "null", "{\"responseContext\":{\"mainAppWebResponseContext\":[]}}")
            .forEach { assertNull(Json.parseToJsonElement(it).findMainAppWebDataSyncId()) }
        assertNull(response("\"datasyncId\":null").findMainAppWebDataSyncId())
        assertNull(response("\"datasyncId\":\"null\"").findMainAppWebDataSyncId())
    }

    private fun response(fields: String) =
        Json.parseToJsonElement("""{"responseContext":{"mainAppWebResponseContext":{$fields}}}""")
}
