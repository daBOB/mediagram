---
title: "Android Settings/System redesign (B two-pane) on the web player's tokens"
description: "Move Android design tokens to the web look, add the web's Artwork setting, rebuild Settings+System as a two-pane index on phone/tablet and TV."
status: pending
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
| 04 | [TV two-pane Settings](phase-04-tv-settings-two-pane.md) | ui-tv system/*, TvMenuBranches | 4.5h / 2.5d | pending |
| 05 | [Verify, docs, version](phase-05-verify-docs-version.md) | tests sweep, docs/, manifests | 2.5h / 1d | pending |

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
