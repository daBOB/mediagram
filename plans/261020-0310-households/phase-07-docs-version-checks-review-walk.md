# Phase 07 — Docs, version, checks, review, device walk

**Spec:** § Testing. **Status:** pending. **Depends on:** phases 01–06.

### Task 7.1: Docs and version

**Files:**
- Modify: `docs/system-architecture.md`, a "Households" section:
  - the rule (resolution, the owner's actions, joining, the device's household), written once in `household-rules.js` and ported;
  - the stored shape (web v16 / core v12 at planning time);
  - the wire (from `phase-01`: top-level `households`, per-profile `household`, nothing dated 0 said, sync format still 1);
  - what an older build sees (owner = the old admin claim = Home's admin; other households' admins read as grown-ups until it updates);
  - that it is a household rule, not a lock (spec decision 3).
- Modify: `docs/web-player.md`:
  - the routes table: `POST /api/households`, `PUT …/name`, `PUT …/admin`, `POST …/move`, `DELETE …/{id}`, `POST …/join`, and `GET /api/profiles` now saying `households`, plus `owner` and `household` on each profile;
  - the Profiles page: This device, Switch household, Owner, Households;
  - Join a household in the picker.
- Modify: `CONTEXT.md` § Language, new terms:
  - **Household**: a group of profiles on one install; a device joins one and sees only its profiles. _Avoid_: family, account.
  - **Owner**: the one profile that sets households up, the install's first admin claim. _Avoid_: superadmin.
  - **Household admin**: the grown-up who runs one household. _Avoid_: admin (alone, when the household matters).
- Modify: `docs/project-changelog.md`: the release's entry. Say what a viewer sees; that nothing changes on a one-household install; that it is not real separation; and the new routes and record keys.
- Modify: `docs/superpowers/specs/2026-10-20-households-design.md`: the status line becomes "approved; implemented in <version>"; append the plan's decisions the user confirmed.
- Version: the next **minor** above `main` at merge time, in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts`, bumped by regex, never by exact string (memory `bump-versions-by-pattern`: other sessions commit to `main`). `versionCode` derives on its own.

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Bump the three manifests; `grep -n '<version>'` in each.
- [ ] **Step 3: Commit** `docs: households in the architecture, player docs and glossary` and, separately, `chore: <version>, households`.

### Task 7.2: Checks, review, merge

- [ ] **Checks.** Rebuild the native core first (memory `rebuild-native-core-after-rust-changes`), then run `scripts/check.sh`: cargo tests and clippy, `bun test`, `tsc`, `eslint`, and gradle `testDebugUnitTest :core:model:test lint detekt`. Everything passes; paste the summary into `plan.md` § Review.
- [ ] **Review.** A whole-branch review by a fresh reviewer, against the spec and this plan. It checks `plan.md`'s Review Focus items first. Fix the findings, re-run the checks, and record both in § Review.
- [ ] **Merge** `feat/households` into `main` locally, only on the user's word. No push, no release without it. Bump again by pattern if `main` moved past the version.

### Task 7.3: Device walk (spec § Testing)

Ask before starting. Rules:
- test profiles only;
- navigate only, outside the steps below;
- the TV switched on first;
- devices pinned by serial: tablet `ANDROID_SERIAL=caad49da` or `d54bc94f`, box `192.168.0.56:5555` (nmap port 5555 if it moved);
- the owner's PIN is typed by the user on the device, never asked for in chat;
- any PIN set on a test profile is written into the walk report.

- [ ] **Install** the build on the tablet and the box, after rebuilding the `.so`. On the box, check `install-dm` (memory `tv-self-updates-install-uncompiled`).
- [ ] **Migration.** On first launch both devices show the same pickers as before. On the web, Settings → Profiles reads "Owner · Admin" for andre, and shows no "This device" row while one household exists.
- [ ] **Walk:**
  1. On the tablet, as the owner, create a second household "Test house", with **TV test** as its admin (moved in with any kids of its own). If TV test has no PIN, set `2468` and note it. If TV test turns out to be a kid, stop and ask: a kid moves only with its parent.
  2. On the box, from the picker's Manage profiles: This device → Switch household → Test house → TV test's PIN.
  3. The box's picker shows only TV test (and its kids). The tablet's picker no longer shows them. The web picker in a fresh browser asks "Join a household".
  4. As a non-owner grown-up on the tablet, the Owner button asks the owner's PIN.
- [ ] **Restore**, unless the user says to keep it:
  - move TV test back to Home: allowed, as Test house's last grown-up;
  - switch the box back to Home with the owner's PIN, typed by the user;
  - delete the now-empty Test house.
  
  Both pickers read as before.
- [ ] Record the walk in `plans/261020-0310-households/reports/walk-<date>-households-report.md` and leave both devices on profile "test".
- [ ] Update the memory on the release state.

**Success criteria:** all checks pass; the review is recorded; the walk and the restore pass on real devices.
