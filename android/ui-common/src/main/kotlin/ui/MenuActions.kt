package ui

/**
 * The five things the menu can do, in the order they are shown — the
 * phone's overflow menu and the television's menu page list the same five,
 * from this one description, so the two cannot drift apart.
 *
 * Refreshing the library and fetching its details were two items and are
 * one. They were always done in that order and only in that order: a
 * refresh brings sets the channel has gained, and those are exactly the
 * sets with no synopsis and no artwork yet, so fetching without refreshing
 * first fills in gaps while leaving new ones unlisted. Two items made a
 * viewer remember a sequence the app already knew.
 *
 * It is named "Update library" and not "Refresh library", though refreshing
 * is the half anyone would name. Refreshing is a few seconds of one round
 * trip; fetching is minutes of HTTP over hundreds of titles, and a word
 * promising the first while doing the second is a lie a viewer only catches
 * by waiting.
 *
 * The TMDB key sits directly under the update it configures, and is named
 * with an ellipsis because it opens a screen to fill in rather than doing
 * anything itself.
 *
 * Settings sits directly after System, ahead of the update it does not
 * configure: both are about the app and the account rather than the
 * library's contents, and grouping them keeps the update sequence —
 * update, key, start over — together and in the order described below.
 *
 * Updating is third and start over last, two items apart: updating is the
 * most-used of the five and starting over discards this device's Telegram
 * session, and the most frequent should not sit beside the most
 * destructive. The confirmation dialog is a backstop, not a reason to
 * invite the mis-tap.
 *
 * [updateDisabledReason] is `null` when the action is available and a
 * sentence when it is not — a run already in flight. [updateNote] is said
 * under the label while the item stays tappable: with no TMDB key the
 * refresh still works and only the artwork half is skipped, which is worth
 * doing and worth saying. An item that silently does less than its name is
 * worse than one that says what it will leave out.
 */
data class MenuActions(
    val onSystem: () -> Unit,
    val onSettings: () -> Unit,
    /**
     * Re-read the channel's newest index, then fill in what it has no room
     * for: the descriptions nobody wrote and the artwork no index carries.
     * In that order, because the second is about what the first brought in.
     */
    val onUpdate: () -> Unit,
    val onTmdbKey: () -> Unit,
    val onStartOver: () -> Unit,
    val updateDisabledReason: String? = null,
    val updateNote: String? = null,
)
