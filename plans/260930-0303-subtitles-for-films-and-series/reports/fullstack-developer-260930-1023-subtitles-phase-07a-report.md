# Phase 07a — measure before backfill: implementation report

## Executed Phase
- Phase: phase-07a-measure-existing-titles-before-backfill.md
- Plan: /home/andre/Workspace/mediagram/plans/260930-0303-subtitles-for-films-and-series
- Worktree: /home/andre/Workspace/mediagram/.claude/worktrees/agent-a33bd71a3a0113f49
- Branch: worktree-agent-a33bd71a3a0113f49
- Status: completed (build only — no operator runs; those are the lead's per the task)

## Base merge
1. `git merge --ff-only worktree-agent-aa581a9d67fed3199` (phase 02, tip `5a29b419`, 0.84.0) — fast-forwarded cleanly.
2. `git merge worktree-agent-a85cb9380467b6523` (phase 01, tip `1c94e390`, 0.83.1) — conflicts in `Cargo.toml`, `Cargo.lock` (5 workspace-crate version stanzas), `android/app/build.gradle.kts`, `web/package.json`, `docs/project-changelog.md`. Resolved every version conflict to `0.84.0`; changelog resolved with the 0.84.0 entry above 0.83.1, both kept in full. Merge commit `8c267a4e`.
3. Confirmed the merged base built (`cargo build --workspace`, `cargo metadata --locked --offline`) before touching anything else.

## Files Modified
- `crates/mediagram/src/commands/subtitles/mod.rs` (54 lines, new) — `SubtitlesAction`/`BackfillArgs`, dispatch, `--dry-run` required-for-now guard.
- `crates/mediagram/src/commands/subtitles/match_source.rs` (154 lines, new) — pure matcher: exact-size match, ambiguous, mp4-only name+duration fallback, conflict resolution.
- `crates/mediagram/src/commands/subtitles/match_source_tests.rs` (182 lines, new) — the 7 required cases plus a fallback-ambiguity case.
- `crates/mediagram/src/commands/subtitles/dry_run.rs` (88 lines, new) — walk, load candidate sets, probe, call the matcher.
- `crates/mediagram/src/commands/subtitles/dry_run_report.rs` (194 lines, new) — TSV + totals by kind/container + the unmatched-de/en-slang list.
- `crates/mediagram/src/commands/mod.rs` (+1) — `pub mod subtitles;`.
- `crates/mediagram/src/cli.rs` (+6/-1) — `Cmd::Subtitles { action: SubtitlesAction }`.
- `crates/mediagram/src/main.rs` (+1) — dispatch arm. **Not in the phase's listed file ownership**, but required for the crate to compile (`Cmd` is matched exhaustively, no wildcard arm); no other running phase claims this file. Flagging per the ownership rule rather than silently touching it.
- `crates/mediagram/src/media/prepare/plan.rs` (+2/-1) — `PICTURE_SUBTITLES` → `pub(crate)`, one line as specified.
- `plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh` (175 lines) — added `subs` mode alongside `audio`; factored the shared ffprobe call into `probe_set()`.
- `docs/project-changelog.md` — new 0.85.0 entry above 0.84.0.
- `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`, `android/app/build.gradle.kts` — 0.84.0 → 0.85.0.

## Design decisions (resolving spec ambiguity)
- **Pool query**: `SELECT * FROM sets WHERE status='complete' AND kind IN ('movie','ep','docu')`, mapped with the existing `SetRow::from_row` (`pub(crate)`, same crate) rather than duplicating its column list — reuse over a parallel struct.
- **Probing**: every walked file is probed once via the existing `media::streams::probe` (ffprobe only, no ffmpeg extraction), before matching. This needs a probe to get a candidate's duration before the fallback can even be attempted, and the same probe's stream list is reused for the de/en-text/picture-only counts if the file ends up matched or fallback. Marked with a `ponytail:` comment naming the ceiling (large uncurated folders would waste probes on files that end up unmatched) and the upgrade path (a first size-only pass, probing only what it leaves undecided). Given the runbook's folders are curated media folders, not drive roots, this was the simpler correct choice over threading a two-phase probe split through `dry_run.rs`.
- **Fallback name rule**: episode guesses (`season` + `episode` both present) compare `show` + season + episode-range-membership; everything else with a `year` compares `title` + `year`. An absolute-numbered anime guess or a bare title matches neither shape and is correctly `Unmatched`.
- **TSV/totals shape**: the phase text left the totals' exact layout open ("totals per kind/container and in bytes"); implemented as one `(kind, container)`-keyed table for matched, one for fallback, plus scalar ambiguous/conflict/unmatched lines — literal enough to satisfy "per kind/container" without inventing a heavier report format.
- **CLI shape**: `mediagram subtitles backfill <FOLDER>... --dry-run`, structured as a growable `SubtitlesAction` subcommand enum so phase 07 can add `move-inline` and extend `Backfill` (sending, `--accept-fallback`, `--redo`) without a CLI redesign — matches the args-co-located-with-command-module pattern already used by `push_index.rs`/`pull_index.rs`.

## Tasks Completed
- [x] matcher + tests (9 unit tests, all passing)
- [x] dry-run command
- [x] script `subs` mode
- [x] check.sh, manifests, changelog
- [ ] runs on this machine (lead — build only per task)
- [ ] runs on the other machine (lead)
- [ ] decision table in `reports/`, linked from `reports/rollout-log.md`; user's scope recorded in phase 07 (lead, after both runs)

## Tests Status
- Type check / build: pass (`cargo build --workspace`, `cargo metadata --locked --offline`)
- Unit tests: pass — `cargo test -p mediagram` 197+ passed, 0 failed, including the 9 new `commands::subtitles::match_source::tests::*`
- Clippy: pass (`cargo clippy --all-targets --all-features -- -D warnings`, whole workspace)
- Full `scripts/check.sh`: **green** — clippy, `cargo test --all`, gradle `testDebugUnitTest`/`lint`/instrumented-compile (bun test skipped: no `node_modules` in this worktree, no web files touched beyond the version bump)
- Manual smoke: ran the whole pipeline once (temp `#[tokio::test]`, deleted after) against a real ffmpeg-built fixture (2 audio langs, 2 `mov_text` subtitle tracks ger+eng) and a freshly migrated in-memory-style temp `library.db` with one matching `movie` set. Output: `matched  <set>  movie  Alpha  2  0` plus correct totals — confirms the DB read, walk, probe, match and report wiring end to end before deleting the scratch test.
- Shell script: `bash -n` syntax check passed; the `subs` mode's jq filter and the `json_each(slang)` SQL clause were each verified standalone against synthetic ffprobe JSON / an in-memory sqlite table (see below) — real de/en text vs. picture-only classification confirmed correct, non-de/en text correctly excluded, non-complete/empty-slang rows correctly excluded.

## Issues Encountered
- `main.rs` required a one-line dispatch addition though not in phase 07a's listed ownership — see note above; no conflict since no other phase currently touches it.
- Cargo.lock conflicts were purely the 5 workspace-crate version stanzas (no dependency-graph changes from phase 01's remux work); resolved mechanically.

## Next Steps
The command and script are ready for the lead's operator runs (phase 07a's own next steps, not part of this build):

### Runbook (lead)

**This machine** (after this branch is merged/checked out and installed):
```
cargo install --path crates/mediagram --locked
mediagram --version   # confirm it matches this branch
mediagram pull-index
# confirm mounts before walking — <second data drive> errored I/O today
findmnt <media drive> <NAS share> <second data drive>

mediagram subtitles backfill \
  <media drive>/<series folder> \
  <NAS share> \
  <second data drive> and series folders only, never the drive root> \
  --dry-run > plans/260930-0303-subtitles-for-films-and-series/reports/backfill-dry-run-<machine>-<date>.tsv

plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh subs \
  > plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-subs-<machine>-<date>.tsv
```
No upload may be running (the script self-checks via `pgrep`; the backfill command itself only reads, but the shared Telegram session used by `serve` still applies to the script). Expect the dry run to take a while if the media folders are large — it probes every walked file once; the header-probe script paces per set through `mediagram serve` similarly to the existing `audio` mode. Bring back both TSVs.

**Other machine** (`<other machine's media folder>`):
```
git pull   # or however this branch reaches it
cargo install --path crates/mediagram --locked
mediagram pull-index
mediagram subtitles backfill <its own media folders, never drive roots> --dry-run \
  > .../reports/backfill-dry-run-<other-machine>-<date>.tsv
.../reports/uploaded-stream-headers.sh subs \
  > .../reports/uploaded-subs-<other-machine>-<date>.tsv
```
The header-probe totals from both machines should agree (± sets pushed in between) since it reads the whole channel from either side; the backfill dry run differs per machine's own reachable sources.

**Then**: fill `reports/backfill-measurements-<date>.md`'s bucket table (S/U/N/P) from the four TSVs, link it from `reports/rollout-log.md`, and get the user's film/series scope decision for phase 07.

## Unresolved Questions
None blocking. One judgment call worth the lead's eyes: the dry run probes every walked file unconditionally (see "Probing" above) rather than deferring probes for files a cheap size-only pass would leave undecided — fine for curated media folders per the runbook, would want revisiting only if a folder turns out much larger than expected.
