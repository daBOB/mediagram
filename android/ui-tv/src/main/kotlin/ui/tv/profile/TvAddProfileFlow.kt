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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import model.DAILY_LIMIT_CHOICES
import model.DailyLimitWords
import model.KIDS_LIMITS
import ui.tv.TvTextRow
import ui.tv.setup.TvTextQuestion

/** A daily-limit row's tag, for tests; the menu's minutes, or "none". */
internal fun tvDailyLimitTag(minutes: Int?) = "tv-daily-limit-${minutes ?: "none"}"

/** A limit row's tag, for tests. */
internal fun tvKidsLimitTag(age: Int) = "tv-kids-limit-$age"

private enum class AddStep { NAME, LIMIT, DAILY }

/**
 * Adding a profile on television — the web's name-and-button form across
 * screens of their own, since a remote has nowhere to land on a second field
 * once the first holds it. The name first; then, for a kid, its limit —
 * FSK 6 or FSK 12, the remote starting on 6, the web's own first choice; then its daily limit, starting on No limit. A
 * grown-up goes on after its name: its first PIN is asked next, twice, on
 * the pad. Back from the limit returns to the name with what was typed; Back
 * from the name leaves. A blank name is not taken, as the web's form sends
 * none.
 */
@Composable
internal fun TvAddProfileFlow(
    heading: String,
    askLimit: Boolean,
    onAdd: (name: String, age: Int?, dailyLimit: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf(AddStep.NAME) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf(KIDS_LIMITS.min()) }
    when (step) {
        AddStep.NAME -> {
            BackHandler(onBack = onCancel)
            TvTextQuestion(
                heading = heading,
                explanation = null,
                label = "Name",
                value = name,
                onValue = { name = it },
                onSubmit = {
                    if (name.isNotBlank()) {
                        if (askLimit) step = AddStep.LIMIT else onAdd(name.trim(), null, null)
                    }
                },
            )
        }
        AddStep.LIMIT -> {
            BackHandler { step = AddStep.NAME }
            TvKidsLimitChoice(name.trim(), onChoose = { chosen ->
                age = chosen
                step = AddStep.DAILY
            })
        }
        AddStep.DAILY -> {
            BackHandler { step = AddStep.LIMIT }
            TvDailyLimitStep(name.trim(), onChoose = { minutes -> onAdd(name.trim(), age, minutes) })
        }
    }
}

@Composable
private fun TvKidsLimitChoice(
    name: String,
    onChoose: (Int) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        Text(name, style = TvType.titleLarge.copy(fontSize = 21.6.tvSp, lineHeight = 1.1.em))
        KIDS_LIMITS.forEachIndexed { index, age ->
            TvTextRow(
                text = "FSK $age",
                onClick = { onChoose(age) },
                focusRequester = first.takeIf { index == 0 },
                modifier = Modifier.testTag(tvKidsLimitTag(age)).padding(top = Spacing.medium),
            )
        }
    }
}

/** The new kid's daily limit, "No limit" first and holding the remote, as the web's select starts. */
@Composable
private fun TvDailyLimitStep(
    name: String,
    onChoose: (Int?) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        Text("$name · ${DailyLimitWords.LABEL}", style = TvType.titleLarge.copy(fontSize = 21.6.tvSp, lineHeight = 1.1.em))
        DAILY_LIMIT_CHOICES.forEachIndexed { index, choice ->
            key(choice.minutes) {
                TvTextRow(
                    text = choice.label,
                    onClick = { onChoose(choice.minutes) },
                    focusRequester = first.takeIf { index == 0 },
                    modifier = Modifier.testTag(tvDailyLimitTag(choice.minutes)).padding(top = Spacing.small),
                )
            }
        }
    }
}
