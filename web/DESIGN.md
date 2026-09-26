---
name: Mediagram (web player)
description: A dark-first magazine catalogue with editorial departments, tabbed feature pages, and accent colour customization.
colors:
  dark:
    paper: "#0d0d0e"
    paper-sunk: "#1b1b1d"
    surface: "#151517"
    sidebar: "#09090a"
    ink: "#f3efe7"
    ink-2: "#cbc5ba"
    ink-3: "#9c968b"
    rule: "rgba(243, 239, 231, 0.18)"
    rule-soft: "rgba(243, 239, 231, 0.08)"
    warn: "#e6c47f"
    held: "#a3d3a4"
  light:
    paper: "#f4f0e8"
    paper-sunk: "#e5dfd3"
    surface: "#fbf8f2"
    sidebar: "#ebe5d9"
    ink: "#1b1916"
    ink-2: "#48433b"
    ink-3: "#676157"
    rule: "rgba(27, 25, 22, 0.2)"
    rule-soft: "rgba(27, 25, 22, 0.09)"
    warn: "#7a5510"
    held: "#2f6a35"
accents:
  - name: Coral (default)
    dark: "#e57a61"
    light: "#a3392a"
  - name: Blue
    dark: "#7cb4f0"
    light: "#2c5c9a"
  - name: Violet
    dark: "#b9a1f7"
    light: "#6243a6"
  - name: Teal
    dark: "#5ec9c0"
    light: "#1d6a65"
  - name: Green
    dark: "#93cf80"
    light: "#3a6a2a"
  - name: Amber
    dark: "#e8b058"
    light: "#80530c"
  - name: Rose
    dark: "#f08aa2"
    light: "#a1304d"
typography:
  wordmark:
    fontFamily: "Fraunces, Georgia, serif"
    fontSize: "1.85rem"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "-0.015em"
  display:
    fontFamily: "Fraunces, Georgia, serif"
    fontSize: "clamp(1.9rem, 4.2vw, 2.6rem)"
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "-0.012em"
  headline:
    fontFamily: "Fraunces, Georgia, serif"
    fontSize: "1.35rem"
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "-0.008em"
  title:
    fontFamily: "Fraunces, Georgia, serif"
    fontSize: "1.22rem"
    fontWeight: 500
    lineHeight: 1.25
    letterSpacing: "-0.005em"
  body:
    fontFamily: "Newsreader, Georgia, serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Geist, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    letterSpacing: "0.05em"
spacing:
  gutter: "clamp(1rem, 3.2vw, 3.5rem)"
  measure: "96rem"
  radius: "6px"
components:
  pill-solid:
    padding: "0.42rem 0.8rem"
    borderRadius: "6px"
    backgroundColor: "{accent}"
    textColor: "#fff"
    fontSize: "0.875rem"
    fontWeight: 500
  pill-line:
    padding: "0.42rem 0.8rem"
    borderRadius: "6px"
    border: "1px solid"
    borderColor: "{accent}"
    textColor: "{accent}"
    fontSize: "0.875rem"
    fontWeight: 500
---

# Design System: Mediagram (web player)

## Overview

A dark-first screen for a household's personal video library, structured as editorial departments (Home, Movies, Series, Tutorials, Collections) with tabbed feature pages, full-bleed artwork, and a searchable cast and people directory. The interface adapts to light mode (via Settings › Appearance) and supports seven accent colours, all contrast-tested to 4.5:1 on both themes.

**Key Characteristics**

- Dark screening room (near-black background) for artwork legibility
- Light variant via `data-theme="light"` set by `lib/appearance-boot.js` (before first paint)
- Seven accent colours (coral, blue, violet, teal, green, amber, rose), swappable via Settings
- Fraunces for display and headlines, Newsreader for body and quotes, Geist for UI labels
- Full-width departments with tabbed detail pages (Overview, Cast, Similar, Details)
- Featured reel: up-to-12-film showcase with drifting poster and blurred backdrop
- Magazine home: cover story, three single-title features, Continue row, Latest row
- Cast from schema v9 credits: 12 cast by billing order with circular 185px portraits, people directory

## Colors

The palette flips between dark and light themes, set via radio control in Settings › Appearance. Both themes have the same accent, contrast-tested.

### Dark theme (default)

