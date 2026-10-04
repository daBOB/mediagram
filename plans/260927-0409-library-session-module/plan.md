# Library session module (architecture review candidate D)

Pull "the catalog this profile sees, kept current" out of `web/public/app.js`
into one module, `web/public/lib/library-session.js`, behind a small browser
port. Branch `refactor/library-session` (worktree `../mediagram-channel-index`).

## Decisions (user-confirmed 2026-09-27, "i follow you")

| # | Decision |
|---|----------|
| Q1 | Owns: the catalog (fetch `/api/sets`, diff by body text, kids filter, `groupLibrary`/`groupDocumentaries`, `byId`); keeping it current (`/api/events` stream opened only while the tab is visible, coalesced refresh — one read at a time, never a notice dropped); holding redraws while the player or list editing is open. Not navigation (F), not playback start / `pendingOpen` (G) |
| Q2 | A browser port (fetch catalog text, open the event stream → close, visibility) with two adapters: browser, in-memory fake; tests DOM-free |
| Q3 | Small interface: start, current, change/redraw notification, hold → release (refined below) |
| Q4 | Library-session behaviour moves from `test/browser-application.test.ts` to a DOM-free `test/library-session.test.ts`; moved cases deleted there, one app-level wiring test kept per concern; `app.js` ceiling in `test/code-standards.test.ts` lowered to its new length |
| Q5 | Fresh branch from `main`; visual check with the stub preview (`bun run preview`), never the real player |

## Interface (refinement of Q3, from reading app.js)

Counts update at once even while a title plays; only the page redraw waits
(test "progress and collection mutations update counts and defer the shelf
until close"). So two notifications, not one:

- `createLibrarySession({ port, state, remoteState })`
- `start()` — first catalog read; rejects on failure (startup shows its error)
- `current()` — `{ library, byId, visibleSets }` for the chosen profile
- `reapply()` — profile changed: re-filter the committed catalog, no fetch
- `invalidate()` — something the shelves show changed (watch state, editor's choice)
- `onData(fn)` — immediate: counts, colophon
- `onRedraw(fn)` — once per change, never while held; the last `release()` delivers one waiting redraw
- `hold()` → `release` — the player (open→close) and list editing each take one

Status reconciled 2026-10-04: completed — shipped 0.68.5 (`c7cdeeff`); `web/public/lib/library-session.js` exists on main.

## Phases

| Phase | Status |
|-------|--------|
| 01 Port + fake + failing `library-session.test.ts` | done |
| 02 Module; `app.js` delegates; moved browser tests deleted; ceiling lowered | done |
| 03 Full web suite, tsc, eslint; stub-preview check; docs + changelog; 0.68.5 | done (0.68.5, `c7cdeeff`) |

## Risks
- Ordering bugs are what this module exists to contain: every moved test must
  keep its exact assertion (coalescing count, wait-until-close, one stream).
- `app.js` keeps rendering, counts DOM, colophon, routing; the module never
  touches the DOM except through the port's visibility.
