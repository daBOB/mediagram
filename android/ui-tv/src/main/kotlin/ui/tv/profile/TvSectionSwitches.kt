package ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import catalog.profile.DONE
import designsystem.Spacing
import designsystem.TvType
import model.Profile
import model.SECTIONS
import model.SectionAccessWords
import model.hidesWords
import model.turning
import ui.tv.catalog.TvSpreadPill
import ui.tv.rememberStableRequester
import ui.tv.setup.TvDialog

/** The line a row's choices offer the Sections with: what is hidden, as the Everyone line says it. */
internal fun sectionsLine(profile: Profile): String = listOfNotNull(SectionAccessWords.TITLE, profile.hidesWords).joinToString(" · ")

/**
 * Which sections [profile] is shown — the web's Sections switches as one
 * dialog, as a kid's daily limit is: a switch per section in nav order, on
 * while it is shown. A shown section is a quiet line pill; a hidden one is
 * solid, its name struck through, the look the user chose. A press sends
 * the whole hidden list, keys this app does not know kept, and the dialog
 * stays: the switches are keyed and their chains never change, so the
 * answer redraws them in place and the remote stays on the one it pressed.
 * Until the panel's next answer — [answers] moves on every one, a refusal
 * heard before included, and a refusal is said as the body ([notice]) — a
 * press does nothing, so a second never sends a list the first has not
 * settled; the switches stay enabled, as a disabled one would drop the
 * remote. Done or Back closes it.
 */
@Composable
internal fun TvSectionSwitches(
    profile: Profile,
    notice: String?,
    answers: Int,
    onSet: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    var held by remember(answers) { mutableStateOf(false) }
    TvDialog(
        title = "${SectionAccessWords.TITLE} · ${profile.name}",
        body = notice.orEmpty(),
        onDismissRequest = onDismiss,
        initialFocus = first,
        content = {
            Column(
                Modifier.padding(top = Spacing.medium).semantics { contentDescription = SectionAccessWords.forName(profile.name) },
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                SECTIONS.forEachIndexed { index, section ->
                    key(section.key) {
                        val shown = section.key !in profile.hiddenSections
                        TvSpreadPill(
                            text = section.label,
                            onClick = {
                                if (!held) {
                                    held = true
                                    onSet(profile.hiddenSections.turning(section.key))
                                }
                            },
                            modifier =
                                Modifier.focusRequester(rememberStableRequester(first.takeIf { index == 0 })).semantics {
                                    role = Role.Switch
                                    toggleableState = ToggleableState(shown)
                                },
                            solid = !shown,
                            struck = !shown,
                        )
                    }
                }
            }
        },
    ) { Button(onClick = onDismiss) { Text(DONE, style = TvType.labelLarge) } }
}
