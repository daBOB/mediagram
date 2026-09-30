# Red team: assumption destroyer / scope auditor — subtitles plan

Read-only. Evidence comes from the working tree at `a21b7df0` and from `sqlite3 -readonly` on the local index and the channel follower copy (`~/.cache/mediagram-channel-index/current/library.db`: v12, 6,205 sets). ffprobe/ffmpeg were run only on local source files and scratch fixtures. Nothing was uploaded, published or modified.

These findings are already covered by the other red-team reports, so they are not repeated here: web held/offline parity, the EXPECTED_SCHEMA lockstep that breaks phase 02's check.sh, `push-index --force` bypassing the guard, and the duration-fallback pool that shrinks (Finding 5 below adds new evidence to that one).

## Finding 1: The backfill assumes the uploaded source files still exist. For de/en-subtitled titles, 98% of them are not reachable from this machine
- **Severity:** Critical
- **Location:** Phase 07, "Key insights" (lines 17-18) and "Runbook" step 4 (line 47); Phase 01, "Overview" (line 15) and "Key insights" (line 20); plan.md "Key decisions" (≈2,000 titles)
- **Flaw:** The whole value of phases 06/07 for the existing library rests on "exact byte size identifies a set's source". Size is indeed near-unique. But the plan never checks that the sources are still on disk, and most of them are not. Phase 01 also misstates what the old remux kept, so "healed by phase 07" has no fallback when the source is gone.
- **Failure scenario:** Phases 02–07 ship, taking about 8 days. The backfill then matches a few dozen films and shows. The remaining ~2,770 subtitled films and episodes stay without subtitles. The remux-damaged mp4 episodes have no subtitle stream left even in the channel copy, so no later tool can recover them.
- **Evidence:**
  - I listed every video file reachable here: both btrfs 18 TB drives plus the <NAS share> NFS mount (`192.168.0.240:/hidrive`, where the backups' `source:` meta keys say 30 Rock was uploaded from). That is 23,363 files. Joining them by exact size against the channel's 5,048 movie/ep/docu sets gives **286 matches (285 ep/mkv, 1 movie/mkv)**.
  - Of the 2,834 sets whose `slang` lists de/en, **63** have a reachable source. **0 of the 2,028 mp4 sets** match. The plan's top mp4 shows (American Dad 248, Die Simpsons 229, King of Queens 186) have 0 files on disk. Frasier exists only as a different release (`dmpd-frasier…2160p…mkv`), while the index holds mp4.
  - `<NAS share>` now holds only Brooklyn Nine-Nine; the 30 Rock sources are gone.
  - Jurassic Park (1993): the index has `mkv 9,101,363,540 h264/dts`; the disk has a different release (`9,942,133,985`, hevc, untagged PGS).
  - Sources are deletable by design: `add --delete-source` / `add-show` (`commands/add.rs:18`, `upload/session/item.rs:41`, `commands/args.rs:78,115,140`).
  - Phase 01 line 15 says the old remux "keeps … one subtitle stream". A scratch test (2 audio + 2 `mov_text`, `ffmpeg -i src.mp4 -c copy -movflags +faststart`) kept `video, audio(ger)` and **zero** subtitle streams. `-map 0:V? -map 0:a? -map 0:s?` kept all five streams and the forced flag. The uploaded copies of remuxed mp4s therefore hold no subtitles at all.
  - Data has drifted from the plan's numbers: the channel has 1,266 inline subtitle rows (en 184), while the local index has 1,235 (en 153). So 31 lesson rows were written by the other machine, which contradicts phase 07 line 19 ("this one (it wrote them)").
- **Suggested fix:** Before building phases 06/07, add a feasibility gate:
  - Ship the matcher alone first (`subtitles backfill --dry-run`: walk, size/duration match, no ffmpeg). Run it on **both** machines and record the counts in plan.md.
  - Re-scope the goal, effort and phase 07 success criteria from those counts, and tell the user how many existing titles can realistically gain subtitles.
  - Fix phase 01 line 15 and line 20: subtitle damage is healed only where the source still exists.
  - Add <NAS share> to the runbook folders if its sources count.

## Finding 2: The forced-track rule misses how this library actually marks forced subtitles (no flag, no title)
- **Severity:** High
- **Location:** Phase 06, "Key decisions" 2 (line 18); plan.md "Default rule"
- **Flaw:**
  - Forced is decided only by `disposition.forced` or a title matching `forced|erzwungen`.
  - The German-dub releases here carry their sign/foreign-dialogue track as an untitled, unflagged German text track.
  - `select` therefore labels it "German" (regular). Forced-auto-on never fires for German audio, and the picker offers a 2-cue track as full "German" subtitles.
- **Failure scenario:**
  - A viewer watches Boardwalk Empire in German. The foreign-language lines and signs never show, although the file carries their translation. That is the headline behaviour of decision 3.
  - A profile with preference "German" gets the same 2–12 cues, presented as full subtitles.
  - The survey samples (Arcane, Band of Brothers) are flagged releases and not in the index at all, so the fixtures never test this shape.
- **Evidence:**
  - Among the German text tracks in the reachable matched sources, 52 of 53 are `{"c":"ass","t":null,"fo":0,"hi":0}` (all of Boardwalk Empire S1–S5, `<media drive>/<series folder>/…`). Only 1 is flagged forced.
  - Extracting three of them with the plan's own command (`-map 0:2 -c:s webvtt`) gave 2, 4 and 12 cues per ~55-minute episode. The S02E01 track has 2 cues: "FALLS Jesus / JEMALS nach Atlantic City KAM", "BRUTKÄSTEN / WlR RETTEN BABYLEBEN".
  - The channel has 235 sets with German-only audio **and** German-only subtitles (`alang='["de"]' AND slang='["de"]'`: ep mkv 46, ep mp4 115, movie mkv 68, movie mp4 6). That is the same shape.
  - Neither Arcane nor Band of Brothers is in the local or channel index (checked by show name and exact size).
- **Suggested fix:**
  - Add a cue-density rule to `select.rs`, run after extraction, while the VTT is still in hand. For example, a de/en track is treated as forced when it has fewer than ~10 cues per hour, or fewer than 25% of the densest same-language track.
  - Add a Boardwalk-shaped fixture to `select_tests` and to `choice-cases.json`.
  - Pick the phase 06 end-to-end title from this shape, not from a flagged release.

## Finding 3: Titles whose only text track is forced get auto-shown cues with no size or offset control and no CC on any surface
- **Severity:** Medium
- **Location:** Phase 03, "Browser" (`subtitle-picker.js`, line 35); Phase 04, "Key insights" (pickers "render whatever `subtitleOptions()` returns"); Phase 05, "Requirements" (line 20)
- **Flaw:**
  - The picker is "Off + regular tracks", and `subtitleOptions` returns an empty list when there is no regular track ("Off alone is not a choice").
  - Every surface hides the cue-style controls behind a non-empty picker. The plan adds a track that shows without being in the picker, but does not move that gate.
- **Failure scenario:** A title shaped like Band of Brothers (only `ger "Forced (SRT)"` as text) opens with forced cues showing. If they are 2 s late, or too small on the TV, the viewer cannot nudge, resize or hide them. The section and the CC button are all hidden.
- **Evidence:**
  - Android phone: `ui-mobile/.../PlayerSettingsSheet.kt:81-91`. `SubtitleStyleSection` is inside `if (subtitleOptions.isNotEmpty())`.
  - Android TV: `ui-tv/.../TvPlayerSettingsPanel.kt:80-82`, same gate.
  - Android choice: `feature/player/.../SubtitleChoice.kt:32-33` (`if (available.isEmpty()) return emptyList()`).
  - Web: `player.js:640` (`cuePanel.trigger.hidden = document.getElementById("subs").hidden`), `transport.js:389` (`subs.hidden = options.length < 2`) and `:323` (toggle returns when hidden).
  - Phase 05 line 20: "CC button hidden for a title with no regular tracks".
- **Suggested fix:**
  - Gate the style/offset section on "some track can show", forced included.
  - Add a forced-only case to `choice-cases.json` that states the picker and style visibility.
  - Ask the user whether CC or "Off" should hide forced (this is the open question the scope critic raised).

## Finding 4: The plan silently reverses a documented decision: the "Subtitles" fact stops describing the file
- **Severity:** Medium
- **Location:** Phase 03, "Browser → Facts" (line 38); Phase 04, "Requirements → Facts" (line 41)
- **Flaw:**
  - Phase 03 makes the film page and the series summary list bundle track labels instead of the file's `slang`.
  - `series-summary.js` states the opposite on purpose. The film facts panel is "What the file is: the questions a viewer asks when a title will not play".
  - Together with Finding 1, most titles will show no Subtitles fact at all, and a 30-language PGS release will say nothing.
- **Failure scenario:** A viewer checks whether a film has English subtitles before picking a release. The page is silent (no bundle could be backfilled), although the file carries 36 subtitle languages. Android copies the same behaviour (phase 04), so both surfaces drift from the files together.
- **Evidence:**
  - `web/public/lib/catalog/series-summary.js:46-49`: "Both audio and subtitles are read from the file's own tracks. The `assets` table knows about … a different question … answering the header with it would say a show has no subtitles when its files do."
  - `web/public/lib/catalog/film-page.js:98` doc line.
  - Channel `slang` lists of 36 languages on 38+ sets (query above).
- **Suggested fix:**
  - Keep "Subtitles" from `slang`, and only fix the JSON-string parse bug.
  - If a "playable subtitles" line is wanted, add it as a separate fact.
  - Otherwise, surface the reversal to the user as a decision.

## Finding 5: The backfill's duration fallback never compares the title, and the runbook walks whole drives full of unrelated mp4s
- **Severity:** Medium
- **Location:** Phase 07, "Key insights" (line 17), "Runbook" step 4 (line 47), "Risk assessment" (line 88)
- **Flaw:**
  - The fallback matches on duration ±2 s and container. It compares SxxEyy only "when the file name carries one". Show or film name is never compared.
  - The runbook hands it whole drives rather than media folders.
  - This adds new evidence to the failure-mode report's F1 (the candidate pool shrinks).
- **Failure scenario:** A 23-minute course lecture, or an un-uploaded mp4 film, carries an English text track. Its duration is unique among the candidate mp4 sets, so it attaches its subtitles to an unrelated episode. The shrinking pool makes this more likely on every re-run.
- **Evidence:**
  - The two local drives hold 19,927 `.mp4` files. Only 18 carry SxxEyy (17 are Boston Legal); the rest are mostly Udemy/Pluralsight lectures.
  - In the channel, **181 of 3,651** mp4 movie/ep/docu sets are the only set within ±2 s of their duration. Any such mp4 without SxxEyy matches them uniquely.
  - Runbook: `backfill --dry-run <second data drive> <media drive> …`.
- **Suggested fix:**
  - Require a name match for the fallback: the parsed show plus SxxEyy for episodes, or title (and year if present) for films. Keep "unique candidate" as a second condition.
  - Name media folders in the runbook, not drive roots.

## Finding 6: Lessons carry both a `.vtt` and an `.srt` of one transcript, and the new sidecar rule has no tie-break between two sidecars
- **Severity:** Medium
- **Location:** Phase 06, "Key decisions" 2 and 3 (lines 18-19), "Implementation steps" 3 (line 54)
- **Flaw:**
  - Sidecar discovery now accepts `.vtt|.srt`.
  - The transcriber writes both forms of the same words beside every lesson, so both land on the same `(und|lang, forced=0, sdh=0)` triple.
  - The tie rules cover only sidecar vs embedded and embedded vs embedded. The tests list `x.vtt` and `x.srt` only as separate cases.
- **Failure scenario:** A newly added course lesson shows two identical "Subtitles" rows, or keeps an unspecified one. Every lesson also spawns an ffmpeg SRT→VTT conversion it does not need.
- **Evidence:**
  - `crates/mediagram/src/course/sidecars.rs:3-7`: "A transcription leaves several forms of the same words beside each video: `.vtt`, `.srt`, `.txt` … Only the `.vtt` travels".
  - Today only `format!("{stem}.vtt")` is read (`:39`).
- **Suggested fix:**
  - Add a rule: for the same triple, `.vtt` beats `.srt`.
  - Add a test for `x.vtt` and `x.srt` present together.

## Finding 7: The extraction pass does not run "sequential with the upload". It runs before it, in the foreground, as a second full read of every file
- **Severity:** Medium
- **Location:** Phase 06, "Requirements" (`prepare_set.rs` wiring) and "Risk assessment" row 1 ("Certain × Low … sequential with the upload")
- **Flaw:**
  - `stage::prepare` is placed in `prepare_and_record_set`. For `add`, that function runs before the upload is handed to a background process, so the terminal waits for ffmpeg to demux the whole source.
  - For `add-show`/`add-course` it runs inside the session loop, before each item's upload starts.
  - An mkv's subtitle packets are interleaved, so `-map 0:s` still reads every byte. Sources on the <NAS share> NFS mount pay for that over the network, twice.
- **Failure scenario:** `mediagram add` on a 40 GB 4K remux from <NAS share> holds the terminal for several extra minutes before "set … planned". `add-show` of a season adds a full read per episode to the wall clock.
- **Evidence:**
  - `commands/add.rs:1-5`: "Everything up to the upload is `prepare_and_record_set`"; then `:19` and `:29-30` (`spawn_finish_set` after it).
  - `upload/session/item.rs:62` (prepare inside the session loop).
  - <NAS share> is NFS (`findmnt`: `192.168.0.240:/hidrive nfs`) and holds 147 of the 286 matched sources.
- **Suggested fix:**
  - Stage in the background `finish-set` process (the source is still there until `--delete-source` runs after completion), or run it after the upload finishes.
  - Correct the risk row to state where the time lands.
  - Keep the probe-only check in the foreground.

## Unresolved questions
1. Does the other machine (`<other machine's media folder>`) hold the mp4 episode sources? Finding 1's counts cover only this machine. Its dry run decides whether phase 07 is worth building.
2. Should a remembered/picked "Off" or CC hide an auto-shown forced track? This is user-visible, so ask the user (see Finding 3 and the scope critic's Finding 2).
3. Which cue-density threshold separates a forced track from a full one? Measure it on a few more reachable German-dub sources before fixing a number.
