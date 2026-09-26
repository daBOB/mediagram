# Rust half of phase 9 — docu kind and custom artwork

Worktree: `/home/andre/Workspace/mediagram-docu`, branch `feat/documentaries-and-artwork`.
Spec: `plans/260926-1142-web-player-editorial-departments/phase-09-documentaries-and-custom-artwork.md`.
Not committed — lead commits.

## What shipped

**Schema v10** (`crates/mlib-spec/src/schema.rs`): `artwork(key TEXT PRIMARY KEY,
mime TEXT NOT NULL, bytes BLOB NOT NULL)`, additive over v9. `SCHEMA_VERSION=10`,
`READABLE_SCHEMAS=[6,7,8,9,10]`, `OLDEST_READABLE_SCHEMA=6` (unchanged). A v9
reader never sees the table name so it just doesn't query it; a v10 reader opening
v6–v9 gets `shared_columns`/table-existence checks that already tolerate an absent
table (same pattern v9's `credits`/`franchises` set).

**`crates/mlib-spec/src/package/artwork_key.rs`**: `poster_key_is_valid` now also
accepts `title-{slug}` and `title-{slug}-bg`. New `title_art_key(name) ->
Option<String>` — the one place that derives this key, used by both the uploader
and `mediagram-core`.

**`crates/mediagram/src/index/artwork.rs`**: `put`/`get`/`clear`/`put_file` (1 MB
cap, mime from extension: jpg/jpeg/png/webp) and `adopt_folder` (picks up
`poster.*`/`backdrop.*` at a folder root, case-insensitive on both name and
extension).

**`mediagram artwork <set-id|title> --poster <file> --backdrop <file> [--clear]`**
(`commands/artwork.rs`): resolves `target` to a set by exact `set_id`, else by
exact `show`/`title` match; keys by that set's `tmdb-…` key if it has one, else
`title-{slug}` of its `show` or `title`. Never pushes.

**`Kind::Docu`** (`mlib-spec/src/caption.rs`, spelled `"docu"`): a documentary,
never looked up at any provider. Added everywhere `Kind` is matched exhaustively:
`mediagram-tmdb::{details,certification,posters,credits}`, `mediagram::{export::titles,
metadata::{resolve,lookup,search}, index::label, edit::plan (now editable),
commands::args (edit --kind help)}`, `mlib-spec::part_name` (base_name),
`mlib-spec::caption_display` (new file, split out of `caption.rs` — see below).
New `docu_code(chapter, episode) -> "C02E03"`.

**`mediagram add-docu <file|dir>`** (`commands/add_docu/`): a file uploads one
standalone documentary; a folder uploads a collection, reusing `add-course`'s
`walk_course`, `course_title`/`collection_id`, `dry_run_table` and
`course::sidecars` whole — only the kind differs (`Docu` instead of `Tut`, via a
new `kind: Kind` field on `upload::new_set::LessonOf`). `poster.*`/`backdrop.*`
at a collection's root become its artwork. Flags: `--title`, `--cid`, `--dry-run`,
`--no-push`, `--no-remux`, `--variant`. `add-show`/`add-course` now also pick up
`poster.*`/`backdrop.*` at their folder root.

**`mediagram-core`**: new `crates/mediagram-core/src/artwork.rs::get` (read-only,
tolerates a pre-v10 snapshot with no such table). `api/store.rs::poster_path` now
checks the artwork table before falling back further, writing a hit into the
artwork directory once (kept the existing `.jpg` file-name convention — see
ponytail note in that function). `dto/summary.rs::poster_key_for` now takes
`show`/`title` and returns `title-{slug}` for a `Tut`/`Docu` set with no TMDB id.

**Channel merge**: `index::merge_artwork` carries missing `artwork` keys the same
way v9's `merge_credits` carries missing credits/franchises (never overwrites a
locally-held key; tolerates a pre-v10 channel snapshot). Wired into `MergeReport`
and `pull-index`'s printed summary.

