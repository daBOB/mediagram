# Real Google TV box check — 2026-09-26 00:10–00:30

Device: Skyworth UHD Google TV STB (HPR302), Android 14, armeabi-v7a only, 4 cores, 3.9 GB RAM,
UI 1920x1080 @320dpi, physical 3840x2160. Network adb `192.168.0.35:5555`.
Display attached: HP OMEN 27k **monitor** (EDID), not a TV — overscan results reflect a panel
with no overscan; 24p modes are not offered by this sink (mode switched to 2160p59.94 on play).
App: com.mediagram.android 0.54.0 (versionCode 16), **debug build** (`pkgFlags=DEBUGGABLE`,
dexopt `run-from-apk`/`verify`; `cmd package compile -m speed` is capped to `verify` for a
debuggable app, so no AOT comparison was possible).

Profile used for all playback: **TV test**. Box left on **andre**, Home screen.

## Checks

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Overscan / edges | PASS with 2 minor defects | Home, Movies wall, title page keep 96px (48dp) side gutters; masthead fits. Defects D4, D5 below |
| 2 | Browsing performance (Movies, 851 films) | **FAIL** (debug build) | gfxinfo during DPAD_DOWN hold: 86.2% janky (109 frames), then 94.3% janky (53 frames over 8.1 s for 20 presses); p50 300 ms, p90 400–450 ms; `Slow UI thread` 47/53. ~6.5 fps while scrolling |
| 3a | H.264 playback (Das ist das Ende, 1080p, 7.3 GB, 3 parts) | PASS video / **FAIL audio** | first frame 6.3 s (press 00:14:32.07 → decoder frame-info 00:14:38.43); decoder `c2.realtek.video.avc.decoder` (HW). No audio: DTS track, see D1 |
| 3b | HEVC playback (Der 13te Krieger, 1080p, 6.5 GB, 2 parts) | PASS | first frame 4.2 s (press 00:21:49.01 → frame-info 00:21:53.19, decoder allocated 00:21:51.86); decoder `c2.realtek.video.hevc.main10.decoder` (HW); E-AC3 passthrough on DIRECT output (`AUDIO_FORMAT_E_AC3`, Standby: no) |
| 3c | ~30 s smooth play | PASS | positions advance 1:1 with wall clock; buffer 30 s ahead (H.264), 28 s ahead (HEVC); no decoder errors, no drop logs |
| 4a | Seek burst DPAD_RIGHT x10 (H.264 7.3 GB) | PASS | 306 s → 407 s; burst end 00:19:13.61 → PLAYING 00:19:16.74 = **3.1 s** (4.3 s from first key). Buffer after resume only ~1 s ahead, then grows ~+1.3 s per 3 s (fetch ≈ 1.4x bitrate); no re-buffer |
| 4b | Long-press seek x3 (H.264) | PASS | ~446 s → 512 s (each long-press = 2 steps); screenshot 1 s after last key already shows playing frame; PLAYING at next poll |
| 4c | Seek burst x10 (HEVC 6.5 GB) | PASS with log defect | burst end 00:22:44.72 → PLAYING 00:22:47.24 = **2.5 s**. 6 `E/LoadTask Unexpected exception loading stream` stacks, see D3 |
| 5 | Media keys | PASS | PLAY_PAUSE toggles; PAUSE idempotent; PLAY idempotent; FAST_FORWARD +10 s; REWIND −10 s; in course: NEXT 7 → 8 (Tools), PREVIOUS 8 → 7 (resumes 7 at its saved 0:54) |
| 5b | BACK order | PASS | controls visible → BACK hides controls → BACK leaves to title page (shows ► Resume 9:14) |
| 6 | Home mid-play → relaunch | PASS | HOME at 2:53; `am start -W` HOT 186 ms; lands on app Home, Continue shows Der 13te Krieger 3% · 2:54; title page offers ► Resume; resumed at 2:57 (177 s). Playback stopped when backgrounded |
| 7 | Subtitles + Notes (fixture) | PASS | See fixture section. Subtitle bottom at ~1020 px with controls hidden (inside 1026 safe line), lifts to ~500 px above the scrubber with controls shown; Notes panel opens, pages with DPAD_DOWN (paragraphs advance per press) |
| 8 | FATAL / ANR / UnsatisfiedLinkError / OOM | PASS | crash buffer empty; none in main log captures. Only dropbox entry is a `system_app_anr` at 23:58:08, before this run and not this app |

Cold start: `am start -W` COLD 1248 ms / 1267 ms.

## Defects

