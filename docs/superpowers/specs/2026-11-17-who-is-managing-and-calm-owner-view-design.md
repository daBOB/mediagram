# Who's managing? and a calm owner view

Date: 2026-11-17 · Status: approved by the user's two answers below · Revises
`2026-11-16-owner-profiles-view-design.md` (everything there still holds unless this says otherwise)

## Why

The user looked at picker → *Manage profiles* on the live player and wrote
"STILL crazy ugly !!". The screen they showed (Everyone here, *Who are you?* pills,
This device, Households · *Owner*, Done stacked under it) is the one the first
design kept unchanged; only after choosing andre did the new owner view appear.
Shown the owner view as built (every control visible: Reset PIN, Remove, five
Sections pills under every person, two add forms per household), they chose the
calmer form too.

## The user's answers (2026-11-17)

1. **Picker → Manage profiles becomes one question, *Who's managing?*,** that looks
   like *Who's watching?*: big avatar tiles of the grown-ups, owner first, labelled
   *Owner*. Choosing andre asks the owner's PIN and opens the owner view. No
   *Everyone here* list, no separate *Owner* button, no *This device* block on it.
   Done returns to the picker.
2. **The owner view is calm: tap a person to see their controls.** Each household
   shows its people as one row each (name, role line, ›). Tapping a person opens
   their controls. The household's actions sit behind one button (⋯) on the
   household line. One *Add someone* per household opens the add form.
   *Create a household* at the end.

## The design

### Who's managing?

- Same tile component as *Who's watching?* (avatar initial, name), on the same page
  the picker route opens today. Grown-ups of this device's household, owner first;
  kids are not managers and have no tile.
- The owner's tile carries the word *Owner* under the name.
- On a device showing another household (the owner is not among its grown-ups), an
  *Owner* tile ends the row; choosing it asks the owner's PIN and opens the owner view.
  This replaces the old Households · *Owner* gate.
- Choosing a tile asks that person's PIN (the owner's PIN for the owner tile), then
  opens that person's management view. A wrong PIN says so on the same screen.
- Settings → Profiles for a grown-up who is not the owner shows the same
  *Who's managing?* tiles where *Everyone here* + *Who are you?* + the gate were.
  Settings → Profiles while watching as the owner opens the owner view at once
  (unchanged: PIN at the first change). A kid still sees only *You*.

### The calm view (owner, and anyone managing)

```
Home                              Admin andre · 4 profiles   [⋯]
  andre    Owner · Admin · PIN set                              ›
  test     Grown-up · PIN set                                   ›
  TV kids  Kid · andre · FSK 12                                 ›
  [+ Add someone]

Madani                            Admin Celine · 1 profile   [⋯]
  Celine   Admin · PIN set · hides Tutorials                    ›
  [+ Add someone]

[+ Create a household]

This device · Home                               [Switch household]
```

- **Person row:** name, the role line, ›. Tapping opens that person's controls in
  place under the row (one open at a time): Change PIN / Reset PIN, Sections, a kid's
  age and daily limit, Remove — exactly the controls the rule allows today. People
  the manager cannot change show no › and do not open.
- **Household line:** name (a heading), *Admin X · N profiles*, and ⋯ opening the
  household's actions the rule allows (Rename, Change admin, Move a grown-up here,
  Use on this device, Delete). Only the owner's view has household lines with ⋯.
- **Add someone:** one per household. Opens the add form in place: *Grown-up* / *Kid*
  choice (Kid only where the household has a grown-up), name, then for a kid age,
  daily limit and *Belongs to* (only with more than one grown-up; default the admin).
  The rules from the first design hold: the owner adds into that household, a refused
  add keeps what was typed, the owner's PIN is asked once per visit.
- **This device** is one quiet line at the end with *Switch household*.
- A non-owner manager (a household's admin, a grown-up) sees the same calm rows for
  what they may manage today — their household, no household line, no ⋯, no Create.
  What they may do is unchanged; only the look changes. (Call made here: the first
  design said non-owner pages stay as they are; the user's "ugly" covers the page,
  and one row style everywhere is simpler than two.)

### Surfaces

Web first (the reference), then tablet/phone and TV with the same structure and words:
- Tablet/phone: the same tiles as its picker; person rows expand in place; ⋯ opens a menu.
- TV: *Who's managing?* uses the picker's tiles; a person row's OK opens that person's
  controls panel; the household header's OK opens its actions (as built); one
  *Add someone* row opens a *Grown-up* / *Kid* choice step, then the existing add flow.

### Words

New words go in `household-words.js`, `HouseholdWords.kt` and `household-rules.json`
`words` together: *Who's managing?*, *Owner* (tile label, if not already a word),
*Add someone*, *Grown-up*, *Kid* (if not already words).

## Left to the builder

Exact spacing, motion and the expand affordance, within the player's existing
dark-first look and its *Who's watching?* tiles. Check both themes and phone width.
