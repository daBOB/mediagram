# Phase 10 — Web: AirPlay in Safari

**Spec:** §7, §8. **Status:** pending. **Starts only after the AirPlay probe** (phase 01 report). The probe decides how transcoded titles are handed over: Case C (alternate `<source>`) or Case B (native HLS).

**Context:**
- **Markup:** `web/public/index.html` (`#control-card` tools row :200, `#together-label` :156).
- **Playback:**
  - `web/public/lib/playback/hls-playback.js`: `needsNativeHls()` :29, `playTranscoded` :174, the native branch :182-187, hls.js :189-213;
  - `player.js`: direct `video.src` :726, `convert()` :213; `transport.js`.
- **No existing code.** There is no AirPlay, `webkitShowPlaybackTargetPicker` or `x-webkit-airplay` anywhere yet.

### Task 10.1: The AirPlay button

**Files:**
- Create: `web/public/lib/playback/airplay.js` (+ `.d.ts`)
- Modify:
  - `index.html`: add `<button id="airplay" type="button" hidden>AirPlay</button>` after `#cast`, and give the `<video>` the attribute `x-webkit-airplay="allow"`;
  - `player.js`: one `mountAirPlay({...})` line beside `mountPlayerCast`, with its ceiling raised.
- Test: `web/test/airplay.test.ts` (a stub video with `webkitShowPlaybackTargetPicker` and the two events dispatched)

**Interfaces — Produces:** `mountAirPlay({video, button, label}): void`. Where `window.WebKitPlaybackTargetAvailabilityEvent` exists:
- `webkitplaybacktargetavailabilitychanged` sets `button.hidden = event.availability !== "available"`;
- a click calls `video.webkitShowPlaybackTargetPicker()`;
- `webkitcurrentplaybacktargetiswirelesschanged` sets `label.textContent` to `"Playing on AirPlay"` while `video.webkitCurrentPlaybackTargetIsWireless`, and back to what it was otherwise.

Elsewhere it does nothing, and the button stays hidden.

- [ ] **Step 1: Failing tests:**
  - `"no WebKit, no button"`;
  - `"an available target shows the button"`;
  - `"clicking opens Safari's picker"`;
  - `"playing wirelessly labels Playing on AirPlay"`.
- [ ] **Step 2: Run** `bun test test/airplay.test.ts` → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(web): AirPlay from Safari`.

### Task 10.2: Transcoded titles reach the TV

**Files:**
- Modify: `web/public/lib/playback/hls-playback.js`
- Test: `web/test/hls-playback-airplay.test.ts`

**Interfaces — Produces:** `airPlayAlternate(video: HTMLVideoElement, playlist: string): void`, called in `playTranscoded` beside `attachMedia`.
- **Case C:** where `WebKitPlaybackTargetAvailabilityEvent` exists, append one `<source type="application/x-mpegURL" src=playlist>` to the video, removing any earlier one, so WebKit hands the playlist over when an AirPlay target is chosen. On teardown the same source is removed.
- **Case B instead:** `needsNativeHls()` also returns true where `WebKitPlaybackTargetAvailabilityEvent` exists and `video.canPlayType("application/vnd.apple.mpegurl")` is non-empty. `airPlayAlternate` is then not needed. Record this as a deliberate trade-off in the code comment: hls.js's bitrate adaptation is lost in Safari.

- [ ] **Step 1: Failing tests** for the probe's case:
  - C: `"Safari gets the playlist as an alternate source"` and `"teardown removes it"`;
  - B: `"Safari with AirPlay plays native HLS"` and `"Chrome still uses hls.js"`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS, plus `test/hls-playback-lifetime.test.ts`.
- [ ] **Step 5: Commit** `feat(web): transcoded titles can be AirPlayed from Safari`.

### Task 10.3: Device check (the user's Apple device)

- [ ] With the user, in Safari against the live player's LAN address, AirPlay to the Hisense:
  - one direct film;
  - one transcoded film;
  - subtitles on, for each.

  Record what plays, and whether the label reads "Playing on AirPlay", in `reports/walk-261018-airplay-report.md`.
- [ ] If subtitles don't reach the TV, add that sentence to spec §7 as a known limit.

**Success criteria:** the tests pass, the device check is recorded, and nothing changes in Chrome or Firefox (existing tests pass).
