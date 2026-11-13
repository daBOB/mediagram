# Phase 5: Parity note, version, verification

### Task 9: Write down the difference, bump, prove it end to end

**Files:**
- Modify: the Android player's parity notes — find where earlier deliberate differences are recorded (`grep -rn "Deliberate\|deliberate difference" docs android`), add: web marks, lists and exports scenes; Android only marks (user's decision 2026-11-10).
- Modify: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (versionName), `Cargo.lock` (mediagram, mediagram-cache, mediagram-core, mediagram-tmdb, mlib-spec) → `0.179.0` (by pattern; never touch versionCode).
- Modify: `docs/project-changelog.md` if present (one entry).

- [ ] **Step 1:** Parity note + changelog entry.
- [ ] **Step 2:** Version bump; `grep -rn '0\.179\.0' Cargo.toml web/package.json android/app/build.gradle.kts Cargo.lock` shows all six.
- [ ] **Step 3:** `scripts/check.sh` — passes.
- [ ] **Step 4: Real export check** with the real ffmpeg on this machine: run `clipArgs` output against (a) an SDR H.264 file and (b) an HDR10 HEVC file (generate one with `ffmpeg -f lavfi -i testsrc2 … -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -c:v libx265 -x265-params hdr10=1` if the library has none at hand); `ffprobe` the output: `h264`, `yuv420p`, `bt709` (HDR case), `aac` 2 ch, duration within 0.1 s of requested, width ≤ 1920.
- [ ] **Step 5:** `trail check`; commit `chore: Media 0.179.0 — clip a scene`.
- [ ] **Step 6:** Merge `feat/clip-a-scene` into `main` (`--no-ff`), run `scripts/check.sh` on main.
