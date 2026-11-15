# Shelf art renewal: posters and backdrops from each game's own rules

Status: done. Merged into `main` (a61429f5); Spieltisch 0.192.4 released by the funstation session; the boot picture installed on the funstation by hand (`limine-mkinitcpio`) and seen on a real boot (user, 2026-11-15).

## Why
User, 2026-11-15, after Spruchreif's art was redone: "what about the other poster ? renew them with skill
antialgorithm". The 2026-11 set was one picture repeated: the cast crowded into a warm living room under the same
cream banner title. Each new poster starts from something its game does, and the set spreads place, time of day,
light and the way the title sits in the scene, so the shelf does not settle into a new sameness.

## Scope (user's choice, 2026-11-15)
Playable first: the 14 playable games plus Lebensweg (WIP), poster and backdrop, gpt-image-2.6-sunburst medium,
about 3,600 credits (7,263 left this month; all 33 at that price would be ~7,920). The 18 "Coming soon" games keep
their art until next month.

## The ideas (game fact → picture; title in the scene)
| Game | Fact it starts from | Picture | Title |
|---|---|---|---|
| Buzzer-Quiz | the phones are the buzzers; questions from the family's own films | home cinema lit only by the screen, the fox's thumb first | the film's title card on the screen |
| Poster-Raten | a wrong tap locks you out | blocky poster on an advertising column at noon; the dark knight sulks on the kerb, padlock on his phone | painted on the column's band |
| Grenzfall | a country is nothing but its outline | country-shaped cookies in a bakery; Chile too long for the tray | piped in icing on a cookie |
| Zündstoff | nobody knows when the bomb goes | bomb passed round a Ferris wheel cabin at the top, no way out | light bulbs on the wheel's hub |
| Kopfnuss | tricks on the others' phones, then the summit | dawn rope team with mirror writing, a snail, glue; walnut on the peak | stamped on the summit banner |
| Dreifelder | pick one of three; wrong is out unless all are wrong | three ice floes in bright polar day, one tipping | letters sawn from ice |
| Preisfrage | odd articles from the net, guess the price | a doorstep buried in parcels, absurd things unpacked | printed on the packing tape |
| Kritzelei | the line shows live while it is drawn | a light-painting: the stroke drawn in light in a dark room | scrawled in light |
| Heimlauf | a hit piece goes back home | a village path as the track, a piece flung back to its house | road paint on the track |
| Grand Allee | guests choose a hotel's entrance and pay (realistic, no cast) | rainy night boulevard, lit entrances, a guest with a suitcase | gilt letters on a canopy |
| Flüchtig | the fugitive shows only in rounds 3, 8, 13, 18 | night ferry in fog, the raccoon visible in one lamp's light | the ferry's painted name |
| Mau-Mau | forgetting to say "Mau!" costs | a train compartment by day, everyone pointing at the one who forgot | the compartment's reservation sign |
| Schwimmen | knock on the table; lives as coins | beer garden under chestnuts, a fist knocking, coins counted | carved in the wooden table |
| Durak | everyone throws in on one defender | summer veranda, the defender behind a wall of crossed cards | on the card box |
| Lebensweg | the avatar's car climbs the Lebensberg (miniature look) | tilt-shift photo of the mountain road, the car full of family pegs | a yellow town sign |

Prompts as sent: `prompts.md`. Originals: `~/Workspace/mediagram/game_poster/<key>_{poster,backdrop}.jpg`, the
previous ones kept as `<key>_{poster,backdrop}-v1.jpg`.

## The Fun-Station boot picture
User, 2026-11-15, mid-run: "create also a new boot screen for the machine". The picture
(`scripts/funstation/fun-station.png`) is Plymouth's boot screen, Plasma's login splash and Android's boot
animation. The old one was a glowing gamepad over a planet with a Movies / TV Shows / Video Games row. The new
one shows what the screen is up for, the box getting ready: the cast backstage preparing a film night (film cans,
lighting desk) and a game night (the giant die), the name painted on the brick wall, "by KipOlsenXyphon" kept from
the old picture. Upscaled (p-image-upscale 16 MP), cut to 16:9, stored at 2880×1620 (7 MB; 4K would be 12 MB).
The TV tile's logo is now its own cut-out (`fun-station-logo.png`); the script's crop-and-key of the old
picture's dark sky could not work on a lit wall. The script now rebuilds the initramfs when the picture changes.

## Steps
- [x] Posters (15) and backdrops (15), each checked: title spelled right, layout, backdrop subject top right.
      Redone: Poster-Raten's backdrop (the whole cast crowded the middle). Edited: Kritzelei's poster (a bat
      emblem on the dark knight's chest, another brand's mark; seedream-5.5 edit).
- [x] Shelf WebPs; backdrops cropped to 16:9 per image (offset 80–160 px of 1024) so the characters sit above
      the poster row; sheets in `visuals/`
- [x] Boot picture, logo, script
- [x] Versions (Spieltisch 0.192.4, media 0.180.7), changelog
- [x] Spieltisch 0.192.4 installed on the funstation; shelf `visuals/funstation-shelf-new-art.jpg`
- [x] Merged; boot picture on the funstation, seen on boot

Credits: 30 × 120 + 120 (redo) + 56 (edit) + 120 (boot) + 30 (upscale) + 70 (logo) = 3,996.
