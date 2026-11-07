# bytefortress: uploader install + ~/kdownloads prepare (2026-10-28)

## Install
- `mediagram 0.141.1` (HEAD 222b6962) built on the box: `~/.cargo/bin/mediagram`. No sudo there:
  `SQLITE3_LIB_DIR=~/.local/lib/sqlite3` (symlink to system libsqlite3.so.0), static ffmpeg/ffprobe 9.0.2 in `~/.local/bin`.
- Config copied from dev box (600). **No Telegram session** — `mediagram login` in a real terminal on the box before any upload, then `pull-index`.

## Inventory (before)
- 100 folders, 2.8 TB, 1508 mkv. 64 packed episode sets (Breaking Bad 62, Stranger Things S04E09 = copy of unpacked file, Expanse S05E01 WAYNE).
- 497 release samples (27 GB) → `~/kdownloads-samples/` (mediagram does not skip them; add-show would see duplicate episodes).
- Skipped → `~/kdownloads-skip/` (230 GB, user-approved): two `(1)` copies, Expanse S05 ZeroTwo + jUNiP, Oceans 13 VC-1 remux, 13 films already held (local index 2026-10-25).
- Kept although held: Im Auftrag des Teufels (library has 720p only).
- Within-folder duplicate left: Roseanne `S08E00a/b/c` Bonus — add-show will refuse the folder until moved.

## Prepare dry run
- Track dropping saves ~3 GB of 2752. 77 files stay 2 parts.
- Still non-direct-play after mp4 (picture codec): 72 HEVC (Marco Polo 21, Zwei an einem Tag 14, The Scandal 8, Auf dem Wasser 4, VECTOR/toto/RocketHD films), 0 VC-1 left after skips.

## Running (background on bytefortress)
- `~/src/unrar-kdownloads.sh` → `~/src/unrar-kdownloads.log` (~60 MB/s).
- `~/src/convert-kdownloads.sh` → `~/src/convert-kdownloads.log`: 4 folders at a time via `~/src/convert-one-kdownloads.sh` (`prepare --mp4 --out <dir> --delete-source <dir>`); BB/ST/Expanse wait for unrar, then rar sets with `OK` are deleted (~298 GB), then converted. Ends with `ALL DONE`; failures show as `END(1)` / `left unchanged:`.
- Measured 16:45 (box time): 20.6 MB/s combined while unrar shares the RAID1 HDDs (ffmpeg DTS decode + AAC encode is one core per file) → ETA ~1.5 days.

