---
title: "Funstation's web player is in use while sync (push) lines arrive, even with no ffmpeg running"
author: Andre
date: 2026-11-14
branch: main
cost: { minutes: 1, tokens: 3291 }
id: l-baa0a056
scope: repo
status: active
src: agent
touches:
  - web/src/transcode/args.ts
  - scripts/setup-funstation.sh
---

## Decided
Before restarting the web player (deploys, config changes), treat it as busy while its journal shows "sync (push)" lines, which a playing browser sends about every 12 s. Wait for those to stop for several minutes.

## Tried and ruled out
Waiting for no transcode ffmpeg and no "sync (write)" lines for 5 minutes (2026-11-14). A transcode reads the source at up to twice real time (`-readrate 2` from `transcodeArgs` in web/src/transcode/args.ts) and exits once it reaches the end, so it was gone at 00:49 while the viewer still had about 20 minutes of the episode left. "sync (write)" appears only around the start, so the 5 minutes of quiet ran out and the job restarted the player at 00:51:04, in the middle of the user's episode.

## Watch out
Funstation's state and stats show the web player's own sync device id (logged at start as "sync: <uuid> every 300s"), not the browser's, so they can't tell you which device is watching. scripts/setup-funstation.sh installs the service; restart it with the check above.

In the code: journalctl -u mediagram-player | grep 'sync (push)'; the HLS output lives in ~/.cache/mediagram-hls/<session>/.
