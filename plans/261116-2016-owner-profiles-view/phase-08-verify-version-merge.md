# Phase 08 — Verify, version, docs, merge

Status: todo · Depends on: Phases 01–07

- [ ] **Step 1:** `scripts/check.sh` → all green (clippy, cargo test, web test/typecheck/lint, Android units).
- [ ] **Step 2:** Whole-branch review against the spec (one fresh reviewer); fix what it confirms.
- [ ] **Step 3: Version** — media library line only, minor bump from whatever `main` holds at that moment
  (main is 0.181.0 on 2026-11-17 → 0.182.0), in step: `Cargo.toml` `version`, `web/package.json` `version`,
  `android/app/build.gradle.kts` `versionName`. Bump by pattern, not exact string (other sessions commit to `main`).
  Spieltisch line untouched.
- [ ] **Step 4: Docs** — `docs/project-changelog.md` entry; `docs/web-player.md` Profiles section if it
  describes the two halves; tick this plan's checkboxes and add a Review section to `plan.md`.
- [ ] **Step 5:** `trail check`; then merge `feat/owner-profiles-view` into `main` (user: never leave
  finished work on a branch), run `scripts/check.sh` on `main`, remove the worktree after `git status` there is clean.
- [ ] **Step 6:** Release is a separate ask: web player picks it up on restart; Android via
  `scripts/release-android.sh` only when the user says so.
