---
title: "Tutorial/course support (mlib caption v3)"
date: 2026-09-15
status: approved
supersedes: none
affects: mlib-spec (caption, schema, part_name, filename), mediagram (add, add-course, rescan, index)
---

# Tutorial/course support

Adds a third content kind to mediagram: a **course** made of **chapters**,
each chapter holding several **lessons**. One lesson is one video file and
therefore one mlib set, exactly as one TV episode is today.

"Part" keeps its existing meaning throughout: a 3.5 GiB byte-slice of one
file. A chapter's lessons are never called parts.

## 1. Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Lesson = one video file = one set | Matches how downloaded courses are laid out; reuses the whole upload path unchanged |
| 2 | New kind `t:"tut"`, reusing `show`/`title`/`s`/`e` | Course → `show`, lesson title → `title`, chapter no → `s`, lesson no → `e`; ordering, resume, dedup and the playable invariant keep working with no new code paths |
| 3 | Two new caption fields: `cid`, `chap` | `chap` carries the chapter title (no existing field fits); `cid` is a stable grouping anchor, see decision 4 |
| 4 | `cid` is a general **collection id**, not course-specific | Survives renaming the course, distinguishes two courses sharing a title, and generalizes later to any set that needs grouping without a provider id (a trilogy, a series TMDB lacks) |
| 5 | Caption marker bumps to `v=3`; parser accepts `v=2` and `v=3` | The versioning section of the spec already prescribes this; nothing already uploaded is rewritten |
| 6 | All new captions are written as `v=3`, including movies and episodes | One encoder, not two; the two new fields serialize as `null` for non-tutorials |
| 7 | `library.db` `SCHEMA_VERSION` 1 → 2, adding `sets.chap`; `sets.group_key` holds `cid` | `group_key` is already declared and never populated; this is its intended use |
| 8 | Migrations become version-gated | The current flat replayed list cannot express `ALTER TABLE`; see §4 |
| 9 | Identity for re-runs is `cid` + chapter no + lesson no | Explicit anchors over fuzzy title matching, per the plan's locked decisions; folder moves and renames do not break it |
| 10 | Tutorials never contact TMDB | TMDB has no courses; the `tmdb_key` guard must not fire on this path |
| 11 | Lesson file names use `c02l02`, not `s02e02` | Names are a human convenience only; a course should not read as a TV series in the channel's file list |
| 12 | `#mlib-index` marker stays at `v=2` | It versions the index caption's own shape, which is unchanged; the `schema` value inside it goes to `2` |

## 2. Caption wire format (v3)

### Kind

```rust
pub enum Kind { Movie, Ep, Tut }   // serde rename_all = "lowercase" → "tut"
```

### Field order

Field order is wire order and is asserted byte-for-byte by
`caption_roundtrip::exact_wire_format_is_stable`. Two fields are inserted:

| Position | Field | Type | Meaning |
|---|---|---|---|
| after `ids` | `cid` | `Option<String>` | Collection id: stable grouping anchor. Set for tutorials, `null` otherwise |
| after `show` | `chap` | `Option<String>` | Chapter title. Set for tutorials that have one, `null` otherwise |

Full v3 order:

```
t, ids, cid, show, chap, title, year, s, e, abs, q, hdr, container,
vcodec, acodec, alang, slang, dur, variant, set, part, total
```

### Tutorial field mapping

| Concept | Field | Notes |
|---|---|---|
| Course title | `show` | Required for `t:"tut"` |
| Collection id | `cid` | Required for `t:"tut"`; slug of the course title unless overridden |
| Chapter number | `s` | Required; a course with no chapters uses `1` |
| Chapter title | `chap` | Optional |
| Lesson number | `e` | Required, always `Episode::Single`; ranges are not used for lessons |
| Lesson title | `title` | Optional |
| Course year | `year` | Optional (e.g. a 2024 edition) |

Example (line 2, minified as always):

```json
{"t":"tut","ids":{"tmdb":null,"tvdb":null,"imdb":null},"cid":"rust-course-2024","show":"Rust Course","chap":"Ownership","title":"Borrowing","year":2024,"s":2,"e":2,"abs":null,"q":"1080p","hdr":"SDR","container":"mp4","vcodec":"h264","acodec":"aac","alang":["en"],"slang":["en"],"dur":612,"variant":null,"set":"01JQ8F2K9M4XZ","part":{"i":0,"n":1,"off":0,"len":412336102,"sha256":"…"},"total":412336102}
```

### Compatibility

