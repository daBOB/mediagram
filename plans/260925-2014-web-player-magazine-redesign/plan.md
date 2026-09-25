# Web player — digital-magazine redesign

Status: implemented 2026-09-25 (all phases); uncommitted. User asked to implement all without stopping, so the phase-3 screenshot checkpoint became a self-check (see below). Supersedes the rejected sidebar redesign
(`260925-1923-web-player-redesign`, commit ccb0605).

## Direction (user decisions, 2026-09-25 — do not reverse silently)

- Reference: a Jellyfin-style *digital entertainment magazine* home, with this hierarchy:
  **cover story → editorial features → Continue → typographic break → library browsing.**
- **Real TMDB backdrops** for the cover story (pipeline change, not a poster-only cover).
- **Whole web player**, not just home.
- **Dark-first**, with a matching light "paper" variant that follows the system setting.
- Editorial copy comes from **real data only**: TMDB taglines (409), overviews (586),
  genres, counts, arrival dates. No invented marketing lines ("Cinema at Home" becomes
  a real film's title and tagline).

- **Editorial features are single-title spotlights, not genre lists** (user correction):
  Editor's Choice = a pick the user pins; Trending = TMDB popularity; Staff Pick =
  top TMDB rating, unwatched, rotated daily.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Backdrop pipeline + popularity](phase-01-backdrop-pipeline.md): Rust fetch, key, schema v8, web fields | done |
| 2 | [Magazine shell and theme](phase-02-magazine-shell-and-theme.md): tokens, department bar, index rail | done |
| 3 | [Home as a magazine](phase-03-home-as-a-magazine.md): cover, features, break, shelves | done |
| 4 | [Remaining pages](phase-04-remaining-pages.md): shelves, film/series, search, collections, reel | done |
| 5 | [Verify and ship](phase-05-verify-and-ship.md): stub harness, tests, a11y, docs, version | done |

Checkpoint: after phase 3, show desktop and phone screenshots of home (dark + light)
**before** phase 4. This is the lesson from the rejected redesign: approve a bounded
visual first.

## Key facts (verified)

- Posters are files `<key>.jpg` keyed `tmdb-{movie|tv}-{id}`, not DB rows
  (`crates/mediagram-tmdb/src/posters.rs:95`, `web/src/package/posters.ts:20`).
  Backdrops get the key `…-bg`, so no schema bump is needed.
- The TMDB details cache never expires (`disk_cache.rs:44`). A backfill is
  `mediagram posters` again, which downloads images only.
- The channel index ships `library.db` only. Each device fetches images from the CDN
  (`web/src/channel-index/fetch-posters-for-index.ts:23`).
- Fonts are already self-hosted: Fraunces (display serif), Newsreader (text serif),
  Geist (UI). No new fonts.

## Out of scope / deferred

- **Android hero.** Parity is owed. Android inherits the backdrop *files* through
  `resolve_posters`, but its home layout is a follow-up plan, recorded here per
  § Surface Parity.
- Backdrops in the encrypted export package (64 MB cap). Excluded, see phase 1.

- **Android "Make editor's choice" action and the home spotlights**: owed under
  § Surface Parity; they go in the same Android follow-up plan.

## Answered 2026-09-25

- Android's on-device fetch **skips backdrops** until its hero exists (user: yes).
- Backdrops are **excluded from the encrypted export package** (user: yes).

## Outcome (2026-09-25)

- Shipped in the working tree: backdrops and popularity (phase 1), the dark-first
  shell with `.library-rail` and a department bar (2), the magazine home (3), and title
  bands, serif openers and the editor's-choice pin (4).
- Verified against a stub (the real `startServer` over a scratch copy of the channel
  snapshot with popularity grafted in, real posters, and a scratch copy of watch state)
  at 1440 and 375, dark and light: no horizontal overflow; the player dialog is intact.
- Bug found in verification: `.rail` collided with the player HUD's `.rail` and was
  renamed `.library-rail`.
