# TV feedback, 2026-10-10 (user on the box, 0.119.2 sideload, dexopt verify)

| # | User said | Status | Where |
|---|---|---|---|
| 1 | "On tv the sidebar looks still too much margin" | done with #6 in fe85a285 + 0.120.1 bump f55ab9e1 (TvScale = 960/1280 in designsystem/TvTypeScale.kt); follow-up running: symmetric right edge + in-page spacing via TvScale | plans/reports/tv-rail-margin-parity-261010-report.md |
| 2 | "Why is the top bar not frosted glass" | user chose: measure on the box, then frost. TV frost built (feat/frosted-masthead f4522beb, shared Frost.kt in ui-common, frosted on every tab like web/tablet); measure on the box after 0.120.1 lands, comparing 0.120.1 vs 0.120.1+frost in one box session — ask the user first | feat/frosted-masthead (phone/tablet built, TV left solid on purpose) |
| 3 | "Going from the top bar down to entries it's a hard jump" | queued after #1 (same worktree) | — |
| 5 | "The font is still old on tv" | audit done: display titles on the opsz-28 Fraunces cut, synthetic italic quotes, wall heads in Fraunces not Geist 600, meta at 500 not 400, small ones; fix after the scale change | plans/reports/tv-font-audit-261010-report.md |
| 6 | "The font size is still too large" | user chose one TV scale for text AND spacing, a quarter smaller: 1 web px = 0.75 dp (web read at a 1280 px viewport); folded into #1's agent with one constant; ships with #1 as 0.120.1 | tv-rail-margin-parity-261010-report.md |
| 4 | "The loading indicator should be cinema related" | user chose: countdown leader (ring + sweeping hand + serif 3·2·1, words beneath, still on 3 under reduced motion), everywhere: web, phone/tablet, TV | motion plan Task 7.3 amendment + web |

Box facts at 04:37: awake, MainActivity resumed, versionName 0.119.2 (batch 1 sideload, 17:30 on 10-09), dexopt `verify` (uncompiled → slower than a channel update). 0.120.0 (compiled, with profile) installs on its own at the app's next onStop.
Never send keys while the user is on the box; ask before taking it over for measurements.

## Rulings on the font audit's open questions (controller, 2026-10-10)
1. Meta weight 400: fix it at the shared style if that is one place (phone and TV follow, parity); if it really needs the 34 phone call sites, do the TV now and leave the phone as a follow-up (feat/motion is editing ui-mobile heavily).
2. Spread headline opsz 92: 112 for normal, 72 for long, as proposed; check on the box.
3. Shelf head: use the nearest cut that already ships; no new font file unless the mismatch is plainly visible.
4. Tracking reset: in scope only if it is a one-line style fix per role; otherwise a follow-up.
5. Latin-ext coverage: out of scope; follow-up.
6. Quotes' italic: ship a real Newsreader italic (copy the web's own font file if web/public carries one; OFL), replacing the synthetic slant on TV and phone.
Order: after #1/#6 (the scale change) lands, in the same worktree.
