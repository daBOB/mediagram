package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.profile.PIN_LENGTH
import catalog.profile.PinPrompt
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus
import ui.tv.TvTextRow

private const val DELETE = "Delete"
private val Keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(null, "0", DELETE))

// Four rows of these, with the heading, an error line, the dots and Cancel,
// fit a 540dp-tall television inside its overscan; 72dp-tall keys did not.
// Wide enough for "Delete" in words, so every column lines up.
private val KeyHeight = 56.dp
private val KeyWidth = 80.dp

/** A key's tag — `tv-pin-key-0` … `tv-pin-key-9`, `tv-pin-key-Delete` — for tests. */
internal fun tvPinKeyTag(key: String) = "tv-pin-key-$key"

/** The dots that say how many digits are in. */
internal const val TvPinDotsTag = "tv-pin-dots"

/**
 * The PIN question as a whole screen, for the picker and Manage alike —
 * composed in place of what asked, as every TV flow is, so nothing behind it
 * competes for the remote. Its Back cancels, and is registered after the
 * picker's own ("Stay as I am"), so it is the one that runs while this is up.
 */
@Composable
internal fun TvPinPrompt(
    prompt: PinPrompt,
    onPin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(prompt.heading, style = TvTypeScale.title)
        prompt.error?.let {
            Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small))
        }
        TvPinPad(enabled = !prompt.busy, onPin = onPin, modifier = Modifier.padding(top = Spacing.medium))
        TvTextRow(text = "Cancel", onClick = onCancel, modifier = Modifier.padding(top = Spacing.medium))
    }
}

/**
 * Four digits from a remote that may have nothing but a D-pad: a telephone
 * grid the remote walks in both directions, Centre typing the key it rests
 * on and "Delete" taking the last digit back. A remote with digit keys types
 * with those too, wherever it rests on the pad. The remote starts on "1".
 * The digits go the moment there are four and the pad empties, so a new
 * PIN's second entry and a retry both start from nothing. Dots, never
 * digits, show how far the entry is — the whole room can see a television.
 *
 * While not [enabled] (a PIN is being checked) the keys still hold the
 * remote, as `TvTextRow` does, but type nothing.
 */
@Composable
internal fun TvPinPad(
    enabled: Boolean,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    fun type(key: String) {
        if (!enabled) return
        val next = if (key == DELETE) digits.dropLast(1) else digits + key
        digits = if (next.length == PIN_LENGTH) "" else next
        if (next.length == PIN_LENGTH) onPin(next)
    }

    Column(
        modifier =
            modifier.onKeyEvent { event ->
                val typed = typedBy(event.key)
                if (typed != null && event.type == KeyEventType.KeyDown) type(typed)
                typed != null
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            text = "●".repeat(digits.length) + "○".repeat(PIN_LENGTH - digits.length),
            style = TvTypeScale.title,
            modifier =
                Modifier
                    .testTag(TvPinDotsTag)
                    .semantics { contentDescription = "${digits.length} of $PIN_LENGTH digits" },
        )
        Keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                row.forEach { key ->
                    if (key == null) Spacer(Modifier.size(KeyWidth, KeyHeight)) else TvPinKey(key, first.takeIf { key == "1" }) { type(key) }
                }
            }
        }
    }
}

/** What a remote's own key types: a digit, from the number row or a keypad, or a delete; null for every other key. */
private fun typedBy(key: Key): String? =
    when (key) {
        Key.Backspace, Key.Delete -> DELETE
        else -> DigitKeys.indexOf(key).takeIf { it >= 0 }?.let { (it % 10).toString() }
    }

private val DigitKeys =
    listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine) +
        listOf(Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9)

@Composable
private fun TvPinKey(
    label: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    // Never omitted: a requester that appeared only on the first key would
    // change the chain's shape, and Compose would reset whatever it focused.
    val own = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(KeyWidth, KeyHeight).testTag(tvPinKeyTag(label)).focusRequester(focusRequester ?: own),
        shape = TvFocus.surfaceShape(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, style = if (label == DELETE) TvTypeScale.body else TvTypeScale.title)
        }
    }
}
