# Households — design

**Asked 2026-10-20:** "you supposed households. before we can create a profile we must create household and then can assign a profile. only the profile in a household can see other profiles from this household."

## What it is for

One Mediagram install — one Telegram library, one web server, its phones, tablets and TVs — is shared by more than one household. Each household sees only its own profiles: its picker, its "Everyone here", its management. The owner of the install sets households up.

## Decisions (the user's, 2026-10-20; do not reverse silently)

1. **The owner creates households**, gives each its admin, and assigns profiles to households. Each household's admin then manages its own grown-ups and kids as today.
2. **A device joins one household** (phone, tablet, TV or browser), once, with that household's admin PIN. Its "Who's watching?" shows only that household's profiles.
3. **A household rule, not real separation.** Like the kids filter: other households' profiles are not shown, but everyone shares the same library and server, and nothing is refused server-side.
4. **The four profiles that exist** (andre, test, TV test, TV kids) become the first household, with andre as its admin.

## The controller's calls (provisional; the user may change any)

1. **The owner is a role on a profile**: andre after the migration, with andre's PIN. The owner also belongs to a household (the first one) like anyone else. Handing the owner role to someone else is out of scope.
2. **A household** has an id, a name, and its admin (a grown-up of that household). The first one is named **Home**; the owner can rename it.
3. **A profile belongs to exactly one household.** A kid always belongs to its parent's household; moving a grown-up moves their kids with them.
4. **Who manages whom becomes per household.** Today's rule (`profile-rules.json`) applies inside a household: its admin adds and removes its grown-ups and sets their PINs, every grown-up manages their own kids and their own PIN. Nobody manages a profile in another household — except the owner, for household-level actions (below).
5. **The owner's actions:**
   - create a household (name + its admin: a new grown-up created with it, or an existing grown-up moved into it);
   - rename one;
   - change its admin;
   - move a grown-up (with their kids) to another household;
   - delete an empty one.
6. **Joining a household on a device** stores the household on that device only (browser storage on the web, app settings on Android); it is not synced. A device that has never joined:
   - with exactly one household on the install, joins it by itself, so every install that has only one household — every one today — works as before, with no question;
   - with more than one, shows **Join a household**: the households by name, then that household's admin PIN.
   Leaving or switching a household is in Settings → Profiles, for the household's admin or the owner.
7. **Where it shows** (on top of the one Profiles page being built: You, Everyone here, Manage profiles):
   - "Everyone here" and Manage list only this device's household;
   - a **Households** part on the same page, shown only when the chosen profile is the owner: every household with its admin and member count, and the owner's actions. Every action asks the owner's PIN once per visit, as Manage does.
8. **Sync:** households and each profile's household travel in the existing state sync, like a kid's age or daily limit: every field is stamped, the newest write wins, and ties go by device id. The web server, the Rust core (Android) and their shared fixtures carry it.
9. **Everything per profile stays per profile:** watch state, Continue, My List, lists, stats, achievements, hidden sections and limits.
10. **Out of scope:**
    - real separation: logins, the server refusing other households, per-household libraries or channels;
    - per-household settings (cache, Telegram);
    - which household a cast or watch-together session belongs to. A TV still takes a cast from any device on the install, as today; a later change can limit it.

### Settled after the builder check (controller's calls, 2026-10-20)

11. **Profile names stay unique across the whole install.** Sync knows a viewer by its normalised name, so two households can't both have a "Mum". Making names unique per household would rekey every synced record. The refusal reads "That name is already used on this install", which doesn't say where.
12. **A household's admin is a field of the household record** (stamped, newest wins; changing it is an owner action). It replaces the install-wide "earliest admin claim". At migration Home's admin is andre, the current claim holder, so an older build that hasn't updated still reads Home as before; until it updates, it sees other households' admins as ordinary grown-ups (a soft rule, briefly). A kid whose parent is gone falls to its household's admin.
13. **The owner's PIN works on any device**, whichever household the device has joined. From Settings → Profiles an **Owner** button asks for it and opens the Households part, and the owner can switch the device to another household there. A household's admin PIN switches the device only into that household.
14. **Every household admin has a PIN.** It is set when the household is created or the admin is changed, and the owner role needs andre's PIN, which is already set.
15. **Which household an existing device is in:** on its first launch with the new build, a device that has chosen a profile before joins that profile's household, silently, and stores it. A brand-new device, or one that has never chosen anyone, joins by itself while only one household exists, and otherwise asks **Join a household**. The kids' TV box therefore stays where its profile is, even if a second household was created before it updated.

## Testing

- Shared fixtures for the household rule and for the sync record and merge, run by the web, the Rust core and Kotlin.
- Migration tests: the existing profiles land in Home, andre is admin and owner, and a device with no household joins Home by itself.
- On a device: create a second household as the owner, move TV test into it, and join the TV box to it. The box's picker then shows only TV test, and the tablet's picker no longer shows it.
