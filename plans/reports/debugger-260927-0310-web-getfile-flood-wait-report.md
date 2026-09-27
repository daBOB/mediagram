# Web player: recurring `upload.GetFile` flood waits

Investigation only, no code changed, no Telegram contact, dev server (pid 3504066) untouched.

## Symptom recap
`[INFO] Sleeping for Ns on flood wait (Caused by upload.GetFile)` every ~5-6s, N=1-2, continuous over the ~3h41m dev-server run. Line format traced to `web/src/telegram/measured-client.ts:15,25-34` (`FLOOD_LOG` regex + `countingLogger`, reproducing teleproto's own `Logger` print verbatim — confirms this is teleproto's built-in flood-wait auto-retry, not app code raising/catching anything).

## Q1 — every GetFile path in web/src

All bottom out in `iterDownload` at two call sites, both gated by one process-wide `DownloadGate`:

- `web/src/telegram/source.ts:143` — foreground stream, only used when `reader` (cache) is absent (not this deployment's normal path — a `CachedReader` is always constructed when a cache dir is configured, see `web/src/index.ts:244`).
- `web/src/telegram/part-fetch.ts:96` (`fetchPartOnce`) — the one real fetch path once a `CachedReader` exists. Reached via:
  - `partFetcher`/`connectionFetcher` (`part-fetch.ts:31-45`), each wrapping the call in `downloads.run(...)` (`part-fetch.ts:25`, the module-level `const downloads = new DownloadGate()` — one instance for the whole process, `MAX_DOWNLOADS = 4`, `web/src/telegram/download-gate.ts:17`).
  - Callers of that fetcher, all sharing the same gate instance:
    - Browser range request → `CachedReader.readStream` on a miss (`web/src/cache/reader.ts:130-165`, foreground) and its trailing `warm()` readahead (`reader.ts:165,253-274`, background, same request).
    - ffprobe audio-track probe → reads through `/api/sets/:id/stream` (`web/src/catalog/audio-tracks.ts:1-15,39` doc + `PROBE_LIMIT` 8 MB), i.e. the same cache/gate path.
    - ffmpeg HLS transcode → `web/src/transcode/ffmpeg.ts:57`, `input: ${baseUrl}/api/sets/:id/stream` — also the same route, same gate, whenever the playing title needs conversion.
    - `SeriesPreload` background full-episode caching → `web/src/cache/series-preload.ts`, wired in `web/src/index.ts:316-328` with `fetcherFor: (messageId) => connectionFetcher(connection, messageId)` — **the same shared gate**.
    - `/api/preload` 8 MB next-title byte preload (`web/public/lib/playback/player-next-title.js:9,85-91`) — also through `/stream`, same gate. Fires once per episode transition, ahead≥30s only; minor next to `SeriesPreload`.
  - Thumbnail sheets and the LAN `mediagram-cache` (`docs/system-architecture.md` §12) are **not** GetFile sources here: thumbs read `/api/sets/:id/cached-stream`, disk-only, no upstream fetch (confirmed no `iterDownload`/`fetcherFor` in `web/src/thumbs/`); `mediagram-cache` is a separate, unauthenticated-read LAN binary for Android devices, not part of this web player process.
  - `channel-index/find-newest-channel-index.ts:77` uses `iterDownload` too, but only for a rare, one-shot index-document fetch (channel refresh), not per-playback traffic — ruled out as a steady contributor.

Request size: `requestSizeFor` (`web/src/range.ts:76-78`) caps at 512 KiB (`REQUEST_SIZES`, `range.ts:26`), matching `CACHE_CHUNK = 512 * 1024` (`web/src/cache/key.ts:18`). Any read bigger than one chunk becomes several sequential 512 KiB `invoke()` calls inside one `iterDownload`, back-to-back, no artificial delay beyond RTT (doc comment in `cache/strategy.ts:6` says 150-450 ms/round trip).

Concurrency in ONE playback: foreground read (1 gate slot) + its readahead `warm()` (1 slot, `reader.ts:253`, capped `maxAhead=4` chunks = 2 MiB by default, `config.ts:198`) + audio-probe (1 slot, once) + transcode-read (1 slot, if converting) can all be in flight together — this is exactly what `download-gate.ts:1-14`'s own doc comment names as the original problem.

## Q2 — steady-state rate

Single playback: `strategy.ts:6-8` states the design assumption directly — "a 4 Mbit/s stream wants a 512 KiB chunk about every second." With cache warm and readahead working, that settles near 1 miss/s once the run is established, well inside 4 slots.

**But** `SeriesPreload` (`series-preload.ts`) is a different shape of load: it downloads an *entire* next episode (up to `MAX_PRELOAD = 2`, `web/src/cache/preload-route.ts:9`) through `CachedReader.fill` → `fillMissing` → `fillRun` (`cache/reader.ts:176-237`), each run capped at `MAX_RUN_BYTES = 8 * CACHE_CHUNK` = 4 MiB (`reader.ts:30`) but with **no pacing between runs** — `fillMissing` awaits one `fillRun` after another in a tight loop until the whole part, then the whole episode, is on disk. It holds exactly one `DownloadGate` slot (by design, `series-preload.ts:9-11`), but holds it *continuously* for as long as the transfer takes — potentially minutes for a multi-GB episode — running flat-out at whatever rate RTT allows (~2-6 req/s per the 150-450 ms figure above), not paced to playback speed the way the foreground read is.

`web/public/app.js:337-353` (`preloadAfter`) fires `/api/preload` for the next two episodes on **every** episode open (`kind === "ep"`), and `config.ts:219` shows `seriesPreload` defaults **on** (`MEDIAGRAM_SERIES_PRELOAD` must be explicitly set to disable). So any show/tutorial episode played for 3h41m plausibly has this background bulk fetch running for a meaningful fraction of that window, continuously competing for the same 4-slot gate as the live stream, its own readahead, and any transcode/probe reads — a sustained multi-slot, multi-request-per-second load, not the brief startup burst the gate was originally sized for.

## Q3 — GramJS/teleproto flood handling

`web/src/telegram/client.ts:74-94` (`Telegram.open`, the **playback** client) constructs `MeasuredClient` with **no `floodSleepThreshold` override** — unlike `web/src/login/setup.ts:29` and `web/src/settings/sign-in.ts:56`, which explicitly set `floodSleepThreshold: 0` on their own separate, empty-session clients used only for login/sign-in (confirmed distinct auth keys, `docs/web-player.md` "Sign-in runs on its own client"). teleproto/GramJS's documented default is 60s ([gram.js.org](https://gram.js.org/beta/classes/TelegramClient.html)): any `FLOOD_WAIT` at or under that is slept through and the *same* request retried automatically inside `super.invoke()` — never thrown, never surfaced to a caller. That is exactly the observed 1s/2s log lines: they are teleproto sleeping-and-retrying transparently, not an app-level error.

Does it stall the byte stream? The slot doing the sleeping is unusable for that 1-2s — `DownloadGate.run` (`download-gate.ts:26-41`) holds the slot until `task()` resolves, and `task()` here is the whole `invoke()` including its internal sleep. Whichever request is queued behind it in the FIFO (`waiting`, `download-gate.ts:21`) waits that much longer. If that queued request is the *foreground* read the `<video>`/HLS reader is blocked on, the viewer would see a stall of that length; if it's the readahead or the background preload, nothing visible happens. No direct evidence either way was collected (would need live `/api/status` or playback telemetry, both out of scope for a read-only, no-Telegram-contact pass) — flagged as an open question below.

## Q4 — prior flood-wait work (git log)

`git log -S FLOOD -- web` and `-S DownloadGate` both surface the same pair, same timestamp, same author, 2026-09-23:

- `b69b1930` *fix(web): stop part downloads from tripping Telegram's flood limit* — added `DownloadGate` (`MAX_DOWNLOADS=4`) specifically because "the browser's range requests, the audio-track probe and readahead together" each became their own `upload.getFile` stream and "a dozen at once drew FLOOD_WAIT of 1-2s that stalled every reader." Its own doc comment (`download-gate.ts:6-14`) already predicts and accepts **occasional short flood waits** as the residual cost, explicitly calling them cheaper than the unbounded burst they replaced.
- `df88713c` *feat(web): preload the next two episodes while one plays* — landed in the **same commit batch** as the fix above, and is the thing that reintroduces sustained (not bursty) load on that same gate: a continuous, unpaced, whole-episode background download sharing the fix's own gate.

So this is a known, already-mitigated class of issue whose mitigation (the gate) was sized for a one-time startup burst, then — in the same breath — handed a new, continuous consumer (series preload) that the gate caps in *count* but not in *rate*.

## Q5 — root cause and recommendation

**Root cause:** `SeriesPreload`'s whole-episode background download (on by default, triggered on every episode open) runs unpaced inside the shared `DownloadGate`, continuously occupying one of its four slots for as long as the transfer takes and issuing back-to-back `upload.getFile` calls at whatever rate round-trip latency allows. This, layered on the live episode's own foreground+readahead traffic (and, when the title needs conversion, ffmpeg/ffprobe reads through the same route), produces sustained multi-request-per-second load against Telegram's `upload.getFile` limit for the whole time a next episode is being pre-cached — not just at video start. `DownloadGate` (the 2026-09-23 fix) caps *how many* streams run at once; it does not pace *how fast* any one stream, including the preload's, issues requests, so a single continuously-busy slot can by itself exceed Telegram's rate and trip `FLOOD_WAIT` repeatedly over an extended window, matching the observed cadence far better than a one-time startup spike would.

Effect: **benign for correctness** — teleproto's default `floodSleepThreshold` (60s, unset for the playback client) sleeps-and-retries transparently inside `invoke()`; nothing throws, nothing truncates, no corrupted playback. **Possibly not benign for smoothness** — each sleep holds a `DownloadGate` slot for its duration, so a queued foreground read can stall by that many seconds; unconfirmed whether it actually reached a viewer given no playback telemetry was read in this pass (see below).

Fix options, ordered simplest first (not implemented — this is a read-only investigation):
1. **Cheapest**: rate-limit `SeriesPreload` itself (e.g. a short `await` between `fillRun` calls in `fillMissing`, or shrink its `MAX_RUN_BYTES`) so the background bulk transfer paces itself well under whatever req/s trips Telegram — trades preload completion time for fewer floods. Smallest diff, touches one file.
2. Turn `DownloadGate` from a pure concurrency cap into a request-rate limiter (token bucket, N req/s) in addition to the slot count — fixes the general case (any future continuous consumer), larger diff, more machinery for what may be a one-consumer problem today (YAGNI concern).
3. Do nothing: since the waits are already auto-handled and short, and the existing gate/preload design already documents this as an accepted trade-off (`series-preload.ts:9-11`, `download-gate.ts:6-14`), this may be working as designed — confirm via option below before spending a diff on it.

**Recommended first step, not done here (would touch the live server/Telegram):** compare flood-wait frequency between a film (no `SeriesPreload` — `kind !== "ep"`, `preload-route.ts:20`) and a show episode of similar bitrate; if the film session is quiet, `SeriesPreload` is confirmed as the driver rather than the Rust uploader.

## Rust uploader (SaveBigFilePart) — considered, not confirmed

Different RPC (`upload.saveBigFilePart` vs `upload.getFile`), different top-level operation; Telegram's per-method-group flood buckets are typically independent, and the player uses a separate auth key/session from the uploader (`docs/web-player.md` "sign-in runs on its own client... a separate auth key from the live one"). No evidence in this codebase ties the two together, and this claim cannot be verified without either querying the live Telegram-facing `/api/status` link-stats panel or pausing one side — both out of scope for a read-only, no-Telegram-contact pass. Weaker candidate than `SeriesPreload`, kept as an open question rather than ruled in or out.

## Unresolved questions
1. Was the title playing during the report window a show/tutorial episode (triggers `SeriesPreload`) or a film (does not)? Determines whether the root cause above actually applies to this specific session.
2. Did the viewer see actual playback stutter, or only the log noise? Not observed in this pass; `/api/status` "Watching now" (dropped frames, buffer health, `docs/web-player.md` line ~339) or the per-DC flood-wait counts in the same status panel would answer it without touching Telegram — reading that HTTP endpoint on the already-running server was avoided here per the "don't touch the running dev server" constraint, but would resolve this quickly if permitted.
3. Does Telegram's `upload.getFile` flood bucket for this account also cover the concurrently-running Rust uploader's DC/session, or is it fully independent? Unconfirmed (see above).

**Status:** DONE
**Summary:** Root cause is `SeriesPreload`'s unpaced, continuous whole-next-episode background download (on by default, `web/src/cache/series-preload.ts` + `web/src/index.ts:316-328`) sharing the same 4-slot `DownloadGate` (`web/src/telegram/download-gate.ts`) as live playback, readahead, ffprobe and transcode reads — the 2026-09-23 fix (`b69b1930`) capped concurrent streams but not request rate, and the preload feature landed in the same commit batch as a new continuous consumer of that same gate. The flood waits themselves are auto-handled by teleproto's default 60s `floodSleepThreshold` (unset on the playback client, `web/src/telegram/client.ts:74-94`) — no stream errors, no corruption — but each sleep holds a gate slot and can delay whichever read is queued behind it. Uploader-interference hypothesis considered but unconfirmed; two open questions need either the running server's own status panel or a controlled film-vs-episode comparison to close.
