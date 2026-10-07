package system

/** What the LAN cache block of Settings has to say about the current connection, in one word each. */
enum class LanCacheConnection { SEARCHING, CONNECTED, NOT_FOUND, NEEDS_PERMISSION }

/**
 * The LAN cache block's own facts, read straight off
 * [playback.LanCacheSettings], [data.settings.LanCacheTokenSettings] and
 * [playback.LanServerLocator]. Left unformatted, the same split
 * [system.SystemUiState] keeps from `ui.system.SystemRows` — this module
 * holds the facts, `ui-mobile`'s `LanCacheBlock` turns them into sentences.
 */
data class LanCacheUiState(
    val enabled: Boolean,
    val hasToken: Boolean,
    val manualAddress: String,
    val connection: LanCacheConnection,
    /** The connected server's host, or `null` outside [LanCacheConnection.CONNECTED]. */
    val connectedHost: String?,
    /** What the connected server reports holding, or `null` when nothing is connected or it could not be read. */
    val heldBytes: Long?,
    /** The last write this device attempted was rejected — the token typed in does not match the server's. */
    val tokenRejected: Boolean,
    /** A sentence to show under the address field when the last save was refused, or `null` between attempts. */
    val addressError: String? = null,
    /** A sentence to show under the token field when the last save was refused, or `null` between attempts. */
    val tokenError: String? = null,
    /** The connected server's own budget, from the same `GET /v1/status` as [heldBytes], or `null` when that could not be read. */
    val budgetBytes: Long? = null,
    /** How many chunks the connected server holds, from the same `GET /v1/status`, or `null` when that could not be read. */
    val chunks: Long? = null,
)
