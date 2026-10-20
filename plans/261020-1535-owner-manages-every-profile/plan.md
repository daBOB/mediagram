# The owner manages every profile — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Request (2026-10-20):** "i cant edit profile celine". Celine runs the household Madani; the owner (andre) is in Home.
Asked what the owner should be able to change on anyone in another household, the user chose all four: **reset their
PIN, rename them, hide sections, remove them.**

**Today (main 0.132.0):** the shared profile rule (`web/test/fixtures/watch-state/profile-rules.json`, ported to
`web/src/state/profiles-rules.ts`, `crates/mediagram-core/src/state/profiles/rules.rs`,
`android/core/model/.../ProfileRoles.kt`) says nobody manages a profile in another household, and whoever runs a
household never has sections hidden (`section-access.js` `hiddenSectionsOf`, Android `Sections.kt` `hiddenFrom`).
Manage profiles lists only this device's household. No profile can be renamed anywhere.

## Decisions
User's: the owner can reset the PIN of, hide sections from, and remove **any** profile on the install, in any household,
and rename them (rename: see "Not in this plan").

Controller's calls (provisional; the user may change any):
1. **The owner is never the target of their own owner powers**: the owner is not removed, has nothing hidden, and
   changes their own PIN as today ("Your PIN").
2. **A household's admin may now have sections hidden — by the owner only.** The filter's rule becomes "the owner sees
   everything; everyone else sees what their list leaves". A household's admin still cannot hide their own sections.
3. **The owner also gets a kid's age limit and daily limit** in any household: those controls sit on the same Manage row,
   and offering half a row would read as broken.
4. **Where:** Manage profiles, as the owner, lists every household's profiles, grouped under each household's name
   (this device's household first), with the rows and actions Manage already has. Anyone else's Manage is unchanged.
5. Removing a household's admin leaves that household with nobody running it — as moving its last grown-up out already
   can; the owner then sets a new admin, or the household is empty and can be deleted.

## Not in this plan
**Renaming a profile.** Sync identifies a viewer by its normalised name across devices (`web/src/state/sync-record.ts`
`ProfileState.name`); renaming one is renaming its identity on every device, and a device on an older build would see
the new name as a new viewer and keep the old one. It needs its own design and the user's call; asked separately.

## Global Constraints
- The web is the reference (CLAUDE.md § Surface Parity). The rule changes in the shared fixture and all three ports in
  one task, so no surface disagrees; the Android Manage UI follows in the Android households phase (06), which shows
  other households to the owner the same way — say so in that phase's brief.
- Line limits: new files ≤200; ratcheted web files grow only with a dated sentence; Rust `src/` ≤200; Kotlin over 200
  must not grow.
- Commits conventional, no AI references, no plan labels. Never `git stash`. One minor version bump at the merge.
- Branch `feat/owner-manages-all`, worktree `../mediagram-owner`, from `main` d21ad473.
- UI checks: the preview harness on a scratch copy (PIN 1357 for the owner in the copy only), gstack browse.

## Tasks

### Task 1: The rule, in the fixture and all three ports, and the section filters
**Files:** `web/test/fixtures/watch-state/profile-rules.json` (new cases), `web/src/state/profiles-rules.ts`,
`crates/mediagram-core/src/state/profiles/rules.rs`, `android/core/model/.../ProfileRoles.kt`; the filters
`web/public/lib/section-access.js` `hiddenSectionsOf` and `android/core/model/.../Sections.kt` `hiddenFrom` (owner sees
everything; everyone else their list) — the Android `Profile` model gains `owner` if it lacks it (mapped from the core
record in `core/data/.../CoreProfileCalls.kt`); the web/Kotlin `manageable` offer lists follow the rule.
**Cases (fixture):** the owner may `set-pin`, `remove`, `set-access`, `set-kids-age` (and the daily limit's action) on
a grown-up or kid in another household, including that household's admin; the owner may not remove themselves or hide
their own sections; a household's admin still may not act across households nor hide their own sections; everything
that was allowed stays allowed.
**Tests:** the three fixture runners; `section-access.test.ts` (an admin with a list now has it applied; the owner never);
Android `Sections` test likewise.
**Commit** `feat: the owner manages every profile on the install`.

### Task 2: Web — the server honours it, and Manage shows everyone to the owner
**Files:** `web/src/state/profiles-manage.ts` / `profiles-access.ts` (if any check bypasses the rule across households,
e.g. a household guard of its own), `web/public/lib/profile-api.js` `manageable` (the owner's view = `everyone()`),
`web/public/lib/profile-manage.js` (grouped under household names, this device's first; words from `household-words.js`).
**Tests:** HTTP: the owner resets Celine's PIN, hides a section from her, removes her (in another household); a
household's admin still cannot; Manage as the owner lists every household grouped; as anyone else unchanged.
**Preview walk:** as the owner, reset a PIN, hide a section, remove a profile in another household; screenshots
desktop + 390 px into `reports/`.
**Commit** `feat(web): the owner manages every profile from Manage profiles`.

### Task 3: Docs, version, merge
Changelog, `docs/web-player.md`, minor bump, full `scripts/check.sh`, merge with the user's word.
