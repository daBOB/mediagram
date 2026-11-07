# Spike report: CAS sharpening on TV playback (box, 2026-10-10)

Brief: `tv-video-sharpen-spike-261010-1615-brief.md`.

**Status: DONE_WITH_CONCERNS.**
- The filter is viable at 720p and 1080p, but not at 2160p.
- Sharpening is clearly visible.
- The effects path also darkens the picture, which is a defect to fix before it could ship.

The first attempt was blocked on profile PINs. The user then chose the `test` profile (PIN 1357), and every test play below ran on it.

## What was built
- **Where:** worktree `/home/andre/Workspace/mediagram-sharpen`, branch `spike/video-sharpen` (from feat/tv-parity 16567443), commit `e2785e3b`. Not merged anywhere.
- **The filter:** `core/playback/CasSharpenEffect.kt` is AMD FidelityFX CAS, the no-scaling path, ported from `ffx_cas.h` (MIT, cited). It runs as a media3 `GlEffect`: one 3×3 pass at the input resolution, sharpness 0.5 (peak −0.154).
- **Toggle:** `core/playback/VideoFilterSpike.kt` reads `debug.mediagram.videofilter` before every `prepare`.
- **Counters:** the same file logs the `DecoderCounters` every 10 s and on release, plus how many frames the CAS shader drew. The HDR guard removes the effect again on a PQ/HLG format.
- **Logging:** tag `MgFilter`, at `Log.e`, because the release R8 rules strip `Log.v/d/i/w`.
- **Build:** media3 1.10.1 `media3-effect`, versionName 0.120.4, release cert. Installed with `install-multiple -r` (apk + dm, `install-dm`).

## Protocol as run
- **Order:** every filter-off run came first, on the fresh process the install started (`graphBuilt=false` on each open). Then `setprop 1` and the on runs.
- **Start points:** a prep play put each title at a content start point (30 Rock 5:07.6, Boston Legal 5:40.6, Bad Boys II 5:14.1).
- **Each run:** open → pause at once → screencap → `gfxinfo reset` → play 60 s → dump gfxinfo → pause → rewind 4×15 s → leave. Each on run therefore started within 0.1–0.7 s of its off run's start.
- **Bad Boys II:** it was streamed from Telegram once, in a warm-up pass over 5:00–6:14 (0 drops while streaming). Both measured runs replayed that window, which the player had written into the box's cache. So the measured numbers are **probably not network-confounded**, although the title was streamed.
- **Paired frames:** these needed a second method.
  - **Problem:** the run-start frames fell on either side of a cut in Boston Legal. And on the direct path, HEVC frames capture **black** (the `hevc.main10` decoder output); AVC captures fine.
  - **Method:** media3's video graph stays built once created, so a title reopened with the property at 0 still renders through GL with no effect, and that path can be captured. A media pause sent during open lands on the exact resume frame. Both shots of each pair are therefore the **same frame** (same media_session position to the ms). The "on" shot ran CAS; the "off" shot ran the same GL path without it.
  - **Direct-path reference:** a third Boston Legal shot was taken at the same position on the plain 0.120.4 after reinstall.

## Results (release counters, whole run: open + 60 s play + rewind)

| Res (title, fps, codec) | Arm | Rendered | Dropped | Dropped % of rendered | Max consec. | UI p90 (gfxinfo) |
|---|---|---|---|---|---|---|
| 720p (30 Rock S6E19, 25, HEVC) | off | 1513 | 0 | 0.00% | 0 | 89 ms* |
| | on | 1527 | 3 | **0.20%** | **2** | 44 ms* |
| 1080p (Boston Legal S3E1, 23.976, AVC) | off | 1453 | 0 | 0.00% | 0 | 29 ms* |
| | on | 1466 | 0 | **0.00%** | 0 | 36 ms* |
| 2160p (Bad Boys II, 23.976, HEVC) | off | 1454 | 5 | 0.34% | 5 | 30 ms* |
| | on | 778 | 974 | **125%** (56% of all frames) | **21** | 250 ms, 100% janky |

\*gfxinfo saw only 10 UI frames per run (the UI is idle while video plays), so p90 is a weak signal except for 2160p on.

**Proof the filter was active:**
- Every on open logged `filter=on graphBuilt=true` and `CAS shader configured WxH` at the title's own resolution (1280x720, 1920x1080, 3840x2160).
- The shader's frame count rose in step with rendered frames. Over the 1080p window, `casFrames` rose by 1425 and `rendered` by 1425.
- The GL-off reopens left `casFrames` unchanged.

