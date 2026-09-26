package system

/*
 * The System screen's Catalogue and This app blocks as label/value rows,
 * pure so the phone and the television set the same words in the same
 * order. A row whose value is null is left out by whoever draws it, the
 * rule [cacheRows] and [upstreamRows] already follow.
 */

/** The installed catalogue: where it came from, what it holds, how fresh it is, and the schema it was read with. */
fun catalogueRows(
    state: SystemUiState,
    now: Long,
): List<Pair<String, String?>> =
    listOf(
        // Not "this machine" for the other case, which is what the web
        // player says: nothing here is ever assembled on the device it is
        // read on. Every catalogue this app holds was pushed to a channel
        // and pulled back down — which is what the Refresh row two lines
        // below is reporting the age of.
        "Source" to
            when (state.origin) {
                "package" -> "published package"
                "channel" -> "the library's channel"
                else -> "nothing installed yet"
            },
        "Holds" to "${state.sets} playable sets, ${state.posters} posters",
        // Read against the wall clock the caller passes at the moment the
        // block is drawn rather than when the facts were taken: the state
        // is re-read on every visit to the screen, so the two are the same
        // moment, and a clock carried inside the state would be a second
        // thing to keep current.
        "Refresh" to refreshLine(state.publishedAt, state.lastRefresh, now),
        // schema is this build's own compiled constant, not a value read
        // back out of the installed catalog — see CatalogFacts' own doc.
        "Schema" to "v${state.schema}, expected by this build",
    )

/** This app itself: its version, its Telegram session, and how long it has been running. */
fun thisAppRows(state: SystemUiState): List<Pair<String, String?>> =
    listOf(
        "Version" to state.versionName,
        "Telegram" to telegramLine(state.connected),
        "Uptime" to uptimeLine(state.uptimeSeconds),
    )
