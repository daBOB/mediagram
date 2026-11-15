# App icons for Mediagram and Spieltisch

Status: done. Merged into `main` (793e2639); Spieltisch installed on the funstation.
Versions: media 0.180.4 (0.180.3 left to the session that holds it uncommitted on `main`), Spieltisch 0.192.2.

## Why
User, 2026-11-15: "lets create icons for mediagram and spieltisch app". Both icons were placeholders: a white ring
on near-black and the die showing five on felt green, and the TV tiles carried no names, so on the funstation's
launcher the two apps told apart only by colour.

## Decisions (user, 2026-11-15, from `visuals/icon-options-board.jpg`)
- **Mediagram: the film leader** (recommended), the countdown the web player shows while a film loads, on the
  player's dark paper `#0d0d0e`, coral `#e57a61`. Declined: a film strip cut into the parts a film is stored in;
  the letter m.
- **Spieltisch: the cast** (recommended), cat, panda and piglet at the felt table with their phones, in the shelf
  posters' 3D look. Declined: phones round the table; the die.
- The phone controllers (Android, iOS) carry Spieltisch's icon, as they carried the die.

## Done
- [x] Options drawn and rendered on a launcher mock (`visuals/draw_icon_options.py`, board)
- [x] Mediagram `ic_launcher.xml`, `tv_banner.xml`: vectors, wordmark as outlines (`visuals/make_mediagram_vectors.py`)
- [x] Spieltisch icon and tile: BudgetPixel gpt-image-2.6 medium, square (icon) and a widened edit of it (tile,
      name on the table felt in Bricolage Grotesque); originals in `visuals/`
- [x] Controller: adaptive foreground fills the visible 72 dp, background the picture's felt `#01301E`, monochrome
      layer dropped; Android 7 legacy and iOS `AppIcon.png` full-bleed
- [x] Versions, changelog
- [x] Spieltisch 0.192.2 installed on the funstation; launcher `visuals/funstation-launcher-spieltisch-installed.jpg`, with the Mediagram tile pasted in `visuals/funstation-launcher-with-new-tiles.jpg`
- [x] Merged into `main` (793e2639)
- [x] Media app 0.180.4 benchmark installed on the funstation (x86_64, data kept); Bigscreen's mediagram tile
      now wears WayDroid's icon of the app instead of `video-television` (0.180.5)

## Not done
- The media app's new icon is not on the channel yet: the box, phones and tablets get it with the next release.
- The web player's favicon and the phone controller's in-app screens keep their look.