**Notes per resolution:**
- **720p on:** all 3 drops fell in the first 10 s after resuming from pause, then none for 50 s.
- **2160p off:** the 5 drops came in the first 3 s (start burst or the frame-rate mode switch), then 0.
- **2160p on:** about 16 fps dropped against about 13 fps rendered, steadily (24 fps source). The main thread stalled too: the 10 s ticks arrived 1–4.5 s late. Thermal status stayed 0 (cpu-thermal 62 °C afterwards), so the cause is GPU fill rate, not throttling.

## Verdict per resolution
Ship rule: dropped ≤ 0.5% of rendered, no more than 0.3 points worse than off, and max consecutive drops ≤ 2.

| Res | Off → on dropped % | Verdict |
|---|---|---|
| 720p | 0.00% → 0.20% (+0.20 points), max consecutive 2 | **Viable, at the limit.** Max consecutive is exactly at the rule's ceiling, from one run. |
| 1080p | 0.00% → 0.00%, max consecutive 0 | **Viable.** |
| 2160p | 0.34% → 125% of rendered, max consecutive 21 | **Not viable.** |

## Does it look better?
- **CAS adds visible crispness** at 720p (wood grain, dress print, hair) and 1080p (hair, skin texture, bar edges).
- **Edge energy** (mean absolute Laplacian of the frame above the controls), on vs GL-off: +48% at 720p, +64% at 1080p, +19% at 2160p (a dark scene, and screencaps are 1920x1080).
- **It also amplifies grain.** The 3× eye crop at 1080p shows noisier skin.
- **The effects path alters the picture even without CAS.**
  - GL-off is about **3.6% darker** than the direct path (luma 0.607 vs 0.630), and about **12% softer**.
  - Part of what looks "different" is therefore the pipeline, not the sharpening. Likely a colour-range or transfer mismatch in media3's SDR conversion. Not investigated.

**Screenshots** (in `/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/tv-sharpen-spike/`):
- `compare/720p-30-rock-off-vs-on.png`: GL-off vs on.
- `compare/1080p-boston-legal-off-vs-on.png`: direct-off vs GL-off vs on.
- `compare/1080p-boston-legal-eyes-3x.png`: direct-off vs on, at 3×.
- `compare/2160p-bad-boys-ii-off-vs-on.png`: GL-off vs on.
- Full frames: `runs/{r30,bl,bb}-pair-{on,off-gl}.png` and `runs/bl-pair-off-direct.png`.
- Run-start frames: `runs/*-off-paused.png` and `*-on-paused.png`. The HEVC off frames are black, and Boston Legal's two start frames sit either side of a cut.
- Logs: `mgfilter.log`, `runs/*.txt`, `runs/*-gfx.txt`.

## Caveats
- **HDR guard untested on device.** No HDR title was played. The guard can only empty the effect list after the format arrives, so an HDR title would still pass through GL. A real version must decide before `prepare`, from `MediaSet.hdr`.
- **Sticky graph.** Once a title has used effects, every later title runs through the GL path, even with the filter off, until the player is released (process-singleton ExoPlayer). Toggling the setting therefore needs a player rebuild.
- **Tunnelling** is not used by the app, so effects were not silently ignored. The "on" path gives up the decoder→SurfaceView hardware overlay for GPU composition.
- **Sample size.** One run per arm. The 720p "at the limit" verdict rests on 3 drops in one run.
- **Screencaps** are 1920x1080 composites of a 4K panel. 2160p detail is downscaled in them, so the 2160p pair understates what the panel shows.

## Recommendation
**Do not ship as-is.** If it is pursued:
- **Offer it** as an opt-in TV setting, off by default, only for sources ≤ 1080p.
- **Never at 2160p.** It overloads the box, and 4K sources gain nothing from it.
- **Before shipping, fix three things:**
  1. Find and fix the GL path's tone shift (darker, softer). Without that, "off" and "on" are not a fair choice.
  2. Decide on/off and the HDR skip before `prepare`.
  3. Rebuild the player when the setting changes.
- **Consider a gentler sharpness** (about 0.3), because 0.5 visibly lifts grain.
- **Re-run 720p** two or three times before trusting "viable".

## End state (verified)
- Plain 0.120.4 reinstalled from `scratchpad/tv-0.120.4/` (no `MgFilter` strings in its dex): `versionName=0.120.4`, `speed-profile` / `install-dm`.
- `debug.mediagram.videofilter` empty.
- Profile: **left on `test` for the user**, on the app's Home (avatar T). No PIN was created or changed, and no switch back to andre was attempted.
- **Plays on `test`:** 30 Rock S6E19, Boston Legal S3E1, Bad Boys II. Their saved positions are about 5:08, 5:41 and 5:15.
