# Board games at the Spieltisch

User, 2026-11-06: "we define more board games" — after the card-game catalogue
(`plans/261106-1808-card-games/plan.md`). This defines the board-game backlog.

Heimlauf (0.144.0) is the first board game and is shipped. The game-night spec
(`docs/superpowers/specs/2026-11-03-game-night-design.md:32-34`) already named
five more, without defining them. Below they are defined, costed and ordered.

**One spec line is now wrong and has not been corrected:** `design.md:35` still
lists "Funny 3D avatar images for players (user: 'add later')" as a non-goal, but
avatars shipped in `fd7009ce` (0.151.0). The spec is a dated design record and was
not touched by that commit, so it now contradicts the tree. Flagged rather than
silently rewritten — decide whether to amend the spec or leave it as the record of
what was true on 2026-11-03.

## The one architectural decision this list forces

**Heimlauf's board is a ring.** `race_rules.rs` hardcodes `TRACK` = 40 fields in
a fixed loop, four corners, four goal fields. That shape does not survive
contact with four of the five games below — a trivia board has category fields
and shortcuts, a life path **branches**, a property game has a ring with
buildable plots, a hotel game has a 2D plan.

So the shared board engine must model a board as a **graph of tiles**, never a
ring: `tiles: Vec<Tile { id, kind, at, exits }>` with tokens holding a tile id.
Deciding this now costs nothing; deciding it after a second board game ships
means rewriting it. This is the "is there a more elegant way?" question, and the
answer is to build the board as data once.

**What Heimlauf already provides and every board game reuses:**

- dice rolled on the table beside the board (`race-motion.ts`, 1.15 s flight,
  clatter at 0.69 s) — `race-sound.ts` schedules cues on the audio clock from
  host times;
- a wooden table surface and dark wood frame (`client/public/race/table-lacquered-cherry.jpg`,
  `frame-dark-wood.jpg`);
- pre-drawn canvas textures for all HUD text, no HTML over the canvas
  (`race-print.ts`);
- `renderer.compile` at load, which is what removed Heimlauf's first-tap stutter;
- the 960×540 TV page that holds 60.0 fps with 0 frames over 20 ms on the box;
- absent players' turns playing themselves, counting from
  `max(prompt, left_at)` (`race.rs:139`).

New per game: a board description, tile art, a rules module, and the phone's
action screen for that game.

## Box constraints that bound every board game

From `plans/reports/benchmark-261105-1611-tv-box-3d-framework-spike-report.md`:

- three.js in the WebView is the decided route; no native engine.
- The box is **GPU-bound on pixels**, not triangles. At 1080p nothing holds
  60 fps; the 960×540 page is what makes Heimlauf smooth. Every board game
  renders at that page scale.
- Figures are **2D sprites**; 3D avatar models only on reaction, winning and
  losing screens (user decision, 2026-11-05).
- Matte Lambert shading, not shiny (user: "i prefer the matte over shiny").
- Shadows cost 8–17 fps → baked or blob shadows only.
- Memory: the game host plus renderer already sits near 434 MB RSS. Boards are
  data plus a few textures; **no per-game 3D asset packs**.
- Assets ship inside the APK and the cost doubles across two ABIs (Heimlauf's
  sound added ~2×2.2 MB). Board art stays small and drawn, not photographed
  beyond the table already on disk.
- The room seats **8** (`host.rs:42`, eight colours in `room_seats.rs:8`).
- No names, boards or artwork of existing games (spec line 47: no Monopoly,
  Mario Party, Trivial Pursuit, Buzz). Traditional public-domain games keep
  their rules and usual names — that is why Heimlauf could be Ludo.

## The games

Items 1–5 come from the spec's own list of later games (line 32-34), each under an
own name. Items 6–14 are this session's additions. Item 14 is a duplicate of item 3.

### 1. Wissensreise — category trivia board · 2–6 players · origin: Trivial Pursuit (Hasbro, 1979)

Roll, move along a board of six category fields (Film, Natur, Geschichte,
Sport, Musik, Alltag), answer one question from the category you land on, keep
a token for each category you win; first with all six wins.

**Why this is first.** The questions already exist: 311 sourced German general
questions (`general.rs`) and the library-driven film questions (`film_questions.rs`),
both already filtered by *Nur für Kinder*. The buzzer quiz's answer screen,
its 12 s timer, its 1000→500 speed scoring and its 400 ms claim-lag rule are
built and tested. What is new is a board, a token walk and a per-player turn
instead of a simultaneous buzz — i.e. the board engine, no new content pipeline.

