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

To issue a session from the uploader's own key instead of logging in again:

```
mediagram export-session      # prints MEDIAGRAM_SESSION=... for one player
```

One auth key cannot serve two clients at once — a second connection using it
breaks both until the process restarts — so a host that runs the uploader and
a player needs two keys, not one shared.

## Starting it

```
cd web
bun run start             # loopback only; this is what you run behind a proxy
bun run dev               # every interface, for a phone or a TV on the LAN
```

It prints what it decided, and reading that line is most of the diagnosis when
something is wrong later:

```
catalog: 16 playable sets
cache: 0.45 GB of 8.00 GB in /home/andre/.cache/mediagram-player, readahead 4 chunk(s)
encoder: h264_vaapi on /dev/dri/renderD128
serving on http://127.0.0.1:8770
```

With a package configured, one more line above it says where the catalog came
from (`catalog: updated from …`, or `unchanged`), and the count of playable
sets gains a poster count. A refusal says why instead, on two lines.

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
| `MEDIAGRAM_PLAYER_ADDR` | `127.0.0.1:8770` | Where to listen. **Leave it on loopback in production** |
| `MEDIAGRAM_TRUST_PROXY` | `0` | Believe `X-Forwarded-For`. Set to `1` **only** behind a proxy |
| `MEDIAGRAM_CACHE_DIR`, `MEDIAGRAM_CACHE_MAX` | `~/.cache/mediagram-player`, `8G` | Chunk cache and its quota |
| `MEDIAGRAM_CACHE_READAHEAD` | `4` | Chunks fetched ahead of a sequential read |
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
| Video | `h264`, `avc`, `avc1`, `vp8`, `vp9`, `av1` | everything else; HEVC in particular |
| Audio | `aac`, `mp4a`, `opus`, `vorbis`, `mp3` | everything else; AC-3 and E-AC-3 in particular |

The lists live in `web/public/lib/playable.js` and a test fails if this table
stops matching them.

Why those three are where the line falls:

- **Matroska.** No browser ships a `.mkv` demuxer. The codecs inside may be
  perfectly playable and it makes no difference; there is nothing to open the
  container with.
- **HEVC.** Patchy and licence-bound. Safari on Apple hardware plays it,
  Chrome does on some platforms and not others, and "some platforms" is not
  something a library can be built on.
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

### Searching

`GET /api/search?q=…`, and the box in the sidebar. It matches titles, show and
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

Two ways, and the second is what makes the player independent of the machine
that did the uploading.

**The index on this machine.** `MEDIAGRAM_LIBRARY_DB` points at the
`library.db` that `mediagram` writes, and the player opens it read-only. It
has no default — `bun run login` writes the usual path into `.env` for you —
and it is what the player uses unless a package is configured. Only useful
where the player runs beside the uploader.

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
