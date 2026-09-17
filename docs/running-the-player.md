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

## Configuration

Everything is environment variables. `web/.env` is read at startup and is in
`.gitignore`; keep it at mode 600.

| Variable | Default | What it does |
|---|---|---|
| `MEDIAGRAM_API_ID`, `MEDIAGRAM_API_HASH` | — | Telegram application credentials |
| `MEDIAGRAM_SESSION` | — | The auth key. Treat as a password for the account |
| `MEDIAGRAM_CHAT_ID`, `MEDIAGRAM_CHANNEL_ACCESS_HASH` | — | The library channel |
| `MEDIAGRAM_LIBRARY_DB` | `~/.local/share/mediagram/library.db` | The published index, opened read-only |
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

Two directories grow, and only one of them has a quota.

`MEDIAGRAM_CACHE_DIR` is bounded by `MEDIAGRAM_CACHE_MAX` and evicts by last
use. `MEDIAGRAM_TRANSCODE_DIR` is not: a conversion keeps every segment it has
written so the viewer can seek back through them, which is about 2 MB per
second of film — some 7 GB for a feature watched to the end. A session's
directory goes when the session stops, the whole directory is cleared at
startup, and at most four conversions run at once, so the ceiling is roughly
four films' worth. Put it somewhere that can take ~30 GB, or lower the cap.

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
ReadWritePaths=/home/andre/.cache/mediagram-player /home/andre/.cache/mediagram-hls
DeviceAllow=/dev/dri/renderD128 rw
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

`ProtectHome=read-only` still allows reading the index and the session file.
Both cache paths need to be writable, and the render node is the VAAPI
encoder — drop that line on a machine encoding in software.

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
