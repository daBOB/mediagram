# Phase 08: Docs, version, box walk

Spec: "Parity", Constraints (version bump, docs, renumber at merge), "Check early" (WebView smoothness, TMDB access); success criterion in "Intent".

Outcome: the feature is documented, versioned, and walked on the real TV box with real phones.

---

### Task 8.1: Docs and version

**Files:**
- Modify: `docs/system-architecture.md` (§1 crate list: `mediagram-game`; a new "Game night" section under §8 beside "Cast to the TV": host in the core, loopback-only TV role and images, kids decision in Kotlin, title mapping, timings; "Television differs from the web player": game night is TV-only by design and phones join through the browser), `docs/project-changelog.md` (entry), `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`)

- [ ] **Step 1: Write the docs.**
- [ ] **Step 2: Bump the minor version in all three manifests, in step.** Edit by pattern (the current `version = "…"` line), not by exact old value. `main` moves while this branch lives (it held 0.142.2 uncommitted when the branch started): pick the next minor above `main`'s version at merge time and renumber if `main` moved again.
- [ ] **Step 3: Run** `scripts/check.sh` — `all checks passed`.
- [ ] **Step 4: Commit** `docs: game night in the architecture and changelog` and `chore: version X.Y.0`

### Task 8.2: Walk it on the box

The box and the phones are real household devices: navigate only, change no settings, pin every adb command to the box's serial (`ANDROID_SERIAL`).

- [ ] **Step 1: Ask the user** how this build should reach the box (sideload or a channel release; a release is outward-facing and needs their go-ahead) and which kids profile may be used for the kids check.
- [ ] **Step 2: Install** as agreed (rebuild the native core first: `scripts/build-android-core.sh`).
- [ ] **Step 3: Early checks:** open *Games*; the TV page renders and the poster steps run smoothly in the box's WebView; a `Filme` quiz shows stills (the box has the artwork on disk).
- [ ] **Step 4: Grown-up profile:** two phones (or one phone plus `bun crates/mediagram-game/tools/smoke.mjs ws://<box-ip>:<port>/ws` from the dev machine) join by QR; play a full `Gemischt` quiz and a full *Poster-Raten*; switch *Nur für Kinder* on and check that no FSK 16/18 still or poster appears; Back mid-game returns to the lobby; Back in the lobby leaves, and the join URL no longer answers.
- [ ] **Step 5: Kids profile (the one the user named):** *Games* opens; the toggle shows on and locked; a phone cannot switch it off.
- [ ] **Step 6: Record** the results, with anything that stuttered or surprised, in `plans/261103-2254-game-night/reports/box-walk-report.md`; mark the phases done in `plan.md`.
