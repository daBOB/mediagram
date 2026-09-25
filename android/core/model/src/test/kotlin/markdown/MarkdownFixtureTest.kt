package model.markdown

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the web's own `cases.json` — the same file `markdown-shared-fixtures.test.ts`
 * runs against `markdown.js` — so the two parsers cannot drift apart unseen.
 * The Kotlin tree is written back out in the web's JSON shape and compared
 * whole, quirks included.
 */
class MarkdownFixtureTest {
    @Test
    fun everySharedCaseParsesToTheWebsTree() {
        val file = assertNotNull(locateFixture(), "web/test/fixtures/markdown/cases.json not found above ${System.getProperty("user.dir")}")
        val cases = Json.parseToJsonElement(file.readText()).jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases.map { it.jsonObject }) {
            val name = case.getValue("name").jsonPrimitive.content
            val input = case.getValue("input").jsonPrimitive.content
            assertEquals(case.getValue("blocks"), blocksJson(parseMarkdown(input)), name)
        }
    }

    @Test
    fun anIntentLinkKeepsItsWordsAndLosesItsLink() {
        val paragraph = parseMarkdown("[öffnen](intent://scan#Intent;end)").single() as Block.Paragraph
        assertEquals(Span.Link(null, listOf(Span.Text("öffnen"))), paragraph.spans.single())
    }
}

private fun blocksJson(blocks: List<Block>): JsonArray = JsonArray(blocks.map(::blockJson))

private fun spansJson(spans: List<Span>): JsonArray = JsonArray(spans.map(::spanJson))

private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))

private fun str(value: String) = JsonPrimitive(value)

private fun blockJson(block: Block): JsonElement = when (block) {
    is Block.Heading -> obj("kind" to str("heading"), "level" to JsonPrimitive(block.level), "spans" to spansJson(block.spans))
    is Block.Paragraph -> obj("kind" to str("paragraph"), "spans" to spansJson(block.spans))
    is Block.MarkdownList -> obj(
        "kind" to str("list"),
        "ordered" to JsonPrimitive(block.ordered),
        "items" to JsonArray(block.items.map { obj("spans" to spansJson(it.spans), "blocks" to blocksJson(it.blocks)) }),
    )
    is Block.Quote -> obj("kind" to str("quote"), "blocks" to blocksJson(block.blocks))
    is Block.Code -> obj("kind" to str("code"), "text" to str(block.text))
    Block.Rule -> obj("kind" to str("rule"))
}

private fun spanJson(span: Span): JsonElement = when (span) {
    is Span.Text -> obj("kind" to str("text"), "text" to str(span.text))
    is Span.Code -> obj("kind" to str("code"), "text" to str(span.text))
    is Span.Strong -> obj("kind" to str("strong"), "spans" to spansJson(span.spans))
    is Span.Emphasis -> obj("kind" to str("em"), "spans" to spansJson(span.spans))
    is Span.Link -> obj("kind" to str("link"), "href" to (span.href?.let(::str) ?: JsonNull), "spans" to spansJson(span.spans))
}

/** Walks up from the working directory to the web's fixture, or gives up at the filesystem root. */
private fun locateFixture(): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "web/test/fixtures/markdown/cases.json")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}
