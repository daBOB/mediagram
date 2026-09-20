# Android player transport controls — design

The Android player has one touch control: a back arrow. A viewer cannot
pause, cannot see where they are in a film, and cannot move. The web player
has had all three since it existed, because `<video controls>` hands them
over for nothing — which is exactly how the gap opened without anyone
deciding it should.

This adds play/pause, a scrubber, skip by ten seconds either way, and a
position readout to the phone surface, and settles where playback control
lives so the features that follow it do not each have to decide again.

## 1. What this is a part of

"Parity with the web player" is four pieces of work, not one. They are named
here so the boundaries of this one are legible, and so the later ones are not
rediscovered from scratch:

| | Piece | Needs |
|---|---|---|
| **A** | **Transport controls — this document** | nothing outside `ui-mobile` and `core:playback` |
| B | Audio track picker, buffer and preload readout | A |
| C | Watch state: resume, watchlist, kids, collections, notes | a new core surface, and a decision about where the state lives |
| D | Next-episode autoplay and the up-next overlay | A and C |

C is the one that is not a port. The web player keeps watch state in a
`bun:sqlite` database on the server, and `web/src/state/store.ts` says that is
the point — a phone and a laptop pointed at the same player share a position
because the position belongs to the title, not to the device. The Android app
has no server by design, and `crates/mediagram-core/src/api/mod.rs` exposes
twelve methods, none of which touch progress. Giving Android a resume point
therefore means choosing a home for that state, not moving code. It gets its
own design.

## 2. The decision this settles

Transport controls could be built in two places, and the choice matters more
than the controls do, because B, C and D all attach to whatever it decides.

> **media3's state holders own transport.** Whether the player is playing,
> where the playhead is, how long the set runs, seeking by an increment —
> facts ExoPlayer already knows. The UI reads them from the `Player` that
> `PlayerViewModel` already exposes.
>
> **`PlayerViewModel` and `PlayerHandle` own the library.** Which set is
> open, whether it is preparing or failed, stopping when the screen is left
> for good — everything that needs `CoreClient`.

This line goes in `feature/player`'s module documentation, because it is the
answer to a question that will be asked three more times.

The alternative — routing play, pause and seek through `PlayerHandle` and
ticking position from a coroutine in `DefaultPlayerHandle` — was considered
and rejected. It would put every control behind the JVM-testable seam that
`FakePlayerHandle` already stands in for, which is a real gain; but media3
`1.10.1` ships `PlayPauseButtonState`, `SeekBackButtonState`,
`SeekForwardButtonState` and `ProgressStateWithTickInterval`, all tested
upstream, and `ProgressStateJob` already does the lifecycle work a hand-rolled
ticker would have to learn. The seam's other argument is the television
surface, and it does not hold either: `PlayPauseButtonState.onClick()` answers
a D-pad key exactly as well as it answers a tap.

`PlayerScreen` already takes the `Player` and drives `rememberPresentationState`
from it. This follows that pattern rather than introducing one.

## 3. What gets built

### New, in `android/ui-mobile/src/main/kotlin/`

**`PlayerControls.kt`** — the bar. Skip back, play/pause, skip forward across
the middle; a scrubber beneath; elapsed on the left and duration on the right.

```
      ⏪10    ▶/⏸    ⏩10
   ──────●───────────────────
   12:34                1:58:02
```

Driven by `rememberPlayPauseButtonState`, `rememberSeekBackButtonState`,
`rememberSeekForwardButtonState` and `rememberProgressStateWithTickInterval`,
all against the `Player` passed in.

**`ControlsVisibility.kt`** — when the bar may be on screen, and when it
fades, as pure functions. Pure because `ui-mobile` has no Compose test rule:
`PlayerScreenTest` tests `shouldStopOnDispose` and nothing else, and says so.
Decisions that live in a function can be proved; decisions that live in a
`LaunchedEffect` cannot, in this module, today.

**`PlayerClock.kt`** — `clockTime(ms)`, the elapsed and total times as a
viewer reads them. There is no time formatter anywhere in the Kotlin sources
today, so this is new rather than reused; it mirrors `clockTime` in the web
player's `format.js` down to dropping the hour when there isn't one, because
two surfaces over one library should not print a time two ways.

These are separate files rather than more of `PlayerScreen.kt` because that
file is at 167 lines of the 200 the architecture allows
(`docs/system-architecture.md` §2), and the bar does not fit in 33.

### Modified

| File | Change |
|---|---|
| `ui-mobile/.../PlayerScreen.kt` | hosts the bar; tap toggles it |
| `core/playback/.../PlayerFactory.kt` | `setSeekBackIncrementMs(10_000)`, `setSeekForwardIncrementMs(10_000)` |
| `feature/player/.../PlayerUiState.kt` | `Playing` and `Paused` lose their fields |
| `feature/player/.../PlayerHandle.kt` | `onPositionChanged(pos, dur, isPlaying)` → `onPlayingChanged(isPlaying)` |
| `feature/player/.../PlayerViewModel.kt`, `DefaultPlayerHandle.kt` | follow the renamed listener |
| four tests and `FakePlayerHandle` | follow |