**Line-limit compliance**: `crates/mediagram/tests/code_standards.rs` enforces
≤200 lines per source file workspace-wide. Split out where a file grew past that:
`caption.rs` → `caption_display.rs` (display_name); `main.rs` → `cli.rs` (the
whole clap surface); `commands/args.rs` → `commands/args_docu.rs`
(`AddCourseArgs`/`AddDocuArgs`/`ArtworkArgs`); `commands/add_docu.rs` →
`add_docu/{mod.rs,collection.rs}`; `index/artwork.rs`'s tests →
`index/artwork_tests.rs`.

**Docs**: `docs/system-architecture.md` §10.1 (schema table now describes v10 and
`artwork`), module map entries for `add_docu/`, `artwork.rs`, `index/artwork.rs`;
`docs/project-changelog.md` new "Unreleased — documentaries and custom artwork"
section (did not touch the pre-existing, already-stale "Unreleased — 0.62.0"
heading — that predates this work and isn't mine to fix); `docs/mlib-package-v1.md`
example JSON payloads bumped from `"schema":9` to `10` (the crate's own
`package_spec_examples` test checks these against `SCHEMA_VERSION` and was
failing before this fix — nothing else in that doc needed a v10-specific change).

## Exact column values for the web/lead

**A single-file documentary** (`add-docu movie.mp4`):
- `kind` = `"docu"`
- `show` = `NULL`
- `title` = the file's name with a leading number stripped (`crate::media::file_names::split_number_and_title`), or `--title` verbatim if given
- `group_key` (cid) = `NULL`
- `chap`, `path`, `season`, `episode`, `year` = `NULL`
- `tmdb`/`tvdb`/`imdb` = `NULL`
- Poster key: `title-{slug(title)}` (only if that title slugs to something; see below)

**A folder collection member** (`add-docu "Terra X/"`, one episode):
- `kind` = `"docu"`
- `show` = the collection title — folder name, or `--title` override (same
  derivation as `add-course`'s course title: `course::identity::course_title`)
- `title` = that file's own title, same leading-number-stripped rule as above
- `group_key` (cid) = `mlib_spec::slug::slug(show)`, or `--cid` override —
  **identical mechanism to a course's `cid`**, so the web can group a
  documentary collection exactly the way it already groups a course
- `chap` = the video-holding subfolder's inferred/declared title (e.g. a
  "Season 1" folder), or `NULL` if the episode sits at the collection root
- `path` = folders within the collection, `/`-separated, `NULL` at the root
- `season` (caption `s`) = the chapter number `walk_course` assigned (1-based,
  unique across the whole collection — same numbering rule a course uses)
- `episode` (caption `e`) = the number within that chapter
- `year`, `tmdb`/`tvdb`/`imdb` = `NULL`
- A PDF handout inside the same folder keeps `kind = "doc"` (unchanged, exactly
  like a course document) — it is not a `Docu`.
- Poster key: `title-{slug(show)}` (same key every episode in the collection
  shares, and the same key `mediagram artwork "Terra X" --poster …` resolves to)

**The slug function**: `crates/mlib-spec/src/slug.rs::slug(title: &str) -> String`.
Rules: keep lowercase ASCII alphanumerics; collapse any run of anything else
(spaces, punctuation) to a single `-`; trim leading/trailing `-`; drop non-ASCII
characters entirely (no transliteration) — a title of only non-ASCII text slugs
to `""`. This is the exact function `add-course` already uses for its default
collection id (`course::identity::collection_id`), and is now also what
`title_art_key` and `add-docu`'s collection `cid` use. An empty slug is refused
by `collection_id`/`course_title` flows (same as today) and by `title_art_key`
(returns `None`, so no artwork row is written and no poster key is derived for
such a title — a set with an unsluggable name/title simply gets no title-based
poster key at all, same as one with no TMDB id and no name today).

**Art keys produced**:
- TMDB-linked title (unchanged): `tmdb-{movie|tv}-{id}`, backdrop `…-bg`.
- No provider id, but a `Kind::Tut` or `Kind::Docu` set with a `show` or (for a
  standalone `Docu`) `title`: `title-{slug}`, backdrop `title-{slug}-bg`
  (`mlib_spec::package::title_art_key`). **A TMDB id always wins** when both
  would apply (mirrors `mediagram artwork`'s resolution).
- Any other kind with no provider id (a manually-entered movie/episode) gets
  **no** poster key at all — two such entries could share a title and collide
  on one key, so this is deliberately not extended to them.
- `poster_key_is_valid` grammar for the new shape: `title-<slug>[-bg]`, where
  `bg` may appear **at most once** and only as the trailing segment. Known,
  accepted narrow limitation: a title whose slug's last word is literally `bg`
  (e.g. a documentary titled "Terra BG") collides with the backdrop marker —
  not solved, on the same reasoning TMDB's numeric-id keys don't need to worry
  about it and this one, being free text, structurally can't fully rule it out
  without a second delimiter.

## Tests

- `crates/mlib-spec/tests/package_format.rs`: `title-…`/`title-…-bg` validity
  (accept/reject incl. the double-`bg` case), `title_art_key`.
- `crates/mlib-spec/src/schema_tests.rs`: generic (version count, readable range)
  — passes automatically with `V10` added; no dedicated per-table test needed
  beyond `index::artwork`'s round trip, which exercises the migration for real.
- `crates/mediagram/src/index/artwork_tests.rs`: put/get/clear round trip,
  invalid key, over-cap, mime-from-extension, unrecognised extension,
  `adopt_folder` (found / not found / case-insensitive).
- `crates/mediagram/src/index/merge_tests.rs`: artwork carried across a merge,
  never overwritten locally, tolerates a v9 channel snapshot.
- `crates/mediagram/tests/add_docu_plan.rs`: `docu_file` (title from name,
  `--title` override, no course), `lesson(Kind::Docu, …)` (grouped like a
  lesson), and a full folder-plan test (title/cid/two episodes/artwork keys)
  against a real temp directory and a real temp index.
- `crates/mediagram-core/tests/dto_mapping.rs`: `poster_key_for` for a course
  lesson, a standalone documentary, and confirms a manual movie still gets no key.
- `crates/mediagram-core/src/artwork.rs`: reads a stored image, missing key,
  and a snapshot with no `artwork` table at all.

`cargo test --workspace` and `cargo clippy --workspace --all-targets`: both
green, zero warnings.

## Deviations from the literal spec text

- Spec step 7 says "poster-key derivation (dto/summary.rs area) returns
  `title-{slug}` for a set with no tmdb id that has a course/collection name,
  and handles docu" — implemented as: applies to `Kind::Tut` and `Kind::Docu`
  only (not every kind), and falls back to the set's own `title` (not just
  `show`) so a standalone documentary — which has no `show` — still gets a key
  from its own title. This is the natural reading of "handles docu" given a
  standalone documentary carries no `show` at all.
- Added `index::merge_artwork` (channel-merge carrying for the new table),
  which isn't in the phase's numbered build list but mirrors v9's
  `merge_credits`/`merge_franchises` exactly and without it a second uploading
  machine's custom artwork would silently vanish on the next merge — the same
  failure class `CLAUDE.md`'s "two uploaders share the channel index" lesson
  already warns about.

## Status

**Status:** DONE
**Summary:** Schema v10 (`artwork` table), `title-{slug}` art keys, `Kind::Docu`,
`add-docu`, `mediagram artwork`, and Android/core `poster_path`/`poster_key_for`
support are all in, tested, and green (`cargo test --workspace`, `cargo clippy
--all-targets`, both clean). Only `crates/**` and `docs/**` touched.
**Concerns:** None blocking. Two judgment calls worth a second look from the
lead: (1) the docu-collection episode code letter is `E` (`C02E03`), invented
here since the spec didn't name one and `L`/`D` were taken; (2) the
title-slug/backdrop double-suffix edge case above is a real, if narrow,
ambiguity — flagging it rather than over-engineering a fix nobody asked for.
