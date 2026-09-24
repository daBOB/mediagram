# Browser playback and catalog contract pre-review

Scope: pending browser changes after `67ed2bce`, for the three fifth-assessment
findings. This is a requirements and edge-case check, preceding independent
quality review; it is not the final review verdict.

| Requirement | Evidence |
| --- | --- |
| Handle manual play rejection and retain retry | Transport's only production caller is player.js, which supplies a guarded async play handler; both button and keyboard share it. The handler consumes rejection and displays controlled text. |
| Ignore interrupted or obsolete requests | The source controller and source object identify ownership; a request counter excludes older manual completions and explicit pause. Tests cover another title, reopened same title, closed player, audio replacement, AbortError, old success and old failure. |
| Preserve useful existing player feedback | A successful retry restores the prior explanation only when the note still contains that manual failure. Source stop discards the prior notice. A conversion explanation regression covers restoration. |
| Describe real browser catalog records | Added presentation fields match `catalog/routes.ts`'s shared catalog/search projection; raw media fields match `catalog.ts`. Nullable fields stay nullable; arrays and booleans are always provided. |
| Keep view callbacks and metadata contracts explicit | Home/grid callbacks use their actual set/collection arguments. Series metadata uses ShowMeta; pure helpers accept bounded Pick/Partial views rather than untyped object. |

One declaration consistency correction was requested and completed: summarize
accepts a divisions-only collection, and flattenCollection's declaration now
accepts that same valid runtime shape. The implementation reads only divisions.
This broadens the accepted parameter and changes no runtime behavior. TypeScript
passes after the correction. Requirements check: PASS, ready for quality review.

Relevant flow: transport.js → player.js source ownership; library.d.ts →
library.js grouping → series-summary/header and shelf/home/plate rendering;
catalog/routes.ts → the browser record projection. Native searches confirm there
is only one production mountTransport caller, updated in the same change.

The combined web gate before the declaration correction passes **1,707 tests**,
12,082 assertions across 127 files (`/tmp/web-seventh-full-tests.log`). Browser
focused and mutation evidence is recorded by its implementer; the final review
must still inspect those records and any subsequent correction.
