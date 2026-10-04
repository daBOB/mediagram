package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import catalog.profile.FIRST_PROFILE
import catalog.profile.ProfileUiState
import catalog.profile.WHO_RUNS_THIS
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvTextRow

/** Manage profiles' row on the picker. */
internal const val TvManageProfilesTag = "tv-profile-picker-manage"

/** A "who runs this household" answer's tag. */
internal fun tvClaimTag(id: String) = "tv-profile-picker-claim-$id"

/**
 * Where the remote rests on the picker: what it left from — a tile, a claim,
 * Manage — comes back to it when a PIN is given up or Manage is done, as a
 * browser hands focus back to what opened a dialog. Somewhere no longer
 * there falls back to the picker's own first choice.
 */
internal sealed interface TvPickerSpot {
    data class Tile(val id: String) : TvPickerSpot

    data class Claim(val id: String) : TvPickerSpot

    data object Manage : TvPickerSpot

    data object FirstProfile : TvPickerSpot

    data object TryAgain : TvPickerSpot
}

/**
 * The spot that takes the remote: a failed load's Try again; else [landing]
 * while it is still drawn; else the first profile on a device with no
 * grown-up; else the first tile. Mirrors what [TvPickerBody] draws, so the
 * requester is always attached to something.
 */
internal fun focusSpot(
    state: ProfileUiState.Picking,
    landing: TvPickerSpot?,
): TvPickerSpot {
    val error = state.error != null
    if (error && state.profiles.isEmpty()) return TvPickerSpot.TryAgain
    val drawn =
        when (landing) {
            is TvPickerSpot.Tile -> state.profiles.any { it.id == landing.id }
            is TvPickerSpot.Claim -> !error && state.needsAdmin && state.grownUps.any { it.id == landing.id }
            TvPickerSpot.Manage -> state.grownUps.isNotEmpty()
            else -> false
        }
    return when {
        drawn && landing != null -> landing
        !error && state.needsFirstProfile -> TvPickerSpot.FirstProfile
        else -> TvPickerSpot.Tile(state.profiles.first().id)
    }
}

/** The first grown-up on a device with none; it runs the household. */
@Composable
internal fun TvFirstProfileRow(
    onStart: () -> Unit,
    focusRequester: FocusRequester?,
) {
    TvTextRow(
        text = FIRST_PROFILE,
        onClick = onStart,
        focusRequester = focusRequester,
        modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.medium),
    )
}

/** Asked above the tiles while nobody runs the household; only grown-ups are offered, each a PIN away. */
@Composable
internal fun TvAdminQuestion(
    grownUps: List<Profile>,
    onClaim: (String) -> Unit,
    focus: TvPickerSpot,
    focusRequester: FocusRequester,
) {
    Text(WHO_RUNS_THIS, style = TvTypeScale.body, modifier = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.medium))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.large), modifier = Modifier.padding(top = Spacing.small)) {
        grownUps.forEach { profile ->
            key(profile.id) {
                TvTextRow(
                    text = profile.name,
                    onClick = { onClaim(profile.id) },
                    focusRequester = focusRequester.takeIf { focus == TvPickerSpot.Claim(profile.id) },
                    modifier = Modifier.testTag(tvClaimTag(profile.id)),
                )
            }
        }
    }
}
