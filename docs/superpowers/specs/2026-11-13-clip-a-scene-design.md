# Clip a scene — design

**Asked 2026-11-10:** "i need to feature to clip a scene. on android mark the position is enought but on web i need
to export feature". **Why (2026-11-13):** "maybe to upload the clip to instagram".

## The user's decisions (verbatim; do not change)

- Android only marks the position. The web player exports.
- The clip is **widescreen, as the film is** (chosen 2026-11-13 over a vertical 9:16 crop and over vertical with
  blurred bars). Instagram's own editor can zoom to fill a Reel; we don't crop.

Every other choice below is marked **my call** and can be corrected.

## In short

A viewer marks a scene on any surface. The mark is a per-profile row that travels in the existing sync record, the
same way My List does, so a scene marked on the TV box shows up in the web player. In the web player a mark (or any
stretch picked on the spot) becomes an A–B range. **Export** has the web player's server cut that range with ffmpeg
into an MP4 that Instagram accepts, and the browser downloads it. On a phone the file lands in Downloads, ready to post.

## Goals

- Marking takes one press and does not stop playback, on the TV box, phone and tablet, and in the web player.
- A mark made on one device is on every other device of that profile, like My List.
- From the web player, on a desktop or a phone browser, a clip is one MP4 that uploads to Instagram as is: H.264,
  AAC stereo, SDR colours, at most 1080p, start and end on the exact frame the viewer chose.

## Non-goals

