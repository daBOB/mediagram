# Web player redesign

Status: reopened. The user rejected the visual design as generic. The technical
checks in [validation.md](validation.md) describe the rejected version, not
visual acceptance.

## Revision

- [x] Compare the original and rejected screens. The sidebar, boxed progress
  rows, green tint, and generic introduction overwhelm the media identity.
- [x] Produce one isolated home-screen preview using actual library content.
- [ ] Resolve the visual direction with the user before applying it throughout.
- [ ] Implement and verify the accepted direction.

The preview changes no catalog, watch state, or application source. In the
absence of a selected direction, explore a dark poster-led library with a
compact horizontal navigation and no introductory marketing copy. Keep all
existing navigation destinations and operational state visible. Use native
CSS, design variance 3, motion 1, density 6. Acceptance for this step is a
desktop/mobile image pair showing actual catalog content without overflow.

Lesson: passing functional and accessibility checks does not establish visual
quality. After a rejected redesign, show a bounded visual revision before
another broad implementation.

Preview: [desktop](visuals/revision-desktop.png),
[mobile](visuals/revision-mobile.png). Styles are in
[revision-preview.css](revision-preview.css), injected only into an isolated
browser; the search/profile toolbar was moved into the masthead in that browser
DOM. The application source remains at the rejected version. Both 1440x1000
and 375x1000 were visually inspected: no page overflow, all 15 existing artwork
images loaded. These are visual previews, not a full functionality validation.
The direction question is unanswered as of this preview; dark mode is an
exploration, not a recorded user preference.

Reused server PID 558266 on port 8770. Preview browser session
`mediagram-revision` used Chrome PID 762698, daemon PID 762649, DevTools port
46707, temporary profile
`/tmp/agent-browser-chrome-0ef70ca9-59a5-4d52-a84f-44fbd737425f`.
The preview browser is closed at handoff.

## Outcome

Redesign the existing web interface as a readable, artwork-led personal media
library. Keep the Mediagram wordmark, navigation labels, routes, live data,
profile behavior, collection editing, and playback contracts intact.

## Baseline and direction

The live player runs on port 8770. Its 1440px baseline is captured in
`visuals/before-desktop.png`. The existing narrow paper-and-serif catalog leaves
large unused margins, wraps search below the navigation, and shows muted text
at 3.30:1 contrast. Brand: Fraunces wordmark, warm red selection accent, square
posters. Navigation: Home, Movies, Series, Tutorials, Continue, Watchlist,
Collections, and local-only System. Hash routes remain unchanged. This private
application has no public SEO migration or marketing conversion path.

Use neutral light and graphite dark surfaces following system appearance,
Geist for interface text, the existing red hue for selections and primary
actions, and Fraunces only for the preserved wordmark. Compact persistent
navigation on desktop, a wrapping navigation layout on mobile, larger usable
content width, and compact Continue/Next up cards above full poster shelves.
Real catalog artwork supplies the imagery. No fabricated promotional assets.
Design variance 4, motion intensity 3, visual density 5.

## Work

1. Done: separate theme, shell, catalog, and collection/profile styling along their
   actual boundaries; retain player behavior and modernize its controls.
2. Done: recompose the shell and home shelves, improve title-detail hierarchy, and
   provide accessible search and visible loading feedback.
3. Done: check desktop/mobile, light/dark, keyboard navigation, list/grid, details,
   profiles, search, collections, featured, and playback markup contracts.
4. Done: run focused browser/application tests, web lint and tests, and Lighthouse.
   Review the diff and document the shipped appearance in the owning docs.

## Acceptance and safety

- No page-level horizontal overflow at 375, 768, 1024, and 1440px.
- Both color schemes render legibly; normal text meets 4.5:1 contrast.
- Navigation labels, IDs consumed by controllers, forms, and hash routes stay
  stable. Existing user work is preserved.
- No backend changes, new UI framework, fake data in shipped code, or Android
  redesign. Existing list/grid preference semantics remain intact.
- Tests exercise the real shipped HTML and player controls. Live browser
  validation does not write playback progress or alter the user's lists.
- Reuse the existing server; close only the browser/processes started here.

Rollback: restore this task's CSS, template, and home-view changes, retaining
pre-existing edits. No database or API rollback is needed.
