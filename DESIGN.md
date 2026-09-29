---
name: Mediagram (Android)
description: A printed catalogue of things already owned, set in the web player's own dark theme.
colors:
  ground: "#0D0D0E"
  page: "#151517"
  sunk: "#1B1B1D"
  sidebar: "#09090A"
  text: "#F3EFE7"
  figures: "#CBC5BA"
  rule: "rgba(243, 239, 231, 0.18)"
  rule-soft: "rgba(243, 239, 231, 0.08)"
  rule-strong: "#9C968B"
  imprint: "#E57A61"
  ochre: "#E6C47F"
  sage: "#A3D3A4"
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
  page-title:
    fontFamily: "Fraunces"
    fontWeight: 500
    letterSpacing: "-0.03em"
    lineHeight: "0.86em"
    fontVariation: "opsz 112 (opsz 72 compact/TV)"
  body:
    fontFamily: "Newsreader"
    fontSize: "17sp"
    fontWeight: 400
    fontVariation: "opsz 16"
  body-small:
    fontFamily: "Geist"
    fontSize: "13sp"
    fontWeight: 400
  eyebrow:
    fontFamily: "Geist"
    fontSize: "11sp"
    fontWeight: 500
    letterSpacing: "0.32em"
    lineHeight: "1.4em"
  section-head:
    fontFamily: "Geist"
    fontSize: "16sp"
    fontWeight: 600
  figures:
    fontFamily: "Geist"
    fontSize: "13sp"
    fontWeight: 400
    fontFeature: "tnum"
  label:
    fontFamily: "Geist"
    fontSize: "11sp"
    fontWeight: 400
    fontFeature: "tnum"
  ledger-label:
    fontFamily: "Geist"
    fontSize: "14sp"
  ledger-value:
    fontFamily: "Geist"
    fontSize: "15sp"
    fontFeature: "tnum"
rounded:
  control: "6dp"
  plate: "0dp"
spacing:
  extraSmall: "4dp"
  small: "8dp"
  medium: "16dp"
  large: "24dp"
  extraLarge: "32dp"
components:
  plate:
    backgroundColor: "{colors.sunk}"
    rounded: "{rounded.plate}"
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
  settings-index-row:
    textColor: "{colors.figures}"
    selectedTextColor: "{colors.text}"
    typography: "{typography.title-small}"
    subLabelColor: "{colors.rule-strong}"
    rounded: "{rounded.control}"
    minHeight: "64dp"
    padding: "12dp 14dp"
  page-head:
    eyebrowColor: "{colors.rule-strong}"
    eyebrowTypography: "{typography.eyebrow}"
    titleColor: "{colors.text}"
    titleTypography: "{typography.page-title}"
  ledger-row:
    labelColor: "{colors.rule-strong}"
    labelTypography: "{typography.ledger-label}"
    valueColor: "{colors.figures}"
    valueTypography: "{typography.ledger-value}"
    dividerColor: "{colors.rule-soft}"
    minHeight: "48dp"
  pill-line:
    borderColor: "{colors.imprint}"
    textColor: "{colors.imprint}"
    rounded: "{rounded.control}"
    minHeight: "48dp"
    padding: "0dp 20dp"
  pill-quiet:
    borderColor: "{colors.rule}"
    textColor: "{colors.figures}"
    rounded: "{rounded.control}"
    minHeight: "48dp"
    padding: "0dp 20dp"
  chip:
    borderColor: "{colors.imprint}"
    textColor: "{colors.imprint}"
    rounded: "{rounded.control}"
    minHeight: "36dp"
    padding: "0dp 14dp"
  swatch-card:
    rounded: "12dp"
    borderColor: "{colors.rule}"
    selectedRingColor: "{colors.imprint}"
---

# Design System: Mediagram (Android)

## Overview

**Creative North Star: "The Catalogue, One Ground"**

The web player sets this library on a screening-room dark ground, with a warm
paper variant a viewer can choose in Settings › Appearance. The Android app
now sets the same catalogue in the same ground — the palette below is
`styles/theme.css`'s `:root` block, verbatim, not re-lit or reasoned about
for a phone. A phone or a tablet is held in the room the film is about to
play in, and this catalogue no longer treats that as a reason to invert the
web player's own choice of ground: the two surfaces read as one product
because they are lit by one palette. Everything else about the identity
carries over unchanged: two serifs plus one sans for the interface, hairline
rules instead of cards, artwork set as plates with the name beneath, one
accent for the thing a viewer is in the middle of.

