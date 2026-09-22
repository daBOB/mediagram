# Code standards

Conventions actually in force in this codebase, not aspirational ones.
Cross-reference [`docs/system-architecture.md`](system-architecture.md) for
the module map these rules apply to.

## Module layout

- One directory per concern (`commands/`, `media/`, `metadata/`, `index/`,
  `upload/`, `telegram/`, `verify/`); each has a thin `mod.rs` that only
  declares submodules and states, in its doc comment, what the directory is
  for and which submodule (if any) is the only one allowed to touch an
  external system (e.g. `verify/mod.rs` states that `download_hash` is the
  only piece of `verify` that talks to Telegram).
- Each `commands/*.rs` file exposes a `pub async fn run(...)` as its entry
  point and is the orchestration layer: it wires config, the index, and
  Telegram together and prints output. Logic a second command needs lives in
  a domain module (`index`, `upload`, `media`, ...), not in a sibling
  command. Decision logic that doesn't need IO (e.g.
  `verify::report`, `index::rescan::apply_seen`) lives in its own module so
  it can be unit-tested without a live connection or a temp database.
- **Every file under `src/` stays under 200 lines**, and
  `tests/code_standards.rs` fails the build when one does not. Test files are
  exempt — everything in `tests/`, and the `<module>_tests.rs` (or `tests.rs`
  beside a `mod.rs`) a module includes for its unit tests: a suite is a flat
  list of independent cases, and splitting it by line count would hide rather
  than clarify coverage. When a file would grow past
  that, split out a focused submodule (`media/mp4_atoms.rs` out of
  `media/remux.rs`; `verify/report.rs` out of `commands/verify.rs`) rather
  than letting one file accumulate unrelated responsibility.
- `snake_case` for files, functions, modules; `UpperCamelCase` for types.

## Error handling

- **`anyhow` at the edge, `thiserror` in libraries.** `mediagram` (the CLI
  crate) uses `anyhow::Result` everywhere and attaches context with
  `.context("what was being attempted")` at every fallible boundary — file
  IO, Telegram RPCs, SQL. `mlib-spec` (the pure data crate) defines typed
  `thiserror` enums (`CaptionError`, `PlanError`) because its errors are
  matched on by callers, not just displayed.
- Context messages describe the operation, not the failure ("uploading
  part 3 of 18" not "upload failed") — the underlying error already says
  what went wrong; the context says what was being attempted.
- No `unwrap()`/`expect()` on data that crosses a process or network
  boundary. The exceptions are `SystemTime::now().duration_since(UNIX_EPOCH)`
  clock-skew cases (`.unwrap_or_default()`) and genuine invariants proven
  earlier in the same function.

## Security

- `Config` has a hand-written `Debug` impl that redacts `api_hash` and
  `tmdb_key` as `<redacted>`; never derive `Debug` on a struct that carries
  a secret, and never `format!("{:?}", cfg)` a substructure that bypasses
  the redaction.
- The Telegram session file and its parent directory are `chmod 0600` /
  `0700` right after creation (`telegram::client::session_path` and
  `restrict_session_permissions`) since the session holds the account's
  auth key.
- Phone numbers are masked to their last 2 digits before printing
  (`commands::whoami::mask_phone`).
- Errors from HTTP clients (`reqwest`) must never surface a URL containing
  an API key; TMDB requests build the key into the query string, so error
  paths log/format the request path, not the full URL.

## Retry policy

`telegram::retry` has two entry points, and the choice between them is a
correctness property, not a style preference:

- `with_retry`: retries `FLOOD_WAIT` (sleeping the server-specified
  duration plus one second of slack) and 5xx-class server errors with
  exponential backoff. Use this for requests that are safe to repeat if the
  first attempt's response was merely lost — reads (`get_messages_by_id`,
  `iter_messages`), and RPCs like `pin_message`/`unpin_message` whose
  effect is idempotent (pinning an already-pinned message is a no-op).
- `with_flood_wait_only`: retries **only** `FLOOD_WAIT`. Use this for
  anything that is not idempotent — `send_message` in particular. If the
  server actually committed the send but the response was lost to a
  network blip, retrying with the general policy would post a duplicate
  part; instead, the caller's failure surfaces as an error and the next
  `resume` run's adoption scan (§ System architecture, "Resume and adopt")
  finds the already-posted part without re-uploading it.
- Some Telegram-facing code deliberately has **no** retry wrapper at all:
  `verify::download_hash::hash_document` streams a document through
  `iter_download` chunk by chunk without retrying a failed chunk, because
  `grammers-client` 0.10's `DownloadIter::next` silently ends the stream
  (returns `Ok(None)`) after any failed request instead of resuming it — a
  naive retry there would produce a wrong, truncated hash instead of a
  loud error. Read the vendored source before adding a retry around any
  new grammers API; do not assume a call is safely idempotent just because
  it looks like a read.

## Testing tiers

1. **Unit tests** (`#[cfg(test)] mod tests` inside the module) for pure
   logic: caption codec round-trips, part-plan math, `verify::report`
   verdicts, retry backoff math.
2. **Fixture-based integration tests** (`crates/*/tests/*.rs`) that exercise
   real IO against controlled inputs: a real sqlite file in a `tempdir()`,
   ffmpeg-built MP4/MKV fixtures (`media::test_fixtures`), or canned TMDB
   JSON responses served by `tests/support::FixtureApi`. No network access.
3. **`edge_cases_probe_*` suites** (`crates/*/tests/edge_cases_probe_*.rs`):
   boundary conditions and adversarial inputs for one area at a time (mp4
   atom scanning, config env overrides, part-plan boundaries, caption
   budget edge cases) — written after the "happy path" tests, specifically
   hunting for gaps the fixture tests didn't cover.
4. **`#[ignore]`d live tests** (`crates/mediagram/tests/live_add.rs`) hit
   the real Telegram API against a real, already-authorized session; they
   never run in CI or a default `cargo test`. Run explicitly with
   `MEDIAGRAM_LIVE=1 MEDIAGRAM_PART_SIZE=1048576 cargo test -p mediagram
   --test live_add -- --ignored --nocapture`, and only against a
   disposable test channel.

New logic gets unit or fixture coverage for its happy path and its
documented failure modes before being considered done; a bug fix gets a
regression test that would have caught it.

## Formatting and lint gates

- `cargo fmt --all -- --check` and `cargo clippy --all-targets -- -D
  warnings` must both be clean before a change is considered complete.
  `clippy::too_many_arguments` is allowed with an explicit `#[allow]` on
  the handful of functions (e.g. `index::parts::mark_done`) where a
  narrower struct would only add ceremony.
- Doc comments (`//!` module-level, `///` item-level) explain *why*, not a
  restatement of the signature — see the existing modules for the level of
  detail expected.

## No plan references in code

Code comments, file names, and commit messages describe the code's
invariants, trade-offs, and reasons — never a plan phase number, finding
code, or review-round label. Those references go stale the moment a plan
is renumbered or archived; a comment like "adopt scan avoids duplicate
sends after a lost response" stays true forever, "fixed per review round 2
finding 4" does not. Plan documents themselves (`plans/**/*.md`) are the
right place for that history.

## Commit messages

Conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`,
`chore:`), scoped when it adds clarity (`feat(mediagram): ...`), imperative
mood, no AI-authorship references in the subject or body. One logical
change per commit. The `Claude-Session:` trailer this repository's commits
carry is the documented exception: it is a machine-readable provenance
link, kept out of the human-readable message.
