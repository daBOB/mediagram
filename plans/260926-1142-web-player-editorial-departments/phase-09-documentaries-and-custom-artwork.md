# Phase 9 — Documentaries department and custom artwork

**Priority:** P2. **Status:** built 2026-09-26 on `feat/documentaries-and-artwork` (0.63.0), uncommitted. Rust 1290 + web 2162 tests green. Reports: `plans/reports/fullstack-developer-260926-1400-*`. Runs before phase 8.

## Locked decisions (user, 2026-09-26)
- **Documentaries** becomes a department: Home · Movies · Series · **Documentaries** ·
  Tutorials · Collections.
- A documentary is **its own upload kind**: simple documentaries recorded from TV stations
  (ARTE, ZDF, Terra X, …), usually not on TMDB. **TMDB films with the Documentary genre stay
  in Movies and do not appear in Documentaries** (user correction, 2026-09-26: "movies can
  have the genre documentary that stays in movies").
- Custom posters and backdrops are **image files the user supplies**. They override TMDB
  art where both exist. The same mechanism serves tutorials (Geldhochschule has no art
  today and shows the initial "G").

## Key facts (scouted 2026-09-26)
- **`doc` is taken.** It means a PDF inside a course (`mlib-spec/src/caption.rs:11-30`,
  commit a69a1d62). The documentary kind needs its own word: **`docu`** (proposed).
- The `doc` commit is the template for adding a kind: 32 files, and no schema bump,
  because `kind` is TEXT. Exhaustive matches that need a new arm:
  `mediagram-tmdb/src/{details.rs:21,certification.rs:30,posters.rs:154}`,
  `export/titles.rs:32`, `metadata/{resolve.rs:128,lookup.rs:23,search.rs:33}`,
  `index/label.rs:39`, `edit/plan.rs:99`, `commands/args.rs:144`.
- Old readers put an unknown kind on the Movies shelf: web `library.js:285-307`, Android
  `CatalogRepository.kt:147-168`. Nothing disappears before a reader learns `docu`.
- **Nothing carries image bytes today.** Art is either the posters in the export package
  (no backdrops) or a TMDB fetch on the device (`core api/store.rs:66`). The `assets` table
  holds only TEXT per set (subtitle, summary), with 1 MB per row.
- Art keys are `tmdb-{movie|tv}-{id}[-bg]` (`artwork_key.rs:16`). A course has no key.

## Design
1. **`artwork` table in schema v10**, an additive group on top of v9. This removes any need to coordinate who publishes v9 first:
   `artwork(key TEXT PRIMARY KEY, mime TEXT NOT NULL, bytes BLOB NOT NULL)`. It rides the
   index push, so web and Android both get it with no extra Telegram round trip.
   - Keys: the existing `tmdb-…` / `tmdb-…-bg` key **overrides** TMDB art.
   - A title with no TMDB id uses `title-{slug}` / `title-{slug}-bg`, with the slug taken
     from the show or course name. Extend `artwork_key.rs` to accept it.
   - Cap: 1 MB per image. The uploader refuses a bigger file and says to resize it.
     There is no image-processing dependency.
2. **Uploader**
   - `mediagram artwork <set-id|title> --poster <file> --backdrop <file>` sets art.
     `--clear` removes it.
   - `add-show` and `add-course` also pick up `poster.jpg`/`backdrop.jpg` (or `.png`/`.webp`)
     from the folder root.
3. **Documentary kind `docu`, uploaded by `mediagram add-docu <file|dir>`**, modelled on
   `add-course` (no TMDB lookup):
   - a file is one documentary, titled from its file name (`--title` overrides)
   - **multi-part documentaries** (user, 2026-09-26) are a folder of parts: `Die Römer/Teil 1..N`
     standalone, or `Terra X/Die Römer/Teil 1..N` as a group inside a collection. Unnumbered
     names order naturally (`Teil 2` before `Teil 10`, `course/plan.rs` `natural_cmp`)
   - a folder is a collection such as "Terra X": the folder name is its title, and the
     course `cid` mechanism groups its files
   - `poster.*`/`backdrop.*` in the folder are picked up as that collection's art
   - **summaries**: the same sidecars `add-course` reads (`course/sidecars.rs`) attach a
     summary to each documentary (user, 2026-09-26: "docus can have summaries as well")
   - caption, label and the `edit --kind` list gain `docu`, so a mis-kinded upload can be
     fixed with `edit`
4. **Readers look up artwork first:**
   - web `PosterStore`/`/api/posters/<key>.jpg`, falling back to the file store
   - core `poster_path` (Android), which writes the blob into the artwork directory once
   - `posterKeyFor`/`poster_key_for` return `title-{slug}` when there is no TMDB id
5. **Web department `#/documentaries`**
   - lists `docu` sets only, with the department hero and shelves: Continue watching,
     Recently added, and one row per collection
   - `groupLibrary` places `docu` sets in the Documentaries department, not Movies
   - the nav link and its count go in `index.html:54-56,80-82`, and the route in
     `app.js:696`

## Todo
- [ ] v10 `artwork` table + migration test (`schema.rs`, `READABLE_SCHEMAS`)
- [ ] `artwork_key.rs` accepts `title-{slug}[-bg]`, plus a test
- [ ] `index/artwork.rs` put/get/clear with the 1 MB cap, plus a test
- [ ] `mediagram artwork` command; folder pickup in `add-show`/`add-course`
- [ ] `Kind::Docu` across spec, uploader, tmdb and metadata matches; `add-docu` command (file or collection folder)
- [ ] web: artwork lookup in poster routes; `posterKeyFor` slug fallback
- [ ] web: Documentaries department, nav, route, `groupLibrary`
- [ ] core `poster_path` reads the `artwork` table (Android gets the art for free)
- [ ] Android: the Documentaries department is owed; added to `260926-1330-android-editorial-departments-parity`
- [ ] version bump (all three manifests)

## Success criteria
- `mediagram artwork "Geldhochschule" --poster p.jpg --backdrop b.jpg` → push-index →
  the web Tutorials card and hero show that art, and so does the Android course card.
- A TMDB documentary with custom art shows the custom art on both surfaces.
- `#/documentaries` lists every `docu` upload and no TMDB film; Unsere Erde and the other
  TMDB-genre documentaries stay in Movies.
- A v9 reader opening a v10 snapshot with artwork rows still works.

## Risks
- Index size: 20 posters plus backdrops at up to 1 MB each is about 40 MB against the
  package's 64 MB ceiling. Say so in the command output, and keep posters in practice
  ~200 KB.
- The other uploading machine must run a v9 build before it pushes, or its push drops the
  artwork table (as phase 1 already warns for credits).

## Answered (user, 2026-09-26)
- The kind name is `docu`.
- No TV station field.
- Docus carry summaries, like lessons.
