# Kritzelei, the drawing game, with a native drawing board on the iPad — design

Date: 2026-11-13. Built on a branch cut from `main` and merged back to `main` when done (the user
keeps finished work on `main`). Spieltisch line.

The look: `2026-11-13-kritzelei-look.html` next to this file (open it in a browser). On the iPad
the user picked **layout A, tools along the bottom**. Layout B (the left rail) is in the file only
as the option that was turned down.

## Intent

Game night has no drawing game yet, and it is next on the backlog. The household's drawing iPad
is an **iPad (5th generation)** on iOS 16.7, which the iOS controller app (`ios/controller/`, on
`main` since 0.153.3, targets iOS 16) can install on. The user asked: *"lets build the native
controller and start with ios. lets build here the drawing board"*. When asked whether the board
should be a web page or native, they chose **native**.

Native is the point. A drawing in a web view fights the browser, and every touch sample should
reach the paper. The TV should show the drawing as it grows, so the room can shout guesses while
the line is still moving.

## Who

The household and guests. **The iPad is the drawing board**: it belongs to the table, not to
a player, and it gets handed to whoever is drawing. Everyone else plays on their own phone, in the
app or the browser, the same as in every other game.

## Goals

- A complete party drawing game: one person draws a word on the iPad, the TV shows it live, the
  room guesses **out loud**, and the drawer taps who got it.
- The native board feels like paper: no lag under the finger, and every touch sample is drawn.
  Drawing is by finger only, with no Pencil and no pressure.
- The TV drawing keeps up with the hand closely enough that shouting at the TV works.
- It plays in kids mode, with simpler words.

## Non-goals

- **No Android board yet.** The user said "start with iOS". The Android controller app gets the
  same board later through the same bridge calls (see Approaches).
- No board in a plain browser. Browsers never see the "Als Zeichenbrett verwenden" button.
- No typed guesses and no guess checking on the box. Guessing is out loud.
- No native lobby. The iPad joins through the same phone page as before.
- No drawing on the phones.

## Anti-goals (a failure even if it technically works)

- **The word leaks.** The word must never appear on the TV or on a guesser's phone while someone
  is drawing, and the box sends it only to the board.
- **The TV lags far enough behind the hand that guesses come late.** It should feel live.
- **The phone page becomes different in the app and in the browser** beyond the board itself.
  The 2026-11-07 controller-apps spec had the anti-goal "one set of screens". The board is a
  **deliberate exception**, chosen by the user for drawing control. It is the only native game
  screen, and it lives only on the board device.

## Constraints and facts (from the code, 2026-11-13)

- **Views are snapshots.** The box sends every connection its whole current view, at most every
  50 ms (`TICK`, `crates/mediagram-game/src/room_loop.rs:13`) and after every event, and only when
  the view changed (`room_views.rs` `flush`). Sending a growing drawing inside the TV snapshot would
  make the TV box re-read the whole drawing up to 20 times a second.
- **Roles.** There are two roles today, `Role::Phone | Role::Tv` (`protocol_client.rs`). The TV
  role is accepted only over loopback (`room.rs` `handle_msg`), and phones are players with seat
  tokens (`room_seats.rs`).
- **Phones are thin.** They render generic `PhoneScreen`s (`Wait`, `Choice`, `Outcome`, `Final`,
  …) and know nothing about the game (`protocol.rs`). New games reuse those screens.
- **The game catalogue** (`catalogue.rs`) has a match with no wildcard arm, so a new
  `GameKind::Kritzelei` will not compile until it has an entry (name, min, max). Kids-mode refusals
  live in `room_settings.rs`.
- **The bridge.** The page and the app talk over `window.mediagramApp`
  (`ios/controller/Sources/Spieltisch/GameWebView.swift`), which has `vibrate`, `getMemory` and
  `setMemory`. Messages go page→app through `webkit.messageHandlers.mediagramApp`. There is no
  app→page path yet. The page side is `client/src/phone/app-bridge.ts`.
- **One connection.** The page has a single WebSocket to `/ws` that reconnects by itself
  (`client/src/net.ts`).
- **PencilKit doesn't fit.** `PKCanvasView` hands over a stroke only after the finger lifts, so
  live strokes need a custom touch-drawing view with coalesced touches.
