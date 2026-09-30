# Red team (security adversary + fact check): subtitles for films and series

Scope: `plan.md`, `phase-01`…`phase-08`, `reports/codebase-survey-subtitles.md`, checked against the working tree at `a21b7df0` (+ uncommitted `web/src/cache/*` edits). Read-only, apart from this report.
Threat model: a private channel written only by the household's two uploaders. The codebase itself still treats channel strings as untrusted before they reach a file name (`web/src/cache/key.ts:58-66`, `crates/mlib-spec/src/package/mod.rs:165-171`).

## Finding 1: Moving lesson subtitles out deletes a copy the plan wrongly says is on disk, with no read-back and no way to repair a bad bundle
- **Severity:** Medium
- **Location:** Phase 07, "Rollback", "Requirements" (`move-inline`, backfill resumability); Phase 02, "Uploader" merge step 3; Phase 06, `index/subtitles.rs` `record`
- **Flaw:** `record` deletes the inline `assets` rows in the same transaction that stores the *send* result. Nothing downloads the sent document back, checks its sha256 or decodes it before the delete. Merge step 3 then deletes the other machine's copy on its next merge. Once a set has a `subtitle_files` row it is never retried: backfill treats a file row as "done", `move-inline` only takes sets with "no bundle", and readers turn every failure into `null` plus a warning. The rollback says the sidecars are still on disk. On this machine that is mostly false.
- **Failure scenario:** a bundle gets recorded but cannot be read: the sha is taken over the wrong buffer on one path, the message is deleted later, or a reader-side decode bug hits some content. The lesson silently loses its subtitles on every surface, and neither machine's index still holds the body. No command re-bundles the set. Recovery would mean hand-extracting bodies from an old unpinned channel snapshot, and the plan does not mention that route.
- **Evidence:**
  - Plan: "After it, the inline rows are gone from the index but the sidecars still exist on disk"; "resumable by design (a set with a `subtitle_files` row is done)"; merge step 3 `DELETE FROM main.assets WHERE kind = 'subtitle' AND set_id IN (SELECT set_id FROM main.subtitle_files)`.
  - Index (read-only query): all 1,235 inline rows are `tut`, one per set; 864 of them belong to "Wall Street Story".
  - `find ".../<media drive>/andre/Videos/1 Wallstreet Stroy Masterclass" -iname '*.vtt'` finds 0 files, next to 884 `.mp4`. `<course working folder>` (maxdepth 6) holds 990 `.vtt` against 1,235 bodies.
  - Old index snapshots are only unpinned, never deleted (`crates/mediagram/src/channel_index/unpin.rs:17-30`). That is the real backup, and the plan does not name it.
  - `rescan` restores no assets (project memory "summary sidecars are the durable copy").
- **Suggested fix:**
  - In `move-inline`, before `record`: fetch the sent message by id, compare its sha256 and decode it, then delete the inline rows.
  - Add `mediagram subtitles backfill --redo <set>` (or a `verify` pass) that ignores an existing file row.
  - Replace the rollback text with the real safety net: the message id of the last pre-move index snapshot, recorded in `plan.md` before the run.

## Finding 2: Android's prune turns the plan's own "old uploader drops the tables" window into permanent loss of held subtitles
- **Severity:** Medium
- **Location:** Phase 04, "Requirements" → Core: "After each successful catalog install, delete cached bundles whose sha no set in the new index references"
- **Flaw:** the prune trusts whichever index was installed last. Phases 02 and 07 both rate "a v12 build pushes afterwards and drops the tables" as a real risk (Med × Med). The reader contract in this same phase says "missing tables → empty". So an index without `subtitle_files` references nothing, and the prune deletes every cached and held bundle. A lagging package catalog does the same thing through `api/refresh`, which installs separately (`crates/mediagram-core/src/api/refresh/mod.rs:60`).
- **Failure scenario:** the other machine, still on an old build, finishes an upload and pushes a v12-layout snapshot. The tablet installs it at launch and prunes all bundles. The next v13 push brings the rows back, but the preloaded films are held for a trip. In airplane mode they play with no subtitles, which breaks phase 06's own success check ("airplane mode after a preload … subtitles still load").
- **Evidence:** the prune rule quoted above. Phase 02 risk row: "v12 uploader on the other machine pushes after a v13 push, dropping the tables from the channel | Med × Med". The merge copies only shared tables (`crates/mediagram/src/index/merge_columns.rs:14-18`), and the push is a whole-db `VACUUM INTO` (`index/snapshot.rs:39-50`).
- **Suggested fix:** skip the prune when the installed index has no `subtitle_files` table. Never prune the bundles of held or preloaded sets. Or drop the prune entirely and cap the directory by size (the plan already allows "add an LRU if needed").

