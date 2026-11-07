# Spike: an optional sharpening filter for TV playback — does the box keep up?

The user asked for "optional video filter to improve image quality" and approved this test: "yes run the filter test". This is a **spike**. Its output is numbers and a recommendation. The code stays on a spike branch until the user decides.

Motion smoothing is out of scope: the TV's own MEMC does that, and the app already matches the film's frame rate.

## Question
Can the box (Realtek RTD1325, a weak GPU; UI at 1080p, panel 4K) run a per-frame GPU sharpening filter on SDR video without dropping frames? Test at 720p, 1080p and, if possible, 2160p. And does the picture look better? Answer that with paired screenshots for the user.

## Build
- New worktree: `git -C /home/andre/Workspace/mediagram worktree add /home/andre/Workspace/mediagram-sharpen -b spike/video-sharpen feat/tv-parity` (HEAD 16567443, 0.120.4). Keep versionName **0.120.4**: the same versionCode installs over the box's 0.120.4 with `-r`, and the release key makes the signatures match.
- The filter:
  - Media3 1.10.1's video effects: `ExoPlayer.setVideoEffects(listOf(...))`, with the `androidx.media3:media3-effect` artifact at the same version.
  - Write one custom `GlEffect`: AMD FidelityFX **CAS** (contrast-adaptive sharpening). It is a small single-pass fragment shader with a sharpness of about 0.4–0.5, and the only knob. Cite the CAS source/licence (MIT) in a comment.
  - Output stays at the input resolution.
- Toggle, so one build serves both conditions:
  - Read the system property `debug.mediagram.videofilter` when a title opens: "1" means on; anything else, or unset, means off. Read it via `android.os.SystemProperties` reflection, or `Runtime.exec("getprop …")`.
  - Turn it on and off with `adb -s 192.168.0.46:5555 shell setprop debug.mediagram.videofilter 1|0`, then reopen the title.
  - Skip the filter for HDR content (colorInfo transfer PQ/HLG), because effects would tone-map it.
- Where the player is built: grep feature/player for the ExoPlayer.Builder / `setVideoEffects`. This app sometimes plays through its own renderers or tunnelling; check that effects aren't silently ignored and log once whether they're active.
- Frame counters:
  - Log the video `DecoderCounters` (renderedOutputBufferCount, droppedBufferCount, maxConsecutiveDroppedBufferCount, skippedOutputBufferCount) at INFO every 10 s, plus once on release, under the tag `MgFilter`, and read them with `adb logcat -s MgFilter`.
  - Check that R8/proguard doesn't strip `Log.i` in release. If it does, use `Log.w`.
  - Also record `adb shell dumpsys gfxinfo com.mediagram.android` around each run (UI jank while video plays).
- Build: run `/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/build-tv-release.sh /home/andre/Workspace/mediagram-sharpen <outdir>` (outdir: scratchpad `tv-sharpen-spike/`). It runs build-android-core.sh and assembleRelease, verifies the release cert and copies the apk and API 31 .dm.
  - If assembleRelease fails on missing FFmpeg audio libraries, copy them from /home/andre/Workspace/mediagram-tv (same paths, gitignored). An earlier session had to do this.
- Install: `adb -s 192.168.0.46:5555 install-multiple -r app-release.apk app-release.dm`. Confirm dexopt `install-dm`.

## Measure (box only)
- **Profile:** the box is on profile "andre". Before any playback, switch to the test profile **"TV test"**: Settings › Profile, or the profile picker, using arrows and OK on the profile plate only. At the end, switch back to "andre". Plays on "TV test" are fine.
- **Titles:** pick one SDR title each at 720p, 1080p and 2160p (the player's format line or stats overlay shows the resolution). Prefer titles already in the box's cache or on the home cache server, so the network doesn't confound the numbers. If no SDR 2160p exists, say so.
- **Per title:** play the same 60 s twice (filter off, then on) from the same start point. For each run record:
  - rendered and dropped frames, and the max consecutive drops;
  - UI gfxinfo;
  - one screencap of the same paused frame, off and on (`exec-out screencap -p`). The video plane may capture black; if so, say so.
- **Ship rule (spike verdict):**
  - Filter **viable** at a resolution if its dropped share is ≤ 0.5% of rendered frames and no worse than off by more than 0.3 points, with max consecutive drops ≤ 2.
  - Report per resolution.
  - Also note heat or slowness signs, such as stutter visible in the logs or skipped buffers.

## Box rules (hard)
- Pin `-s 192.168.0.46:5555` in every command. Never touch the tablet caad49da; another walk is running on it.
- Navigate only: DPAD, BACK, OK on a pill, plate or profile, and play/pause. Never press OK on a Settings toggle or menu entry, except the profile choice. Never "Start over". Never change a system setting. Never uninstall or `pm clear`. `setprop debug.mediagram.videofilter` is the one allowed property write. Clear it at the end (`setprop debug.mediagram.videofilter ""`).
- Foreground `sleep` is blocked: run timed sequences as background scripts and wait for their completion notice.
- A tool hook blocks shell commands whose text contains a `build` path segment.
- **End state, always:** reinstall plain 0.120.4 from scratchpad `tv-0.120.4/` (apk + dm, install-multiple -r), and confirm versionName 0.120.4 / install-dm. Clear the property. Restore profile "andre".

## Output
- Commit the spike code on spike/video-sharpen with conventional commits, no AI references and no trailers. Don't merge it anywhere.
- Report: `/home/andre/Workspace/mediagram-tv/plans/reports/tv-video-sharpen-spike-261010-1615-report.md` (uncommitted). It holds:
  - what was built;
  - per-resolution tables (off/on: rendered, dropped, dropped %, max consecutive, UI p90);
  - the verdict per resolution;
  - the screenshot paths;
  - caveats (HDR skipped, tunnelling, effects-path quirks);
  - a recommendation: ship as a TV setting (and at which resolutions), or don't.
