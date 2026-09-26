# Phase 09 — Docs, parity note, versions, validation

## Context links
- `CLAUDE.md` § Surface Parity, § Versioning; phase 10 (sessions) is covered by the same parity note (three manifests in step; `versionCode` +1 separately)
- `Cargo.toml:6`, `web/package.json` (`version`), `android/app/build.gradle.kts:12-13`
- `docs/system-architecture.md` §7 (web), §8 (Android), §10 (on-disk layout); `docs/running-the-player.md`;
  `docs/project-changelog.md`; `docs/development-roadmap.md`

## Overview
Priority P2. Status: **done 2026-09-26**, except the version bump: the lead bumps `Cargo.toml`,
`web/package.json` and `android/app/build.gradle.kts` at merge, not this phase (explicit
instruction for this round). Documented instead: `docs/web-player.md` (new "Settings" section:
admin gate, `TelegramConnection`, restart ordering, channel retarget, sign-in's separate client,
sessions, the parity note), `docs/system-architecture.md` §10 (on-disk layout: `telegram.json`,
`admin-token`, `state.db`'s `settings` table, the per-channel catalog directory), and
`docs/running-the-player.md` (a "Settings" section; "Revoking access" gained the sessions list;
the example systemd unit's `ReadWritePaths` gained `~/.local/share/mediagram-player` and
`~/.cache/mediagram-channel-catalog`, a pre-existing gap for `state.db` this pass happened to
touch). Changelog entry at the top of `docs/project-changelog.md` under "## Unreleased —
settings", per the task's instruction rather than this phase's own suggested "bump versions in
the same commit" order.

## Key insights
- On-disk table (§10) gains `telegram.json`, `admin-token`, channel catalog dir; `web/.env` becomes bootstrap only.
- The admin gate is the one deliberate parity difference and must be written where the Android reader will look.

## Requirements
- `system-architecture.md`: §7 settings router + holder + runtime swap; §8 Settings screen; §10 files + precedence (file > env; state DB `settings` > `MEDIAGRAM_CACHE_MAX`).
- `running-the-player.md`: unlocking Settings (where the token file is), signing in from the browser, what signed-out mode serves.
- Parity note (§8 "What it does not have yet" neighbour): "Web Settings sit behind an admin token and own-network rule; Android's do not, because the phone is the account holder's own device."
- Changelog entry; roadmap status.
- Versions: minor bump in all three manifests per committed feature phase (CLAUDE.md); `versionCode` +1 when the APK changes.

## Architecture
N/A (docs).

## Related code files
Modify: the docs above, `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`.

## Implementation steps
1. Docs edits (via `docs-manager`).
2. Version bump, all three in step.
3. Final gates: `bun test` (web), `cargo test --workspace`, `scripts/check.sh`, `./gradlew test detekt`.
4. Real web run is the **user's** call (it takes the auth key; in-flight uploads break): hand over a checklist — unlock, read rows, cache shrink, library re-choose same channel. Sign-in/out and id/hash on a real account only when no upload is running.

## Todo
- [x] architecture/running docs
- [x] parity note
- [x] changelog (roadmap: no entry there tracks this feature at its granularity; left alone)
- [ ] version bump — lead's, at merge
- [x] all gates green (`bun test`, `tsc`, `eslint`, `cargo test --workspace`, `cargo clippy --workspace --all-targets`, Gradle unit tests for the touched Android modules)
- [x] user checklist delivered — see the phase report

## Success criteria
All gates green; docs name every new file and precedence rule; three manifests equal.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Version drift between manifests | L×M | one commit bumps all three |
| Someone keeps secrets only in `.env` then signs out via UI → env resurrects | M×M | `session: null` in `telegram.json` wins over env (01); documented |

## Security
Docs name file modes (0600/0700) and state the threat model summary from 01.

## Next steps
Merge; later: web TMDB "fetch missing" for channel libraries (Q4), QR sign-in (Q2), TV settings (Q6).
