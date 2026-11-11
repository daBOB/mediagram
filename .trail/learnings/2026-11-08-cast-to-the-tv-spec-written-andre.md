---
title: Cast to the TV spec written 2026-10-18…
author: Andre
date: 2026-11-08
id: l-cdb2b66a
scope: repo
status: active
touches:
  - docs/superpowers/specs/2026-10-18-cast-to-tv-design.md
  - ".superpowers/sdd/plan/progress.md"
---

## Decided
On 2026-10-17 the user called "cast from tablet to tv" an important feature; brainstorm 2026-10-18 (architectural path).

**User decisions 2026-10-18 (do not reverse silently):**
- Moments: both, pick on tablet → play on TV, and move what's playing.
- Tablet becomes the remote (play/pause, seek, ±10 s, next ep, subtitles/audio, Stop; a bottom bar to get back to it).
- TV side: only casts to Mediagram when it is open on the box (no Google Cast / Cast Connect).
- TV plays under the caster's profile; returns to its own afterwards.
- Senders: phone, tablet and web.
- One TV for now, no picker.
- Busy TV: ask on the tablet first ("Replace it?").
- Approach A: extend watch together to `#mlib-together v=2` with a `cast` section and remote members.

**Then the user asked for "anyview cast, airplay 2 and miracast"** and chose: box cast first, then Miracast/AnyView as a second screen (Android `Presentation`, 1080p, no HDR), and AirPlay only from Safari in the web player. The user has an Apple device for the AirPlay probe.

- **Spec:** `docs/superpowers/specs/2026-10-18-cast-to-tv-design.md` (commit be1b7de5 on main, unpushed). All five parts were approved in conversation.
- **Added while writing, flagged for review:** the `refused` state, and the rule that screens opening the remote join as remote members.
- **Spec approved** 2026-10-18 ("continue"), including the refused state and remote-joins rule.
…

## Watch out
- Resume at the spec review; do not re-ask the decisions above.
- The box sleeps off the LAN, so the TV must be switched on first, whatever the approach.
- Related: kids-daily-limit-merged-0-125-0, tv-compose-focus-gotchas, tv-box-4k-facts.

**2026-10-20 late:** main (0.136.1) merged into feat/cast-to-tv (8a2d8ce0, all tests pass). User: casting only to TVs in the sender's household ("Only to TVs in my household"). Still owed: that filter, phase 10 AirPlay (user's Apple device), phase 11 box walk, then merge with the user's word.
…

_From Claude Code's memory of this repo, last updated Oct 19._
