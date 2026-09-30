# Phase 09 — Remove the inline-subtitle read path (gated)

## Context links
- Inline fallbacks added by phases 03/04: web `web/src/catalog/subtitle-tracks.ts` (inline branch, `legacyBody`), `web/scripts/preview-subtitles.ts` (inline case); core `crates/mediagram-core/src/catalog_subtitles.rs` (inline branch, `legacy_body`); their tests.
- Merge guard from phase 02: `crates/mediagram/src/index/merge_subtitles.rs` step 3 and the `fill_missing_assets` exclusion — **kept**. Local `library.before-channel-merge-*.db` backups still hold inline rows, and restoring one must not bring them back.
- Red team: scope-critic F6, failure-mode F5.

## Overview
Priority P3. Effort 2h. Version: next **patch** (no behaviour change). Status: pending.
**Gate:**
- The channel follower copy shows `SELECT COUNT(*) FROM assets WHERE kind='subtitle'` = 0 for at least a week of pushes.
- Both uploaders are on ≥ phase 06, verified by the caption `schema` of each one's own push and by `mediagram --version` there.
- If the gate is not met by the time phase 08 closes, file a GitHub issue instead (`gh issue create`, label `ready-for-agent`).

## Requirements
- Web and core read subtitle tracks from `subtitle_tracks`/`subtitle_files` only; inline code and tests are deleted.
- The `/api/sets` shape is unchanged; an index with inline rows and no bundles lists no subtitles (acceptable after the gate).

## Related code files
- Modify: `web/src/catalog/subtitle-tracks.ts`, `web/test/subtitle-tracks.test.ts`, `web/scripts/preview-subtitles.ts`, `crates/mediagram-core/src/catalog_subtitles.rs`, `catalog_subtitles_tests.rs`, `crates/mediagram-core/src/api/subtitles.rs` (legacy branch), `docs/web-player.md`, `docs/system-architecture.md`, `docs/project-changelog.md`.

## Implementation steps
1. Confirm the gate; paste the query result and both versions into `reports/rollout-log.md`.
2. Delete the inline branches and their tests on both surfaces.
3. `scripts/check.sh`, `bun test`; rebuild `.so` + bindings; bump (patch); changelog.
4. Deploy: restart the web player; install tablet + TV box.

## Todo
- [ ] gate confirmed (or issue filed)
- [ ] web inline branch removed
- [ ] core inline branch removed; bindings/.so rebuilt
- [ ] checks, manifests, changelog, deployed

## Success criteria
- Tests green; a lesson and a film still show their tracks on web (API), tablet and TV box.

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| A stale push brings inline rows back after removal | Low × Med | Gate needs both uploaders ≥ 06; merge guard kept; phase 02's alarm re-publishes |

## Security
Less code reading channel data; nothing new.

## Rollback
Revert the commit and redeploy; the inline rows are gone anyway, so nothing changes for viewers.

## Next
None.
