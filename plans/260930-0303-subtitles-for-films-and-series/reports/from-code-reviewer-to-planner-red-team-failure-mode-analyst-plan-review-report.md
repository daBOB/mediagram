# Red team: failure-mode analyst / flow tracer — subtitles plan

Scope: `plan.md`, phases 01–08, `reports/codebase-survey-subtitles.md`, all traced against the working tree on 2026-09-30.
The survey's claims hold: every reader accepts a newer schema (`web/src/catalog.ts:58-69`, `crates/mlib-spec/src/package/mod.rs:157-163`, `web/src/package/pointer.ts:148`), `push-index` without `--force` pulls first (`commands/push_index.rs:18-22`), `retireOtherChunkSizes` only removes names made of digits, and `is_mlib` only matches `#mlib v=` (`caption_codec.rs:161-162`).
The findings below are the places where a flow breaks anyway.

## Finding 1: The backfill's duration fallback checks uniqueness against a pool that shrinks, so re-runs attach another title's subtitles
- **Severity:** High
- **Location:** Phase 07, section "Requirements" (candidates, size match, then the `.mp4` duration fallback) and "Runbook" step 5
- **Flaw:** Candidates are "complete sets **without** a `subtitle_files` row". The fallback accepts "exactly one candidate" with matching duration ±2 s, the same container, and SxxEyy only "when the file name carries one". Show or title is never compared. Every set that gets a bundle leaves the pool. Sets with no de/en text never get a row, so they stay in it forever. "Unique" therefore means unique among what is left, not unique among the library.
- **Failure scenario:**
  - Run 1 bundles show A S01E03 (mp4, 42:10).
  - Run 2 is required by the runbook ("re-run until it reports nothing left to send"). A's file no longer size-matches any candidate, because A left the pool, so it falls through to the fallback.
  - Show B S01E03 (mp4, 42:11) is still in the pool, because its own file has no de/en text. It is now the only candidate, so A's German/English tracks are extracted, recorded on B, published and merged to every client.
  - B now has a row, so no later run ever reconsiders it.
  - The same happens on run 1 with any stray mp4 in the folders: extras, never-uploaded files, or files uploaded by the other machine and not yet pulled. Films carry no SxxEyy, and the mp4 movie/docu pool is only 57 sets, so duration alone decides.
- **Evidence:**
  - Plan: "Candidates: complete `movie|ep|docu` sets without a `subtitle_files` row … size match (unique) → else, `.mp4` only, the duration fallback".
  - The premise "only faststart-remuxed MP4 sources differ in size" is also false. `mediagram prepare --out` writes a rewritten copy into another tree, optionally converting to mp4 (`media/prepare/paths.rs:31-47`, `commands/prepare/mod.rs:47,72`), and `--replace` rewrites the original (`media/prepare/plan.rs:1-6`).
  - `add --delete-source` removes sources after upload (`commands/args.rs:75-78`, `commands/add.rs:18`).
  - The size-uniqueness count (3,759 of 3,760) was taken on the local 5,005-set index, not the 5,910-set channel.
- **Suggested fix:**
  - Match against all complete sets, whether they already have a bundle or not.
  - Never send a fallback match for a file whose size equals any set's `total`.
  - Require the parsed show/title to agree (`parse_filename` already yields it), and reject the match if two files claim the same set.
  - Make the dry-run's fallback matches a separate list that the operator has to approve, e.g. `--accept-fallback <file>`, rather than sending them automatically.

## Finding 2: The staged-bundle sweep races `add` planning, and nothing but an upload session ever sends a staged bundle
- **Severity:** High
- **Location:** Phase 06, "Key decisions" 4 and "Requirements" (`upload.rs` "delete orphan staged files whose set is gone"; `end.rs` hook)
- **Flaw:**
  - `add` plans in the foreground and holds no lock while it does, then hands the set to a background `finish-set` process. Several such processes run and end independently.
  - The plan writes the staged file "after the set id exists" (`prepare_set.rs:83`). The set row only lands at `record_planned` (`:142`), after TMDB/credits/franchise network calls (`:126-137`).
  - Any other process reaching its session end in that window runs `send_staged`, sees a staged file whose set is "gone", and deletes it.
  - Separately, staged files are only sent from `Session::end`. `resume` returns before building a session when nothing is pending (`commands/resume.rs:14-17`). `push-index` ignores them.
- **Failure scenario:**
  - The user runs `add film1 --delete-source`, then `add film2 --delete-source`.
  - film1's background session ends while film2 is resolving TMDB. film2's staged bundle is swept.
  - film2 uploads, its source is deleted, and it has no subtitles. There is no source left for phase 07 to recover them from.
  - Or: a background upload is killed after its last part and before its session end. The set is complete and its bundle sits staged until whenever the next `add` happens to run. `resume` says "no pending sets" and sends nothing.
- **Evidence:**
  - `commands/add.rs:18-31`: `prepare_and_record_set` runs, then `background::spawn_finish_set`.
  - `upload/session/item.rs:24-28`: the lock is taken per item inside the session, not by `add`.
  - `channel_index/publish.rs:22-24`: "two background uploads finishing together".
  - `upload/session/end.rs:37-45`.
