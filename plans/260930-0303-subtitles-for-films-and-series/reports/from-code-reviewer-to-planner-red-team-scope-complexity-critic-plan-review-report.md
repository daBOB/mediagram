# Red team: scope/complexity critic + contract verifier — subtitles plan

Reviewed: `plan.md`, phases 01–08, `reports/codebase-survey-subtitles.md`, against the working tree (2026-09-30).
Locked user decisions were not attacked, only how the plan implements them.

## Finding 1: Phase 06's staging pipeline rebuilds what `finish` already holds
- **Severity:** High
- **Location:** Phase 06, "Key decisions" 4, "Requirements" (`stage.rs`, `upload.rs`, `upload/session/end.rs`, `remove`)
- **Flaw:** The plan extracts at plan time, stages to `<data_dir>/subtitles/staged/`, sweeps at session end under a new `subtitles.lock`, deletes orphan staged files, re-sends after a crash, and makes `remove` handle staged files. At the moment a set completes, the per-item `finish` already has the upload-slot lock (`upload/session/item.rs:25-31`), the checked source (`:91`), the open link (`:100-105`) and the `complete` flag, before `report_deletion` (`:135-141`).
- **Failure scenario:** `mediagram add` runs `prepare_and_record_set` in the foreground (`commands/add.rs:19`) and only then hands off to the background (`:29-30`). Extracting at plan time therefore blocks the user's terminal for a full ffmpeg read of a multi-GB file. Extraction also runs for sets that never complete. The design adds about six moving parts, each needing its own tests.
- **Evidence:** Plan: "Planning writes `<data_dir>/subtitles/staged/<set_id>.subs.json.gz` … the session end sends every staged bundle whose set is complete … under `subtitles.lock`". Code: `item.rs:112-141` (`run_set` → `complete` → `report_deletion`).
- **Suggested fix:** Add one `subtitles::attach(conn, remote, set_id, source)`, called in `finish` when `complete`, before `report_deletion`. Skip it for document sets, which also pass through `finish` (`item.rs:66-71`). A crash between completion and recording is healed by phase 07 backfill, which targets complete sets without a `subtitle_files` row. Phase 07 calls the same `attach` for each matched file. This cuts `stage.rs`, the staged directory, `subtitles.lock`, the `end.rs` change and the staged-file branch in `remove`.

## Finding 2: The toggle rule that four phases implement is never written down
- **Severity:** High
- **Location:** `plan.md` "Default rule"; Phase 02 "Shared fixtures"; Phase 03 `toggleTarget`; Phase 04 port; Phase 05 CC state
- **Flaw:** The rule block only defines `chooseSubtitles`. Phase 02 has to author "toggle-target cases" before the reference implementation (phase 03) exists, and 03 and 04 run in parallel. Nothing says:
  - what 'c', CC or `KEYCODE_CAPTIONS` turn on when nothing was chosen before;
  - whether "off" also hides the auto-forced track;
  - whether CC reads "on" while only a forced track shows.
  The only hint is phase 06's manual check: "CC / captions key turn German on".
- **Failure scenario:** Today's web toggle falls back to `lastSubtitle = "0"`, the first track (`transport.js:299, 303, 324, 375`). In the plan's shared bundle fixture, track 0 is "German (Forced)" (phase 02 fixture order). A straight port therefore makes 'c' switch on a track the picker never lists, and the picker is left showing a value with no option. Web and Android will settle these three questions separately, which builds a parity defect in from the start.
- **Evidence:** Phase 03: "`toggleTarget({tracks, last, preferred, audio})`", with no semantics given. Phase 02: "plus toggle-target cases", with no content.
- **Suggested fix:** Add toggle lines to `plan.md` before phase 02 builds the fixture. For example: `on = last regular ?? wanted(preferred) ?? regular in audio language ?? first regular; off = off (forced stays automatic); CC state = a regular track is showing`. Then build the fixture cases from that text. The "does off hide forced" question is user-visible, so confirm it with the user.

