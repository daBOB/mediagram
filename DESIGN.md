---
name: Mediagram (Android)
description: A printed catalogue of things already owned, set in ink for a room with the lights off.
colors:
  ground: "#16130F"
  page: "#1E1B16"
  sunk: "#272319"
  text: "#E8E2D4"
  figures: "#A89B84"
  rule: "#3A342A"
  rule-strong: "#574E3E"
  imprint: "#D26A55"
  ochre: "#C99A3F"
  sage: "#8AA17A"
typography:
  display:
    fontFamily: "Fraunces"
    fontSize: "26sp"
    fontWeight: 500
    letterSpacing: "-0.1sp"
    fontVariation: "opsz 28"
  headline:
    fontFamily: "Fraunces"
    fontSize: "23sp"
    fontWeight: 500
    letterSpacing: "-0.1sp"
    fontVariation: "opsz 28"
  title:
    fontFamily: "Fraunces"
    fontSize: "18sp"
    fontWeight: 500
    fontVariation: "opsz 28"
  title-small:
    fontFamily: "Fraunces"
    fontSize: "16sp"
    fontWeight: 500
    fontVariation: "opsz 28"
  body:
    fontFamily: "Newsreader"
    fontSize: "17sp"
    fontWeight: 400
    fontVariation: "opsz 16"
  body-small:
    fontFamily: "Newsreader"
    fontSize: "13sp"
    fontWeight: 400
    fontVariation: "opsz 16"
  figures:
    fontFamily: "Newsreader"
    fontSize: "13sp"
    fontWeight: 400
    fontFeature: "tnum"
    fontVariation: "opsz 16"
  label:
    fontFamily: "Newsreader"
    fontSize: "11sp"
    fontWeight: 400
    fontFeature: "tnum"
    fontVariation: "opsz 16"
rounded:
  none: "0dp"
spacing:
  extraSmall: "4dp"
  small: "8dp"
  medium: "16dp"
  large: "24dp"
  extraLarge: "32dp"
components:
  plate:
    backgroundColor: "{colors.sunk}"
    rounded: "{rounded.none}"
    padding: "0dp"
  plate-name:
    textColor: "{colors.text}"
    typography: "{typography.title-small}"
    padding: "8dp 0 0 0"
  plate-figures:
    textColor: "{colors.figures}"
    typography: "{typography.figures}"
    padding: "2dp 0 0 0"
  plate-initials:
    backgroundColor: "{colors.sunk}"
    textColor: "{colors.figures}"
    typography: "{typography.title}"
  shelf-tab:
    backgroundColor: "transparent"
    textColor: "{colors.figures}"
    typography: "{typography.title}"
    padding: "4dp 0"
  shelf-tab-selected:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    typography: "{typography.title}"
    padding: "4dp 0"
  masthead:
    backgroundColor: "transparent"
    width: "560dp"
  app-bar:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text}"
    typography: "{typography.headline}"
  notice:
    textColor: "{colors.ochre}"
    typography: "{typography.body-small}"
    padding: "8dp 16dp"
---

# Design System: Mediagram (Android)

## Overview

**Creative North Star: "The Catalogue Printed in Ink"**

The web player sets this library on uncoated paper. The Android app sets the
same catalogue in ink: the same hues, the same restraint, the page inverted.
A phone or a tablet is held in the room the film is about to play in, and a
paper-white slab there is a lamp — so the stock is inverted rather than
reproduced. Everything else about the identity carries over unchanged: two
serifs, hairline rules instead of cards, artwork set as plates with the name
beneath, one imprint red for the one thing worth marking.

Density is a wall, not a rail. A shelf puts everything it holds on the page in
one direction of travel, because this library is finite and already owned and
nothing in it is ranked. The refusal of the horizontally-scrolling poster row
is the thesis, not a stylistic preference: a rail hides how much is on a shelf
and promotes whatever it happens to show first.

This is one screen's worth of system, recorded from the shipped catalogue
screen. It is Material 3 underneath — Material's components, touch targets,
back behaviour and system bars are kept — with Material's own colour and type
defaults entirely replaced. Before this build the app called `darkColorScheme()`
with no arguments and rendered Material's baseline violet in Roboto; that is
what this system exists to have ended.

**Key Characteristics:**

- Ink ground, paper text, no white anywhere.
- Flat by tonal layering. Three grounds, no shadows, no elevation.
- Plates, not cards: square corners, a hairline, the name underneath.
- Two variable serifs, both driven on the optical-size axis.
- One accent, on the shelf you are on and nothing else.
- Figures in tabular numerals, always.

## Colors

