/**
 * Notes, as nodes.
 *
 * The other half of `markdown.js`: it describes what the text meant, and this
 * builds it. Every node is created and every string goes in through
 * `textContent`, so there is no point at which a summary's bytes are treated
 * as markup — see the note at the top of `dom.js`.
 *
 * Kept apart from the parser so the rules about what markdown means can be
 * tested without a document to build into.
 */

import { el } from "../dom.js";
import { parseMarkdown } from "./markdown.js";

/** Inline spans, appended into `parent`. */
function appendSpans(parent, spans) {
  for (const span of spans) {
    if (span.kind === "text") {
      parent.append(document.createTextNode(span.text));
      continue;
    }
    if (span.kind === "code") {
      parent.append(el("code", null, span.text));
      continue;
    }
    if (span.kind === "link" && span.href !== null) {
      const link = el("a");
      link.href = span.href;
      // A summary's links point out of this page and out of this library, so
      // they open away from the player rather than replacing it, and say
      // nothing to whatever is on the other end.
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      appendSpans(link, span.spans);
      parent.append(link);
      continue;
    }
    // A link whose scheme was refused keeps its words and loses its link.
    const tag = span.kind === "strong" ? "strong" : span.kind === "em" ? "em" : "span";
    const node = el(span.kind === "link" ? "span" : tag);
    appendSpans(node, span.spans);
    parent.append(node);
  }
}

/** One block as a node. */
function nodeFor(block) {
  if (block.kind === "heading") {
    // Floored rather than shifted. The panel has its own heading, so nothing
    // in here may be an `h1` — but demoting every level to make room turned
    // these notes' `###` sections, which are their top-level structure, into
    // the smallest label the stylesheet has.
    const node = el(`h${Math.min(Math.max(block.level, 2), 6)}`);
    appendSpans(node, block.spans);
    return node;
  }

  if (block.kind === "list") {
    const list = el(block.ordered ? "ol" : "ul");
    for (const item of block.items) {
      const row = el("li");
      appendSpans(row, item.spans);
      for (const child of item.blocks) row.append(nodeFor(child));
      list.append(row);
    }
    return list;
  }

  if (block.kind === "quote") {
    const quote = el("blockquote");
    for (const child of block.blocks) quote.append(nodeFor(child));
    return quote;
  }

  if (block.kind === "code") {
    const pre = el("pre");
    pre.append(el("code", null, block.text));
    return pre;
  }

  if (block.kind === "rule") return el("hr");

  const paragraph = el("p");
  appendSpans(paragraph, block.spans);
  return paragraph;
}

/** Replaces everything in `parent` with `text` rendered as markdown. */
export function renderNotes(parent, text) {
  parent.textContent = "";
  for (const block of parseMarkdown(text)) parent.append(nodeFor(block));
}
