package system

import model.humanSize

/**
 * The home cache server's Status row, in the words both Android surfaces
 * show: "Searching" / "Connected to host, holding X" / "Not found" /
 * "Needs local network permission". Kept beside [LanCacheUiState] so the
 * phone and the television read one sentence rather than two copies.
 */
fun lanCacheStatusLine(state: LanCacheUiState): String =
    when (state.connection) {
        LanCacheConnection.NEEDS_PERMISSION -> "Needs local network permission"
        LanCacheConnection.NOT_FOUND -> "Not found"
        LanCacheConnection.SEARCHING -> "Searching"
        LanCacheConnection.CONNECTED -> {
            val host = state.connectedHost ?: "server"
            val held = state.heldBytes?.let { ", holding ${humanSize(it)}" }.orEmpty()
            "Connected to $host$held"
        }
    }
