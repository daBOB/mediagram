# The start page

The player opens on a shelf. Which shelf depends on a rule nobody asked for:
Continue if anything is half-watched, otherwise the first of Movies, Series
and Tutorials that has something in it. Both are lists of everything, sorted
by title, and neither answers the question somebody opening a library
actually has — *what should I watch now?*

Two things answer it. What arrived recently, and what was already underway.
This page shows both, and becomes where the player opens.

## 1. What it shows

`#/home`, a first entry in the masthead, and the landing route. Five rows,
each a `heading()` with a **See all** link on the right and at most six cards
under it. A row with nothing in it is not rendered — not as an empty box,
not as a heading with a sentence under it. A start page whose rows say
"nothing here yet" four times is worse than the shelf it replaced.

| Row | Holds | Ordered by | See all |
|---|---|---|---|
| Continue | half-watched titles Next up is not already showing | last watched | `#/continue` |
| Next up | one card per show or course underway | last touched | `#/series` |
| Latest films | films | arrival | `#/movies` |
| Latest series | shows | newest episode's arrival | `#/series` |
| Latest courses | courses | newest lesson's arrival | `#/tutorials` |

### Continue, and why it is mostly films

Everything half-watched, minus whatever Next up is showing. Without that
subtraction a show being watched appears twice within one screen — as the
episode in progress, and again as the same episode under Next up. One title
is one card on this page.

What survives the subtraction is films, and the occasional lesson in a course
whose position is not the course's most recent. That is the intended reading:
a series is represented once, by the row that knows what a series is.

### Next up, which is the only hard rule here

For each collection in `series` and `tutorials`, from the episodes the viewer
has touched — a recorded position, or a completion:

1. **Nothing touched** → the show is not on this row. It belongs to Latest,
   or to the shelves.
2. **Something has a resume point** (`resumeAt` is not null) → the card is
   the most recently touched of those, captioned by `resumeLine()`: *38 min
   left*. Being part-way through beats what follows something finished; the
   viewer is in the middle of that episode.
3. **Everything touched is finished** → walk forward from the most recently
   finished one with `nextAfter()` and take the first episode not already
   watched. Captioned *next up*. Skipping the watched ones matters: an
   episode seen out of order must not push the row back to it.
4. **Nothing follows** → the show is finished and drops off the row.

Ordered by when the show was last touched, which is the later of its
episodes' positions and completions.

A card plays through `play(set)`, which already finds the set's collection by
`set.show` and wires `nextAfter` into the player — so the episode after the
one on the card follows it without this page arranging anything.

### Latest, which counts arrival and not release

Arrival is when the uploader added it, which is the only date that tracks
what is new *to this library*. A show ranks by its newest episode: a series
still being uploaded keeps its place, and one finished two years ago does not
sit at the top for having been started recently.

## 2. What has to change underneath

### `addedAt` on a catalog row

`created_at` is in the index and `listPlayable` already orders by it, but the
column is not selected and `groupLibrary()` sorts by title, so by the time
the page has a library the arrival order is gone. `created_at AS addedAt`
joins `COLUMNS` in `catalog.ts`; `forBrowser()` spreads the row, so it
reaches the browser without a second change. `PlayableSet` and the browser's
`CatalogSet` both gain `addedAt: number`.

Ordering by array position instead would work today and break the first time
anything re-sorts. A rule about time should read time.

### `/state` says when something was finished

The `watched` table records `finished_at`. The read route flattens it to bare
ids, so a client can rank a show it is part-way through and not one whose
last episode it *completed* — the exact case Next up exists for. `watched`
becomes `[{ setId, finishedAt }]`, the shape `progress` already has.

Breaking, and the player is the only consumer: the Android app talks to
Telegram directly and reads none of this API.

### Two modules, because `app.js` is 600 lines

```
lib/home-shelves.js   pure. library + watch state in, the five rows out
lib/home-view.js      renders them, from setGrid / collectionGrid / card
app.js                viewHome(), the route, the nav entry
```

`home-shelves.js` holds every rule in §1 and is the only part with tests.
`home-view.js` follows `search-view.js`: a render function taking what to
draw and what to call. Neither knows about the other's job, which is what
makes the rules testable without a DOM.

## 3. What is deliberately not here

**No horizontally scrolling rails.** Six cards and a **See all** link. A rail
would be the first thing in this catalogue that scrolls sideways, and the
shelves it links to already exist.

**No new empty states.** A row with nothing in it is absent. The only empty
state is the one already written for a library with nothing in it at all.

**No "because you watched" or any other recommendation.** The library is six
hundred titles belonging to one household. Arrival and progress are the two
facts it has; inventing a third from them is inventing it.

**No Android counterpart in this work.** The phone's catalog is a different
surface with a different index behind it, and the parity rule wants it
written down rather than done silently: the start page is the web player's
until an Android phase takes it, and the phone keeps opening on its catalog.

## 4. Testing

`home-shelves.test.ts`, against the rules that can be got wrong:

- arrival ordering, and a show ranked by its newest episode rather than its
  first
- a resume point beating the episode after a finished one
- an episode watched out of order being skipped rather than re-offered
- a finished show leaving the row
- the Continue / Next up subtraction
- the cap at six, and a row that is absent rather than empty

`state-http.test.ts` gains the new `watched` shape. Then `scripts/check.sh`,
and the page in a browser against the offline stub before it is called done.
