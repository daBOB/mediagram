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
- Every source file stays **strictly under 200 lines**. 200 exactly is over.
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
| 1 | [One row mapping, in the crate both sides share](phase-01-shared-row-mapping.md) | Not started |
| 2 | [Somewhere to keep what the phone learns](phase-02-the-details-sidecar.md) | Not started |
| 3 | [One action, both gaps](phase-03-one-action-both-gaps.md) | Not started |
| 4 | [The phone asks](phase-04-the-phone-asks.md) | Not started |

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

_Filled in as phases complete._