- **Suggested fix:**
  - Write the staged file only after `record_planned` commits (stage to `<set>.tmp`, rename after), or sweep only files older than a day.
  - Have `resume` and `push-index` (or a `subtitles send-staged` subcommand) also flush staged bundles for complete sets.
  - Extraction now makes `add` block in the foreground for a full read of the source. State that in the risk table; the "Certain × Low" rating hides it.

## Finding 3: Android's prune on install wipes the whole bundle cache, including preload-held bundles, whenever an index without the new tables is installed
- **Severity:** Medium
- **Location:** Phase 04, "Requirements" ("After each successful catalog install, delete cached bundles whose sha no set in the new index references"); plan.md "Caches" ("pruned on install, held by preloads")
- **Flaw:**
  - `install_proven` accepts any snapshot it can count (`api/channel/install.rs:44-50`). The package path (`api/refresh/mod.rs:34`) is lower-bound only.
  - A snapshot pushed by a pre-v13 uploader has no `subtitle_files`. `tracks_by_set` treats the missing tables as empty, so the prune sees zero referenced shas and deletes everything.
  - That v12 push is exactly the window phase 02's risk table accepts ("v12 uploader … pushes after a v13 push … Med × Med").
  - The prune never consults held state, despite "held by preloads". It can also delete a `<sha>.tmp` that `hold_subtitles`/`subtitle_text` is writing at that moment, since both run concurrently with a launch-time install.
