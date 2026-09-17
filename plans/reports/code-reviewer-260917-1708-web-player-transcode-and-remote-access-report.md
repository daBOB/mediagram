# Code review — web player: HLS transcode + remote access

Commits `2f94233` (transcode to HLS) and `1e9208e` (LAN vs internet), diff `6dc104e..HEAD`.
Review is report-only; no code was modified.

**Baseline**: `bun test` 222 pass / 0 fail (18 files), `bunx tsc --noEmit -p .` clean, working tree
clean, no untracked probe files. Suite was green before and after.

Every finding below was reproduced with a probe run against the real modules (probes live in the
session scratchpad, not in the repo). Measured output is quoted per finding.

---

## Critical

### C1 — Two concurrent `begin()`s for the same title spawn two ffmpegs, one untracked forever

`web/src/transcode/registry.ts:57-72`. `sessionFor` reads the map at `:59`, then **awaits**
`mkdir` at `:66`, and only writes the map at `:70`. Two requests interleave in that gap.

Measured:

```
started: [ "97deee735f132d2e", "97deee735f132d2e" ]   same id: true   tracked count: 1
stopped: [ "97deee735f132d2e" ]                        leaked: 1
```

Both processes get the **same directory** (`:65`, deterministic id at `:122-128`), so both write
`index.m3u8` and `index0.ts…` on top of each other — the playlist a viewer is given is rewritten
under them. The untracked one is in no map: `stop`, `reapIdle` and `stopAll` (`:93,:102,:110`)
cannot see it, so it survives SIGINT (`web/src/index.ts:101`) and holds the VAAPI encoder until
the machine is rebooted or it finishes encoding the whole film.

Trigger without an attacker: two viewers pressing play on the same title in the same tick, or one
viewer whose `fetch` is retried. The existing test (`web/test/transcode-registry.test.ts:46-55`)
awaits the two calls in sequence, which is exactly the case that works.

Fix: put the in-flight `Promise<Tracked>` in the map before the first `await`, and have both
callers await it.

### C2 — `stop()` racing a restart deletes the new session's directory

`web/src/transcode/registry.ts:93-99`: the map entry is removed at `:96`, then up to 3 s of ffmpeg
shutdown is awaited (`web/src/transcode/ffmpeg.ts:68-70`), and only then `rm -rf` runs at `:98`.
A `sessionFor` in that window computes the same id and the **same directory** and starts a new
ffmpeg into it; the old `rm` then wipes the new session's output.

Measured:

```
same directory: true
new session's directory after A's stop finished: gone: ENOENT
still tracked: true
```

Failure as seen by a viewer: press play, close, press play again within 3 s → the second attempt
hangs for the full 45 s readiness timeout (`web/src/transcode/server.ts:32,72-88`) and returns 503
"the conversion produced no segment within 45s". The ffmpeg.log that would explain it lives in the
same directory and is deleted with it.

Fix: derive the directory per attempt (or await `stop` fully before reusing the id), and delete the
map entry only after the teardown completes.

### C3 — Any client can end any session; the session id is computed from public data

`web/src/routes.ts:186-193` accepts `DELETE /hls/{16 hex}` from anyone and calls `hls.end` with no
ownership check. The id is not a secret: `sha256(setId ‖ 0x00 ‖ String(seek))[0..16]`
(`registry.ts:122-128`), `setId` is published by `/api/sets`, and `seek` is a small integer.

Measured (router + real hashing):

```
a remote client computed 97deee735f132d2e from the public set id and got 204;
server ended session "97deee735f132d2e"
```

Two distinct failures:

* **Benign, will happen on its own.** Sharing one session per `(setId, seek)` is deliberate
  (`registry.ts:51-56`), and everyone starts at seek 0. The first viewer to close the dialog fires
  `DELETE` (`web/public/lib/player.js:170-181` → `web/public/lib/hls-playback.js:60-66`), which
  kills ffmpeg and removes the directory for the other viewer. The survivor then gets 503 on every
  segment (`routes.ts:333`) and `hls-playback.js:93-95` installs no `Hls.Events.ERROR` handler, so
  playback dies with no recovery.
* **Hostile.** Anyone who can reach the port can stop other viewers' transcodes in a loop. (Not a
  browser CSRF vector: `DELETE` needs a preflight and this server answers 405 with no CORS headers.)

Fix: reference-count readers per session and only stop at zero, or hand each viewer an
unguessable token and key `DELETE` on that.