## Defect found → fixed (uncommitted, 2026-10-28 19:20)
- `prepare --mp4 --replace` wrote mp4 bytes under the original `.mkv` name; container comes from the extension (`classify.rs:84`), so upload would caption/serve it as Matroska.
- Fix: `paths.rs` `replaced()` → `--replace --mp4` lands at `<stem>.mp4`, `.mkv` removed after checks pass; existing `<stem>.mp4` beside it → file left unchanged (also catches two sources mapping to one name in a run). `rewrite.rs` uses it; tests `mp4_replacement_of_an_mkv_is_named_mp4`, `mp4_replacement_leaves_an_existing_mp4_and_its_source_alone` (red before, green after); whole crate green, clippy clean in `prepare/`.
- Changelog bullet under the uncommitted 0.142.0 (another session's bump). bytefortress still runs 0.141.1 — batch uses `--out <same dir> --delete-source`, unaffected.

## TMDB ids
- All 80 folders mapped: `bytefortress:~/src/kdownloads-tmdb-ids.tsv` (`folder, show|film, tmdb, German title`). 46 show folders → 38 shows, 34 films.
- Shows already in library use the same ids (Alex Cross 213306, Arcane 94605, Diplomatische Beziehungen 203857, For All Mankind 87917, HotD 94997, Silo 125988, Ted Lasso 97546, The Capture 93166); film Im Auftrag des Teufels 1813 same.
- One `add-show --tmdb` per folder: AoT ×2 (1429), Stranger Things ×2 (66732), Severance ×2 (95396), Ricky Gervais Alley Cats ×6 (300480, one episode each).
- Disk vs TMDB episode counts: match except — Breaking Bad + Expanse S05E01 still unpacking; Stranger Things S02E08 missing; Hundert Jahre Einsamkeit S02 has E01–07 of 8; Undercover-Liebe 2 of 12 (airing); Roseanne S07 ends E25 of 26, plus English E00 bonus files S01–S05, S08 (a/b/c), S09; Marco Polo S00E01 (special) has no season 0 on TMDB; Stargate Atlantis S01E01E02 is one double-episode file (parser handles `SxxEyyEzz` as a range).
- Titles worth a glance: "Chilling Romance" = TMDB 298610 "Spooky in Love" (Korean, 12/12 eps match); "Wicked For Good" file is `…gesang…` (sing-along cut?) → 967941.

## Status 2026-10-28 21:52
- `pull-index` done 18:27→19:21 (~55 min; mid-run 2.5 h estimate was wrong): library 23,875 sets / 41,954 GB, 1,684 shows, 9,673 subtitle files. Batch auto-resumed 19:21.
- unrar ALL DONE 19:55: 62 OK, 2 checksum errors (corrupt downloads, unrar removed the partial mkv) → **Breaking Bad S03E01** and **The Expanse S05E01** missing; their rars kept.
- 304 volumes of the 62 OK sets deleted by hand at 21:50 (264 GB; disk 63% → 58%). The same step in `convert-kdownloads.sh` will now only log "No such file". Proof/subs rars (small) kept.
- Convert: 12 folders END(0), 4 running, 0 failed.

## Upload (started 2026-10-29 00:39 box time)
- `~/src/upload-kdownloads.sh run` → `~/src/upload-kdownloads.log`: the 35 folders converted by then (452 GB), one at a time: `add-show --yes --no-push` per show folder, `add --watch --no-push` per film, then one `push-index`. `dry` mode first: 0 problems.
- Re-checked against the box's merged index: no ready film or episode already held. Im Auftrag des Teufels goes up as `--variant "Director's Cut"` beside the held 720p.
- Measured ~9 MB/s (1.7 GB in 3 min) while conversion shares the disks → ~14 h. Sources kept (no `--delete-source`).
- Library gap noticed: Diplomatische Beziehungen has S1–S2 held, folder is S4; S3 not in the library.

## Batch 1 done (2026-10-29 12:11 box time)
- 35 folders, 0 failed: 91 episodes + 25 films; published msg 43429. Library 23,991 sets / 42,406 GB. ~10.5 MB/s.

## Batch 2 (started 2026-10-29 20:31 box time, pid 1562761)
- 37 folders, ~1.25 TB, ~33 h. Same script, appended to `upload-kdownloads.log`; `~/src/upload-hold.txt` keeps folders out.
- Held: Marco Polo (21 eps) + Super Happy Magic Forest (52 eps) — hyphenated `-s02e07-` names parse as "no episodes", which add-show reports with exit 0 (would be logged END(0) = never retried).
- Roseanne E00 bonus folders (7, already mp4) → `~/kdownloads-skip/48DVD-RoseanneS01E00-BUX/` (user decision).

## Fixes (uncommitted, in the 0.142.0 changelog section; user said fix all three)
- `mlib-spec/src/filename.rs` EP grammar: `[._-]` before `sNNeNN`. Test `an_episode_code_joined_to_the_name_by_hyphens_parses`.
- `media/streams.rs` + `probe.rs`: `Probed.duration` = longest video/audio stream (MP4 `duration`, MKV `DURATION` tag), container only as fallback. The Capture S01E05: ger subtitle cue to 59:36 vs picture 59:33 → false `DurationChanged`. Test `the_duration_is_how_long_the_picture_and_sound_run`.
- `commands/prepare/rewrite.rs`: `-af aformat=channel_layouts=…` when audio is re-encoded. ffmpeg AAC refuses 5.1.2/7.1.4 (Atmos) → One Battle After Another failed in 1 s ("no packets" was the follow-on). Measured on ffmpeg 9.0.2: 5.1.2→5.1, 7.1.4→7.1, stereo/5.1/7.1/2.1/7.1(wide) unchanged, 5.1(side)→5.1 relabel. Test `audio_in_a_layout_aac_refuses_is_folded_into_one_it_takes` (FLAC-in-MKV fixture keeps the layout).
- mediagram 847 tests + mlib-spec green; clippy clean on changed files; rewrite.rs at the 200-line limit.

## Chain on the box (`~/src/kdownloads-batch3.sh 1562761`, log `kdownloads-batch3.log`)
- Working tree shipped via `git stash create` → `~/src/mediagram-next`, installed to `~/src/mediagram-next-bin` (not `~/.cargo/bin` while batch 2 runs).
- Re-prepares The Capture + One Battle with it (`MEDIAGRAM=` override in `convert-one-kdownloads.sh`), waits for batch 2 to exit **and** convert `ALL DONE`, installs into `~/.cargo/bin`, empties the hold list, runs batch 3 (rest: held 2, Roseanne, BB, ST S1–4, Expanse, The Capture S2–S3, One Battle), one publish.
- Cancel: `pkill -f kdownloads-batch3.sh`.

## Status 2026-10-30 00:45
- Re-prepares with 0.142.0 both passed: The Capture S01E05 (4.28 GB mp4, 20:53), One Battle After Another (9.51 GB, ger+eng AAC 5.1, 21:05). Sources deleted by the check.
- Their END(0) lines were overwritten: `convert-kdownloads.sh` holds its log without O_APPEND. Recorded in `~/src/convert-kdownloads-redo.log`; `upload-kdownloads.sh` now reads both logs (swapped by mv, batch 2 keeps the old inode).
- Conversion: BB, ST, Expanse since 21:12, 59 files done, 103 mkv left, ~23/h → ~05–06 Berlin.
- Batch 2: 5 of 37 folders; Homeland in progress (S01–S03 = 36 eps up). ETA unchanged (~Sat morning).

## Status 2026-10-31 00:10
- Conversion ALL DONE 2026-10-30 07:47 UTC. Expanse END(1): S05E03 rejected by 0.141.1 duration check (3118.491 vs 3117.088); S05E01 sample rejected too.
- Batch 2: 37/37 END(0), published 18:11 UTC. Batch 3 started 18:11 with 0.142.0 installed: BB, ST, Marco Polo, SHMF done; The Capture uploading.
- No sample clip in the library (only 3 course lessons named "Sample Size"). Expanse S05E01 Sample dir moved to `~/kdownloads-skip/`.
- `~/src/kdownloads-batch4.sh 1581762` (log `kdownloads-batch4.log`): re-prepares Expanse with 0.142.0 into `convert-kdownloads-redo.log`, waits for batch 3, uploads Expanse, publishes. Skips upload if re-prepare fails.
- 12 new folders since the TMDB lookup (~558 GB: One Piece, Naruto?, FMA Brotherhood, Jujutsu Kaisen S1/S2, Frieren S1/S2, Nobody Wants This S3, Yu Yu Hakusho, Rurouni Kenshin OVA, 2 sanet.st courses). Not touched; One Piece alone has 137 sample clips.

## Status 2026-10-31 05:55
- Expanse re-prepare with 0.142.0 failed again: S05E03 video/audio carry no duration (neither field nor DURATION tag); only subtitles (3118.491 = container). Fallback to container vs mp4 stream durations (3117.088) → DurationChanged. Third case, not fixed.
- Roseanne files were `S01E01 - Title.mp4` (no show name) → add-show skipped all 221 ("no season/episode in the name", exit 0). Renamed on the box to `Roseanne S01E01 - …`. Parser gap open.
- Two upload slots (user ask): 429 storm, both runs failed after the retry budget (The Capture S03E01, Roseanne S01E01 left pending). User chose one slot; config back to default. One Battle After Another uploaded + published (msg 48653); batch 3 published msg 48647.
- `~/src/kdownloads-batch3b.sh` (04:53 UTC): `resume --no-push`, then The Capture + Roseanne, one slot, one publish. 26 MB/s.
- `upload-kdownloads.sh dry|run [folder...]` now takes a folder list.

## Status 2026-10-31 07:05
- batch 3b done 06:04 UTC: resume END(0), The Capture END(0) (18 complete), Roseanne END(0) (221 complete), index pushed as msg 48904. 0 pending in the library (27027 sets).
- Only The Expanse (S05E03 duration case) and the 12 new folders remain.

## Status 2026-11-01 12:05
- 0.142.1 (uncommitted): where no video/audio stream declares a length, `streams::probe` reads the last minute's packets (`probe::media_end`) instead of trusting the container duration. Test `without_a_declared_length_the_last_frames_decide_it` (real mkv, DURATION tags patched out, late subtitle cue). Built + installed on bytefortress. Expanse S05E03 re-prepared END(0) 2.85 -> 2.65 GB.
- 12 new folders queued (`~/src/kdownloads-batch5.sh`, 11:05 UTC): samples -> `~/kdownloads-samples`; episodes renamed to TMDB numbering (`~/src/rename-kdownloads-new.py`, log `rename-kdownloads-new.log`, 352 renames): JJK 2nd season -> S01E25-47 (TMDB has one 59-ep season), Frieren S02 -> S01E29-38, One Piece abs 1-130 -> S01-S04 with TMDB's absolute ep numbers (S02E62...S04E130), Naruto release S01-S05 (35/48/48/48/41) -> TMDB S01-S04 absolute (52/52/54/62), Kenshin OVA 1-4 -> tmdb 317885 S01E01-04. FMA folder lost its `.mkv` suffix. All dry runs clean (518 episodes).
- Conversion keeps `jpn` audio (`PREPARE_AUDIO=ger,deu,eng,jpn`; default would drop the anime originals, irreversible with --delete-source). 3 parallel.
- Courses `add-course --category Development` (64 + 38 lessons, PDFs as documents), no prepare.
- Upload loop: `upload-kdownloads.sh run` every 10 min, one slot; a pass with nothing ready no longer publishes.

## Next (not done)
- Upload the remaining 45 folders once their conversion ends: `~/src/upload-kdownloads.sh run >> ~/src/upload-kdownloads.log 2>&1` (**append**, not `>`: the script skips folders with END(0) in that log, because `add` does not de-dup films).
- Re-download Breaking Bad S03E01 and The Expanse S05E01, or upload those seasons without them.

## Unresolved
- Commit the `--replace` fix with 0.142.0, or separately?
- Roseanne bonus files: skip or upload?

## Status 2026-11-02 00:26 (batch 5 running, batch 6 queued)
- Batch 5: courses, Expanse, YYH, NWT S3, Kenshin, Frieren, JJK published as msg 49466 (2026-11-01 21:12 UTC); FMA uploading; One Piece/Naruto converting.
- Batch 6 = 11 folders that arrived 2026-11-01, rows appended to `kdownloads-tmdb-ids.tsv` (backup `.bak-261102`), list `~/src/batch6-folders.txt`, script `~/src/kdownloads-batch6.sh` (started 23:26 UTC): converts after "batch 5 conversion done", uploads after "=== batch 5 ALL DONE", same pass loop.
  - Shows: True Detective S1 46648 (8), R.J. Decker S2 302074 (2/7), Friends Sex and Games 298550 (8), A Knight of the Seven Kingdoms 224372 (6, 2160p HEVC), Suits 37680 (134, HEVC), Space 2063 3534 (23). All dry runs clean.
  - Films: Disneys Eine Weihnachtsgeschichte 17979, Aristocats 10112, Drive – The Pretenders 1596275, Machete 23631, Der Grinch 8871.
  - Renamed: Friends Sex and Games `S01E01.Title.mp4` → `Friends Sex and Games S01E01 - Title.mp4` (parser gap); Space 2063 release `E00…E22` → `S01E01…S01E23` (TMDB counts the pilot as E1; checked against German titles). Space rars deleted after OK unpack.
  - → `~/kdownloads-skip/`: identical `(1)` copies (True Detective, Suits, Knight, Aristocats); already held: Mr. Fox 10315 (1080p), Venom 2 580489 (library 2160p), Krull 849 (1080p). Samples → `~/kdownloads-samples/`.
- Held: Terminator S.C.C. 433 (31 eps, 190 GB, VC-1 remux; prepare copies the picture, no player plays VC-1) — skip or re-encode? True Detective `(2)` (third copy, rars) still downloading → skip when done.

## Terminator: S.C.C. re-encode (2026-11-02 01:30)

- User chose re-encode over skip. TMDB 433 (S1 9, S2 22), not held; add-show dry run finds 31 episodes. TSV row added.
- Source: 1080p25 progressive VC-1, ~21 Mbit/s, ger+eng AC3 5.1, ger+eng PGS, nfo attachment.
- `~/src/reencode-kdownloads.sh <folder>`: libx264 medium CRF 20, maxrate 10M/bufsize 20M (box Telegram path ~17-20 Mbit/s), `nice 19`, all other streams copied. Replaces a file only after exit 0 + duration within 1 s; skips non-VC-1 files, so a rerun resumes. Log `~/src/reencode-kdownloads.log`.
- Test (60 s, under load): medium 34 fps, slow 22 fps, same bitrate → medium. ~2.1 M frames → ~17 h; expect ~3.5 GB/ep, ~100-110 GB total (source 190 GB).
- `~/src/kdownloads-terminator.sh` started 00:30 UTC: re-encode → convert-one → waits for "=== batch 6 ALL DONE" → `upload-kdownloads.sh run` → "=== terminator ALL DONE".

## 2026-11-02 09:05 (batch 5 done, batch 6 uploading)

- `=== batch 5 ALL DONE 07:42:53 UTC`, 0 failed; publishes 49466, 49536, 49800, 50044. Library: Expanse 61, YYH 5, NWT 30 (S1-S3), Kenshin 4, Frieren 38, JJK 47, FMA 64, One Piece 130, Naruto 220. Its last pass also took RJ Decker 2 and Knight 6 from batch 6.
- Batch 6 conversion done 04:09 UTC except FriendsSexGames END(1): sources already `.mp4`, so `--out <same dir>` refused ("would be written over its own source"). Redone with `prepare --mp4 --replace` → 8 rewritten, END(0) 08:03 UTC in convert-kdownloads-redo.log; dry run 8 eps. Missed batch 6's single upload pass (started 07:46); the Terminator script's pass after batch 6 ALL DONE uploads it.
- Terminator re-encode: ~28 min/ep, 2.0-3.3 GB/ep → ~80 GB, done ~15:00 UTC.
