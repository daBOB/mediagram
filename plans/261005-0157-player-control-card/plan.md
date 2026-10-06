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
| 01 | [Web control card](phase-01-web-control-card.md) | — | done |
| 02 | [Web episode sidebar](phase-02-web-episode-sidebar.md) | 01 | done |
| 03 | [Android shared player model](phase-03-android-shared-player-model.md) | — (can run beside 01–02) | done |
| 04 | [Phone/tablet control card](phase-04-phone-control-card.md) | 03 | done |
| 05 | [TV control card](phase-05-tv-control-card.md) | 03 (parallel with 04, no shared files) | done |
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

- **2026-10-05, ruling: one implementer per phase.** Each phase gets one implementer, working task by task test-first, rather than a fresh agent per task. A spec-and-quality review follows each phase, and a final whole-branch review closes the work.
  - *Why:* the 5 phases hold about 45 tasks that build on each other inside a phase. A fresh agent per task would re-read the same files about 45 times.
  - *Cost if wrong:* a mistake surfaces at the phase review instead of the task review, so the rework is up to one phase.
- **2026-10-05, ruling: the progress line follows the web.** It shows for any recorded position with a known runtime, as the web's season page does, rather than only for a resume point.
  - *Why:* surface parity.
  - *Cost if wrong:* a one-line change in `episodeListOf`.
- **2026-10-05:** phase 03 started in a worktree. It runs beside the web phases, as the phase table allows.
- **2026-10-05, phase 03 merged** (`a0f5e5c5`). The review found 1 High, 1 Medium and 4 Low issues, all fixed in fix round 1 and confirmed by a scoped re-review, with mutation checks showing the new tests catch breakage.
  - **Parity rulings made at review:**
    - No ☰ for a hand-picked run (the web passes `collection: null`).
    - The last section is "Episodes".
    - Course sections take the last path segment.
    - A watched row keeps its progress line.
  - **Parked:**
    - A title twice in a run: ⏮/⏭ use the first occurrence, as ⏭ always did.
    - Two Low-risk surviving mutants on catalogue retention (the call site of `runSetsOf`, and dropping `loaded` on an empty run). Neither can be seen by a viewer.
- **2026-10-05:** phases 04 (phone) and 05 (TV) started in parallel worktrees from `a0f5e5c5`, with no shared files. Phase 01 (web) is in progress.
- **2026-10-05, tablet walk of the phase 04 branch** (look only; one test play of a cached Boston Legal episode moved its resume point from 28:13 to 29:11):
  - The card, the sidebar and Back closing only the sidebar all work on the device.
  - **Found:** row 2's tools are centred, where the spec and the web put them on the left with PiP at the right. Goes into phase 04's fix round.
  - **Found:** the open sidebar overlaps the card.
- **Ruling: the card clears the open sidebar on every surface.** It is laid out left of the sidebar and narrows if it has to.
  - Added to the phase 02 plan.
  - Sent to the phase 05 worker.
  - Goes into phase 04's fix round.
- **2026-10-05, Continue false alarm.** Boston Legal S3E15 left the tablet's Continue during the walk. `stats_titles` showed the user watching it on another device, 02:50–03:32, finishing it at 03:33:55. It was not a player bug. On 0.114.0, Back stops playback and a stray pause key after leaving does not restart it, both checked on the tablet.
- **2026-10-05, phase 02 review: changes needed.** One High (focus lost to `<body>` after ✕, a pick, or a season arrow at its end) and three Medium. Fix round 1 is under way.
  - **Ruling:** the clears-the-sidebar breakpoint is set to the width where the card's bottom row fits.
  - **Parked:** web and Android group a rare edge case of episodes differently. Nothing in today's catalogue hits it.
- **2026-10-05, phases 01–05 merged and released:** 0.112.0 (Android model), 0.113.0 (web card), 0.114.0 (phone), 0.115.0 (web sidebar), 0.116.0 (TV and docs). Each phase was reviewed, sent back for one fix round, then re-reviewed. `scripts/check.sh` is green on main.
  - **Phase 06:** docs are done. The tablet walk was done on the phase 04 branch, and 0.114.0 is installed on the tablet.
  - **Owed:** the TV box walk (box unreachable at 02:35); it includes the parked TV Lows: R1 one-shot focus, R2 the Notes fence, R3 ⓘ's conditional modifier. Also owed: the user's word on publishing to the channel.
- **2026-10-06, TV box walk** ([report](reports/tv-box-walk-261006-report.md)). It ran on the published 0.117.1, as "andre" and paused. The user ruled this because "TV test" now requires choosing a PIN.
  - Focus, the row fences (R1 and R3), the menus, the sidebar, greyed rows, title switching and the Back order all held.
  - Three layout bugs were found, plus a fourth from review. All are fixed in 0.117.2:
    - menus opened over the seek row;
    - the TV title ran under the sidebar;
    - the card covered the stats while the sidebar was open;
    - a menu opened with stats on was 0 dp tall.
  - **Ruling:** menus sit above the card, centred on their button. This follows the spec's decision table ("above the card") and the web; the spec's line 109 ("directly above its button") was read as horizontal placement.
  - **Ruling:** on the TV the stats wait while the sidebar is open, because of the screen budget. This is recorded in the Television differs section of the design docs.
  - **Open:** R2, the Notes fence, needs a title with notes. Publishing still needs the user's word.
- **2026-10-06, rechecked on the box as 0.117.3.**
  - Menus have no headings, matching the web and phone. Six speeds still scroll at 540 dp.
  - Beside the sidebar the TV keeps the marks and drops the title. This keeps the user's "Slim top bar keeps them" decision intact.
  - `check.sh` on main first failed: a TV subtitle test still expected the removed "Subtitles" heading, and the ui-tv test JVM ran out of its 512 MB default and wedged the run. The test now asserts no heading; ui-tv tests get 2 GB. Rerun green (ui-tv: 572 tests, 0 failures).
