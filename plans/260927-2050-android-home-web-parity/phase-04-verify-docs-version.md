# Phase 04 — Verify on tablet, docs, version

## Overview

Priority P2 · completed 2026-10-04 — see `reports/home-web-parity-tablet-report.md`. Close the plan: full test sweep, tablet walk-through against the
web, docs, follow-up list.

## Steps

1. `./gradlew :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest
   :feature:catalog:testDebugUnitTest :core:data:testDebugUnitTest
   :ui-tv:testDebugUnitTest` — all green.
2. Tablet (`ANDROID_SERIAL=caad49da`, test profile): landscape and portrait —
   home at three scroll depths, each department tab, rail destinations, search,
   profile chooser, ⋮ actions, back from a pushed page. Side-by-side sheet with the web
   stub preview at the same width → `reports/home-web-parity-tablet-report.md`.
3. Compact: Robolectric at the default width plus the tablet in portrait
   (medium) — no phone device is attached; say so in the report.
4. Docs: `DESIGN.md` (chrome + home sections), `docs/project-changelog.md`,
   `docs/system-architecture.md` if the chrome seam is worth a line. Remove the
   "no Documentaries" note wherever it was recorded.
5. Follow-ups list in the report (at least: TV home to the same layout; the web's
   right-hand cover genre column above 1180dp; backdrop blur behind the bar).
6. Patch bump if anything changed, commit. Merge to `main` only when the user says.

## Todo

- [x] test sweep
- [x] tablet walk-through + side-by-side report
- [x] compact check
- [x] docs
- [x] follow-ups listed
- [x] version + commit

## Success criteria

The user can put the tablet next to the web player and find the same home, same
chrome, same departments; every difference is written down with its reason.
