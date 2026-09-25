package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.initialsOf
import catalog.profile.ProfileUiState
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvFocus
import ui.tv.setup.TvLoadingIndicator
import ui.tv.setup.TvTextQuestion

private const val HEADING = "Who's watching?"
private const val NOTE =
    "Profiles keep your places and lists apart. They are not a login — " +
        "anyone who can reach this app can pick any of them."
private const val NEW_PROFILE = "New profile"
private const val NAME_PROMPT = "Name for this profile"
private const val KIDS_LABEL = "Kids profile — only FSK 12 and under"
private const val TRY_AGAIN = "Try again"
private const val STAY = "Stay as I am"
private const val ADD_LABEL = "Add"
private const val KIDS_ON = "On"
private const val KIDS_OFF = "Off"

private val TileWidth = 180.dp

// Fixed, not derived from content: a kids tile carries one more line ("KIDS")
// than a plain one, and a row of `Card`s sized only by their own content
// grows that one tile taller than its neighbours — a ragged row a fixed
// height (both tiles centring their content inside it) closes off for good.
private val TileHeight = 220.dp
private val AvatarSize = 88.dp

// The web reference (`style.css` `.who-card { max-width: 40rem; text-align:
// center }`) keeps the whole picker — heading, tiles and note alike — from
// running edge to edge and centres it; this is that same intent carried into
// dp for the note specifically, not a unit-exact rem-to-dp conversion.
private val NoteMaxWidth = 640.dp

/** The first tile's own tag — the one always focused when the picker appears. */
internal const val TvProfilePickerFirstTileTag = "tv-profile-picker-first-tile"

/** The Add tile's own tag, used whenever it is not also the first tile. */
internal const val TvProfilePickerAddTileTag = "tv-profile-picker-add-tile"

/** The kids toggle row's own tag, for the androidTest set. */
internal const val TvProfilePickerKidsToggleTag = "tv-profile-picker-kids-toggle"

/** The Add flow's confirm row, for the androidTest set. */
internal const val TvProfilePickerAddConfirmTag = "tv-profile-picker-add-confirm"

/**
 * The television counterpart to `ui.profile.ProfilePickerScreen`: the same
 * [ProfileUiState] switch, the same "Who's watching?" wording and the same
 * add-a-profile fields — drawn as tiles in a single row a D-pad moves
 * across instead of a phone grid a finger taps. Renders nothing for
 * [ProfileUiState.Chosen], exactly as the phone's own picker does — the
 * caller only shows this while there is something left to decide.
 */
@Composable
fun TvProfilePicker(
    state: ProfileUiState,
    onChoose: (String) -> Unit,
    onAdd: (String, Boolean) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ProfileUiState.Loading -> TvLoadingIndicator()
        is ProfileUiState.Picking -> TvPickerBody(state, onChoose, onAdd, onStay, onRetry)
        is ProfileUiState.Chosen -> Unit
    }
}

@Composable
private fun TvPickerBody(
    state: ProfileUiState.Picking,
    onChoose: (String) -> Unit,
    onAdd: (String, Boolean) -> Unit,
    onStay: () -> Unit,
    onRetry: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    // Composing the add flow in place of the tile row, not over it: the two
    // never need to be visible together, and a `naming` overlay would still
    // have to answer what the tiles behind it do with the remote while it
    // is up. Cancelling out of it below re-enters this branch, which is
    // also what re-seeds first-tile focus for free.
    if (adding) {
        TvAddProfileFlow(
            onAdd = { name, kids ->
                adding = false
                onAdd(name, kids)
            },
            onCancel = { adding = false },
        )
        return
    }

    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(HEADING, style = TvTypeScale.title)
        val error = state.error
        if (error != null) {
            Text(
                text = error,
                style = TvTypeScale.body,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.small),
            )
            TvTextRow(text = TRY_AGAIN, onClick = onRetry, modifier = Modifier.padding(top = Spacing.small))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.large),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        ) {
            state.profiles.forEachIndexed { index, profile ->
                TvProfileTile(
                    profile = profile,
                    onClick = { onChoose(profile.id) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                    tag = if (index == 0) TvProfilePickerFirstTileTag else "tv-profile-tile-${profile.id}",
                )
            }
            TvAddTile(
                onClick = { adding = true },
                focusRequester = if (state.profiles.isEmpty()) firstFocusRequester else null,
                tag = if (state.profiles.isEmpty()) TvProfilePickerFirstTileTag else TvProfilePickerAddTileTag,
            )
        }
        Text(
            NOTE,
            style = TvTypeScale.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = NoteMaxWidth).padding(top = Spacing.large),
        )
        if (state.canStay) {
            TvTextRow(text = STAY, onClick = onStay, modifier = Modifier.padding(top = Spacing.small))
        }
    }
}

@Composable
private fun TvProfileTile(
    profile: Profile,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(TileWidth)
                .height(TileHeight)
                .testTag(tag)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.medium),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The shared, two-letter `initialsOf` poster art already falls back
            // to — not the phone dialog's own single-letter `initialOf` — so a
            // second surface never reimplements what one function already
            // covers. The two only ever visibly differ on a two-word name.
            TvProfileAvatar(letters = initialsOf(profile.name))
            Text(profile.name, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
            if (profile.kids) {
                Text(
                    // The phone's own tile label, unabridged — see
                    // `ui.profile.ProfilePickerScreen.ProfileTile`.
                    "KIDS",
                    style = TvTypeScale.body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.extraSmall),
                )
            }
        }
    }
}

@Composable
private fun TvAddTile(
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tag: String,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(TileWidth)
                .height(TileHeight)
                .testTag(tag)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.medium),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A literal glyph, not a title to derive letters from — `initialsOf`
            // drops anything that is not a letter or digit, which would turn
            // this into "?" instead of the "+" the phone itself draws.
            TvProfileAvatar(letters = "+")
            Text(NEW_PROFILE, style = TvTypeScale.body, modifier = Modifier.padding(top = Spacing.small))
        }
    }
}

@Composable
private fun TvProfileAvatar(letters: String) {
    Box(
        modifier =
            Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(letters, style = TvTypeScale.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A plain clickable text row, focus read the same way every other TV list row reads it. */
@Composable
private fun TvTextRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = text,
        style = TvFocus.textStyle(TvTypeScale.body, focused),
        modifier =
            modifier
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    )
}

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
private fun TvAddProfileFlow(
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
