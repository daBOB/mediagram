package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus
import ui.tv.TvTextRow
import ui.tv.setup.TvTextQuestion

private const val NAME_PROMPT = "Name for this profile"
private const val KIDS_LABEL = "Kids profile — only FSK 12 and under"
private const val ADD_LABEL = "Add"
private const val KIDS_ON = "On"
private const val KIDS_OFF = "Off"

/** The kids toggle row's own tag, for the androidTest set. */
internal const val TvProfilePickerKidsToggleTag = "tv-profile-picker-kids-toggle"

/** The Add flow's confirm row, for the androidTest set. */
internal const val TvProfilePickerAddConfirmTag = "tv-profile-picker-add-confirm"

private enum class AddStep { NAME, KIDS }

/**
 * "Add" on television, mirroring the phone's single name+kids dialog
 * (`ui.profile.ProfilePickerScreen.NameDialog`) across two screens instead
 * of one field beside another — the same split `TvApplicationScreen` makes
 * for its own two Telegram values, and for the same reason: a remote has
 * nowhere to land on a second field once the first already holds it.
 *
 * Back from [AddStep.KIDS] returns to [AddStep.NAME] with what was typed
 * still in hand, the one place this split invents that the phone's single
 * dialog never needed — [TvApplicationScreen]'s own api_hash step does the
 * same for the same reason. Back from [AddStep.NAME] instead leaves the add
 * flow entirely, back to the tile row: the phone's dialog is dismissed by
 * that same key wherever it is open, so TV's first step reaches for the
 * closest match a plain dialog dismissal has.
 */
@Composable
internal fun TvAddProfileFlow(
    onAdd: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf(AddStep.NAME) }
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }

    when (step) {
        AddStep.NAME -> {
            BackHandler(onBack = onCancel)
            TvTextQuestion(
                heading = NAME_PROMPT,
                explanation = null,
                label = "Name",
                value = name,
                onValue = { name = it },
                onSubmit = { if (name.isNotBlank()) step = AddStep.KIDS },
            )
        }

        AddStep.KIDS -> {
            BackHandler { step = AddStep.NAME }
            TvKidsChoiceScreen(
                name = name,
                kids = kids,
                onToggle = { kids = !kids },
                onConfirm = { onAdd(name, kids) },
            )
        }
    }
}

/**
 * [name] is echoed as the heading rather than repeating [NAME_PROMPT]: the
 * phone still shows the typed name in its own field while its kids toggle
 * is being decided, since both live in the one dialog — this step has no
 * field left to carry it, so the heading is what echoes it here instead.
 */
@Composable
private fun TvKidsChoiceScreen(
    name: String,
    kids: Boolean,
    onToggle: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Text(name, style = TvTypeScale.title)
        TvKidsToggleRow(
            kids = kids,
            onToggle = onToggle,
            focusRequester = focusRequester,
            modifier = Modifier.padding(top = Spacing.large),
        )
        TvTextRow(
            text = ADD_LABEL,
            onClick = onConfirm,
            modifier = Modifier.testTag(TvProfilePickerAddConfirmTag).padding(top = Spacing.large),
        )
    }
}

/**
 * The kids choice itself: a focusable row rather than tv-material's own
 * `Switch`, which is built to sit inside a row something else already owns
 * focus for — there is nothing else on this screen for that owner to be. A
 * `ClickableSurface` row carries its own focus and its own border from
 * [TvFocus], the same pieces [TvProfileTile] takes for a card, and states
 * its own value in words rather than a thumb position alone, legible from
 * the couch this app is always read from.
 */
@Composable
private fun TvKidsToggleRow(
    kids: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(TvProfilePickerKidsToggleTag)
                .focusRequester(focusRequester),
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(KIDS_LABEL, style = TvTypeScale.body, modifier = Modifier.weight(1f))
            Text(if (kids) KIDS_ON else KIDS_OFF, style = TvTypeScale.body)
        }
    }
}
