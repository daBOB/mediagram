---
phase: 5
title: "Remote access, auth and TLS"
status: pending
priority: P2
effort: "0.5d"
dependencies: [2]
---

# Phase 5: Remote access, auth and TLS

## Overview
Make the player reachable from outside the house without writing an auth
system.

## Key insight
Authentication and TLS are solved problems with mature implementations, and a
hand-rolled login on a media server is a liability with no upside. A tunnel or
reverse proxy in front means the app itself keeps no credentials, and the Rust
service never has to leave localhost.

## Requirements
- Functional: the player is reachable over the internet, behind
  authentication, over TLS.
- Non-functional: nothing is exposed unauthenticated, not even briefly during
  setup; the Rust service stays bound to localhost.

## Architecture
```
internet ──TLS──> Cloudflare Tunnel or Caddy ──auth──> Bun (127.0.0.1)
                                                        └──> mediagram serve (127.0.0.1)
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
1. Bind Bun to 127.0.0.1 and confirm the Rust service is already localhost.
2. Put the chosen proxy in front; verify with the app stopped that nothing
   answers on the public name.
3. Range requests must survive the proxy: verify a seek still produces a 206
   end to end from outside.
4. Cap transcode bitrate under the uplink (phase 4 supplies the knob); direct
   play remains LAN-only, because 13.9 Mbit/s against a 25 Mbit/s uplink with
   no headroom will stall.
5. Document the setup, including how to revoke access.

## Success Criteria
- [ ] The public name serves nothing without authentication
- [ ] A seek from outside produces a 206, not a full refetch
- [ ] The Rust service is not reachable from outside at all
- [ ] Playback from outside stays within the uplink and does not stall
- [ ] The operating document is enough to rebuild this from scratch

## Risk Assessment
- **Exposure during setup.** Configure auth before the first public DNS
  record, not after.
- **Uplink saturation.** One remote viewer at 8 Mbit/s is comfortable; direct
  play of a 13.9 Mbit/s source is not. The player must not offer direct play
  remotely.
- **Tunnel terms of service.** Sustained video through a free tunnel tier is
  the kind of thing that gets an account throttled. Caddy sidesteps it.
