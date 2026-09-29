# Phase 08 — Verify, docs, version

## Context links

- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md`
- Contract: [shared-contract.md](shared-contract.md)
- Docs that describe today's kids profile and must change:
  `docs/system-architecture.md:392-425` (§ Kids profiles: "FSK 12", "a filter,
  not a lock", "the kids flag is therefore permanent", "the phone cannot remove
  profiles"), `docs/development-roadmap.md` (sections end at `:222` Android
  Settings/System, `:250` deferred), `docs/project-changelog.md` (one entry per
  release, newest first, `## X.Y.Z — title` then **Added/Changed/Fixed**).
- Release commits: subject ends `; release X.Y.Z` (e.g. `ca3137b4`), and touch
  `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts`
  (`versionName` only — `versionCode` is not part of it, CLAUDE.md § Versioning),
  and `docs/project-changelog.md`.

## Overview

Priority P2. Status: pending. Two jobs: (1) the **Bumping** procedure every
earlier phase's commit step points at; (2) closing the plan — whole-project
checks, both surfaces by eye, docs, the spec amendment, the plan's Review section.

## Bumping (used by every commit in phases 01–07)

Another session commits to `main`, and other machines release too — so the
version is read, never assumed, and bumped by pattern.

- [ ] **Step B1: learn the real current version, local and remote**

```bash
cd /home/andre/Workspace/mediagram
git fetch -q origin
git show HEAD:Cargo.toml | grep -m1 '^version'
git show origin/main:Cargo.toml | grep -m1 '^version'
```

Take the higher of the two as `cur`. If `origin/main` is ahead, rebase first.

- [ ] **Step B2: bump all three manifests to `next`**

`next` is minor (`X.Y+1.0`) for the first commit of this feature (phase 01's
first commit) and patch (`X.Y.Z+1`) for every commit after it.

```bash
next=0.70.0   # the value computed in B1 — never copied from this file
sed -i -E "0,/^version = \"[0-9]+\.[0-9]+\.[0-9]+\"/s//version = \"$next\"/" Cargo.toml
sed -i -E "0,/\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"/s//\"version\": \"$next\"/" web/package.json
sed -i -E "s/versionName = \"[0-9]+\.[0-9]+\.[0-9]+\"/versionName = \"$next\"/" android/app/build.gradle.kts
cargo update -w -q
```

- [ ] **Step B3: read the result back — all four must say `next`**

```bash
grep -m1 '^version' Cargo.toml
grep -m1 '"version"' web/package.json
grep versionName android/app/build.gradle.kts
grep -A1 -E '^name = "(mediagram|mediagram-cache|mediagram-core|mediagram-tmdb|mlib-spec)"$' Cargo.lock | grep version
```

Expected: every line shows `next` (three manifests plus five workspace crates in `Cargo.lock`). If any does not, stop — do not commit.

- [ ] **Step B4: changelog entry** at the top of `docs/project-changelog.md`,
  `## <next> — <short title>`, with **Added** / **Changed** / **Fixed** bullets
  written for someone who uses the player, not for someone reading the diff.
  The commit subject ends `; release <next>`.

## Key insights

- `docs/system-architecture.md:408-423` states three things this feature makes
  false: kids is "a filter, not a lock" (now: a filter, plus a PIN on the way
  back to a grown-up), the kids flag is permanent (still true for `kids`; the
  limit is not), and "the phone cannot remove profiles" (it can, with a PIN, by
  role). Each needs rewriting, not appending.
- The spec's `POST /api/profiles` body omits the new grown-up's first PIN;
  the contract (§8) adds `newPin`. The spec is amended here so the two agree.

## Requirements

- Every test suite green: web, core, Android modules, lint.
- Both surfaces checked by eye against spec §2 and §4.
- Docs describe what shipped; the spec agrees with the contract.

## Related code files

- Modify: `docs/system-architecture.md`, `docs/development-roadmap.md`,
  `docs/project-changelog.md`, `docs/superpowers/specs/2026-09-28-profile-roles-design.md`,
  `plans/260928-0047-profile-roles-pins-kids-age-limits/plan.md`, the three manifests + `Cargo.lock`.
- Create: `plans/reports/verify-260928-profile-roles-closing-report.md`, screenshots under `plans/reports/`.

## Implementation steps

### Task 1: Whole-project checks

- [ ] **Step 1:** `cd web && bun test` — expected: all pass, including
  `code-standards.test.ts` and `shared-watch-state-fixtures.test.ts`.
- [ ] **Step 2:** `cd web && bun run typecheck` (or the typecheck script
  `web/package.json` names) — expected: no errors.
- [ ] **Step 3:** `cargo test --workspace` — expected: all pass, including
  `crates/mediagram/tests/code_standards.rs` and
  `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`.
- [ ] **Step 4:** `cd android && ./gradlew testDebugUnitTest lint` — expected:
  green in every module.
- [ ] **Step 5:** `rg -n "KIDS_AGE_LIMIT|renameProfile|create_profile\(" web/public web/src crates android --glob '!**/build/**'`
  — expected: no hits outside generated bindings history.