Density is a wall, not a rail. A shelf puts everything it holds on the page in
one direction of travel, because this library is finite and already owned and
nothing in it is ranked. The refusal of the horizontally-scrolling poster row
is the thesis, not a stylistic preference: a rail hides how much is on a shelf
and promotes whatever it happens to show first.

This is one screen's worth of system, recorded from the shipped catalogue
screen, plus the tokens a Settings/System rebuild draws from. It is Material 3
underneath — Material's components, touch targets, back behaviour and system
bars are kept — with Material's own colour, type and shape defaults entirely
replaced.

**Key Characteristics:**

- Ink ground, paper text, no white anywhere — the web's own dark theme.
- Flat by tonal layering. Three grounds plus a rail ground, no shadows, no
  elevation.
- Plates, not cards: square corners, a hairline, the name underneath — the
  one shape this system keeps flat while everything a viewer operates gets
  a soft 6dp corner.
- Two variable serifs for names and sentences, one variable sans for
  everything that reads as interface rather than as prose.
- One accent, spent on the thing a viewer is in the middle of, on a focus
  ring, or on an outline control — never a solid fill behind a whole surface.
- Figures in tabular numerals, always.

## Colors

Ink and paper with three warm signals, all of them the web player's own dark
theme — Android reads the same nine roles the web names, under the names the
existing catalogue already used.

### Role mapping

| Android | Web | Role |
|---|---|---|
| Ground | `--paper` | the window, cleared before a frame draws |
| Page | `--surface` | a plain screen |
| Sunk | `--paper-sunk` | missing artwork, inset areas |
| Sidebar | `--sidebar` | a settings index's own ground |
| Text | `--ink` | titles, wordmark, selected shelf |
| Figures | `--ink-2` | years, runtimes, counts, unselected shelf names |
| Rule | `--rule` | a hairline that has to be seen |
| Rule Soft | `--rule-soft` | a hairline that only has to separate |
| Rule Strong (quiet) | `--ink-3` | tertiary text: eyebrows, sub-labels, ledger labels |
| Imprint | `--accent` (Coral) | the one accent |
| Ochre | `--warn` | the only warning |
| Sage | `--held` | the only good news |

### Primary

- **Imprint** (`{colors.imprint}`): the one accent. It marks the shelf
  currently in view — drawn as the rule under the selected masthead label —
  and, on a settings screen, a focus ring, a selected row's ring, and an
  outline pill's border and text. It is never a filled background behind a
  whole control or surface: that is what the *rest* of the One Accent Rule
  (below) still holds the line on, even as where the accent is allowed to
  appear has grown past a single rule under a label. This is the web
  player's own `#e57a61` — Coral, its default — now used verbatim rather
  than lifted for a different ground, because the ground it has to clear
  4.5:1 on is now the web's own.

### Secondary

- **Figures** (`{colors.figures}`): everything a title is *about* — years,
  runtimes, episode and chapter counts, the unselected shelf names, standing-in
  initials, and a ledger row's value. Present, and quieter than a title.

### Tertiary

- **Ochre** (`{colors.ochre}`): the only thing the catalogue ever warns about. A
  refresh that failed is a warning, not an alarm — the library on screen is
  still every bit of the one that was there before — so ochre, not a signal red,
  is bound to Material's `error` role.
- **Sage** (`{colors.sage}`): the only good news the catalogue has, something
  already held on the device. Bound to `tertiary`. Marks a ledger row's held
  dot on a Storage screen.

### Neutral

- **Ground** (`{colors.ground}`): the deepest ink. The window is cleared to it
  before a single composable draws, and the top app bar sits on it.
- **Page** (`{colors.page}`): one step up from the ground — the sheet the plates
  are tipped onto. The wall and the masthead are on the page.
- **Sunk** (`{colors.sunk}`): where artwork is missing and the page itself shows
  through. The plate's own ground behind a poster, behind initials, and a
  toggle's track at rest.
- **Sidebar** (`{colors.sidebar}`): a settings index's own ground — the one
  surface this palette makes *darker* than the window it opens in, not
  lighter. A viewer reads it as a rail, the same way the web's own sidebar
  reads, not as another step of the ground/page/sunk stack.
