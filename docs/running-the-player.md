# Running the player

The player is a Bun process that holds the Telegram session and serves the
library over HTTP. It has **no authentication of its own**. Everything below
exists because of that one sentence.

What the process holds, in order of how much it would hurt to lose:

1. The account's MTProto auth key (`MEDIAGRAM_SESSION`). Whoever has it is the
   Telegram account — every chat, not just this channel.
2. The whole library, readable and streamable.
3. The channel id and message ids, which the HTTP API never discloses but the
   process knows.

So the rule is: the player binds to loopback, and the only thing that talks to
it from outside is a proxy that has already authenticated the caller.

## Layout

```
internet ──TLS──> Caddy (auth) ──> Bun on 127.0.0.1:8770 ──> Telegram
                                     └── ffmpeg, also on 127.0.0.1
```

## Getting a session onto the player host

The player speaks MTProto itself, so it needs an auth key for an account with
access to the channel. Issue one once per host, either by logging in there or
by exporting the uploader's.

```
cd web
bun install
bun run login             # asks for a phone number and the code Telegram sends
```

It writes `web/.env` itself, mode 600 — not to stdout for you to redirect,
because a redirect also captures whatever the MTProto library decides to
print, and a session file is a bad place to discover log noise. The file
carries the API credentials, the session string, and the channel's id and
access hash. Run it once per player host.

On a host you do not control, use a **second Telegram account** invited to the
channel rather than your own. The key is the account, not the library: whoever
has it can read every chat that account can, and revoking it means terminating
a session rather than changing a password. A dedicated account limits that to
the one channel you invited it to.

A player always logs in for itself. One auth key cannot serve two clients at
once — a second connection using it breaks both until the process restarts —
so a host that runs the uploader and a player needs two keys, not one shared.

## Starting it

```
cd web
bun run start             # loopback only; this is what you run behind a proxy
bun run dev               # every interface, for a phone or a TV on the LAN
```

It prints what it decided, and reading that line is most of the diagnosis when
something is wrong later:

```
catalog: 16 playable sets, 2 poster(s)
cache: 0.45 GB of 8.00 GB in /home/andre/.cache/mediagram-player, readahead 4 chunk(s)
encoder: h264_vaapi on /dev/dri/renderD128
serving on http://127.0.0.1:8770
```

With a package configured, one more line above it says where the catalog came
from (`catalog: updated from …`, or `unchanged`). A refusal says why instead,
on two lines.

