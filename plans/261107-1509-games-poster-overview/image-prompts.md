# Game posters and backdrops — GPT Image prompts

User, 2026-11-07 15:09: posters + backdrops for the Games overview, made by the user with GPT Image 2.5.
Reference to attach with every prompt: `~/Workspace/mediagram/assets/game-night/avatars/viewer-pages/cast.png`
(all 22 avatars), or the single `…/avatars/<id>/<id>-concept-v1.png` files of the characters named. `assets/` is
untracked: it exists only in the main checkout, not in worktrees.

Formats: poster 2:3 portrait (1024×1536 or larger), backdrop 16:9 landscape (1920×1080 or larger; if only 3:2
is offered, keep everything important away from the top and bottom edges so it crops to 16:9).
Files: `<game>-poster.png`, `<game>-backdrop.png` with game = the host's game kind: quiz, poster, race, mau_mau,
schwimmen, durak, grand_allee, kopfnuss, dreifelder, fluechtig.

**State 2026-11-12:** delivered and on the shelf: quiz, poster, mau_mau, schwimmen, durak. Owed, the shelf shows a
name tile until they arrive (`android/spieltisch/src/main/kotlin/ShelfGames.kt`): race (Heimlauf), grand_allee,
kopfnuss, dreifelder, fluechtig.

Backdrop layout, from the shelf: the game's name sits top left and the poster row covers the bottom ~40 %, and the
backdrop fades out to the left and bottom. So the scene's subject goes top right; left 40 % calm and darker, bottom
quiet, no text.

Style sentence of the cartoon games (all but the hotel game, which is realistic): stylized 3D animated-film look,
chunky toy-like characters with big heads and short bodies, soft matte materials (no glossy or plastic shine — the
user finds glossy cheap), warm cozy game-night light, rich but soft colours, gentle depth of field, family-friendly.

Every prompt states its title text exactly and names no other game's or brand's title: the 2026-11-07 hotel
prompt named the box it took its mood from and set no title, and the image came back titled with that brand.

## Delivered 2026-11-07 — what was asked (summaries of the chat prompts, for reference)

## Buzzer-Quiz
Poster: four characters around a giant glowing red buzzer in a cozy living room, big TV with a question mark — fox
(popcorn-bucket hat, magenta hoodie), granny (lilac cardigan, round glasses), white-and-teal robot, frog (mint hoodie,
red-blue 3D glasses); floating question marks, a film reel, a small globe. Title "Buzzer-Quiz".
Backdrop: same living room wide, characters on the sofa on the right, left 40 % calm and darker, bottom quiet.

## Poster-Raten
Poster: tabby cat (purple hoodie, pink star sunglasses) with a magnifying glass at an invented film poster pixelated
into chunky blocks, sharp only under the glass; raccoon (teal tracksuit, headphones), hedgehog (black cap, orange
hoodie). Title "Poster-Raten".

## Mau-Mau
Poster: card table of lacquered cherry wood; witch slaps down her second-to-last card shouting, vampire gasps,
princess holds a Jack, ghost peeks over; French-suited cards flying. Title "Mau-Mau".

## Schwimmen
Poster: card table standing in a sunny lake; bodybuilder (lime sling swimsuit) on a swim ring proudly holding three
cards, fashion doll and a slightly soggy mummy playing with their feet in the water, a rubber duck. Title "Schwimmen".

## Durak
Poster: card duel in a snowy wooden cabin, samurai vs yeti laying cards in attack/defence pairs, a jester's cap waits
for the loser, armour hero referees. Title "Durak".


## Missing — paste these (10 images, 2026-11-12)

Attach `cast.png` (path at the top) with every cartoon prompt. Each block is the whole prompt.

### Heimlauf — `race-poster.png`, `race-backdrop.png`

```text
Game poster, 2:3 portrait. A cozy game night: an original wooden race board on a lacquered cherry-wood table, a 40-field track with four corners and four coloured home lanes, set in a dark wooden frame. A wooden die tumbles through the air. Four characters from the attached cast race along the track as the game pieces: the bunny, the bear in a yellow hoodie and red fez, the panda with a teal neckerchief and the piglet in a yellow raincoat. Stylized 3D animated-film look, chunky toy-like characters with big heads and short bodies, soft matte materials, no glossy or plastic shine, warm cozy game-night light, rich but soft colours, gentle depth of field, family-friendly. The only text in the image is the title "Heimlauf", large, at the top.
```

```text
Wide 16:9 backdrop, no text at all. The same lacquered cherry-wood table seen wide, the wooden race board with its four coloured home lanes on the right third, a die mid-tumble, the bunny, bear, panda and piglet small on the track. The left 40 % is calm, darker wood and soft shadow; the bottom fifth is quiet. Stylized 3D animated-film look, soft matte materials, warm cozy game-night light.
```

