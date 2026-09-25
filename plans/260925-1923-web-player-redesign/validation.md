# Web redesign validation

Visual acceptance: rejected by the user. The checks below apply to the first
implementation only. See [the reopened plan](plan.md#revision) for the isolated
revision preview and pending design decision.

## Result

The existing player is redesigned, with its wordmark, route names, IDs,
catalog data, saved shelf modes, profiles, and playback bindings preserved.
The page uses real library artwork. Marketing-only skill rules about hero
sections, generated promotional images, logo walls and React do not apply to
this vanilla JavaScript product interface.

Owning files: `web/public/index.html`, `web/public/style.css`,
`web/public/styles/*.css`, `web/public/lib/catalog/home-view.js`, and
`web/public/lib/catalog/film-page.js`. `web/public/app.js` gains only the
skip-to-library focus handler and current-navigation accessibility state.
Pre-existing changes in those files and elsewhere are retained. Geist's
local font files include the upstream OFL license.

## Checks

- Final `cd web && bun run lint`: passed.
- Final `cd web && bun test`: 1,813 passed, 0 failed, 132 files.
- Focused shipped-HTML/player, browser application, home shelves and shelf
  rendering checks: passed. These exercise actual controller bindings,
  subtitle changes, routing, profiles, list/grid, and watch-state behavior.
- Code-reviewer pass: no findings; independently ran 47 focused tests.
- Live geometry: all eight main routes at 375, 768, 1024 and 1440px,
  in both light and dark, had no page overflow or content outside the viewport.
- Live search returned matching titles; an unmatched query showed the real
  empty state. Blocking the catalog request showed the error state, and
  removing the block plus reloading recovered. No missing CSS/JS/font assets.
- Keyboard skip moved focus to `main` without changing the route. Active
  navigation carries `aria-current="page"`. Search has a visible label.
- Featured works under reduced motion with visible text and reachable
  actions. Profile chooser fits mobile. Film Play precedes its synopsis;
  the tested 375x812 film page placed the action inside the first viewport.
- Playback control geometry inspected by opening the native dialog without
  loading a media source. No live watch progress or collection data was
  changed. Actual playback behavior is covered by the shipped-HTML tests;
  no end-to-end Telegram video stream was played during this visual review.
- Changed-file whitespace check passed. Whole-repository `git diff --check`
  also identified a pre-existing blank line at EOF in Android `KeptWall.kt`;
  that unrelated file was not edited by this task.

## Lighthouse and performance limit

Reports are local lab runs against the existing Bun server on port 8770.

| Metric | Desktop | Simulated mobile |
|---|---:|---:|
| Performance | 94 | 73 |
| Accessibility | 100 | 100 |
| Best practices | 100 | 100 |
| LCP | 1.5 s | 8.0 s |
| CLS | 0.04 | 0 |
| Total blocking time | 0 ms | 0 ms |

The mobile LCP target is not met. The trace points to the existing startup
dependency chain: about 619 KB of catalog JSON and 59 JavaScript modules
before the final library view. The stylesheet payload decreased from 65,257
to 44,654 bytes. Backend delivery and module bundling were not changed in
this visual redesign. Do not describe the mobile startup result as passing.

The browser CLI's URL-pattern wait timed out even though the expected search
route and rendered results were present. Direct inspection through the same
test browser's DevTools connection verified both search outcomes. No product
workaround was added for that tool failure.

## Screenshots and process ownership

Before: [desktop](visuals/before-desktop.png).
After: [light desktop](visuals/after-desktop-light.png),
[dark desktop](visuals/after-desktop-dark.png),
[light mobile](visuals/after-mobile-light.png),
[dark mobile](visuals/after-mobile-dark.png).
Additional detail, profile, Featured and player-control captures are in
`visuals/`. Raw audits: `lighthouse-desktop.json`, `lighthouse-mobile.json`.

Reused the user's existing server, PID 558266, port 8770, worktree
`/home/andre/Workspace/mediagram/web`. Started only the isolated
`agent-browser --session mediagram-redesign` browser (Chrome PID 622085,
daemon PID 622039, DevTools port 45709) and short-lived checks. The test
browser is closed at handoff; the server remains running.

Unresolved product questions: none. Known limitation: slow-network startup.
