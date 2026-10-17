# Phase 5 — Docs, version, checks, review, device walk

**Status:** pending.

### Task 5.1: Docs + version

**Files:** `docs/system-architecture.md` (new "### Reporting a broken video" under §8 after Watch together: Saved Messages reminder and why, the route, `report_send`, the fixture; a line in §7 for the web route), `docs/mlib-spec.md` only if it lists every `#mlib-` message kind (then one line: `#mlib-report` is never in the channel — it lives in Saved Messages and no reader parses it), `PRODUCT.md` (one line), `docs/project-changelog.md` ("## 0.126.0 — report a broken video"), `docs/development-roadmap.md` if it tracks shipped features; version bump in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` `versionName` → `0.126.0` by regex (memory `bump-versions-by-pattern`), then update the fixture's `app` strings only if they stop matching nothing (they are sample text; leave them).

- [ ] Write docs. - [ ] Bump. - [ ] **Commit** `chore: 0.126.0 — report a broken video`.

### Task 5.2: Checks + review

- [ ] `scripts/check.sh` all green (log to the scratchpad).
- [ ] `code-reviewer` agent on `main..feat/report-broken-video` with the spec and the plan's Review Focus; fix Critical/High; document Low.
- [ ] Re-run `scripts/check.sh` after fixes. Commit fixes.

### Task 5.3: Merge (local) and device walk

- [ ] Merge to `main` locally (`--no-ff`), remove the worktree; the live player (`bun --watch` on :8770) restarts — confirm `POST /api/report` answers 400 to `{}` (no real send).
- [ ] **Ask the user** before any real send. Then: tablet `ANDROID_SERIAL=caad49da`, TV box `adb -s 192.168.0.56:5555` (installs pinned to a serial, native core rebuilt first), on the **test** profiles: open a title, Report → *Picture or sound wrong* → Send; the user confirms the phone notification and the text; TV: reach Report with the remote from the top bar and from a failure screen (navigate only; never select settings).
- [ ] No push, no release without the user's word.

### Unresolved

- None.
