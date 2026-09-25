---
name: Mediagram (web player)
description: A printed catalogue of things already owned, set on uncoated stock, with a black room to watch in.
colors:
  paper: "#f4f0e7"
  paper-sunk: "#e9e2d3"
  ink: "#1e1b16"
  ink-2: "#5f584c"
  ink-3: "#8b8375"
  rule: "#d5ccb8"
  rule-soft: "#e3dbc9"
  imprint: "#8c3b2e"
  ochre: "#866021"
  sage: "#4a6741"
  stage: "#0d0c0b"
  stage-raised: "#17150f"
  stage-ink: "#ece7db"
  stage-ink-2: "#968e7d"
  stage-rule: "#2c2721"
  stage-amber: "#d99a63"
typography:
  wordmark:
    fontFamily: "Fraunces, Iowan Old Style, Georgia, serif"
    fontSize: "1.85rem"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "-0.015em"
  display:
    fontFamily: "Fraunces, Iowan Old Style, Georgia, serif"
    fontSize: "clamp(1.9rem, 4.2vw, 2.6rem)"
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "-0.012em"
  headline:
    fontFamily: "Fraunces, Iowan Old Style, Georgia, serif"
    fontSize: "1.35rem"
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "-0.008em"
  title:
    fontFamily: "Fraunces, Iowan Old Style, Georgia, serif"
    fontSize: "1.22rem"
    fontWeight: 500
    lineHeight: 1.25
    letterSpacing: "-0.005em"
  title-small:
    fontFamily: "Fraunces, Iowan Old Style, Georgia, serif"
    fontSize: "1.02rem"
    fontWeight: 500
    lineHeight: 1.25
  body:
    fontFamily: "Newsreader, Iowan Old Style, Georgia, serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.55
  figures:
    fontFamily: "Newsreader, Iowan Old Style, Georgia, serif"
    fontSize: "0.875rem"
    fontWeight: 400
    fontFeature: "tnum"
  label:
    fontFamily: "Newsreader, Iowan Old Style, Georgia, serif"
    fontSize: "0.78rem"
    fontWeight: 400
    letterSpacing: "0.1em"
    fontFeature: "tnum"
rounded:
  none: "0"
  hairline: "2px"
  dot: "50%"
spacing:
  gutter: "clamp(1.25rem, 6vw, 5.5rem)"
  measure: "62rem"
  row: "1.35rem"
  row-gap: "2.9rem"
components:
  masthead-link:
    textColor: "{colors.ink-2}"
    typography: "{typography.body}"
    padding: "0 0 0.15rem 0"
  masthead-link-active:
    textColor: "{colors.imprint}"
    typography: "{typography.body}"
    padding: "0 0 0.15rem 0"
  search-field:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "0.42rem 0 0.42rem 0.1rem"
  plate:
    backgroundColor: "{colors.paper-sunk}"
    textColor: "{colors.ink-3}"
    rounded: "{rounded.none}"
  index-row:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.title}"
    rounded: "{rounded.none}"
    padding: "1.35rem 0.25rem"
  tag:
    backgroundColor: "transparent"
    textColor: "{colors.ink-3}"
    typography: "{typography.label}"
    rounded: "{rounded.hairline}"
    padding: "0.15rem 0.5rem"
  tag-warn:
    textColor: "{colors.ochre}"
    typography: "{typography.label}"
    rounded: "{rounded.hairline}"
    padding: "0.15rem 0.5rem"
  tag-held:
    textColor: "{colors.sage}"
    typography: "{typography.label}"
    rounded: "{rounded.hairline}"
    padding: "0.15rem 0.5rem"
  quiet-control:
    backgroundColor: "transparent"
    textColor: "{colors.ink-3}"
    typography: "{typography.label}"
    padding: "0.2rem 0"
  quiet-control-hover:
    textColor: "{colors.imprint}"
  stage-play:
    backgroundColor: "{colors.stage-ink}"
    textColor: "{colors.stage}"
    typography: "{typography.label}"
    rounded: "{rounded.hairline}"
    padding: "0.6rem 1.3rem"
  stage-play-hover:
    backgroundColor: "{colors.stage-amber}"
    textColor: "{colors.stage}"
