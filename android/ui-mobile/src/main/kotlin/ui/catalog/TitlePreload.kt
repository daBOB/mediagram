package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing
import playback.FilmPreloadState
import player.preloadAccessibilityHint
import player.preloadBarLabel
import player.preloadIsAffirmative
import player.preloadIsEnabled
import player.preloadLabel
import ui.settings.LinePill
import ui.settings.QuietPill

/**
 * What [TitleDetailScreen] needs to draw a film's Preload control — plain
 * data and callbacks, the same "no ViewModel inside the screen" shape
 * [TitleDetailScreen]'s other optional parameters already take (see
 * `onToggleEditorsChoice`). The wiring layer ([ui.LibraryFlowBranches])
 * collects [playback.FilmPreloading] and builds this; the screen itself
 * never touches Hilt or the engine.
 */
data class TitlePreloadUi(
    val state: FilmPreloadState,
    val serverLine: String?,
    val onToggle: () -> Unit,
    val onRemove: () -> Unit,
    val onOpenStorage: () -> Unit,
    /** What a [FilmPreloadState.Queued] film's own label adds beyond "Queued" — see [player.queuedAheadLabel]. */
    val queuedAheadLabel: String? = null,
    /** The live cache budget a [FilmPreloadState.NeedsSpace] film's own label names — see [player.TitlePreloadViewModel.needsSpaceBudget]. */
    val needsSpaceBudgetBytes: Long? = null,
)

/**
 * A film's own Preload control — the pill beside Play, the thin bar under
 * the button row while it runs, and the quiet line naming a paired home
 * server. Android-only by decision (2026-09-27): the web player has no
 * film preload of its own.
 *
 * Every piece here reads [FilmPreloadState] and nothing else — the engine
 * decides what state a film is in, [player.preloadLabel] and its neighbours
 * decide what that state says, and this file only draws it. No Hilt, no
 * engine call, so the same [ui.catalog.title.FilmPageTest]-style Robolectric
 * test that builds [TitleDetailScreen] with plain lambdas today keeps doing
 * so once one of them is this control's own tap.
 */
@Composable
internal fun PreloadPill(
    state: FilmPreloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    queuedAheadLabel: String? = null,
    needsSpaceBudgetBytes: Long? = null,
) {
    val label = preloadLabel(state, queuedAheadLabel, needsSpaceBudgetBytes)
    val hint = preloadAccessibilityHint(state)
    val enabled = preloadIsEnabled(state)
    val description = if (hint != null) "$label. $hint" else null
    if (preloadIsAffirmative(state)) {
        LinePill(text = label, onClick = onClick, enabled = enabled, contentDescription = description, modifier = modifier)
    } else {
        QuietPill(text = label, onClick = onClick, enabled = enabled, contentDescription = description, modifier = modifier)
    }
}

/** The ⋯ menu's own "Remove preload" row, offered only once a film is [FilmPreloadState.Done]. */
@Composable
internal fun PreloadRemoveMenuItem(onRemove: () -> Unit, onDismiss: () -> Unit) {
    DropdownMenuItem(text = { Text("Remove preload") }, onClick = { onDismiss(); onRemove() })
}

/**
 * Whether [state] is a moment worth drawing the full-width bar for —
 * [FilmPreloadState.Running] and every [FilmPreloadState.Paused] reason: a
 * pause does not clear or reset what the write has already held, so the
 * bar stays up rather than disappearing and reappearing with every pause
 * and resume, and only Running/Paused carry the held/total pair a bar
 * needs in the first place.
 */
internal fun showsPreloadBar(state: FilmPreloadState): Boolean = state is FilmPreloadState.Running || state is FilmPreloadState.Paused

/**
 * The thin full-width bar under the button row — [state] must be one
 * [showsPreloadBar] answers true for. Its own caption is always the
 * numeric "2.1 of 5.8 GB · 36%": a pause does not reset held or total
 * bytes, so the same figure that read while running keeps reading true
 * while paused; what changed is said once, by [PreloadPill]'s own label
 * above, not repeated here. Its own tap action follows [state] the same
 * way the pill's does — cancel for an ordinary run or pause, resume for a
 * background-limit one — via [preloadAccessibilityHint] rather than a
 * fixed "Tap to cancel." that would say the wrong thing for the latter.
 */
@Composable
internal fun PreloadProgressBar(state: FilmPreloadState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val (held, total) =
        when (state) {
            is FilmPreloadState.Running -> state.heldBytes to state.totalBytes
            is FilmPreloadState.Paused -> state.heldBytes to state.totalBytes
            else -> return
        }
    val fraction = if (total <= 0L) 0f else (held.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val label = preloadBarLabel(held, total)
    val hint = preloadAccessibilityHint(state)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onCancel)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    contentDescription = if (hint != null) "$label. $hint" else label
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** NeedsSpace's own second control, beside its own retry pill — opens Settings › Storage. */
@Composable
internal fun PreloadStorageLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LinePill(text = "Raise the cache budget", onClick = onClick, modifier = modifier)
}

/** The quiet "Home server: x of y GB" line — `null` [line] draws nothing, the caller's own "hidden on null" rule. */
@Composable
internal fun PreloadServerLine(line: String?, modifier: Modifier = Modifier) {
    if (line != null) Text(text = line, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}
