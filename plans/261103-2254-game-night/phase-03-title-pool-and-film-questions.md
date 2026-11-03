# Phase 03: Title pool, film questions, images

Spec: "Game 1: Buzzer-Quiz" (film and general questions), "Which titles the games use", Constraints (phones never get images).

Outcome: the quiz asks film questions generated from a title list (still, year, director, cast), general questions from a bundled pack, or both alternating; images are served to the box only; repeats are avoided within an evening.

---

### Task 3.1: Titles, the pool and image serving

**Files:**
- Create: `crates/mediagram-game/src/titles.rs`, `src/titles_tests.rs`, `crates/mediagram-game/fixtures/titles.json`
- Modify: `src/server.rs`, `src/server_tests.rs`, `src/host.rs`, `src/bin/game-night-dev.rs`

**Interfaces:**
- Produces:
  - `enum TitleKind { Film, Series }`
  - `struct GameTitle { key: String, kind: TitleKind, title: String, year: Option<u16>, poster: Option<PathBuf>, still: Option<PathBuf>, directors: Vec<String>, cast: Vec<String>, kids_ok: bool }` — `cast` in billing order; files are local paths on the box
  - `TitlePool::new(titles: Vec<GameTitle>, kids_locked: bool) -> TitlePool` — when locked, drops every title with `kids_ok == false`
  - `TitlePool::eligible(&self, kids_only: bool) -> Vec<usize>`, `get(&self, i: usize) -> &GameTitle`, `image_path(&self, i: usize, which: ImageKind) -> Option<&Path>`
  - `enum ImageKind { Poster, Still }`; `image_url(i: usize, which: ImageKind) -> String` → `/img/{i}/poster` or `/img/{i}/still`
  - `HostConfig` gains `pool: Arc<TitlePool>` (default empty)
- Route `GET /img/{i}/{which}`: loopback peers only (`403` otherwise), `404` for an unknown index or missing file, content type from the extension (`png`, `jpg`/`jpeg`, `webp`). Handler written as a plain async fn taking `State`, `ConnectInfo<SocketAddr>`, `Path`, so tests call it directly.
- Fixture: 8 films and 4 series (mixed `kids_ok`, years, directors, cast), images pointing at the repo's `wallstreet story.poster.png`, `geldhochschule_plakat_2x3.png`, `wallstreet story.backdrop.png`, `geldhochschule_backdrop_16x9.png` (paths relative to the workspace root; reuse is fine). The dev binary loads it with `--titles crates/mediagram-game/fixtures/titles.json`.

- [ ] **Step 1: Write the failing tests:** `locked_pool_drops_unsafe_titles` (pool of 3 safe + 2 unsafe, locked → `eligible(false).len() == 3`); `kids_only_eligible_is_the_safe_subset`; `images_are_only_served_to_the_box` (handler with peer `192.168.0.5:5000` → `403`; with `127.0.0.1:5000` → `200` and the file's bytes; index 99 → `404`).
- [ ] **Step 2: Run** `cargo test -p mediagram-game titles server` — FAIL.
- [ ] **Step 3: Implement** `titles.rs`, the route, the fixture and the dev-binary flag.
- [ ] **Step 4: Run** — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(game): title pool and box-only image route`

### Task 3.2: Film questions, the general pack, and the mix

**Files:**
- Create: `src/film_questions.rs`, `src/film_questions_tests.rs`, `src/general.rs`, `src/general_tests.rs`, `crates/mediagram-game/questions/allgemein.json`, `src/quiz_mix.rs`, `src/quiz_mix_tests.rs`, `src/factory.rs`
- Modify: `src/starter.rs` (removed — its 14 questions become the first entries of `allgemein.json`), `src/host.rs`, `src/bin/game-night-dev.rs`

**Interfaces:**
- Consumes: Task 3.1 `TitlePool`, `image_url`; Task 1.2 `Question`, `Quiz::new`; Task 2.1 `LobbySettings`, `GameFactory`.
- Produces:
  - `film_questions(pool: &TitlePool, eligible: &[usize], rng: &mut StdRng, count: usize, used: &mut HashSet<String>) -> Vec<Question>`
  - `general::general_pack() -> Vec<Question>` — parses `include_str!("../questions/allgemein.json")`; entry shape `{ "category", "text", "correct", "wrong": [3], "source" }`
  - `quiz_mix::quiz_questions(mode: QuizMode, pool: &TitlePool, kids_only: bool, general: &[Question], rng: &mut StdRng, rounds: usize, used: &mut HashSet<String>) -> Vec<Question>`
  - `factory::game_factory(pool: Arc<TitlePool>, general: Arc<Vec<Question>>) -> GameFactory` — owns the evening's `used` set
  - `host::start_game_night(titles: Vec<GameTitle>, kids_locked: bool, preferred_port: u16) -> io::Result<Host>` — builds pool, factory and config; what the core and the dev binary call
- Question kinds (all wrong options from the same eligible list):

| Kind | Needs | Category | Text | Answer / wrong options |
|---|---|---|---|---|
| Still | `still` | `Filmszene` / `Serienbild` | `Aus welchem Film ist diese Szene?` / `Aus welcher Serie ist dieses Bild?` | title / 3 other titles of the same kind; `image = image_url(i, Still)` |
| Year | `year` | `Erscheinungsjahr` | `In welchem Jahr erschien „{title}“?` | year / 3 distinct years within ±6, never the answer |
| Director | film with ≥1 director | `Regie` | `Wer führte bei „{title}“ Regie?` | `directors[0]` / 3 directors of other films, none of this film's |
| Cast | ≥1 cast | `Besetzung` | `Wer spielt in „{title}“ mit?` | `cast[0]` / 3 names from other titles' first three cast, none in this title's cast |

  A kind with fewer than 4 candidates is skipped. `used` keys: `"{kind}:{title.key}"` for film questions, the text for general ones; when too few unused remain, clear `used` and continue.
- Mix: `Films` → film questions topped up with general; `General` → general only; `Mixed` → film, general, film, … topped up from the other kind. Always `rounds` questions when the general pack has enough.

- [ ] **Step 1: Write the failing tests:**
  - `kids_only_never_uses_unsafe_titles` — pool 6 safe + 6 unsafe with distinct names; for seeds 0..50, every answer, wrong option and image index of `film_questions(.., eligible(true), ..)` comes from safe titles only.
  - `options_are_four_distinct_with_the_answer_once`
  - `year_distractors_are_near_and_distinct`
  - `series_never_get_director_questions`
  - `too_few_titles_tops_up_with_general` — 2 films, mode `Films`, 8 rounds → 8 questions, at least 6 of them general.
  - `mixed_alternates_film_and_general`
  - `no_repeats_within_an_evening` — two calls sharing one `used` on a pool big enough for both return disjoint question keys.
  - `general_pack_is_well_formed` — parses; each entry has a non-empty category and text, 3 wrong options distinct from each other and from the answer.
- [ ] **Step 2: Run** `cargo test -p mediagram-game` — FAIL.
- [ ] **Step 3: Implement**; the dev binary now calls `start_game_night` with the fixture titles.
- [ ] **Step 4: Run** — PASS; clippy clean. Dev binary + desktop browser: a `Filme` quiz shows a still on the TV page; the phone page shows only four buttons.
- [ ] **Step 5: Commit** `feat(game): film questions from the library and a general pack`
