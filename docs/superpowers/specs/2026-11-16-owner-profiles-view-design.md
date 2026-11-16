# One owner view on the Profiles page

Date: 2026-11-16 · Status: draft for approval · Approach A (user's pick)

## Why

The owner (andre) set out to add one person to household Madani and could not.
It took four screenshots and ended with the profile made by hand through the API.
The user: "everything is crazy confusing … i am already logged in as owner why is
there no 1 page to create households and profiles with access management".

The page today splits the owner's work into two halves that each ask the same PIN:

- **Manage profiles → Who are you? → andre** shows every household's people with
  Reset PIN, section access, age, daily limit and Remove, but its *Add a grown-up*
  and *Add a kid* forms only add to the owner's own household (Home).
- **Households → Owner** creates, renames, changes admin, moves a grown-up, uses a
  household on this device and deletes, but cannot add a person.

On top of that, the same people are listed up to three times (*Everyone here*,
the *Who are you?* pills, then Manage), and the household tools sit behind a bare
*Owner* button.

The page is mostly for the owner, setting up every household (user's answer).

## Goals

- When the owner is the one managing, the Profiles page is **one view**: each
  household with its admin and actions, its people with their controls, an
  *Add a grown-up* and *Add a kid* in **every** household, then *Create a household*,
  then *This device*.
- **One PIN** for the whole view, asked once and held until the page is left.
- The owner can add a grown-up or a kid **straight into any household**.
- Web first (the reference), then the Android tablet/phone page and the TV page
  with the same structure (Surface Parity).

## Non-goals

- Anything a non-owner sees. Celine, Nadin, Kevin and plain grown-ups keep today's
  page (*Everyone here*, *Who are you?*, their own household).
- The PIN model: PINs stay per change, held only while the page is open. No
  "already logged in" trust.
- Moving people: *Move a grown-up here* stays as it is, on the household's row.
- The test profiles (test, TV test, TV kids) stay; device test walks use them.
- A new page or screen. Everything stays on the one Profiles page
  (user decision 2026-10-20).

## Anti-goals

- Owner tools usable without the owner's PIN.
- A second place to manage profiles.
- The owner view behaving differently on web, tablet and TV.

## Approaches considered

- **A. Merge both halves into one owner view** — picked by the user. One list
  replaces three, one PIN, add-anywhere. Needs a small rule change on the server
  and in the Rust core so the owner can name the household (grown-up) or the
  grown-up (kid).
- **B. A separate owner page.** Dropped: brings back the multiple profile pages
  the user rejected on 2026-10-20.
- **C. Patch only** (share the PIN between halves, add a person from the household
  row). Cheapest, but keeps the duplicate lists and the two-halves feel the user
  called confusing.

## The design

### When the owner view shows

- **Settings → Profiles while watching as the owner:** the view is drawn at once,
  no *Who are you?*, no *Owner* button, no *Everyone here*. The first change asks
  the owner's PIN once; it is held for every later change until the page is left.
- **Picker → Manage profiles (nobody chosen):** *Who are you?* stays. Choosing the
  owner asks the PIN there, and the owner view opens with that PIN held. Choosing
  anyone else shows today's page.
- **A device that shows another household** (say Madani, after *Use on this
  device*): the owner is not in its picker or in *Who are you?*. The *Owner* button
  stays on the page wherever the owner view is not already showing, and now opens
  the **full** owner view after the owner's PIN, replacing the page's management
  parts, instead of today's households-only part. So there is one owner view,
  reachable from any device.
- A kid still sees only *You*.

### Layout (web; tablet and TV follow it)

```
You (who is watching · Switch profile · Subtitles)      ← unchanged

Home            Admin andre     [Rename] [Move a grown-up here] …
  andre   Owner · Admin                         [Change PIN]
  test    Grown-up · PIN set     [Reset PIN] [Sections] [Remove]
  TV kids Kid · andre · FSK 12   [Age] [Daily limit] [Sections] [Remove]
  [Add a grown-up]  [Add a kid]
Madani          Admin Celine    [Rename] [Change admin] [Move a grown-up here] [Use on this device]
  Celine  Admin · PIN set · hides Tutorials   [Reset PIN] [Sections] [Remove]
  [Add a grown-up]  [Add a kid]
…every live household…
[Create a household]   (name · admin: a new grown-up or an existing one)

This device: Home  [Switch household]
```

- Each person's row carries the role line *Everyone here* shows today (Owner ·
  Admin · PIN set, Kid · parent · FSK, hidden sections), so nothing it said is lost.
- The household header carries the actions the Households part offers today,
  under the same household rule (`householdAllowed`): Rename, Change admin (never
  in the owner's own household), Move a grown-up here, Use on this device, Delete
  (only an empty one other than Home). The sketch above is illustrative; the rule decides.
- Households in the live order with the device's own first, as Manage orders them today.
- An empty household still shows, with *Add a grown-up* and Delete. *Add a kid*
  shows only where the household has at least one grown-up, since a kid always
  belongs to one.
- Words stay those of `household-words.js` / `HouseholdWords.kt`; new words go in both.

### Adding into another household

- **Add a grown-up** in household H: name, then the new PIN asked twice (as today).
  The profile is created in H. If H has no admin at that moment, the new grown-up
  becomes its admin, as a grown-up moved into such a household does today.
- **Add a kid** in household H: name, age (6/12), daily limit, as today. The kid
  belongs to H's admin; when H has more than one grown-up, a *Belongs to* choice
  lists them, defaulting to the admin (or to the first grown-up, in the rare
  household left without an admin). A kid always lives where its grown-up is, so
  `create-kid` needs only the parent, never a household. In Home this is the
  owner, which is today's behaviour.

### Rule and API change (web server and Rust core, held to one fixture)

- `create-grown-up` takes an optional target **household**. Only the owner may name
  one other than their own; anyone else naming another household is refused.
  Omitted means the maker's household, as today.
- `create-kid` takes an optional **parent** (a grown-up). Only the owner may name a
  grown-up other than themselves; the kid's household follows the parent.
  Omitted means the maker, as today.
- Web: `profiles-rules.ts`, `profiles-manage.ts` (`createGrownUp`, `createKid`),
  the `/api/profiles` route, `profile-api.js`.
- Rust core: `state/profiles/rules.rs`, `state/profiles/manage.rs`
  (`create_grown_up`, `create_kid`), `api/state/profile_roles.rs`, the uniffi
  surface, and the Android calls (`ProfileRequests.kt`, `CoreProfileCalls.kt`).
- Both implementations are checked against `web/test/fixtures/watch-state/profile-rules.json`,
  extended with the new cases, so web and Android agree.
- Old clients keep working: they never send the new fields.

### Where it lives today

- Web: `catalog/settings-profiles.js` (page), `profile-manage.js` +
  `profile-manage-households.js` (Manage), `catalog/settings-households.js` +
  `catalog/household-forms.js` (owner's Households), `profiles-page-overlay.js`
  (picker route).
- Tablet/phone: `ui-mobile/.../settings/ProfilesPage.kt`,
  `HouseholdsOwnerBlock.kt`, `HouseholdForms.kt`, `ui/profile/ManageProfiles*.kt`,
  view models `ManageProfilesViewModel.kt`, `HouseholdsOwnerViewModel.kt`.
- TV: `ui-tv/.../settings/TvProfilesPage.kt`, `profile/TvManageProfiles.kt`,
  `TvHouseholdsPanel.kt`, `TvHouseholdSteps.kt`, `TvAddProfileFlow.kt`.

## Calls made here (not asked of the user)

- Settings route asks the PIN at the first change, not on opening, so changing
  subtitles in *You* never asks a PIN.
- *Everyone here* goes away only in the owner view; others keep it.
- A kid added elsewhere belongs to that household's admin by default, with a
  *Belongs to* choice only where there is more than one grown-up.
- A grown-up added to an admin-less household becomes its admin.
- *Move a grown-up here* is kept as is.
- *Add a kid* is hidden in a household with no grown-up, rather than letting a kid
  exist without one (raised by the builder check).
- The *Owner* button stays as the way into the owner view on a device showing
  another household, and opens the whole view, not just households (raised by
  the builder check). Today's households-only part (`settings-households.js`,
  `HouseholdsOwnerBlock.kt`, `TvHouseholdsPanel.kt`) is folded into the owner view.

## Left to the builder

- How the web merges `mountManage` and `householdsPart` (one module or two sharing
  one held PIN), within the 200-line file limit.
- How the TV lays the view out for the D-pad (rows and step panels as today's TV
  panels do), as long as the structure and words match the web.
- Tests: rule fixture cases, server route tests, browser tests for the owner view,
  Android view model tests, Robolectric/emulator checks for TV focus.

## Release

Media library line, minor bump in step: `Cargo.toml`, `web/package.json`,
`android/app/build.gradle.kts` `versionName`. Verify the web with the stub harness
(`cd web && bun run preview`), then the tablet (caad49da) and the TV.
