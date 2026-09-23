# Validation: Android watch state on the tablet, against the running web player

Date: 2026-09-23 · Tablet `caad49da` (signed in, library Mediagram), web player on this machine with `MEDIAGRAM_SYNC_STATE=1`, same channel. Read-only on the account except what the product itself does (one state document pinned by the tablet).

| Check | Result |
|---|---|
| First launch pulls the web's state | tablet `state.db`: profile **andre**, 8 positions, 31 finished titles, all from the web's document |
| Tablet publishes its own document | message 3211 `#mlib-state v=1 device=1ad8217…`, pinned once (3212) |
| "Who's watching?" | lists **andre**; choosing it opens the start page |
| Player records | Justice League played to 0:37, left → `progress` row 43.6 s / 7202.6 s for andre |
| Player resumes | reopened → 0:53 nine seconds after Play, i.e. resumed ≈0:43 |
| Tablet → web position | web `state.db` had Justice League at 64.3 s for andre moments after the tablet left the player |
| Web → tablet Continue | tablet start page: Continue · 2 (Justice League 1:04, Zwischen Himmel und Hölle 0:47), Next up · 1 |
| Kept shelves | tab row Home · Movies · Series · Tutorials · Continue · Watchlist · Collections · Kids |
| Watchlist across devices | toggled on the tablet's player → leave → web `watchlist` row andre / Justice League, not removed, ≈25 s later |

## Not exercised on the device
- Collections and Kids across devices: same record and merge path as the watchlist; covered by the shared list fixtures (web + core, both orders). Not tapped through on the tablet.
- Pin refused on first send: the refused path only occurs under a Telegram flood wait; covered by core and web unit tests, not provoked live (provoking it would spend the account's pin budget).
- Web ↔ tablet latency in seconds via push: the tablet → web direction was within half a minute; a stopwatch run both ways was not done.

## Unresolved
- ~~The player's top-bar toggles sit in the status-bar band~~ — fixed: inset by the system bars (now y≈195–250 px); plain taps toggle them, and a remove then re-add on the tablet left the web's watchlist row on the list.
