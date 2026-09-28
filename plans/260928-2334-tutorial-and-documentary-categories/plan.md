---
title: "Categories for Tutorials and Documentaries"
description: "A hand-set category per course, documentary collection or standalone documentary, kept in the channel index, drawn as one row per category on both department pages."
status: pending
priority: P2
effort: 15h
branch: main
tags: [categories, tutorials, documentaries, schema-v12, web, android, rust-core, parity]
created: 2026-09-28
blockedBy: [260928-2042-anime-department]
---

# Categories for Tutorials and Documentaries

User decisions (2026-09-28, locked): category set by hand — `add-course <dir> --category`,
`add-docu <file|dir> --category`, `edit <set-id> --category` plus a clear; index-only
(channel index, no caption rewrite, no Telegram); ONE category per unit; on the
Tutorials and Documentaries department pages, after the Continue row, one row per
category, then the existing sections; uncategorised units under "Other".

## Phases

| # | Phase | Effort | Status | Release |
|---|---|---|---|---|
| 1 | [Index: schema v12 `categories`, key rule, merge, `--category` flags](phase-01-index-categories-and-uploader-flags.md) | 5h | completed | 0.79.0 |
| 2 | [Web: `category` per set, row rule + fixture, rows on both department pages](phase-02-web-category-rows.md) | 4h | completed | 0.80.0 |
| 3 | [Android: core field + bindings, rows on phone/tablet, TV Tutorials rows](phase-03-android-category-rows.md) | 6h | pending | minor |

Strict order 1 → 2 → 3; each green on `scripts/check.sh`, ships alone, one version bump
(all three manifests in step, by pattern from whatever `main` carries; `versionCode` untouched).
Starts from `main` at 0.78.0 / schema v11 (anime phase 4 landed first).

## Settled decisions

- **Unit:** a course (all its lessons + documents), a documentary collection (all its
  episodes), a standalone documentary (itself). Exactly what each department page shows as a card.
- **Key:** `(department, item_key)` where `item_key = title_art_key(show ?? title)`
  (`title-<slug>`, `crates/mlib-spec/src/package/artwork_key.rs:71`) — the key a unit's
  custom artwork already lives under, and the name every surface groups by.
  `department` = `tutorials` for `tut`/`doc`, `documentaries` for `docu`; films/episodes: none.
  Survives re-uploads, resumes and a course uploaded from two folders under one title;
  follows a rename (like the art does). `group_key` (cid) rejected: readers group by `show`,
  not cid, and a split course can carry two cids. `set_id` rejected for singles: a re-upload
  of a single is a new set id and would drop its category.
- **Storage:** v12 table `categories(department, item_key, category, set_at)`; NULL
  category = cleared, kept as a row so the clear travels; last-writer-wins merge on
  `set_at` (anime_overrides shape). Readers treat the table as optional.
- **Normalisation (writer only):** whitespace trimmed and collapsed; empty refused;
  "Other" (any case) refused; a case-variant of a spelling already used in the same
  department is replaced by that spelling (prints a note). Readers compare exactly.
- **Rows rule (shared fixture):** no unit categorised → no rows (page exactly as today —
  an "Other" row alone would repeat "All courses" verbatim). Otherwise one row per
  category, alphabetical (natural order), "Other" (uncategorised) always last, omitted when
  empty; units keep department order (docu: collections, then singles).
- **Rows are uncapped** strips (units are few; no per-category page — YAGNI).
- **Nowhere else:** no category on course/title pages, search, home or Latest.
- **TV:** Tutorials gets the same rows (its department page already draws rows).
  Documentaries stays the plain wall it already is — deliberate, written down.

## Dependencies & coordination

- Blocked by `260928-2042-anime-department` (phase 4 edits `Departments.kt`,
  `TvDepartmentPages.kt`, `CatalogScreen.kt`, `Shelves.kt`): phase 3 re-reads them first.
- Readers accept newer schemas (`crates/mlib-spec/src/package/mod.rs:157-164`,
  `web/src/package/pointer.ts:144-150`): a v12 index is safe for older players.
- Operator steps (phase 1, not executed by agents): set categories, then pull → edit → push
  on one machine; upgrade the second uploader before it publishes.
- Rollback: revert the phase commit; phase 1 data is inert to readers without 2–3.

## Resolved with the user (2026-09-28)

- All four courses (Forex Mentor - Trendline Mastery, Geldhochschule, Mentfx Course
  2026, Wall Street Story) get the category "Trading". The 7 documentary collections
  and 6 standalone documentaries are listed for the user when the flag exists.

- Documentaries (chosen 2026-09-29): **China** — Die Geschichte Chinas,
  China – Wie eine Nation entstand, Mao – Chinas roter Kaiser (collections),
  Pekinger Frühling (single); **Geschichte** — Die Inquisition, Die amerikanische
  Revolution – Geburtsstunde der USA (collections), Versailles – Palast des
  Sonnenkönigs, Frauen und Männer der Steinzeit – Gleicher als gedacht? (singles);
  **Politik** — Bin Laden – Gesicht des Terrors, Kapitalismus made in USA – Reichtum
  als Kult (collections), Enthüllt: Die Suche nach Osama bin Laden (single);
  **Kultur** — Falco - Mon Amour, Venedig retten (singles).

## Unresolved questions

1. The local index is still at schema v10 (anime backfill not run yet); run both in one
   session per phase 1's operator steps.
