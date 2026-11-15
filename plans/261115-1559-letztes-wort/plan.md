# Letztes Wort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** A new Spieltisch minigame: one question per round, players buzz on the phone and answer out loud, a rotating host judges, a correct answer takes the stick, the holder when the hidden time runs out wins the round.

**Architecture:** A new `GameKind::LetztesWort` with its own host module (stage machine driven by `on_action`/`on_tick`), its own wire types (a TV scene and a game-specific phone screen), a bundled question file, and React screens for the phone (buzzer, stick, host desk) and the TV. Actions reuse `Action::Choose`; no new action type.

**Tech Stack:** Rust (`crates/mediagram-game`, ts-rs bindings exported by `cargo test` into `client/src/protocol`), React + TypeScript client built with bun, Web Audio for the buzzer.

**Spec:** `docs/superpowers/specs/2026-11-15-letztes-wort-design.md` · Mockup: `plans/261115-1559-letztes-wort/reports/mockup-buzzer-and-host-screens.jpg` (approved by the user 2026-11-15).

## Global Constraints

- The TV never shows any answer, any answer count, or a clock.
- The hidden time: 15–35 s per round, drawn when the buzzers first open; Tempo stretches it (`GameSpeed::stretch`).
- Length: laps of host rotation, 2 at normal; Dauer via `GameLength::scale(2)` → 1 / 2 / 3. Rounds = laps × players at the start.
- At least 3 players (`catalogue.rs` min), at most `ROOM_CAP`.
- Wrong answer = out for the round. A repeat of a given answer is wrong. The stick holder and the host cannot buzz.
- German text only; no pronouns guessed from names ("Tipp die genannte Antwort an").
- Spieltisch version line: bump minor in every manifest of the line (see repo `CLAUDE.md` § Versioning) above main's version at merge time (main is 0.192.5 on 2026-11-15).
- Files under ~200 lines; one responsibility each.

## Review Focus

1. **Time runs out while someone has the floor** → the verdict still counts; a correct one makes that player the winner. (Task 2 test `time_up_during_floor_waits_for_the_verdict`.)
2. **The host leaves mid-round** → after the house grace the next present player in seat order hosts and the round restarts with a new question; the old host's turn is not counted twice. (Task 2 test `an_away_host_hands_the_round_on`.)
3. **Two buzzes arrive together** → the first to arrive has the floor, the second is ignored, not queued. (Task 2 test `the_second_buzz_is_ignored`.)
4. **Everyone but the host is out, nobody holds the stick** → the round ends at once with no winner, it doesn't wait for the hidden time. (Task 2 test `all_out_and_no_holder_ends_without_a_winner`.)
5. **A double tap on the host desk** (verdict, then the next screen opens under the finger) → verdict keys reopen only after `verdict_gap_ms` and a second verdict without a floor is ignored. (Task 2 test `a_verdict_without_a_floor_is_ignored`.)

---

### Task 1: Config, question pack and loader

**Files:**
- Create: `crates/mediagram-game/src/letztes_wort_config.rs`, `crates/mediagram-game/questions/letztes-wort.json`
- Modify: `crates/mediagram-game/src/lib.rs` (mods)
- Test: `crates/mediagram-game/src/letztes_wort_config_tests.rs`

