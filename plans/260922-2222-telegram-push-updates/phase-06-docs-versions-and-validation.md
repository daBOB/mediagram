# Phase 06 — Docs, versions, validation

Status: **done 2026-09-23**, except web ↔ Android watch-state latency, which has no Android side yet and is
handed to the Android watch-state plan's phase 09 (see `reports/validation-260923-0055-push-update-latency-report.md`).
Docs: `system-architecture.md` §5 (rescan dating), §7 and §8 "Updates Telegram pushes" (Android's foreground-only
listening written down as a deliberate difference), §11 (pin flood limit, subscription, no catch-up).
Version: patch bump (docs only; the features bumped minor in their own commits).

## Requirements
- `docs/system-architecture.md`: an "Updates" subsection under web (§7) and Android (§8): hints only, timers stay,
  foreground-only on Android (a deliberate limit, written down per § Surface Parity).
- `docs/project-changelog.md` entry; minor bump in all three manifests (CLAUDE.md § Versioning).
- Device validation on the signed-in phone (never start over on it). Measure the web↔Android latency both ways and
  record it in `reports/`.
