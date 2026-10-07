package system

import model.heldOfBudget
import model.humanSize
import playback.CacheOccupancy

/** The Status row's own wording, in the words both Android surfaces show: "Searching" / "Connected to host" / "Not found" / "Needs local network permission". */
private fun connectionLine(state: LanCacheUiState): String =
    when (state.connection) {
        LanCacheConnection.NEEDS_PERMISSION -> "Needs local network permission"
        LanCacheConnection.NOT_FOUND -> "Not found"
        LanCacheConnection.SEARCHING -> "Searching"
        LanCacheConnection.CONNECTED -> "Connected to ${state.connectedHost ?: "server"}"
    }

/**
 * Settings › Storage's Home cache server ledger: the connection, then what
 * the server reports in `GET /v1/status` — what it holds against its own
 * budget, and how many chunks that is — each as its own row. Kept beside
 * [LanCacheUiState] so the phone and the television read the same sentences
 * rather than two copies.
 */
fun lanCacheRows(state: LanCacheUiState): List<Pair<String, String?>> =
    listOf(
        "Status" to connectionLine(state),
        "Holding" to state.heldBytes?.let { held -> heldAgainstServerBudget(held, state.budgetBytes) },
        "Chunks" to state.chunks?.toString(),
    )

/** The server's held amount against its own budget, as the device's cache reads its own; just the amount when the budget is unknown. */
private fun heldAgainstServerBudget(
    held: Long,
    budget: Long?,
): String = if (budget != null && budget > 0) heldOfBudget(held, budget) else humanSize(held)

/**
 * Settings index's Storage row: held against the budget, and — only when it
 * means something — that the home cache server is connected, with the held
 * dot [ui.common.settings.IndexStatus] draws for it.
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
