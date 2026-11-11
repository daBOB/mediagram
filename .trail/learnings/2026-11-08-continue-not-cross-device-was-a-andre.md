---
title: "\"Continue not cross-device\" was a stale web page, not lost data"
author: Andre
date: 2026-11-08
id: l-f5abe051
scope: repo
status: active
touches:
  - web/src/application/lifecycle.ts
---

## Decided
2026-09-24: user reported "the played sync (continue) is not cross device". Web
`~/.local/share/mediagram-player/state.db` and the phone's
(`adb exec-out run-as com.mediagram.android cat files/state.db` + `-wal` + `-shm`)
held identical progress and watched rows to the millisecond. Data sync worked.

The gap was presentation: the web server pulled other devices' state but never
told a visible page (only `catalog` went down `/api/events`); Android reloads its
snapshot when a round pulls. Fixed by a `state` SSE event (`announcingPulls` in
`web/src/application/lifecycle.ts`).

Still open then: the web pushes only on its 5-min timer, while Android pushes on
leaving the player — web plays reach the phone up to 5 min late. Check whether a
push re-pins before adding more pushes; see telegram-pins-are-flood-limited.

**Why:** reading code first sent the investigation toward the sync protocol.

## Watch out
for any "not syncing" report, diff both DBs read-only first;
Android logs only sync failures (tag `sync`), so a quiet logcat proves nothing.
Related: device-playback-tests-touch-continue.

_From Claude Code's memory of this repo, last updated Sep 24._
