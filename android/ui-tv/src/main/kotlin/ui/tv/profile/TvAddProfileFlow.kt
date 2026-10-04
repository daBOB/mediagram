package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.KIDS_LIMITS
import ui.tv.TvTextRow
import ui.tv.setup.TvTextQuestion

/** A limit row's tag, for tests. */
internal fun tvKidsLimitTag(age: Int) = "tv-kids-limit-$age"

private enum class AddStep { NAME, LIMIT }

/**
 * Adding a profile on television — the web's name-and-button form across
 * screens of their own, since a remote has nowhere to land on a second field
 * once the first holds it. The name first; then, for a kid, its limit —
 * FSK 6 or FSK 12, the remote starting on 6, the web's own first choice. A
 * grown-up goes on after its name: its first PIN is asked next, twice, on
 * the pad. Back from the limit returns to the name with what was typed; Back
 * from the name leaves. A blank name is not taken, as the web's form sends
 * none.
 */
@Composable
internal fun TvAddProfileFlow(
    heading: String,
    askLimit: Boolean,
    onAdd: (name: String, age: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf(AddStep.NAME) }
    var name by remember { mutableStateOf("") }
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
                        if (askLimit) step = AddStep.LIMIT else onAdd(name.trim(), null)
                    }
                },
            )
        }
        AddStep.LIMIT -> {
            BackHandler { step = AddStep.NAME }
            TvKidsLimitChoice(name.trim(), onChoose = { age -> onAdd(name.trim(), age) })
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
        Text(name, style = TvTypeScale.title)
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
