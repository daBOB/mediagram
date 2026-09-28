package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import playback.FilmPreloadRow
import player.preloadLabel
import player.preloadRowState
import ui.tv.TvTextRow

/**
 * What is preloading, queued, or already fully on this device — the
 * television twin of [ui.catalog.PreloadsScreen]. Android only, since the
 * web player has no film preload. Reads [rows] exactly as
 * [playback.FilmPreloader] orders them and [heldFilms] as the catalogue's
 * own held set, the same as the phone; neither section keeps a record of
 * its own.
 *
 * A film Android's own background time limit paused keeps the place it
 * held before the pause — under Preloading if it was the one actually
 * writing, under Queued otherwise — named "Paused — background limit"
 * with one Resume action ([onResume]) rather than Cancel.
 *
 * [restoreKey] names the row a title was opened from, so Back lands the
 * remote back on it rather than the page's own first row — see
 * [rememberQueueFocus].
 */
@Composable
internal fun TvPreloadsPage(
    rows: List<FilmPreloadRow>,
    heldFilms: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    onCancel: (setId: String) -> Unit,
    onRemove: (setId: String) -> Unit,
    onResume: (row: FilmPreloadRow.TimeLimitPaused) -> Unit,
    restoreKey: String? = null,
) {
    val running = rows.filterIsInstance<FilmPreloadRow.Running>()
    val waiting = rows.filterIsInstance<FilmPreloadRow.Waiting>()
    val pausedActive = rows.filterIsInstance<FilmPreloadRow.TimeLimitPaused>().filter { it.wasActive }
    val pausedQueued = rows.filterIsInstance<FilmPreloadRow.TimeLimitPaused>().filterNot { it.wasActive }
    val rowIds =
        remember(running, pausedActive, waiting, pausedQueued, heldFilms) {
            (running.map { it.setId } + pausedActive.map { it.setId } + waiting.map { it.setId } + pausedQueued.map { it.setId } + heldFilms.map { it.setId })
        }
    val focus = rememberQueueFocus(rowIds, restoreKey)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(text = "Preloads", style = TvTypeScale.title)
        if (rowIds.isEmpty()) TvQuietLine("Nothing is preloading right now.")
        if (running.isNotEmpty() || pausedActive.isNotEmpty()) {
            TvSectionHeading("Preloading")
            for (row in running) {
                TvPreloadingRow(row, onOpenTitle = { onOpenTitle(row.setId) }, onCancel = { onCancel(row.setId) }, focus = focus)
            }
            for (row in pausedActive) {
                TvTitleActionRow(
                    row.title, onOpen = { onOpenTitle(row.setId) }, actionLabel = "Resume", onAction = { onResume(row) },
                    caption = PAUSED_CAPTION, focus = focus, setId = row.setId,
                )
            }
        }
        if (waiting.isNotEmpty() || pausedQueued.isNotEmpty()) {
            TvSectionHeading("Queued")
            for (row in waiting) {
                TvTitleActionRow(row.title, onOpen = { onOpenTitle(row.setId) }, actionLabel = "Cancel", onAction = { onCancel(row.setId) }, focus = focus, setId = row.setId)
            }
            for (row in pausedQueued) {
                TvTitleActionRow(
                    row.title, onOpen = { onOpenTitle(row.setId) }, actionLabel = "Resume", onAction = { onResume(row) },
                    caption = PAUSED_CAPTION, focus = focus, setId = row.setId,
                )
            }
        }
        if (heldFilms.isNotEmpty()) {
            TvSectionHeading("On this device")
            for (set in heldFilms) {
                TvTitleActionRow(set.title, onOpen = { onOpenTitle(set.setId) }, actionLabel = "Remove", onAction = { onRemove(set.setId) }, focus = focus, setId = set.setId)
            }
        }
    }
}

private const val PAUSED_CAPTION = "Paused — background limit"

@Composable
private fun TvSectionHeading(text: String) {
    Text(text = text, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
}

@Composable
private fun TvPreloadingRow(row: FilmPreloadRow.Running, onOpenTitle: () -> Unit, onCancel: () -> Unit, focus: QueueFocus) {
    Column(modifier = Modifier.padding(bottom = Spacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            TvTextRow(text = row.title, onClick = onOpenTitle, modifier = Modifier.trackedBy(focus, row.setId), focusRequester = focus.requesterFor(row.setId))
            TvTextRow(text = "Cancel", onClick = onCancel)
        }
        val state = preloadRowState(row)
        TvQuietLine(preloadLabel(state), modifier = Modifier.padding(top = Spacing.extraSmall))
        TvPreloadDetailLines(state = state, serverLine = null, onOpenStorage = {})
    }
}

/** A queued, held, or time-limit-paused film's own row — a title that opens it, and one action beside it. */
@Composable
private fun TvTitleActionRow(
    title: String,
    onOpen: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    focus: QueueFocus,
    setId: String,
    caption: String? = null,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            TvTextRow(text = title, onClick = onOpen, modifier = Modifier.trackedBy(focus, setId), focusRequester = focus.requesterFor(setId))
            TvTextRow(text = actionLabel, onClick = onAction)
        }
        caption?.let { TvQuietLine(it, modifier = Modifier.padding(top = Spacing.extraSmall)) }
    }
}
