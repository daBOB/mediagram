package ui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/*
 * Material's buttons, answering a press the web's way. MediagramTheme turns
 * ripple off (the web sets no tap highlight and answers with a quiet tint and
 * a 0.98 snap, theme.css:164-167), and a Material button hard-codes its ripple
 * instead of reading LocalIndication, so a bare one would answer with nothing.
 * Each wrapper draws the theme's indication over its own shape, on the
 * interaction source it hands the button.
 */

@Composable
fun CatalogueTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, ButtonDefaults.textShape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun CatalogueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, ButtonDefaults.shape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, shape, LocalIndication.current),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun CatalogueIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.pressFeedback(source, CircleShape, LocalIndication.current),
        enabled = enabled,
        interactionSource = source,
        content = content,
    )
}

private fun Modifier.pressFeedback(source: InteractionSource, shape: Shape, indication: Indication): Modifier =
    clip(shape).indication(source, indication)