Ink and paper with three warm signals, all of them carried over from the web
player's uncoated-stock palette and re-lit for a dark ground.

### Primary

- **Imprint Red** (`{colors.imprint}`): the one accent. It marks the shelf
  currently in view — drawn as the rule under the selected masthead label — and
  is reserved for the thing a viewer is in the middle of. It appears nowhere
  else on a surface. This is the web player's `#8c3b2e` lifted until it cleared
  4.5:1 on the page (measured 4.85:1), because it lands on small text saying
  where a viewer got to, not on decoration.

### Secondary

- **Figures Grey** (`{colors.figures}`): everything a title is *about* — years,
  runtimes, episode and chapter counts, the unselected shelf names, standing-in
  initials. Present, and quieter than a title (6.28:1 on the page).

### Tertiary

- **Ochre** (`{colors.ochre}`): the only thing the catalogue ever warns about. A
  refresh that failed is a warning, not an alarm — the library on screen is
  still every bit of the one that was there before — so ochre, not a signal red,
  is bound to Material's `error` role (6.68:1).
- **Sage** (`{colors.sage}`): the only good news the catalogue has, something
  already held on the device. Bound to `tertiary` (6.10:1). Defined and reserved;
  the catalogue screen does not yet have a surface that shows it.

### Neutral

- **Ground** (`{colors.ground}`): the deepest ink. The window is cleared to it
  before a single composable draws, and the top app bar sits on it.
- **Page** (`{colors.page}`): one step up from the ground — the sheet the plates
  are tipped onto. The wall and the masthead are on the page.
- **Sunk** (`{colors.sunk}`): where artwork is missing and the page itself shows
  through. The plate's own ground behind a poster, and behind initials.
- **Text** (`{colors.text}`): paper, at the weight a dark page carries without
  glare (13.29:1 on the page). Titles, wordmark, selected shelf.
- **Rule** (`{colors.rule}`): a hairline. Structure, never a boundary anyone has
  to look at. Every plate's edge.
- **Rule Strong** (`{colors.rule-strong}`): visible enough to read as an edge
  where one is doing work; Material's `outline`.

### Named Rules

**The One Accent Rule.** Imprint red says one thing: *this is the shelf you are
on / this is the thing you are in the middle of*. It is never a button fill,
never a heading colour, never a link. Its rarity is the whole of its meaning.

**The Inverted Stock Rule.** The palette is the web player's, one relation
inverted: ink where paper was. Ground is darker than page; page is darker than
nothing else. Any surface lighter than the page it sits over breaks the one
relation the palette names — which is why the top app bar is on ground, not on
a lifted container.

**The Measured Colour Rule.** A colour that carries text is measured against
both grounds before it ships. Every text-carrying value in this palette clears
4.5:1 on ground and on page, and the doc comment beside it records the number.

## Typography

**Display Font:** Fraunces (variable, `wght` 500–600, `opsz` 28)
**Body Font:** Newsreader (variable, `wght` 400–600, `opsz` 16)

Both faces are the exact files the web player loads, decompressed from the same
woff2 sources; OFL licences ship in `android/core/designsystem/licenses/`.

**Character:** A press catalogue's pairing. Fraunces names things — it has the
weight and the slight eccentricity of a title set on a cover. Newsreader says
things — it is a reading face, and every sentence, year, runtime and count is
set in it. Neither is ever asked to do the other's job.

### Hierarchy

- **Display** (Fraunces Medium, 26sp, −0.1sp): section headings.
- **Headline** (Fraunces Medium, 23sp, −0.1sp): the top app bar title — the
  wordmark on the catalogue, the name of a collection or title elsewhere.
- **Title** (Fraunces Medium, 18sp): shelf names in the masthead; standing-in
  initials on a plate with no artwork.
- **Title Small** (Fraunces Medium, 16sp): the name under a plate, to two lines
  then ellipsis.
- **Body** (Newsreader, 17sp): whole sentences — loading, empty, and failure
  messages.
- **Body Small** (Newsreader, 13sp): the notice above a shelf.
- **Figures** (Newsreader, 13sp, `tnum`): the line under a plate's name — year
  and runtime, or the count of episodes and chapters.
- **Label** (Newsreader, 11sp, `tnum`): the smallest figures.

### Named Rules

**The Optical Size Rule.** Android does nothing automatic with `opsz` — there is
no `font-optical-sizing` here. Every face is declared with its axis fixed at the
distance it is read from: 28 for a name held at arm's length, 16 for a sentence
or a figure. A new face declaration that omits the axis gives up the reason
these two files are worth their bytes.

**The Tabular Figures Rule.** Anything countable — runtimes, years, counts,
sizes — is set in tabular numerals, so a column of them lines up down a page
instead of shifting with each digit's width.

