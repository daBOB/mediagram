---
title: "Anime department"
description: "Japanese animation (TMDB genre Animation + original language ja, or a hand-set override) leaves Movies/Series for one Anime department on web and Android."
status: completed
priority: P2
effort: 22h
branch: main
tags: [anime, catalog, schema-v11, web, android, rust-core, parity]
created: 2026-09-28
---

# Anime department

User decisions (2026-09-28, locked): automatic classification = TMDB genres include
"Animation" AND original language `ja`, plus a `mediagram edit` override (in/out)
that lives in the channel index; anime leaves Movies/Series entirely (Documentaries-style
exclusivity); one department mixing anime series (seasons kept) and anime films.

## Phases

| # | Phase | Effort | Status | Release |
|---|---|---|---|---|
| 1 | [Index: original language, overrides, backfill, `edit --anime`](phase-01-index-language-and-anime-overrides.md) | 5h | completed | minor |
| 2 | [Web: rule, shared fixture, Anime department](phase-02-web-anime-department.md) | 7h | completed | minor |
| 3 | [Core: Rust rule, `SetSummary.anime`, bindings, `MediaSet.anime`](phase-03-core-anime-flag-and-android-data.md) | 3h | completed | patch |
| 4 | [Android: Anime shelf on phone, tablet, TV](phase-04-android-anime-department.md) | 7h | completed | minor |

Order is strict 1 → 2 → 3 → 4; each ends green on `scripts/check.sh` and ships alone.
Version: one bump per shipped phase, all three manifests in step, by pattern from
whatever `main` carries (another session commits there); `versionCode` untouched.

## Settled decisions

- **Rule, once per language:** `isAnime(kind, genres, originalLanguage, forced)` —
  `movie`/`ep` only; override wins; else `ja` + genre exactly `Animation`. TS
  `web/src/catalog/anime.ts`, Rust `crates/mediagram-core/src/shows/anime.rs`;
  Kotlin reads the core's boolean. Shared cases: `web/test/fixtures/anime/cases.json`
  (anime film, anime series, Western animation, live-action Japanese, override in,
  override out, older index, docu, another-language genre).
- **Genre by name, not id:** this library is `de-DE`; TMDB names genre 16
  "Animation" in en/de/fr. A library in another language is pinned as not
  matching (fixture case); store genre ids only if one appears.
- **Data:** schema v11 = `shows.original_language` + table `anime_overrides(source,
  kind, id, anime 1/0/NULL, set_at)`. Separate table because `shows::upsert`
  replaces rows whole and `merge_shows` only fills NULLs — an override needs
  last-writer-wins across the two uploaders (newer `set_at` wins; NULL = back to
  automatic, kept as a row so clearing propagates). Older index → no language →
  not anime unless overridden. Readers treat both as optional.
- **Override:** `mediagram edit <set-id> --anime yes|no|auto` — per TMDB title
  (every episode, incl. future ones), index-only, `--dry-run`, then `push-index`.
- **Backfill:** `mediagram metadata` re-reads the TMDB disk cache — no key, no
  network (all 981 titles have cached payloads; ~35 anime titles: 4 series / 222
  episodes, ~31 films). Operator: pull-index → metadata → push-index (or
  `sync-index`) on one machine; upgrade the second uploader before it publishes.
- **Where anime shows:** department views exclude it (Movies/Series depts, film
  wall, Latest, home Latest rows, home editorial picks); lookups include it
  (search group, genre, person, franchise, Similar, Continue, Next up, autoplay).
- **Home:** editorial picks stay Movies-only (like Documentaries); Continue and
  Next up include anime (the viewer's own progress).
- **Empty tab:** hidden at 0 on both surfaces (no upload command to point at;
  unlike Documentaries); `#/anime` still renders an empty state.
- **TV difference:** Anime is a plain poster wall on TV, as Documentaries is.

## Dependencies & coordination

- Readers accept newer schemas (`mlib-spec/src/package/mod.rs:157-164`), so a
  v11 index is safe for not-yet-updated players.
- Pending plan `260928-0047-profile-roles-pins-kids-age-limits` also edits
  `app.js`, `library-session.js`, `CatalogScreen.kt`, `CatalogViewModel`: rebase
  whichever lands second.

- Rollback: revert the phase's commit (details per phase); phase 1 data is inert
  to readers without phases 2–4.

## Resolved with the user (2026-09-28)

- Released 2026-09-29 01:59: `pull-index` (the other uploader had already backfilled 986 languages), `metadata`, `push-index` as channel message 9793. Live: 36 anime titles (4 series, 32 films) on the web player and the tablet (0.81.0).
- Home editorial picks stay Movies-only: anime films (Ghibli included) leave
  the cover, features and "This month"; Continue/Next up still include anime.
- Genre pages include anime, under their "Movies"/"Series" headings, so a
  title's own genre link always finds it.
