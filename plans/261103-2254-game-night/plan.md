# Game Night Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The TV box hosts a game night: phones join by QR code and play a buzzer quiz (film, general or mixed questions) and a pixelated-poster game, with a *Nur für Kinder* toggle.

**Architecture:** The user's draft "Spieltisch" (Rust `Room` + `Game` trait, axum WebSocket host, React client) moves into a new workspace crate `crates/mediagram-game`. `mediagram-core` links it and exposes start/back/stop to Kotlin; the core builds the title list from its own catalog and credits, Kotlin decides which titles are kid-safe with the existing `forKidsProfile`. The TV app shows the game's TV page in a WebView; phones use the same web client in their browser.

**Tech Stack:** Rust 2024 (axum 0.8.9 + `ws`, tokio, rand 0.9, ts-rs, rust-embed, qrcode), React 18 + Vite built with bun, UniFFI 0.32, Kotlin/Compose for TV, Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-03-game-night-design.md` (approved mockup beside it: `2026-11-03-game-night-mockup.html`). Draft source: `/home/andre/Downloads/spieltisch/` (read-only; untrusted download — copy only files you have read).

## Phases

| # | Phase | Status |
|---|---|---|
| 01 | [Port the draft into `crates/mediagram-game`](phase-01-port-draft-into-game-crate.md) | todo |
| 02 | [Lobby settings: game choice and *Nur für Kinder*](phase-02-lobby-settings-and-kids-toggle.md) | todo |
| 03 | [Title pool, film questions, images](phase-03-title-pool-and-film-questions.md) | todo |
| 04 | [Poster game](phase-04-poster-game.md) | todo |
| 05 | [General-knowledge question pack](phase-05-general-question-pack.md) | todo |
| 06 | [Core binding: start, back, stop from Kotlin; textless posters](phase-06-core-binding.md) | todo |
| 07 | [TV app: Games entry and screen](phase-07-tv-games-screen.md) | todo |
| 08 | [Docs, version, box walk](phase-08-docs-version-box-walk.md) | todo |

Order: 01 → 02 → 03 → 04 → 06 → 07 → 08. Phase 05 is content work that can run any time after 03.

## Global Constraints

- Everything inside the game is German (TV page, phone page, questions, errors). The app's entry label is English: `Games`.
- No names, boards or artwork of existing games (Buzz, Monopoly, Trivial Pursuit, Mario Party). Rename the draft's "Buzz-style" wording to "Buzzer-Quiz".
- The kids rule is never re-implemented in Rust. Kotlin decides with `forKidsProfile` (`android/core/model/src/main/kotlin/AgeRating.kt:50`): kids profile → its own `kidsLimit`, toggle locked on; grown-up profile → limit **12**, toggle starts off.
- When *Nur für Kinder* is on, every title used — answers, wrong options, images — is a kid-safe title.
- Phones never receive an image. The `tv` role and `/img/…` are served only to loopback connections (the box itself).
- A game night writes nothing to profile state (no positions, history, stats, daily-limit time).
- Films and series only; a series is one show (episodes grouped by `show`); no `tut`, `doc`, `docu`.
- Timings and points: quiz 8 questions, 3.5 s intro, 12 s answer, 1000→500 points by speed, 400 ms claim lag (draft values). Poster game 10 rounds, 8 steps, 2000 ms per step, 3000 ms after fully sharp, 1000→500 points by step.
- Rust: edition 2024, `cargo clippy --all-targets --all-features -- -D warnings` clean; tests in sibling `*_tests.rs` files included with `#[cfg(test)] #[path = "x_tests.rs"] mod tests;` (as `crates/mediagram-cache/src/token.rs:154`); keep files under ~200 lines.
- No plan, phase or finding references in code, comments, test names or file names.
- `scripts/check.sh` passes at the end of every phase. Version bump in all three manifests happens once, in phase 08.

## Review Focus

1. **Box on a kids profile:** a phone sends `configure` with `kids_only: false` → the toggle stays on, and no title outside the kid's set appears anywhere. Tests: phase 02 `locked_kids_toggle_stays_on`, phase 03 `kids_only_never_uses_unsafe_titles`, phase 06 `kids_locked_keeps_only_kids_titles`, phase 07 `kidsProfileLocksAtItsOwnLimit`.
2. **Two right answers a fraction of a second apart in the poster game:** the earlier tap by claimed time (within 400 ms) wins, not the first packet. Test: phase 04 `earliest_claimed_right_tap_wins`.
3. **A fresh box with few posters or stills on disk, or kids-only with few titles:** the poster game cannot be started and the phone says why; the quiz tops up with general questions; never an empty or crashing game. Tests: phase 02 `poster_game_needs_posters`, phase 03 `too_few_titles_tops_up_with_general`, phase 04 `readiness_reflects_the_pool`.
4. **A phone sleeps or the Wi-Fi blips mid-round:** it rejoins its seat and score, and an absent player never holds a round open. Tests: phase 01 `reconnect_with_token_keeps_seat`, `disconnected_players_do_not_block_early_close`; phase 04 `absent_player_does_not_block_everyone_out`.
5. **Someone on the Wi-Fi opens `/tv` or an image URL on a phone:** refused. Tests: phase 01 `tv_hello_from_the_lan_is_refused`, phase 03 `images_are_only_served_to_the_box`.