- **Paper** (`#0d0d0e`): page background, near-black screening room
- **Paper-sunk** (`#1b1b1d`): inset areas (missing artwork, form backgrounds)
- **Surface** (`#151517`): elevated surfaces (cards, modals)
- **Sidebar** (`#09090a`): the rail background (full height)
- **Ink** (`#f3efe7`): headings and body text
- **Ink-2** (`#cbc5ba`): secondary text (navigation at rest, captions)
- **Ink-3** (`#9c968b`): tertiary text (counts, labels, placeholders)
- **Rule** (18% white): principal dividers (under headings, between rows)
- **Rule-soft** (8% white): soft dividers (between shelf items)
- **Warn** (`#e6c47f`): conversion needed, age ratings
- **Held** (`#a3d3a4`): already cached, finished mark, kids label

### Light theme (via Settings)

Warm paper background with dark inks, adapted from the 2026-09-18 design:

- **Paper** (`#f4f0e8`): warm uncoated stock
- **Ink** (`#1b1916`): near-black text
- **Ink-2** (`#48433b`): secondary text
- **Ink-3** (`#676157`): tertiary text
- All seven accent colours adjusted for 4.5:1 contrast on light paper (see accents table, YAML frontmatter)

### Accent system (7 swatches)

Each swatch is a pair (dark value, light value), both contrast-tested to 4.5:1 against that theme's paper. Selectable in Settings › Appearance with a 7-swatch grid:

1. **Coral** (default): `#e57a61` dark, `#a3392a` light
2. **Blue**: `#7cb4f0` dark, `#2c5c9a` light
3. **Violet**: `#b9a1f7` dark, `#6243a6` light
4. **Teal**: `#5ec9c0` dark, `#1d6a65` light
5. **Green**: `#93cf80` dark, `#3a6a2a` light
6. **Amber**: `#e8b058` dark, `#80530c` light
7. **Rose**: `#f08aa2` dark, `#a1304d` light

The active accent is applied via `[data-accent="name"]` selector on `<html>`, set by `lib/appearance-boot.js` and persisted in browser storage (per-screen, not per-profile).

### Artwork-backed blocks (theme-independent)

Cover story, title spreads, and department heroes carry their own light-on-image colours:

- **On-image** (`#f6f2ea`): text over artwork
- **On-image-2** (82% of on-image): secondary text over artwork
- **Progress bar** (`#6fb7e8`): watch progress indicator over artwork

## Typography

**Display Font:** Fraunces (self-hosted, variable, optical-sizing auto)
**Body Font:** Newsreader (self-hosted, variable, upright + italic)
**Label Font:** Geist (self-hosted, sans-serif)

### Hierarchy

- **Wordmark** (600, 1.85rem): "mediagram" in masthead
- **Display** (500, clamp 1.9–2.6rem): page heading (Home, Movies, Series, etc.)
- **Headline** (500, 1.35rem): row heading on home page
- **Title** (500, 1.22rem): film/show in index row; smaller (1.02rem) on plates
- **Body** (400, 1.0625rem): descriptions, taglines, running text; 68ch measure
- **Label** (Geist 400, 0.875rem, spaced caps): navigation, tabs, pills, counts

## Layout

### Departments (new in 0.62.0)

Five top-level sections: Home, Movies, Series, Tutorials, Collections, plus Search and Settings in the rail. Movies shelf pages (`#/movies`, `#/movies/page/N`) show 48 films per page with sequential pager links.

### Home page (magazine)