- **Text** (`{colors.text}`): paper, at the weight a dark page carries without
  glare. Titles, wordmark, selected shelf, a page's own huge title.
- **Rule** (`{colors.rule}`): a hairline. Structure, never a boundary anyone has
  to look at. Every plate's edge, a pill's own quiet border.
- **Rule Soft** (`{colors.rule-soft}`): quieter still — the divider under a
  ledger row, the line between a settings index and the page beside it.
- **Rule Strong / quiet** (`{colors.rule-strong}`): visible enough to read as
  an edge where one is doing work; Material's `outline`. Doubles as *quiet*
  text — a page's eyebrow, a ledger row's label, a settings row's sub-label —
  through `CatalogueTones.quiet`, the same value under a name a settings
  screen reaches for without knowing it is reading an outline role.

### Named Rules

**The One Accent Rule, widened.** Imprint red still says one thing: *this is
the shelf you are on / this is the thing you are in the middle of / this is
what you would undo by tapping it*. What changed is where it is allowed to
say that: the catalogue screen still spends it on nothing but the selected
shelf's rule, but a settings screen may also spend it on a focus ring, a
selected row's background tint, and an outline pill's or a toggle's border —
the same set of places the web spends its own accent (`web/DESIGN.md`'s Do's
and Don'ts: *"use the active accent for focus outlines, active states, and
interactive hints"*). It is still never a heading colour, never a link, and
never a solid fill spanning a whole surface — an outline pill's *border* is
accent; its background is not.

**One Palette, One Ground.** Android's palette is the web player's dark
theme, not a re-lit variant of it. Ground is darker than page; page is
darker than sunk; the sidebar is darker again than all three. Any surface
lighter than the page it sits over breaks the relation the palette names —
which is why the top app bar is on ground, not on a lifted container, and why
a settings index sits on its own darker sidebar rather than on the page.

**The Measured Colour Rule.** A colour that carries text is measured against
every ground a viewer can land it on before it ships. Every text-carrying
value in this palette clears 4.5:1 on Ground, Page, Sunk and Sidebar, held by
`PaletteContrastTest`.

## Typography

**Display Font:** Fraunces (variable, `wght` 500–600, `opsz` 28–112)
**Body Font:** Newsreader (variable, `wght` 400–600, `opsz` 16)
**Interface Font:** Geist (variable, `wght` 400–600)

All three are the exact files the web player loads, decompressed from the same
woff2 sources; OFL licences ship in `android/core/designsystem/licenses/`.

**Character:** A press catalogue's pairing, now with the web's own third
voice added for chrome. Fraunces names things — it has the weight and the
slight eccentricity of a title set on a cover, at arm's length or, on a page
head, at the scale of a magazine's own department opener. Newsreader says a
whole sentence — loading, empty and failure states, nothing else. Geist is
everything that reads as interface rather than as prose: counts, labels,
eyebrows, settings rows, ledger lines. None of the three is ever asked to do
either of the others' jobs.

### Hierarchy

- **Display** (Fraunces Medium, 26sp, −0.1sp): section headings.
- **Headline** (Fraunces Medium, 23sp, −0.1sp): the top app bar title — the
  wordmark on the catalogue, the name of a collection or title elsewhere.
- **Title** (Fraunces Medium, 18sp): shelf names in the masthead; standing-in
  initials on a plate with no artwork.
- **Title Small** (Fraunces Medium, 16sp): the name under a plate, to two lines
  then ellipsis; a settings index row's own label.
- **Page Title** (Fraunces Medium, uppercase, −0.03em, 0.86 line height,
  stepped down from 112sp on a phone/tablet or 72sp compact/TV): the huge
  title atop a page — `PageHead`'s own title slot.
- **Body** (Newsreader, 17sp): whole sentences — loading, empty, and failure
  messages. The one role kept in the reading face rather than moved to Geist.
- **Body Small** (Geist, 13sp): the notice above a shelf.
- **Eyebrow** (Geist Medium, 11sp, 0.32em tracking, uppercase): the spaced
  caps over a page title — `PageHead`'s own eyebrow slot.
- **Section Head** (Geist SemiBold, 16sp): a settings section's own heading.
- **Figures** (Geist, 13sp, `tnum`): the line under a plate's name — year
  and runtime, or the count of episodes and chapters.
- **Label** (Geist, 11sp, `tnum`): the smallest figures.
- **Ledger Label** (Geist, 14sp, quiet): what a ledger row's value is *of*.
- **Ledger Value** (Geist, 15sp, `tnum`): a ledger row's own value, right-aligned.

### Named Rules

**The Optical Size Rule.** Android does nothing automatic with `opsz` — there is
no `font-optical-sizing` here. Every Fraunces and Newsreader declaration fixes
the axis at the distance it is read from: 112 for a page title, 28 for a name
held at arm's length, 16 for a sentence or a figure. Geist carries no optical
axis at all — it has none to fix. A new face declaration that omits the axis
it does have gives up the reason these files are worth their bytes.

**The Tabular Figures Rule.** Anything countable — runtimes, years, counts,
sizes, a ledger's own values — is set in tabular numerals, so a column of them
lines up down a page instead of shifting with each digit's width.

**The Fraunces Floor.** Fraunces is never asked for a weight below 500. On an
ink ground its 400 goes thin enough to shimmer, and the catalogue sets its names
in medium on paper anyway.

**The Whole-Sentence Rule.** A first run, an empty library, a failed load and
a page's own eyebrow-and-title pair are the moments a viewer reads a line
meant to be read rather than scanned. The first three are set in Newsreader;
the eyebrow is deliberately not — it is a label, not a sentence, and Geist is
what a label is set in here.

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
tonal: four grounds — ground, page, sunk, sidebar — and a hairline in one of
two weights. Nothing is lifted, nothing floats, and no Material elevation
overlay is used.

### Named Rules

**The Hairline Rule.** Structure is drawn with a hairline in rule or rule-soft
(`{colors.rule}` / `{colors.rule-soft}`), never with a shadow, a fill or a
raised container. On a plate the hairline is painted as an overlay on top of
the artwork, not as a border on the same box — a border modifier paints
beneath the content, and a poster cropped to fill would cover it.

**The No Floating Container Rule.** The masthead's container is transparent so it
reads as type on the page. Given a colour of its own it becomes a filled band
floating between the bar and the wall, which is the one thing this world does not
do.

## Shapes

Two shapes, not one. A control — an input, a button, a pill, a settings row,
a dialog — takes `{rounded.control}` (6dp), the web's own `--radius`, through
M3's `Shapes(extraSmall..large = RoundedCornerShape(6.dp))`. A plate stays
square at `{rounded.plate}` (0dp): a plate is a rectangle of artwork with a
hairline around it, not a control, and the catalogue still has no rounded
poster, no clipped silhouette on artwork. `extraLarge` (sheets, full-screen
dialogs) is left at M3's own default — nothing on this catalogue draws one
yet.

The 2:3 plate remains the one recurring square silhouette, on every surface
that shows artwork. A swatch card (Appearance's theme/artwork picker) is the
one deliberate exception to 6dp on the *other* side: it takes a 12dp corner,
the web's own `.swatch` radius, because it is a picture of a theme rather
than a control that acts on one.

## Components

### Plate (signature component)

The catalogue's one real component: artwork at 2:3, a hairline around it, the
name beneath in Fraunces Medium 16sp to two lines, and a figures line 2dp under
that in Geist 13sp tabular.

- **Corner style:** square (`{rounded.plate}`).
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
- **No icons.** There are no icons in the masthead, and a drawn one would be
  inventing a mark for a shelf that already has a name.

### Top app bar

- **Container:** ground (`{colors.ground}`) — darker than the page below it.
- **Title:** Fraunces Medium 23sp in paper. The wordmark at the root; the name of
  whatever is open elsewhere, read from the destination so no two screens can
  spell it differently.
- **Icons:** the platform's own vector back and overflow marks, in figures grey,
  each carrying a content description.
- **Exception:** Settings and System draw no top app bar at all. `PageHead`
  opens every section instead, and Back is the pane's own rule (the index's
  arrow at compact width, the frame's leave action at expanded) rather than
  an app-bar icon — the approved mockups draw no bar, so this screen owns
  its own back behaviour entirely.

### Page head

The huge uppercase page title over a small tracked-caps eyebrow — the web's
`.dept-title`/`.eyebrow` pair, and this catalogue's opener for a screen that
is not the wall: Settings, System, and anywhere else a screen wants to open
the way a magazine department does.

- **Eyebrow:** Geist Medium 11sp, 0.32em tracking, uppercase, in quiet
  (`{colors.rule-strong}`).
- **Title:** Fraunces Medium, uppercase, −0.03em tracking, 0.86 line height,
  in text (`{colors.text}`), stepped down from 112sp (72sp compact/TV) to fit
  one line rather than wrapping or clipping.
- **Semantics:** eyebrow and title merge into one heading node, the same way
  a plate merges artwork and caption.

### Settings index row

A left-pane row in a Settings/System index: an icon, a label in Fraunces
Medium 16sp, and a one-line status underneath in quiet Geist 12sp.

- **Corner style:** `{rounded.control}` (6dp).
- **Rest:** figures (`{colors.figures}`) label, quiet sub-label.
- **Selected:** text (`{colors.text}`) label, a tinted background reading as
  the row a viewer is on — the one place besides the masthead's rule this
  catalogue tints a whole row rather than drawing an accent border.
- **Target:** 64dp minimum height, 12dp/14dp padding, the whole row.

### Ledger

A settings/system screen's own table: a quiet label left, a value right,
tabular, divided by a soft rule.

- **Row:** 48dp minimum height, `{colors.rule-soft}` divider beneath.
- **Label:** Geist 14sp in quiet (`{colors.rule-strong}`).
- **Value:** Geist 15sp tabular in figures (`{colors.figures}`); a held row's
  own leading dot is sage (`{colors.sage}`).

### Pill (line / quiet)

A 6dp-radius outline control, 48dp minimum height, for an action a settings
screen offers.

- **Line:** border and text in imprint (`{colors.imprint}`) — an affirmative
  action (add an account, start a scan).
- **Quiet:** border in rule (`{colors.rule}`), text in figures
  (`{colors.figures}`) — an action that undoes something (sign out, clear a
  cache).
- **Chip:** the same line pill at a 36dp compact height, for a smaller choice
  inline with text rather than in its own row.

A film's own Preload control (Android only — the web player has no film
preload) reuses this pair rather than inventing a third look: Line while
idle, retrying a failure, or retrying once the cache budget has been
raised (NeedsSpace); Quiet once queued, running, done, or paused for any
reason — a background-limit pause resumes on a tap too, but is a queue
unclogging on its own, not a fresh choice the viewer made, so it stays
Quiet with the rest of a pause rather than reading as a new affirmative
action. TV reads the same states as a focusable plate, in this catalogue's
one television focus treatment — the accent border and scale every other
TV card and row already carries — with the same thin bar the phone draws,
drawn plainly rather than focused (nothing here answers a direction key;
cancelling or resuming is the plate's own OK, not the bar's).

The Preloads page (Android only, the same reason) is a plain list rather
than a poster wall — three sections, Preloading/Queued/On this device, each
row a title beside one action (Cancel or Remove) — since what it shows is a
handful of films at most, not a library's worth of cards. A queued film's
own Preload control names what it is waiting on the same way: the running
film's own title and percent when it is next, otherwise how many films
stand ahead of it, read off the engine's one ordered queue rather than kept
apart from what the Preloads page itself shows.

### Toggle

A settings switch, drawn rather than left at Material's default: track in
sunk (`{colors.sunk}`) with a quiet (`{colors.rule-strong}`) border at rest,
imprint (`{colors.imprint}`) border and fill when on. The thumb's own
on-accent ink has no Palette token yet — see What this system does not yet
cover.

### Swatch card

A picture of a theme or an artwork mode (Appearance's own pickers): 16:9,
12dp corner (the one deliberate exception to `{rounded.control}` — see
Shapes), a rule hairline at rest, ringed in imprint (`{colors.imprint}`) when
selected.

### Artwork

Appearance's fourth picker (Default/Blurred/Artwork/Solid — the web's own
setting, ported), drawn everywhere a hero shows a title or department's own
art: a film's title page, a department's cover story, Movies and Shows.

- **Default:** artwork fades into the page — the hero draws plainly.
- **Blurred:** the same artwork behind a 28dp blur, scaled 1.12× and
  saturated 1.25×, so the colour and light carry without the picture
  competing with the words over it.
- **Artwork:** "the picture behind the words" — today this draws exactly as
  Default does. The web's own version of this mode only separates from
  Default in a wide two-column layout (`departments.css`'s ≥900px
  breakpoint); Android has not built that layout yet (see What this system
  does not yet cover), so a phone or a tablet showing the two identically is
  web narrow parity, not a bug — the web's own narrow width draws them the
  same way.
- **Solid:** no artwork at all — the hero art, its gradient and the
  department's own pull-quote are skipped entirely; a film's title page and
  a department's own cover story fall back to the same no-art layout each
  already had for a title with nothing fetched. Home is unaffected: its own
  cover story keeps its art regardless of this setting.
- **TV answers two of Appearance's four choices, not all of them.** Accent
  and Artwork reach the television the same way they reach a phone; Theme
  (Dark/Light/Auto) does not — the television stays dark regardless (`docs/
  system-architecture.md` § Television differs from the web player).

### Notice

- A single line of Geist 13sp in ochre, above the shelf and never instead of
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
- **Do** set names in Fraunces at 500 or above, whole sentences in Newsreader,
  and everything else that reads as interface — labels, counts, eyebrows,
  settings rows, ledgers — in Geist.
- **Do** fix `opsz` explicitly on every Fraunces or Newsreader declaration:
  112 for a page title, 28 for a name at arm's length, 16 for a sentence or a
  figure.
- **Do** set every figure in tabular numerals.
- **Do** draw structure with a hairline in rule or rule-soft, and keep surfaces
  flat.
- **Do** measure any new text-carrying colour against every ground it can land
  on (`PaletteContrastTest`) before it ships.
- **Do** take the colour of a new surface from Material roles: a component that
  reaches for `colorScheme.surface` knowing nothing about this app must land on
  the page.
- **Do** round a control 6dp (`{rounded.control}`); leave a plate and a swatch
  card at their own radius instead.
- **Do** keep the app dark on every device, regardless of system theme. A media
  library is looked at in the dark.

### Don't:

- **Don't** ship a horizontally-scrolling poster rail.
- **Don't** round a plate's corner. Its radius is `{rounded.plate}` (0dp).
- **Don't** add a shadow or a Material elevation overlay. Depth here is tonal,
  and the hairline already carries the job a shadow would.
- **Don't** give a masthead, a shelf header or a grouping band a container colour.
  A filled container floating between the bar and the wall is the one thing this
  world does not do.
- **Don't** fill a whole control or surface with imprint red. It borders, rings,
  and underlines; it does not flood.
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

- **Two screens' worth of system now, not one.** The catalogue screen and
  Settings/System are both composed and shipped: a two-pane index (Telegram
  · Storage · Appearance · System) on phone and tablet, and a second,
  D-pad-driven two-pane build on television, both drawing the
  Settings-index-row, ledger, pill, toggle, swatch-card and artwork entries
  above. A menu shortcut still opens System directly, without the index.
  The player, the title detail screen and the collection screen have not
  been restyled at all; they inherit the palette and type through the theme
  but their own composition is undocumented and unreviewed — a sweep of
  what that leaves off-token is tracked outside this file (see the
  Settings/System redesign plan's own review).
- **This palette change touches every screen at once.** Every surface that
  reads `MaterialTheme.colorScheme` or a body/label typography role now reads
  the web's dark theme and Geist rather than the values this system replaces.
  Nothing here fixes a screen whose own layout assumed the old, warmer ground
  or an all-Newsreader interface; that sweep is separate work, tracked
  outside this file.
- **The toggle's on-accent ink is undecided.** The web's own `--on-accent`
  (the thumb colour on a filled track) has no equivalent in `Palette` yet —
  it is a fixed value tuned to Coral specifically, not a role every accent
  resolves the way the others do.
- **No component vocabulary for dialogs or lists.** The catalogue has none of
  these, so none are recorded. They are Material defaults wherever they
  appear today, at the new 6dp radius.
- **The player's palette is unresolved on Android.** The web player's own
  screening room is this catalogue's own dark ground now, which narrows this
  question rather than closing it: whether playback still wants a second,
  darker palette of its own is open.
- **Dynamic type is untested.** The scale is declared in `sp`, so it will
  respond to the system font scale, but no size has been checked at a large
  setting, and `TextAutoSize.StepBased` on the page title has not been
  checked against one either.
- **API 24–25 render every face at its default instance.** Variation settings
  are ignored there, so the optical-size axis and the weight axis do nothing on
  those two releases. The fallback is legible; it is not the designed type.
