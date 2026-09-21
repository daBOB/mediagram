# The phone fetches its own details — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One menu action fills in everything the index left out — synopses and
artwork both — so the phone stops depending on whoever last ran
`mediagram metadata` before pushing.

**Architecture:** The index the phone downloads already carries provider ids
(324 sets with a TMDB id, 28 with TVDB) and already records which language it
was described in (`shows.lang`, `de-DE`). The core already fetches posters with
a TMDB key it holds. So this is not new capability: it is the same fetch, asked
one question further, and a place to keep the answer that a refresh cannot
delete.

**Tech Stack:** Rust (`mediagram-tmdb`, `mlib-spec`, rusqlite, UniFFI) ·
Kotlin, Jetpack Compose, Material 3, Hilt.

**Spec:** none. This is a bounded extension of
[`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md`](../../docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md)
§6, whose poster pipeline it follows step for step. Where this plan and that
spec disagree, the plan is the later word and says so in the phase that
diverges.

## Global Constraints

- Worktree `mediagram-android`, branch `android-fetches-details`, forked from `main` at `b5775ea`.
- Every file this work **creates or grows** stays **strictly under 200 lines**. 200 exactly is over.
  Two core files are already past it and neither is this plan's to fix:
  `crates/mediagram-core/src/api/mod.rs` (286 at the fork) and
  `crates/mediagram-core/src/api/library.rs` (307). Adding a line to `mod.rs` is
  allowed where the alternative is an arbitrary split; adding a function is not.
  Report their counts, do not chase them.
- **Fetched rows are never written into the downloaded index.** `catalog.rs`
  opens it read-only on purpose, and a refresh deletes the version directory
  wholesale — that is what destroyed every fetched poster before `0ce5fd2`.
- **Anything the phone fetches lives beside the version directories, inside
  `catalog/`**: out of reach of `install_staged` and `remove_other_versions`,
  inside what `FileCoreStorage.clear()` deletes. Both halves matter; naming
  only one is how the poster defect happened.
- `mlib_spec::schema` is the only place the `shows` DDL is written. The sidecar
  is created from `migrations_up_to`, not from a second copy.
- The TMDB key is never logged, never written to a report, and never appears in
  a `CoreError`. A rejected key surfaces as `NotAuthorized`.
- Live TMDB is never called from a test. CI has no key and no channel.
- Rust edition `2024`, rust-version `1.87`. `reqwest` stays `0.13.5`.
- Media3 `1.10.1`, Compose BOM `2026.06.01`, Material3 `1.4.0`, minSdk `24`,
  targetSdk `37`, AGP `9.3.1`, Kotlin `2.3.21`. Pins, not floors.
- **No version bump on the branch.** Compute it at the merge, against `main` as
  it actually stands — a version reasoned from a branch's own manifests is a
  guess about where main will be, and that guess was wrong last time.
- Any task touching `mediagram-core` must also compile it alone
  (`cargo build -p mediagram-core`); a workspace run masks a class of defect
  that has already cost this project a round.
- Conventional commits. No AI references. Code comments, test names and commit
  messages carry no plan references — they name the invariant, not its origin.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [One row mapping, in the crate both sides share](phase-01-shared-row-mapping.md) | Complete |
| 2 | [Somewhere to keep what the phone learns](phase-02-the-details-sidecar.md) | Complete |
| 3 | [One action, both gaps](phase-03-one-action-both-gaps.md) | Complete |
| 4 | [The phone asks](phase-04-the-phone-asks.md) | Complete |

## Key dependencies

Phase 1 is a move and changes no behaviour; it exists so the core and the
uploader read one copy of the mapping rather than two that can drift.

Phase 2 is the gate. It decides where fetched rows live and how they are read
back, and phases 3 and 4 are both assembly on top of it.

Phase 3 needs 1 and 2. Phase 4 needs 3.

## What is already there, and must not be rebuilt

- `mediagram_tmdb::details::details(api, kind, id)` — the request itself.
- `mlib_spec::schema` — the `shows` DDL and `migrations_up_to`.
- `api::artwork::plan_fetch` — resolves the current directory once, reads
  `library.db`, and splits titles into askable and countable.
- `api::artwork::split_titles` — already `pub`, already the classification.
- `catalog::artwork_dir` — the boundary this plan's sidecar sits on.

## Out of scope

Rescanning and pushing an index. Both are authoring operations that belong on
the machine holding `library.db`; a phone has neither that database nor the
session that wrote it. Also out of scope: re-fetching text that an index
already carries — the publisher's own description wins, and the phone only
fills gaps.

## Review

**Delivered.** One menu action, `Fetch details and artwork`, fills in both the
synopses and the artwork an index left out, asking TMDB in the language the
library itself records. Proved on a real device: a series that had a title and
episode list and nothing else came back with a poster, genres, a rating, a
tagline and a German synopsis — while the fallback language passed in was the
phone's own `en-GB`. The core read `de-DE` off the index and ignored it.

**The boundary held.** After a full `Refresh library` the fetched descriptions
are identical and still there. That was the question four phases rested on,
and it is the defect that destroyed every fetched poster before this work
started, not repeated.

**Accepted deviations**, each recorded in its phase file: the fetch walks the
title list twice rather than once, with `tests/fetch_cache.rs` counting real
provider requests to pin the property that makes it safe; and phase 2's store
shipped `pub` rather than `pub(super)` so integration tests can reach it.

**Follow-ups, none blocking.** Freshly fetched *artwork* still waits for the
next reload — descriptions do not, because they are read live. Reusing
`reload()` would be wrong, not merely heavy: a refresh failure would paint
"could not reach the channel" over shelves the fetch had just filled, and a
successful one could install a newer index as a side effect of asking for a
poster. The lighter path is a `regroup()` beside `reload()`, about fifteen
lines. Also open: a first run's answers are permanent, since an all-`NULL` row
counts as already-known and nothing offers to ask again; a `tut` or `doc` set
carrying a TMDB id now reports as a failure; `toLanguageTag()` can produce
tags TMDB will not accept on exotic locales; and `split_titles` prefers `show`
over `set_id` for a film, which is backwards for the parity it claims.
