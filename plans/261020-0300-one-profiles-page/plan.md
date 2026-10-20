# One Profiles page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Request (2026-10-20):** "why do i have 4 different profile setting pages". The user chose **one Profiles page** (answer to "Where should profiles live?"): *Settings gets a single "Profiles" page: you (who's watching, switch, your subtitles), then everyone, then managing. The picker only chooses who's watching; its "Manage profiles" button opens that same page. Profile + Profiles + the separate manage screen become one.* This supersedes the 2026-10-20 rename to "My profile" / "Household" (dropped before merge) and plan decisions 1–2 of `plans/261020-0107-profiles-page-and-access/plan.md` (tab grown-up-only; Manage kept on the picker as its own screen).

**Today (on `main`, 0.128.2):** four places — the picker ("Who's watching?"), the picker's Manage profiles screen (web overlay `profile-manage-overlay.js`; phone `ProfileGate` → `ManageProfilesScreen`; TV `TvProfileGate` → `TvManageScreen`), Settings → Profile (web `settings-page.js` `profilePanel`: who is watching + Switch profile + subtitle language + System link; Android `SettingsSection.PROFILE`, `ProfileSettingsSection`, TV `TvProfileSection`), and Settings → Profiles (web `settings-profiles.js`; Android `SettingsSection.PROFILES`, `ProfilesSettingsSection.kt`/`ProfilesVisit.kt`; TV `TvProfilesSection.kt` + its Manage panel).

## The page (decisions; the user's choice, details the controller's)

1. **One Settings entry, "Profiles"**, on the web (a tab), phone/tablet and TV (a section). Three parts, top to bottom:
   - **You** — who is watching, Switch profile, your subtitle language (today's Profile content, same words). Shown whenever someone is chosen, kid or grown-up.
   - **Everyone here** — today's list, same words.
   - **Manage profiles** — today's manage body (who are you + PIN, then what your role allows), same words.
   Everyone + Manage are hidden while the chosen profile is a kid (a kid manages nothing), as today.
2. **The picker only chooses.** Its "Manage profiles" button opens this same page (the same component), with nobody chosen: the **You** part is absent, Everyone + Manage are shown. Leaving it returns to the picker. No separate manage screen remains: the web overlay and the phone/TV picker-only Manage routes go; the shared bodies stay.
3. **The PIN rule is unchanged:** every visit asks who you are and the PIN; leaving the page, Settings or the app drops it.
4. **Settings → Profile disappears** (its content is the You part). The index status line of Profiles reads `<chosen name> · <n> profiles` (just `<n> profiles` with nobody chosen).
5. **Same words everywhere**, reused from where they live today; nothing retyped.

## Global Constraints

- The web is the reference (CLAUDE.md § Surface Parity); Android matches its decisions.
- Line limits: new files under 200 lines; ratcheted web files grow only with a dated sentence (`web/test/code-standards.test.ts`); Kotlin files over 200 must not grow.
- Commits conventional, no AI references, no plan labels in code, test names or messages. Never `git stash`.
- UI checks: web preview harness (`cd web && bun run preview`) with gstack browse; devices pinned by serial (Pad 5 Pro `d54bc94f`, tablet `caad49da`, box `192.168.0.56:5555`), test profiles only.
- Version: one bump at the merge, the next patch above `main`.
- Branch `feat/one-profiles-page`, worktree `../mediagram-onepage`, from `main` 54a2a9d4.

## Tasks

### Task 1: Web — one Profiles tab, the picker opens it
**Files:** `web/public/lib/catalog/settings-page.js` (tabs: Appearance, Profiles, Library & Telegram; Profiles holds `profilePanel`'s content as **You** above `settings-profiles.js`' Everyone + Manage), `settings-profiles.js`, `web/public/lib/profile-picker.js` (Manage profiles → the Settings Profiles tab with nobody chosen), `profile-manage-overlay.js` (removed if unused), `app.js` (route so Settings → Profiles can show over/instead of the picker with nobody chosen; back returns to the picker), tests (`settings-page.test.ts`, `settings-profiles.test.ts`, `profile-manage.test.ts`, picker tests).
**Tests:** one Profiles tab, no Profile tab; a kid sees You only; a grown-up sees You + Everyone + Manage; nobody chosen (from the picker) sees Everyone + Manage and no You, and leaving returns to the picker; the PIN is dropped on leaving.
**Preview walk:** screenshots desktop + 390 px of Settings → Profiles as a grown-up and from the picker, into `plans/261020-0300-one-profiles-page/reports/`.
**Commit** `feat(web): one Profiles page in Settings, which the picker opens too`.

### Task 2: Phone and tablet
**Files:** `ui-common/.../settings/SettingsSections.kt` (PROFILE goes; PROFILES titled "Profiles"), `ui-mobile/.../settings/*` (the page: You from `ProfileSettingsSection`, then Everyone + Manage), `ui-mobile/.../profile/ProfileGate.kt` (the picker's Manage profiles opens the same page with nobody chosen; no separate ManageProfilesScreen route), tests.
**Commit** `feat(android): one Profiles page in Settings on phone and tablet, which the picker opens too`.

### Task 3: TV
**Files:** `ui-tv/.../settings/*` (one Profiles section: You from `TvProfileSection`, Everyone, the Manage profiles row → the TV's manage panel as today), `ui-tv/.../profile/TvProfileGate.kt` (the picker's Manage profiles opens the same page with nobody chosen), tests with real D-pad keys. Mind the TV focus traps (memory `tv-compose-focus-gotchas`).
**Commit** `feat(android): one Profiles page in TV Settings, which the picker opens too`.

### Task 4: Docs, version, merge
`docs/web-player.md` Settings row; changelog entry; version bump; merge to `main` with the user's word.
