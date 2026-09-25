package model.markdown

/**
 * A lesson's notes as a tree — the shape `markdown.js` returns on the web,
 * one type per `kind`. The renderer builds from this and never from the
 * text itself, which is where the link allow-list lives: a link the parser
 * refused arrives here with no [Span.Link.href] at all, so nothing
 * downstream has to remember to check.
 */
sealed interface Block {
    /** `level` is the number of `#`s, 1–6; the renderer floors it. */
    data class Heading(val level: Int, val spans: List<Span>) : Block

    data class Paragraph(val spans: List<Span>) : Block

    data class MarkdownList(val ordered: Boolean, val items: List<ListItem>) : Block

    data class Quote(val blocks: List<Block>) : Block

    /** Fenced code, exactly as written between the fences. */
    data class Code(val text: String) : Block

    data object Rule : Block
}

/** One item: its own line, then whatever is nested under it. */
data class ListItem(val spans: List<Span>, val blocks: List<Block>)

sealed interface Span {
    data class Text(val text: String) : Span

    data class Strong(val spans: List<Span>) : Span

    data class Emphasis(val spans: List<Span>) : Span

    /** Literal, markers and all. */
    data class Code(val text: String) : Span

    /** [href] is null when the scheme was refused: the words stay, the link goes. */
    data class Link(val href: String?, val spans: List<Span>) : Span
}