- **The drawing iPad** (read over USB on 2026-11-13): iPad (5th generation), `iPad6,11`, iOS
  16.7.12, the last iOS it gets. The screen is 1024×768 pt with a home button. The pairing is
  valid and xtool lists it. No Apple Pencil works with it, and the screen doesn't sense pressure.
  - The iPad mini 2 that was plugged in first (`iPad4,5`, iOS 12.5.7) can't run the app, which
    targets iOS 16, and doesn't support the Pencil either. It is out.
- **The iPad has no vibration motor.**
- **Signing.** The free Apple ID signs for 7 days, so the app needs a refresh before game night.
- **Nothing has been installed on the iPad yet**, and there is no record of the controller app
  being walked through on a real device.
- **iOS version.** The app's version sits in `ios/controller/Info.plist` (`0.153.3`). CLAUDE.md's
  Versioning section does not list it, and changing CLAUDE.md needs the user's sign-off.
- **The game file pattern** (see Preisfrage): `<game>.rs`, `<game>_config.rs` (all numbers in one
  struct), `<game>_rules.rs`, `<game>_views.rs`, `protocol_<game>.rs`, plus `_tests.rs` beside
  each. TV pages are `client/src/tv/Tv<Game>.tsx`.

## Approaches

1. **The page stays in charge (picked by the user).** The iPad keeps running the box's phone page
   for the lobby, joining, reconnecting and name memory. During a drawing turn, the page tells the
   app over the bridge to show the native board, along with what to show. The board sends strokes
   and taps back over the bridge, and the page passes them to the box on its one connection. Swift
   gets the drawing surface and the board screens, but no networking. The cost is that each stroke
   hops through the page, which adds a few milliseconds (estimated, not measured). Android's board
   later reuses the same page and the same bridge calls.
2. **The board has its own connection.** The app talks to the box directly, with its own joining
   and lobby in Swift. It would be a first step toward a fully native controller. The cost: it
   repeats in Swift what the page already does (seats, tokens, reconnecting, kids rules), leaves two
   lobbies to keep in step, and needs about twice the Swift code, with nothing visible gained.
3. Dropped: **the page for the lobby plus a second socket only for strokes.** That means two
   connections to keep in step for no gain anyone would see. Also dropped: **a web canvas board**,
   because the user chose native.

## How — decided by the user

- The board is **native, in the iOS app**. iOS comes first, Android later.
- **The iPad is the drawing device**, not a player, and it is passed to whoever draws. It is the
  iPad (5th generation) on iOS 16 ("i have another ipad with ios 16").
- **Finger only.** The household has no Pencil ("we have no pencil. just finger"), so lines have
  no pressure.
- **Guessing is out loud, and the drawer taps who got it** on the iPad.
- **Layout A on the iPad.** The tools sit in one row under the paper, with "Niemand" and
  "Erraten!" at the right end of that row.
- **Approach 1**: the page stays in charge, and the board talks through the bridge.
- **The iPad is connected early.** The first step of the build is a throwaway drawing view on
  the real iPad that measures the delay from finger to TV through the bridge, the page and the box.
  It runs before any game rules are built. If the delay misses the target, the approach comes back
  to the user before the build goes on.
- **The iOS app joins the Spieltisch version line.** On every Spieltisch bump,
  `ios/controller/Info.plist` gets the same `CFBundleShortVersionString`, and its
  `CFBundleVersion` is computed from it the way Android's versionCode is
  (`major·1,000,000 + minor·1,000 + patch`). Approving this design approves this exact
  CLAUDE.md change:
  - in § Versioning, under **Spieltisch, the games**, a new bullet:
    `` `ios/controller/Info.plist` — `CFBundleShortVersionString`, the iOS controller app; `CFBundleVersion` is computed from it like Android's versionCode ``
  - in § Changelog:
    `- 2026-11-13: § Versioning names the iOS controller app's Info.plist in the Spieltisch line. It sat at 0.153.3 while Spieltisch moved on to 0.178, because the rule never named it (user's decision).`

## How — proposals played back to the user

I played these back and the user replied "yes, show me the mockup". After seeing the mockup they
picked a layout and an approach without objecting to any of these. They count as accepted
proposals, not as items the user confirmed one by one.

- **The name is Kritzelei.**
- **Turns.** Everyone draws once, in seat order. With fewer than 5 players there are two rounds.
- **The drawer's phone** buzzes "turn" and shows "Du zeichnest – hol dir das iPad". **The other
  phones** show "Rate laut!" with the drawer's name.
