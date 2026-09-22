---
version: 1
slug: "android-ui-mobile-src-main-kotlin-catalogscreen-kt"
primary_target: "android/ui-mobile/src/main/kotlin/CatalogScreen.kt"
related_targets: ["android/ui-mobile/src/main/kotlin/PosterCard.kt","android/ui-mobile/src/main/kotlin/ShelfTabs.kt","android/core/designsystem/src/main/kotlin/Theme.kt","android/core/designsystem/src/main/kotlin/Type.kt","android/core/designsystem/src/main/kotlin/Palette.kt"]
---

## Direction contract

THESIS: The catalogue is a wall of plates, not a storefront of rails. It refuses
the horizontally-scrolling poster row every media app ships: a shelf you swipe
hides how much is on it and ranks what it shows first. Everything a shelf holds
is on the page, in one direction of travel.

OWN-WORLD: The printed catalogue in ink rather than on paper. Ground #16130f,
the page one step up at #1e1b16, text #e8e2d4, figures #a89b84, hairline rules
#3a342a. One imprint red for the thing a viewer is in the middle of; ochre and
sage reserved for warning and held. No cards, no elevation, no filled
containers: a plate is artwork with a hairline and a short shadow, tipped onto
the page. Against ink the shadow is invisible and the hairline does that work
alone. Shelf names sit in the masthead, over the rule the tab row draws.
Figures set in tabular numerals. Faces: Fraunces for names and headings,
Newsreader for sentences and figures, both variable and both carrying the
optical-size axis the web player set them for.

STORY: A viewer opens the app knowing the library is theirs and finite. They see
how much of each kind there is, recognise a title by its artwork, and reach it
in one tap. Nothing recommends, ranks, or promotes.

FIRST VIEWPORT: Top app bar, wordmark left, overflow right. Beneath it a
masthead of three shelf names, the one in view marked in imprint red. Then that
shelf alone, as plates on a fixed grid, 2:3, three across on a phone and six on
a tablet, sized so a shelf of one course keeps a plate the size of a plate
instead of stretching across the width. Each plate carries a hairline; the name
sits beneath in the display face, the extent line under that in figures. The
primary action is the plate itself.

AMENDED IN BUILD: one shelf at a time, not three stacked. Written as stacked
shelves, built and then measured: the film shelf alone is three hundred plates
deep, so the courses sat some fifty screens below the fold with nothing on
screen to say they existed. A wall puts everything on the page, which is the
thesis, and that is exactly why it cannot also hold every shelf at once. The
reference surface already answers this and the contract had not read it: the
web player gives each shelf its own route and reaches them from a masthead.
Tabs are that masthead in the platform's vocabulary.

FORM: The wall, plated. Candidate 2 of six on the resonance-ordered list, dealt
with 4 and 6; seed key dded0791, surface scope, operate mode.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