---

# Design System: Mediagram (web player)

## Overview

**Creative North Star: "The Catalogue Printed on Paper"**

The web player is the reference surface, and this is its record. It sets the
household's library the way a small press sets its backlist catalogue: warm
uncoated stock, two serifs, a wide margin the page makes no attempt to fill,
hairline rules where another product would draw cards, and artwork set as
plates with the caption beside them. Nothing here is sold, ranked or
recommended, so nothing borrows from a streaming storefront. The Android app
prints the same catalogue in ink (`../DESIGN.md`); this file is the paper it
was inverted from.

The page is typographic before it is visual. Hierarchy comes from face,
size and case, not from boxes or colour: a shelf is a display-face title
over a rule, a row is a name and its figures set at two ends of one line,
and every fact about a title that is not its name is set small, spaced, in
capitals or in tabular figures, the way a catalogue sets extents and call
numbers. Density is calm rather than packed; the measure stops at 62rem even
on a wide screen, because a catalogue is not trying to fill the window.

One room breaks the paper, on purpose. The player, and the Featured reel that
is its lobby, are black and carry their own palette. A poster reads best
against a page; a picture reads best against nothing. The two rooms share
the faces and the restraint, not the stock.

**Key Characteristics:**

- Warm uncoated paper, never white; ink, never black, on the page.
- Hairline rules instead of cards; a double rule under the masthead.
- Plates at the 2:3 a poster is drawn at, tipped onto the page.
- One imprint red for "where you are" and "what you are pointing at".
- Two variable serifs driven by optical size: Fraunces to name, Newsreader to tell.
- A separate black screening room with its own amber accent.

## Colors

A warm, low-chroma palette of paper and brown-black inks, with exactly three
coloured voices on the page and one in the screening room.

### Primary

- **Imprint Red** (`imprint`): the shelf you are on, the title under the
  pointer, the progress rule and the resume line, focus outlines, and the
  one control in a row that plays rather than edits. A printer's red, not a
  brand red: it marks, it does not decorate.

### Secondary

- **Ochre** (`ochre`): the only warning the catalogue ever gives — a title
  the link or the browser cannot take as it is. Tag text, with a border at
  45% of itself.

### Tertiary

- **Sage** (`sage`): the only good news the page has. A title already held on
  this disk, a finished title's tick, the kids mark on a profile. Chosen over
  a signal green because the palette is uncoated stock and a UI green sits on
  top of it rather than in it.

### Neutral

- **Uncoated Paper** (`paper`): the page. Pure white under a serif at this
  size reads as a word processor.
- **Sunk Paper** (`paper-sunk`): the ground an absent poster leaves, a
  profile tile, an inline `code`.
- **Ink** (`ink`): titles and running text.
- **Second Ink** (`ink-2`): figures beside a title, the masthead's resting
  links, empty-state prose.
- **Third Ink** (`ink-3`): the quietest ink — counts, call numbers, crumbs,
  small-capital controls at rest, placeholders.
- **Rule** (`rule`) and **Soft Rule** (`rule-soft`): the hairlines. `rule`
  under a heading and in the masthead's double rule; `rule-soft` between one
  row of an index and the next, where a heavier line would chop the list up.

### The screening room

- **Stage** (`stage`) and **Raised Stage** (`stage-raised`): the black the
  reel and the player's panels sit on. The picture itself sits on `#000`.
- **Stage Ink** (`stage-ink`) and **Second Stage Ink** (`stage-ink-2`):
  text over the picture and its quieter figures.
- **Stage Rule** (`stage-rule`): hairlines and inactive reel dots.
- **Stage Amber** (`stage-amber`): the screening room's single accent, in the
  imprint red's role. Red on black reads as an alarm; amber reads as a lamp.

### Named Rules

**The One Accent Rule.** Imprint red marks position and intention — where
you are, what you point at, what will play. Never a heading, a background, a
decoration or a second meaning.

