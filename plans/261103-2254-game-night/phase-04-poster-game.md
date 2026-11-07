# Phase 04: Poster game

Spec: "Game 2: Poster-Raten"; approved mockup section 2 (`docs/superpowers/specs/2026-11-03-game-night-mockup.html`).

Outcome: *Poster-Raten* is playable: a poster sharpens every 2 s on the TV, the first right answer by claimed time wins, a wrong tap locks that player out for the poster.

---

### Task 4.1: The poster game

**Files:**
- Create: `crates/mediagram-game/src/poster.rs` (state machine), `src/poster_views.rs`, `src/poster_tests.rs`
- Modify: `src/protocol.rs`, `src/factory.rs`, `src/host.rs` (readiness from the pool)

**Interfaces:**
- Consumes: Task 3.1 `TitlePool`, `image_url`; Task 2.1 `Readiness`, `LobbySettings`.
- Produces:
  - `struct PosterConfig { rounds: usize, steps: u8, step_ms: f64, grace_ms: f64, reveal_ms: f64, max_points: i32, min_points: i32, max_claim_lag_ms: f64, early_tolerance_ms: f64, seed: Option<u64> }` — defaults 10, 8, 2000, 3000, 5000, 1000, 500, 400, 150, `None`
  - `points_at_step(cfg: &PosterConfig, step: u8) -> i32` = `max − round(step × (max − min) / (steps − 1))`
  - `step_at(cfg: &PosterConfig, started_at: ServerTime, at: ServerTime) -> u8` = `floor((at − started_at) / step_ms)` clamped to `0..=steps−1`
  - `PosterGame::new(cfg: PosterConfig, pool: &TitlePool, eligible: &[usize], used: &mut HashSet<String>, now: ServerTime) -> PosterGame` — rounds from titles with a poster; options: the title plus 3 others of the same kind with a poster, distinct names
  - `poster_ready(pool: &TitlePool, eligible: &[usize]) -> bool` — at least 4 titles of one kind have a poster
  - Protocol: `TvScene::Poster { scene: PosterScene }`, `PosterScene { round: u8, rounds: u8, stage: PosterStage }`, `PosterStage::Showing { image: String, started_at: ServerTime, step_ms: f64, steps: u8, locked: Vec<PlayerId> }`, `PosterStage::Reveal { image: String, title: String, winner: Option<PlayerId>, points: i32, ends_at: ServerTime }`, `PosterStage::Final`
- Rules:
  - Round closes at `started_at + (steps − 1) × step_ms + grace_ms` (17 s) with no winner.
  - Taps: claimed time accepted as in the quiz (`[now − 400 ms, now]`, early tolerance 150 ms). A wrong answer locks the player out; later taps from them are ignored.
  - Winner: on the first right tap with accepted time `t`, wait until `now ≥ t_min + max_claim_lag_ms`, where `t_min` is the earliest accepted right time so far; the holder of `t_min` wins `points_at_step(step_at(started_at, t_min))`.
  - All connected players locked out → reveal at once, no winner.
  - Phone screens: Showing → `Choice { prompt: "Welcher Film?" | "Welche Serie?", options, opens_at: started_at, closes_at, picked }`; locked out → `Outcome { title: "Falsch – diesmal bist du raus.", text: None, tone: Bad, points: 0 }`; Reveal → winner `Outcome { "Richtig!", Some(title), Good, points }`, others `Outcome { "Aufgelöst", Some(title), Neutral, 0 }`; Final → `Final` as in the quiz.
  - `start_game_night` sets `Readiness { poster: poster_ready(eligible(false)), poster_kids: poster_ready(eligible(true)) }`; the factory builds `PosterGame` for `GameKind::Poster`.

- [ ] **Step 1: Write the failing tests:**