- **Failure scenario:** The other machine, still on an old build, publishes once. The TV box launches, installs that index, and wipes every bundle. The v13 index returns an hour later. Preloaded films watched offline (the plan's own success check: "airplane mode after a preload of that title → subtitles still load") now show no subtitles until they are refetched online.
- **Evidence:** Phase 04 Requirements; phase 02 risk row 1; `crates/mediagram-core/src/api/channel/install.rs:44-50`.
- **Suggested fix:**
  - Skip the prune when the installed index lacks `subtitle_files`.
  - Keep any bundle whose set is held in the media cache.
  - Or drop prune-on-install for an age/size LRU. The web already relies on LRU.
  - Name temporary files so the prune never matches them.

## Finding 4: Moving lesson subtitles out of the index removes offline subtitles for exactly the titles that have them today; Android's "hold" never runs for lessons
- **Severity:** Medium
- **Location:** Phase 04, "Key insights" / "Requirements" (`holdSubtitles` in `CacheDataSourceWriter`); Phase 07, `move-inline`
- **Flaw:**
  - Today all 1,235 subtitle tracks are lesson rows read from the installed index, with no network (`api/set_text.rs:1-3`).
  - The only new offline path is the preload hook. Series preload only takes `Kind.EPISODE` (`feature/player/.../PlayerViewModelPreload.kt:36,57`). Film preload is only offered for `Kind.MOVIE` (`ui-mobile/.../LibraryTitleBranches.kt:61`).
  - Lessons and documentaries are never preloaded, so after `move-inline` every lesson's subtitles need live Telegram on first open and again after every prune (Finding 3).
- **Failure scenario:** The tablet opens a course lesson on a train. Its video bytes are in the playback cache from an earlier watch, but its bundle was never fetched, or was pruned. It plays with no subtitles, where the day before `move-inline` it had them.
- **Evidence:** Plan "Survey": "Offline works today for free; bundles change that." No phase restores it for lessons. Phase 07 "Success criteria" only checks lessons online.
- **Suggested fix:**
  - Hold bundles for a course's lessons when any lesson of it opens (bundles are KB), or prefetch the next lessons' bundles as series preload does for episodes.
  - Or state the regression as a deliberate, user-approved difference in phase 07.

## Finding 5: A single publish from a stale uploader after `move-inline` removes subtitles from every client, and the gate cannot be checked
- **Severity:** Medium
- **Location:** Phase 07, "Risk assessment" row 5 ("Low × Med"); Runbook step 2 ("Confirm the other machine is idle and on ≥ phase 06"); plan.md Rollout gate 3
- **Flaw:**
  - After `move-inline` there are no inline rows left anywhere.
  - A pre-v13 build merges the channel copying only shared tables (`index/merge_columns.rs:14-18`), then pushes its own db (`index/snapshot.rs:39-50`). The channel ends up with neither bundles nor inline rows, and web, tablet and TV lose all subtitles at once. On Android, Finding 3 then also wipes the cache.
  - The phase 02 guard only stops future bumps. The gate relies on the operator remembering the other machine's version, and that version is known to lag (the other machine was on 0.68.3 while `main` was at 0.81.0).
  - Every index caption already carries the pushing build's `schema` (`mlib-spec/src/index_caption.rs:31-39`), yet nothing reads it for this purpose.
- **Failure scenario:** The other machine runs `add` on an old install the evening after `move-inline`. Every client shows no subtitles until this machine happens to publish again.
- **Evidence:** as cited; phase 02 "Publish guard … cannot protect against today's v12 builds".
- **Suggested fix:**
  - Make runbook step 2 checkable: after the other machine's next publish, read the current channel caption's `schema` (≥ 13) and its set count.
  - In `pull_from`, when `shared_columns(conn, "subtitle_files")` is empty but local has rows, print a loud "the channel was published by a pre-v13 uploader" and re-publish.
  - Re-rate the risk as Med × High.

## Finding 6: Phase 02 cannot pass its own success criterion, because a lockstep test ties the schema bump to a web file that phase 02 says it will not touch
- **Severity:** Medium
- **Location:** Phase 02, "Success criteria" ("`scripts/check.sh` green (all crates, web untouched)"); plan.md phase table (02 owns no `web/src`; 03 owns `web/**`)
- **Flaw:** `crates/mediagram/tests/shared_playable_sql.rs:36-45` asserts that `web/src/catalog.ts` contains `EXPECTED_SCHEMA = {SCHEMA_VERSION}`. Bumping `SCHEMA_VERSION` to 13 in phase 02 fails `cargo test -p mediagram` until `web/src/catalog.ts:24` changes, and phase 03 owns that file and runs later.
- **Failure scenario:** The phase 02 implementer gets a red `check.sh`. Either phase 02 edits a phase 03 file (breaking file ownership), or phase 02 cannot be released and pushed ahead of 03 as the rollout requires ("02 → reinstall uploader here, `push-index`").
- **Evidence:** `shared_playable_sql.rs:39` `format!("EXPECTED_SCHEMA = {}", mlib_spec::schema::SCHEMA_VERSION)`; `web/src/catalog.ts:24`.
- **Suggested fix:** Move the one-line `EXPECTED_SCHEMA = 13` (and its doc comment) into phase 02's file list. It is harmless for the running player: the check is lower-bound only.

## Finding 7: `-map 0:s? / 0:a?` turns remuxes that succeed today into upload failures; the risk table's premise is false
- **Severity:** Medium
- **Location:** Phase 01, "Requirements" and "Risk assessment" row 1 ("Source is already mp4, so its subtitles are mp4-legal")
- **Flaw:**
  - ffmpeg's mp4 demuxer exposes streams that its mp4 muxer cannot write. Examples: `eia_608` closed-caption tracks (`c608`) in TV/iTunes recordings, and PCM audio in QuickTime files named `.mp4`.
  - Today's automatic stream selection skips a subtitle stream it cannot encode or copy for the output. An explicit `-map` bypasses that check, and the muxer then refuses ("codec not currently supported in container").
  - `ensure_faststart` bails on any non-zero exit (`media/remux.rs:36-51`), which aborts `prepare_and_record_set` with "preparing file for splitting" (`upload/prepare_set.rs:74-76`).
- **Failure scenario:** An mp4 with a `c608` track that uploaded fine yesterday now fails at planning. With `add-show`, that episode is skipped or the walk stops.
- **Evidence:** `remux.rs:36-43` current args; plan phase 01 Requirements.
- **Suggested fix:**
  - Build the `-map` list from `streams::probe`, keeping only subtitle codecs the mp4 muxer accepts (`mov_text`, `dvd_subtitle`) and all audio.
  - Or, on failure, retry once with today's arguments and log what was dropped.
  - Add a fixture case with an un-muxable stream.
  - Note: I could not execute ffmpeg under the read-only rule, so the exact codec list needs a local probe.

## Finding 8: The forced-track rule takes a different audio-language input on Android than on the web, and the shared fixture cannot see the gap
- **Severity:** Medium
- **Location:** Phase 04, "Key insights" ("the index's `alang` never reached Android and is not needed"); Phase 03, Browser (`audioLanguage(found[audioTrack]?.lang ?? firstOf(set.alang))`)
- **Flaw:**
  - The web falls back to the caption's `alang` when the probed stream has no language tag. Android uses only ExoPlayer's selected `Format.language`, which is null or `und` for an untagged stream.
  - Untagged streams are exactly the case the uploader's `--alang` override exists for (`commands/args.rs:65`, `upload/prepare_set.rs:101`).
  - The shared `choice-cases.json` takes `audio` as an input, so both ports pass while computing different inputs.
- **Failure scenario:** A film uploaded with `--alang de` has an untagged German audio stream and a German forced track. The web shows the forced lines; the tablet and TV show nothing. This is a parity defect under CLAUDE.md "Surface Parity".
- **Evidence:** as cited.
- **Suggested fix:**
  - Carry `alang` into `SetSummary`/`MediaSet` (one field) and use the same `?? firstOf(alang)` fallback on Android.
  - Add a fixture row "audio unknown → alang fallback" that both surfaces derive the same way.

## Unresolved questions
- Is `upload_slots > 1` set on either machine? Phase 01's `is_held` and phase 07's "holds the upload lock" only check slot 0 (`upload/lock.rs:118-124` vs `acquire_slot`).
- The plan does not say which uploader the 905 channel-only sets came from. `move-inline` after `pull-index` covers them anyway, but the "it wrote them" rationale in phase 07 is unverified.
- A `rescan` into an empty data dir restores no `subtitle_files`. Only a merge from a healthy channel heals it, and `push-index --force` after such a rescan orphans every bundle with no restore path (restore is deferred to a follow-up).
