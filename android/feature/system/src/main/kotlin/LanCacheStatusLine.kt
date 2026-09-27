package system

import model.humanSize
import playback.CacheOccupancy

/** The connection half of [lanCacheStatusLine], without what is held — [lanCacheRows] draws that as its own row instead. */
private fun connectionLine(state: LanCacheUiState): String =
    when (state.connection) {
        LanCacheConnection.NEEDS_PERMISSION -> "Needs local network permission"
        LanCacheConnection.NOT_FOUND -> "Not found"
        LanCacheConnection.SEARCHING -> "Searching"
        LanCacheConnection.CONNECTED -> "Connected to ${state.connectedHost ?: "server"}"
    }

/**
 * The home cache server's Status row, in the words both Android surfaces
 * show: "Searching" / "Connected to host, holding X" / "Not found" /
 * "Needs local network permission". Kept beside [LanCacheUiState] so the
 * phone and the television read one sentence rather than two copies.
 */
fun lanCacheStatusLine(state: LanCacheUiState): String {
    val held = state.heldBytes?.let { ", holding ${humanSize(it)}" }.orEmpty()
    return connectionLine(state) + held
}

/**
 * Settings › Storage's Home cache server ledger: the connection, then what
 * it is holding as its own row — the approved mockup splits what
 * [lanCacheStatusLine] says in one sentence into two ledger rows instead.
 */
fun lanCacheRows(state: LanCacheUiState): List<Pair<String, String?>> =
    listOf(
        "Status" to connectionLine(state),
        "Holding" to state.heldBytes?.let(::humanSize),
    )

/**
 * Settings index's Storage row: held against the budget, and — only when it
 * means something — that the home cache server is connected, with the held
 * dot [ui.settings.IndexStatus] draws for it.
 */
fun storageStatus(
    occupancy: CacheOccupancy?,
    lan: LanCacheUiState?,
): Pair<String, Boolean> {
    val held = occupancy?.let { shortHeldOfBudget(it.heldBytes, it.budgetBytes) } ?: "Reading…"
    val connected = lan?.connection == LanCacheConnection.CONNECTED
    return (if (connected) "$held · home cache connected" else held) to connected
}

/** [model.heldOfBudget] without the repeated unit or the percent — the index row has one line, not a ledger's own room. */
private fun shortHeldOfBudget(
    held: Long,
    budget: Long,
): String {
    val budgetText = humanSize(budget)
    val unit = budgetText.substringAfterLast(' ')
    val heldText = humanSize(held).removeSuffix(" $unit")
    return "$heldText of $budgetText"
}
