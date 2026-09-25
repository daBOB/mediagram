package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
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
 */
@Composable
internal fun rememberNotesText(spans: List<Span>): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(spans, linkColor, codeBackground) {
        buildAnnotatedString { appendSpans(spans, NotesTextStyle(uriHandler, linkColor, codeBackground)) }
    }
}

private class NotesTextStyle(val uriHandler: UriHandler, val linkColor: Color, val codeBackground: Color)

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
 * web player's own page and have nothing to open on a phone, so they keep
 * their words and lose the link, as a refused one does.
 */
private fun AnnotatedString.Builder.appendLink(span: Span.Link, style: NotesTextStyle) {
    val href = span.href?.takeIf { it.startsWith("http:", true) || it.startsWith("https:", true) || it.startsWith("mailto:", true) }
    if (href == null) {
        appendSpans(span.spans, style)
        return
    }
    val link = LinkAnnotation.Url(
        url = href,
        styles = TextLinkStyles(SpanStyle(color = style.linkColor, textDecoration = TextDecoration.Underline)),
        // Handed to another app, as the web opens it in a new tab. With
        // nothing installed to take it, the tap does nothing rather than
        // taking the player down.
        linkInteractionListener = { runCatching { style.uriHandler.openUri(href) } },
    )
    withLink(link) { appendSpans(span.spans, style) }
}
