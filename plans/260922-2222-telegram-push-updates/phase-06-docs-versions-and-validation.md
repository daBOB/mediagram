# Phase 06 — Docs, versions, validation

## Requirements
- `docs/system-architecture.md`: an "Updates" subsection under web (§7) and Android (§8): hints only, timers stay,
  foreground-only on Android (a deliberate limit, written down per § Surface Parity).
- `docs/project-changelog.md` entry; minor bump in all three manifests (CLAUDE.md § Versioning).
- Device validation on the signed-in phone (never start over on it). Measure the web↔Android latency both ways and
  record it in `reports/`.
