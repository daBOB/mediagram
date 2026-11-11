---
name: robolectric-misses-tv-bring-into-view
description: ui-tv Robolectric tests run without FEATURE_LEANBACK, so Compose's TV pivot scroll rule never runs; scroll/under-the-bar bugs on TV pages pass CI
metadata:
  type: project
---

ui-tv Robolectric tests (`w960dp-h540dp`) get Compose's default BringIntoViewSpec, not the
leanback `PivotBringIntoViewSpec` (child to 30% of the viewport, or flush with the far edge
when the request is taller than 70%) that the TV box uses. Custom rules in `TvPage.kt`
(`StaysPutWhenOnScreen`, `revealsPageBelow`) and `TvBarClearanceBringIntoView.kt` fall back
to that pivot rule on the box only.

**Why:** 2026-10-22 review of "one frame for every page" (0.138.3): 796 tests passed while
`revealsPageBelow` still parked the title/series tab row at the 27dp overscan line, under
the 84dp bar that pushed pages now carry. Only arithmetic on the pivot rule showed it.

**How to apply:** when a TV change moves the bar, page padding or a scroll rule, compute the
landing offset with the pivot formula by hand, and ask for a box/TV-emulator walk rather
than trusting green Robolectric runs. Also: tiny test fixtures (one show, two films) leave
department arrival targets null, which hides focus-steal paths. See [[tv-compose-focus-gotchas]].
