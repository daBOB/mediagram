/**
 * The markdown a lesson's notes are written in, as a tree.
 *
 * Pure, and deliberately not a renderer: it returns blocks and spans, and
 * something else turns those into nodes. That split is the security property,
 * not a tidiness preference. `dom.js` explains why nothing here may produce a
 * string of HTML — the text comes from a file beside a video, and the
 * shortest path from there to a script tag is `innerHTML`. A tree of
 * described intent cannot carry one.
 *
 * A deliberate subset: headings, lists that nest, blockquotes, fenced code,
 * rules, and inline emphasis, code and links. That is what these notes use.
 * Anything it does not recognise survives as the text it was written as,
 * which is the right failure for a summary — a viewer reading a stray `|`
 * loses nothing, a viewer shown an empty panel loses the notes.
 */

const HEADING = /^(#{1,6})\s+(.*)$/;
const BULLET = /^(\s*)[*+-]\s+(.*)$/;
const ORDERED = /^(\s*)\d+[.)]\s+(.*)$/;
const QUOTE = /^\s*>\s?(.*)$/;
const RULE = /^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/;
const FENCE = /^\s*```/;
const LINK = /^\[([^\]]*)\]\(([^)\s]+)\)/;

/** Schemes a link may use. Anything else is shown as the text it was. */
const SAFE_SCHEME = /^(?:https?:|mailto:|#|\/)/i;

const isBlank = (line) => line.trim() === "";
const indentOf = (line) => /^\s*/.exec(line)[0].length;
const markerOf = (line) => BULLET.exec(line) ?? ORDERED.exec(line);
const isOrdered = (line) => ORDERED.test(line) && !BULLET.test(line);

/**
 * Inline emphasis, code and links.
 *
 * Scanned rather than matched with one expression, because the markers nest:
 * `**Bargeld vs. Karte:**` is strong text that may itself hold a link. An
 * opener with no closer is not a marker at all and stays as the character it
 * is, which is what keeps a lone asterisk in prose from eating the rest of a
 * paragraph.
 */
export function parseSpans(text) {
  const spans = [];
  let plain = "";
  let i = 0;

  const flush = () => {
    if (plain !== "") spans.push({ kind: "text", text: plain });
    plain = "";
  };

  while (i < text.length) {
    const rest = text.slice(i);

    // Two before one, or `**bold**` opens as emphasis and never closes.
    if (rest.startsWith("**")) {
      const end = rest.indexOf("**", 2);
      if (end > 2) {
        flush();
        spans.push({ kind: "strong", spans: parseSpans(rest.slice(2, end)) });
        i += end + 2;
        continue;
      }
    }

    if (rest[0] === "*" || rest[0] === "_") {
      const end = rest.indexOf(rest[0], 1);
      if (end > 1) {
        flush();
        spans.push({ kind: "em", spans: parseSpans(rest.slice(1, end)) });
        i += end + 1;
        continue;
      }
    }

    if (rest[0] === "`") {
      const end = rest.indexOf("`", 1);
      if (end > 1) {
        flush();
        // Not parsed further: code spans are literal, markers and all.
        spans.push({ kind: "code", text: rest.slice(1, end) });
        i += end + 1;
        continue;
      }
    }

    const link = LINK.exec(rest);
    if (link) {
      flush();
      const href = SAFE_SCHEME.test(link[2]) ? link[2] : null;
      // A scheme this does not vouch for keeps its text and loses its link.
      // `javascript:` in an `href` is the same hole as a script tag.
      spans.push({ kind: "link", href, spans: parseSpans(link[1]) });
      i += link[0].length;
      continue;
    }

    plain += text[i];
    i += 1;
  }

  flush();
  return spans;
}

/** Strips the shallowest indent from `lines`, so a nested block starts at 0. */
function dedent(lines) {
  const smallest = Math.min(...lines.filter((l) => !isBlank(l)).map(indentOf));
  return lines.map((line) => (isBlank(line) ? line : line.slice(smallest)));
}

/**
 * One list, and everything nested inside its items.
 *
 * An item owns every following line indented deeper than its marker: that is
 * both how a continuation line and how a nested list are written, and parsing
 * those deeper lines as blocks in their own right is what makes the nesting
 * fall out rather than needing a rule per depth.
 */
function readList(lines, start) {
  const ordered = isOrdered(lines[start]);
  const base = indentOf(lines[start]);
  const items = [];
  let i = start;

  while (i < lines.length) {
    const marker = markerOf(lines[i]);
    if (!marker || indentOf(lines[i]) !== base || isOrdered(lines[i]) !== ordered) break;

    const own = marker[2];
    const nested = [];
    i += 1;

    while (i < lines.length) {
      if (isBlank(lines[i])) {
        // A blank ends the list unless the list plainly carries on past it.
        const next = lines.findIndex((line, at) => at > i && !isBlank(line));
        if (next === -1 || indentOf(lines[next]) < base) break;
        if (indentOf(lines[next]) === base && !markerOf(lines[next])) break;
        nested.push(lines[i]);
        i += 1;
        continue;
      }
      if (indentOf(lines[i]) <= base) break;
      nested.push(lines[i]);
      i += 1;
    }

    items.push({
      spans: parseSpans(own),
      blocks: nested.length > 0 ? parseBlocks(dedent(nested)) : [],
    });
  }

  return [{ kind: "list", ordered, items }, i];
}

/** The blocks in `lines`, which are already split and already dedented. */
function parseBlocks(lines) {
  const blocks = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (isBlank(line)) {
      i += 1;
      continue;
    }

    if (FENCE.test(line)) {
      const body = [];
      i += 1;
      while (i < lines.length && !FENCE.test(lines[i])) body.push(lines[i++]);
      // Past the closing fence, or past the end when there never was one.
      i += 1;
      blocks.push({ kind: "code", text: body.join("\n") });
      continue;
    }

    if (RULE.test(line)) {
      blocks.push({ kind: "rule" });
      i += 1;
      continue;
    }

    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ kind: "heading", level: heading[1].length, spans: parseSpans(heading[2]) });
      i += 1;
      continue;
    }

    if (QUOTE.test(line)) {
      const quoted = [];
      while (i < lines.length && QUOTE.test(lines[i])) quoted.push(QUOTE.exec(lines[i++])[1]);
      blocks.push({ kind: "quote", blocks: parseBlocks(quoted) });
      continue;
    }

    if (markerOf(line)) {
      const [list, next] = readList(lines, i);
      blocks.push(list);
      i = next;
      continue;
    }

    // A paragraph runs until a blank line or until something else starts.
    const text = [];
    while (i < lines.length && !isBlank(lines[i])) {
      const stops = FENCE.test(lines[i]) || RULE.test(lines[i]) || HEADING.test(lines[i]);
      if (text.length > 0 && (stops || markerOf(lines[i]) || QUOTE.test(lines[i]))) break;
      text.push(lines[i].trim());
      i += 1;
    }
    blocks.push({ kind: "paragraph", spans: parseSpans(text.join(" ")) });
  }

  return blocks;
}

/** The blocks of a markdown document. */
export function parseMarkdown(text) {
  if (typeof text !== "string" || text.trim() === "") return [];
  return parseBlocks(text.replace(/\r\n?/g, "\n").split("\n"));
}
