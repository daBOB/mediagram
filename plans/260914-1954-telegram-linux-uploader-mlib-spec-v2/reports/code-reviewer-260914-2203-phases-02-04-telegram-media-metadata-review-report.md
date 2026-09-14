# Code Review: phases 2-4 (Telegram auth, media inspect/remux, TMDB resolve)

Scope: commits `cbf9610`, `3cdc6dd`, `c034fef`, `a39e1c8` on `main`; 22 source files + 3 test files, ~2,300 LOC. All grammers 0.10.0 / reqwest 0.13.5 claims below verified against `~/.cargo/registry/src/*/`. No files modified.

Build state at review time: `cargo build`, clippy `-D warnings`, fmt clean. `cargo test` on the **committed** tree is green (92). The working tree also holds an untracked probe file (see M8) whose 2 failures make `cargo test` red as-is.

## Overall assessment

Solid, small, well-factored. All grammers calls match the 0.10.0 signatures; the fixture-driven TMDB tests cover the plan's three phase-4 criteria; the mp4 scanner reads only 8-16 bytes per box. The findings that matter are (1) a TMDB api_key leak through reqwest error messages, (2) session sidecar files not covered by the 0600 chmod, (3) a u64 overflow / infinite-loop path in the atom scanner on crafted input, and (4) two resolve-path behaviours that contradict the plan's own tolerance rules.

## Critical

### C1. TMDB `api_key` leaks into error output via reqwest error chain
`crates/mediagram/src/metadata/tmdb_client.rs:52` appends `api_key` to the URL query. Both `send().await.with_context(...)` (line 60-65) and `resp.json().await.with_context(...)` (line 80-83) wrap a `reqwest::Error` whose `Display` includes `" for url ({url})"` (verified: reqwest-0.13.5 `src/error.rs:44-45`). `main.rs:53` returns `anyhow::Result`, so any network failure prints the full "Caused by:" chain to stderr, including `?api_key=...`. Same leak for any `tracing::error!(%err)` a later phase adds.

Fix (pick one):
- `.map_err(reqwest::Error::without_url)` on both `send()` and `json()` results (`without_url` exists at reqwest error.rs:91); or
- send the key as `Authorization: Bearer <v4 token>` header instead of a query param (also removes it from proxy logs).

Cache key (line 107-120) and cache file contents are key-free; no leak there.

## High

### H1. Session sidecar files (`-wal`, `-shm`) and data dir are not permission-restricted
`telegram/client.rs:62-66` opens the session, then chmods only `session.sqlite` to 0600. libsql opens local DBs with `PRAGMA journal_mode = WAL` (libsql-0.9.30 `src/local/connection.rs:85`), so `session.sqlite-wal` / `session.sqlite-shm` are created with the process umask (typically 0644) and the WAL holds the auth key until checkpoint. Also a brief window where the main file itself is 0644 before line 66 runs.

Fix: `create_dir_all` then `set_permissions(dir, 0o700)` in `session_path` (line 153-158); that covers every file libsql creates. Optionally pre-create the file with `OpenOptions::new().create(true).mode(0o600)` before `SqliteSession::open`.

### H2. mp4 atom scanner: `pos += box_size` can overflow → debug panic / release infinite loop
`media/mp4_atoms.rs:84`. A crafted or corrupt `largesize` (line 57) of e.g. `u64::MAX - pos + 1` wraps `pos` to 0 in release builds, restarting the scan forever; in debug it panics. Truncation is otherwise handled correctly (loop guard line 37), and memory use is bounded (only 8+8 byte reads) - good.

Fix: `pos = pos.checked_add(box_size).ok_or_else(|| anyhow!("{}: box size overflow at {pos}", ...))?;` and additionally bail if `box_size > file_len - pos` when a box that is not `mdat`/`moov` claims to run past EOF (currently silently ends the loop and may return `Ok(false)` for garbage input, which is acceptable but worth a `tracing::warn`).

### H3. Server-side year filter defeats the plan's "year ±1" plausibility rule
`metadata/search.rs:30-38` sends `year` / `first_air_date_year` to TMDB. `first_air_date_year` is an exact filter; a filename `Show (2022)` for a show TMDB dates 2021-12 returns zero results → `bail!("no tmdb results …; retry with --manual")` (line 46-51) before the client-side ±1 window (line 59-68) ever runs. The plan requires ±1 tolerance; the client-side filter already implements it, so the server param is redundant and harmful.

Fix: drop the year params from the query (cache key stays stable), or on empty results retry once without them.

## Medium

### M1. `--imdb` value not normalised to `tt` prefix; explicit value not preserved
`commands/args.rs:16` documents "with or without the `tt` prefix", but `metadata/resolve.rs:62-63` passes it verbatim to `/find/{id}?external_source=imdb_id`, which TMDB rejects without `tt`. `mlib-spec/src/ids.rs:9` also requires the prefix in `ProviderIds.imdb`. Additionally, unlike `--tvdb` (resolve.rs:74-76), an explicit `--imdb` is not written back into `item.ids.imdb`; if TMDB's `external_ids.imdb_id` is null the user's value is lost.
Fix: normalise once at the top of `resolve` (`if !s.starts_with("tt") { format!("tt{s}") }`) and mirror the tvdb write-back.