- [ ] **Step 6:** `rg -n -i "phase[ -]?0[0-9]|\bF[0-9]+\b" $(git diff --name-only <first-commit-of-feature>^..HEAD -- web crates android)`
  — expected: no plan references in code or tests.

### Task 2: Both surfaces, by eye

Steps 1–6 run on the web **stub harness only** (`cd web && bun run preview`,
which works on copies — nothing reaches the channel). On the tablet
(`ANDROID_SERIAL=caad49da`) the walk is **read-only unless the user says
otherwise**: anything created, claimed or changed on a device syncs to the
whole household, and a removal does not follow it. In particular **admin is
never claimed from a device or a test** — andre claims it on the web player
once, right after release (until then, anyone can claim it or set a PIN for a
grown-up that has none). The TV is checked only if the user agrees.

- [ ] **Step 1: first admin.** Fresh state with no profiles: the picker offers
  "Create the first profile — it runs this household"; the profile made is the
  admin. State with grown-ups but no admin (today's `state.db` copy): the picker
  asks "Who runs this household?"; choose andre, set a PIN; the question does
  not come back.
- [ ] **Step 2: entering.** A kid's tile opens at once; a grown-up's asks the
  PIN; a pre-upgrade grown-up with no PIN asks to set one (twice) first.
- [ ] **Step 3: wrong PINs.** Five wrong → "wait 60 s"; the right PIN during
  the wait is still refused; after it, the right PIN opens.
- [ ] **Step 4: manage as admin.** Create a grown-up (with its PIN), reset its
  PIN, remove it (its kids go too); the admin has no "remove" for itself.
- [ ] **Step 5: manage as a parent.** Add a kid at FSK 6, change it to 12,
  remove it; another parent's kids are not offered.
- [ ] **Step 6: what a kid sees.** One FSK 6 and one FSK 12 kid: an FSK 12 film
  shows only for the 12; an unrated title marked "From 6" shows for both,
  "From 12" only for the 12; the empty shelf names the kid's own limit.
- [ ] **Step 7: sync (only with the user's go-ahead).** On a throwaway test kid
  the user approves: change its limit on the tablet; after the next sync round
  the web shows the new limit (and the other way round).
- [ ] **Step 8:** screenshots of the picker, the PIN prompt, the manage panel
  (admin and parent) on web and tablet into `plans/reports/` — none showing
  the Telegram account.

### Task 3: Docs

- [ ] **Step 1:** Rewrite `docs/system-architecture.md` § Kids profiles as
  § Profiles and roles: the three roles and the table from spec §2; the rule in
  spec §4; the sync keys and merge rules (contract §7); the PIN and its honest
  ceiling (spec §1); what removal still does not do (spec §8).
- [ ] **Step 2:** `docs/development-roadmap.md`: a new section before
  "Explicitly deferred" — "Profile roles, PINs and per-kid age limits" — with
  what shipped and the release numbers; add spec §8's items to the deferred list.
- [ ] **Step 3:** Amend the spec to match the contract, each with a one-line
  note that the contract made the change: §5's `POST /api/profiles` row gains
  `newPin` (the new grown-up's first PIN) and the no-`actorId` bootstrap;
  §2 § The first admin gains the fresh-install case ("Create the first
  profile — it runs this household", contract §12); §2 § Wrong PINs says the
  wait applies only to calls that compare a PIN.
- [ ] **Step 4:** Mark the plan: every phase `complete` in `plan.md`, frontmatter
  `status: complete`, and a `## Review` section — commits and releases, what
  shipped, verification evidence, follow-ups — the shape
  `plans/260927-1731-android-settings-system-redesign/plan.md` § Review uses.
- [ ] **Step 5:** Bump (§ Bumping, patch) and commit:

```bash
git add docs/system-architecture.md docs/development-roadmap.md docs/project-changelog.md \
  docs/superpowers/specs/2026-09-28-profile-roles-design.md \
  plans/260928-0047-profile-roles-pins-kids-age-limits/plan.md \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "docs: profiles have roles, grown-ups a PIN, kids their own limit; release <next>"
```

## Todo list

- [ ] Task 1 — all suites green
- [ ] Task 2 — web and tablet walked through, screenshots saved
- [ ] Task 3 — docs, spec amendment, plan Review, release

## Success criteria

All suites green in one run; every step of Task 2 observed on both surfaces;
`docs/system-architecture.md` no longer says "FSK 12" as a fixed rule or "the
phone cannot remove profiles"; versions agree across all manifests.

## Risk assessment

- **Real profiles touched during checks** → only test profiles on the tablet;
  the web check runs on the stub harness, which works on copies.
- **Version collision with another machine** → Step B1 reads `origin/main`.

## Security considerations

Screenshots must not show a PIN being typed in clear (the prompt masks it) or
the Telegram account; the closing report names no PINs.

## Next steps

Spec §8 follow-ups: removals that propagate across devices; a sync identity
other than the name (rename); handing on the admin role.