- Decoding: `cid` and `chap` are `#[serde(default)]`, so a `v=2` caption
  parses into the v3 struct with both `None`.
- The decoder accepts markers `#mlib v=2` and `#mlib v=3`, and rejects
  anything else, as the versioning rule requires.
- Because `serde` ignores unknown fields by default, a parser built against
  v2 also reads a v3 caption, losing only the two new fields. This is a
  convenience, not a guarantee: readers should accept versions explicitly.
- Budget is unaffected in practice. Measured against the reference example:

| Measure | UTF-16 units |
|---|---|
| v2 reference caption | 411 |
| Budget | 1024 |
| `cid` + `chap` populated | under 60 |

## 3. Naming

`base_name` gains a `Kind::Tut` arm:

```
{course}[ ({year})] - c{chapter:02}l{lesson:02}[ - {lesson title}]
```

Truncation is the existing word-boundary truncation to 60 characters
including the `.ext` / `.pNNN` suffix, so the lesson title is what drops
first. Example: `Rust Course (2024) - c02l02 - Borrowing.mp4`.

The fallback filename grammar (`filename.rs`, used only for files that
arrive with no caption) learns the `c<N>l<N>` code, mapping chapter to
`Guess::season` and lesson to `Guess::episode` so `Guess` gains no fields.

## 4. Index schema and migrations

`SCHEMA_VERSION` goes to `2`. The migration list becomes version-gated:

```rust
/// Statements to apply to reach each version. Index i holds the statements
/// that take a database from version i to version i+1.
pub const MIGRATIONS: &[&[&str]] = &[
    &[ /* v0 → v1: the existing CREATE TABLE statements, unchanged */ ],
    &[ "ALTER TABLE sets ADD COLUMN chap TEXT" ],   // v1 → v2
];
```

`db::open` reads `meta.schema_version` (absent means 0), applies every
group above it in order inside one transaction, and records the new
version. A fresh database therefore runs v1 then v2 and reaches exactly the
same layout as an upgraded one, so there is one definition of the schema
rather than two.

`sets.group_key` stores `cid`. No other column changes; `season` and
`episode` carry chapter and lesson, `episode` keeping its existing JSON
encoding (`"2"`).

`SetRow::from_caption` is the single mapping point and gains: the `Tut`
arm for `kind`, `group_key: caption.cid.clone()`, and `chap`. This makes
`rescan` recover courses correctly with no extra work, which is the whole
reason `cid` lives in the caption.

## 5. CLI

### `add` (existing command, new flags)

| Flag | Meaning |
|---|---|
| `--course <title>` | Marks the set as a tutorial and sets the course title |
| `--cid <id>` | Collection id; defaults to the slug of the course title |
| `--chapter <n>` | Chapter number (default `1`) |
| `--chap <title>` | Chapter title |
| `--lesson <n>` | Lesson number |

`--course` selects `t:"tut"` and bypasses TMDB resolution entirely, so the
`tmdb_key` guard does not apply. `--tmdb`/`--tvdb`/`--imdb` are rejected
with `--course`: a course has no provider id in v1.

### `add-course` (new command)

```
mediagram add-course <dir> [--course <title>] [--cid <id>] [--dry-run]
                           [--no-push] [--variant <label>]
```

Walk rules:

1. Each immediate subdirectory of `<dir>` is a chapter; video files
   directly inside `<dir>` form chapter 1 when there are no subdirectories.
   Nesting deeper than one level is walked, and every file is attributed to
   the top-level subdirectory that contains it, so a chapter split into
   sub-folders still yields one chapter.
2. A leading integer in a directory or file name is its number; the
   remainder, with separators (`-`, `_`, `.`) and the extension stripped and
   whitespace collapsed, is its title.
3. Entries with no leading integer are numbered in name order, continuing
   from the highest explicit number among their siblings. Ordering is by
   number, then by name, so two walks of the same tree always agree.
4. Only known video extensions are considered: mkv, mp4, m4v, webm, mov,
   avi, ts. Everything else (subtitles, resources, archives) is ignored and
   counted in the summary.
5. Course title defaults to the directory name; `cid` defaults to its slug
   (lowercase, ASCII alphanumerics and `-`, runs collapsed, trimmed).

Behaviour:

- Every lesson is inspected, classified and remuxed exactly as `add` does
  it, so quality, HDR, codecs, languages and duration are detected per
  lesson rather than declared for the course. `--variant` is the one label
  applied to every lesson in the walk.
- `--dry-run` prints the chapter/lesson/file/title table plus totals and
  exits without touching the network.
