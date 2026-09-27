# Phase 04: callers, CLI, docs, version

## Context
Callers of `pull_index::merge_and_publish` / `pull_index::pull`:
`commands/{push_index,sync_index,finish_set,resume,add_course}.rs`,
`commands/add_show/mod.rs:120`, `commands/add_docu/{mod.rs:72,collection.rs:108}`.
Docs: `README.md:204-225`, `docs/system-architecture.md` (module map, §3–4),
`crates/mediagram/src/commands/sync_index.rs` doc comment.

## Overview
Priority high. Switch every caller to `ChannelIndex`, shrink `push-index`,
delete the old modules, update docs, bump the version.

## Requirements
- `push-index [--force]`; `--merge` and `--check` removed.
- `pull-index [--dry-run]` unchanged for users.
- `sync-index` keeps its four steps; its final publish now downloads nothing
  when the channel did not move during steps 2–3.
- The six post-upload commands publish through one call each (cadence
  unchanged until candidate A).
- `commands/mod.rs:3-4` rule holds: no command imports another command's logic.

## Steps
1. Command-facing helper that connects, builds the Telegram adapter, runs, shuts down.
2. Rewrite callers; delete `telegram/{index_publish,index_guard,download_index,unpin}.rs`
   and `commands/pull_index/{backup_path,conflicts,keep_live}.rs`.
3. README + `docs/system-architecture.md`; changelog entry.
4. Version bump in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`
   (0.67.0). Bump by pattern, not exact string: another session edits these files.
5. `cargo test --workspace`, `cargo clippy --workspace`, code-standards test.

## Todo
- [x] callers switched
- [x] old modules deleted
- [x] docs + changelog
- [x] version bumped in all three manifests
- [x] workspace tests + clippy green

## Resolved
Version **0.67.0**: while at 0.x a breaking change bumps minor (user decision
2026-09-27, recorded in `CLAUDE.md` § Versioning).

## Risks
Merge conflicts with the other session on the three manifests and
`docs/project-changelog.md`. Mitigation: branch in a worktree, rebase before merging.