### M2. `with_retry` retries every error, including permanent ones
`telegram/retry.rs:39-46`: non-FLOOD_WAIT errors get exponential backoff regardless of kind. `AUTH_KEY_UNREGISTERED` / `SESSION_REVOKED` (401), `CHAT_WRITE_FORBIDDEN` / `CHANNEL_PRIVATE` (400/403) and `MESSAGE_ID_INVALID` will be retried 5× (≈3 s wasted) before surfacing. It does not swallow errors (last error is propagated - good), and FLOOD_WAIT semantics (value+1 s, counts toward budget) match the phase notes.
Fix: treat `InvocationError::Rpc(e)` with `e.code == 401 || e.code == 403 || (e.code == 400 && e.name != "FLOOD_WAIT")` as non-retryable and return immediately. Keep retrying `InvocationError::Dropped`/io-style errors and 5xx.

### M3. Phase-5 contract hazard: retrying `send_message` is not idempotent
`commands/smoke_upload.rs:28-41` wraps `send_message` in `with_retry`. grammers mints a fresh `random_id` per call (`client/messages.rs:22`), so a request whose response is lost after the server applied it will post a duplicate on retry; in the smoke test the orphan is then never deleted. Fine for a hidden smoke command, but phase 5 must not copy this pattern for part uploads without a post-failure scan (the plan's "adopt unrecorded parts" logic) before re-sending.

### M4. Remux leaves partial output behind on failure
`media/remux.rs:47-57`: if ffmpeg exits non-zero, or the post-remux `needs_faststart` check fails, `dest` (possibly multi-GB) stays on disk. No collision guard either: with a shared `tmp_dir`, two sources with the same stem in different directories map to the same `<stem>.faststart.mp4` (line 36) and `-y` silently overwrites.
Fix: `let _ = std::fs::remove_file(&dest)` on both failure paths; consider a short hash of the source path in the temp name when `tmp_dir` is set.

### M5. TMDB cache stores empty search responses forever
`metadata/tmdb_client.rs:134-139` caches any successful body, including `{"results": []}`. A title added to TMDB after the first attempt is never found again for that filename until the user deletes `tmdb-cache/`. No TTL or purge command exists.
Fix: in `DiskCachedApi` skip the write when `value["results"]` is an empty array, or expose a `--no-cache` flag in phase 5.

### M6. Non-JSON error bodies hide the HTTP status
`tmdb_client.rs:80-86`: a 5xx/HTML or empty body fails at `resp.json()` with "was not JSON", losing the status that would have been reported on line 85. Read `text()` first, then parse.

### M7. `--tmdb <id>` kind inference can hit the wrong endpoint
`resolve.rs:58-61`: kind comes from flags/filename only, so `--tmdb 95396` on `Severance.mkv` (no episode marker, no `--season`) queries `/movie/95396` and 404s with an opaque error. Either require `--season/--episode` (or `--movie`/`--show`) with `--tmdb`, or fall back to `/tv/{id}` on 404. Also, the plan states "`--season/--episode` required for `t=ep` unless parsed from filename" - `resolve` does not enforce this (an `--abs`-only input yields `Kind::Ep` with `season: None`); confirm phase 5's caption validation is where this is enforced.

### M8. Untracked probe test makes the working tree red
`crates/mediagram/tests/edge_cases_probe_config_retry.rs` (untracked, 22:06, not part of the reviewed commits) fails 2/23. Its tests mutate process env via `unsafe { env::set_var(..) }` in parallel (`lines 27, 51`), which races with `config_requires_api_hash_and_channel`, and at least one classify boundary expectation contradicts the implemented thresholds. It must not be committed as-is; either delete it or rewrite with `--test-threads=1`-safe env handling before it lands.

## Low

- L1. `commands/smoke_upload.rs:3` comment says "phase-1 assumptions" - plan reference in code, violates the no-plan-refs rule; reword to "confirms the 4 GB cap and FLOOD_WAIT shape".
- L2. `config.rs:12` `Config` derives `Debug` with `api_hash`/`tmdb_key` in plain fields. No `{:?}` use today (grepped), but a hand-written `Debug` that redacts both fields removes the foot-gun. `MEDIAGRAM_TMP_DIR`, `_THROTTLE_MS`, `_MAX_ATTEMPTS` are documented as env-overridable in the doc comment but `apply_env` (line 71-96) skips them.
- L3. `telegram/client.rs:128-138`: a channel whose title is purely numeric can never be matched by title (numeric parse wins); duplicate titles resolve to the first dialog silently. Acceptable; document in config.example.toml. `iter_dialogs` walks every dialog on every command; fine for a personal account.
- L4. `media/test_fixtures.rs` is compiled into the release binary (`media/mod.rs:8`, no `cfg`) after the lib refactor. Gate with `#[cfg(any(test, feature = "test-fixtures"))]` or accept the dead code.
- L5. `metadata/search.rs:88` indexes `candidates[idx]` from a trait return; a misbehaving `Prompter` panics. `.get(idx).context(...)`.
- L6. `metadata/prompt.rs:105` silently drops an unparsable year in manual entry (`parse().ok()`); a validation loop or `bail!` is friendlier.
- L7. `media/classify.rs:46-78`: unknown 3-letter codes pass through unchanged, so `alang` mixes 639-1 and 639-2 (`["en","hun"]`). Either extend the map or normalise via a small table; the caption spec should say which it expects.
- L8. `inspect.rs:43`, `remux.rs:40-42`: a path beginning with `-` is parsed by ffprobe/ffmpeg as an option. Prefix relative paths with `./` (or canonicalize) before spawning.
- L9. `resolve.rs:85-89` swallows episode-title fetch errors with no log; a `tracing::debug!` helps diagnose "why is title empty".
- L10. No README exists yet; phase 2 says the admin requirement and smoke-test procedure are "documented in README". Phase 7 owns docs, so just track it.

## Explicit checks requested

(a) grammers API: `SqliteSession::open` (session `storages/sqlite.rs:208`), `SenderPool::new(Arc<S>, i32)` (`sender_pool.rs:160`), pub `runner`/`handle` fields (`:84-87`), `Client::new(SenderPoolFatHandle)` (`client/net.rs:53`), `is_authorized` (`auth.rs:103`), `request_login_code(&str, &str)` (`:242`), `sign_in(&LoginToken, &str)` (`:339`), `check_password(PasswordToken, impl AsRef<[u8]>)` (`:416`), `PasswordToken::hint -> Option<&str>` (`:74`), `iter_dialogs` (`dialogs.rs:158`), `upload_file` (`files.rs:504`), `send_message<Into<PeerRef>, Into<InputMessage>>` (`messages.rs:593`), `delete_messages(peer, &[i32])` (`:869`), `get_me` (`chats.rs:410`), `User::full_name/phone` (`user.rs:145/189`), `Channel::title -> &str` (`channel.rs:148`), `Dialog::peer/peer_id/peer_ref` (`dialog.rs:74-87`), `PeerId::bot_api_dialog_id/bare_id` (`peer.rs:243/259`), `PeerRef: Copy` (`:48`), `handle.quit()` (`sender_pool.rs:146`), `RpcError{code,name,value,caused_by}` (`errors.rs:79`). All calls in `client.rs`, `login.rs`, `whoami.rs`, `smoke_upload.rs` match. Live login/smoke remain unrun (no credentials) - honestly marked in phase-02.
(b) Covered by C1, H1-H3, M1-M7. Channel `-100` handling is correct: `bot_api_dialog_id` is the `-100…` form, `bare_id` strips it (`peer.rs:266`). HDR precedence DV > smpte2084 > arib-std-b67 > SDR is correct. ffprobe model tolerates every missing field (`inspect.rs:130-161`, all `Option`/`default`). Auto-pick rule (exactly one plausible, similarity ≥ 0.9) and prompt-exactly-once match the plan (`search.rs:70-88`, test `tmdb_resolve.rs:66-85`). `--tvdb` verbatim: `resolve.rs:74-76`. Episode title non-fatal: `resolve.rs:85`.
(c) Secrets: no `api_hash`, `tmdb_key` or phone in any `println!`/`tracing` call; phone masked (`whoami.rs:22-28`); `RpcError` Display carries only code/name/value. Only leak path is C1.
(d) Contracts: all `run` signatures match `main.rs:61-68`. Public names present: `MediaInfo`/`inspect` (`inspect.rs:17/33`), `needs_faststart` (`mp4_atoms.rs:22`), `ensure_faststart` (`remux.rs:19`), `ResolveInput`/`ResolvedItem`/`resolve` (`resolve.rs:17/30/44`), `TmdbApi`/`TmdbClient::with_cache`/`DiskCachedApi` (`tmdb_client.rs:18/39/94`), `Prompter`/`DialoguerPrompter` (`prompt.rs:12/20`). Phase 5 note: `tmdb_key` is `Option<String>`; `with_cache` takes `&str`, so phase 5 must bail with a clear message when unset and non-manual.
(e) Every file < 200 lines (max 189); snake_case throughout; one plan reference (L1); no new lint/build errors on the committed tree.

## Plan status

- Phase 2: 3 of 4 criteria unrun (need credentials) - correctly left unchecked. FLOOD_WAIT unit test passes.
- Phase 3: all 3 criteria met (DV via unit test on the exact function `inspect` calls - acceptable).
- Phase 4: all 3 criteria met by fixture tests.
- Recommend lead: fix C1, H1, H2 before phase 5 starts (phase 5 builds on all three); H3/M1 before first real `add`.

## Unresolved questions

1. Should `Kind::Ep` without season/episode (abs-only anime) be rejected in `resolve` or in phase-5 caption validation? Plan text implies `resolve`.
2. Is a v4 bearer token acceptable for TMDB auth (cleanest fix for C1), or must v3 `api_key` remain for the free tier setup the user already has?