- A lesson whose identity (`cid`, chapter, lesson) already has a
  **complete** set is skipped.
- A lesson whose identity has an **incomplete** set is skipped with a
  warning naming `resume`; `add-course` never duplicates resume's logic.
- A lesson that fails is reported and the walk continues. The final summary
  lists uploaded, skipped, and failed counts, and the exit code is non-zero
  if any lesson failed.
- The index is pushed once after the walk, not per lesson, matching the
  locked rule that `--no-push` exists for explicit bulk sessions.
  `--no-push` suppresses even that final push.

## 6. Module layout

New and changed files, all within the 200-line rule:

| File | Change |
|---|---|
| `crates/mlib-spec/src/caption.rs` | `Kind::Tut`, `cid`, `chap` |
| `crates/mlib-spec/src/caption_codec.rs` | Emit `v=3`, accept `v=2` and `v=3` |
| `crates/mlib-spec/src/lib.rs` | `SPEC_VERSION = 3` |
| `crates/mlib-spec/src/part_name.rs` | `Kind::Tut` arm |
| `crates/mlib-spec/src/filename.rs` | `c<N>l<N>` code |
| `crates/mlib-spec/src/schema.rs` | `SCHEMA_VERSION = 2`, version-gated `MIGRATIONS` |
| `crates/mlib-spec/src/slug.rs` | **new**: the slug function. It lives in the spec crate because §5 makes it the documented default derivation of `cid`, so any client that generates a collection id derives the same one |
| `crates/mediagram/src/index/db.rs` | Version-gated migration runner |
| `crates/mediagram/src/index/set_row.rs` | `Tut` arm, `group_key`, `chap` |
| `crates/mediagram/src/index/sets.rs` | `chap` column in read/write; identity lookup query |
| `crates/mediagram/src/commands/args.rs` | New `add` flags, `AddCourseArgs` |
| `crates/mediagram/src/commands/add.rs` | Tutorial path skips TMDB |
| `crates/mediagram/src/commands/add_course.rs` | **new**: orchestration only |
| `crates/mediagram/src/course/walk.rs` | **new**: directory → lessons, pure apart from reading the tree |
| `crates/mediagram/src/course/plan.rs` | **new**: numbering, ordering, titles; pure |
| `crates/mediagram/src/course/report.rs` | **new**: dry-run table and final summary; pure |
| `crates/mediagram/src/main.rs` | Wire `add-course` |

The `course::` modules follow the `verify::` split that phase 7 settled on:
pure decision logic separated from the one module that performs IO.

## 7. Testing

- **Wire format**: v3 round-trip with both new fields set and both null; a
  stored v2 caption still decodes, with `cid`/`chap` as `None`; byte-exact
  v3 serialization; budget check with a long course and chapter title.
- **Naming**: `Kind::Tut` base names with and without year and lesson
  title; truncation at the 60-character limit; `c<N>l<N>` parsed back by
  the fallback grammar.
- **Migration**: a database created at v1 gains `chap` and reports version
  2; a fresh database reaches the identical layout; running `open` twice is
  a no-op.
- **Walk and plan** (pure, over temp directory trees): nested and flat
  layouts, missing numbers, duplicate numbers, non-video files, unicode and
  very long titles, deterministic ordering, deep nesting.
- **Identity**: skip on complete, warn on incomplete, upload otherwise.
- **Rescan**: a `t:"tut"` caption rebuilds `group_key` and `chap`.
- All new tests are offline. The live gate is unchanged and still requires
  a real channel.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Spec bump breaks an existing reader | No third-party reader exists; the Android client is unbuilt. Parser accepts both versions |
| Migration damages an existing `library.db` | No `library.db` exists on the target machine yet (only `session.sqlite`). Migrations run in one transaction and are gated by recorded version |
| Walker misnumbers a messy course | `--dry-run` shows every inferred number and title before anything uploads |
| A course renumbered upstream re-uploads lessons | Documented consequence of identity by number. Re-downloading a course with different numbering is a new course; use a new `cid` or accept duplicates |
| Two new fields cost caption budget | Measured at under 60 of 613 spare units |

## 9. Out of scope for v1

- Fetching course metadata from any provider.
- A manifest file for hand-editing an inferred course (rejected in favour
  of the dry-run table plus per-lesson `add` flags).
- Chapter-level or course-level sets: only lessons are uploaded.
- Renumbering or re-captioning already-uploaded lessons.

## 10. Unresolved questions

None. Decisions 1-12 were settled in the brainstorming session on
2026-09-15.