**The Pair of Facts Rule.** Ochre and sage are a pair and the page's only
colours for a fact about a title: ochre for what will cost something, sage
for what will not. A third status colour breaks the pair.

**The No White Rule.** Nothing on paper is `#fff` and nothing is `#000`. The
page's white is `paper`, its black is `ink`. True black belongs to the
picture alone.

## Typography

**Display Font:** Fraunces (with Iowan Old Style, Georgia, serif)
**Body Font:** Newsreader (with Iowan Old Style, Georgia, serif)
**Label Font:** Newsreader, in spaced capitals and tabular figures

**Character:** A soft, wonky display serif that names things, beside a text
serif built for reading at small sizes. Both are variable with an
optical-size axis, and `font-optical-sizing: auto` lets one file set a 44px
wordmark and a 12px codec line without either looking scaled.

### Hierarchy

- **Wordmark** (600, 1.85rem, 1): "mediagram" in the masthead only; 1.5rem
  under 720px.
- **Display** (500, clamp 1.9–2.6rem, 1.1): a shelf's or a title page's own
  name. One per page. The reel's title runs larger (clamp 2–3.6rem) because
  it is set on black against a single poster.
- **Headline** (500, 1.35rem, 1.1): a row's name on the start page. Rows are
  subordinate to the page, so they never take the display size.
- **Title** (500, 1.22rem, 1.25): a film, show or course in an index row;
  **Title Small** (1.02rem) on a plate, where the artwork already speaks.
- **Body** (400, 1.0625rem, 1.55): running text. Excerpts and overviews stop
  at 68ch.
- **Figures** (400, 0.875rem, tabular): year, runtime, count, codec — the
  line of detail that tells two titles apart.
- **Label** (400, 0.68–0.82rem, +0.09–0.16em, uppercase, tabular): crumbs,
  pager, shelf modes, tags, "see all", quiet controls, the colophon.

### Named Rules

**The Two Faces Rule.** Fraunces names a thing; Newsreader tells you about
it. A label, a figure or a control is never set in Fraunces, and a title is
never set in Newsreader.

**The Footnote Rule.** Counts ride high and small beside what they count —
superscript in the masthead, a quieter figure after a row's name, flush
right on a shelf head where a catalogue prints an extent. Never a pill, never
a bubble.

**The Sixteen Pixel Floor.** A field's text is never below 1rem; smaller, and
a phone zooms the page on focus.

## Layout

A single centred column: `main` is capped at the 62rem measure and set inside
a gutter of `clamp(1.25rem, 6vw, 5.5rem)`. The masthead runs the full width
with the same gutter, and pages change beneath it; only the page turns
(140ms out, 280ms in, a 60ms beat between), never the masthead.

Shelves come in two layouts of the same card. The **index** is one title per
line: a 4.75rem plate beside a two-ended line of name and figures, 1.35rem
above and below, a soft rule between rows. The **wall** is a grid of plates,
`repeat(auto-fill, minmax(11rem, 1fr))` with 2.4rem × 1.75rem gaps —
`auto-fill` so three films stay the size of three plates rather than
stretching into three enormous posters. The start page stacks rows of six,
each a window onto a shelf, separated by 2.9rem and a rule under each head.

A course nests: top-level folders read as page headings over a rule; deeper
folders indent 0.6rem with a soft rule down the left, and stop indenting
after the fourth level.

**Responsive.** Two breakpoints. At 720px the wordmark, the nav and the
search each take a full line and the nav wraps rather than scrolling
sideways; the wall becomes exactly two plates across (stated, not fitted:
`auto-fill` at 11rem would collapse to one); an index row stacks its figures
under its name; the player's rails lose padding and the technical line. At
560px the title-page artwork narrows.

### Named Rules

**The Wall, Not a Rail Rule.** A shelf shows everything it holds in one
direction of travel. No horizontally scrolling poster row: a rail hides how
much is on a shelf and promotes whatever happens to be first.

**The Unfilled Margin Rule.** The measure is 62rem on every screen. Width
beyond it goes to margin, never to more columns of text.