**Both increments must be set.** media3's defaults are five seconds back and
fifteen forward; a bar labelled 10 either way that moves 5 and 15 is a lie
told by omission.

**On the trimmed fields.** `PlayerUiState.Playing(positionMs, durationMs)`
has never ticked — `DefaultPlayerHandle` notifies only when playing starts or
stops and when the player reaches `STATE_READY`. Under the decision in §2 the
position comes from media3 instead, so the fields would stay dead forever.

One test reads them: `askingAgainForASetThatIsPlayingKeepsItsPositionAndSaysWhereItIs`
in `PlayerReopenTest`. It makes two guarantees and only one is in the number.
That a reopened set is *not reloaded* — the guarantee that protects a viewer's
place across a rotation — is proved by `verify(exactly = 1) { setMediaItem }`,
which is untouched. That it *reports where it is* is proved by the position,
and that concern dissolves here: after a rotation the new Composition reads
the playhead from the `Player`, which never forgot it. What still matters is
that the reopened set reports `Playing` rather than `Preparing`, so the screen
does not wait on a spinner forever, and asserting the state alone still proves
that. The test keeps both real guarantees and is renamed to claim only them.

A second consequence, worth knowing rather than fixing: as objects, repeated
`Playing` values collapse in the `StateFlow` instead of emitting. That is
fewer recompositions, not fewer facts.

## 4. How it behaves

The bar is visible when the screen opens, so a viewer finds out it exists,
and fades four seconds later. A tap toggles it; showing restarts the timer.

It **never fades while paused**. A paused picture with no controls is a dead
end — nothing on screen offers a way to start again, and the gesture that
would is the one the viewer cannot see. It never fades mid-drag either.

While `Preparing` or `Failed` there is no bar at all: there is nothing to
control, and a scrubber over an error message invites a drag that cannot do
anything. Back stays reachable in every state, where it already is.

The scrubber follows the ticker except while it is being dragged, when it
shows the drag. This is the problem the web player solved at
`web/public/lib/player.js:584` by refusing to move the slider while it holds
focus; the same fix, in Compose's idiom. The seek fires on release, never
during — `player.js:595` listens for `change` rather than `input` for the same
reason, which for the web player was that every drag position would start an
encode, and here is that every drag position would be a seek into a Telegram
read.

## 5. Where it can go wrong

A seek that fails reaches the viewer through the path that already exists:
ExoPlayer raises it, `DefaultPlayerHandle.playerListener` catches it, and the
state becomes `Failed`. Nothing new is needed and nothing new is added.

One disagreement is worth recording without acting on it.
`MlibDataSource.open` rejects a `dataSpec.position` strictly greater than the
set's total, while the Rust `read` rejects an `offset` greater than *or equal
to* it. A read starting at exactly the last byte is accepted by one and
refused by the other. ExoPlayer should never ask for it, and chasing it inside
this piece of work would be scope it does not own — but the next person to
touch either end should know the two ends do not agree.

## 6. Proving the seek

`PlayerScreen.kt` carries a hold, written when the screen was built:

> There is no scrubber or seek bar yet, since nothing has verified seeking
> across a part boundary works; adding one before that is proven would let a
> user hit a bug no test caught.

The matching gate is phase 5, task 4, step 5 of the android-foundation plan,
and it has never been run. It is run here.

The machinery says it should pass. `MlibDataSource.open` honours an arbitrary
`dataSpec.position` and drops held bytes so a seek is not served stale ones;
the Rust `read` plans across parts through `range::plan_reads` and walks the
resulting steps; and its module documentation says this is the same
range-planning code `mediagram serve` uses — the code the web player has been
exercising in production for days. None of that is a measurement.

**The order matters: the scrubber is built, then proven on the phone, then
committed.** A scrubber that ships before the gate runs is precisely what the
hold forbids, and "it will almost certainly pass" is the argument the hold was
written against.

Two traps, both of which would make the gate lie:

- **The disk cache.** A 2 GiB LRU cache lives at `cache/mlib`. A film already
  in it seeks instantly and proves nothing about the byte path. Clear it with
  `adb shell run-as` before the run.
- **Tapping.** `adb shell input tap` is unreliable against Compose; use
  `adb shell input swipe X Y X Y 150`.

The run: take a set with more than one part, read the first part's
`byte_length` from the index, seek to a position either side of that
boundary, and confirm playback continues without stalling or artefacts.

## 7. Definition of done

- Play, pause, skip ±10s and drag-to-seek all work on the phone.
- Elapsed and duration advance while playing.
- The bar fades after four seconds of play, stays while paused, and survives
  a rotation.
- `./gradlew :core:playback:testDebugUnitTest :feature:player:testDebugUnitTest :ui-mobile:testDebugUnitTest` passes.
- A seek across a part boundary is clean on a real phone, against a cleared
  cache — and phase 5's step 5 is ticked, with what was observed.
- `feature/player` documents the boundary from §2.

## 8. Testing