```rust
#[test]
fn points_fall_from_1000_to_500_over_8_steps() {
    let c = PosterConfig::default();
    assert_eq!(points_at_step(&c, 0), 1000);
    assert_eq!(points_at_step(&c, 3), 786);
    assert_eq!(points_at_step(&c, 7), 500);
}

#[test]
fn sharpens_every_2_seconds() {
    let c = PosterConfig::default();
    assert_eq!([0.0, 1999.0, 2000.0, 14_000.0, 99_000.0].map(|t| step_at(&c, 0.0, t)), [0, 0, 1, 7, 7]);
}

#[test]
fn earliest_claimed_right_tap_wins() {
    // Ana claims 5000, arrives 5300. Ben claims 4900, arrives 5350 → accepted 4950.
    let (mut g, mut players) = game_started_at(0.0); // two players, round 1 answer index known
    tap(&mut g, &mut players, 5300.0, ANA, RIGHT, 5000.0);
    tick(&mut g, &mut players, 5320.0);
    assert!(g.winner().is_none(), "Ana's claim window is still open");
    tap(&mut g, &mut players, 5350.0, BEN, RIGHT, 4900.0);
    tick(&mut g, &mut players, 5360.0);
    assert_eq!(g.winner(), Some((BEN, 857)));
}
```

  plus `wrong_tap_locks_the_player_out`, `everyone_out_reveals_early`, `absent_player_does_not_block_everyone_out` (Ben disconnected, Ana wrong → reveal), `restored_poster_closes_after_three_seconds` (still showing at 16 999, reveal at 17 000), `phones_never_see_the_image` (serialize every phone screen of a whole game; none contains `/img/`), `rounds_use_only_titles_with_posters_and_same_kind_options`, `readiness_reflects_the_pool` (3 films with posters → `poster == false`).
- [ ] **Step 2: Run** `cargo test -p mediagram-game poster` — FAIL.
- [ ] **Step 3: Implement.** (`winner()` is a `#[cfg(test)]` helper.)
- [ ] **Step 4: Run** `cargo test -p mediagram-game` — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(game): poster game that sharpens until someone names it`

### Task 4.2: Poster scene on the TV

**Files:**
- Create: `crates/mediagram-game/client/src/tv/TvPoster.tsx`, `client/src/tv/pixelate.ts`
- Modify: `client/src/tv/TvApp.tsx`, `client/src/styles.css`, `crates/mediagram-game/tools/smoke.mjs`

- Pixelation, as in the mockup: draw the image into an offscreen canvas of `ceil(w/b) × ceil(h/b)` with smoothing on, then draw it scaled up with smoothing off. Block widths per step at a 200 px wide canvas: `[40, 28, 20, 14, 9, 6, 3, 1]`, scaled by `canvasWidth / 200`. The step comes from server time, `clamp(floor((now − started_at) / step_ms), 0, steps − 1)`, so the TV needs no extra messages.
- Copy: `Poster-Raten · Runde {round} von {rounds}`, `{points} Punkte` (same formula as `points_at_step`), `Schärfe {step + 1} von {steps}`, an 8-segment meter, locked players' chips dimmed with `✕`. Reveal: full poster, the title, `{name} +{points}` or `Niemand hat's erkannt`. Final: the quiz's final screen.

- [ ] **Step 1: Implement** the scene; `pixelate.ts` exports `blockWidth(step: number, canvasWidth: number): number` and `drawPixelated(ctx, img, block)`.
- [ ] **Step 2: Run** `cd crates/mediagram-game/client && bun run build` — clean.
- [ ] **Step 3: Smoke:** the script gets a `--game poster` flag (configures `game: "poster"`); bots tap random answers. Run against the dev binary with the fixture titles — expected `final scores:` and `after back_to_lobby: lobby`.
- [ ] **Step 4: Look at it** in a desktop browser at `/tv` beside the mockup: steps every 2 s, title text readable only near the end, reveal and lockout chips.
- [ ] **Step 5: Commit** `feat(game): pixelated poster scene on the TV`