The poster count is artwork found in `posters/` beside whichever index was
opened: inside the catalog a package unpacked, or next to `library.db` on this
machine. A package carries its own; a local index is filled by running
[`mediagram posters`](../README.md#commands) on the uploader. Zero is not an
error — cards fall back to the title's initials, which is also what a course
shows, having no provider id to key a poster by.

`bun run dev` also prints a warning saying, in as many words, that anyone who
can reach the port can stream the whole library. That is fine on a network you
trust and is not how to put it on the internet — for that, see
[Caddy](#caddy).

## Configuration

Everything is environment variables. `web/.env` is read at startup and is in
`.gitignore`; keep it at mode 600.

| Variable | Default | What it does |
|---|---|---|
| `MEDIAGRAM_API_ID`, `MEDIAGRAM_API_HASH` | — | Telegram application credentials |
| `MEDIAGRAM_SESSION` | — | The auth key. Treat as a password for the account |
| `MEDIAGRAM_CHAT_ID`, `MEDIAGRAM_CHANNEL_ACCESS_HASH` | — | The library channel |
| `MEDIAGRAM_LIBRARY_DB` | — | The index on this machine, opened read-only. Required unless both package settings below are given |
| `MEDIAGRAM_PACKAGE_URL` | — | Base URL of a published package, the directory holding `latest.json` |
| `MEDIAGRAM_PACKAGE_KEY` | — | 32 bytes, base64. The only thing protecting the package |
| `MEDIAGRAM_CATALOG_DIR` | `~/.cache/mediagram-catalog` | Where decrypted catalogs are kept |
| `MEDIAGRAM_CHANNEL_INDEX_DIR` | `~/.cache/mediagram-channel-index` | Where the channel's index snapshots are installed |
| `MEDIAGRAM_POSTERS_COMMAND` | `mediagram` | The CLI run as `<command> posters --index <snapshot>` to fetch covers for a new snapshot |
| `MEDIAGRAM_PLAYER_ADDR` | `127.0.0.1:8770` | Where to listen. **Leave it on loopback in production** |
| `MEDIAGRAM_TRUST_PROXY` | `0` | Believe `X-Forwarded-For`. Set to `1` **only** behind a proxy |
| `MEDIAGRAM_CACHE_DIR`, `MEDIAGRAM_CACHE_MAX` | `~/.cache/mediagram-player`, `8G` | Chunk cache and its quota |
| `MEDIAGRAM_CACHE_READAHEAD` | `4` | Chunks fetched ahead of a sequential read |
| `MEDIAGRAM_SERIES_PRELOAD` | `1` | Opening an episode takes the next two into the cache in full, one download at a time. `0` turns it off |
| `MEDIAGRAM_TRANSCODE_DIR` | `~/.cache/mediagram-hls` | Where HLS segments are written. Cleared at startup |
| `MEDIAGRAM_TRANSCODE_MAXRATE` | `8000000` | The uplink budget, in bits per second |

`MEDIAGRAM_TRANSCODE_MAXRATE` does two jobs: it caps what ffmpeg produces, and
it decides which titles a remote viewer may play as they are. Set it to what
the uplink really carries with room for the rest of the house. A 25 Mbit/s
upstream comfortably serves one viewer at 8 Mbit/s.

### Disk

Three directories hold state, and only one of them has a quota.

`MEDIAGRAM_CACHE_DIR` is bounded by `MEDIAGRAM_CACHE_MAX` and evicts by last
use. `MEDIAGRAM_TRANSCODE_DIR` is not: a conversion keeps every segment it has
written so the viewer can seek back through them, which at the default cap is
about 1 MB per second of film — some 7 GB for a feature watched to the end. A session's
directory goes when the session stops, the whole directory is cleared at
startup, and at most four conversions run at once, so the ceiling is roughly
four films' worth. Put it somewhere that can take ~30 GB, or lower the cap.

**Sizing the chunk cache.** It holds 512 KiB chunks of the parts the player
has read, evicting the least recently used when the quota is reached, so what
it buys is not having to fetch the same bytes from Telegram twice: a second
viewing, a seek backwards, a conversion restarted at another offset. Anything
smaller than a film evicts the beginning of that film before its end arrives
and buys nothing, so size it in films rather than in percentages — 8 GB, the
default, holds roughly one; 50 GB holds an evening's worth. Set
`MEDIAGRAM_CACHE_MAX=0` to turn it off, which is a reasonable choice on a
machine with no disk to spare: every read then goes to Telegram, which is
correct, only slower.

`MEDIAGRAM_CATALOG_DIR` holds one decrypted package — tens of megabytes, most
of it artwork — and older versions are deleted as each new one is swapped in.
It exists only when a package is configured.

### What plays directly, and what is converted

A browser is handed the original file whenever it can decode it. When it
cannot, the server converts as the viewer watches — the same bytes, through
ffmpeg, out as HLS.

| What | Direct play | Converted |
|---|---|---|
| Container | `mp4`, `m4v`, `webm` | everything else; Matroska in particular |
| Video | `h264`, `avc`, `avc1`, `vp8`, `vp9`, `av1` | everything else; HEVC unless the browser says it decodes it |
| Audio | `aac`, `mp4a`, `opus`, `vorbis`, `mp3` | everything else; AC-3 and E-AC-3 in particular |

The lists live in `web/public/lib/playable.js` and a test fails if this table
stops matching them.

### What the player remembers

Watch positions, the watchlist and collections live in the player's own
database, at `MEDIAGRAM_STATE_DB`, defaulting to
`~/.local/share/mediagram-player/state.db`. Note the directory: every other
path this process uses is a cache holding something it can fetch again, and
this one holds the only thing it cannot. Back it up or do not, but do not put
it under `~/.cache`.

It is not the index. That belongs to the uploader, is opened read-only, and is
replaced whole when a package refresh lands — a position written there would
be destroyed by the next catalog update.

A player whose state directory cannot be written says so at startup and runs
without a memory. Films still play; positions are not kept.

**Resume across devices needs nothing.** The player is a server. A phone, a
laptop and a television pointed at it are reading and writing the same rows,
so a film put down on one is where you left it on the next. There is nothing
to sync, and syncing the database file between hosts would be worse than
nothing: it is WAL-mode, so the three files are consistent only as a set, and
two hosts writing means one silently overwriting the other.

### Profiles

Profiles keep two people's positions and lists apart. The page asks who is
watching and remembers the answer in that browser, so a television stays on
the television's profile.

**A profile is not a login.** This API has no authentication — the sentence
this whole document is built around — and a profile does not add any. Anyone
who can reach the port can pick any profile, exactly as they can already
stream the whole library. It is a convenience for a household, not a boundary.

The API takes writes now. They require a JSON content type and, where the
browser sends one, a same-origin `Origin`. Neither is authentication; together
they stop a page on another origin from submitting a form at your player. The
answer to everything else is still a proxy in front.

### Choosing an audio track

A title holding more than one audio stream gets a chooser in the player. The
list is read off the file by `ffprobe` the first time a viewer opens it, and
held for the life of the process.

It is **not** read from the index. `sets.alang` stores the *distinct* language
codes of a set with untagged streams dropped, so a file whose streams run
`[und, en, en-commentary, de]` is recorded as `["en","de"]` — a list whose
positions are not the ordinals ffmpeg selects by. Choosing "German" from that
would have played the commentary, and nothing about the result would look
wrong. The ordinal has to come from the file.

Picking a track always converts, including for a title that was playing
directly. Chrome and Firefox do not implement `HTMLMediaElement.audioTracks`,
so there is no way to tell a `<video>` to use a different stream of the file it
already has. The conversion restarts where the viewer was, and the note under
the scrub bar says why it started.

This needs `ffprobe` on the player host. It ships with ffmpeg, which the
converter already requires, so a host that can convert can also probe. Where
it is missing, a probe fails quietly and every title simply offers no choice.

Why those three are where the line falls:

- **Matroska.** No browser ships a `.mkv` demuxer. The codecs inside may be
  perfectly playable and it makes no difference; there is nothing to open the
  container with.
- **HEVC.** Patchy and licence-bound. Safari on Apple hardware plays it,
  Chrome and Edge do where there is a hardware decoder, Firefox does on a
  Linux with VA-API — and "some browsers" is not something a library can be
  built on. So it is not on the list above; it is **negotiated**. The page
  asks its own browser once (`web/public/lib/codec-support.js`), counting
  HEVC only when both `canPlayType` and `MediaSource.isTypeSupported` accept
  `hvc1`, and says so on every conversion request as `?vcodecs=hevc`. For
  that browser an HEVC Matroska is *repackaged* rather than re-encoded: the
  picture is copied, tagged `hvc1`, into fMP4 segments — the only form hls.js
  plays HEVC from — while the soundtrack is converted as before. Every other
  browser still gets H.264. Two limits: a negotiated codec is never played
  directly, even from an mp4, because the index cannot say whether the file
  is tagged `hev1` (refused by Safari and Chrome) and repackaging retags it;
  and only SDR at 1080p or below is negotiated, because the probe asks about
  8-bit Main at level 4 and says nothing about HDR, Dolby Vision or 2160p. The server accepts only codec names on
  `NEGOTIABLE` in `playable.js`, so a request cannot talk it into copying
  anything else.
- **AC-3 and E-AC-3.** Broadcast and disc audio, licensed per decoder, and
  shipped by essentially no browser. A film with AC-3 is converted for its
  soundtrack alone even when its video would have played.

The rules are deliberately conservative: anything not known to work is
converted. Being wrong that way costs a conversion nobody needed; being wrong
the other way costs a viewer staring at a player that never starts.

There is a fourth reason, and it is not about codecs at all. Direct play hands
over the original file, and a film at 13.9 Mbit/s does not fit a household
uplink — so a viewer the server judges remote gets a conversion for anything
above `MEDIAGRAM_TRANSCODE_MAXRATE`, whatever its codecs.
[`MEDIAGRAM_TRUST_PROXY`](#mediagram_trust_proxy) is how that judgement is
made.

### When the link turns out to be slower than expected

All of the above is decided before a single byte moves, from numbers in the
catalog. Reality disagrees often enough — a phone on a weak signal, Telegram
in a flood wait, someone else in the house starting a download — and the
symptom is the one nobody tolerates: play, stall, play a few seconds, stall,
with nothing changing in between to make the next attempt go better.

So the page also watches. It samples how many seconds of video are buffered
ahead of the playhead and how fast that is growing against the wall clock,
and when the link is sustainably delivering less than playback consumes it
moves to a conversion the link can actually carry — keeping the viewer's
place, and saying so in the note under the player.

Two things make that measurement harder than it sounds, and both are worth
knowing if you ever change it:

- **A full buffer looks exactly like a slow download.** A browser that has
  buffered all it wants stops fetching, so the buffer stops growing. Nothing
  is judged while more than 45 seconds are buffered.
- **A browser refilling in bursts looks exactly like a slow download.** Chrome
  playing a file directly keeps twenty-odd seconds in hand and lets it sag for
  several seconds before topping it up. So the rate is measured across a
  twenty-second window rather than between two samples, and nothing is judged
  "behind" until fewer than ten seconds are buffered — a level no browser lets
  itself reach on a link that is keeping up. Before this, one ordinary refill
  pause could read as a link delivering nothing, and the suggested bitrate is
  the source's times that rate: titles that were playing perfectly were
  converted straight to the 600 kbit/s floor.
- **A stalled player looks exactly like a healthy one**, if the rate is
  measured against playback. With an empty buffer the playhead advances
  precisely as fast as bytes arrive, so "buffered seconds gained per second
  played" is exactly 1.0 while the viewer watches a spinner. The rate is
  measured against the wall clock instead.

It only ever converts downward, waits 25 seconds between switches, and stops
when there is nothing lower left to try — at which point it says the
connection is too slow for this title rather than restarting the same encode
forever. A viewer who wants a specific quality can still pick a position with
the slider, which keeps whatever rate was last found to work.

### Browsing

The web library follows the device's light or dark appearance. On desktop,
navigation stays in a sidebar; on phones it sits above the library. Search
and the profile switcher share a separate toolbar. Keyboard users can choose
**Skip to library** to move past navigation without changing the current route.

Home keeps **Continue** and **Next up** compact, followed by the latest film,
series, and course shelves. Movies and Series retain the device's saved
List/Grid choice. A film's **Play** or **Resume** action appears before its
synopsis. The player itself stays dark in either appearance.

### Searching

`GET /api/search?q=…`, and the Search field above the library. It matches titles, show and
course names, chapter and folder paths, and the body of any summary — so a
lesson called "Interpretation" is findable by the chapter it sits in or by a
word in its notes, which is the only way a course of a hundred and seventy
terse titles is navigable at all.

Two things it does deliberately:

- **Folds case and diacritics.** `uberblick` finds "Überblick", `qualitat`
  finds "Qualität", and `ss` finds `ß`. A German library searched by someone
  without an umlaut key is the normal case, not the edge one.
- **Ranks by where the match was.** Title, then show or course, then chapter,
  then folder, then summary. Every term has to match somewhere on a set, but
  they may match different fields: `signal interpretation` finds the lesson
  called Interpretation inside the Signal chapter.

Results carry the same fields a catalog row does, so opening one is the same
player dialog with the same subtitles and notes. Summaries are per lesson —
drop a `<video-name>.summary.md` beside a video before `add-course` and it
travels. There is no course-level summary yet.

The whole index is folded once at startup, because the catalog cannot change
while the process runs. At a few hundred sets that is faster than a database
index and needs no schema; a library of thousands would want SQLite's FTS5
instead.

### Where the catalog comes from

Three ways, in this order: a published package when one is configured, else
the channel's pinned index, else the index on this machine.

**The channel's pinned index.** Without a package, the player reads the newest
index snapshot the uploader pinned to the channel — the same one the Android
app reads — at startup and again the moment a new one is pinned, with no
restart and no reload: open pages are told over `/api/events` and redraw
themselves. Snapshots are installed under `MEDIAGRAM_CHANNEL_INDEX_DIR`, one
directory per version with `current` naming the live one. An uploader on
another machine is followed as closely as one on this machine. A channel that
cannot be read leaves the last installed snapshot in service.

A snapshot carries descriptions but no artwork, so after each one — and once at
startup — the player runs `mediagram posters --index <snapshot>` itself
(`MEDIAGRAM_POSTERS_COMMAND`, default `mediagram` on the `PATH`). That fetches
the missing covers from TMDB into the folder beside `MEDIAGRAM_LIBRARY_DB`,
where the player reads them, and open pages redraw with them. It needs the
uploader's CLI and its TMDB key on this machine; without them the new titles
show initials, and the player says why in its log.

**The index on this machine.** `MEDIAGRAM_LIBRARY_DB` points at the
`library.db` that `mediagram` writes, and the player opens it read-only. It
has no default — `bun run login` writes the usual path into `.env` for you. It
is served only when the channel has never been read and nothing is installed,
such as a first start with no network.

**A published package.** Set `MEDIAGRAM_PACKAGE_URL` and
`MEDIAGRAM_PACKAGE_KEY`, and at startup the player fetches `latest.json`,
downloads the package it names, verifies it, decrypts it, and reads the
`library.db` and the artwork inside. Nothing from the uploader's filesystem is
needed, and `MEDIAGRAM_LIBRARY_DB` becomes unnecessary — set both, and the
package wins. Publish with `mediagram export-package --publish`; the format is
[`docs/mlib-package-v1.md`](mlib-package-v1.md).

```
MEDIAGRAM_PACKAGE_URL=https://packages.example.com/mediagram
MEDIAGRAM_PACKAGE_KEY=<the same package_key the uploader has, base64>
```

A refresh that fails — host down, edited pointer, wrong key — leaves the
player with the catalog it already had and says why. Only a first run with
nothing held is fatal, because there is then nothing to serve.

The key is the whole of the protection. The package carries the private
channel id and every message id, so treat it exactly as the Telegram session
is treated: mode 600, never in git, never in a log. The URL is not a secret
and must not be treated as one; a leaked URL yields ciphertext.

Catalogs are kept per version under `MEDIAGRAM_CATALOG_DIR`, with `current` a
symlink to the live one. The swap is a single rename, so a player that dies
mid-refresh is looking at one whole catalog or the other.

### `MEDIAGRAM_TRUST_PROXY`

Behind a proxy every request arrives from `127.0.0.1`, so the player cannot
tell a viewer on the sofa from one on the internet without the address the
proxy forwards. Set the flag and it reads `X-Forwarded-For`; leave it unset
and it uses the socket's address.

Set it only when a proxy is genuinely in front. On a player anyone can reach
directly, the header is whatever the caller chose to send, and a remote
viewer could set it to `192.168.0.10` to be offered the original 13.9 Mbit/s
file — a stall for them and a saturated uplink for everyone else.

The **last** entry in the header is the one read, not the first. A proxy that
replaces the header writes a single entry and the distinction does not arise;
a proxy that *appends* — Cloudflare does — leaves whatever the caller sent in
front of the address it observed itself, and reading the first entry would
believe the caller. This assumes exactly one proxy in front, which is what
both setups below are.

## Caddy

Caddy over Cloudflare Tunnel, for the reason the plan gave: sustained video
through a free tunnel tier is the kind of traffic that gets an account
throttled, and Caddy leaves nobody else in the path. The cost is an open 443
and a public DNS record.

```caddyfile
player.example.com {
        # Configure this before the DNS record exists, not after.
        basic_auth {
                viewer $2a$14$...        # caddy hash-password
        }

        reverse_proxy 127.0.0.1:8770 {
                # The player reads this to tell a LAN viewer from a remote
                # one. Caddy sets it; MEDIAGRAM_TRUST_PROXY=1 makes the
                # player believe it.
                header_up X-Forwarded-For {remote_host}

                # Films are large and a viewer may pause for an hour.
                transport http {
                        read_timeout 0
                        write_timeout 0
                }
        }
}
```

Caddy gets a certificate on first request and renews it itself. Nothing else
is needed for TLS.

Passwords are hashed with `caddy hash-password`; the plaintext never goes in
the file. For more than one household member, one entry each — revoking then
means deleting a line and reloading, not changing a shared secret.

### Cloudflare Tunnel instead

If port 443 cannot be opened, `cloudflared` reverses the direction: the tunnel
dials out, and Cloudflare Access does the authentication. Nothing inbound is
needed and TLS is terminated at their edge, which also means the video
transits their network. Decide that deliberately.

```yaml
# ~/.cloudflared/config.yml
tunnel: <tunnel-id>
credentials-file: /home/andre/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: player.example.com
    service: http://127.0.0.1:8770
  - service: http_status:404
```

Put a Cloudflare Access policy on `player.example.com` **before** creating the
DNS route. Cloudflare sets `X-Forwarded-For` too, so
`MEDIAGRAM_TRUST_PROXY=1` applies the same way — it *appends* the address it
observed rather than replacing the header, which is why the player reads the
last entry. Put another hop in front of it and that stops being true.

## Bringing it up

Order matters: nothing is exposed unauthenticated, not even for a minute.

1. Start the player on loopback and check it locally:
   ```
   curl -s localhost:8770/api/player     # {"remote":false,"maxBitrate":8000000}
   ```
2. Write the proxy config **including its authentication**, and reload it.
3. Only now create the public DNS record.
4. Verify, from off the network:
   ```
   curl -si https://player.example.com/            # 401
   curl -si -u viewer:… https://player.example.com/api/player
                                                   # {"remote":true,…}
   ```
5. Verify seeking survives the proxy — a proxy that buffers turns every seek
   into a fresh download of the whole film:
   ```
   curl -s -u viewer:… -r 1000000-1999999 \
        -o /dev/null -D - https://player.example.com/api/sets/<id>/stream
   # HTTP/2 206
   # content-range: bytes 1000000-1999999/5870132
   ```
6. Verify the player itself is not reachable except through the proxy. From
   another machine on the LAN:
   ```
   curl -s --max-time 3 http://<player-host>:8770/api/sets   # must fail
   ```

## When playback stalls

Work outward from the browser; each step rules out everything before it.

**Nothing starts, and the page says the conversion did not.** The reason is on
screen, and it says which of two things happened. `the conversion stopped
before it produced anything` means ffmpeg exited — a bad argument, a missing
encoder — and is reported the moment it does. `produced no segment within 45s`
means it is still running and has written nothing, which is the interesting
case: it is reading from the player's own Range route, so it is usually the
byte path rather than the encoder. Either way the detail is in
`$MEDIAGRAM_TRANSCODE_DIR/<session>/ffmpeg.log`, written as it happens rather
than at exit, so it exists even for a conversion still hanging.

`too many conversions at once` means four are already running. One stops five
minutes after the last viewer stops reading it — an open player says it is
still watching every minute, so that clock only runs for a session nobody has.

**A title that used to direct-play now converts.** Check
`curl -s localhost:8770/api/player`. If it says `"remote":true` from a machine
that is not remote, the forwarded address is wrong: either
`MEDIAGRAM_TRUST_PROXY` is set without a proxy in front, or the proxy is not
setting `X-Forwarded-For`, or there are two hops where the player assumes one.

**Playback starts, stalls, and then converts itself.** That is the player
noticing the link cannot carry what it was sent — see
[when the link turns out to be slower than expected](#when-the-link-turns-out-to-be-slower-than-expected).
The note under the player says what rate it settled on. If it settles
somewhere far below what the link should manage, the link is the thing to
look at; if it happens on every title, `MEDIAGRAM_TRANSCODE_MAXRATE` is set
above what the uplink really carries.

**Playback starts and then stops, repeatedly, without converting.** The page
only watches a source it started. A stall with no note under the player means
the measurement is not running — an old page still open in a tab is the usual
reason, since the watch arrived with a later version. Reload it.

**Everything is slow, including the first seconds.** Time a ranged read
directly, which takes the browser out of it:

```sh
# 20 MB from an hour into a large title, so the cache cannot already hold it
curl -s -o /dev/null -r 3000000000-3020971519 \
     -w '%{speed_download} B/s\n' localhost:8770/api/sets/<id>/stream
```

Five to six MB/s is what the link to Telegram gives, wherever in the file the
range falls — measured here at 4.88 MB/s against the live channel. Run it a
second time: served from the cache it should be hundreds of times faster
(2.86 GB/s on the same machine). If it is not, the cache is not holding what
it fetched, so check the quota and that `MEDIAGRAM_CACHE_DIR` is writable.

**The catalog is old.** The player says what it did at startup: `catalog:
updated`, `unchanged`, or a reason it kept the one it had. A package refresh
happens only at startup, so restart after publishing a new one.

## What the player deliberately does not do

Worth stating, because each of these looks like an omission and is a decision:

- **No accounts, no profiles, no history.** Authentication belongs to the
  proxy in front, and everyone who gets through it sees the same library. There
  is no per-viewer state anywhere — no resume points, no watched marks.
- **One viewer at a time, in practice.** Nothing enforces it and two people on
  a LAN are fine, but the uplink budget and the four-conversion ceiling are
  sized for a household, not an audience.
- **No writing.** The player opens the index read-only and never edits a
  caption, a set or a file. Corrections are `mediagram edit` on the uploader.
- **No library management.** No adding, no deleting, no renaming. The player
  shows what the index says exists.
- **No transcoding to more than one rendition.** One output at one bitrate,
  chosen from the link rather than adapted during playback. Adaptive bitrate
  would mean several encoders per viewer for a household that has one.

## Revoking access

- **One viewer**: delete their line from `basic_auth` and reload Caddy
  (`caddy reload --config …`). Under Cloudflare Access, remove them from the
  policy; existing sessions end at their next request.
- **Everyone, now**: stop the proxy. The player is on loopback, so nothing
  else can reach it.
- **The Telegram session**, if you believe the host itself is compromised:
  terminate it from Telegram (Settings → Devices), then issue a new one with
  `bun run login`. This is the one that matters — the auth key is the account,
  not just the library. Rotating the player's password does nothing for it.

## Running it as a service

```ini
# /etc/systemd/system/mediagram-player.service
[Unit]
Description=mediagram player
After=network-online.target

[Service]
User=andre
WorkingDirectory=/home/andre/Workspace/mediagram/web
EnvironmentFile=/home/andre/Workspace/mediagram/web/.env
Environment=MEDIAGRAM_PLAYER_ADDR=127.0.0.1:8770
Environment=MEDIAGRAM_TRUST_PROXY=1
ExecStart=/usr/bin/env bun run src/index.ts
Restart=on-failure

# The process holds an auth key and spawns ffmpeg; it needs no more than
# its own files and the render node.
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=/home/andre/.cache/mediagram-player /home/andre/.cache/mediagram-hls \
               /home/andre/.cache/mediagram-catalog
DeviceAllow=/dev/dri/renderD128 rw
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

`ProtectHome=read-only` still allows reading the index and the session file.
The three cache paths need to be writable — drop the catalog one if no package
is configured — and the render node is the VAAPI encoder, so drop that line on
a machine encoding in software.

## On the local network

`bun run dev` binds to every interface on purpose, so a phone or a TV can
reach it, and prints a warning saying exactly what that means. That is fine on
a network you trust and is not a way to run it on the internet: there is no
authentication in front of it there either.

## What does not need protecting

The HTTP API never returns a channel id, a message id or a document id. A
viewer who authenticates sees the library and can stream it; they cannot learn
where the bytes live in Telegram or reach them directly. That is a property of
the routes, not of the proxy, and it holds however the player is exposed.

## Home cache server

`mediagram_cache` (`crates/mediagram-cache`) is a separate, optional process
on the same home box: a dumb LAN chunk store Android devices read and write
so a chunk fetched from Telegram once is not fetched again by the next
device. It has no Telegram session, no index of its own, and nothing to do
with the player above — the two run independently and neither depends on
the other being up.

### Installing it

```ini
# /etc/systemd/system/mediagram-cache.service
[Unit]
Description=mediagram-cache: a LAN chunk store for the Android app
After=network-online.target

[Service]
DynamicUser=yes
CacheDirectory=mediagram-cache
StateDirectory=mediagram-cache
ExecStart=/usr/local/bin/mediagram_cache
Restart=on-failure

PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

`DynamicUser=yes` needs no user to be created ahead of time; systemd makes
`CacheDirectory=` and `StateDirectory=` for it and passes their paths in
`$CACHE_DIRECTORY` and `$STATE_DIRECTORY`, which `mediagram_cache` reads by
default (see `crates/mediagram-cache/src/config.rs`). Build and install the
binary, then:

```sh
cargo build --release -p mediagram-cache
sudo cp target/release/mediagram_cache /usr/local/bin/
sudo systemctl enable --now mediagram-cache
```

It listens on `0.0.0.0:7788` by default — every interface, on purpose, the
same choice `bun run dev` makes above — and is safe to leave running
alongside the player; starting or restarting either one never touches the
other.

### Pairing a device

```sh
sudo cat /var/lib/mediagram-cache/token
```

is the 64-character hex pairing token — `DynamicUser=yes` puts
`StateDirectory=mediagram-cache` at `/var/lib/mediagram-cache`, owned by a
transient system user, but root can still read it. The file is created,
mode 0600, on the very first request the server ever handles (or run
`mediagram_cache token` directly, as the same user, to print it without
starting the server). A device's owner enters this token once; it is never
sent over the network — every PUT instead carries an HMAC-SHA256 signature
keyed on it, checked in constant time, so a passive listener on the LAN
learns nothing that lets it write.

The exact bytes signed, and a worked example, so the Android client's HMAC
and this server's cannot drift apart silently:

```
scheme:    Authorization: MGC1 <hex>
signed:    HMAC-SHA256(token, "{method}\n{path}\n{total}\n" + hex(sha256(body)))

token:     00112233445566778899aabbccddeeff00112233445566778899aabbccddee
method:    PUT
path:      /v1/sets/abc123/chunks/0
total:     5
body:      "hello"                 (5 ASCII bytes)

signature: 5b6d16159fbd287ed1da02570ef266f83790c52de8ec1eb0d2a1500ed34aef18
```

`crates/mediagram-cache/src/token_tests.rs` asserts these same numbers in
Rust (`the_shared_test_vector_signs_to_the_published_signature` and
`the_shared_test_vector_verifies`); an Android-side test asserting them too
is how the two implementations are checked against each other rather than
against themselves.

### Finding it: mDNS, with an avahi fallback

`mediagram_cache` advertises itself as `_mediagram-cache._tcp.local.` (TXT
`v=1`) via `mdns-sd`, which is documented to coexist with `avahi-daemon` on
the same box — both bind the same multicast group without conflict. Confirm
it independently of the app with:

```sh
avahi-browse -rt _mediagram-cache._tcp
```

Set `MEDIAGRAM_CACHE_MDNS=false` (or `mdns = false` in its config) to turn
this off, and either publish a static Avahi service file instead —

```xml
<!-- /etc/avahi/services/mediagram-cache.service -->
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name>mediagram-cache</name>
  <service>
    <type>_mediagram-cache._tcp</type>
    <port>7788</port>
    <txt-record>v=1</txt-record>
  </service>
</service-group>
```

— or configure a device with the server's address by hand; either is a
complete substitute, since the API itself carries no address of its own.

### What a paired device can do, and what it cannot

A write requires the token; a read does not. That asymmetry is deliberate:
what a GET returns is exactly a chunk of a file already reachable from the
Telegram channel a viewer is on, so serving it to anyone on the LAN costs
nothing a paired viewer could not already have. A write is different — an
unpaired flood of PUTs would evict everything a paired device worked to
cache — which is what the signature guards. What it does not guard against
is a *paired* device writing a wrong-but-right-length chunk; there is no
per-chunk checksum to catch that (see `docs/system-architecture.md`
[§12](system-architecture.md#12-the-lan-chunk-server-mediagram-cache)), and
the remedy is deleting the affected set's directory under the cache root.
