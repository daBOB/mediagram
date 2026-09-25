package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import model.markdown.Span

/**
 * A run of inline spans as styled text — `appendSpans` in the web's
 * `notes-view.js`. Built from the parsed tree only, never from the text.
 *
 * The colours are the caller's, since each surface draws with its own
 * component library. [uriHandler] is what a link is handed to; without
 * one, a link keeps its words and loses the link, as a refused one does —
 * for a surface with nothing that could point at a word inside a
 * paragraph, a link drawn as one would promise a press that cannot land.
 */
@Composable
fun rememberNotesText(
    spans: List<Span>,
    linkColor: Color,
    codeBackground: Color,
    uriHandler: UriHandler?,
): AnnotatedString =
    remember(spans, linkColor, codeBackground, uriHandler) {
        buildAnnotatedString { appendSpans(spans, NotesTextStyle(uriHandler, linkColor, codeBackground)) }
    }

/**
 * The level a notes heading is drawn at: floored rather than shifted, as on
 * the web. The panel has its own head, so nothing in it is a top-level
 * title, but these notes write their sections as `###` and demoting every
 * level would shrink those to the smallest label there is.
 */
fun notesHeadingLevel(level: Int): Int = level.coerceAtLeast(2)

private class NotesTextStyle(val uriHandler: UriHandler?, val linkColor: Color, val codeBackground: Color)

private fun AnnotatedString.Builder.appendSpans(spans: List<Span>, style: NotesTextStyle) {
    for (span in spans) {
        when (span) {
            is Span.Text -> append(span.text)
            is Span.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = style.codeBackground)) { append(span.text) }
            // SemiBold, not Bold: the reading face is declared up to 600, and
            // asking it for 700 came back at regular weight on the device.
            is Span.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendSpans(span.spans, style) }
            is Span.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendSpans(span.spans, style) }
            is Span.Link -> appendLink(span, style)
        }
    }
}

/**
 * Only a link that leaves the app is a link here. The parser has already
 * refused unsafe schemes; of the ones it keeps, `#` and `/` point into the
 * web player's own page and have nothing to open in the app, so they keep
 * their words and lose the link, as a refused one does.
 */
private fun AnnotatedString.Builder.appendLink(span: Span.Link, style: NotesTextStyle) {
    val handler = style.uriHandler
    val href = span.href?.takeIf { it.startsWith("http:", true) || it.startsWith("https:", true) || it.startsWith("mailto:", true) }
    if (href == null || handler == null) {
        appendSpans(span.spans, style)
        return
    }
    val link = LinkAnnotation.Url(
        url = href,
        styles = TextLinkStyles(SpanStyle(color = style.linkColor, textDecoration = TextDecoration.Underline)),
        // Handed to another app, as the web opens it in a new tab. With
        // nothing installed to take it, the tap does nothing rather than
        // taking the player down.
        linkInteractionListener = { runCatching { handler.openUri(href) } },
    )
    withLink(link) { appendSpans(span.spans, style) }
}
