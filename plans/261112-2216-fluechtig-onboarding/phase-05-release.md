# Phase 5: Version, checks, screenshots, review

**Context:** repo `CLAUDE.md` § Versioning (Spieltisch line), `docs/project-changelog.md`, the earlier live run `plans/261109-1924-fluechtig/reports/live-run/` and its method (`plan.md` Results there), dev host `cargo run -p mediagram-game --bin game-night-dev` (`docs/running-the-player.md`).

**Overview:** priority high, status todo. After Phases 1–4.

---

### Task 12: Release checks

**Files:** Modify `crates/mediagram-game/Cargo.toml`, `crates/spieltisch-core/Cargo.toml`, `crates/mediagram-game/client/package.json`, `android/spieltisch/build.gradle.kts` (`versionName`), `Cargo.lock`, `docs/project-changelog.md`, `plans/261112-2216-fluechtig-onboarding/plan.md` (Results, phase statuses); Create `plans/261112-2216-fluechtig-onboarding/reports/live-run/*.jpg`

- [ ] **Step 1: Version.** Read `main`'s Spieltisch version (`git show main:crates/mediagram-game/Cargo.toml`); bump the next minor (0.177.1 → 0.178.0 if `main` has not moved) in all four manifests; `cargo build -p mediagram-game` updates `Cargo.lock`.
- [ ] **Step 2: Changelog** entry in `docs/project-changelog.md`: one figure per player and the ladder (with the measured numbers), the rules cards, role card, role bar and colours, names instead of piece numbers, the line legend, six bridges and buses on streets (new totals 342/89/20/3), `add_bridges.py`.
- [ ] **Step 3: Checks**, all must pass:
  - `cargo test -p mediagram-game`
  - `cargo clippy -p mediagram-game --all-targets -- -D warnings`
  - `cd crates/mediagram-game/client && bun run typecheck && bun test && bun run build`
- [ ] **Step 4: Live run** on the dev host, TV at 960×540 (scale 2), phones at 390×800, players as the mockup (four, then two). Retain screenshots in `reports/live-run/`:
  - `tv-1-card-who.jpg`, `tv-2-card-lines.jpg`, `tv-3-card-hidden.jpg` (four players, leader named in the footer)
  - `tv-4-play-legend-named.jpg` (a hunter's turn: "{name} ist dran", no badges, legend)
  - `tv-5-bridges-and-buses.jpg` (the plan: decks, bus stripes on streets)
  - `tv-6-two-players-figures.jpg` (two players: badges 1/2, "zieht Figur 2")
  - `phone-1-role-fugitive.jpg` (night), `phone-2-role-hunter-leader.jpg` (police blue, skip button), `phone-3-hunter-moves.jpg`, `phone-4-fugitive-waits.jpg`, `phone-5-final-no-theme.jpg` (house green again)
  - Also: skip from the leader's phone works; a restart shows the cards again.
- [ ] **Step 5: Compare** each screenshot with `reports/mockups/` and `reports/bridges/`; open them and look (contrast of the night theme, deck clipping at the banks, no text overflow at 390 px).
- [ ] **Step 6: Code review** of the branch against `main` with a fresh reviewer (superpowers:requesting-code-review); fix confirmed findings; re-run Step 3.
- [ ] **Step 7: Results** in `plan.md`: versions, test counts, balance numbers, screenshots, review outcome, anything not done. Then ask the user whether to commit.

**Success criteria:** every check green; screenshots retained and matching the approved renders; review without open high findings.

**Risks:** `main` moves during the build (version collision as on 0.171 → 0.173 last time): re-read `main`'s version right before the user commits.