## Finding 3: A cached bundle is trusted forever once written, with no single-flight on Android and index strings used as file names
- **Severity:** Medium
- **Location:** Phase 03, `subtitle-bundles.ts`; Phase 04, `api/subtitles.rs` (`subtitle_text`, `hold_subtitles`), "Security"
- **Flaw:**
  - The sha256 is checked only on a miss. A hit reads, gunzips and parses the file, and on failure returns `null` but leaves the file in place.
  - Android has no "one in-flight download per sha" (the web does). Its tmp naming is unspecified, and nothing fsyncs before the rename.
  - `hold_subtitles` is described as "fetch into the cache … (no decode)", with no sha check stated.
  - The file name is the index's `sha256` column, with no shape check. The DDL (`sha256 TEXT NOT NULL`) does not pin lowercase 64-hex. This goes against the project's rule that channel-derived strings are checked "before it reaches a cipher, a file name or an allocation".
- **Failure scenarios:**
  - **Race:** a preload's `hold_subtitles` and the player's `subtitle_text` miss the same sha together (film page Preload, then Play), and both write the same tmp path. One of them can rename a half-written file into place.
  - **Power cut:** the TV box loses power right after a rename that was never fsynced, leaving a zero-length `<sha>.json.gz`.
  - In either case, every later open is a cache hit, the gunzip fails, and there are no subtitles. The prune keeps the file because its sha is still referenced, so the title stays broken on that device.
  - On the web, a non-`.tmp` temp name is counted by the LRU and can be evicted mid-write. A `.tmp` orphan is never counted or cleaned.
- **Evidence:**
  - Plan phase 03: "cache hit → read; miss → … check `sha256`, write tmp + rename, share one in-flight download per sha". Phase 04 has the same steps without single-flight.
  - `web/src/cache/store.ts:294` (only `.tmp` names are skipped by the LRU walk).
  - `web/src/cache/key.ts:58-66` ("A set id comes from a caption, which anyone with channel access can write, so it is checked here").
  - `crates/mlib-spec/src/package/mod.rs:165-171`, plus the existing helper `crates/mlib-spec/src/package/charset.rs:15` `is_lower_hex`.
- **Suggested fix:**
  - Spec: `sha256` is lowercase 64-hex. Readers refuse any other shape, reusing `is_lower_hex`.
  - On a hit whose decode fails, delete the file and fall through to a miss.
  - Use a unique tmp name ending `.tmp` (pid + random), fsync it, then rename.
  - One shared, sha-verified fetch function for both `subtitle_text` and `hold_subtitles`, with a per-sha mutex in core.

## Finding 4: On the web, a "held" title loses its subtitles offline while Android keeps them, so the parity gap is on the reference surface
- **Severity:** Medium
- **Location:** Phase 03, "Key insights" ("the chunk cache's existing LRU walk bounds them with no edit to `web/src/cache/*`"); Phase 07, "Gate"
- **Flaw:**
  - Today lesson subtitles come out of the index and need no Telegram. After phase 07 they are fetched on demand.
  - The web's "held" badge promises "playable with no Telegram at all", but `/api/preload` never holds the bundle, and the LRU may evict it (by atime) while the title's chunks stay.
  - Android deliberately holds bundles with preloads (phase 04). So the newer surface is ahead of the reference, and CLAUDE.md "Surface Parity" calls that divergence a defect.
- **Failure scenario:** the web player is signed out, or Telegram is unreachable. A fully held lesson or film plays from disk, but its `<track>` request goes through `connectionFetcher`, which throws, so the title plays silently without subtitles. Before phase 07, the same lesson showed them.
- **Evidence:**
  - `web/src/cache/held.ts:1-9` ("A set is held when every chunk of every part is on disk, which makes it playable with no Telegram at all — the claim the shelf badge makes").
  - `web/src/telegram/part-fetch.ts:63-65` ("this part is not cached, and the player is signed out").
  - `web/src/routes.ts:115` (`/api/preload` exists).
  - Phase 04: "Preloaders hold video only; since one writer serves both, one hook holds the bundle too."
- **Suggested fix:** have the web series or film preload call `SubtitleBundles.hold(setId)`. Keep `<cacheDir>/subtitles/` out of the LRU walk, or re-fetch the bundle when a held title opens. If the file-ownership rule forbids this in phase 03, write the gap down as a deliberate difference and add it as a follow-up phase.

## Finding 5: Two phase 02 facts are false: check.sh cannot pass with the web untouched, and the guard does not cover every publish
- **Severity:** Medium
- **Location:** Phase 02, "Success criteria" ("`scripts/check.sh` green (all crates, web untouched)"); "Requirements" → Guard ("Covers `pull-index` and every publish (both pull through here)"); `plan.md` ownership table (`web/**` belongs to 03)
- **Flaw (a):** `cargo test --all` (`scripts/check.sh:24`) runs `crates/mediagram/tests/shared_playable_sql.rs:36-45`. That test asserts that `web/src/catalog.ts` contains `EXPECTED_SCHEMA = {SCHEMA_VERSION}`. Setting `SCHEMA_VERSION = 13` in phase 02 while leaving `web/src/catalog.ts:24` at `EXPECTED_SCHEMA = 12` fails the test. Phase 02 has to edit a file that phase 03 owns, and "01 ∥ 02, 03 ∥ 04" is not as clean as stated.
- **Flaw (b):** `push-index --force` sets `Mode::Force`, which snapshots and sends without pulling (`channel_index/publish.rs:36-39`, `commands/push_index.rs:11-22`), so the guard in `pull_from` never runs. A publish whose pinned id equals the last pulled one also skips the pull (`publish.rs:44-46`).
- **Failure scenario:**
  - (a) The phase 02 gate is red on the first run. The implementer then either touches web out of turn or "fixes" the test.
  - (b) After a future schema bump, a v13 box using `--force` (the documented recovery lever) drops the newer tables with no refusal, which is exactly what the guard is meant to stop.