**The Fraunces Floor.** Fraunces is never asked for a weight below 500. On an
ink ground its 400 goes thin enough to shimmer, and the catalogue sets its names
in medium on paper anyway.

**The Whole-Sentence Rule.** A first run, an empty library and a failed load are
the three moments a viewer reads a whole sentence. They are set in the
catalogue's reading face, not left at a platform default, because they are
exactly the moments the app would otherwise stop sounding like itself.

## Layout

The wall is a fixed-count vertical grid. Columns come from the window's width
class, never from how many titles a shelf holds: six across on Expanded, four on
Medium, three on Compact. A shelf with one course in it keeps a plate the size
of a plate rather than stretching one across the width and saying something
untrue about how much is there.

Plates are 2:3 — the ratio a poster is actually drawn at, so nothing is cropped.
Gutters and the wall's outer padding are both one medium step (16dp). The name
sits one small step (8dp) under the artwork and the figures line 2dp under the
name, so a plate reads as one block with air around it rather than as three
stacked items.

The spacing scale is five steps — 4 / 8 / 16 / 24 / 32dp — shared with the
television surface. 16dp is the working rhythm; 32dp is the margin an empty-state
message is held inside.

Vertical order on the catalogue is fixed: app bar on ground, then a full-width
progress line while the library is being worked on, then the masthead, then any
notice, then the wall. The progress line and the masthead are pinned above the
wall and do not scroll with it, because they report on the whole library rather
than on a row of it.

The masthead is held to 560dp and centred, not stretched. Three labels spread
across a tablet's twelve hundred points read as three unrelated buttons rather
than as one masthead.

### Named Rules

**The Wall Rule.** A shelf is a wall: everything it holds, on the page, in one
direction of travel. No horizontally-scrolling rail, ever. A rail hides how much
is on a shelf and ranks what it shows first, which is a storefront's job and not
this one's.

**The One Shelf Rule.** Exactly one shelf is on the wall at a time, chosen from
the masthead. Stacked shelves and the wall cannot both be had: the film shelf
alone is three hundred plates deep, so stacking would put the courses fifty
screens below the fold with nothing on screen to say they existed.

## Elevation & Depth

**There are no shadows and no elevation in this system.** Depth is entirely
tonal: three grounds — ground, page, sunk — and a hairline. Nothing is lifted,
nothing floats, and no Material elevation overlay is used.

This is a deliberate translation, not an omission. On paper the web player gives
each plate a short drop shadow so it reads as tipped onto the page. Against ink
that shadow is invisible, so the hairline is what does that work here.

### Named Rules

**The Hairline Rule.** Structure is drawn with a 0.5dp hairline in rule
(`{colors.rule}`), never with a shadow, a fill or a raised container. On a plate
the hairline is painted as an overlay on top of the artwork, not as a border on
the same box — a border modifier paints beneath the content, and a poster
cropped to fill would cover it.

**The No Floating Container Rule.** The masthead's container is transparent so it
reads as type on the page. Given a colour of its own it becomes a filled band
floating between the bar and the wall, which is the one thing this world does not
do.

## Shapes

Square. The corner radius of this system is 0dp and there is one shape token to
say so. A plate is a rectangle of artwork with a hairline around it; the
catalogue has no pills, no rounded cards, no chips, and no clipped silhouettes.
Material components that ship with a default radius are either given the
catalogue's own flat treatment or not used.

The one recurring silhouette is the 2:3 plate, at every size, on every surface
that shows artwork.

## Components

### Plate (signature component)

The catalogue's one real component: artwork at 2:3, a hairline around it, the
name beneath in Fraunces Medium 16sp to two lines, and a figures line 2dp under
that in Newsreader 13sp tabular.

- **Corner style:** square (0dp).
- **Background:** sunk (`{colors.sunk}`) behind the artwork, so a poster still
  loading or absent shows the page's own stock rather than a hole.
- **Border:** 0.5dp hairline in rule (`{colors.rule}`), overlaid on the artwork.
- **Shadow:** none. See Elevation & Depth.
- **Missing artwork:** two initials in figures grey, sized at 26% of the plate's
  own width with 2sp tracking, not at a fixed size. A mark that reads on a phone
  is adrift in the middle of a tablet's plate, which looks like something failed
  rather than like a stand-in.
- **Target:** the whole plate, artwork and caption together — a name that does
  not open what it names is a dead patch in the middle of a wall. The semantics
  are merged, so a screen reader meets one plate once rather than an image and
  then its title again.
