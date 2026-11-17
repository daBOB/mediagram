package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import model.DAILY_LIMIT_CHOICES
import model.DailyLimitWords
import model.HouseholdWords
import model.KIDS_LIMITS
import model.Profile
import ui.tv.TvTextRow
import ui.tv.setup.TvTextQuestion

/** A daily-limit row's tag, for tests; the menu's minutes, or "none". */
internal fun tvDailyLimitTag(minutes: Int?) = "tv-daily-limit-${minutes ?: "none"}"

/** A limit row's tag, for tests. */
internal fun tvKidsLimitTag(age: Int) = "tv-kids-limit-$age"

private enum class AddStep { PARENT, NAME, LIMIT, DAILY }

/**
 * Adding a profile on television — the web's name-and-button form across
 * screens of their own, since a remote has nowhere to land on a second field
 * once the first holds it. For a kid with more than one of [parents] to
 * belong to, Belongs to first, the remote on [parentDefault]; then the name;
 * then, for a kid, its limit — FSK 6 or FSK 12, the remote starting on 6, the
 * web's own first choice; then its daily limit, starting on No limit. A
 * grown-up goes on after its name: its first PIN is asked next, twice, on
 * the pad. Back returns a step with what was chosen and typed; Back from the
 * first leaves. A blank name is not taken, as the web's form sends none.
 *
 * What was chosen and typed is saved state, so a holder around the flow
 * keeps it while a PIN pad stands in its place; [notice] says why the last
 * add from it did not take, on whichever step shows.
 */
@Composable
internal fun TvAddProfileFlow(
    heading: String,
    askLimit: Boolean,
    onAdd: (name: String, age: Int?, dailyLimit: Int?, parentId: String?) -> Unit,
    onCancel: () -> Unit,
    parents: List<Profile> = emptyList(),
    parentDefault: String? = null,
    notice: String? = null,
) {
    val asksParent = parents.size > 1
    var step by rememberSaveable { mutableStateOf(if (asksParent) AddStep.PARENT else AddStep.NAME) }
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableIntStateOf(KIDS_LIMITS.min()) }
    var daily by rememberSaveable { mutableStateOf<Int?>(null) }
    var parent by rememberSaveable { mutableStateOf(parentDefault) }
    when (step) {
        AddStep.PARENT -> {
            BackHandler(onBack = onCancel)
            TvChoicesScreen(heading, HouseholdWords.BELONGS_TO, parents.map { it.id to it.name }, parent, notice) {
                parent = it
                step = AddStep.NAME
            }
        }
        AddStep.NAME -> {
            BackHandler { if (asksParent) step = AddStep.PARENT else onCancel() }
            TvTextQuestion(
                heading = heading,
                explanation = null,
                label = "Name",
                value = name,
                onValue = { name = it },
                error = notice,
                onSubmit = {
                    if (name.isNotBlank()) {
                        if (askLimit) step = AddStep.LIMIT else onAdd(name.trim(), null, null, parent)
                    }
                },
            )
        }
        AddStep.LIMIT -> {
            BackHandler { step = AddStep.NAME }
            TvKidsLimitChoice(name.trim(), age, notice, onChoose = { chosen ->
                age = chosen
                step = AddStep.DAILY
            })
        }
        AddStep.DAILY -> {
            BackHandler { step = AddStep.LIMIT }
            TvDailyLimitStep(name.trim(), daily, notice, onChoose = { minutes ->
                daily = minutes
                onAdd(name.trim(), age, minutes, parent)
            })
        }
    }
}

/** Why the last add did not take, under a step's heading. */
@Composable
private fun AddNotice(notice: String?) = notice?.let { Text(it, style = WhoNote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.small)) }

@Composable
private fun TvKidsLimitChoice(
    name: String,
    chosen: Int,
    notice: String?,
    onChoose: (Int) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        Text(name, style = TvType.titleLarge.copy(fontSize = 21.6.tvSp, lineHeight = 1.1.em))
        AddNotice(notice)
        KIDS_LIMITS.forEach { age ->
            TvTextRow(
                text = "FSK $age",
                onClick = { onChoose(age) },
                focusRequester = first.takeIf { age == chosen },
                modifier = Modifier.testTag(tvKidsLimitTag(age)).padding(top = Spacing.medium),
            )
        }
    }
}

/** The new kid's daily limit, "No limit" first and holding the remote, as the web's select starts — or the one chosen before. */
@Composable
private fun TvDailyLimitStep(
    name: String,
    chosen: Int?,
    notice: String?,
    onChoose: (Int?) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        Text("$name · ${DailyLimitWords.LABEL}", style = TvType.titleLarge.copy(fontSize = 21.6.tvSp, lineHeight = 1.1.em))
        AddNotice(notice)
        DAILY_LIMIT_CHOICES.forEach { choice ->
            key(choice.minutes) {
                TvTextRow(
                    text = choice.label,
                    onClick = { onChoose(choice.minutes) },
                    focusRequester = first.takeIf { choice.minutes == chosen },
                    modifier = Modifier.testTag(tvDailyLimitTag(choice.minutes)).padding(top = Spacing.small),
                )
            }
        }
    }
}