- **Evidence:** as cited above. `web/src/catalog.ts:24` holds `export const EXPECTED_SCHEMA = 12;`.
- **Suggested fix:**
  - Move the one-line `EXPECTED_SCHEMA = 13` edit (and its comment) into phase 02 and list it in phase 02's files.
  - Either make `--force` also refuse a newer channel schema (read the pinned caption's `schema` or download it first), or change the claim to "every publish except `--force`".

## Checked and not a risk (for this household)
- **VTT/HTML injection:** there is no `innerHTML` or `insertAdjacentHTML` anywhere in `web/public/lib` (only the doc warnings in `dom.js:7` and `notes/markdown.js:8`). Cues render through native `<track>`. Android reads media3-parsed cue text with markup already stripped (`core/playback/src/main/kotlin/SubtitleTrack.kt:34-36`). Labels come from a fixed vocabulary the uploader writes.
- **Auth on the subtitle endpoint:** the web has no per-request auth. `/api/sets/:id/stream` (`web/src/routes.ts:139`) already serves the whole file to anyone who can reach the server, so subtitles expose nothing new. Kids filtering is client-side today, so this adds nothing there either.
- **Decompression bombs:**
  - Rust `take` and the web `gunzipSync(buf, { maxOutputLength })` both cap. Verified on Bun 1.4.2: a 200 KB gzip of 200 MB throws at 64 MiB through `node:zlib`.
  - `Bun.gunzipSync` has **no** cap and returned all 200 MB, so the implementation must import from `node:zlib`. Worth one line in phase 03.
- **ffmpeg argument injection:** argv is built without a shell. Paths come from the walk, and absolute folders mean no `proto:` prefix is possible.
- **Channel noise:** `#mlib-subs` never matches the web update classifier (`web/src/telegram/updates.ts:51-58`: state or `#mlib-index` only) or rescan (`rescan.rs:62-68`). State discovery reads pins, not search (`state-channel.ts:13-31`), so thousands of unpinned bundles cannot crowd out state or index documents.
- **Synced preference privacy (phase 08):** per-show choices reveal nothing that synced watch state does not already. Cue values are validated on read (`subtitle-panel.js:153-158`).

## Fact-check: claims verified true
- Readers accept newer schemas: `web/src/catalog.ts:58-69` (lower bound), `web/src/package/pointer.ts:148`, `mlib-spec/src/package/mod.rs:161-163` (min), `mediagram-core/src/api/channel/install.rs:47-52`.
- `writeWorthSyncing` refuses `/preferences` (`web/src/routes.ts:79`). `film-page.js:99-108` `languages()` needs an array, but `alang`/`slang` are strings (`web/src/catalog.ts:102-104`).
- `retireOtherChunkSizes` touches only all-digit names (`web/src/cache/key.ts:80`). The LRU walks every file and skips `.tmp` (`store.ts:274-306`). `index.ts:143`/`:200` are as cited. `SUBTITLE_PATH` is at `catalog/routes.ts:25`.
- `course/sidecars.rs` takes the exact `<stem>.vtt` (`:39`), strips `.faststart` (`:51-58`), and uses the first audio language or `und` (`:110`). `prepare_set.rs` references hold, and `total` is the size of the remuxed file.
- Inline bodies: 1,235 `tut` rows (und 1,076 / en 153 / de 6), exactly one per set, max 612 KB, all beginning `WEBVTT`. So keeping the `ORDER BY lang` numbering is trivially safe, and no body reaches the 4 MiB per-track cap.
- `migrations::apply` does nothing when `current >= SCHEMA_VERSION` (`index/migrations.rs:36-39`). Foreign keys are on in the uploader (`index/db.rs:41`), so the `ON DELETE CASCADE` in the V13 DDL takes effect.

## Unresolved questions
1. Where do the transcript `.vtt` files for "Wall Street Story" (864 inline rows), "Mentfx Course 2026" and "Forex Mentor - Trendline Mastery" live? Only 990 `.vtt` were found under the course working folder, and none beside the <media drive> source.
2. Where does phase 06 write its per-track extraction outputs and SRT conversions? If they go beside the source, a crash leaves `.vtt` files that the new sidecar rule will adopt later.
3. Does the TV box ever install a package catalog (`api/refresh`) instead of the channel index? If it does, Finding 2 also fires whenever a package lags the channel.

**Status:** DONE
**Summary:** 5 Medium findings, no Critical or High. The real risks are irreversibility and cache integrity around the move to bundles, not injection. Two phase 02 claims are false and cheap to fix.