- **On the iPad.** A hand-over screen ("Anna ist dran") keeps the word hidden until someone taps
  "Ich bin dran", which shows the word and starts a **90 s** clock.
  - The tools are 5 colours, 3 line widths, an eraser, undo and clear. A finger draws at the
    picked width.
    - *Changed after it was played back:* the proposal said the Pencil draws with pressure. The
      user then said there is no Pencil, only fingers.
  - "Erraten!" opens the list of players, minus the drawer, and the clock pauses while it is open.
    "Doch nicht – weiterzeichnen" goes back to drawing.
  - "Niemand" ends the turn with no points.
- **On the TV.** The drawing appears live, stroke by stroke, along with the drawer's name, the
  clock ring, "Ruft laut, was ihr seht!" and the scores. The word is never shown while drawing.
  When the turn ends, the TV reveals the word for about **6 s**, with who got it, the points and
  the finished drawing.
- **Points.** The guesser gets 2 and the drawer 1. If nobody got it, nobody scores. The game ends
  with the existing awards ceremony.
- **Words.** A new German list of drawable words, with a simpler kid-friendly part used when
  "Nur für Kinder" is on.
- **The guesser's phone.** When the drawer taps a guesser, that phone shows "+2 Richtig!" with the
  word and buzzes.

## How — my calls (the user has not confirmed these)

1. **The board role.**
   - A new `Role::Board`. A board connection is not a player: it has no seat, no score, and doesn't
     count toward the minimum number of players.
   - There is one board per room, and it gets a **board token**, the same way a phone gets a seat
     token.
     - **During a game**, only the holder of that token can take the board back. That is how a
       reloaded or reconnected iPad gets it back, even past a stale connection, and no other device
       can take over in the middle of a turn and see the word.
     - **In the lobby**, any iPad can take the board over.
   - The TV lobby shows whether a board is connected.
2. **How the iPad becomes the board.**
   - **The board is iPad only.** The app offers the board only when it runs on an iPad
     (`UIDevice` idiom `.pad`), which matches the user's "we assign the ipad as drawing device".
     iPhones and browsers never see the button, and there is no phone-sized board layout.
     - The cost: if the iPad's 7-day signing has lapsed on game night, Kritzelei can't start. The
       weekly refresh already covers that.
   - The bridge gains a capability that the page can check, so only the app on an iPad can offer
     "Als Zeichenbrett verwenden" in the lobby.
   - **The iPad becomes the board by itself** (the user's decision on 2026-11-14, "Automatic"):
     the app on an iPad skips the name screen and joins as the board straight away. Nobody taps a
     button for it.
   - The choice is saved in page memory (`setMemory`), so the iPad comes back as the board after
     the app restarts.
   - The board's waiting screen, a web screen, offers "Doch mitspielen" to switch back to playing.
     That choice is remembered too, so an iPad someone plays on stays a player after a restart;
     "Als Zeichenbrett verwenden" in its lobby turns it back into the board.
3. **Starting needs a board.** Kritzelei won't start without one. The lobby gives the reason
   through the existing `blocked` mechanism, for example "Kritzelei braucht das iPad als
   Zeichenbrett." The minimum is **2 players** (one draws, at least one guesses).
4. **What the board shows.**
   - Native, laid over the web view: the hand-over screen, the drawing screen and the
     who-got-it list.
   - Web, rendered by the page: the waiting screens (lobby, between turns, the reveal).
   - The box sends the board its own view (a new `ServerMsg::Board`, or similar). The page turns
     that view into bridge calls to show, update or hide the native board.
   - The word reaches only the board and, at the reveal, everyone.
5. **What a drawing is.**
   - A list of strokes. Each stroke has a colour index, a width index and points, with `x, y`
     normalized to a fixed **16:9 paper**. There is no pressure value.
     - 16:9 is what layout A leaves on this iPad in landscape. The mockup was drawn at iPad mini
       proportions, but with its top bar and tool row on 1024×768 pt the paper gets about
       983×578 pt, and 16:9 fills its width (983×553). 16:9 is also the TV's own shape.
     - The board is locked to landscape and hides the status bar.
   - The eraser draws in the paper colour, so the TV needs no special rendering for it. Undo removes
     the last stroke, and clear empties the drawing.
   - While the finger moves, the board sends the points of the stroke in progress about every
     50 ms.
   - Predicted touches are drawn only on the iPad and are never sent.
6. **How strokes reach the TV.**
   - The box keeps the current turn's drawing. It sends the TV **only what was added** (a new
     message carrying new points, undo and clear), not the whole drawing inside the TV snapshot.
   - A TV or board that connects mid-turn first gets the whole drawing once.
   - The TV draws on a 2D canvas at 16:9.
   - **This departs from the TV mockup**, whose paper is nearly square. On the TV the drawing takes
     most of the width, and the clock, the drawer's name, "Ruft laut, was ihr seht!" and the scores
     fit around it. At 1080p a drawing about 1400 px wide is about 790 px tall.