## Finding 3: Three ways to send a small document, one of them only to fill a column nobody reads
- **Severity:** Medium
- **Location:** Phase 06 "Requirements" (`telegram/document.rs`, `Transport::send_document`); Phase 02 `subtitle_files.doc_id`
- **Flaw:** `ChannelRemote::send_index` already sends a small captioned document with flood-wait-only retry (`channel_index/remote.rs:52-53`, `telegram_remote.rs:107-133`). `Link::open()` returns both the transport and the remote (`upload/session/link.rs:23`). `Transport` is shaped around parts: `send_part(name, mime, &Caption, human, &mut PartReader, len)` (`upload/transport.rs:33-42`). The plan adds a free function, refactors `send_index` onto it, and adds a second method on a second trait. Every fake `Transport` then changes too (`tests/support/upload.rs:82`). The extra return value, `doc_id`, is read by no reader: the web strips it (`web/src/catalog.ts:79`) and fetches by message id (`part-fetch.ts:42-45`), and core resolves by message id (`transport/stream.rs:75-93`).
- **Failure scenario:** Two trait methods for one operation, one extra module, and changes to two fakes, all to return a value no reader uses.
- **Evidence:** Plan: "`telegram/document.rs` (new): `send_document(...) → (message_id, doc_id)` — the body of `send_index`, which now calls it (DRY). `Transport::send_document` wraps it".
- **Suggested fix:** Generalise `ChannelRemote::send_index(path, caption)` into `send_document(bytes, name, mime, caption) -> i32`, and have the index call it with `library.db`. Drop `doc_id` from `subtitle_files`.

## Finding 4: The index table stores columns no reader reads
- **Severity:** Medium
- **Location:** Phase 02, "Requirements" (DDL, bundle v1)
- **Flaw:** `subtitle_tracks.source` and `codec` are consumed by no phase. The web `SubtitleTrack` is `{track, lang, forced, sdh, label}` (phase 03), and the core DTO is the same (phase 04). The bundle already carries `source` and `codec` for every track "so a future `rescan` could restore rows", and that rescan restore is explicitly out of scope (phase 07 "Next"). The merge copies table rows (merge step 2) and never checks them against the bundle.
- **Failure scenario:** Write-only schema that goes into every v13 index. Two copies of per-track provenance can drift apart with nothing to detect it. Readers that later trust the table column may be reading a stale value.
- **Evidence:** Phase 02 DDL: `source TEXT NOT NULL, codec TEXT`. Phase 03: "`SubtitleTrack = {track, lang, forced, sdh, label}` — no file refs".
- **Suggested fix:** Keep the bundle self-describing, as the durable copy (the same lesson as "summary sidecars are the durable copy"). Limit the index table to what pickers need: `track, lang, forced, sdh, label`.

