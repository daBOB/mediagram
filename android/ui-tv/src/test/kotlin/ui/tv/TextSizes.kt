package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit

/** The size [text] is actually laid out at — what the screen draws, not what a style constant says. */
internal fun SemanticsNodeInteractionsProvider.fontSizeOf(
    text: String,
    substring: Boolean = false,
): TextUnit {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, substring = substring, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    return results.first().layoutInput.style.fontSize
}
