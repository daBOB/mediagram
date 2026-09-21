# Web player start page

A landing page at `#/home`: what arrived recently, and what is underway.
Design: `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md`.

Five rows — Continue, Next up, Latest films, Latest series, Latest courses —
each capped at six cards with a **See all** link to the full shelf. Becomes
the route the player opens on, and a first entry in the masthead.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Arrival time reaches the browser](phase-01-arrival-time-on-a-catalog-row.md) | done |
| 2 | [`/state` says when something was finished](phase-02-state-serves-finished-at.md) | done |
| 3 | [The shelves, as a pure model](phase-03-home-shelves-model.md) | done |
| 4 | [The page, and where the player opens](phase-04-home-view-and-landing.md) | done |
| 5 | [Verify, document, release](phase-05-verify-and-release.md) | done |

## Dependencies

Phases 1 and 2 are independent of each other; phase 3 needs both, because
the model ranks by arrival and by completion. Phase 4 needs 3. Phase 5 needs
everything.

Nothing here touches the uploader, the index schema, or the Android app. The
state database's schema is unchanged too — `finished_at` is already stored;
only the read route's shape changes.

## Review

Built in order, as planned. Four things were decided while building that the
plan did not settle.

**The watch-state rows are plates, not an index.** `setGrid` drew a
full-width list row, which is right for the Continue shelf and wrong here:
the first two rows came out as wide rows above three rows of posters, one
page in two shapes. `setGrid` gained a `mode`, defaulting to the list every
existing caller already gets, and drops the year and the runtime from a
plate's caption for the reason `filmMeta` already drops facts from one.

**The row heading is an `h2` of its own, not `heading()`.** Five `h1`s at
`clamp(1.9rem, 4.2vw, 2.6rem)` would be five page titles stacked, to a
reader and to anything reading the outline aloud. `home-view.js` builds a
`.row-head` instead, and the page carries a hidden `h1` so it still has one.
That also meant `heading` did not have to be passed into the view at all.

**`watchedAt` needed a fallback.** A state file written before this change
serves `watched` as bare ids. The client reader takes both shapes rather
than losing every completion on the first load after an upgrade; an undated
row keeps 0, which sorts behind anything dated and still counts as watched.

**Documents needed no filtering.** The phase file flagged that a PDF must
never be offered as the next lesson; `flattenCollection` already walks only
what plays, so the forward walk is document-free without a second guard.

**Latest courses is an index, not plates** — decided twice. It was built as
plates on the argument that a row of six is not a wall of them; the browser
check showed what that actually is, a poster-shaped blank with a "G" in it
taking a third of the row's height. The Tutorials shelf is a list for that
exact reason, and the page now agrees with it.

Verified in a browser against a cache-only stub on port 8794, with state
seeded through the real API: five rows drawn, Continue holding the two films
and not the show underway, Next up captioning a part-watched episode "68% ·
15:00" and the episode after a finished one "Next up", every See all link
arriving at its shelf, and a Next up card opening the player on 30 Rock S1E2
— the episode after the one marked finished. No horizontal scroll at 375px.
`scripts/check.sh` passes. Version 0.18.0.

Not done, deliberately: the Android app keeps opening on its catalog. The
spec says why, and a phase there would need the phone's own index.