## Finding 5: A permanent `audit-streams` subcommand and a `serve` refactor for a count taken once
- **Severity:** Medium
- **Location:** Phase 01, "Requirements" and steps 4–6
- **Flaw:** `mediagram serve --addr` already exists (`cli.rs:110-114`, `commands/serve.rs:21-31`) and serves `/sets/{set_id}/stream` (`serve/routes.rs:31-34`). The phase also says that "A lost subtitle stream is healed by phase 07", so the `missing_subs` column feeds no decision. Only `missing_audio` matters (`plan.md` "Decided" #3).
- **Failure scenario:** Three hours of work, a refactor of a working command (the `start()` extraction), a permanent CLI surface with tests, and a minor version bump, all for a measurement taken once on each machine.
- **Evidence:** Plan: "`mediagram audit-streams [--limit N]` … prints TSV `set_id title missing_audio missing_subs`"; "`serve.rs`: factor `pub(crate) async fn start(...)`".
- **Suggested fix:** Keep phase 01 to the `-map` fix and its test. Do the audit as an operator script under `reports/`:
  - run `mediagram serve --addr 127.0.0.1:<spare port>`;
  - list candidates with `sqlite3 -readonly` where `json_array_length(alang) > 1`;
  - run `ffprobe -show_streams` against the served URL;
  - map `ger`→`de` with jq;
  - write audio-only TSV.

## Finding 6: The fallback for the old subtitle layout is never removed
- **Severity:** Medium
- **Location:** `plan.md` "Key decisions" (readers read both layouts); Phases 02, 03, 04, 07
- **Flaw:** The plan says "Readers read both layouts … until phase 07 empties the old one", but no phase or follow-up removes that code once the old layout is empty. What stays behind:
  - the legacy branch of web `subtitle-tracks.ts` and `legacyBody`;
  - core `legacy_body`;
  - the merge's `fill_missing_assets` exclusion and cleanup delete (merge rule 3);
  - the preview helper's legacy case;
  - the tests for all of these.
  Phase 07's "Next" lists three follow-ups, and this is not one of them. The data is small: all 1,266 inline rows in the follower copy are lessons with exactly one track each (`tut` de 6 / en 184 / und 1,076; `assets` primary key is `(set_id, kind, lang)`, `schema_versions.rs:59-64`).
- **Failure scenario:** Once phase 07's success criterion holds (0 inline rows), a second read path stays in place on two surfaces for a layout with no rows. Every later change to subtitle reading has to be made twice.
- **Evidence:** Phase 03: "a set without a `subtitle_files` row falls back to its inline `assets` rows". Phase 07 success: "`SELECT COUNT(*) FROM assets WHERE kind='subtitle'` = 0".
- **Suggested fix:** End phase 07 with a removal step, gated on the follower copy showing 0 inline rows and the other machine being on ≥ phase 06. Alternatively, file it as a tracked issue now.

## Finding 7: The web adds a second disk cache tied to another session's uncommitted cache code
- **Severity:** Medium
- **Location:** Phase 03, "Requirements" → `subtitle-bundles.ts`; Key insights (reuse of the LRU)
- **Flaw:** `SubtitleBundles(fetch, dir)` brings its own tmp+rename writes, in-flight sharing, sha check and a `dir = null` mode. It depends on `retireOtherChunkSizes`, which only exists in another session's uncommitted `web/src/cache/key.ts` and `src/index.ts:151` (the plan itself says "re-check once that lands"). The LRU evicts by atime (`web/src/cache/store.ts:297`), and chunk hits update it explicitly with `utimes` (`store.ts:137-139`). The plan's hit path ("cache hit → read") does no such touch.
- **Failure scenario:** Bundles are evicted in the order they were written, so popular titles lose theirs first. The cache has two modes to test. It is coupled to files the plan says must not be touched, and they are still changing.
- **Evidence:** Plan: "cache hit → read; miss → … write tmp + rename, share one in-flight download per sha". The route already sends `Cache-Control: private, max-age=3600`.
- **Suggested fix:** Use a bounded in-memory `Map<sha, Promise<Bundle>>`. It de-duplicates concurrent loads of the forced and regular tracks, needs no disk, and does not touch `web/src/cache`. If subtitles must show for held titles while signed out, state that as a requirement, and only then add disk storage with an explicit `utimes` on each hit.

## Finding 8: Android's bundle cache prune limits almost nothing, and the hold hook duplicates an existing parameter
- **Severity:** Medium
- **Location:** Phase 04, "Requirements" (prune, `CacheDataSourceWriter`), "Risk assessment"
- **Flaw:** Pruning deletes only bundles whose sha no longer appears in the index. A sha leaves the index only when its bundle is replaced or its set is removed. So nearly every opened or preloaded title's bundle stays forever, while the risk table lists "Pruned on every install" as the mitigation. There are two install paths, `api/channel/install.rs:50` and `api/refresh/mod.rs:75`, and both go through `versions/install.rs:51 Staging::install`. The plan only hedges ("if it installs separately"). `CacheDataSourceWriter` already receives `currentCore` (`CacheDataSourceWriter.kt:50-56`; `PreloadModule.kt` passes `{ coreProvider.core.value }`), so the new `holdSubtitles` lambda and its wiring duplicate it.
- **Failure scenario:** The stated mitigation is false, and a prune hooked only into the channel path misses package installs. An extra constructor parameter and DI change buy nothing.
- **Evidence:** Plan: "After each successful catalog install, delete cached bundles whose sha no set in the new index references"; "`CacheDataSourceWriter` gets `holdSubtitles: suspend (String) -> Unit` (wired in `PreloadModule`…)".
- **Suggested fix:** Call `currentCore()?.holdSubtitles(item.setId)` inside `write()`. If pruning stays, put it in `Staging::install` and rewrite the risk row to state the real bound (unbounded, one gzip per title opened), keeping the ponytail note about adding an LRU later.

## Contracts checked and consistent (no finding)
- Every reader accepts a newer schema: web `assertSchema` checks only the lower bound (`web/src/catalog.ts:58-69`). Package pointers are now lower-bound too (`crates/mlib-spec/src/package/mod.rs:157-163`, `web/src/package/pointer.ts:144-149`).
- Foreign keys are on for the merge and remove connections (`pull.rs:138`, `db.rs:41`, `remove/apply.rs:79`), so the v13 cascades hold.
- Preference `updated_at` is in ms on both surfaces (`web/src/state/store.ts:339`, core `preferences.rs` `now_ms()`), so merging newest-wins across surfaces is sound.
- Per-show scope keys differ between web and Android for untagged films and episodes (`posters.ts:57-67` vs `dto/summary.rs:171-185`), but the follower copy holds 0 untagged `movie`/`ep` sets. Nothing is affected today.
- `#mlib-subs` captions are skipped by rescan (`rescan.rs:62-68`) and by index discovery (`pick-newest-index.ts:31` `startsWith`).

## Unresolved questions
1. Toggle semantics (Finding 2): should "off" also hide the auto-forced track? This is user-visible, so the user should decide.
2. Must the web show subtitles for held titles while signed out? That decides between Finding 7's in-memory cache and a disk cache.
3. When is the legacy-layout reader removed (Finding 6)? End of phase 07, or a follow-up issue?
