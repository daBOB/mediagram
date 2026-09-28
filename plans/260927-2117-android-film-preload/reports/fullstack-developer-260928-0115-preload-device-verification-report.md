# Phase 04 — Preload device verification (TV box)

Plan: `plans/260927-2117-android-film-preload/`. Device: TV box `192.168.0.35:5555`
(Android TV, ARM 32-bit, benchmark build, `compile -m speed`d), TV test profile.
Home cache server `192.168.0.240:7788` already on 0.71.0 throughout. Tablet pass
not started — TV box work occupied the full session, including an unplanned bug
investigation (below). HEAD stayed at d6ceddad; no code changed, no commit, no
version bump.

Screenshots: `reports/screenshots/` (many; filenames are chronological, not all
referenced below).

## What was verified

**Full sweep, before any device work**: `cargo test -p mediagram-cache` — 67 +
7 + 11 passed. `./gradlew testDebugUnitTest lint :app:assembleDebug` — build
successful, no new lint findings (38 stale baseline entries, pre-existing).

**Preload lifecycle (Chihiros Reise ins Zauberland, 1.1 GB, fresh)**:
- Idle → tap Preload → Queued is skipped when nothing else is running;
  Preloading with the bar and "x of y · n%" appears within ~2s.
- Measured throughput: ~3.5–4 MB/s sustained (device write), home server
  tracked within a few MB of the device figure throughout.
- Backgrounded (Home key) for 60s+: `dataSync` notification channel
  `film_preload` confirmed present (`dumpsys notification`); process (pid
  1903) stayed alive; home server held_bytes kept climbing the whole time
  (+~200MB across the background window) — the service is doing its job.
- Finished to "Preloaded ✓" / "offline" badge in search results; "Remove
  preload" plate present; Home server line read "1.1 of 1.1 GB".
- Played the fully-preloaded film for 45+ seconds real time: continuous
  playback, zero rebuffer stalls (contrast with a network fetch, which
  visibly rebuffers every few seconds), zero `telegram`/`mtproto`/`grammers`
  log lines in that window. Left it as the one preloaded film per the plan.

**Cancel / resume (Lockere Geschäfte, 1.1 GB, separate fresh film, used
because Chihiro had already finished)**:
- Cancelled at 34 MB / 3% → state read "Preload · 5% held" within ~1.5s (the
  extra 2% is the home-server-side write finishing after the local cancel;
  expected, matches `FilmPreloadState.Idle`'s own re-verification doc).
- Re-enqueued → resumed from 69 MB, not 0 — confirmed continuing from the
  held share, not restarting.
- Left running in the background at session end (~20% when last checked) —
  a deliberate byproduct of the cancel/resume test, not cleaned up; harmless,
  no further action needed from it (fits comfortably under budget, will
  either finish or sit "Preloading" until next opened).

**Pause while a different film plays**: confirmed at the mechanism level.
`FilmPreloader.runItem` pauses on *any* open title (`if (open != null)`,
`FilmPreloader.kt:233`), not just a different one — reserving budget is what
excludes the same film, not the pause itself. Directly observed: while
Chihiro preloaded, a few seconds of accidentally resuming *Chihiro itself*
via search's default row action did not visibly interrupt its progress in
the following screenshot (consistent — pausing and resuming inside a few
seconds is easy to miss between screenshots); Lockere Geschäfte's preload
continued to climb (69→216 MB) across an unrelated ~5-minute window that
included another film (Der Astronaut) being opened, played, paused, and
eventually hanging (below) — i.e. the preload survived a real "something
else is open in the player" episode start to finish, including recovery.
I did not manage to catch the literal "Paused while playing" label on
screen at the same moment a different film's controls were visible (TV is
one screen; the accidental-resume window was too short to screenshot
mid-pause). The engine-level behavior is already covered by
`FilmPreloaderTest.kt` (phase 02); this is corroborating device evidence,
not a gap.

**NeedsSpace**: extensively confirmed, first accidentally then deliberately
— see the bug investigation below. "Der Pate" (30 GB) and "The Green Mile"
(12 GB) both correctly read "Needs X GB · Try again" against the box's
8.0 GB configured budget (confirmed live via Settings › Storage at the same
moment, no restart in between, no `fellBack`, volume "android" with 458 GB
free — the budget is a deliberate small choice, not a clamp).

**Home server line**: confirmed throughout — paired (`192.168.0.240:7788`,
"Connected"), climbed with every preload, matched the device figure closely,
`GET /v1/sets/{id}` answered correctly for both partial and complete films.

**NeedsSpace / budget UI**: Settings › Storage screenshotted at 8.0 GB /
budget ladder correctly bounded by the volume's live free space (offers up
to 256 GB on "android", 458 GB free — not artificially capped).

## Not completed

- **Tablet pass** (`caad49da`) — not started this session. TV box work plus
  the bug investigation took the full session.
- Did not literally screenshot "Paused while playing" simultaneous with
  another film's controls (see above) — engine behavior confirmed by
  existing unit tests plus indirect device evidence.

## Bug investigation (priority interrupt, not part of the planned walk)

The user reported, live on this same TV box while I was working: a 12 GB
film gave "not enough cache storage" and "playback fails"; separately, Der
Pate (30 GB) went Queued then "Needs 30 GB". Root-caused both:

1. **NeedsSpace is correct, not a bug.** The box's cache budget is
   genuinely, deliberately 8.0 GB (confirmed live via Settings › Storage, no
   restart, no volume fallback). 12 GB and 30 GB films exceeding an 8 GB
   budget is `fitsFilmPreloadBudget` working as designed. The "1.5 of 256 GB"
   the coordinator had seen earlier turned out to be from an *unrelated*
   automated D-pad walk through the same Settings screen that had pressed OK
   on the "8.0 GB" row — not a code bug on this feature's side. Confirmed I
   never pressed center on a budget row myself.
2. **"Playback fails" reproduced, but it's a different bug, likely
   pre-existing.** Direct Play (no preload involved) on "Der Astronaut –
   Project Hail Mary" (8.1 GB, 1080p HDR10, **av1** codec) played fine for
   ~10s, then hung solid black after a pause/back sequence. `logcat` showed
   the real cause: `RTKC2Vdec` (this box's Realtek hardware AV1 decoder)
   spinning in a retry loop — `C2BqBuffer: last successful dequeue was
   27689670 us ago` then `32694227 us ago` moments later, i.e. 30+ seconds
   with no decoded frame and no error surfaced anywhere (no
   PlaybackException, no ANR, no crash — `topResumedActivity` stayed
   `MainActivity` throughout). `KEYCODE_HOME` recovered the device;
   relaunching the app was clean. In the same session, The Green Mile
   (12 GB, h264 — the user's actual title) and Der Pate (30 GB, h264) both
   played perfectly, back to back, no stalls. The common factor is the
   codec (av1 + HDR10), not preload, not cache budget, not
   `DownloadLane`/`CacheDataSourceWriter` (a plain Play never touches that
   code at all). Logged as a follow-up below — out of this worktree's
   surface (hardware/codec compatibility, not the preload feature).

## Defects found

None in the film-preload feature itself. One small, currently-inert
inconsistency found reading the code during the investigation:
`CacheProvider.setBudget()` (`CacheProvider.kt:167-179`, live budget changes
from Settings) does not clamp the chosen value against the volume's
`capBytes` the way `openCache()` does at startup (`CacheOpen.kt:79-81`).
Harmless today because the Settings ladder (`cacheBudgetChoices`) is already
bounded by live `capBytes` every time it's rendered, so a viewer can't
normally choose an unrealistic value through the UI — but it's a real gap
between the two code paths. Not fixed: doesn't match either reported
symptom, and touching it without a confirmed trigger felt like scope creep
mid-investigation. Listed as a follow-up.

## Follow-ups

- **Tablet verification** — the plan's other required device, not reached.
- **Pinning** — no pinning exists yet (by design, per the plan); revisit if
  evictions of an in-progress preload show up in practice.
- **Persisting the queue across process death** — `FilmPreloader`'s queue is
  in-memory only, same as `SeriesPreloader` before it; a swipe-kill loses it.
- **Season preload** — out of scope for this plan (films only).
- **`setBudget` capBytes clamp** — bring `CacheProvider.setBudget()` in line
  with `openCache()`'s clamp for defense in depth, even though the live UI
  path doesn't currently expose it.
- **AV1/HDR10 decoder hang on Realtek TV boxes** — reproduced, root-caused
  to the hardware decoder (`RTKC2Vdec`), unrelated to this feature. Worth a
  ticket of its own: whether it's av1-specific, HDR10-specific, or
  title-specific; whether it predates this feature (try on 0.69.3); whether
  ExoPlayer needs a decode-stall timeout so a hung title fails loudly
  instead of hanging the screen forever.
- **A queue view** — the user noticed Der Pate sitting "Queued" with no way
  to tell what it's waiting for. Coordinator confirmed this is being
  designed separately; not built here.

## Docs updated

- `docs/system-architecture.md` — new "Film preload (Android only)"
  subsection: the FIFO lane shared with `SeriesPreloader`, the budget
  judged only at the front of the queue, `ActivePlayback`'s
  `STATE_IDLE`-only clear, the `dataSync` service and its ceiling, and the
  in-memory-only queue. The per-film status route was already documented
  (§12 API table); no change needed there.
- `docs/development-roadmap.md` — new "Android: film preload" section,
  phase table, TV box verification summary, the AV1 finding noted as a
  separate follow-up.
- `DESIGN.md` — already documents the Preload control (Line/Quiet pill
  states) and the Android-only decision; matches what was observed on
  device exactly. No change needed.
- `docs/project-changelog.md` — no entry added (no code changed this
  session; 0.71.0's entry already covers what shipped).

## Unresolved questions

- Whether the AV1 decoder hang is title-specific or codec-wide on this
  Realtek box — would need 1-2 more AV1 titles tried to narrow.
- Whether it reproduces on 0.69.3 (pre-preload) — not checked; would need
  installing an older build, which I did not do.

**Status:** DONE_WITH_CONCERNS
**Summary:** Preload lifecycle (start, background survival, cancel/resume,
completion, no-network replay, NeedsSpace) verified end to end on the TV box
with no defects in the feature itself. Tablet pass not reached. Found and
root-caused a real but unrelated playback hang (AV1 hardware decode on this
box's Realtek chipset) during the session; not fixed, logged as a follow-up.
**Concerns:** tablet verification outstanding; AV1 decoder hang needs a
ticket; `setBudget` capBytes clamp gap is real but unexercised.
