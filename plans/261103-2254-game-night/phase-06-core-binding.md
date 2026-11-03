# Phase 06: Core binding: start, back, stop from Kotlin

Spec: "Hosting and joining", "Which titles the games use", "Separate from the library".

Outcome: Kotlin calls `startGameNight`, `gameNightBack`, `stopGameNight` on the core; the core builds the title list from its own catalog and credits; the Android `.so` embeds the built web client.

Context: the core exports proc-macro UniFFI (`crates/mediagram-core/src/lib.rs:6`, `#[uniffi::export(async_runtime = "tokio")] impl Core` blocks across `src/api/*.rs`); SQLite work goes through `self.blocking(...)` (`src/api/blocking.rs`). `SetSummary` (`src/dto/summary.rs`) carries `kind`, `title`, `show`, `year`, `poster_key`, `poster_path` and `backdrop_path` (local files, present only if on disk). Credits come from `crate::credits::for_title(&conn, kind, id)`, keyed through `shows::title_of(poster_key)`. Cast is in billing order; crew is the director(s) for films and the creators for series.

---

### Task 6.1: Titles from the catalog

**Files:**
- Modify: `crates/mediagram-core/Cargo.toml` (`mediagram-game = { path = "../mediagram-game" }`)
- Create: `crates/mediagram-core/src/game_titles.rs`, `src/game_titles_tests.rs`

**Interfaces:**
- Consumes: Task 3.1 `GameTitle`, `TitleKind`.
- Produces: `game_titles(sets: &[SetSummary], credits: &dyn Fn(&str) -> Option<TitleCredits>, kids_set_ids: &HashSet<String>) -> Vec<GameTitle>` (credits looked up by `poster_key`)
- Rules:
  - `kind == "movie"`: one film per set, `key = set_id`, directors = crew names, cast = cast names.
  - `kind == "ep"`: one series per `show`, `key = "show:{show}"`, `title = show`, `year` = the smallest episode year, poster/still from the first episode set that has one, `directors = []`, cast from the show's credits, `kids_ok` only if **every** episode set id is in `kids_set_ids`.
  - `tut`, `doc`, `docu`: left out.
  - `poster = poster_path`, `still = backdrop_path`, as `PathBuf`.

- [ ] **Step 1: Write the failing tests:** `films_map_one_per_set`, `episodes_group_into_one_show`, `courses_and_documents_are_left_out`, `a_show_is_kids_ok_only_if_every_episode_is`, `series_have_no_directors`, `kids_locked_keeps_only_kids_titles` (feed the result to `TitlePool::new(.., true)`; only ids in `kids_set_ids` survive).
- [ ] **Step 2: Run** `cargo test -p mediagram-core game_titles` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(core): game titles from the catalog and credits`

### Task 6.2: Exported start, back, stop

**Files:**
- Create: `crates/mediagram-core/src/api/game_night.rs`, `src/dto/game_night.rs`
- Modify: `src/api/mod.rs` (field `game_night: AsyncMutex<Option<mediagram_game::Host>>` on `Core`, initialised in `Core::new`), `src/dto.rs` (module), `scripts/build-android-core.sh`, `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` (regenerated), `android/core/testing/src/main/kotlin/testing/FakeCore.kt`

**Interfaces:**
- Consumes: Task 6.1 `game_titles`; Task 3.2 `start_game_night`; Task 1.3 `Host::{tv_url, join_url, back, stop}`.
- Produces (Kotlin sees camelCase):
  - `#[derive(uniffi::Record)] struct GameNightConfig { kids_set_ids: Vec<String>, kids_locked: bool, tmdb_key: Option<String> }` (`tmdb_key` is used from Task 6.3)
  - `#[derive(uniffi::Record)] struct GameNightUrls { tv_url: String, join_url: String }`
  - `Core::start_game_night(self: Arc<Self>, config: GameNightConfig) -> Result<GameNightUrls, CoreError>` — stops a running host first; reads sets and credits in one `blocking` call; port 8080 preferred
  - `Core::game_night_back(self: Arc<Self>) -> bool` — `false` when no host runs
  - `Core::stop_game_night(self: Arc<Self>)` — no-op when none runs
