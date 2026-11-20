# Phase 08 — Docs, changelog, version

**Context:** CLAUDE.md § Versioning; `~/.claude/rules/documentation-management.md`.
**Status:** todo. **Depends on:** Phases 01–07 merged.

## Steps

- [ ] **1: Spec docs.** `docs/mlib-spec.md`: the `book` kind (standalone, mp3, no video fields), the `audiobooks` category department, and that older readers keep a kind they do not know. `docs/system-architecture.md`: the Audiobooks department, `add-audiobook`, books outside the day total. `docs/web-player.md`: `#/audiobooks` (already added in Phase 04 — check).
- [ ] **2: Changelog.** `docs/project-changelog.md` entry for the release: what a viewer gets, the kids rule, the daily-limit rule, and that day hours and streaks leave listening out.
- [ ] **3: Version.** Read the current media-library version on `main` (`grep -n '^version' Cargo.toml`); bump one minor step in `Cargo.toml` (workspace), `web/package.json`, `android/app/build.gradle.kts` `versionName` — by pattern, not by a remembered number. `cargo build` to refresh `Cargo.lock`.
- [ ] **4: Full gate:** `scripts/check.sh`. Expected: green.
- [ ] **5: Commit** `docs: audiobooks` and `chore: release <version>` (version files + lockfile), naming only those paths.
- [ ] **6: Plan status:** mark Phases 01–08 done in `plan.md`; add a short review section.
