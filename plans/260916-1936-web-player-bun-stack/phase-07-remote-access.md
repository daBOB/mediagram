---
phase: 7
title: "Remote access, auth and TLS"
status: completed
priority: P2
effort: "0.5d"
dependencies: [4]
---

# Phase 7: Remote access, auth and TLS

## Overview
Put authentication and TLS in front of the player, without writing an auth
system. Where the player runs — at home behind a tunnel, or on a host of its
own — changes the topology but not this phase's answer.

## Key insight
Authentication and TLS are solved problems with mature implementations, and a
hand-rolled login on a media server is a liability with no upside. A tunnel or
reverse proxy in front means the app itself keeps no credentials.

This matters more than when it was planned: the player holds the account's
MTProto auth key, so an unauthenticated player is not merely a leaked
library.

## Requirements
- Functional: the player is reachable over the internet, behind
  authentication, over TLS.
- Non-functional: nothing is exposed unauthenticated, not even briefly during
  setup; the player binds to loopback and is reached only through the proxy.

## Architecture
```
internet ──TLS──> Cloudflare Tunnel or Caddy ──auth──> Bun (127.0.0.1)
                                                        └──> Telegram
```

Two options, both fine:

| | Cloudflare Tunnel | Caddy |
|---|---|---|
| Inbound ports | none | 443 open |
| Auth | Cloudflare Access | basic auth or forward auth |
| TLS | terminated by Cloudflare | automatic |
| Cost | free tier | free |
| Trade | traffic transits Cloudflare | you own the edge |

Streaming media through Cloudflare's free tier is against the spirit of their
terms; for a single household it is unlikely to matter, but Caddy avoids the
question entirely. Worth deciding deliberately rather than by default.

## Related Code Files
- Create: `docs/running-the-player.md` (the operating document),
  deployment config kept out of the repo
- Modify: `web/src/server.ts` (trust proxy headers, bind localhost),
  `README.md`

## Implementation Steps
1. Bind Bun to 127.0.0.1.
2. Put the chosen proxy in front; verify with the app stopped that nothing
   answers on the public name.
3. Range requests must survive the proxy: verify a seek still produces a 206
   end to end from outside.
4. Cap transcode bitrate under the uplink (phase 6 supplies the knob). Direct
   play of a 13.9 Mbit/s film only works where the link carries it: on the LAN,
   or from a host whose egress is not the house's 25 Mbit/s uplink.
5. Document the setup, including how to revoke access.

## Success Criteria
- [x] The public name serves nothing without authentication — verified against
  a stand-in proxy holding basic auth; no public name exists yet
- [x] A seek from outside produces a 206, not a full refetch — 206 through the
  proxy with the right `Content-Range`, and the bytes match the whole file
- [ ] The player is reachable only through the proxy, never directly — it binds
  to loopback by default, but this is a property of the deployment host and has
  to be checked there
- [x] Playback from outside stays within the uplink and does not stall — a
  remote viewer is offered a conversion for anything above the cap, verified by
  lowering the cap below the library's own bitrate
- [x] The operating document is enough to rebuild this from scratch —
  `docs/running-the-player.md`

## Risk Assessment
- **Exposure during setup.** Configure auth before the first public DNS
  record, not after.
- **Uplink saturation.** One remote viewer at 8 Mbit/s is comfortable; direct
  play of a 13.9 Mbit/s source is not. The player must not offer direct play
  remotely.
- **Tunnel terms of service.** Sustained video through a free tunnel tier is
  the kind of thing that gets an account throttled. Caddy sidesteps it.
