package ui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.isUnspecified

/*
 * Material's buttons, answering a press the web's way. MediagramTheme turns
 * ripple off (the web sets no tap highlight and answers with a quiet tint and
 * a 0.98 snap, theme.css:164-167), and a Material button hard-codes its ripple
 * instead of reading LocalIndication, so a bare one would answer with nothing.
 * Each wrapper draws the theme's indication over its own shape, on the
 * interaction source it hands the button.
 * [CatalogueButton] and [CatalogueIconButton] take Material's own
 * `interactionSource`, for content that moves with the button's hover or press.
 */

@Composable
fun CatalogueTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    PressTarget(modifier, ButtonDefaults.textShape, source) { inner ->
        TextButton(onClick = onClick, modifier = inner, enabled = enabled, interactionSource = source, content = content)
    }
}

@Composable
fun CatalogueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    PressTarget(modifier, ButtonDefaults.shape, source) { inner ->
        Button(onClick = onClick, modifier = inner, enabled = enabled, interactionSource = source, content = content)
    }
}

@Composable
fun CatalogueOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    PressTarget(modifier, shape, source) { inner ->
        OutlinedButton(
            onClick = onClick,
            modifier = inner,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = source,
            content = content,
        )
    }
}

@Composable
fun CatalogueIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    PressTarget(modifier, CircleShape, source) { inner ->
        IconButton(onClick = onClick, modifier = inner, enabled = enabled, interactionSource = source, content = content)
    }
}

/*
 * Material pads every button out to the 48dp touch box inside itself, so an
 * indication placed on the caller's modifier would tint and shrink that box,
 * not the drawn pill. The web tints the button's own box. The 48dp box is
 * taken here instead, ahead of the indication, and Material is told it needs
 * none: the touch target is the same size, the indication lands on the
 * visible container, and the caller's modifier (its semantics included) still
 * sits on the button's own node.
 */
@Composable
private fun PressTarget(
    modifier: Modifier,
    shape: Shape,
    source: InteractionSource,
    button: @Composable (Modifier) -> Unit,
) {
    val indication = LocalIndication.current
    val minimum = LocalMinimumInteractiveComponentSize.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        button(modifier.touchBox(minimum).clip(shape).indication(source, indication))
    }
}

private fun Modifier.touchBox(minimum: Dp): Modifier =
    if (minimum.isUnspecified) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val min = minimum.roundToPx()
            val width = constraints.constrainWidth(maxOf(placeable.width, min))
            val height = constraints.constrainHeight(maxOf(placeable.height, min))
            layout(width, height) { placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2) }
        }
    }