- Tests: web 1836 pass (editorial-picks, editor's-choice store/HTTP/sync, home DOM);
  Rust 1111 pass; lint clean; one pre-existing `tsc` error remains.
- Featured reel: the film's backdrop, lightly softened, replaces the blurred-poster wash
  when present.
- Not done:
  - **Lighthouse measured the profile picker**, not home, because a fresh profile is
    asked who is watching. Load timing was measured in the browse session instead
    (FCP 24 ms locally).
- Pending for the user:
  - `mediagram push-index` so Trending has popularity. Until then the card reads
    "New in the library".
  - Install the new binary.
  - Install the new builds before publishing a v8 package.
- Android parity is owed: hero, features, pin.

## Review (code-reviewer, 2026-09-25, web): fixed

- **High, fixed:** the home masthead's white type sat over light paper when there was
  no cover, under reduced motion, or without scroll timelines. The dark glass is now
  gated on `data-cover` + `@supports (animation-timeline)` +
  `prefers-reduced-motion: no-preference` + desktop; otherwise it is the paper bar.
- **High, fixed:** unpinning after a merge revived an older pick. Unpin now retires
  every live mark, and GET returns the newest *playable* pick. Tested:
  `unpinning after a merge leaves no pick, not an older one`.
- **Medium, fixed:**
  - Carousel: pointer and focus are held separately; added a visible pause/play
    button (WCAG 2.2.2); slides carry `role=group` / `aria-roledescription=slide`;
    a redraw resumes the same slide.
  - Reveal: the observer is disconnected per draw, and a redraw of the same hash
    shows everything at once instead of fading it again.
  - The pin control's focus is restored after the redraw it causes.
- **Low, fixed:** features take a poster when there is no backdrop; the cover still
  requires a backdrop.
- **Low, left as is:**
  - A show is pinned by its current first episode.
  - A kids profile can change the household pick, limited to titles it can see.
  - "This month" overlaps "Recently added".
- **Not ours; leave out of any commit:** `web/.github/workflows/react-doctor.yml`, the
  `doctor` script in `web/package.json`, `web/.claude/`, and `skills-lock.json`.

## Validation Summary

**Validated:** 2026-09-25 (after implementation: this validates decisions Claude made alone during the build)
**Questions asked:** 4

### Confirmed Decisions
- **Slot with no pin:** keep the staff rule labelled "Staff pick". Two Staff pick cards
  until something is pinned is accepted.
- **Kids profiles:** **hide** "Make editor's choice". The household pick is a grown-up's
  decision.
- **This month:** keep it, but **skip the films the Recently added row already shows**,
  so it adds titles instead of repeating them.
- **Finish:** commit (conventional, stray files excluded), run `mediagram push-index`,
  and install the new binary (`cargo install --path crates/mediagram`).

### Action Items
- [x] `pin-control.js` / `film-page.js` / `series-header.js`: no pin control on a kids
  profile (pass the profile's kids flag in, or check `state.profile()?.kids`); add a
  browser test.
- [x] `editorial-picks.js` `arrivedWithin` / `homeEditorial`: exclude the set ids shown
  on the Recently added row (`shelves.latestMovies`); update the unit test; hide the
  column when nothing is left.
- [x] Commit with the versions at 0.55.0 (`9edda2f`, not pushed to a remote). Exclude `web/.github/`, the `doctor` script
  line in `web/package.json`, `web/.claude/`, `skills-lock.json`, and
  `plans/260921-1751-android-external-cache/plan.md` (another session's edit).
- [x] `cargo install --path crates/mediagram`: 0.37.0 replaced by 0.55.0.
- [x] **Resolved 2026-09-25 21:4x:** the uploader machine was upgraded to 0.55.0 and ran `metadata` + `push-index` (message 5327). The player serves v8 (1164 sets, popularity on 838/838 films); home reads Editor's choice / Trending on TMDB / Staff pick. Background, kept for the record: `mediagram push-index` from *this* machine was **not** run. It publishes the
  local index wholesale (no merge, `telegram/index_publish.rs`). On 2026-09-25 the channel
  snapshot held 1161 sets; this machine's index held 925. Pushing would drop **257 sets**
  (the other machine's uploads) from web and Android. This machine has 21 sets the channel
  lacks. Options: (a) on the other machine, install 0.55.0, then run `mediagram metadata`
  and `mediagram posters`, then `mediagram push-index` (it lacks the 21 local sets, as
  before); (b) build a channel merge into this index first, then push from here.
  Previously: Confirm
  that the player's channel snapshot is v8 and that Trending shows "Trending on TMDB".
  Mind the other machine: until it runs a v8 build, its pushes carry no popularity.
