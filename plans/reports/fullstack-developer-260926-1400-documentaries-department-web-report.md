# Documentaries department + custom artwork — web half (phase 9)

Worktree `/home/andre/Workspace/mediagram-docu`, branch `feat/documentaries-and-artwork`. Not committed (lead commits). File ownership respected: `web/**` only; crates/docs/android untouched (verified via `git status`, all crates/docs diffs belong to the parallel Rust agent).

## Files modified (web/src)
- `web/src/package/posters.ts` (+~45 lines): ported `slug()` byte-for-byte from `mlib_spec::slug::slug`; `posterKeyFor(kind, tmdb, fallbackTitle?)` now falls back to `title-{slug}` when there's no provider id; `KEY` regex accepts `title-{slug}[-bg]`.
- `web/src/catalog/artwork-routes.ts`: added `artworkRow()` (mime-allowlisted single-row read) and `artworkKeys()` (cheap existence set, read once per catalog); `artworkResponse` serves the `artwork` table first, falls back to `PosterStore`; both probe gracefully for "no such table: artwork" (pre-v10 snapshots). `POSTER_PATH` loosened to `[a-z0-9-]{1,80}`, `posterKeyIsValid` is the real gate (was a second, driftable copy of the same regex).
- `web/src/catalog/routes.ts`: `forBrowser` computes the poster/backdrop/season key with `set.show ?? set.title` as fallback title; `has()` unions `posters.has()` and the artwork-key set so a table-only image (no packaged file) still marks a title as having art.
- `web/src/catalog.ts`: `EXPECTED_SCHEMA` 9 → 10 (confirmed against the parallel agent's `crates/mlib-spec/src/schema.rs`, which landed `SCHEMA_VERSION = 10` independently — numbers and doc wording match).

## Files modified (web/public)
- `web/public/lib/library.js`: `groupLibrary`'s movies bucket now excludes `docu`; `collections()` and `byTitle` exported for reuse. Net line count reduced (307/309 ceiling).
- `web/public/lib/catalog/shelf-view.js`: `SECTIONS` moved to new `sections.js` (re-exported for compatibility); `collectionGrid` generalized to read `noun`/`chapterNoun` from `SECTIONS[section]` instead of a movies/series ternary — now correct for documentaries with no special-casing; `emptyState` empty-state upload hints table-driven, includes documentaries.
- `web/public/lib/catalog/course-view.js`: `renderCollection`/`viewCourseLevel` thread `section` through instead of hardcoding `"tutorials"` — documentary collections now reuse the course view unchanged, per spec.
- `web/public/lib/catalog/department-pages.js`: new `renderDocumentariesDept` — hero, Continue watching (via `homeShelves().continues` filtered to `kind === "docu"`), Recently added, one row per collection, singles in their own row; empty shelves hidden.
- `web/public/lib/format.js`: `countOf` now pluralizes a noun ending in consonant+`y` as `-ies` (caught by the fixture screenshot: "five documentarys"). Verified no existing noun (`film`, `show`, `episode`, `lesson`, `season`, `chapter`, …) ends in `y`, so this is a zero-behavior-change fix for everything else.
- `web/public/app.js`: wired documentaries end to end — nav count, `library.documentaries` merge in `applyCatalog`, router branch for `#/documentaries` (dept page) and `#/documentaries/<collection>/…` (course view via `collectionsIn`-style inline lookup), `openTitle`'s "next" lookup includes documentary collections. Held at the 888-line ceiling by collapsing a multi-line import and removing a pre-existing double blank line.
- `web/public/index.html`: nav link + count (`n-documentaries`) in both the rail and the department bar, between Series and Tutorials.

## New files
- `web/public/lib/catalog/sections.js` — `SECTIONS` config (label/empty/extent/noun/chapterNoun per department).
- `web/public/lib/documentaries.js` + `.d.ts` — `groupDocumentaries` (splits into `collections`/`singles` rather than merging singles into one fake collection) and `countDocumentaries`.
- `web/test/documentaries.test.ts`, `web/test/slug.test.ts` — new unit tests.

## Tasks completed
- [x] v10 accepted in `catalog.ts` (6–10 readable)
- [x] `posterKeyFor`/`KEY` accept `title-{slug}[-bg]`; parity-tested against the Rust `slug` fn's own cases + umlaut
- [x] Artwork-table-first serving with file-store fallback; mime allowlist; graceful on pre-v10 snapshots
- [x] Tutorials pick up artwork (course cards + hero + resume card) — verified in the preview, Geldhochschule shows its poster instead of "G"
- [x] `#/documentaries`: nav, route, department page (hero, Continue watching, Recently added, per-collection rows, singles row, empty state)
- [x] `groupLibrary` keeps `docu` off Movies; TMDB-genre documentaries untouched (movies grouping logic never looked at genre, so this was already true — confirmed by test)
- [x] Collection detail reuses course view (parameterized, not duplicated)
- [x] Lint clean, full suite green, all line ceilings respected without raising any

## Tests status
- Lint: clean (`eslint public --max-warnings 0`)
- Typecheck: clean (`tsc --noEmit -p .`)
- Unit tests: 2161 pass, 0 fail (was 2138 before this phase's additions; net +23 across `documentaries.test.ts`, `slug.test.ts`, `posters.test.ts` artwork-table describe block, `format.test.ts` pluralization case)

## Visual verification
Built a scratch fixture (copy of the real channel snapshot + a `docu` collection "Terra X" with 3 episodes, 2 standalone docus "Free Solo"/"Citizenfour", and `artwork` rows for `title-terra-x`/`title-terra-x-bg`/`title-geldhochschule`) — not committed, lives only under `/tmp`. Ran `bun run preview` against it (copies, no Telegram) and screenshotted with `playwright-core` against the cached Chromium at `~/.cache/ms-playwright` (no npm browser download needed).

`plans/260926-1142-web-player-editorial-departments/visuals/p9-{documentaries,tutorials}-{desktop,phone}-{dark,light}.png` (8 files). Confirmed: hero + three shelves render correctly, singles fall back to initials (no artwork given), Terra X and Geldhochschule show their custom artwork, no horizontal overflow at either viewport, dark and light both legible. First pass caught the pluralization bug (fixed above) and a `reveal.js` scroll-reveal quirk in the screenshot tool itself (fixed by emulating `prefers-reduced-motion: reduce`, which is exactly the accessibility path the app already has for showing everything at once — not a product bug).

## Issues encountered / concerns
- **Search mislabeling (pre-existing, not fixed):** `search-view.js`'s "Lessons"/"Tutorials" search-result bucket is `hit.kind !== "movie" && hit.kind !== "ep"`, a catch-all that already covered `tut`. `docu` hits fall into it too and are labeled "Tutorials" rather than "Documentaries" in search results. Not in the phase's Build list; results are still discoverable, just miscategorized. Flagging rather than silently leaving it, per the project's own "gap is a bug until shown otherwise" standard — happy to fix in a follow-up if wanted.
- **Course-view wording:** a documentary collection's detail page (reused `course-view.js`) says "N lessons" rather than "N documentaries" (`extentOf`/`lessonRow` hardcode "lesson"). Left alone: fixing it means threading `section` further into `extentOf`/`countsUnder` for a wording-only gain, and `course-view.js` is already right at its 234-line ceiling.
- **Rail masthead** (`"902 films / six shows / one course"` under the logo) intentionally not extended to mention documentaries — not requested by the phase text, and doing so cleanly would need the same array-vs-object generalization `applyCatalog`'s nav-count line needed.
- Rust side (`artwork_key.rs`, `schema.rs`) landed in parallel and was read (not modified) to cross-check my TS port; both independently converged on `SCHEMA_VERSION`/`EXPECTED_SCHEMA = 10` and the same `title-{slug}[-bg]` shape, including the same documented "a title that slugs to literally `bg` collides with the backdrop marker" limitation.

**Status:** DONE
**Summary:** Web half of phase 9 fully implemented and verified — artwork table (poster/backdrop override with file-store fallback), title-slug keys ported byte-for-byte from Rust with a parity test, Documentaries department with nav/route/hero/shelves, tutorials now show custom art. Full suite green (2161 tests), lint/typecheck clean, 8 preview screenshots confirm the department page and tutorials art fix render correctly desktop+phone, dark+light.
**Concerns:** Search results mislabel `docu` hits as "Tutorials" (pre-existing catch-all bucket, out of this phase's scope); course-view wording says "lessons" for a documentary collection's detail page; rail masthead line not extended to documentaries. None block ship; all are documented above for a follow-up decision.
