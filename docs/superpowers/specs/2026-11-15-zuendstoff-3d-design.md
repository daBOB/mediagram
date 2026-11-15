# Zündstoff in 3D — design

Zündstoff is redone as a 3D game: every player's avatar stands in a circle on the TV, a bomb with a hidden fuse is
thrown from one to another, and the target has to catch it. Whoever holds it when it goes off loses a life.

## Why

The shipped Zündstoff (Spieltisch 0.187.0) is a flat 2D screen where the holder answers a 4-choice question before
each throw. On 2026-11-15 the user said this was built wrong: Zündstoff is a 3D game, avatars in a circle, nobody
knows when the bomb explodes, and players throw it to each other. The quiz idea moves into a separate game,
Letztes Wort (`2026-11-15-letztes-wort-design.md`).

## Rules

The user's:
- All players' avatars **stand in a circle**, in 3D, on the TV.
- **Nobody knows when the bomb explodes** (hidden fuse, no clock anywhere).
- The holder **throws the bomb to another player**.
- The throw rule the user picked: the holder **taps a name** on the phone; the bomb **flies about 1 s**; the target
  must **tap "Fangen!"** as it lands, watching the TV. **A miss drops it at their feet**, and picking it up costs about 2 s.
- Built with **new Meshy animations** for throwing and catching.

Carried over from today's Zündstoff (decided 2026-11-14, not reopened):
- 3 lives each; the lobby's Dauer setting gives 2 / 3 / 5. At zero a player is out; the last one left wins.
- The fuse burns 18–40 s per round; the lobby's Tempo stretches it.
- After an explosion the victim starts the next round (a random player still in, if the victim is out).
- An away player's turn is played by the house after the usual delay (today: a random throw).

## Calls I made (the user can overrule any)

- **No question before throwing** any more: the question deck leaves Zündstoff.
- **Catch window**: the Fangen! key is live from 400 ms before the landing to 400 ms after (plus the usual
  150 ms clock tolerance). A tap before the window opens is a fumble and counts as a miss, so mashing doesn't work.
- **After a catch** the catcher can throw at once. **After a miss** the phone shows the names again after ~2 s;
  the avatar picks the bomb up (pick-up clip) before throwing.
- **Fuse runs out mid-flight**: the bomb goes off on the player it was thrown to. A dropped bomb belongs to the
  player at whose feet it lies.
- **Throwing straight back is allowed**: the holder may throw to anyone still in, including whoever just threw.
- **Away players** never catch (always a miss); the house throws for them after the usual delay.
- **Out players** stay in the circle but greyed and sitting (or step back), so the circle keeps its shape.
- **The four clips** (Meshy library, previews checked 2026-11-15):
  throw `Over_Shoulder_Throw` (421), catch `Leap_Right_and_Catch` (465), miss `Jump_to_Catch_and_Fall` (419: jumps,
  misses, lands flat), pick-up `Male_Bend_Over_Pick_Up` (276). The blast victim uses the existing `Falling_Down`.
  4 clips × 22 avatars × 3 credits ≈ 264 Meshy credits (balance 4,590).

- **The phone gives no hint of the window**: Fangen! looks live from the moment of the throw, so players watch
  the TV, and an early tap can be a fumble.
- **The host fixes the landing time** at the throw: tap time + the throw clip's lead-in to the release + the flight.
  The catch window is measured from it. The 150 ms tolerance and the TV's display lag are measured on the TV box
  before the window is fixed.
- ~~Zündstoff gets its own ~20k-triangle avatar copies~~ — replaced: the funstation draws the ceremony copies (below).
- **Catch and miss clips are pinned in place** (`pin_clip_in_place_gltf.py`) if Meshy moves the avatar off its spot,
  so the circle keeps its shape. The bomb hangs on the `RightHand` bone (all avatars have one).
- Noted for the playtest, not changed: with no question, nothing rewards holding the bomb, so a round is a fast
  chain of throws and catching is the skill. That is the rule the user chose.

## Decided after the first renders (2026-11-15)

- **Camera A, the high overview** (~45° down onto the whole circle; user's pick of three):
  `plans/261115-2340-zuendstoff-3d/reports/circle-camera-a.jpg` (b: low show angle, c: top-down, both rejected).
  The look in the renders: a round felt-green table with a wooden rim in a dark room, a spot from above, names and
  hearts under each avatar, an out player see-through. Geometry check of the render: every avatar's feet at y = 0,
  no two avatars' bounds overlap at circle radius 4.5 (at 4.0 the bear's shrug touched the yeti), the bomb clear.
- **The funstation is the target, not the old TV box** (user, 2026-11-15, recorded in the spieltisch game brief: "mark
  the 3d on tv box as deprecated … if we want to play we have to use the funstation"). So no separate 20k-triangle
  copies: Zündstoff uses the awards ceremony's avatar copies, with its four clips added to the clip list of
  `make-ceremony-avatars.mjs` (one set of files for both). Frame rate measured on the funstation.
- **Fits the coming TV table shell**: a peer session (plan `plans/261115-2305-deepening-modules/`, phases 05 and 07) is
  extracting one lifecycle and stage host for the 3D boards from Grand Allee. Zündstoff's TV stage follows
  `TvGrandAllee.tsx` / `grand-allee-stage.ts` so it becomes the fourth board; the holder's and catcher's timing goes
  on that session's Ask value if it reaches main first.

## Approaches

- **New Meshy throw and catch clips (picked by the user)**: added to every avatar through
  `assets/game-night/avatars/add-gestures.sh`, which appends Meshy gesture sets to each avatar's master and remakes
  its game copy. Looks right; costs credits and a pass of avatar QA.
- **Existing clips only (rejected)**: the bomb flies while the thrower waves and the catcher jumps; nobody actually
  throws.
- **Flat or 2.5D figures (ruled out)**: the user asked for 3D.

## What gets built

- Avatars: the four clips on all 22 avatars; each checked playing them (arms through heads, feet through floor,
  as in the earlier gesture passes), then added to the ceremony copies' clip list (one file per avatar for podium and
  Zündstoff). Frame rate with 8 avatars measured on the funstation.
- TV: a three.js stage built like the awards ceremony (`client/src/tv/ceremony/`, lazily loaded, flat figures until
  each avatar arrives): avatars in a circle facing the middle, the bomb in the holder's hand (attached to the hand
  bone), a sparking fuse, a parabola flight, the drop, the blast. Names and lives under each avatar. A camera that
  shows the whole circle.
- Host (`crates/mediagram-game/src/zuendstoff*.rs`): the stage machine becomes hold → flight → caught / dropped →
  boom, with the catch window and fumble rule; the question deck and its factory wiring are removed; protocol and
  views follow. Tests for catch, fumble, miss, mid-flight explosion, away players, lives and the lobby settings.
- Phone: the names to throw to (today's throw list), the big Fangen! key, the "dropped, wait" state, the out state.

## Not in scope

No questions, no visible fuse clock, no aiming or swiping, no power-ups.

## Left to the builder

The look: circle size, camera, floor, light, bomb model (mockup renders first; the user decides from pictures, and
3D gets a geometry check before it is shown). Flight arc height and duration within ~1 s, sounds, the boom effect,
the house delay.
