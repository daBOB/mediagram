# Channel index module (architecture review candidate B)

Give pulling and publishing the channel index one deep module,
`crates/mediagram/src/channel_index/`, behind a Telegram port with two
adapters (Telegram, in-memory). Terms: `CONTEXT.md` (Channel index, Local
index, Pull, Publish).

Source: architecture review 2026-09-27, candidate B; decisions from the
grilling session below. Work in a worktree (`refactor/channel-index`) because
another session is committing to `main`.

## Decisions (user-confirmed 2026-09-27)

| # | Decision |
|---|----------|
| Q1 | Module owns the whole round trip: pull, merge, re-check, send, pin, unpin, pin bookkeeping |
| Q2 | Named `channel_index` |
| Q3 | One publish path: every publish pulls first; `Force` is the only other mode |
| Q4 | Port with a Telegram adapter and an in-memory channel for tests |
| Q5 | Machine-local publish lock (`publish.lock`, flock) around the round trip |
| Q6 | Publish cadence stays with callers (settled under candidate A) |
| Q7 | Local index records which channel index it last pulled; before sending, re-list pins; pull again if it moved (max 3), else no download |
| Q8 | "Which pinned index is current" moves to `mlib-spec`; core and uploader share it |
| Q9 | `push-index` keeps only `--force`; `--merge` and `--check` removed (`pull-index --dry-run` replaces `--check`) |
| Q10 | Interface: `ChannelIndex::new(remote, data_dir)`, `pull(dry_run)`, `publish(Mode)`; callers stop seeing `Tg`, `Guard` |
| Q11 | Eight interface tests (phase 2) |
| Q12 | Build B before grilling candidate A |

Version: 0.67.0 (0.x breaking changes bump minor; CLAUDE.md § Versioning updated).

Status reconciled 2026-10-04: completed — shipped 0.67.0 (`e3a8db50`).

## Phases

| Phase | Status |
|-------|--------|
| [01 Spec rule for the current channel index](phase-01-spec-rule-for-current-channel-index.md) | done |
| [02 Port, in-memory channel, failing interface tests](phase-02-port-in-memory-channel-and-interface-tests.md) | done |
| [03 channel_index implementation](phase-03-channel-index-implementation.md) | done |
| [04 Callers, CLI, docs, version](phase-04-callers-cli-docs-and-version.md) | done (reviewed: `plans/reports/code-reviewer-260927-0215-channel-index-module-review-report.md`) |

## Dependencies

01 → 03 (pick rule), 02 → 03 (tests drive it), 03 → 04.
Candidate A (upload session) builds on `ChannelIndex::publish`.
