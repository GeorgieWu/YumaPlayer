package moe.rukamori.archivetune.utils.potoken

/**
 * Holds the two PoToken variants produced by a single BotGuard minting cycle.
 *
 * - [playerToken]: bound to a specific video — sent in the player request's
 *   `serviceIntegrityDimensions.poToken` and the stream URL's `pot` parameter.
 * - [sessionToken]: bound to the visitor/dataSync session, not to a video stream.
 */
data class PoTokenResult(
    val playerToken: String,
    val sessionToken: String,
)