| What | Where | How |
|---|---|---|
| Auto-hide decisions | `ui-mobile`, JVM | pure functions, as `shouldStopOnDispose` is |
| Both seek increments are 10s | `core:playback`, Robolectric | extend `PlayerFactoryTest` |
| Trimmed state | `feature/player` | the four existing tests, updated |
| Seek across a part boundary | a real phone | by hand, §6 |

Compose UI testing is deliberately not introduced. `ui-mobile` has no test
rule and adding one is its own piece of work; the decisions that would need it
are in pure functions instead, which is the arrangement the module already
chose.

## 9. Deliberately out of scope

**The television surface.** `TvPlayerScreen` and its remote mapping belong to
phase 4, which is blocked for want of a television. The boundary in §2 is
chosen so that surface can reuse these state holders when it arrives.

**Everything the web player does about bandwidth.** `hls-playback.js`,
`adapt-bitrate.js`, `adapt-playback.js`, and the reason the web player's jump
slider calls `convert()` rather than seeking, all exist because that player
streams through a server that transcodes. Android reads original bytes from
Telegram and decodes them natively — phase 5's step 7 requires that no
conversion happens anywhere. There is no encode to restart and no bitrate to
step down to. Android's seek is simply a seek.

This is a difference that is meant, which under the Surface Parity rule has to
be written down where someone will find it. This section is that place, and
what follows belongs to it as much as the paragraph above does.

**The bar goes away on rules the web's HUD does not use.** Three differences in
one mechanism:

*It waits longer.* `CONTROLS_LINGER_MS` (`ControlsVisibility.kt:15`) is 4 000
ms against the web's `HUD_REST_MS` of 2 600 (`web/public/lib/player.js:513`).
The web can afford the shorter rest because `pointermove` is one of the events
that brings its HUD back (`web/public/lib/player.js:534`) — the controls return
for a pointer that twitches. On a phone the only way back is a deliberate tap
on the picture, so the bar is given longer before it asks for one.

*Using a control does not keep it up.* The web dialog re-arms its hide timer on
every `pointerdown` and `focusin` inside it (`web/public/lib/player.js:534`),
so pressing a control is itself a reason for the controls to stay. Android
re-arms on nothing but a change of `PlayerUiState`: the `LaunchedEffect` at
`PlayerScreen.kt:76` is keyed on `controlsShown`, `state` and `scrubbing`, and
a skip changes none of the three. Press ⏪ at 3.9 seconds and the bar goes at
4.0, out from under the thumb that is using it.

This one is **unresolved, and waiting on a decision** — it is recorded rather
than fixed, because the fade rule is a design that was chosen and reversing it
quietly is not a fix. If it is taken up, the shape is small: keep a count of
interactions in `PlayerScreen`, raise it where the bar's own buttons are
pressed, and add the count to that `LaunchedEffect`'s key. Six lines or so,
none of them the hard part.

*A tap on the bar's own scrim hides it.* `PlayerScreen.kt:91` puts
`detectTapGestures` on the whole screen. Compose's children consume their own
taps, so the buttons and the slider are safe, but the scrim between two glyphs
is not a child — it is the screen. A viewer reaching for ⏸ and missing it
dismisses the bar; the web player, which hears that same press as a reason to
stay, would have kept it.

**Telling a viewer that it is buffering.** A seek takes ExoPlayer through
`STATE_BUFFERING`, where `isPlaying` is false, and `DefaultPlayerHandle.kt:57`
forwards every `onIsPlayingChanged`. So every seek now reports
`PlayerUiState.Paused` until the first frame at the new position arrives:
`PlayerScreen.kt:108` draws nothing for that state, the glyph turns to ▶
although nobody paused, and `KeepScreenOnWhile` (`PlayerScreen.kt:70`) gives up
its keep-screen-on flag for the duration. Over a byte path where a cold read is
a Telegram round trip, that is long enough to notice.

The mechanism is not new — it has been there since the player was built. What
this work changed is that a viewer can now reach it whenever they like, several
times a minute, instead of once when a set opens. It is not fixed here and no
spinner is added. §1 gives the buffered-range readout to **B**; the indication
belongs to the same piece, and `ProgressStateWithTickInterval` already carries
`bufferedPositionMs` for it.

Worth recording for whoever takes it: the web player has never needed any of
this, because `<video controls>` draws a spinner of its own — the same free
inheritance the opening paragraph of this document blames for the gap it was
written to close.

**Volume and fullscreen.** The hardware keys and the fact that the screen is
already fullscreen cover both. `MuteButtonState` exists and is not used.

## 10. Unresolved questions

- Does a seek across a part boundary actually hold? §6 answers it, on
  hardware. If it does not, the scrubber does not ship and the finding
  becomes work of its own.
- `android-foundation` is ten commits behind `main`, where the web player's
  next-episode autoplay lives. Nothing here depends on it; D will. The gap
  should close before D is designed. Until it does, every
  `web/public/lib/player.js` line cited above is that file as this branch has
  it — the code is identical on `main`, but it has moved down the file.