### Grand Allee — `grand_allee-poster.png`, `grand_allee-backdrop.png`

The 2026-11-07 art (`game_poster/Hotel_poster.png`) is right except its title, so edit it rather than start over.
Attach `game_poster/Hotel_poster.png` and use:

```text
Edit this image. Replace only the title text at the top with the words "Grand Allee", in the same engraved gold serif lettering, same size, colour and position, with the same small ornament beneath it. Keep every other part of the image exactly as it is.
```

Backdrop: `game_poster/Hotel_backdrop.png` has no text and can be used as it is. A new poster, only if the edit fails
(no cartoon cast: the user's direction for the hotel game, 2026-11-07):

```text
Game poster, 2:3 portrait. Realistic, painterly architectural illustration of an elegant seaside boulevard at golden hour, warm low sun, soft matte surfaces. Along a winding cobbled boulevard stand grand buildings of our own design: a Paris Belle Époque palace with mansard roofs and red awnings, a New York art deco tower, a Japanese cherry-blossom palace, a Moroccan riad with a courtyard, a South Seas lagoon resort with palms, an Alpine chalet. A small river with stone bridges, the sea in the distance, a fountain and street lamps. In the foreground two ivory dice and a stack of gold coins. No people, no cartoon characters. Serious, elegant, gorgeous. The only text in the image is the title "Grand Allee" in engraved gold serif letters at the top.
```

### Kopfnuss — `kopfnuss-poster.png`, `kopfnuss-backdrop.png`

```text
Game poster, 2:3 portrait. Four characters from the attached cast race up a steep rocky mountain toward a summit flag, each holding a phone, with a huge cracked walnut on the peak. On their phones, playful tricks: the dark knight squints at a phone whose letters are mirror-written, a little snail crawls across the android's screen, the bear in a yellow hoodie and red fez has his finger glued to his phone with a blob of glue, and the witch, grinning, taps her phone to send the next trick. Big category cards fly past in blue with a circle, orange with a triangle and yellow with a square. Stylized 3D animated-film look, chunky toy-like characters with big heads and short bodies, soft matte materials, no glossy or plastic shine, warm light, rich but soft colours, gentle depth of field, family-friendly. The only text in the image is the title "Kopfnuss", large, at the top.
```

```text
Wide 16:9 backdrop, no text at all. The steep rocky mountain on the right third with the cracked walnut and a flag on its summit, the four climbers small on its slope. The left 40 % is a calm, darker evening sky; the bottom fifth is quiet rock in shadow. Stylized 3D animated-film look, soft matte materials, warm light.
```

### Dreifelder — `dreifelder-poster.png`, `dreifelder-backdrop.png`

```text
Game poster, 2:3 portrait. A TV game-show floor seen from slightly above, three big glowing floor fields side by side, numbered 1, 2 and 3, marked with a circle, a triangle and a square. Characters from the attached cast: the panda, the frog with red-blue 3D glasses and the granny in a lilac cardigan stand together on the lit orange triangle field, cheering. Under another field a comic trapdoor opens and the vampire drops through it, arms flailing, funny and harmless. The princess hesitates at the edge of a field, deciding. Spotlights, confetti. Stylized 3D animated-film look, chunky toy-like characters with big heads and short bodies, soft matte materials, no glossy or plastic shine, rich but soft colours, gentle depth of field, family-friendly. The only text in the image is the title "Dreifelder", large, at the top.
```

```text
Wide 16:9 backdrop, no text at all. The game-show stage wide, the three glowing fields with circle, triangle and square on the right third, soft spotlights. The left 40 % is a calm, dark auditorium; the bottom fifth is quiet floor. Stylized 3D animated-film look, soft matte materials.
```

### Flüchtig — `fluechtig-poster.png`, `fluechtig-backdrop.png`

The TV shows a daylight city map (the user picked the flat city-map look); the city is our own, never London.

```text
Game poster, 2:3 portrait. An invented European city of our own seen from above like a living, slightly raised city map in daylight: blocks with courtyards, green parks with trees, a river with bridges and a little ferry crossing it, white taxi streets, green bus routes and a purple underground line running between round numbered stops. Standing on the map as big figures from the attached cast: the raccoon in a long coat and hat slipping away down a street, half-faded, his footprints fading behind him; the cat with a magnifying glass, the samurai and the armour hero closing in from three sides. Stylized 3D animated-film look, chunky toy-like characters with big heads and short bodies, soft matte materials, no glossy or plastic shine, bright soft daylight, rich but soft colours, family-friendly. The only text in the image is the title "Flüchtig", large, at the top.
```

```text
Wide 16:9 backdrop, no text at all. The daylight city map seen wide from above, the cat, samurai and armour hero searching on the right third, a faint trail of fading footprints. The left 40 % is calm rooftops and the river; the bottom fifth is quiet park. Stylized 3D animated-film look, soft matte materials, bright soft daylight.
```