- `build-android-core.sh`: before `cargo ndk`, run `(cd crates/mediagram-game/client && bun install --frozen-lockfile && bun run build)` and fail with a clear message if `bun` is missing.
- `FakeCore`: `startGameNight` returns a configurable `GameNightUrls` (default `tv_url = "http://127.0.0.1:8080/tv"`, `join_url = "http://192.168.0.2:8080/"`) and records calls; `gameNightBack` returns a configurable `Boolean` (default `false`); `stopGameNight` counts calls.

- [ ] **Step 1: Write the failing test** in `src/api/game_night.rs`'s sibling test file (`game_night_tests.rs`): `start_twice_replaces_the_first`. If building a `Core` in a test needs Telegram credentials, put the start/replace/stop logic in a small helper over `AsyncMutex<Option<Host>>` that the exports call, and test the helper. The test: start, remember the port from `tv_url`; start again; the first port is free (`TcpListener::bind` on it succeeds) or reused by the new host; `stop_game_night` frees the port; `game_night_back` after stop is `false`.
- [ ] **Step 2: Run** `cargo test -p mediagram-core game_night` — FAIL.
- [ ] **Step 3: Implement**; run `scripts/generate-android-bindings.sh` (builds the client and the `.so` for all ABIs, regenerates the Kotlin bindings); add the `FakeCore` overrides.
- [ ] **Step 4: Run** `cargo test -p mediagram-core` — PASS; `cd android && ./gradlew :core:testing:compileKotlin :core:rust:compileDebugKotlin` — BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(core): start, return to lobby and stop a game night`

### Task 6.3: Textless posters

Spec: "Poster titles become readable before the last step; use a textless poster when TMDB has one, the normal one otherwise."

**Files:**
- Modify: `crates/mediagram-game/src/host.rs`, `src/server.rs`, `src/factory.rs`, `src/server_tests.rs`, `src/bin/game-night-dev.rs`
- Create: `crates/mediagram-core/src/game_posters.rs`, `src/game_posters_tests.rs`
- Modify: `crates/mediagram-core/src/api/game_night.rs`

**Interfaces:**
- Consumes: Task 3.1 image route, Task 4.1 `PosterGame`, Task 6.2 `GameNightConfig.tmdb_key`; `crates/mediagram-tmdb` (`tmdb_client.rs`, `poster_files.rs`) for requests and downloads.
- Produces:
  - `pub type PosterLookup = Arc<dyn Fn(String) -> Pin<Box<dyn Future<Output = Option<PathBuf>> + Send>> + Send + Sync>` in `mediagram-game`; the argument is the `GameTitle.key`
  - `host::start_game_night` gains `textless: Option<PosterLookup>` (the dev binary passes `None`)
  - `GET /img/{i}/poster`: with a lookup, await it for at most 3 s; serve its file if `Some`, else the pool poster
  - When the factory builds a `PosterGame`, it spawns the lookup for each round's title on the current tokio runtime, so the files are cached before their rounds
  - Core: `textless_poster(client, cache_dir: &Path, poster_key: &str) -> Option<PathBuf>`: TMDB `/{movie|tv}/{id}/images?include_image_language=null`, first poster, downloaded at `w500` to `{data_dir}/game-posters/{poster_key}.jpg`; a title with none gets a `{poster_key}.none` marker so it is not asked again; no key → `None`

- [ ] **Step 1: Write the failing tests:** game crate `lookup_poster_is_preferred`, `no_textless_poster_falls_back_to_the_normal_one`, `slow_lookup_falls_back_after_3_seconds` (`tokio::time::pause`); core `cached_textless_poster_needs_no_request`, `a_known_miss_is_not_asked_again` (both against the cache directory, with no network).
- [ ] **Step 2: Run** `cargo test -p mediagram-game -p mediagram-core textless` — FAIL.
- [ ] **Step 3: Implement**; `start_game_night` in the core passes a lookup when `tmdb_key` is set.
- [ ] **Step 4: Run** — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(game): textless posters from TMDB for the poster game`