## Elevation & Depth

Paper is flat. Depth on the page is tonal — paper, sunk paper, a hairline —
with one exception: a plate is tipped onto the page, and lifts when it is
pointed at. Its resting shadow is a hairline ring and a short warm fall-off
(`0 0 0 1px rgba(30,27,22,.13), 0 2px 3px -1px rgba(60,45,25,.16), 0 10px
18px -12px rgba(60,45,25,.5)`); under the pointer it rises 2px, the ring
takes the imprint red at 30%, and the fall-off lengthens. Profile tiles
share the same shadow and lift.

In the screening room shadows are for legibility, not depth: a 1–3px text
shadow at 80–90% black keeps words readable over whatever frame is showing,
gradients behind the rails do the same for the controls, and the reel's
poster sits on a long 30px/80px shadow over a blurred, darkened copy of
itself.

### Named Rules

**The Tipped Plate Rule.** Surfaces are flat at rest. The only thing that
casts a shadow on paper is a plate (or a profile tile), and it rises only in
response to the pointer. No shadow on rows, headings, panels or controls.

**The Legibility Shadow Rule.** On black, a shadow exists to keep text
readable over a moving picture. It is never decorative, and it never
appears on the paper.

## Shapes

Square. Plates, rows, the player, fields and the index have no radius at
all; the only rounding is a 2px corner on bordered tags and bordered
buttons, where a perfectly square outline at that size reads as a form
field, and 50% for the handful of things that are points rather than surfaces: the
seek bar's thumb, a transport control, and the reel's 7px dots, all in the
screening room.
Lines do the work shapes would: a 1px rule under a heading, a 1px rule
between rows, a 1px underline for the active nav item and every hovered
text control, a 3px double rule under the masthead, a 1px rule down the left
of a nested folder.

## Components

### Plate (signature component)

The artwork of a film, show or course, held at 2:3 so no poster is cropped.
- **Shape:** square corners, `object-fit: cover` inside an absolutely placed
  image so an off-ratio poster cannot stretch the plate.
- **Absent artwork:** sunk paper with the title's initials in Fraunces,
  third ink, spaced 0.06em.
- **Marks:** a finished title carries a sage tick in the top-right corner;
  progress uses the other edge, so the two never overlap.
- **Hover:** lifts 2px with a red-tinged hairline (see Elevation), and the
  title beside it turns imprint red.

### Index row

The card as a line of the catalogue: plate, then name in Title and the
figures in Second Ink flush right, with a resume line in imprint red and any
tag dropped to its own line under the name. A word-control ("Mark finished")
sits at the row's lower right. Soft rule underneath. Under 720px the figures
stack beneath the name.

### Lesson row

A course's lesson or document: a call number in spaced tabular capitals, a
title in medium Newsreader over a one-line subtitle in third ink, figures
right. Hover washes the row in imprint red at 4.5% — the only background tint
on paper. A folder row sets its title in Fraunces and ends in a chevron that
shifts 3px right on hover.

### Masthead and navigation

The wordmark left, the shelves in a line of Newsreader links, the search
field, and who is watching. Resting links are second ink; hover goes to ink;
the active shelf is imprint red with a 1px underline, its count riding as a
superscript footnote. Shelves derived from watching are ruled off from the
catalogue's shelves by a hairline, and the player's own status entry is
ruled off again and set in third ink. A 3px double rule closes the masthead.

### Search field

A ruled line to write on, not a box to fill in: no background, no border but
a 1px rule underneath, which turns imprint red on focus. 1rem text,
placeholder in third ink.

### Controls

- **Quiet control:** a word, not a thing — rename, delete, stay, see all,
  shelf modes. Spaced third-ink capitals, a transparent 1px underline that
  becomes imprint red with the text on hover. The one that plays in a row of
  them is imprint red at rest.
- **Outlined action:** the play button on a title page — imprint red text in
  a 1px imprint red outline with a 2px corner; filled on hover.
- **Stage play (reel):** stage-ink fill, stage text, 2px corner, spaced
  capitals; stage amber on hover. Its companion "Details" is an outline in
  second stage ink.