**D1 — DTS titles play silent, with no notice (high).**
Screen: player, Das ist das Ende (`… h264 · dts …`). Keys: Play.
Expected: audio, or a visible "audio format not supported on this device" note / fallback track.
Actual: video plays; no audio renderer: box has no DTS decoder (`dumpsys media.player` lists
ac3/eac3 Realtek decoders, none for DTS), mixer output `Standby: yes`, no DIRECT output; statistics
overlay has no audio row; Playback settings shows only Speed and Framing (no Audio section).
Scope: 269 of ~580 complete movies have `acodec=dts` in the local library (plus 8 truehd).
Screenshot: box-player.png (stats overlay: video row only). Needs a DTS path (FFmpeg extension
decoder or passthrough where the sink supports it) or at least a notice.

**D2 — Grid scrolling is very slow on this 32-bit box (high, measured on debug build).**
Screen: Movies wall. Keys: DPAD_DOWN held / 20 rapid presses.
Expected: smooth row-by-row scrolling.
Actual: 86–94% janky frames, p50 300 ms per frame, `Slow UI thread` on nearly every frame.
Caveat: debug build runs interpreted/JIT (no R8, no baseline profile, AOT refused for debuggable).
Re-measure with a release build + baseline profile before optimising code.

**D3 — Seek logs `E/LoadTask: Unexpected exception loading stream` (low).**
Screen: player, HEVC title. Keys: DPAD_RIGHT x10.
Actual: 6 stacks: `java.lang.InterruptedException at BlockingCoroutine.joinBlocking … at
playback.MlibDataSource.fetch(MlibDataSource.kt:126) ← MlibDataSource.read(MlibDataSource.kt:106)`.
The cancelled load's interrupt escapes `runBlocking` as a raw `InterruptedException`; Media3
expects `InterruptedIOException` for a cancelled load. Playback is unaffected; log noise only.

**D4 — Profile chooser: "New profile" card clipped at the right edge (low).**
Screen: Who's watching?. Card bounds [1754..1920] — runs into the screen edge with no gutter,
label reads "New profil…". Screenshot: scratchpad profiles.png.

**D5 — Title page synopsis runs to the bottom edge (low).**
Screen: title page (Das ist das Ende / Der 13te Krieger). Synopsis node bounds end at y=1080;
last line cut mid-glyph at the panel edge, no bottom gutter. Screenshot: scratchpad title-h264.png.

**D6 — Home "Continue · N" count disagrees with the cards shown (low, verify against web).**
TV test: Home shows "Continue · 3" with 2 cards (the in-progress Geldhochschule lesson is shown
under Next up instead); the Continue wall lists all 3. andre: Home "Continue · 2" with 1 card.
Either the count should match the cards or the dedupe should be stated; check what the web
player does before changing.

Observations (not defects): player controls auto-hide within ~2–3 s and re-show with focus on
the seek bar, so slow D-pad users must re-navigate to the bottom row; "Reading the channel…"
status line sits tight under the masthead (Home tab underline touches it); subtitle text over a
white slide has only a shadow (backing preference exists, left unchanged).

## Titles played (all on TV test)

- Das ist das Ende (2013) — H.264/DTS, 7.3 GB — left at 9:14.
- Der 13te Krieger (1999) — HEVC/E-AC3, 6.5 GB — left at ~2:59.
- Geldhochschule · 7 · Produkte ⁄ Instrumente — H.264/AAC, 8.9 MB — left at ~1:01 (paused).
- Geldhochschule · 8 · Tools — a few seconds via MEDIA_NEXT.

Nothing marked finished; no list / Watchlist / Kids marks made (Add to list dialog opened by a
mis-navigation and dismissed with BACK; nothing created).

## Preferences changed / reset

None changed. Playback settings panel was opened (focus moved over Framing 4:3 without selecting;
Speed stays 1×, Framing stays Fit). Subtitle preferences not touched. Statistics overlay toggled
on and off.

## Fixture (check 7)

- Cold launch 00:24, waited 14 s; `files/catalog/current -> v-1790374521-1`, no -wal/-shm.
- Pulled `library.db` (1818624 B, 1177 sets, **0 assets**); byte-identical to the pre-relaunch copy.
- Copy + `ATTACH ~/.local/share/mediagram/library.db`; `INSERT OR IGNORE INTO assets` for set ids
  present: **324 rows** (162 subtitle/und + 162 summary). Columns identical (set_id, kind, lang, body).
- Pushed to /data/local/tmp, `run-as cp` over current/library.db; verified 324 on device.
- Restored: pushed original, `run-as cp`, verified **0**; cold relaunch reinstalled index
  (`current -> v-1790374521`), assets **0**. /data/local/tmp copies deleted.

## Other device operations

- `cmd package compile -m speed[-profile] -f com.mediagram.android` (result: `verify`, no effect
  beyond re-verifying; no data touched).
- Two `am force-stop` + cold `am start` for the fixture (app state and sign-in unaffected).

## Screenshots

Committed: plans/260924-2239-android-tv-surface/reports/box-home.png (andre Home),
box-player.png (H.264 playback with controls + statistics), box-subtitles.png (lesson subtitle
lifted above controls).
