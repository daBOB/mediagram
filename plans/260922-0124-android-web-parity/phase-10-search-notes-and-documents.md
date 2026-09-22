# Phase 10: Search, notes and documents

**Status:** Not started — sketch

**Deliverable:** The three remaining viewer-facing gaps, none of which blocks
any other: a search box, a lesson's notes, and a document that opens.

## Context

- `web/src/search/index.ts:21-111` — **the reference.** Searches title, show,
  chapter, path and summary; German-aware folding so "steuer" does not collapse
  into "Fenster"; ranked by the strongest field any term hit, then by title
- `web/src/search/excerpt.ts:24-71` — the excerpt is taken from the original
  text, not the folded copy, so accents survive
- `web/public/lib/notes-view.js:17-93`, `markdown.js:18-227` — the notes panel,
  and a Markdown renderer that builds a DOM tree rather than setting HTML
- `android/ui-mobile/src/main/kotlin/CollectionScreen.kt` — where a document
  row says today that the phone cannot open one

## The three

**Search.** The core has no search method; the catalog is a few hundred rows
and already in memory on the phone. Client-side filtering over the sets the
repository holds is the smaller answer and probably the right one. The folding
and ranking rules port; the transport does not need to.

**Notes.** A lesson's summary is Markdown. The web auto-opens the panel for a
lesson and leaves it closed for a film — port that, it is a real preference and
not an accident. A Markdown renderer for Compose is the work; the safety
argument that shaped `markdown.js` is about `innerHTML` and does not carry over,
but its scheme allow-list for links does.

**Documents.** A handout currently says the phone cannot open it. Opening it
means fetching the bytes through the core and handing them to an installed
viewer by intent. That is the honest end of the fix already shipped.

## Todo

- [ ] search: filtering, folding and ranking ported, with the web's test cases
- [ ] notes: a Compose Markdown renderer, and the auto-open rule
- [ ] documents: fetch and hand off by intent, replacing the reason line
- [ ] `./scripts/check.sh`
