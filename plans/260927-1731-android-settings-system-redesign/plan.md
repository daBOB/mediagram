---
title: "Android Settings/System redesign (B two-pane) on the web player's tokens"
description: "Move Android design tokens to the web look, add the web's Artwork setting, rebuild Settings+System as a two-pane index on phone/tablet and TV."
status: complete
priority: P2
effort: 21h
branch: main
tags: [android, designsystem, settings, system, tv, parity, artwork]
created: 2026-09-27
---

# Android — Settings/System redesign + web tokens + Artwork

Base: `main` @ f98dec21 (0.68.13). Surface-parity work: `web/public` is the reference
(CLAUDE.md § Surface Parity). Presentation + tokens + one new setting; every ViewModel
stays. No Rust change, so the native `.so` does not need rebuilding.

## Decisions (user, 2026-09-27 — do not reverse silently)

1. **Design = "B · Two-pane index" in the web player's current look** (not DESIGN.md's
   ink catalogue). Approved mockups: `~/.gstack/projects/daBOB-mediagram/designs/
   android-settings-system-20260927/round2/b-{telegram,storage,appearance,system}.png`
   + `round2/tokens.css`. Index (Telegram · Storage · Appearance · System, one-line
   status, selected row filled) + page (huge uppercase Fraunces title, Geist eyebrow).
   Cache + home cache server = one **Storage** section; **System is an entry of
   Settings**, and the menu keeps a System shortcut that opens it.
2. **Tokens app-wide to the web look** (dark neutrals, Geist interface face, 6dp
   radius; light palette + accents kept — verified equal to the web, phase 01), then
   Settings/System on phone/tablet and TV; root `DESIGN.md` rewritten. Other screens
   take the new colours as they fall; anything off is **listed, not fixed**.
3. **Port the web's Artwork setting** (Default/Blurred/Artwork/Solid). Supersedes the
   2026-09-26 "not ported" decision (`plans/260926-1330-…/plan.md:38`,
   `AppearanceSection.kt:33`).

## Phases

