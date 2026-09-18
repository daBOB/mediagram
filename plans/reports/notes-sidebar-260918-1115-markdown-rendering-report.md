# Notes as a rendered sidebar

Ask: same player for lessons, with a sidebar for the notes, markdown rendered.
Decisions confirmed: hand-rolled parser (no dependency), sidebar pushes the
video and opens by default on a lesson.

## Why a parser and not a library

`dom.js` states the rule this had to satisfy: nothing interpolates a string
into markup, because the text comes from a caption or a file beside a video.
So the renderer is split in two — `markdown.js` returns a tree of blocks and
spans and is pure and tested; `notes-view.js` turns that tree into nodes with
`el()` and `textContent`. There is no point at which a summary's bytes are
markup, which is a property of the shape rather than of remembering to escape.

A link whose scheme is not `http`, `https`, `mailto`, `#` or `/` keeps its
words and loses its `href`. `javascript:` in an `href` is the same hole as a
script tag, and the check lives in the parser so nothing downstream can forget.

## Verified against all 162 real summaries

Parsed every summary in `library.db`:

```
162 summaries parsed, 0 produced nothing
blocks: paragraph 800, heading 784, list 732, rule 84, quote 5
unrecognised block markers left in paragraphs: 0
```

The first pass reported 51. All 51 were lines written `**1. Favoriten
erstellen**` — bold used as a pseudo-heading, which is a paragraph, correctly.
The detector was reading rendered text, not source.

Rendering checked in the browser on the longest summary (4,164 chars):
`### ` sections as serif headings, bullet items with bold run-ins, an ordered
list nested inside a bullet and a bullet nested after it, then back out —
3 nested lists, 2 `ol`, 5 `ul`.

## Layout

| Checked | Result |
|---|---|
| Lesson opens with notes shown | `with-notes` on, panel visible |
| Film does not | `with-notes` off |
| Close button | class off, `video { right: 0 }`, `aria-expanded=false` |
| Notes button reopens | class on, `video { right: 544px }` |
| Phone | panel 390px wide, video not pushed, no overflow |
| Preload still not playing | `paused: true` |

`./scripts/check.sh`: clippy clean, 26 Rust tests, 480 bun tests, 0 fail.

## One thing changed after looking

Headings were mapped `level + 1` to keep them below the panel's own heading,
which turned these notes' `###` sections — their top-level structure — into
the smallest, uppercase label in the stylesheet. Now floored at `h2` instead
of shifted, so `###` is an `h3` and reads as a section.

## Unresolved

- The parser covers what these notes use. A table, an image or a footnote
  would render as its literal text. Nothing in the library uses one; if a
  future summary does, it degrades to readable text rather than breaking.
- Notes do not fade with the HUD. Deliberate — the column is a thing you
  opened, not a control — but it means a lesson left playing keeps a lit panel
  beside it.
- Still open from before: the `cache-store.test.ts` eviction-order flake, and
  4 TypeScript errors in the untracked `web/test/posters.test.ts`.
