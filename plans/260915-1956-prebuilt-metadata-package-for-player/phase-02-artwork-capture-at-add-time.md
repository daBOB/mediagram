---
phase: 2
title: "Artwork capture at add time"
status: pending
priority: P1
effort: "1d"
dependencies: []
---

# Phase 2: Artwork capture at add time

## Overview
Collect a poster (and, where available, a backdrop) for every set into a
local art cache, so the export has images to package. Capture happens when
`add` runs, because the source video is deleted from the index's knowledge
the moment a set completes.

## Key insight
`add` stores the source path in `meta` under `source:{set_id}` and deletes
that key on completion (`commands/add.rs`, `upload/pipeline.rs`). By export
time there is no path to the file. Anything that must be derived from the
video itself, such as a representative frame, is therefore captured during
`add` or never.

TMDB artwork is different: it can be fetched at any time from the stored
`tmdb` id, so it is fetched lazily during export and cached on disk.

## Requirements
- Functional: an art cache under the data directory, keyed so the export
  can find the right image for a set without guessing; a captured frame for
  sets with no provider id; TMDB `poster_path`/`backdrop_path` parsed and
  recorded so the export knows what to fetch.
- Non-functional: a failure to produce art never fails an upload. Art is a
  convenience; the library is the product.

## Architecture
```
~/.local/share/mediagram/art/
  tmdb-693134-poster.jpg        movies and episodes, fetched at export
  tmdb-693134-backdrop.jpg
  set-01JQ8F2K9M4XZ-poster.jpg  captured frame, written during add
  cid-rust-course-2024-poster.jpg  course cover, written during add-course
```

Art keys, in priority order for a given set:

| Key | Source | When |
|---|---|---|
| `cid-{cid}` | course cover image in the folder, else frame from the first lesson | `add-course`, once per course |
| `tmdb-{id}` | TMDB `poster_path` / `backdrop_path` | export, cached |
| `set-{set_id}` | ffmpeg frame from the source file | `add`, when no provider id |

A set resolves to the first key that exists, so a course shows one cover
rather than one image per lesson, and a movie shows its TMDB poster.

## Related Code Files
- Create: `crates/mediagram/src/art/mod.rs` (cache paths, key resolution),
  `crates/mediagram/src/art/capture.rs` (ffmpeg frame extraction)
- Modify: `crates/mediagram/src/metadata/tmdb_types.rs` (add
  `poster_path`, `backdrop_path`), `crates/mediagram/src/commands/add.rs`
  (capture when no provider id), `crates/mlib-spec/src/schema.rs` and
  `crates/mediagram/src/index/set_row.rs` (store the chosen art key)

## Implementation Steps
1. Add `art_key TEXT` to `sets` as a schema migration, holding the key
   chosen for that set. Recording it avoids re-deriving priority rules in
   two places and lets the export build its art map with one query.
2. Write `art::key_for(set)` implementing the priority table above.
3. Implement `art::capture::frame(source, dest)`: ffmpeg seeks to ten
   percent of duration and writes one JPEG, scaled so the long edge is at
   most 1000 pixels. Reuse the subprocess pattern in `media::remux`.
4. Call the capture from `add` after a successful upload, only when the set
   has no provider id, wrapped so any failure is a warning.
5. Parse `poster_path` and `backdrop_path` from TMDB responses and store
   them on the set row for the export to resolve without a second search.
6. For `add-course` (once it exists): look for `poster.jpg`, `cover.jpg` or
   `folder.jpg` in the course root; otherwise capture from the first lesson.
   Guard this behind existence of the command so the phases stay orderable.

## Success Criteria
- [ ] A movie added with a TMDB id records a `tmdb-*` art key
- [ ] A file added with `--manual` produces a captured JPEG on disk
- [ ] ffmpeg failing, or being absent, logs a warning and leaves the upload successful
- [ ] The captured frame is a reasonable image, not a black or letterbox-only frame, for a sample of fixtures
- [ ] Art cache paths never escape the data directory, whatever the title contains

## Risk Assessment
- A frame at ten percent can land on a black title card. Mitigation: if the
  extracted frame is almost uniformly black, retry once at thirty percent,
  then accept it. Cheap and bounded.
- Titles flow into filenames. Mitigation: keys are ids and slugs only,
  never raw titles, so path traversal has no surface.
- Art cache growth is unbounded over time. Mitigation: the export reports
  cache size; pruning is out of scope for v1.
