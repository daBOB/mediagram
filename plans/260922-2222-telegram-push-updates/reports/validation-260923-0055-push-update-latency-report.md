# Validation: push-update latency, end to end

Date: 2026-09-23 · Phase 06 · What was measurable is measured; what was not is handed on, with the reason.

## Measured

| Path | Setup | Result | Source |
|---|---|---|---|
| Channel → another session (raw delivery) | grammers ↔ teleproto, uploader key ↔ web key | new / edit / delete in **0–12 ms** | `spike-260922-2305-update-delivery-redo-report.md` |
| Another device's state write → core `next_library_event` | real `Core`, key-only in-memory session (as Android), after 3 cancelled calls | one `State` **5.0 s** after the send (the debounce window); own-device write → nothing | phase 03 review follow-up |
| Uploader's index push → tablet catalog installed | tablet `caad49da`, catalog on screen, untouched | `pushed_at` 00:22:35 → installed 00:22:38: **~3 s** (two machines' clocks) | phase 04 |
| Uploader's index push → new title's poster on its card | same, after the poster-refresh fix | catalog 00:40:28, poster file and card by **00:40:32** | phase 04 / changelog 2026-09-23 |

Catch-up after a disconnect replays no channel messages (fair test: the missed message still existed at reconnect). The design runs a round on (re)start for watch state instead; the Android catalog deliberately does not (a read downloads the whole index).

## Not measurable yet: web ↔ Android watch state, both ways

The Android app neither reads nor writes watch state — no `WatchSync`, no device id (the Android watch-state plan, phases 02–05, is in progress elsewhere). So there is no Android side to receive a web write or to send one. The core half is already measured above (5.0 s from write to event). **Handed to** `plans/260922-2135-android-watch-state-sync/phase-09-docs-versions-and-device-validation.md`: once `WatchSync.soon()` is wired to `STATE` (this plan's phase 04 remainder), measure web → tablet Continue row and tablet → web Continue shelf.

## Unresolved questions
- The web player's index log line (`library: the channel pinned a new index`) was not read: the running player's stdout belongs to a terminal this session cannot see. Its delivery path is the same teleproto one measured in the spike.