| # | Phase | Owns | Agent / human | Status |
|---|-------|------|---------------|--------|
| 01 | [Web tokens, Geist, 6dp radius, DESIGN.md](phase-01-web-tokens-geist-radius-design-doc.md) | core/designsystem, app res, DESIGN.md | 3h / 1d | complete |
| 02 | [Artwork setting: model, storage, hero rendering](phase-02-artwork-setting-model-and-rendering.md) | designsystem Appearance, feature/setup VM, ui-common art, hero sites | 4h / 1.5d | complete |
| 03 | [Settings + System, phone and tablet (B)](phase-03-settings-system-phone-tablet.md) | ui-mobile settings/system, ui-common settings, status fns | 7h / 3.5d | complete |
| 04 | [TV two-pane Settings](phase-04-tv-settings-two-pane.md) | ui-tv system/*, TvMenuBranches | 4.5h / 2.5d | complete |
| 05 | [Verify, docs, version](phase-05-verify-docs-version.md) | tests sweep, docs/, manifests | 2.5h / 1d | complete |

Order changed from the suggested one (Artwork moved to 02): the Settings rebuild's
Appearance section and the TV block both need the Artwork model; building it first
avoids drawing Appearance twice.

## Dependencies

01 → 02 → 03 → 04 → 05. 04 may start once 03's shared commit (step 1: section enum,
status functions, icons, ledger) lands — no file is owned by two phases (see each
phase's "Related code files"). Implement on a branch/worktree: another session is
landing on `main` (0.68.14 in flight); rebase before each phase.

## Rollback

One commit (or a small series) per phase; `git revert` of a phase restores the previous
look. No data migration: the only new persisted value is `backdrop` in the existing
`appearance_settings` prefs file, ignored by older builds. Menu frame payloads
(`System`/`Settings`/`TmdbKey`) are unchanged, so saved navigation stacks restore.

## Answered (user, 2026-09-27)

- System polls every 2 s while visible, like the web; the eyebrow stays as approved.
- Start after I (catalog reads) merges, in its own worktree.

## Open questions (only what code cannot decide) — resolved above

1. **System eyebrow vs behaviour.** The approved mockup says "What this player is doing,
   refreshed as it happens" (the web's line, `app.js:541`; the web polls every 2s,
   `lib/status/status-lines.js:14`). Android's System is a per-visit snapshot by design
   (`SystemViewModel.kt` doc). Either poll while System is visible (3 lines; reads are
   cheap: `CacheProvider.occupancy` is an in-memory counter, `CacheProvider.kt:145-157`)
   or keep the snapshot and reword the eyebrow. Recommendation: poll at 2s, like the web.

## Success (plan-level)

Four tablet screenshots match the round-2 mockups (layout, tokens, type); compact and TV
flows pass their Robolectric tests; all module unit tests green; follow-up list for
other screens filed in phase 05's report; version bumped minor in all three manifests.

## Review

Closed 2026-09-27. Commits `5ae998e3` (0.69.0, phase 01: web tokens, Geist, 6dp radius,
`DESIGN.md`), `72b2dfa8` (0.69.1, phase 02: Artwork model and hero rendering),
`225b3bd6` (0.69.2, phase 03: Settings/System, phone and tablet, plus a same-day
tablet-check fix round), `f7a89f83` (0.69.3, phase 04: TV two-pane Settings, plus a
same-day review-fix round covering 9 findings) — all on `feat/android-settings-redesign`
in this worktree, none yet merged to `main`. Phase 05 closes the plan at 0.69.4.

### What shipped

- **Tokens** (phase 01): the app-wide palette, Geist interface face and 6dp control
  radius now match the web player's own dark theme, verified equal by
  `PaletteContrastTest`; root `DESIGN.md` rewritten from the shipped catalogue screen.
- **Artwork** (phase 02): the web's Default/Blurred/Artwork/Solid setting, ported to
  every hero (title pages, department cover stories, Home) on phone, tablet and TV.
- **Settings + System** (phases 03–04): one two-pane index — Telegram, Storage,
  Appearance, System — replacing the old single-scroll screens everywhere. Phone and
  tablet get Material back-stack rules per width class; television gets its own
  D-pad focus, scroll and Back rules, reviewed and fixed against 9 findings before
  0.69.3 (`plans/reports/code-reviewer-260927-2110-tv-settings-two-pane-review-report.md`,
  `plans/reports/fullstack-developer-260927-2125-tv-settings-review-fixes-report.md`).
- **This phase** additionally fixed one regression the tablet verification pass
  surfaced: `ui-mobile`'s `SettingsPage` kept one `rememberScrollState()` across a
  section switch at expanded width, so opening Storage after scrolling Appearance
  landed already scrolled down instead of at its own top — the same class of bug
  `TvSettingsPanes.kt` had already been fixed for in the phase 04 review round.
  Fixed the same way, keyed on `section`
  (`android/ui-mobile/src/main/kotlin/ui/settings/SettingsPage.kt`), with a
  regression test in `SettingsPanesTest.kt`. This is a real behaviour change, not a
  docs-only close — recorded honestly in `docs/project-changelog.md`'s 0.69.4 entry
  rather than folded in silently.

### Verification

- `./gradlew testDebugUnitTest lint` (whole project): green. 1526 Android-module unit
  tests plus 9 in `core:model`'s own JVM suite, lint clean across every module.
- Tablet (`caad49da`, test profile): fresh install, all four Settings sections
  screenshotted in landscape and compared against the round-2 mockups structurally —
  two-pane layout, tokens, page-title weight (the phase-03 static-font fix holds on a
  second device pass) all match. Artwork modes Default/Blurred/Solid screenshotted on
  a film's title page and the Movies department; Solid correctly drops the hero art,
  its gradient and the department's own pull-quote on both. Artwork reset to Default
  and the app left on the test profile afterward, as the household profile was not
  touched. TV verification for this closing pass reused phase 04's own review-fix
  round (`plans/reports/tv-*.png`, real TV box, 192.168.0.35:5555) rather than
  repeating it — the device work stayed tablet-only and short, since another session
  needed it next.
- Screenshot of the Telegram section (account name, session locations, IPs) was kept
  out of `plans/reports/` per this plan's own Security note and lives in the session
  scratchpad instead; every other screenshot is in `plans/reports/`.

### Follow-ups (listed, not fixed — decision 2)

The phase 05 spec's own eleven items stand unchanged (see `phase-05-verify-docs-
version.md` § Known follow-ups), with two annotated below since they also touch the
home screen, which is out of scope here — it is being redesigned on another branch
and gets its own web-parity pass there. Three items this session's sweep and the
phase reports themselves surfaced are added as 12–14:

- Item 3 (department hero default is words-over-art, not art-fading-into-the-page)
  and item 4 (hero scrims fade to black even in light theme) both name
  `CoverStory.kt`, which Home's own hero draws through — the Home-screen half of
  each is addressed by the home web-parity plan; the non-Home half (Movies/Shows
  department hero, and department-only surfaces like `DepartmentHero.kt`/
  `TvCoverStory.kt`) stays open here.
- **12. Prose still set in Geist where the web sets it in Newsreader.** These read
  overview/synopsis or note text through `MaterialTheme.typography.bodyMedium`
  (Geist 15sp) rather than `bodyLarge` (Newsreader 17sp, this catalogue's one
  deliberate reading-face exception): `TitleDetailScreen.kt:224`,
  `TitleSpread.kt:98` (facts line) and `:106` (overview), `CoverStory.kt:171`,
  `NotesPanel.kt:97`. Television's own body copy (`TvTypeScale.body`,
  `core/designsystem/src/main/kotlin/Type.kt:230`) is Geist by its own documented
  design rather than an oversight — its KDoc calls this the same swap the phone
  make elsewhere — but that swap disagrees with the web's own Newsreader overview
  text the same way the phone instances above do, so it is listed alongside them
  rather than assumed correct.
- **13. Storage's index-row status truncates mid-word.** `storageStatus` in
  `android/feature/system/src/main/kotlin/LanCacheStatusLine.kt:47` returns
  `"$held · home cache connected"` as one string; the Settings index row has no
  room for it at phone width and clips to "home cache co…". Worth either a shorter
  string or a two-line row.
- **14. `DESIGN.md`'s Typography § Character claims Newsreader is "loading, empty
  and failure states, nothing else."** Item 12 shows that is no longer accurate —
  the web's own overview/synopsis text is Newsreader too. Left as found rather than
  edited in this pass, since fixing the sentence without fixing the four Kotlin
  call sites in item 12 would make the doc claim a behaviour the app does not have.

Fourteen items total, none fixed in this phase per decision 2; none are Settings
regressions, all are pre-existing or general-catalogue token gaps outside this
plan's own file ownership.

### Docs

`DESIGN.md` (top app bar exception, a new Artwork component entry, the "not yet
composed as Kotlin" caveat corrected), `docs/system-architecture.md` (a new
Settings/System structure subsection, the TV Appearance bullet corrected to name
Artwork too), `docs/project-changelog.md` (0.69.4 entry), `docs/development-
roadmap.md` § Android (new Settings/System redesign section, phase 6 of the
television plan's own table annotated superseded), and
`plans/260926-1330-android-editorial-departments-parity/plan.md:38` (superseded
note on the "Artwork not ported" decision).
