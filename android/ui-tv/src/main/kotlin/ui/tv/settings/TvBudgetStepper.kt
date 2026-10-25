package ui.tv.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvType
import model.heldOfBudget
import model.humanSize
import ui.tv.TvFocus

/**
 * What the cache holds against its allowance: the sentence and, under it, a
 * bar in the theme's own colours — the accent for what is held, the
 * [LocalCatalogueTones] rule for the rest. Reading only; focus passes
 * through the block's heading above it.
 */
@Composable
internal fun TvBudgetGauge(
    held: Long,
    budget: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(text = heldOfBudget(held, budget), style = TvType.bodyMedium)
        TvFillBar(fraction = if (budget > 0) held.toFloat() / budget else 0f, height = 10.dp)
    }
}

/** A track with a filled share of it; [fraction] is clamped, so an over-full cache never overdraws. */
@Composable
internal fun TvFillBar(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).background(LocalCatalogueTones.current.ruleSoft, shape)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}

/**
 * The allowance as one control: Left and Right step to the neighbouring size
 * in [choices] and apply it at once, as choosing a row used to. One stop, so
 * Up and Down move on as everywhere else; a key that has nowhere to step is
 * left unconsumed, so Left at the smallest size still leaves to the index
 * pane. Focus alone changes nothing — only a key press does.
 *
 * [budget] need not sit on the ladder (a cap can shrink under a stored
 * value): the neighbours are the nearest sizes either side of it.
 */
@Composable
internal fun TvBudgetStepper(
    budget: Long,
    choices: List<Long>,
    onChoose: (Long) -> Unit,
) {
    val previous = choices.lastOrNull { it < budget }
    val next = choices.firstOrNull { it > budget }
    var focused by remember { mutableStateOf(false) }
    val border = TvFocus.fieldBorder(focused)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(BudgetStepperTag)
                .semantics(mergeDescendants = true) { contentDescription = "Budget ${humanSize(budget)}" }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val target =
                        when (event.key.nativeKeyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> previous
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> next
                            else -> null
                        } ?: return@onKeyEvent false
                    onChoose(target)
                    true
                }.focusable()
                .border(border.border, border.shape)
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Budget", style = TvType.bodyMedium, color = LocalCatalogueTones.current.quiet, modifier = Modifier.weight(1f))
        Arrow("‹", enabled = previous != null)
        Text(text = humanSize(budget), style = TvType.titleSmall)
        Arrow("›", enabled = next != null)
    }
}

/** An end of the ladder dims its arrow rather than hiding it, so the control keeps its width. */
@Composable
private fun Arrow(
    glyph: String,
    enabled: Boolean,
) = Text(text = glyph, style = TvType.titleSmall, modifier = Modifier.alpha(if (enabled) 1f else 0.3f))

internal const val BudgetStepperTag = "budget-stepper"
