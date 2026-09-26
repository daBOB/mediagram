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
- [x] architecture + running docs
- [x] changelog
- [x] parity paragraph
- [x] version bump ×3 (deliberately skipped, see notes)
- [x] final test run

## Implementation notes (2026-09-26)

Done, with one deliberate deviation from the plan and one correction to it.

- **Version bump skipped, on explicit instruction from the dispatching
  task**, which overrides this phase's own step 2: "the lead does that at
  merge." All three manifests already agree at `0.59.0` — verified with
  `grep -n version Cargo.toml web/package.json android/app/build.gradle.kts`
  — so there is nothing to reconcile before that bump happens; `versionCode`
  (16) is untouched, as the rule requires regardless.
- **Docs land in `docs/web-player.md`, not `docs/system-architecture.md`.**
  The plan's own context links cite `docs/system-architecture.md:333-345`
  for the `/api/status` section, but that section does not exist there —
  §7 "Playback: the web player" is one paragraph that points to
  `docs/web-player.md` for exactly this ("the System page ... are in
  docs/web-player.md"), which already carried the pre-phase-1 description
  under "Saying what it is doing". Extended that section in place — four new
  groups, the POST contract, the invoke-latency rationale, ffmpeg's minimum
  version — and added a "Differences from Android" subsection there, since
  it is where a reader already lands for this exact page.
- Changelog entry is at the top of `docs/project-changelog.md`, under
  `## Unreleased — system stats`, ahead of the existing `## Unreleased —
  0.59.0` entry (a different, already-merged change) — per this task's
  explicit instruction, not the plan's own step, which assumed a version
  bump would happen in this phase and the changelog would carry that number.
- ffmpeg's minimum version note landed in `docs/running-the-player.md`,
  beside the direct-play/convert table, matching the plan's own citation.
- No separate Android/core follow-up plan stub was opened. Plan question 1
  asks whether the owed items (per-DC link stats, host memory/disk) should
  be their own plan now or scheduled later; that is a scope decision for the
  user, not something to resolve by writing a stub plan unasked. The
  "Owed to Android" record itself is written in both
  `docs/web-player.md` and `docs/project-changelog.md`, so the debt is not
  silent regardless of when a plan for it exists.

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
