# Phase 6: Docs, version, parity record

## Context links
- `CLAUDE.md` § Surface Parity, § Versioning
- `docs/system-architecture.md:333-345` (`/api/status` section), `docs/project-changelog.md`, `docs/running-the-player.md`
- Android: `android/ui-mobile/src/main/kotlin/SystemScreen.kt`, `SystemRows.kt`, `PlaybackStatsOverlay.kt`, `android/feature/system/src/main/kotlin/SystemUiState.kt`
- Precedent: `docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md:21,292`
- `tasks/lessons.md` (2026-09-18): when two implementations make the same decision, grep the other one. Rust `serve/` has no `/api/status`, verified with `git grep "api/status"`, so there is nothing to mirror there

## Overview
Priority P2 · pending. Record what was built, bump the version, and write down the parity position so the difference is deliberate and not silent.

## Parity analysis (verified 2026-09-22)
| Group | Android today | Verdict |
|---|---|---|
| Telegram link | System screen shows `connected`, `fetches` and `failedReads` (`SystemUiState.kt`) | **Owed**: per-DC counts, latency, flood and reconnects. They belong in the Rust core's grammers byte path, so a separate Android/core plan |
| Playback session | `PlaybackStatsOverlay.kt` on the player: codec, buffer, cache, reads, dropped | **Covered on the device itself.** Web "Watching now" is a cross-device view that only a server can hold. Android has no server: a deliberate difference |
| Transcoder | none | **Deliberate difference.** Android decodes natively, and nothing transcodes (constraint in the Android system-menu plan) |
| Process/host | `versionName`, `uptimeSeconds` | **Owed**: memory and free disk under the cache dir. Runtime version = `versionName` (already there) |

## Requirements
- Update the `/api/status` section of `docs/system-architecture.md` with the four groups, the `POST /api/status/playback` contract, the 15s TTL, and why latency is measured around `invoke`.
- `docs/running-the-player.md`: the minimum ffmpeg version for `-stats_period` (≥ 4.4).
- Changelog entry.
- Parity: add a "Differences from Android" paragraph to the architecture section, using the table above. Open an **unscheduled** follow-up plan stub for the owed items only if the user agrees (plan.md question 1).
- Version: a minor bump, applied in step to `Cargo.toml` (workspace), `web/package.json` and `android/app/build.gradle.kts` `versionName`. Base it on whatever `main` holds at implementation time; the uncommitted working tree currently shows web `0.22.0`. `versionCode` is untouched.

## Related code files
- Modify: `docs/system-architecture.md`, `docs/running-the-player.md`, `docs/project-changelog.md`, `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`
- Create/Delete: none

## Implementation steps
1. Docs updates (delegate to `docs-manager`).
2. Version bump in all three manifests. Check with `grep -n version` in each.
3. Run the full `bun test`, `bunx tsc --noEmit` and `cargo check` (the Cargo version changed).
4. Commit per phase with conventional messages. No plan references in any commit.

## Todo
- [ ] architecture + running docs
- [ ] changelog
- [ ] parity paragraph
- [ ] version bump ×3
- [ ] final test run

## Success criteria
- The three manifests carry one version.
- The docs describe every new field.
- The parity differences are written where a reader of the architecture doc will find them.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| The version collides with uncommitted work on the branch | M×L | Bump at commit time against `main`'s value |

## Security
No secrets in docs. The playback contract section says it answers the household's own network only.

## Next steps
Android parity plan (links and host), if the user approves it.