Needs: no AI. Low asset cost. No new phone screen (the quiz's `Choice` works).

### 2. Lebensweg — life path · 2–6 players · origin: The Game of Life (Milton Bradley, 1860/1960)

Spin, move along a branching path through life stages (Schule, Beruf, Familie,
Haus), collect milestones; most Lebenspunkte at the end wins.

This is the game that **requires** the graph board — the path forks and rejoins.
Needs: no AI. Low-medium asset cost. Phone reuses `Choice` for the forks.

### 3. Bauherr — hotel building · 2–4 players · origin: Hotel (MB Spiele, 1974)

Buy plots on a town plan, build floors, guests arrive and pay per occupied
floor; most money when the town is full wins.

Buildings are extruded boxes (cheap, matte) or 2D sprites per the avatar
decision — decide during the look test, not now. Needs: AI for 2–3 players or a
4-player minimum, like Heimlauf's lobby gate. Medium asset cost. Phone needs a
**new screen**: a plot sheet with build options.

### 4. Grundstück — property and money · 2–6 players · origin: Monopoly (Parker Brothers, 1935)

A made-up town's street plan, own name, own board. Buy plots, build, collect
rent when others land on them.

The longest rule set on the list and the longest to play — a full evening, on a
box whose job is otherwise films. Needs: AI or a high player minimum, a money
and property sheet on the phone (new screen), and the most original board art.
**Recommendation: build it last of the four, or drop it.** It is the one most
likely to read as a copy of an existing game, and the least likely to be played
to the end at a family evening.

### 5. Spielwiese — party board with minigames · 2–8 players · origin: Mario Party (Hudson Soft/Nintendo, 1998)

Roll, land on a field, play a 30–60 s minigame on the phones; win points; most
points after a lap wins.

Biggest by far: every minigame is its own phone interaction and its own timing
model. Some want motion, camera or microphone, and **that is the HTTPS trigger**
the spec names (line 36-40: revisit only if a minigame needs motion; a domain
plus DNS-01 renewals is the route if it ever does). Already decided last.
Tap-only minigames first, motion only if HTTPS is accepted.

### 6. Flüchtig — hidden-move pursuit · 2–6 players · origin: Scotland Yard / Mister X (Ravensburger, 1983)

One player is on the run and nobody can see where; the others chase. The fugitive
moves by taxi, bus or underground and pays with a ticket of that kind; the hunters
see only which ticket was spent, and where the fugitive surfaced on the few forced
reveals. Whoever catches them, or survives every turn, wins.

**Best fit of the four for what is already built.** The host already keeps
information per player: `Game::phone_screen` takes a `PlayerId`, so the fugitive's
phone shows the real position and every hunter's phone shows only spent tickets
and the known trail. `/tv` and images stay loopback-only, so nobody on the Wi-Fi
can read the fugitive's board. The one mechanic that makes this game work needs
nothing new.

Needs: a map graph of nodes with **typed edges** (taxi/bus/underground), no AI,
low asset cost, no new phone screen beyond a ticket picker (`Choice` covers it).
The edge type is the one thing the plain tile graph must grow.

Own map, own city or an abstract one — not London's, not Ravensburger's station
names or ticket artwork.

### 7. Wortwerk — word grid · 2–4 players · origin: Scrabble (Alfred Butts, 1938; Mattel/Hasbro)

Place letter tiles on a 15×15 grid, crossing existing words, each letter worth
its value, premium squares doubling or tripling; highest score when the tiles run
out.

**Not a board game in the sense of the other eight.** Nothing walks and nothing
is rolled: it is letter placement on a grid. It shares the tile-graph renderer,
the sprite and texture machinery and the table look, but none of the turn-and-dice
loop — so it is the game that proves the engine is really "a board as data" and
not "a dice board".

**The cost is a German word list, and it is the largest asset risk in either
plan.** The official Scrabble word list is licensed and cannot ship. A free list
(Wiktionary dumps, open German dictionaries) can, but its judgement of what
counts as a word will not match what players expect, and every mismatch reads as
a bug at the table. Validation must run offline in the core, never as a network
call.

Also needs a new phone screen (a letter rack plus a grid), and a 15×15 board on a
phone is a real layout problem.

### 8. Neuland — settlement and trade · 3–4 players · origin: The Settlers of Catan (Klaus Teuber, Kosmos, 1995)

A hex map built fresh each game; two dice decide which hexes yield; players
collect five resources, trade them, and build roads, settlements and cities.
First to the build target wins, with bonuses for the longest road and the largest
knighthood.

The heaviest rules on either list, and the one mechanic that does not exist yet
is **negotiation between two players**: an offer, an accept, a counter. That is a
phone-to-phone protocol with private terms, a new phone screen, and a timeout so
one player cannot hold the table. The hex map itself is a tile graph and is cheap;
the trading is the project.

Needs: AI or a 3-player minimum. Medium-high asset cost (hexes, ports, resource
icons, cards). Long game.

### 9. Feldzug — territory conquest · 2–6 players · origin: Risk / La Conquête du Monde (Albert Lamorisse, 1957; Parker/Hasbro)

A map of connected territories, armies placed, then attack along an edge with
dice, take the territory, fortify; own the map or hold the target longest.

Dice combat is trivial and Heimlauf's die already exists. The cost is the map data
and the length: a full game is hours, on a box whose job is otherwise films, and a
game that long must survive the app going to the background, a phone sleeping and
a TV reload — states Heimlauf already handles for fifteen minutes, not three hours.

Needs: AI more tractable here than in Neuland (attack-the-weakest heuristics play
respectably), or a 3-player minimum. Low-medium asset cost. Own map — an invented
continent, not Earth, not Risk's territory names or colour blocks.

### 10. Gassenwechsel — shifting maze · 2–4 players · origin: Das verrückte Labyrinth (Max J. Kobbert, Ravensburger, 1986)

A grid of walls and passages, one spare tile. Slide that tile into a row or column
and the whole line shifts; the tile pushed out the far end becomes the new spare.
Then move your figure along the passages the shift just made, collecting your own
secret treasures. First to collect all of theirs wins.

**The best addition on this list, and the cheapest.** 2–4 players, exactly
Heimlauf's lobby gate; 25–40 minutes, shaped like a family evening rather than an
evening of its own; age 8+, so *Nur für Kinder* needs no special case; and no AI at
any count. Each player's treasure list is secret, which the per-player
`phone_screen` already handles — the same mechanic Flüchtig needs.

**It is also the one that breaks the engine's assumption.** Every other board here
is fixed once the game starts. A shifting maze rebuilds the tile graph every turn,
and what animates is the *tiles*, not the tokens. That is a new render path
(sliding tile planes) and it is why the engine must hold the board as data it can
replace, never as geometry it built once. Finding this out in phase 1 costs
nothing; finding it out after six static boards ship costs a rewrite.

Spiel des Jahres 1986 Auswahlliste. Not Ravensburger's tile art, its treasure names
or its dragon-ghost-goblin set — own tiles, own treasures.

### 11. Pleitier — the race to go broke · 2–4 players · origin: Mankomania / Go For Broke! (Dan Glimne, MB Spiele, 1985)

Everyone starts with a million. Roll, move, and lose it: waste money at the hotel,
the stock exchange, the horse race and the casino. The first player who is broke
wins.

**This is Spielwiese at a tenth of the cost.** It is already a dice board with a
small set of money minigames on it, so it is the roll-and-move family plus four
tile effects rather than a minigame framework of its own. No motion, no camera, no
HTTPS. ~60 minutes, age 8+, 2–4 players.

The inverted goal is the whole fun of it and costs nothing to implement — the winner
is whoever crosses zero first, so scoring is Heimlauf's shape with the comparison
flipped.

### 12. Radau — roll and perform · 3–6 players (teams to 12) · origin: Das Nilpferd in der Achterbahn (Bertram Kaes, Otto Maier/Ravensburger, 1988)

Roll, move along a coaster track, and at each station perform a task: describe a
term, act it out, make its sound, or draw it — against a 45-second timer. Guess
everyone right and your piece advances; fail and the opposing piece, on a shorter
track, moves instead. The 2018 edition plays 3–6 cooperatively against the board,
or 6–12 as two teams.

**Cooperative against the board means no AI**, and the tasks are performed by
people in the room, so the technology is only a term on screen and a timer — both of
which the quiz already does. Drawing is the one task that needs new input: a phone
canvas whose strokes reach the TV. That works over plain HTTP (touch, not camera or
motion), so it does **not** trigger the HTTPS question Spielwiese does.

**One task type cannot be ported and must be dropped: moulding with plasticine.**
The 1988 game ships a stick of Knete; a TV box has none. Record it as a deliberate
omission, not a bug.

Age 12+, ~50–60 minutes. Own coaster, own term deck, no Ravensburger hippo and no
"Drecksau".

### 13. Sprechstunde — personal questions · 3–6 players · origin: Therapy (Jay Titel, Pressman 1986; MB Spiele 1988/89)

Six life stages, and in each you win a marker by answering honestly — about
yourself, and about the other players. Perception questions, knowledge questions,
fantasy prompts and an inkblot booklet.

**Same conflict as Kings, and a harder one.** The origin game is rated 16+/18+; its
content is personal psychology questions about the people in the room, asked aloud
on a television. It cannot sit in a lobby with a *Nur für Kinder* toggle without a
gate of its own. Needs a decision before it is built: hidden behind the grown-up
profile check Kotlin already does, or left off the list.

60–90 minutes. 132 therapy cards, 360 knowledge cards and 36 fantasy cards in the
original, so it is also a content-writing project of the size of the quiz's 311
general questions.

### 14. Hotel — already on this list as Bauherr

**Hotel (MB Spiele, 1974) is the origin of item 3, Bauherr** — the same game, so it
is one slot, not two. What is left to decide is the name, not the game: Bauherr was
chosen as an own name because spec line 47 forbids shipping an existing game's name,
board or artwork. If a closer reimplementation of Hotel is wanted rather than a
hotel-building game in its spirit, that is a rules-fidelity decision to make inside
Bauherr's phase, not a new entry here.

### 15. Nachtwache — a video host against the clock · 2–6 players · origin: Atmosfear / Nightmare (Brett Clements and Phillip Tanner, J. W. Spear & Sons 1991; German edition Schmidt, 2–6, ab 12, 60 min)

A recorded host on the television runs the game. He taunts you, gives instructions,
and times you: collect the six keys of your character, then reach the centre square
before the hour runs out. If the clock reaches sixty minutes, **the host wins**.
Players write a fear on a card at setup and the host uses it against them. The
Gatekeeper is played by an actor (Wenanty Nosul) and appears on screen throughout.

**This is the one game on the list that this project was already built for.**
Atmosfear's host is a video, and mediagram is a video player on a TV box: the host
clips are ordinary library assets, played by machinery that already exists and is
already measured on the real box. The sixty-minute clock is video time, which the
player already tracks. No new video technology, no HTTPS, no motion input. The 2022
30th-anniversary edition reached the same conclusion from the other side, replacing
the tape with streamed video.

**One real consequence for the renderer.** The box rules forbid HTML over the canvas
during play, so the host must not be a `<video>` element laid on top of the
three.js scene. The route that respects that is a `VideoTexture` — the host is a
textured plane inside the scene, drawn with everything else. That is new to the
engine and should be proved with a spike before the game is scheduled.

Costs, honestly: **the host is a production, not a program.** A character, a German
voice, and enough clips to run an hour — instructions, taunts, key events, the
hour-up sting. That is filming or rendering work beside the code, and it is why this
sits late in the order despite needing almost no new systems. 2–6 players, 60
minutes, age 12+, dice rolling.

**Two gates before it can ship.** It is horror, and it asks players to name a fear
that is then read aloud on a television — the same problem as Sprechstunde and
Kings, so it needs the grown-up-profile gate, and it must never appear in a lobby
with *Nur für Kinder* on.

Own name and own host: not Atmosfear, not Nightmare, not "the Gatekeeper", not the
Spear or Schmidt board, and not the six realm names.

### 16. Neunmalklug — a snarky hosted quiz · 2–8 players · origin: You Don't Know Jack (Harry Gottlieb's Jellyvision with Berkeley Systems, Sierra On-Line, October 1995; Jackbox Games today)

**Not a board game.** It sits in this plan because it shares the host-video
subsystem with Nachtwache and the question machinery with Buzzer-Quiz, so it is
cheapest to decide beside them.

A quiz show with a host who has opinions. Multiple-choice questions where the
wrong answers are jokes, scored in fake money, and one mechanic that makes it a
party game instead of a test: **you can screw another player** — push your question
onto them, and if they miss it their money becomes yours. Rounds are broken up by
host asides; the original's signature round has the host read a clue while everyone
races to match the right phrase to it.

**Why this one is nearly free.** It is Nachtwache's host and Buzzer-Quiz's game,
both of which already exist or are already designed:

- the question packs, the answer screen, the scoring and the *Nur für Kinder*
  filter are **built and tested** — `general.rs` alone has 311 sourced German
  questions, and `quiz_mix.rs` already mixes packs;
- the host is the same `VideoTexture` plane Nachtwache needs, so **the host-video
  subsystem is built once and serves two games**, and the one that ships second is
  much cheaper than the one that ships first;
- phones already answer questions on `PhoneScreen::Choice`, which is the whole
  input surface this needs.

So the new work is: a German question set **written for jokes** (not the sourced
factual style the existing packs use — a different voice entirely), the screw
mechanic (target another player, they answer, money moves), and the host clips.

**It does not compete with Buzzer-Quiz, and should not be allowed to.** Buzzer-Quiz
is speed — buzz in, answer, next. This is performance — the host, the jokes, the
money, the screwing. Two trivia games in one picker is only defensible if that
difference is legible in their two German one-liners.

**No age gate needed, unlike the other grown-up games.** Because the questions are
ours to write, a kid-safe set and a grown-up set are both possible, and the
existing `kids_only` filter already picks between them. Nachtwache, Sprechstunde
and Kings cannot do that — their content is adult by nature.

No AI, no props, 2–8 players (the room caps at 8). **Deliberately omitted: the
audience.** Modern Jackbox lets hundreds of non-players join and vote; this project
has one phone per seated player and no account system, so there is no audience and
none should be invented.

Worth recording: **there is no official German Jackbox today.** The series was
distributed in German-speaking countries by Take 2 Interactive back then, but the
modern Party Packs are English-only — the German that exists is an unofficial fan
patch, PC-only, not on consoles or TV boxes. So a German hosted comedy quiz on a
TV box is filling a hole, not following a fashion.

Own name, own host, own questions: not "You Don't Know Jack", not Jackbox, not
Cookie Masterson, and none of the original's joke text.

### 17. Denkzettel — fastest finger, with traps · 2–6 players · origin: Knowledge is Power (Wish Studios for Sony Interactive Entertainment, PS4 PlayLink, 24 Oct 2017)

A trivia game where answering well moves you up a track, and where you can make the
other players answer *worse*. Traps and power-ups are the whole difference from a
plain quiz: freeze someone, slow them, muddy their screen, or shield yourself.
Eight puppet characters to play as, a stop-motion game-show set, and a Decades
sequel in 2018 that sorted the same questions by the 80s, 90s, 00s and 10s.

**PlayLink is exactly this project's architecture, and it is now dead.** Phones were
the controllers, joined over the same Wi-Fi or the console's own hotspot — no
controller, no internet. As of 14 December 2023 Sony **delisted the companion
apps**: Knowledge is Power, Decades, That's You, Hidden Agenda, Frantics and
Chimparty can no longer be installed on a new Android phone at all.

That is the strongest argument on this list for the shape mediagram already has:
the phone UI is a web page the box serves, not an app anyone must publish, update
or keep listed. A PlayLink game dies when its store listing does; a box-hosted web
client does not. Whoever builds here should know that the platform with the most
money behind this exact idea lost it to a delisting.

**Why it is less a new game than a layer on one that exists.** The question
machinery is Buzzer-Quiz's, built and tested: packs, `quiz_mix.rs`, the answer
screen, scoring, the *Nur für Kinder* filter. What is new is only:

- **traps** — a small set of per-player status effects with a timer, plus a way to
  target another player. The first time anything here writes to someone else's
  phone rather than its own;
- **advance by answering** instead of by rolling, which the graph board handles as
  a path whose tokens move on a correct answer;
- **the climb track** — the cheapest board on the list, one line of tiles.

So this is **the first game that composes the board engine with the quiz engine**,
and that composition is worth more than the game: if the board really is "a graph of
tiles with pieces on it" and the quiz really is "a pack plus a Choice screen", then
a board driven by quiz answers costs almost nothing. Proving that early is the
reason to build it early.

**But it is the third trivia game, and the picker cannot hold three.** Buzzer-Quiz
is speed, Neunmalklug is hosted comedy, this is sabotage — all three in German, all
three on one lobby screen, all three answering questions on a phone. Two are
defensible; three needs a category rather than a flat list. The honest alternative
is traps plus the climb track as **a mode of Buzzer-Quiz**: one `GameKind`, a lobby
toggle, less code, one fewer thing to explain at the table.

No AI, no props, 2–6 players, no age gate (our own questions, so the kids set
works). **Deliberately omitted: the selfie characters.** PlayLink had players
photograph themselves to personalise a puppet; this project takes no images from
phones and that is not a rule worth bending for a character select. **Character
select is already solved, though**: the shipped avatar catalogue (22 avatars,
`Action::PickAvatar`, taken-by-someone-else handled in `avatars::assign`) is
exactly the choice PlayLink's selfie made, minus the camera. Denkzettel needs no
new character system — its puppets are the avatars already picked in the lobby.

Own name, own track, own traps: not "Knowledge is Power", not PlayLink, not Sony's
puppets or stop-motion set.

## One engine, seven variation points

There are not four families of board game. There is one engine — a board as a graph
of tiles, pieces on it, one renderer, one camera, one pre-drawn texture and sound
system — and seven places where a game differs:

| Variation point | Range across these games | Engine consequence |
|---|---|---|
| Turn shape | dice-walk (Heimlauf, Wissensreise, Lebensweg, Bauherr, Grundstück, Pleitier, Radau, Spielwiese) · chosen move on a static map (Flüchtig, Neuland, Feldzug) · place a piece on a grid (Wortwerk) · advance by answering (Denkzettel) | four action models, not one; the die is optional |
| Interference | none — players race their own game (most) · one player hidden from all the others (Flüchtig) · traps placed on another player's screen (Denkzettel) | a per-player status-effect set and a way to target someone; the first thing here that writes to another player's phone |
| Board mutability | fixed at start (all but one) · rebuilt every turn (Gassenwechsel) | the graph must be replaceable data, and tiles must animate, not only tokens |
| Tile effect | question · money · build · task · nothing · shift | a per-game effect handler, where most games actually differ |
| Hidden information | all public · one player's board secret (Flüchtig) · each player's own goal list (Gassenwechsel) · each player's own hand (the cards) | already solved: `phone_screen(PlayerId)` |
| Opponents | none needed (Wissensreise, Lebensweg, Flüchtig, Gassenwechsel, Pleitier, Radau, Wortwerk) · AI or a player minimum (Bauherr, Neuland, Feldzug, Grundstück) | AI is opt-in per game, never in the engine |
| Who runs the game | the players take turns (all but two) · a recorded host drives the clock and gives orders (Nachtwache) · a recorded host presents questions and jokes (Neunmalklug) | one host-video subsystem — a `VideoTexture` plane and a timed script — serves both, so the second is far cheaper than the first |

The engine is therefore explicitly **not** "a board plus a die". Heimlauf's die loop
is one turn shape among four and one effect handler among many. Getting the graph
replaceable and the die optional in phase 1 is what makes items 6–13 cheap; getting
it wrong makes them rewrites.

## What the origin names cost

Already shipped, for the same column: **Heimlauf** ← Mensch ärgere Dich nicht
(Schmidt Spiele, 1914) / Ludo; **Buzzer-Quiz** ← Buzz! (SCEE, 2005), which is
why the spec had the draft's "Buzz-style" wording renamed to Buzzer-Quiz;
**Poster-Raten** ← none, it is this project's own idea.

**Every board game above descends from a game that is still trademarked and
still sold.** Game rules are not protectable, so reimplementing Monopoly's
mechanics is fine; its name, board artwork, property names and trade dress are
not, and spec line 47 already forbids them. An origin name here is therefore a
**source of rules to reimplement and a list of things to avoid**, never a name
to show on screen:

- Wissensreise: not Trivial Pursuit's six categories, not its pie/wedge board
  shape, not its colour set. Pick six categories and a token that is not a wedge.
- Lebensweg: not the wheel-and-pointer spinner look, not Milton Bradley's pink
  and blue peg people, not its life-event card text.
- Bauherr: not Hotel's facade tiles, not its per-night rent table.
- Grundstück, the worst offender: no street names, no paired chance/community
  decks, no top-hat-dog-ship tokens, no board colour order.
- Spielwiese: not Mario Party's board style, minigame framing or star scoring.
- Flüchtig: not London's map, not Ravensburger's station names or ticket faces.
- Wortwerk: not Scrabble's board colours, premium-square wording or tile values
  as a set — and above all not its licensed word list.
- Neuland: not Catan's hex art, resource icons, development-card text or its
  longest-road/largest-army naming.
- Feldzug: not Risk's world map, territory names or colour blocks.
- Gassenwechsel: not Ravensburger's tile art, treasure names or creature set.
- Pleitier: not Mankomania's champagne-bottle tokens, its board or its money
  denominations.
- Radau: no Ravensburger hippo, no "Drecksau", own coaster and own term deck.
- Sprechstunde: not Therapy's couch pieces, inkblot booklet or card text.
- Nachtwache: not Atmosfear or Nightmare as a name, not the Gatekeeper, not the
  realm names, not the Spear/Schmidt board.
- Neunmalklug: not "You Don't Know Jack", not Jackbox, not Cookie Masterson, and
  none of the original's question or joke text.
- Denkzettel: not "Knowledge is Power", not PlayLink, not Sony's puppets or its
  stop-motion set.

All sixteen are trademarked games still on sale or still recognisable, so each
ships under its own name with its own board art.

## Every asset is recreated

Every asset is original — spec line 47 already forbids the artwork of existing
games, and the user named the case that costs most (2026-11-06): **the Atmosfear
host video is recreated from nothing** (detailed below). That is not only the legal
route, it is where the real cost of this list sits. The engine is the cheap half.
Per game, the work is:

| Asset kind | What it is | Where it goes | Already exists |
|---|---|---|---|
| Board surface | the plane the game is drawn on, matte not shiny | a JPEG like `client/public/race/board-cherry-veneer.jpg`, `table-lacquered-cherry.jpg`, `frame-dark-wood.jpg` | yes, three — reusable by every table game |
| Tile and field art | pre-drawn canvases, **not HTML over the canvas** (box rule) | the `race-print.ts` pattern: draw once at load, upload as a texture, `renderer.compile` | yes, the technique; not the pictures |
| Tokens | 2D sprites on the board (3D only on reaction and win screens, already decided) | sprites beside the board textures | Heimlauf's turned wooden pawns; each game wants its own set |
| Card faces | one French 52-pack, subsets by `Deck::from_rank(lowest)` | `cards.rs` plus face textures | no — the card plan's phase 1 |
| Sound cues | short CC0 oggs scheduled off the audio clock | `client/public/race/sound/` | yes — die clatter, five pawn knocks, capture, goal and winner jingles, two music beds. Most of it transfers as-is |
| Questions and terms | text content in German | Rust data beside `general.rs` (311 sourced questions) and `film_questions.rs` | partly — Wissensreise inherits the existing packs; Radau and Sprechstunde need decks written from nothing |
| Host video | an hour of German video, only for Nachtwache | library assets the existing player already serves | no — see "The Nachtwache host video" below |
| Word list | Wortwerk's dictionary | offline in the core | no — licensed list cannot ship; see open question 6 |

Two costs to keep in view:

- **The APK.** Sound assets cost 2× in the package because two ABIs are built
  (measured in `plans/reports/benchmark-261105-1611-tv-box-3d-framework-spike-report.md`).
  Textures and video do not duplicate that way, but every new megabyte ships to
  every box. Prefer a few shared boards over one per game.
- **Matte, not shiny.** Already decided for the box's GPU: big flat surfaces unlit
  with baked light, no alpha-tested cut-outs, no per-dot noise or blur on large
  canvases. Recreating the art is also the chance to bake lighting into the
  pictures instead of paying for it at 60 fps.

The consequence for ordering: a game with **no new assets** is worth more than its
rules complexity suggests. Gassenwechsel needs tiles and treasures; Pleitier needs
a board and four tile effects; both reuse Heimlauf's sound almost untouched.
Nachtwache needs nearly no code and an hour of filmed German, which is why it sits
last despite being architecturally easy.

The ten card games in `plans/261106-1808-card-games/plan.md` have **none of this
problem** — all ten are traditional public-domain games, which is why they keep
their real names.

### The Nachtwache host video

This is the asset the user named (2026-11-06): **the host video is recreated from
nothing.** Nothing from the 1991 tape can be used — not the Gatekeeper's
performance (Wenanty Nosul), not his voice, not the realm footage, not the music.

**One design difference makes ours cheaper than the original.** Atmosfear ran a
single continuous 60-minute tape, and the board game had to keep pace with it; the
clock and the host's lines were burned into one linear recording. Ours does not
have to work that way. The host can be a **library of short clips fired by game
state** — the Rust game decides when to taunt, when to demand a key, when to end —
and the sixty-minute clock is a number the host already tracks, not video time.
That turns one hour of continuous filming into a few minutes of short takes,
each re-recordable on its own, and it means a clip never has to line up with a
board that has drifted.

What has to be produced:

| Piece | Roughly | Notes |
|---|---|---|
| The host character | 1 | An actor on a dark set, or a rendered figure. Decide before anything is shot; it is the one irreversible choice |
| German voice | all clips | One voice, consistent. The original's menace is mostly in the delivery, so this is the part worth spending on |
| Intro and rules | 2–3 clips | Shown once at setup; also the game's only tutorial |
| Fear-card reads | per player | The player types a fear on the phone; the host says it back. Either generic lines that fit any fear, or the text rendered as a caption while a generic taunt plays — **the caption route is far cheaper and needs no per-fear recording** |
| Timed taunts | ~10–20 short | Played at intervals and on events; the bulk of the atmosphere |
| Key and realm events | ~6–12 | One per key taken or lost |
| Hour-up sting | 1 | The host wins. Needs to land |
| Music bed | 1 loop | Heimlauf already has two CC0 beds; this one wants something darker |

Playback route, restated because it constrains the shoot: the host is a
`VideoTexture` on a plane inside the three.js scene, **not** a `<video>` element
over the canvas (box rule). That means the clips should be shot or rendered with a
**solid or alpha-friendly background**, framed for a plane in the scene rather than
full-screen, and short — a texture that is decoded per frame on a Mali-G57 with
four A55 cores is the unmeasured risk here (open question 13). Full-screen
letterboxed video would sidestep the texture cost but breaks the rule that keeps
the canvas at 60 fps, so it is worth measuring before choosing.

Assets live beside the other game media and are served by the machinery that
already exists; the host is not a new subsystem, it is library video plus a plane.

## Cross-cutting, owed to every board game at once

- **3D avatars.** ~~Decided for reaction, winning and losing screens; not built.~~
  **BUILT** as its own project between Heimlauf and the board games, exactly as
  recommended here: `avatars.rs` with a 22-entry catalogue, per-player ownership
  (first come first served, `assign` falls back to a random free one),
  `Action::PickAvatar`, `/avatars/<id>-head.webp` on phones, and the full-detail
  3D ceremony stage in `client/src/tv/ceremony/` (`ceremony-avatar.ts`,
  `ceremony-set.ts`, `ceremony-stage.ts`, `ceremony-studio-look.ts`,
  `ceremony-sound.ts`). **This is the single largest cost removed from this list**:
  every board game now inherits a character the player chose, and Denkzettel,
  Sprechstunde and Neunmalklug inherit the reaction screens that need one.
  What remains per game is only the 2D sprite for pieces on the board — Heimlauf's
  turned wooden pawns are its own, and each board wants its own.
- **Sound.** Heimlauf's system is reusable wholesale: the die clatter and the
  music already exist, a token step is a pawn knock. New boards need at most a
  win jingle each.
- **Props the room must supply.** Some games need objects a TV box cannot ship.
  The games overview — the TV lobby's game picker, and `docs/` beside it — must
  list them per game, so a family sees what to fetch before they pick, not after.
  See "Props each game asks the room for" below.
- **The card table** (`plans/261106-1808-card-games/plan.md`) shares the table
  surface, `race-print.ts` textures and the sound scheduler, but not the board
  engine. Cards and boards are siblings under one table, not one under the other.

## Props each game asks the room for

The rule: a game that needs something the box does not provide must say so in the
games overview, in the same German the rest of the game speaks, before the game is
chosen. Silence reads as a bug at the table.

| Game | Needs from the room | If it is not there |
|---|---|---|
| Radau | **Modelling clay** (Knete) for the moulding task — the 1988 original ships a stick | Task type dropped; describe, act, sound and draw remain. Recorded in the overview, not hidden |
| Radau | Paper and pen, only if the draw task is played on paper instead of a phone | The phone canvas replaces it |
| Wortwerk | Letter tiles are rendered on screen, so nothing physical — but a **printed score sheet** helps at 2–4 players over a long game | Phone score view is enough |
| Neuland | Nothing physical; resource and building tokens are on screen | — |
| Feldzug | Nothing physical; armies are tokens. A long game does want a **table people will not disturb** for hours | Game can be paused and resumed, but a knocked-over phone costs a turn |
| Sprechstunde | Nothing physical, but it asks for **honest answers about the people present** — the real requirement is a room that wants that | Do not offer it in a lobby with a *Nur für Kinder* toggle; see open question 9 |
| Nachtwache | **A dark room and the volume up** — the original's own instructions. Paper and pen for the fear cards, or a phone field for them | Played in a lit room it loses most of its effect; the game should say so rather than pretend otherwise |
| Neunmalklug | Nothing — phones in hand | Needs no prop, no dark room and no grown-up gate, which makes it the easiest to suggest to any group |
| Denkzettel | Nothing — phones in hand | Same, and it is the one that works in the brightest living room |
| Everything else | A phone per player, on the same Wi-Fi, and the TV in sight | The game cannot start |

Two things are worth checking when this lands: that the prop list is **per game,
not per session**, so a family picking Radau sees the clay note and one picking
Gassenwechsel does not; and that the note appears in the picker, not only in
`docs/`, because nobody reads the repo at the table.

## Proposed order

1. **Board engine + Wissensreise** — proves the graph board with questions that
   already exist, so the risk is all in the engine and none in the content. The
   engine must be shaped for typed edges, a replaceable graph, an optional die and
   three turn shapes, even though only dice-walking is used here.
2. **Gassenwechsel** — 2–4 players, 25–40 minutes, age 8+, no AI, and its secret
   treasure lists ride the per-player `phone_screen` that already exists. It is the
   only game that mutates the board, so it proves the graph is really data while the
   engine is still young enough to fix.
3. **Flüchtig** — no AI, no new phone screen, and it proves the static-map turn
   shape on a board with typed edges.
4. **Pleitier** — the dice board plus four money tile effects; Spielwiese's value
   without its framework.
5. **Radau** — roll-and-perform with a timer and terms the quiz already renders;
   drawing on a phone canvas is the only new input, and it needs no HTTPS.
6. **Lebensweg** — forks the graph, no new systems.
7. **Bauherr** (the Hotel slot) — first game needing a new phone screen and an
   AI-or-gate decision.
8. **Feldzug** — dice combat on a static map; long, so it needs the backgrounding
   and reload durability proven first.
9. **Neuland** — after the trading protocol is designed on its own.
10. **Wortwerk** — only if a German word list can be sourced that players accept;
    the list, not the game, is the blocker.
11. **Grundstück** — only if the ones above land well; revisit or drop.
12. **Spielwiese** — last, as already decided, and the one that may force HTTPS.
13. **Sprechstunde** — only after the age-gate question is settled; it is 16+/18+
    content and a question-writing project of its own.
14. **Nachtwache** — almost no new systems, because the host is video this box
    already plays, but it needs a filmed or rendered German host for a full hour.
    Spike the `VideoTexture` host plane early; schedule the production last.
15. **Neunmalklug** — **run it before Nachtwache and reverse 14 and 15** if the
    host video is ever going to happen. It needs far fewer clips (short question
    asides, not an hour of continuous hosting), it reuses the question and scoring
    machinery that already exists, and it proves the host-video subsystem on a game
    that is still playable if the host turns out too expensive. Nachtwache cannot
    be played at all without its host.
16. **Denkzettel** — **promote this to slot 3, right after Flüchtig**, unless open
    question 17 makes it a mode of Buzzer-Quiz instead. It is the only game that
    composes the board engine with the quiz engine, it needs the cheapest board on
    the list, and it reuses question machinery that is built and tested. Proving
    the two engines compose early is worth more than the game itself.

Avatars and per-game sound ride along with whichever game needs them first.

## Open questions

1. **Board as a graph from the start?** Recommended yes; it is free now and a
   rewrite later.
2. **Wissensreise first?** It is the cheapest because the question packs, the
   kids filter and the answer screen are already built and tested.
3. **Grundstück: build last, or drop?** Longest rules, longest play, closest to
   an existing game.
4. **Player-count gates vs bots.** Heimlauf gates at 2–4 rather than filling
   seats with computer players. Keep that for Bauherr, Neuland and Feldzug, or
   invest in board-game AI?
5. ~~**Avatars as their own project** between board games, rather than inside one?~~
   **Answered by shipping.** They landed as their own project (`fd7009ce`,
   0.151.0) and every game inherits them.
6. **Wortwerk's word list.** The official Scrabble list is licensed and cannot
   ship; a free German list can, but its calls on what is a word will read as bugs
   at the table. Research a source before committing, or drop the game.
7. **Neuland's trading.** A free-form offer between two players is the only
   phone-to-phone private protocol in either plan. Is a simplified fixed-price
   trade enough, or is negotiation the point?
8. **Feldzug's length.** Hours on a box whose job is films, and it must survive
   backgrounding and reloads. Worth it, or does it stay on the list unbuilt?
9. **Sprechstunde's age gate.** The origin game is 16+/18+ and its content is
   personal questions about the people in the room, asked aloud on a TV. Hide it
   behind the grown-up profile check Kotlin already does, or leave it off the list?
   Kings has the same question.
10. **Radau without plasticine.** The moulding task cannot exist on a TV box. Is a
    four-task version (describe, act, sound, draw) still the game, or does dropping
    it change what people liked? Whichever way it goes, the prop note ships in the
    games overview.
11. **Hotel is Bauherr.** One slot, already listed. If a faithful Hotel
    reimplementation is wanted rather than a hotel game in its spirit, that is a
    rules-fidelity call inside Bauherr's phase.
12. **Nachtwache's host.** An hour of German video — filmed with an actor,
    rendered as a 3D figure, or generated? This is the only game on the list whose
    main cost is production rather than code, and the choice decides whether it is
    ever built. It shares Sprechstunde's and Kings' age gate.
13. **`VideoTexture` on the box.** Unmeasured. The spike that settles it is small:
    one looping clip as a plane in the existing three.js scene, watched with
    `gfxinfo` at 960×540 and 1080p like every other measurement in
    `plans/reports/`.
14. **Who writes the jokes?** Neunmalklug's questions are only funny if someone
    writes them funny, in German, in volume — the existing 311 questions are sourced
    facts in a neutral voice and will not carry it. That is the game's real cost,
    and it is a writing job, not a build job.
15. **Three trivia games.** Buzzer-Quiz (speed), Neunmalklug (hosted comedy),
    Denkzettel (sabotage) — in German, on one lobby screen. Which two survive, or
    does the picker grow a category?
16. **Is Denkzettel a game or a mode?** Its only new mechanics are traps and
    advance-by-answering; everything else is Buzzer-Quiz's, already built. A lobby
    toggle is less code and one fewer thing to explain at the table. Recommended:
    build it as a mode first, and give it its own `GameKind` only if the climb track
    turns out to want a real board.
17. **No phone images, ever.** PlayLink personalised its puppets with a selfie.
    This project takes nothing from a phone's camera or library. Denkzettel needs
    no exception: the shipped avatar catalogue is the character select, so the
    rule holds and costs nothing. Confirmed.

## Results

(to be filled in)