**Interfaces — Produces:**
- `pub struct LetztesWortConfig { laps: usize = 2, time_min_ms: ServerTime = 15_000.0, time_max_ms: ServerTime = 35_000.0, intro_ms = 3000.0, verdict_gap_ms = 400.0, round_end_ms = 5000.0, early_tolerance_ms = 150.0, seed: Option<u64> = None }` with `at(self, GameSpeed) -> Self` (stretches `time_min_ms`, `time_max_ms` only) and `sized(self, GameLength) -> Self` (`laps = length.scale(laps)`).
- `pub(crate) struct Prompt { q: String, answers: Vec<String> }` (serde, the JSON entry), `pub(crate) static PROMPTS: LazyLock<Vec<Prompt>>` from `include_str!("../questions/letztes-wort.json")`.
- `pub(crate) fn letztes_wort_prompts(rounds: usize, rng: &mut impl Rng, used: &mut HashSet<String>) -> Vec<&'static Prompt>` — fresh ones first, key `format!("lw:{}", p.q)`; when all are used tonight, start over (Spruchreif's pattern).

- [x] Write tests: `every_prompt_has_five_to_ten_distinct_answers` (also: ≥ 60 prompts, no duplicate questions), `a_game_draws_no_prompt_twice` (rounds = 16), `asked_tonight_comes_back_only_when_all_were_asked`, `tempo_stretches_only_the_hidden_time`, `length_scales_the_laps` (Short 1, Normal 2, Long 3).
- [x] Run `cargo test -p mediagram-game letztes_wort_config` → fails (missing module).
- [x] Write the 60+ prompts (closed sets the host checks at a glance: planets, Beatles, Bundesländer am Meer, Farben der Olympischen Ringe …; 5–10 answers each; well-known German spellings) and the config/loader.
- [x] Run the tests → pass. Commit `feat(spieltisch): Letztes Wort question pack and config`.

### Task 2: The game (host rules)

**Files:**
- Create: `crates/mediagram-game/src/letztes_wort.rs` (Game impl + state), `crates/mediagram-game/src/letztes_wort_rules.rs` (pure helpers if `letztes_wort.rs` passes ~200 lines)
- Test: `crates/mediagram-game/src/letztes_wort_tests.rs`

**Interfaces — Consumes:** Task 1. **Produces:**
- `pub struct LetztesWortGame` with `pub fn new(cfg, prompts: Vec<&'static Prompt>, players: &[Player], now) -> Self`; `rounds = cfg.laps * players.len()`; host order = seat order from a random start (`cfg.seed`), each round the next host.
- `pub(crate) enum Stage { Open { opens_at }, Floor { player, buzz: u16 }, RoundEnd { winner: Option<PlayerId>, ends_at }, Final }`; round state `host, prompt, given: Vec<(Option<usize>, PlayerId)>` (`None` = "Auch richtig"), `holder: Option<PlayerId>`, `out: BTreeSet<PlayerId>`, `time_up_at` (hidden), `buzzes: u16`.
- Actions (`Action::Choose { index, .. }`): a non-host player's choose (any index) = **buzz**; the host's choose during `Floor`: `index < answers.len()` = that answer, `== answers.len()` = **Falsch**, `== answers.len() + 1` = **Auch richtig**. A given answer chosen again = Falsch.
- Rules: buzz accepted only in `Open`, `now >= opens_at - early_tolerance_ms`, `now < time_up_at`, player ≠ host, not out, not holder. Correct → `holder = player`; wrong → `out.insert`. Then `Open { opens_at: now + verdict_gap_ms }` unless the round ends: time up, every listed answer given, or nobody left who may buzz. On tick: `Open` and `now >= time_up_at` → `RoundEnd`. Winner +1 score. After the last round `Final`.
- Away host (`ctx.house_at(host, since)`): next present player in seat order hosts, new prompt, round restarts (round number unchanged).

- [x] Write tests (Table fixture like `zuendstoff_tests.rs`, seeded, `time_min_ms == time_max_ms`): `the_first_correct_answer_takes_the_stick`, `another_correct_answer_takes_it_over`, `the_holder_and_the_host_cannot_buzz`, `a_wrong_answer_puts_the_player_out_for_the_round_only`, `a_repeated_answer_counts_as_wrong`, `also_right_takes_the_stick_without_using_a_listed_answer`, `the_holder_wins_when_the_hidden_time_runs_out`, `nobody_wins_when_nobody_held_the_stick`, `the_round_ends_when_every_answer_is_given`, `the_round_ends_when_only_the_holder_could_buzz`, `the_host_rotates_in_seat_order_and_everyone_hosts_equally`, `a_buzz_after_time_up_is_ignored`, `the_game_ends_after_laps_times_players_rounds`, plus the five Review Focus tests.
- [x] Run `cargo test -p mediagram-game letztes_wort` → fail. Implement. Run → pass. Commit `feat(spieltisch): Letztes Wort rules`.

### Task 3: Wire types, views and registration

**Files:**
- Create: `crates/mediagram-game/src/protocol_letztes_wort.rs`, `crates/mediagram-game/src/letztes_wort_views.rs`, `crates/mediagram-game/src/letztes_wort_views_tests.rs`
- Modify: `src/protocol_settings.rs` (`GameKind::LetztesWort`, doc line), `src/protocol.rs` (`PhoneScreen::LetztesWort { phone: LetztesWortPhone }`, `pub use`), `src/protocol_tv.rs` (`TvScene::LetztesWort { scene: LetztesWortScene }`), `src/catalogue.rs` (after Zündstoff: `("Letztes Wort", 3, ROOM_CAP)`, in `timed()` and `sized()`), `src/catalogue_tests.rs`, `src/factory.rs`, `src/game_speed_tests.rs` (`clocks()`: `time_min_ms`, `time_max_ms`), `src/game_length_tests.rs` (`counts()`: `laps`), `src/lib.rs`.

**Interfaces — Produces (ts-rs exported):**
- `LetztesWortScene { round: u8, rounds: u8, question: String, host: PlayerId, holder: Option<PlayerId>, out: Vec<PlayerId>, stage: LetztesWortStage }`; `LetztesWortStage` (tag `stage`): `Open`, `Floor { player, buzz: u16 }`, `RoundEnd { winner: Option<PlayerId>, next_host: Option<PlayerId> }`, `Final`.
- `LetztesWortPhone` (tag `view`): `Buzzer { question, live: bool, opens_at, line: String }`; `Stick { name: String, avatar: Option<String>, title: String, text: String }` (I hold it, or round over); `Host { question, answers: Vec<HostAnswer>, floor: Option<String> /* name */, opens_at }` with `HostAnswer { text, given_by: Option<String> }`. A player who is out gets `PhoneScreen::Outcome { title: "Falsch – du bist raus", text: "Bei der nächsten Frage bist du wieder dabei.", tone: Bad, show_score: false }`; Final via `rank_of`.
- Factory: `LetztesWortConfig::default().at(speed).sized(length)`, prompts drawn with `history`.

- [x] Write view tests: `the_tv_never_carries_an_answer` (serialize every TV scene of a played round; no answer string appears), `the_host_sees_the_answers_and_who_gave_them`, `a_player_sees_a_live_buzzer_only_when_allowed`, `the_holder_sees_the_stick`, `an_out_player_sees_the_outcome`.
- [x] Run → fail; implement; `cargo test -p mediagram-game` (all, also regenerates `client/src/protocol/*.ts`) → pass; `cargo clippy -p mediagram-game` clean. Commit `feat(spieltisch): Letztes Wort on the wire and in the lobby`.

### Task 4: Phone screens and the buzzer sound

**Files:**
- Create: `client/src/ui/buzzer-sound.ts` (Web Audio synth, ~0.35 s game-show buzz; silent on failure), `client/src/ui/Baton.tsx` (the stick SVG from the mockup), `client/src/phone/PhoneLetztesWort.tsx`, `client/src/phone/letztes-wort-phone.css`
- Modify: `client/src/phone/PhoneApp.tsx` (`case "letztes_wort"`)

**Interfaces — Consumes:** the generated `LetztesWortPhone`. `buzzerSound(): void` plays once; the phone calls it on press (before sending `{ t: "choose", index: 0, at }`), so the sound is instant.

- [x] Build to the approved mockup: big red BUZZ key (locked look when `!live`, with `line`), Stick screen (avatar head + Baton), Host desk (answer cards two columns, given ones dashed + giver's name, red floor banner, Falsch / Auch richtig; keys disabled without a floor or before `opens_at`). Host taps send `Choose` with the index mapping of Task 2.
- [x] `bun run build` and `bunx tsc --noEmit` clean. Commit `feat(spieltisch): Letztes Wort phone: buzzer and host desk`.

### Task 5: TV screen

**Files:**
- Create: `client/src/tv/TvLetztesWort.tsx`, `client/src/tv/letztes-wort/letztes-wort.css`
- Modify: `client/src/tv/TvApp.tsx`, `client/src/ui/shelf-games.ts` (`letztes_wort`, group `mini`, blurb)

- [x] Build to the mockup: question card with "X moderiert", red floor banner on `Floor`, holder head with Baton, player chips (holder yellow, host dashed, out greyed), `RoundEnd` "Zeit!" + "… hatte das letzte Wort" (or "Niemand hatte den Stab"), `TvFinal` at the end. The buzzer sound plays once per `Floor.buzz` (TV effects volume setting respected).
- [x] `bun run build` clean. Commit `feat(spieltisch): Letztes Wort on the TV`.

### Task 6: Run it, version, changelog

- [x] Run the game in the browser (TV page + 4 phone pages, as the earlier browser walks did): a round with a buzz, a wrong answer, a steal, time up; a host leaving. Screenshots of every screen into `plans/261115-1559-letztes-wort/reports/`.
- [x] Bump the Spieltisch line (minor) in all manifests; `docs/project-changelog.md` entry. `cargo test -p mediagram-game`, `bun run build`. Commit `feat(spieltisch): Letztes Wort, the last right answer keeps the stick (0.x.0)`.
- [x] Whole-branch review (fresh reviewer), fix findings. Merge per the worktree routine when the user says so.

## Progress (2026-11-15)

- Built natively in one session. Commits held back: the user's rules say no commits without their word.
- `cargo test -p mediagram-game` 629 passed (32 new), clippy clean, `bunx tsc --noEmit` and `bun run build` clean.
- Browser walk (TV + 4 phones, dev server on :8197): `reports/browser-walk-tv-one-round.jpg`, `reports/browser-walk-phones-one-round.jpg`.

## Found in the walk

- The phone's host-desk banner class `lw-floor` matched the TV section's stage class `lw-floor` (all CSS is global), so the whole TV turned red on a buzz. Renamed to `lw-desk-floor`.
- The host's answer cards were dimmed while nobody had buzzed; they stay readable now (only not tappable).

## Review (fresh reviewer, 2026-11-15) — all fixed

- High: when the pack ran dry, `letztes_wort_prompts` cleared the night's shared `used` set for every game. Now only `lw:` keys start over (test asserts another game's key survives); spares cut to 2 per game.
- A host's late double tap could judge the next buzz: a verdict within `verdict_gap_ms` of the buzz is ignored (`a_verdict_right_after_a_buzz_is_ignored`).
- Buzzer only on `pointerdown`: `onClick` added for screen readers / switch / voice access.
- A press that lost the race locked the next opening: `Buzzer` keyed by `opens_at`.
- An away next host now is passed over before the round starts; away players no longer hold a round open.
- iPhone audio: the buzzer's context unlocks on the first `touchend`/`click` anywhere (Safari ignores finger-down). Not checked on a device.
- Left as is (spec): no timeout while someone has the floor and the host is present.
- After fixes: 632 tests, clippy, tsc, build clean; walk rerun, no page errors.

## Shipped (2026-11-15 23:40)

- Rebased twice onto main (0.192.6 kids fix, 0.192.7 one catalogue entry per game: Letztes Wort now declares `ENTRY` in `letztes_wort_config.rs`; 0.180.10 Android refactor). Merged into main as 09e4280e, not pushed.
- Installed on the funstation (WayDroid `pm install -r`, release key) over 0.192.4 while the TV sat idle in the lobby; Spieltisch reopened afterwards. The shelf lists Letztes Wort without a poster yet: `reports/funstation-shelf-with-letztes-wort.jpg`.
- Next: poster and backdrop for the shelf.
