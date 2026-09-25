package model.markdown

private val LINK = Regex("""^\[([^\]]*)\]\(([^)\s]+)\)""")

/**
 * Schemes a link may use — `SAFE_SCHEME` in `markdown.js`. Anything else,
 * `intent:` and `file:` included, is shown as the words it was.
 */
private val SAFE_SCHEME = Regex("""^(?:https?:|mailto:|#|/)""", RegexOption.IGNORE_CASE)

/**
 * Inline emphasis, code and links — `parseSpans` in `markdown.js`, ported
 * step for step; `cases.json` under `web/test/fixtures/markdown` is the
 * contract both run.
 *
 * Scanned rather than matched with one expression, because the markers
 * nest: `**Bargeld vs. Karte:**` is strong text that may hold a link. An
 * opener with no closer is not a marker and stays the character it is.
 */
fun parseSpans(text: String): List<Span> {
    val spans = mutableListOf<Span>()
    val plain = StringBuilder()
    var i = 0

    fun flush() {
        if (plain.isNotEmpty()) spans += Span.Text(plain.toString())
        plain.clear()
    }

    while (i < text.length) {
        // Two before one, or `**bold**` opens as emphasis and never closes.
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                spans += Span.Strong(parseSpans(text.substring(i + 2, end)))
                i = end + 2
                continue
            }
        }

        val c = text[i]
        if (c == '*' || c == '_') {
            val end = text.indexOf(c, i + 1)
            if (end > i + 1) {
                flush()
                spans += Span.Emphasis(parseSpans(text.substring(i + 1, end)))
                i = end + 1
                continue
            }
        }

        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                spans += Span.Code(text.substring(i + 1, end))
                i = end + 1
                continue
            }
        }

        if (c == '[') {
            val link = LINK.find(text.substring(i))
            if (link != null) {
                flush()
                val target = link.groupValues[2]
                val href = target.takeIf { SAFE_SCHEME.containsMatchIn(it) }
                spans += Span.Link(href, parseSpans(link.groupValues[1]))
                i += link.value.length
                continue
            }
        }

        plain.append(c)
        i += 1
    }

    flush()
    return spans
}