7. **The clock.**
   - It is 90 s and lives in `kritzelei_config.rs`.
   - It pauses while the who-got-it list is open, and also while the board is disconnected.
   - At 0 the list opens by itself, with "Niemand" in it, so a shout in the last second still
     counts.
8. **Something drops mid-turn.**
   - **The board.** The clock pauses and the TV shows that the board is gone. When the board
     returns, it gets back the turn, the word and the drawing.
   - **The drawer's phone.** The turn goes on, since the drawer is at the iPad anyway.
   - **A player whose phone is off when their turn comes.** They are **not** skipped
     automatically. A phone that went to sleep must not cost someone at the table their turn.
     Instead, the hand-over screen gets a small "Überspringen" button that moves on to the next
     drawer. The mockup doesn't have that button.
9. **Phones reuse existing screens.**
   - `Wait` for the drawer and the guessers.
   - `Outcome` at the reveal: "+2 Richtig!" for the guesser, "+1" for the drawer, and the word for
     everyone.
   - **Buzzing must be asked for explicitly.** The existing `buzz-moments.ts` rules never buzz on a
     `Wait` screen or on a good `Outcome`, and `tests/buzz-moments.test.ts` checks that a good
     outcome stays silent.
     - Kritzelei's screens therefore ask for a buzz themselves: "turn" for the drawer at the start
       of their turn, "accepted" for the guesser who got it.
     - The other games keep their rules unchanged.
10. **The word list.**
    - It is a Rust module of about **300 concrete German nouns**, about 100 of them marked as kid
      words.
    - No word repeats within a game.
    - Kritzelei stays allowed in kids mode and uses only the kid words there.
11. **The native drawing view.**
    - A custom `UIView` that draws with coalesced touches, at the picked width.
    - **One line at a time.** The first touch on the paper draws, and any other touch is ignored
      until it lifts, so a resting hand or a second finger doesn't scribble. There is no Pencil to
      tell a palm from a pen.
    - **The pure parts go in `ControllerCore`** with tests, so they build and test on Linux. Those
      parts are stroke encoding, point batching, and the undo/clear model.
12. **Version.** Kritzelei is a Spieltisch **minor** bump. The iOS app takes the same number
    (decided by the user, above).

## Success

- **A full game on the real iPad.** At least 3 players on phones (including a browser, an
  app and an iPhone), with every turn going hand-over → drawing → who-got-it → reveal, through to
  the ceremony.
- **The TV keeps up.** It shows the line while the finger is still moving. Target: under about
  0.2 s from finger to TV, measured on the real devices.
- **Correct results.** Points are right, the word never shows on the TV or on a guesser's phone
  during drawing, and kids mode deals only kid words.
- **Recovery.** Locking the iPad or killing the app mid-turn and coming back resumes the turn,
  with the drawing intact.
- **Tests pass.**
  - Rust rules tests: turn order and rounds, points, the clock pausing and running out, board
    loss, start blocked without a board, kids words.
  - `ControllerCore` tests for the stroke model.
  - The existing checks (`scripts/check.sh` or equivalent).

## Left to the builder

- The exact wire shapes and names (the board view, the stroke messages, the actions for strokes,
  guessed and nobody), and the bridge call names and how the app calls back into the page.
- How the TV smooths the lines, how points are quantized, and the batch size, within the 50 ms
  rhythm.
- The contents of the word list.
- File splits, following the game file pattern and keeping files under about 200 lines.
- The exact visuals of the native screens, matched to the mockup and the Spieltisch tokens
  (felt `#0f5a43`, card `#fbf8f1`, keycap colours, Bricolage Grotesque and Atkinson Hyperlegible
  fonts bundled in the app).
- Whether the game log records anything per turn, as other games do.