Fixed-height cover story on a full-bleed TMDB backdrop, followed by three single-title feature cards (Editor's choice, Trending, Staff pick), a Continue row beside a pull-quote, and Recently added beside a "This month" count.

### Title pages (film, series)

Full-bleed backdrop with title, tagline, runtime/network, rating, and description. Three tabs: Overview (description), Cast (cast/crew/creators from v9 credits, up to 12 cast with portraits), Similar (recommendations), Details (metadata). Tabs keep selection and focus on every redraw.

### Collections page

TMDB franchises (≥2 films held) and user-created lists, each a large feature card. Franchise detail shows all films in the franchise with watch state.

### Person page

Circular 185px portrait, name, filmography split into Films and Shows (both filtered to profile visibility).

### Search (grouped)

Results grouped by type (Movies, Series, Episodes, Lessons, People, Collections), with type filter pills. People include only those visible to the current profile.

### Settings page (admin-gated)

Three tabs: **Appearance** (theme Dark/Light/Auto + 7-swatch accent picker), **Profile** (who is watching, Switch profile), **Library & Telegram** (admin-only: Telegram account and sign-in, library, cache budget, sessions — shown only when `/api/settings` answers).

### Rail (sidebar)

Full height. Top: Utilities (My List, Continue Watching, Latest, Genres, Settings). Below: Departments (Home, Movies, Series, Tutorials, Collections). On phone, departments move to the header row below the masthead (no bottom tab bar).

## Motion and easing

- **Ease** (`cubic-bezier(0.32, 0.72, 0, 1)`): standard motion, settling spring
- **Ease-out** (`cubic-bezier(0.22, 1, 0.36, 1)`): exit, responsive motion
- Reduced motion: all transitions collapse to 1ms

## Components

### Pills (new in 0.62.0)

Filter buttons for search and settings.

- **Solid pill** (`pill-solid`): filled with accent, white text, 0.42rem v-padding, 0.8rem h-padding, 6px radius
- **Outline pill** (`pill-line`): 1px border in accent, accent text, same padding

### Tabs (new in 0.62.0)

Film/series detail pages. Tab bar spans full width above content. Active tab has underline in accent, text in ink. Selection persists on every redraw; focus restores to the previously active tab.

### Destination cards (new in 0.62.0)

Collections detail, person detail, and search results. Large feature cards or rows with poster, title, metadata.

### Department hero (new in 0.62.0)

Full-bleed backdrop at the top of Movies, Series, Tutorials, Collections pages. Shows dept title, tagline, genre pills. Artwork mode selectable in Appearance:
- **Default**: artwork fades into paper
- **Blurred**: artwork blurred and saturated, darkened
- **Artwork**: artwork full-opacity behind text
- **Solid**: no artwork, solid paper background

### Plate (existing, refined)

Film or show artwork at 2:3 ratio. Lifts and gains a subtle accent-tinted shadow on hover. Finished mark (checkmark, held colour) in corner. Progress bar at bottom edge.

### Masthead

Wordmark, department/utility links (Newsreader), search field, profile selector. Double rule underneath (6px CSS, styled via `data-theme`). Department links: `ink-2` at rest, `ink` on hover, accent underline when active.

### Search field

Transparent background, 1px rule underneath (`rule` colour). Ink text, `ink-3` placeholder. Rule turns accent on focus.

## Do's and Don'ts

### Do:

- **Do** use the active accent for focus outlines, active states, and interactive hints (play button, selected tab)
- **Do** set backgrounds on `paper` (dark) or light theme's warm paper, and text in one of the three inks
- **Do** separate content with 1px `rule` (strong) or `rule-soft` (gentle)
- **Do** keep artwork at 2:3 and let it lift only on hover
- **Do** use Fraunces for titles and display, Newsreader for body and quotes, Geist for labels
- **Do** honour `prefers-reduced-motion` by collapsing all motion to 1ms
- **Do** test accent pairs at both light and dark themes (contrast-tested, but verify visually)
- **Do** apply `data-theme="light"` to `<html>` only from `lib/appearance-boot.js`, before first paint

### Don't:

- **Don't** use `#fff` or `#000` on paper; use the palette values
- **Don't** add a fourth accent or a second status colour; ochre and sage are out (replaced by warn and held)
- **Don't** draw cards, panels with backgrounds, or rounded boxes on paper (except pills and tabs)
- **Don't** add a horizontally scrolling poster rail
- **Don't** put shadows on anything but plates
- **Don't** use Fraunces for body text or labels
- **Don't** change `data-theme` mid-session (it must be set before first paint)
- **Don't** forget contrast: all seven accents are 4.5:1 on both themes, but if you layer or tint them, measure again

## Recent changes from the 2026-09-18 design

- **Theme system**: dark-first (screening room) + light variant (warm paper), user-selectable
- **Accent system**: one accent, seven colour choices, all contrast-tested
- **Artwork modes**: four settings (Default, Blurred, Artwork, Solid) for backdrop treatment
- **Departments**: Home, Movies (paged), Series, Tutorials, Collections now top-level sections
- **Tabs on title pages**: Overview, Cast, Similar, Details; selection persists on redraw
- **Cast from v9 credits**: circular portraits (185px), up to 12 cast by billing order
- **People search**: filterable directory of cast and crew, each with filmography
- **Settings merged**: Appearance (theme + accent), Profile (who is watching + switch), Library & Telegram (admin only)
- **Components renamed**: pills (filter buttons), tabs, destination cards
- **Font stack**: Geist now UI (labels, navigation) instead of Newsreader; Newsreader kept for body and quotes
- **Removed**: the light "paper" theme as default; warm paper now opt-in via Settings
