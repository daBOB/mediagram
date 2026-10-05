# Player Control Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the player's scattered controls with one blurred (web) or frosted (Android) card at the bottom of the picture on web, phone/tablet and TV. It has transport, tools, stats and an episode sidebar.

**Architecture:** The web player is built first and is the reference (phases 01–02).
- Android gets one shared model in `feature/player`, phase 03. It covers the episode list, previous, restart, the 15 s skip and the CC toggle.
- Phone/tablet and TV then draw their own cards from that model, in parallel worktrees (phases 04–05).
- Phase 06 updates the design docs, walks the devices and releases.

**Tech Stack:** Vanilla JS + CSS on Bun (web, `bun test`); Kotlin, Jetpack Compose, Compose for TV, Media3 and Robolectric (Android, Gradle unit tests).

**Spec:** [spec.md](spec.md). It is the binding authority, including its User decisions table (2026-10-05).

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Web control card](phase-01-web-control-card.md) | — | pending |
| 02 | [Web episode sidebar](phase-02-web-episode-sidebar.md) | 01 | pending |
| 03 | [Android shared player model](phase-03-android-shared-player-model.md) | — (can run beside 01–02) | pending |
| 04 | [Phone/tablet control card](phase-04-phone-control-card.md) | 03 | pending |
| 05 | [TV control card](phase-05-tv-control-card.md) | 03 (parallel with 04, no shared files) | pending |
| 06 | [Close-out: docs, devices, release](phase-06-close-out.md) | 01–05 | pending |

## Global Constraints

- **Skip is 15 s everywhere.** Web `SKIP_SECONDS` and `SKIP` are 15; Android `SKIP_MS` is 15_000; the TV D-pad's `SKIP_SECONDS` is 15.
- **No new dependencies** on any surface. Android gets no blur library and no TextureView. The video stays on its own surface, which keeps HDR and Dolby Vision passthrough working.
- **Web card look:**
  - `backdrop-filter: blur(24px) saturate(1.2)` over the stage colour at 45% opacity (tune by eye);
  - a 1 px border at 8% white, radius `--radius-card` (12px), no shadow;
  - up to 880 px wide, centred, 24 px from the bottom.
- **Android card look:** black at 78% alpha, radius 12 dp, a 1 dp border at 8% white, no blur, no shadow.
  - Phone: full width minus 12 dp each side, capped at 720 dp on a tablet, 12 dp from the bottom.
  - TV: 760 dp centred, 32 dp from the bottom.
- **Layout:** row 1 is seek, row 2 is tools, row 3 is transport. Controls never lift or grow on hover or focus. Touch targets are at least 48 dp; row 2 wraps rather than shrinking.
- **Accessible names:** "Restart", "Previous", "Back 15 seconds", "Play"/"Pause", "Forward 15 seconds", "Next", "Subtitles", "Subtitle options", "Speed", "Audio", "Framing", "Stats", "Episodes".
- **Sidebar words:** the header is "Season N"; the current row reads "Now playing".
- **Speeds:** 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2× (`SPEEDS` and `PLAYBACK_SPEEDS`, unchanged).
- **Framings:** Fit, Fill, 16:9, 4:3 (`FRAMINGS` and `FramingController`, unchanged).
- **Previous and next:**
  - Previous is never restart. Restart is its own button (↺).
  - The web `0` key restarts; the TV MediaPrevious key goes to the previous title in the run.
  - With no run, ⏮, ⏭ and ☰ are hidden. In a run, ⏮ is disabled on the first title and ⏭ on the last.
- **Hiding:** the card and top bar hide on today's timer. They stay up while paused, while a menu or the sidebar is open, and on the web while the pointer is over the card.
- **TV Back order:** an open menu, then the sidebar, then the stats overlay, then the card.
- **Code style:**
  - Code comments explain why. They never cite plans, phases, tasks or review codes.
  - Source files stay under about 200 lines.
  - Commits use conventional messages with no AI references.
- **Who does what:** subagents commit on their own branch with no version bumps and no changelog. The lead merges, bumps all three manifests by pattern (a minor bump per merged phase), writes the changelog and runs `check.sh`.
- **UI checks:**
  - Web goes through the stub preview (`cd web && bun run preview`), never the live player on :8770.
  - Android devices: `ANDROID_SERIAL=caad49da` for the tablet, `-s 192.168.0.35:5555` for the TV box. Look and navigate only; never select anything that changes a setting.

## Review Focus

1. **Seeking at the edges.** −15 at 0:05 must land on 0:00, not go negative. +15 inside the last 15 s must clamp to the end and let the normal ended/Up-next path run, without looping or freezing. *Owned by: 01 (web), 03 (Android).*
2. **Esc/Back with a menu open.** It must close only that menu. It must not close the sidebar, close the player or leave fullscreen. *Owned by: 01 (web), 04 (phone), 05 (TV).*
3. **Auto-hide with something open.** The card must never hide while a menu or the sidebar is open, or (web) while the pointer is over the card. A hidden card would strand TV focus. *Owned by: 01, 04, 05.*
4. **CC with no remembered language.** "On" picks what the player picks today. A title with no subtitle tracks shows CC disabled, not a toggle that does nothing. *Owned by: 01 (web), 03 (Android).*
5. **A messy run.** A run holding an id the catalogue doesn't know, or an episode without a season or number, still builds the sidebar without crashing; unknown rows are grouped last. ⏮ and ⏭ still step through the run. *Owned by: 02 (web), 03 (Android).*

## Review log

(Filled in as phases merge.)
