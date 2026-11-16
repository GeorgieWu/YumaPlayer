/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState

/** Keeps the parser entry point source-compatible after the network package split. */
internal fun JsonElement.findMainAppWebDataSyncId(): String? {
    val context =
        ((this as? JsonObject)?.get("responseContext") as? JsonObject)
            ?.get("mainAppWebResponseContext") as? JsonObject
            ?: return null

    return sequenceOf("datasyncId", "dataSyncId")
        .mapNotNull { key ->
            ((context[key] as? JsonPrimitive)?.contentOrNull)
                ?.let { PlaybackAuthState(dataSyncId = it).normalized().dataSyncId }
        }.firstOrNull()
}
