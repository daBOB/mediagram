# The web server follows the channel's index

Status: **in progress** — approved 2026-09-23

## Why

Uploads now happen on another machine, which pins each new `library.db`
snapshot to the channel. The Android app installs that snapshot within seconds
of the pin (`docs/system-architecture.md` §push updates). The web player hears
the same pin (`web/src/index.ts:205`) and only logs it: its catalog is this
machine's `library.db`, opened once at startup. 43 films reached the web shelf
only after a hand-run `mediagram rescan` on 2026-09-23; the next upload was
again missing. Per CLAUDE.md § Surface Parity this is a defect in the web player.

## Decided (user, 2026-09-23)

- The **server** receives channel updates — pushed by Telegram, not polled —
  exactly as the Android app does.
- The **page** gets its refresh from its server. It never talks to Telegram.

## Shape

```
Telegram ──pin / #mlib-index post──▶ server listener (always on)
                                        │ newest snapshot by pushed_at
                                        ▼
                              download → prove → install v-<pushed_at>/
                                        │ swap live catalog
                                        ▼
                     GET /api/events (SSE) ──"catalog"──▶ page reloads /api/sets
```

## Phases

| # | Phase | Status |
|---|-------|--------|
| 01 | [Pick and install the channel's newest index](phase-01-pick-and-install-channel-index.md) | done |
| 02 | [A catalog the server can swap while running](phase-02-swappable-live-catalog.md) | done |
| 03 | [Listen always; install on every index event and at startup](phase-03-listen-and-install-on-index-events.md) | done |
| 04 | [Server-sent events to the page](phase-04-server-sent-catalog-events.md) | done |
| 05 | [Validate live, docs, version](phase-05-validate-docs-version.md) | in progress — live pin pending |

## Key dependencies / rules

- Selection mirrors core's `pick_index` (`crates/mediagram-core/src/api/channel/index.rs:56`):
  newest by caption `pushed_at`, pins **and** a `#mlib-index` text search,
  channel posts only, higher id breaks ties. Pinned by shared fixtures, as
  `updates.ts` already is.
- Install mirrors core's `install.rs`: 256 MiB cap, stage, prove it is a
  library by counting playable sets, only then swap.
- Never a second MTProto client: everything runs on the player's existing one
  (memory: shared auth key breaks both clients).

## Answered (user, 2026-09-23) — all three proposals accepted

1. **Posters.** *(Superseded same day at the user's request: the server now
   runs `mediagram posters --index <snapshot>` after each install — see
   changelog.)* A snapshot carries only `library.db`. Posters keep
   coming from this machine's `~/.local/share/mediagram/posters`; new titles
   show initials until `mediagram posters` runs. Android fetches its own
   artwork — matching that is a follow-up, not this plan.
2. **Origin precedence.** a configured package still wins; otherwise
   the channel; this machine's `library.db` only when the channel has never
   answered and nothing is installed (first run offline).
3. **The tab-return refresh** (5d48526) becomes redundant: remove it;
   the page instead reloads once whenever its event stream (re)connects, which
   covers a laptop waking from sleep.
