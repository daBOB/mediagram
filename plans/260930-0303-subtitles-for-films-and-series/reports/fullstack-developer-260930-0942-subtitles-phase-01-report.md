# Phase 01 implementation report — faststart remux keeps every stream; audio audit

## Scope
`crates/mediagram/src/media/{remux.rs,test_fixtures.rs}` (owned), plus the
new `crates/mediagram/src/media/remux_tests.rs` (test-only split, exempt from
the 200-line rule by the codebase's own `<module>_tests.rs` convention —
`remux.rs` alone landed at 286 lines with the fallback logic and both new
fixtures' tests), and the audit script under the plan's own `reports/`.

## What changed

### `faststart_args(streams: &[Stream]) -> Vec<String>`
Pure function, new. Builds `-map 0:V?` (video, ffmpeg's own uppercase-`V`
specifier already excludes attached pictures — no probe-side filtering
needed there), `-map 0:a?` (every audio stream, unfiltered), then one
`-map 0:<index>` per subtitle stream whose `codec` is `mov_text` or
`dvd_subtitle`, then `-c copy -movflags +faststart`. Data/attachment streams
(`StreamKind::Other`) are never mapped since nothing loops over them.

### `ensure_faststart`
Now probes with `streams::probe(src)` before remuxing (only on the path that
already needed a remux — `mp4_atoms::needs_faststart` still gates it first,
unchanged). Runs the mapped args via a new `run_ffmpeg` helper; on failure
(or if the probe itself fails) warns via `tracing::warn!` and retries once
with today's unmapped `-c copy -movflags +faststart`. The existing
faststart-verification bail-out and the three pre-existing tests are
unchanged.

### Test fixtures (`test_fixtures.rs`)
Two new ffmpeg-built fixtures:
- `make_trailing_moov_mp4_multi`: video + 2 audio (`ger`/`eng`) + 2
  `mov_text` subtitles (`ger` forced via `-disposition:s:0 forced` — not
  `-disposition:s:s:0`, which ffmpeg rejects as "Stream type specified
  multiple times"; caught by the ffmpeg-gated test itself, fixed before
  reporting), `eng` plain.
- `make_trailing_moov_mp4_unmuxable_stream`: video + a **second, raw/
  uncompressed video stream** + audio, written with ffmpeg's `mov` muxer
  (lenient) but named `.mp4` — see below for why this replaced the plan's
  suggested eia_608/PCM case.

## Deviation from the plan's suggested refused-stream fixture — verified empirically

The plan (phase-01, step 3) named "an `eia_608` track or PCM audio" as the
candidate for the fallback fixture, to be settled by probing this box's
ffmpeg (n9.0.2, confirmed installed). I tested both against the real
binary before writing any fixture code:

- **PCM audio** (`pcm_s16le`, `pcm_s24le`, `dts`) explicitly mapped into an
  mp4 muxer: all three succeeded (exit 0) in this ffmpeg build. Not usable —
  it never refuses, so it can never exercise the fallback path.
- **`eia_608`** (built via the `scc` demuxer, muxed into a `mov`-as-`.mp4`
  source, confirmed a real `eia_608` subtitle stream by `ffprobe`): an
  explicit `-map` of it into `mp4` genuinely fails ("Could not find tag for
  codec eia_608 ... not currently supported in container"). But
  `faststart_args` **never maps it in the first place** — it only maps
  `mov_text`/`dvd_subtitle` subtitle streams by design, so this exact
  fixture would never reach ffmpeg through our own code; the fallback would
  never trigger.

The residual "explicit map forces something a blanket copy would have
silently dropped" risk therefore lives only in the **unfiltered** blanket
maps — video (`0:V?`) and audio (`0:a?`) — not subtitles, since those are
already filtered client-side. I verified a second, non-default video stream
encoded `rawvideo` reproduces the exact scenario end to end: today's
un-mapped remux silently keeps only the first (h264) video stream and
succeeds; the mapped remux forces the raw stream in via `0:V?` and the mp4
muxer refuses it ("Could not find tag for codec rawvideo ... not currently
supported in container"). Both `ensure_faststart` fallback behavior and the
final `needs_faststart` check on the fallback output were confirmed by the
`falls_back_when_the_mapped_remux_is_refused` test, ffmpeg-gated, passing.

This is a genuine, reproducible trigger of the same failure class the plan
was probing for — an unmuxable stream forced in by an explicit map that
default selection would have dropped — via a codec this ffmpeg build
actually refuses, rather than one it happens to accept.

## Forced-flag verification
`Stream` (this crate's probe model) has no disposition field, and this phase
doesn't add one (that's out of scope — the plan's `Related code files` list
only `remux.rs`/`test_fixtures.rs`). The `remux_keeps_every_stream_and_the_
forced_flag` test shells out to `ffprobe -select_streams s:N -show_entries
stream_disposition=forced` directly to check the German (forced) vs English
(plain) subtitle tracks survive the remux with dispositions intact.

## Audit script
`plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh`
(132 lines). `audio` mode only (any other mode errors cleanly; `subs` is a
later addition to this same script — not implemented here). Verified pieces
individually, since running it for real needs a live channel:
- The SQL (`status='complete' AND container='mp4' AND kind IN ('movie','ep',
  'docu') AND json_array_length(alang)>1`) — ran against a synthetic sqlite3
  DB built from the exact `sets` table DDL in `mlib-spec/src/schema_versions.rs`
  (V1); correctly returned only the matching row.
- The `lang_code` jq function — mirrors `classify::lang_code` in
  `crates/mediagram/src/media/classify.rs:49-81` byte-for-byte (all 19
  three-letter mappings, `und`/empty → dropped, 2-letter passthrough,
  unknown passthrough) — ran against real `ffprobe` JSON output from the
  fixtures above, both a full-match case (`missing_audio` empty) and a
  degraded case (`missing_audio` lists both languages).
- The full script, end to end: a fake `mediagram serve` (a stand-in shell
  script backed by `python3 -m http.server` over a directory shaped like
  `/sets/<id>/stream`) plus a synthetic `library.db`, covering the readiness
  poll, the `pgrep` upload guard, the trap-based cleanup (confirmed via `ss
  -ltnp` — no leftover listener after the script exits, and an empty-DB run
  under `set -u` prints a clean zero-row TSV + totals line instead of an
  "unbound variable" error).
- **Not** run: against a real `mediagram`/channel — explicitly out of scope
  here ("the lead runs the audit against the real channel").

## File ownership note
Split `remux.rs`'s tests into `remux_tests.rs` to satisfy
`crates/mediagram/tests/code_standards.rs`'s 200-line rule (`scripts/check.sh`
fails otherwise — this is not optional). Followed the codebase's own existing
convention exactly (e.g. `cli.rs` / `cli_tests.rs`): `#[cfg(test)] #[path =
"remux_tests.rs"] mod tests;`, a file the line-limit test itself exempts by
name. No other phase touches `media/remux*.rs`.

## Version
0.83.0 → 0.83.1 in `Cargo.toml` (workspace), `web/package.json`,
`android/app/build.gradle.kts` (`versionName`), and `Cargo.lock`'s five
workspace-member entries (`mediagram`, `mediagram-cache`, `mediagram-core`,
`mediagram-tmdb`, `mlib-spec`); `cargo metadata --locked --offline`
confirms the lockfile is internally consistent. Changelog entry added at
the top of `docs/project-changelog.md`.

## Tests
- `cargo test -p mediagram --lib remux::` — 6/6 pass, including both new
  ffmpeg-gated tests (`remux_keeps_every_stream_and_the_forced_flag`,
  `falls_back_when_the_mapped_remux_is_refused`).
- `cargo clippy -p mediagram --all-targets --all-features -- -D warnings` —
  clean.
- `bash scripts/check.sh` — green end to end: clippy, full `cargo test`
  (178 lib tests + every integration suite, 0 failures), `bun test` skipped
  (no `node_modules` — pre-existing setup state, unrelated), gradle
  `testDebugUnitTest`/`lint`/`compileDebugAndroidTestKotlin` — `BUILD
  SUCCESSFUL`. Copied the four gitignored `.so` files (`libmediagram_core.so`,
  `libffmpegJNI.so`, all four ABIs) from the main checkout into this
  worktree to let the Android/gradle leg of `check.sh` run at all
  (`ANDROID_HOME` was set); did not modify anything in the main checkout.

## Concerns
- None blocking. The one open item is inherent to the phase, not a defect:
  the audit's real numbers (`missing_audio` counts, `reports/rollout-log.md`)
  can only come from a run against the live channel, which this phase
  explicitly defers to the lead.

## How the lead should run the audit
```
cd <repo root, with the real installed uploader on PATH>
mediagram pull-index   # if not already current
plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh audio \
  > plans/260930-0303-subtitles-for-films-and-series/reports/remux-audio-audit-$(date +%y%m%d).tsv
```
Requires: `mediagram`, `sqlite3`, `ffprobe`, `jq`, `curl` on `PATH`; no
`mediagram add`/`add-course`/`add-show`/`add-docu`/`resume`/`finish-set`
running (the script refuses and exits 1 if it sees one via `pgrep`). Reads
`~/.local/share/mediagram/library.db` by default (override: second
argument, or `MEDIAGRAM_DATA_DIR`). Starts its own `mediagram serve` on
loopback `127.0.0.1:8799` and tears it down via a trap on exit — never
touches the channel beyond reading already-uploaded bytes through that
loopback serve. After the run, add the `missing_audio` count to
`reports/rollout-log.md` per the phase's own todo item (not done here —
needs the real numbers).
