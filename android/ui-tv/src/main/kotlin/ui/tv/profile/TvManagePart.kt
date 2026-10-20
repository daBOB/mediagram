package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.ManagePart
import model.Profile
import model.dailyLimitChoiceFor
import ui.tv.TvTextRow
import ui.tv.player.TvSettingsHeading

/**
 * One household's part of Manage on television — the web's `byHousehold` as
 * rows the remote walks: the household's name while the owner manages more
 * than one, then its Grown-ups and its Kids ("Name · FSK N · daily limit"),
 * each row opening its choices, and adding where [part] offers it. The
 * headings are text the remote passes over, as the web's are no controls.
 * [first] is the part's first row, for the part drawn first.
 */
@Composable
internal fun TvManagePart(
    part: ManagePart,
    first: FocusRequester?,
    onGrownUp: (Profile) -> Unit,
    onKid: (Profile) -> Unit,
    onAdd: (kid: Boolean) -> Unit,
) {
    part.name?.let { TvSettingsHeading(it) }
    if (part.showsGrownUps) {
        TvSettingsHeading(GROWN_UPS)
        part.grownUps.forEachIndexed { index, grownUp ->
            key(grownUp.id) { TvTextRow(grownUp.name, onClick = { onGrownUp(grownUp) }, focusRequester = first.takeIf { index == 0 }) }
        }
        if (part.addsGrownUp) TvTextRow(ADD_A_GROWN_UP, onClick = { onAdd(false) }, focusRequester = first.takeIf { part.grownUps.isEmpty() })
    }
    if (!part.showsKids) return
    val kidsLead = !part.showsGrownUps
    TvSettingsHeading(KIDS_SECTION)
    part.kids.forEachIndexed { index, kid ->
        key(kid.id) {
            TvTextRow("${kid.name} · FSK ${kid.kidsLimit} · ${dailyLimitChoiceFor(kid.dailyLimit).label}", onClick = { onKid(kid) }, focusRequester = first.takeIf { kidsLead && index == 0 })
        }
    }
    if (part.addsKid) TvTextRow(ADD_A_KID, onClick = { onAdd(true) }, focusRequester = first.takeIf { kidsLead && part.kids.isEmpty() })
}