### C4 — The cached byte path truncates and re-orders a body that still claims a full Content-Length

`web/src/cache/reader.ts:113-119` and `:187-197`. `fillRun` never checks that `fetch` returned
`end - offset` bytes; `readStream` then `break`s out of the yield loop when a chunk is missing and
carries on with the next run. The uncached path has exactly the guard that is missing here
(`web/src/telegram/source.ts:105-109` throws when bytes are still owed).

Measured, single-range read with an upstream that returns short:

```
asked for 2097152 bytes, yielded 1048576, no error thrown
```

Worse above the 4 MiB run cap (`reader.ts:30`), because the loop resumes at the next run:

```
Content-Length would say 8388608; body is 6291456
pieces: 1x524288 1x524288 1x524288 1x524288 2x524288 2x524288 ...
```

That is not a truncation but a **hole**: run 2's bytes are served where run 1's belong, so every
byte after the gap is at the wrong offset — a corrupt video rather than a short one, with 200/206
and a Content-Length that says otherwise.

Reachable whenever `parts.byte_length` exceeds the real document (the uploader's rescan path
records caption-supplied lengths) or an upstream download ends early. Fix: in `fillRun`, throw if
`bytes.length !== end - offset`; in `readStream`, throw rather than `break`.

---

## High

### H1 — `write()` never settles when a viewer disappears mid-write

`web/src/server.ts:52-60` resolves on `drain` only. If the socket is destroyed while the buffer is
full, `drain` never fires and the callback is never invoked with an error.

Measured:

```
write() outcome 2.5s after the client vanished: no
150 aborted mid-write requests → handlers still suspended: 150, completed: 0
rss 26.5 MB -> 42.0 MB   (after three forced Bun.gc(true))
```

`pump` stays suspended for the life of the process: `response.end()` is never reached and the
`finally` at `:149-151` never removes the `close` listener. ~100 KB retained per aborted request in
the probe; the unbounded part is the count of suspended handlers, and a video player aborts a
response on every seek and every tab close. Fix: settle on `close`/`error` as well as `drain`.

### H2 — `X-Forwarded-For`: the leftmost entry is trusted, and that entry is the client's own

`web/src/client-reach.ts:60-63` takes `forwarded.split(",")[0]`. Under any proxy that *appends*
rather than replaces, the leftmost element is whatever the viewer sent.

The Caddy block in `docs/running-the-player.md:78` uses `header_up X-Forwarded-For {remote_host}`,
which replaces, and is safe. But `docs/running-the-player.md:114-115` endorses cloudflared, and
Cloudflare appends its observed IP to a client-supplied chain, so a remote viewer sending
`X-Forwarded-For: 192.168.1.50` arrives as `192.168.1.50, <real ip>` → `isLocalAddress` true →
`/api/player` answers `remote:false` → the page offers the original 13.9 Mbit/s file. That is
precisely the outcome `docs/running-the-player.md:55-59` warns about, and it happens *with*
`MEDIAGRAM_TRUST_PROXY=1` set as documented.

With the flag off, the header is correctly ignored (`:60`) and the socket address wins — no bypass
there. Fix: use the rightmost entry, or the entry N hops from the right for N trusted proxies.

### H3 — `?seek=` accepts non-finite values; each distinct value is another ffmpeg and a 45 s hang

`web/src/routes.ts:250`: `Math.max(0, Math.floor(Number(seek)) || 0)` passes `Infinity` and `1e30`
through (`NaN || 0` is guarded, `Infinity || 0` is not). Measured:

```
seek=Infinity: ... -y -ss Infinity -i http://127.0.0.1:8770/api/sets/X/stream
seek=1e+30:    ... -y -ss 1e+30   -i ...
$ ffmpeg -ss Infinity ... → Invalid duration for option ss: Infinity   (exits at once)
```

`waitForFirstSegment` (`web/src/transcode/server.ts:72-88`) polls the filesystem and never watches
`proc.exited`, so the request is held for the full 45 s even though ffmpeg died in milliseconds —
and that is true of *every* ffmpeg failure, not just this one. Nothing caps `sessions.size`
(`registry.ts`), so a loop over `?seek=N` spawns an unbounded number of ffmpegs, each one also
opening a stream back through Telegram. Fix: `Number.isFinite` plus a ceiling at the set's
duration; fail `begin` as soon as the process exits; cap concurrent sessions.

