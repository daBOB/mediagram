package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import model.MediaSet
import playback.FilmPreloadRow
import player.preloadLabel
import player.preloadRowState

/**
 * What is preloading, queued, or already fully on this device — a Compose
 * port of nothing on the web: the web player has no film preload, so this
 * page exists only here. Reads [rows] exactly as [playback.FilmPreloader]
 * orders them (running film first, then queued in FIFO order, plus any
 * film Android's own background time limit paused) and [heldFilms] as the
 * catalogue's own held set already tracks it — neither section keeps a
 * record of its own. Empty sections are hidden outright, and an idle page
 * with all three empty says so in one line rather than three empty
 * headings.
 *
 * A film Android's own background time limit paused keeps the place it
 * held before the pause — under Preloading if it was the one actually
 * writing, under Queued otherwise — named "Paused — background limit"
 * with one Resume action ([onResume]) rather than Cancel, since the queue
 * itself was emptied for it and only a fresh enqueue puts it back.
 */
@Composable
internal fun PreloadsScreen(
    rows: List<FilmPreloadRow>,
    heldFilms: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    onCancel: (setId: String) -> Unit,
    onRemove: (setId: String) -> Unit,
    onResume: (row: FilmPreloadRow.TimeLimitPaused) -> Unit,
) {
    val running = rows.filterIsInstance<FilmPreloadRow.Running>()
    val waiting = rows.filterIsInstance<FilmPreloadRow.Waiting>()
    val pausedActive = rows.filterIsInstance<FilmPreloadRow.TimeLimitPaused>().filter { it.wasActive }
    val pausedQueued = rows.filterIsInstance<FilmPreloadRow.TimeLimitPaused>().filterNot { it.wasActive }

    if (running.isEmpty() && waiting.isEmpty() && pausedActive.isEmpty() && pausedQueued.isEmpty() && heldFilms.isEmpty()) {
        CenteredMessage("Nothing is preloading right now.")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.medium)) {
        if (running.isNotEmpty() || pausedActive.isNotEmpty()) {
            item(key = "preloading-heading") { SectionHeading("Preloading") }
            items(items = running, key = { "running-${it.setId}" }) { row ->
                PreloadingRow(row, onOpenTitle = { onOpenTitle(row.setId) }, onCancel = { onCancel(row.setId) })
                HorizontalDivider()
            }
            items(items = pausedActive, key = { "paused-${it.setId}" }) { row ->
                TimeLimitPausedRow(row.title, onOpen = { onOpenTitle(row.setId) }, onResume = { onResume(row) })
                HorizontalDivider()
            }
        }
        if (waiting.isNotEmpty() || pausedQueued.isNotEmpty()) {
            item(key = "queued-heading") { SectionHeading("Queued") }
            items(items = waiting, key = { "waiting-${it.setId}" }) { row ->
                TitleActionRow(row.title, onOpen = { onOpenTitle(row.setId) }, actionLabel = "Cancel", onAction = { onCancel(row.setId) })
                HorizontalDivider()
            }
            items(items = pausedQueued, key = { "paused-${it.setId}" }) { row ->
                TimeLimitPausedRow(row.title, onOpen = { onOpenTitle(row.setId) }, onResume = { onResume(row) })
                HorizontalDivider()
            }
        }
        if (heldFilms.isNotEmpty()) {
            item(key = "held-heading") { SectionHeading("On this device") }
            items(items = heldFilms, key = { "held-${it.setId}" }) { set ->
                TitleActionRow(set.title, onOpen = { onOpenTitle(set.setId) }, actionLabel = "Remove", onAction = { onRemove(set.setId) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.small))
}

@Composable
private fun PreloadingRow(row: FilmPreloadRow.Running, onOpenTitle: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenTitle).padding(vertical = Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(row.title, style = MaterialTheme.typography.bodyLarge)
                Text(preloadLabel(preloadRowState(row)), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        PreloadProgressBar(state = preloadRowState(row), onCancel = onCancel)
    }
}

/** A queued or held film's own row — a title that opens it, and one action beside it ("Cancel" or "Remove"). */
@Composable
private fun TitleActionRow(title: String, onOpen: () -> Unit, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** A film Android's own background time limit paused — its own row naming that, one Resume action. */
@Composable
private fun TimeLimitPausedRow(title: String, onOpen: () -> Unit, onResume: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("Paused — background limit", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onResume) { Text("Resume") }
    }
}
