package ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.catalogueIndication

/**
 * A click that answers a press with the catalogue's tint and ring but never
 * the 0.98 shrink. No web `<a>` shrinks when pressed: `theme.css:167` is
 * `button:active` only, so a link that scales on Android would be an
 * invention. Pass the [interactionSource] the link shares with its
 * `hoverMotion`.
 */
fun Modifier.linkClickable(
    interactionSource: MutableInteractionSource?,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier =
    clickable(interactionSource = interactionSource, indication = catalogueIndication(pressScale = 1f), role = role, onClick = onClick)