---

## Medium

### M1 — Stale session directories are reused and reported ready instantly

Nothing clears `MEDIAGRAM_TRANSCODE_DIR` at startup (`web/src/index.ts:53-61`), `sessionFor`
`mkdir`s without clearing (`registry.ts:65-67`), and readiness is "the playlist names a segment"
(`transcode/server.ts:80`). After a SIGKILL (systemd stop timeout, crash, power loss), a viewer
replaying the same title at the same offset gets:

```
begin() returned /hls/97deee735f132d2e/index.m3u8 after 0ms against the stale playlist
served playlist: #EXTM3U STALE-index0.ts
```

hls.js is handed last run's playlist while the new ffmpeg truncates and rewrites those exact files
underneath it. Related: `-hls_list_size 0` with `-hls_playlist_type event` (`transcode/args.ts:96-100`)
never deletes a segment, so one full watch writes the whole re-encode to disk — about 7 GB for a
two-hour film at the default 8 Mbit/s — and there is no quota, unlike `MEDIAGRAM_CACHE_MAX`.

### M2 — A transcode paused for five minutes is reaped and cannot be resumed

`registry.ts:37` (`DEFAULT_IDLE_MS` 5 min) with `lastUsed` touched only by `file()`
(`transcode/server.ts:108`). hls.js stops fetching once its buffer is full, so a paused viewer
makes no requests; five minutes later the session is stopped and its directory removed, and every
subsequent segment is a 503 with no error handling in `hls-playback.js`. `docs/running-the-player.md:80`
states "a viewer may pause for an hour" while configuring Caddy for exactly that.

---

## Low

* **L1** `web/src/routes.ts:130` — `decodeURIComponent` sits outside the `try` at `:136`, so a
  malformed escape is a 500 with a logged stack instead of a 404. Measured: `GET /%zz` →
  `threw URIError: URI error` out of the router, turned into 500 by `server.ts:101-107`.
* **L2** `web/src/cache/reader.ts:167` — the `!` on `chunks.get(...)` throws
  `TypeError: undefined is not an object (evaluating 'chunk.subarray')` on the same short read as
  C4. Latent only: `CachedReader.read` has no non-test caller (production uses `readStream`).
* **L3** `web/src/transcode/server.ts:104-112` — `exists()` then `arrayBuffer()`; a concurrent
  `DELETE` between the two rejects with ENOENT out of the route into the 500 path.

---

## Verified clean

* **No channel/message/document id reaches the browser.** `catalog.ts:84-85` selects an explicit
  column list; `PartLocation` (`catalog.ts:108-124`) is used only to build the byte stream
  (`routes.ts:359-377`) and appears in no response. `config.describe` redacts the session and hash.
* **Path handling.** Route regexes (`routes.ts:79-89`) are spelled out, `staticFile` re-checks the
  normalized prefix (`:131-132`), `TranscodeFiles.file` re-checks with `resolve` (`server.ts:102`),
  and `chunkPath` refuses a non-alphanumeric set id before it reaches the filesystem
  (`cache/key.ts:59-61`). No crafted path reached a file outside its directory in any probe.
* **No shell injection**: `Bun.spawn` takes an argv array (`ffmpeg.ts:41`), set ids are validated by
  the route and confirmed against the DB before `begin` (`routes.ts:249`).
* **`MEDIAGRAM_TRUST_PROXY=0` cannot be bypassed** — the header is not read at all (`client-reach.ts:60`).
* Range arithmetic (`range.ts`, `response.ts`) is pure, clamps per RFC 9110, and is well covered;
  no off-by-one found in `planReads`/`chunksCovering`/`parseRange`.

## Recommended order

1. C1 and C2 together (one change: memoise the in-flight start, tear down before reuse).
2. C4 (a length check in `fillRun` and a throw in `readStream`).
3. C3 (refcount, or per-viewer token on `DELETE`).
4. H1, H3, H2.
5. M1, M2, then the Lows.

## Unresolved questions

* C3: should a second viewer joining an existing session be supported at all, or should each
  viewer get its own session (simpler lifetime, one more encoder)?
* M2: is the five-minute idle limit meant to survive a long pause? If so the reaper needs a
  "paused but attached" signal from the page, or the page needs to restart the transcode on a 503.
* H2: is cloudflared still a supported deployment? If so the docs need an explicit note that the
  rightmost hop is the trustworthy one.
