# Phase 2: Subtitles that draw

**Status:** Not started

**Deliverable:** Subtitles rendered over the video, chosen from the same menu
phase 1 built, with a size and a backing a viewer can change. Off is the
default and the first entry.

## Context

- Phase 1 — the menu button, and `AudioOptions`' shape, which this mirrors
- `android/ui-mobile/src/main/kotlin/PlayerScreenParts.kt:48-64` — `Video()`,
  which sizes `PlayerSurface` to the decoded aspect ratio. Cues have to land in
  that same box, not in the letterbox bars
- `web/public/lib/subtitle-panel.js:77-165` — **the reference** for what a
  viewer may change: size in four steps, backing, and a sync offset
- `web/public/lib/subtitle-style.js:23-32` — and the one thing deliberately not
  offered: position
- `android/gradle/libs.versions.toml:107-111` — the catalog has
  `media3-ui-compose` and **not** `media3-ui`, so `SubtitleView` is not on the
  classpath today

## Key insight

206 of 566 sets carry subtitles and the phone draws none of them. This is not a
missing control — there is no text on screen at all.

**The rendering route is the decision this phase settles**, and it is not
obvious. `media3-ui-compose` at `1.10.1` has no subtitle composable, so there
are two honest routes: add `androidx.media3:media3-ui` and host the View-based
`SubtitleView` in an `AndroidView`, which is proven and brings styling for
free; or listen to `Player.Listener.onCues(CueGroup)` and draw the text in
Compose, which keeps the dependency out and puts size and backing directly in
our hands, but owns cue positioning and cannot draw a bitmap cue at all.

Bitmap cues decide it if nothing else does: Matroska rips commonly carry PGS,
which is an image, not text. A Compose renderer that silently draws nothing for
those is a second vanishing act, and this project has just fixed one.

**Verify which cue types the library actually holds before choosing.** The
answer is in the index: 206 sets, and their subtitle codecs are knowable
without guessing.

## What gets built

**`TextOptions.kt`, pure, in `:core:playback`,** beside `AudioOptions` and
shaped the same. Off is always first and always the default, as
`transport.js:348-384` has it. Language naming is shared with phase 1 rather
than copied.

**Cues on screen.** Whichever route the insight above settles, drawn inside the
video's own box so a cue does not sit in a letterbox bar.

**Size and backing.** Four size steps and a backing choice — shadow, box, none
— matching `subtitle-panel.js`. **Not** a sync offset: on the web it corrects a
container the browser parsed loosely, and the same justification has not been
shown to hold here. If a real file on the phone is out of sync, that is
evidence and the control follows it.

## Open questions

- **Bitmap cues.** Answer from the library before writing a renderer.
- **Whether `media3-ui` is worth the dependency.** It is a View library in a
  Compose app, which the project has avoided so far.

## Success criteria

- A film with German subtitles draws them, and Off is what it starts on.
- Switching language mid-playback changes the text without stopping the picture.
- A PGS-subtitled file either draws its subtitles or offers nothing for them —
  never an entry that selects and shows nothing.
- `TextOptions` tested like `AudioOptions`, including "Off is first".
- `./scripts/check.sh` passes.

## Risks

- **Cue box versus surface box.** `rememberPresentationState` gives the video
  its aspect ratio; cues drawn against the whole screen will float in the bars.
- **`media3-ui` pulls in a View-based theme.** Check it does not drag Material
  Components in behind it.

## Todo

- [ ] establish which subtitle codecs the 206 sets actually carry
- [ ] choose the rendering route, and write the reason where phase 3 will read it
- [ ] `TextOptions.kt` with its tests, sharing phase 1's language naming
- [ ] cues drawn in the video's box
- [ ] size and backing
- [ ] a real device run on a subtitled film, and on a PGS one
- [ ] `./scripts/check.sh`
