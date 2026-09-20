# PDFs in a course

A course is not only video. Two shapes turn up on disk:

    03 Signal.mp4
    03 Signal.pdf          ← the handout for that lesson

    Ressourcen/
      Arbeitsbuch.pdf      ← a folder with no video in it at all

Today both are invisible. `walk_course` collects only `VIDEO_EXTENSIONS`
(`course/plan.rs:73`), and a folder holding no video never becomes a chapter
at all (`course/walk.rs:collect`), so the resources folder is not skipped —
it is never seen. Nothing in the run says a file was passed over.

The sidecar path cannot carry them either: `assets` is a TEXT column with a
1 MB cap (`index/assets.rs:14`) that rides the pinned index package. A PDF is
binary and often larger than the whole package budget.

## The shape

A document is **its own set**, not an extra part on a lesson's set. `parts`
are contiguous byte ranges of one file — the playable invariant is
`total = SUM(byte_length)` — so appending a PDF to a video's set would break
seeking for that video.

Given its own set, a document needs no "attachment" concept. It is numbered
within its chapter exactly the way a lesson is, and the player already
interleaves a course level by leading number (`library.js:levelEntries`). So
`03 Signal.pdf` lands next to `03 Signal.mp4` on the page for free, and
`Ressourcen/` becomes an ordinary folder holding three rows that download
instead of play.

    Kind::Doc  ·  container "pdf"  ·  cid/chap/path/s/e as a lesson carries them
    dur, vcodec, acodec, q, hdr: None   alang, slang: empty

## The hazard worth naming

`assign_unique_numbers` renumbers a whole group 1..n the moment declared
numbers collide, which they always do. If doc-only folders joined the chapter
numbering, every existing lesson's chapter number would shift — and chapter +
lesson **is** a lesson's identity (`sets::lesson_status`). A re-run of
`add-course` over a course already uploaded would then match nothing and
upload the entire course a second time.

So chapter numbers are computed from video-holding folders exactly as today,
untouched, and doc-only folders take numbers continuing after the last one. A
regression test pins this: the lessons a walk returns must be byte-identical
whether or not PDFs sit in the tree. Placement in the player comes from
`path`, not from the number, so nothing is lost by numbering them last.

## Phases

- [x] **01** `Kind::Doc` in `mlib-spec`, codec round-trip, `display_name`
- [x] **02** Walk documents: `walk_course` returns lessons *and* documents, lesson numbering provably unchanged
- [x] **03** `add-doc` upload path — no ffprobe, no remux — and `sets::doc_status` for re-run skip
- [x] **04** `add-course` uploads documents, dry-run table and summary count them
- [x] **05** Player: `pdf` content type, docs out of the movie shelf and into the course tree, a download row
- [x] **06** Docs — README, system-architecture, changelog

## Key decisions

**`.pdf` only.** The ask was PDFs. `DOC_EXTENSIONS` is a list so adding
`.epub` later is one word, but guessing at formats now means guessing at how
the player renders them too.

**No schema migration.** `kind` and `container` are TEXT, and every video-only
column is already nullable. Nothing in the DDL has to change, so
`SCHEMA_VERSION` stays 6 and no index in the wild needs migrating.

**The player ships before the first upload.** `groupLibrary` deliberately
shelves an unknown kind with the films, so a player that has not learned
`doc` would show a PDF as a broken movie. Phase 05 is not optional dressing;
it lands before any course with PDFs is added.

**A document is not in the playback order.** `flattenCollection` feeds "next",
so docs are excluded from it — reaching the end of a lesson must not
auto-advance into a workbook.

## Success criteria

- `add-course <dir> --dry-run` lists PDFs beside lessons, with their numbers
- A course with PDFs walks to the same lesson set as the same course without them
- `add-course` re-run after PDFs were added skips every lesson already complete
- A PDF uploads with no ffprobe or remux run against it
- The player shows `Ressourcen/` as a folder and its PDFs as download rows
- A lesson handout sits on the row next to its lesson, at the same number
- `/api/sets/<id>/stream` serves a PDF as `application/pdf`
- Films shelf holds no documents

## Unresolved

- Formats beyond `.pdf` (epub, zip, docx)?
- A standalone `mediagram add --doc <file>` for a PDF outside any course?
- Should a course's shelf-card count include documents, or lessons only?

## Review

All six phases landed. 731 Rust tests and 594 web tests pass, clippy clean,
`tsc --noEmit` clean for every file this touched.

Two things the work turned up that the plan did not foresee:

**The dry-run sorted handouts above their lessons.** Ordering rows by
`(number, mark)` put `D` before `L`, since `D` sorts first alphabetically —
so every handout appeared above the lesson it belongs to. Found by running
the command against a real tree rather than by a test. Fixed by sorting on
the role rather than the letter, and pinned by
`a_handout_is_listed_under_its_lesson_not_above_it`.

**The caption marker stays `v=4`.** Bumping it would have made old builds
refuse *every* new caption, not just documents. Left at v=4, so an old build
keeps reading movies, episodes and lessons and fails only on a document, with
`unknown variant 'doc'`. That only affects `rescan` run from an old binary.

The web test suite could not run at all before this: `teleproto` and
`@types/bun` were declared but not installed. `bun install --frozen-lockfile`
fixed it without touching `bun.lock`. Three pre-existing `tsc` errors remain
in `posters`, `series-summary` and `shows` tests — unrelated to this work,
confirmed by checking them against a clean tree.
