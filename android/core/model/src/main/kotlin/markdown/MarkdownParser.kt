package model.markdown

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[*+-]\s+(.*)$""")
private val ORDERED = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")
private val FENCE = Regex("""^\s*```""")

private fun isBlank(line: String) = line.isBlank()

private fun indentOf(line: String) = line.length - line.trimStart().length

private fun markerOf(line: String): MatchResult? = BULLET.find(line) ?: ORDERED.find(line)

private fun isOrdered(line: String) = ORDERED.containsMatchIn(line) && !BULLET.containsMatchIn(line)

/**
 * The blocks of a lesson's notes — `parseMarkdown` in `markdown.js`,
 * ported step for step so the phone and the web read a summary alike.
 * `cases.json` under `web/test/fixtures/markdown` is the contract: both
 * surfaces run it, quirks included.
 *
 * A deliberate subset: headings, lists that nest, blockquotes, fenced
 * code, rules, and inline emphasis, code and links. Anything else
 * survives as the text it was written as — a stray `|` loses a viewer
 * nothing, an empty panel loses them the notes.
 */
fun parseMarkdown(text: String?): List<Block> {
    if (text.isNullOrBlank()) return emptyList()
    return parseBlocks(text.replace(Regex("\r\n?"), "\n").split("\n"))
}

/** Strips the shallowest indent from [lines], so a nested block starts at 0. */
private fun dedent(lines: List<String>): List<String> {
    val smallest = lines.filterNot(::isBlank).minOfOrNull(::indentOf) ?: return lines
    return lines.map { if (isBlank(it)) it else it.substring(smallest) }
}

/**
 * One list, and everything nested inside its items. An item owns every
 * following line indented deeper than its marker — both a continuation
 * line and a nested list are written that way, so parsing those lines as
 * blocks of their own is what makes the nesting fall out.
 */
private fun readList(lines: List<String>, start: Int): Pair<Block, Int> {
    val ordered = isOrdered(lines[start])
    val base = indentOf(lines[start])
    val items = mutableListOf<ListItem>()
    var i = start

    while (i < lines.size) {
        val marker = markerOf(lines[i])
        if (marker == null || indentOf(lines[i]) != base || isOrdered(lines[i]) != ordered) break

        val own = marker.groupValues[2]
        val nested = mutableListOf<String>()
        i += 1

        while (i < lines.size) {
            if (isBlank(lines[i])) {
                // A blank ends the list unless the list plainly carries on past it.
                val next = (i + 1 until lines.size).firstOrNull { !isBlank(lines[it]) }
                if (next == null || indentOf(lines[next]) < base) break
                if (indentOf(lines[next]) == base && markerOf(lines[next]) == null) break
                nested += lines[i]
                i += 1
                continue
            }
            if (indentOf(lines[i]) <= base) break
            nested += lines[i]
            i += 1
        }

        items += ListItem(parseSpans(own), if (nested.isNotEmpty()) parseBlocks(dedent(nested)) else emptyList())
    }

    return Block.MarkdownList(ordered, items) to i
}

/** The blocks in [lines], already split and already dedented. */
private fun parseBlocks(lines: List<String>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        if (isBlank(line)) {
            i += 1
            continue
        }

        if (FENCE.containsMatchIn(line)) {
            val body = mutableListOf<String>()
            i += 1
            while (i < lines.size && !FENCE.containsMatchIn(lines[i])) body += lines[i++]
            // Past the closing fence, or past the end when there never was one.
            i += 1
            blocks += Block.Code(body.joinToString("\n"))
            continue
        }

        if (RULE.containsMatchIn(line)) {
            blocks += Block.Rule
            i += 1
            continue
        }

        val heading = HEADING.find(line)
        if (heading != null) {
            blocks += Block.Heading(heading.groupValues[1].length, parseSpans(heading.groupValues[2]))
            i += 1
            continue
        }

        if (QUOTE.containsMatchIn(line)) {
            val quoted = mutableListOf<String>()
            while (i < lines.size) {
                val inner = QUOTE.find(lines[i]) ?: break
                quoted += inner.groupValues[1]
                i += 1
            }
            blocks += Block.Quote(parseBlocks(quoted))
            continue
        }

        if (markerOf(line) != null) {
            val (list, next) = readList(lines, i)
            blocks += list
            i = next
            continue
        }

        // A paragraph runs until a blank line or until something else starts.
        val text = mutableListOf<String>()
        while (i < lines.size && !isBlank(lines[i])) {
            val stops = FENCE.containsMatchIn(lines[i]) || RULE.containsMatchIn(lines[i]) || HEADING.containsMatchIn(lines[i])
            if (text.isNotEmpty() && (stops || markerOf(lines[i]) != null || QUOTE.containsMatchIn(lines[i]))) break
            text += lines[i].trim()
            i += 1
        }
        blocks += Block.Paragraph(parseSpans(text.joinToString(" ")))
    }

    return blocks
}
