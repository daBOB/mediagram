package designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The small round mark a row wears: a title held on this device, beside its
 * status line; an achievement not yet seen, on the Stats icon's corner. One
 * size everywhere. [color] is the caller's theme's tertiary — the phone's
 * Material 3 and the television's tv-material each hold their own.
 * [description] is what a screen reader says for it, where the words beside
 * it do not already.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val said = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Box(modifier = modifier.size(7.dp).clip(CircleShape).background(color).then(said))
}