- Exporting on Android (user's decision).
- Vertical or cropped clips (user's decision: widescreen).
- Subtitles burned into the clip, text overlays, music, any editing beyond start and end. *(my call: nothing asked
  for it; the A–B range and the audio track the viewer is listening to are the whole clip.)*
- Posting to Instagram from the app. Instagram's API only posts for business accounts and needs app review; a
  download is what a phone can share from.
- Sharing marks between profiles.

## Anti-goals

- An export must not stall someone else's playback on the same machine (funstation serves the TV and the web).
- A clip that looks washed out (HDR shown as SDR without conversion) is a failed export, even though it plays.

## What the code does today

- **The A–B loop.** The web player already marks a stretch with keys `a` and `b` and repeats it
  (`web/public/lib/playback/ab-loop.js`, wired in `transport.js` `case "loop"`, keys in `player-keys.js:97-100`).
  Keys only; there are no buttons, so a phone cannot set one.
- **Conversion.** `web/src/transcode/` already turns a set into HLS with ffmpeg: input is the player's own
  Range-served URL for the set, `-ss` before `-i`, `-map 0:v:0 -map 0:a:N`, AAC 160k stereo downmix, VAAPI or
  libx264 from `encoders.ts`, progress parsed in `progress.ts`. No HDR tone mapping anywhere today; `catalog.ts`
  carries `hdr` per set (SDR/HDR10/HLG/DV).
- **Sync.** Each device writes its own JSON document to the Telegram state channel (`web/src/state/sync.ts`); the
  record shape is `sync-record.ts` (`ProfileState.watchlist?: ListRow[]` is the model to copy) and its Rust twin is
  `crates/mediagram-core/src/state/record.rs`. Optional keys were added before without a format bump (`kids`,
  `editorsChoice`, `preferences`). Android writes through the core API (`api/state.rs`, e.g. `set_watchlisted`).
- **ffmpeg filters.** `zscale` and `tonemap` are present on this machine and on funstation (checked 2026-11-13);
  `libplacebo` too.

## Approaches

**Export:**

1. **Server-side re-encode with the existing ffmpeg setup (chosen).** Frame-exact cuts, any source codec becomes
   H.264/AAC, HDR can be converted, works from any browser including a phone.
2. Stream copy (`-c copy`): instant, but cuts snap to keyframes (seconds off) and HEVC/AV1/10-bit/DTS/MKV sources
   come out in a form Instagram rejects. Ruled out.
3. In the browser (MediaRecorder or ffmpeg.wasm): real time at best, WebM output, very slow on a phone. Ruled out.

**Getting Android marks to the web:**

1. **A new per-profile list in the existing sync record (chosen).** Same path as My List; works whether or not the
   web player is running.
2. Android posting marks to the web player's HTTP API: only works while that player is up and reachable, and Android
   does not know its address. Ruled out.
3. Marks stay on the device: TV marks could never be exported. Ruled out.

## The design

### The mark

- A mark is `{ id, setId, at, updatedAt, removed? }` per profile: `at` is the film time in seconds (millisecond
  precision), `id` a random string made by the device that created it, so one title can hold several marks and two
  devices never collide. *(my call)*
- On the wire: a new optional `scenes?: SceneRow[]` on `ProfileState`, last-writer-wins on `updatedAt` per `id`,
  removal is a tombstone like My List. Added to both the TS record and the Rust record, with the same exchange tests
  on both sides. No format bump: an older build drops the key, as it did for `preferences`.
- Marks belong to the profile, like My List, and kids profiles can mark too. *(my call)*

### Marking

- **Android (phone, tablet, TV box):** a "Mark scene" control in the player's controls. Pressing it records the
  current position and shows a brief "Scene marked at 1:02:15". Nothing else on Android: no list of marks, no
  export. *(Deliberate difference, the user's decision; written into the parity notes when built.)*
- **Not on the phone's cast-remote screen** in this first version. While casting, the TV plays as the caster's
  profile, so "Mark scene" on the TV's own controls already lands in the right list; a remote button would need a new
  cast message on both ends. *(my call; easy to add later.)*
- **Web player:** the same "Mark scene" control, so marks made at the desk land in the same list.

### Finding marks on the web

- A **"Marked scenes"** shelf on Home, shown only when the profile has marks, newest first. Each card: the title's
  still or backdrop, the title (and episode), the time. *(my call: marks made on the TV would otherwise only be
  findable by remembering which title they were in.)*
- Opening a card plays the title from the mark with the clip panel open and the range preset to **15 s before to
  15 s after the mark**. *(my call: a mark made on the couch usually comes just after the moment.)*
- A card can be removed. Exporting does not remove a mark. *(my call)*

### The clip panel (web player)

- A **Clip** control in the player bar opens a small panel. The range *is* the A–B loop: setting either end here
  sets the loop, and `a`/`b` keep working and show here.
- Start and End each show their time with **Set to now** and **−1 s / +1 s**, so a phone can do everything by touch.
  While both ends are set the loop plays, which is the preview.
- Length is shown; ranges over **3 minutes** cannot be exported (Instagram's Reel limit). *(my call)*
- **Export MP4** starts the job; the panel shows progress, then the browser downloads the file. Closing the panel
  does not cancel a running export; leaving the player does not either.
- **Getting a clip back later.** An export belongs to the profile that started it. If the tab was backgrounded (a
  phone's screen locked), the panel picks the job up again when the tab returns and downloads it then. If the tab is
  gone, the clip panel for that title lists its finished clips with a **Download** link until they expire (one hour).
  *(my call: no separate "recent clips" page; the title is where the viewer will look.)*

### Clip playback is not watching

- While the clip panel is open, and when playing from a "Marked scenes" card, the player does **not** write a resume
  position, viewing stats or achievements. Opening a mark in a finished film must not put it back in Continue, and a
  preview loop left running must not pile up minutes. *(my call)*
- A kid's daily limit still applies: it is a household rule, and the clip panel must not be a way around it.
  The web player counts a kid's minutes from its position writes (`web/src/state/stats-recorder.ts`), so on a kids
  profile clip playback keeps writing positions like any other play. *(my call)*

```
 ┌ Clip ───────────────────────────────────────┐
 │ Start  1:02:00  [Set to now] [−1s] [+1s]    │
 │ End    1:02:30  [Set to now] [−1s] [+1s]    │
 │ 30 s · plays as a loop                      │
 │                     [ Export MP4 ]          │
 └─────────────────────────────────────────────┘
```

### The export (web server)

- A new route takes `{ setId, from, to, audio }` (audio = the track the viewer is listening to) and runs ffmpeg on
  the same Range-served input the conversions use: `-ss` before `-i` for the seek, `-t` for the length, re-encoded,
  so the cut is frame exact.
- Output: MP4, H.264 (**libx264, CPU**, quality-based rate control), AAC stereo 160k, `+faststart`, scaled down to fit
  1920×1080 (never up), the source's frame rate. *(my call: libx264 rather than VAAPI keeps one filter chain for SDR
  and HDR and gives better quality per bit; clips are short.)*
- HDR10 and HLG are tone mapped to SDR BT.709 (`zscale` + `tonemap`). Dolby Vision without an HDR10 base goes through
  `libplacebo`; if that fails, the export says the title can't be clipped yet rather than producing wrong colours.
  *(my call; the builder picks the exact filter chain and checks it against a real HDR title.)*
- Runs at low CPU priority (`nice`), and only **one export at a time** per server; a second asks to wait.
  *(my call, from the anti-goal.)*
- The finished file is kept in a temp folder for an hour, tagged with the profile that made it, and downloaded with a
  filename like `Title – 1h02m00s.mp4`; then deleted. *(my call)*
- Reads go through the same paced input the conversions use, so an export does not burst the web player's Telegram
  session into a flood wait that stalls other viewers. *(my call; measure on a 4K title.)*
- Who may export: anyone who can play the title in the web player. *(my call)*

### Surface parity

The web player marks, lists and exports; Android marks only. This is the user's decision and is to be written where
the Android player's parity notes live.

## Left to the builder

- Exact ffmpeg arguments, quality settings and the tone-mapping chain (verify on one SDR, one HDR10, and one DV title
  from the library).
- The route shape, the job/progress protocol (reuse `progress.ts`), temp file handling.
- Where exactly the controls sit in each player's existing control bar, and their keys.
- The card artwork for a mark.
- Splitting into phases (sync row on both sides first is the natural order).

## Risks

- Instagram's copyright detection may mute or block clips from films. Nothing the app can do about it.
- The web player is served over plain HTTP, so the browser's "share to app" (Web Share with files) is unavailable;
  the phone gets a download, then the user shares it from Downloads or the gallery.
- Reading the range from Telegram on a cold cache adds time before encoding starts. Three minutes of a 4K remux can
  be over a gigabyte to read.
- A mark is keyed by the set, like My List: when a title's file is replaced, its marks go the way My List rows do.