- **Focus:** a 2px imprint red outline, 3px off the element — louder than
  the browser's, in the page's colour. Stage amber inside the screening room.

### Tags

Three statements share one shape: why a search hit matched (third ink), that
a title must be converted before it plays (ochre), that it is already on this
disk (sage). Spaced capitals in a 1px border at 45% of the text colour, 2px
corner. On a wall they shrink to 0.6rem so twelve of them do not compete with
twelve posters.

### Profile picker

"Who is watching" takes the whole window on paper before anything else,
because every shelf is one profile's. Square tiles of sunk paper with an
initial in Fraunces, sharing the plate's shadow and lift; a kids profile
says so in small sage capitals under its name.

### The screening room

The player is a full-window `dialog` with its own dark `color-scheme`, so its
scrollbars and menus come up dark. The picture is letterboxed, never cropped.
Controls float on two rails, top and bottom, over gradient scrims that pass
clicks through, and fade once the pointer rests.

### Featured reel

The screening room's lobby: one unwatched film at a time on black, the poster
drifting and growing over 8.5s above a blurred, darkened copy of itself; the
copy (count in amber capitals, title, facts, italic tagline, actions) rises
in on a 150ms stagger; slides cross-fade over 1.2s. Reduced motion leaves
still posters and cuts.

## Do's and Don'ts

### Do:

- **Do** set every surface on `paper` and every word in one of the three
  inks; reach for `paper-sunk` only where something is absent or inset.
- **Do** separate with a 1px `rule` or `rule-soft`, and close the masthead
  with the 3px double rule.
- **Do** keep artwork at 2:3 and let it lift only under the pointer.
- **Do** set facts as figures (tabular) and controls as spaced capitals;
  keep Fraunces for names.
- **Do** mark where the viewer is, and what will play, in imprint red — and
  only those.
- **Do** say why something is unavailable, in words, under its own label.
- **Do** carry the screening room's own palette into anything that sits on
  the picture, including `color-scheme: dark`.
- **Do** honour `prefers-reduced-motion`: transitions and animations collapse
  to 1ms and the plate stops lifting.

### Don't:

- **Don't** draw cards, panels with backgrounds, or rounded boxes on paper.
- **Don't** add a horizontally scrolling rail of posters.
- **Don't** use `#fff` or `#000` on the paper, or a UI blue, green or red.
- **Don't** introduce a fourth coloured voice: no second accent, no status
  colours beyond ochre and sage.
- **Don't** fill the width: never widen the measure past 62rem for text.
- **Don't** add a dark mode to the browsing pages by default. The paper is
  the chosen direction; a dark mode was offered, not built, and remains a
  user decision.
- **Don't** put a shadow on anything on paper but a plate or a profile tile.

## What this system does not yet cover

Recorded 2026-09-25 from `web/public/style.css`. Known gaps and drift, so they
are fixed rather than copied:

- **The title-page block drifts.** `.series-header`, `.film-page` and their
  neighbours are set in px (13–15px, 18–22px gaps) rather than rem; read an
  undefined `var(--dim)`, so four rules silently fall back to full ink.
  Match them to this record, don't extend them. (A navy gradient behind a
  missing poster and two `#fff` hover fills were removed on 2026-09-25.)
- **Contrast has been measured, not judged.** Ink 15.1:1, second ink 6.2:1,
  imprint 6.6:1, ochre 5.0:1, sage 5.6:1 on paper; stage ink 15.8:1, second
  stage ink 6.0:1, amber 8.2:1 on stage. Third ink is **3.3:1 on paper** (2.9:1
  on sunk paper), and it carries most small capitals at 0.68–0.82rem; a
  one-off `#a89f8c` folder count is 2.3:1. No standard has been set, so this
  is a fact for that decision, not a verdict.
- **One-offs outside the palette:** the selection tint `#e7cdbf` and the
  folder count `#a89f8c`.
- **No dark browsing pages** exist. See the Don'ts: this is open, not
  rejected.
