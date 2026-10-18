# Phase 11 — Docs, version, checks, review, device walks

**Spec:** §9 Format, Release, §11 Device walks. **Status:** pending.

### Task 11.1: Docs and version

**Files:**
- Modify:
  - `docs/mlib-spec.md` §7b: rewrite for `v=2`, from spec §1. Cover the fields table, the reader rules, "readers accept v=1 and v=2, writers write v=2", the two liveness rules, and "a request is never the shown session".
  - `docs/system-architecture.md`: the cast path (sender → channel message → TV receiver → player as the caster), Miracast and AirPlay.
  - `docs/web-player.md`: Play on TV, the remote, the bar, AirPlay, and `PREVIEW_CAST`.
  - `docs/project-changelog.md`: the 0.127.0 entry.
  - The spec: change its status line to "approved; implemented in 0.127.0". Add the decisions from `plan.md`'s confirmation list that the user approved, and any probe outcome (§6/§7).
- Version: bump by regex to `0.127.0` in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts`. `versionCode` derives on its own.

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Bump the three manifests, then `grep -n "0.127.0"` in each.
- [ ] **Step 3: Commit** `docs: cast to the TV in the format, architecture and player docs` and `chore: 0.127.0`.

### Task 11.2: Checks and review

- [ ] `scripts/check.sh`: cargo tests, `bun test`, `tsc`, `eslint`, gradle `testDebugUnitTest :core:model:test lint detekt`. All must pass; paste the summary into `plan.md` § Review.
- [ ] A whole-branch review by a fresh reviewer (`superpowers:requesting-code-review`) against the spec and this plan. Its five Review Focus items are the first things it checks. Fix the findings, re-run the checks, and record them in `plan.md` § Review.
- [ ] Merge `feat/cast-to-tv` into `main` locally once the user agrees. Don't push.

### Task 11.3: Device walks

Ask before each walk. Use test profiles only, navigate only, and switch the TV on first.

- [ ] **Install.** The tablet, `ANDROID_SERIAL=caad49da`, and the box at `192.168.0.56:5555` (or nmap for port 5555), both on the test profile. Box: sideload `0.127.0`, then check `install-dm` (memory: TV self-updates install uncompiled).
- [ ] **Walk A: from the tablet's title page.** Play on TV while the box is on Home → "Asking the TV…" → the remote. Then check:
  - the box plays under the test profile, and its label reads "Cast from Tablet";
  - pause on the remote → the box pauses and shows "Paused by Tablet";
  - pause on the TV remote → the tablet shows "Paused on TV";
  - choose German subtitles on the remote → they show on the TV;
  - Stop → the box returns to Home.
- [ ] **Walk B: moving what's playing.** Play a title on the tablet, then Play on TV → it continues at the same second on the box, and the tablet's player gives way to the remote.
- [ ] **Walk C: a busy TV.** The box is playing something → "The TV is playing *X*. Replace it?" → Replace → it switches. Cancel → nothing changes.
- [ ] **Walk D: the bar.** Back out of the remote → the bar shows on the tablet and in the web player's preview of the same library. Tap it → the remote.
- [ ] **Walk E: Back on the TV** ends the cast → the tablet's remote says "Stopped on the TV".
- [ ] Record everything in `reports/walk-261018-cast-to-tv-report.md`, and leave both devices on profile "test".
- [ ] Update memory `cast-tablet-to-tv-next.md` with the merge and release state.

**Success criteria:** all the checks pass, the review is recorded, and walks A–E pass on real devices. Phases 09 and 10 record their own device checks.