- **Names underneath, never across the face.** Every episode of a show carries
  the same artwork, and the pinned channel index carries no artwork at all, so
  the face is the least reliable place to say what something is.

### Navigation — the masthead

- **Style:** a tab row with a transparent container, centred, held to 560dp.
- **Typography:** shelf names in Fraunces Medium 18sp. Text only.
- **Selected:** label at full paper weight (`{colors.text}`), with the imprint
  red rule beneath it. The rule is the mark; the label is simply not dimmed.
- **Unselected:** figures grey (`{colors.figures}`).
- **Target:** each label carries 4dp of vertical padding so a short word like
  "Series" still clears Material's 48dp.
- **No icons.** There are no icons in this world, and a drawn one would be
  inventing a mark for a shelf that already has a name.

### Top app bar

- **Container:** ground (`{colors.ground}`) — darker than the page below it.
- **Title:** Fraunces Medium 23sp in paper. The wordmark at the root; the name of
  whatever is open elsewhere, read from the destination so no two screens can
  spell it differently.
- **Icons:** the platform's own vector back and overflow marks, in figures grey,
  each carrying a content description.

### Notice

- A single line of Newsreader 13sp in ochre, above the shelf and never instead of
  it. The library below is the one that was on the device before the refresh was
  tried, and it is still every bit of it.

### Empty and failure states

- One centred sentence in Newsreader 17sp, figures grey, inside 32dp of margin.
  No illustration, no icon, no button.

### Platform window

- The window background is the catalogue's ground, declared in the platform theme
  so a cold start opens on the colour the first frame will be rather than on a
  white flash. Status and navigation bars are transparent. This one value is held
  in two places by necessity — the window opens before Compose exists — and each
  names the other.

## Do's and Don'ts

### Do:

- **Do** put everything a shelf holds on one vertical wall, and reach other
  shelves from the masthead.
- **Do** set names in Fraunces at 500 or above and everything readable or
  countable in Newsreader.
- **Do** fix `opsz` explicitly on every face declaration: 28 for display, 16 for
  reading.
- **Do** set every figure in tabular numerals.
- **Do** draw structure with the 0.5dp hairline in rule, and keep surfaces flat.
- **Do** measure any new text-carrying colour against ground and page, record the
  ratio beside the value, and lift it until it clears 4.5:1 — as imprint was
  lifted from the web player's `#8c3b2e`.
- **Do** take the colour of a new surface from Material roles: a component that
  reaches for `colorScheme.surface` knowing nothing about this app must land on
  the page.
- **Do** keep the app dark on every device, regardless of system theme. A media
  library is looked at in the dark.

### Don't:

- **Don't** ship a horizontally-scrolling poster rail.
- **Don't** round a corner. The radius of this system is 0dp.
- **Don't** add a shadow or a Material elevation overlay. Against ink they do not
  render, and the hairline already carries that job.
- **Don't** give a masthead, a shelf header or a grouping band a container colour.
  A filled container floating between the bar and the wall is the one thing this
  world does not do.
- **Don't** spend imprint red on anything but the shelf in view or the thing a
  viewer is in the middle of.
- **Don't** put a name across a plate's face.
- **Don't** offer dynamic colour. Material You would derive the scheme from a
  wallpaper, and a wallpaper cannot be allowed to decide what the catalogue
  warns in.
- **Don't** invent an icon for something that already has a name. The platform's
  own back and overflow marks are fine in the app bar; a drawn glyph standing in
  for a shelf is not.
- **Don't** reach for a signal red. The strongest thing this catalogue ever says
  is ochre.

## What this system does not yet cover

Recorded honestly, because the next screen will have to decide these rather than
look them up:

- **One screen's worth of system.** The catalogue screen, its plates, its masthead
  and the app bar are what shipped and what is recorded here. The player, the
  title detail screen, the collection screen and the system screen have not been
  restyled; they inherit the palette and type through the theme but their own
  composition is undocumented and unreviewed.
- **No component vocabulary for buttons, inputs, chips, dialogs or lists.** The
  catalogue has none of these, so none are recorded. They are Material defaults
  wherever they appear today.
- **The player's palette is unresolved on Android.** The web player is black on
  purpose and carries its own palette, because a picture reads best against
  nothing. The Android surface is already ink; whether the player needs a second,
  darker palette is open.
- **Sage is defined and unused.** It has no surface on the catalogue yet.
- **Dynamic type is untested.** The scale is declared in `sp`, so it will respond
  to the system font scale, but no size has been checked at a large setting.
- **API 24–25 render both faces at their default instance.** Variation settings
  are ignored there, so the optical-size axis and the weight axis do nothing on
  those two releases. The fallback is legible; it is not the designed type.
