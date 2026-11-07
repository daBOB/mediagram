# Game night: box walk report

Final walk, 2026-11-04, on the real TV box. Result: pass, with two observations (slow Games start, a brief blank poster card at the first round).

## Device and build

- Skyworth HPR302 "UHD Google TV STB", Android 14, rtd1325 + Mali, armeabi-v7a, at `192.168.0.37:5555`. WebView 158.0.8127.2.
- Build: release-signed, sideloaded with `:app:installRelease` to the box only (not published; no `release-android.sh`). Source HEAD 418a4238, native core rebuilt first. Version `0.143.0` (versionCode 143000; the bump is not part of this walk).
- Dexopt: `arm: [status=speed-profile] [reason=install-dm]`.
- Kids-profile check skipped by the user. Players were two bots from the dev machine (join-only copy of the smoke script; the leader bot configures). The box stayed on the grown-up profile "A" throughout; no settings, profile or playback were touched.
- Screenshots are in `.superpowers/sdd/plan/screens/final-*.png` (git-ignored); logcat in `.superpowers/sdd/plan/final-box-logcat.txt`.

## Steps

| # | Step | Result |
|---|---|---|
| 1 | Games start time | Pass, but slow: about 12 s. |
| 2 | Lobby: QR, address, "Spieleabend", start button, 2 bots, leader star | Pass |
| 3 | Remote OK starts the game | Pass |
| 4 | Gemischt quiz | Pass |
| 5 | Poster-Raten | Pass, one note |
| 6 | Nur für Kinder on, one poster game | Pass |
| 7 | Back mid-game, Back in lobby, old port | Pass |
| 8 | Quick re-enter | Pass |
| 9 | Background (HOME) | Pass |
| 10 | Box left on the library | Done |

1. **Start time.** OK on *Games* shows "Starting game night..." (`final-rail-games.png`, `t/00`) and a black frame, then the lobby. The host's port answers 11.4 s and 11.6 s after OK in two timed runs; with the lobby painted it was about 14 s in the first run (screencap polling at ~1 s intervals) and about 12 s later. The screen is a static message the whole time, no stutter or half-drawn UI. The previous walk saw about 10 s. Not a defect, but it is the longest wait in the feature. `final-lobby.png`.
2. **Lobby.** QR, `192.168.0.37:8080`, "SPIELEABEND", "Buzzer-Quiz · Allgemein" badge, the start button ("Wartet auf 1 Spieler" with no one seated, "Los geht's" once two joined) and "Ana ★" as leader. `final-lobby-joined.png`.
3. **Start.** DPAD_CENTER on the focused button started the quiz at once.
4. **Quiz (Allgemein, 8 questions, full game).** Intro "Gleich geht's los ... 4" (`final-quiz-intro.png`), open question with the timer bar (`final-quiz-open.png`), reveal with the right answer lit and the players' picks (`final-quiz-reveal.png`), final ranking "Ana gewinnt" 1766 / 1716 (`final-quiz-final.png`). The bottom players strip ("Ana 0  Ben 0") is fully visible on every question screen, with margin below it: the earlier cut-off is fixed. No still came up (general questions only; a Filme quiz was not played this round). The final screen is stable; the avatars looked dim only because the bots had exited after the final.
5. **Poster game.** First step of round 1: the card is empty in the first ~0.4 s of the round, then the 5x8 mosaic shows (`final-poster-first-blank-frame.png` then `final-poster-first-step.png`; confirmed with a 5 fps screen recording, `final-poster-start.mp4`). It is never a previous poster. Round 2's first step shows its own mosaic immediately (`t/10`). A later step (Schärfe 3 von 8, `final-poster-later-step.png`) and the reveal with the title ("Niemand hat's erkannt", `final-poster-reveal.png`) are fine. Three screenshots about 2 s apart in round 6 show Schärfe 1, 2, 3 with the points 1000, 929, 857 (`final-poster-smooth-1..3.png`): the sharpening advances one step per 2 s, as designed. Full game of 10 rounds played to "Ana gewinnt" 1572 / 786 (`final-poster-final.png`).
6. **Nur für Kinder on** (bot `configure` with `kids_only: true`; badge "Nur für Kinder" in the lobby, `final-kids-lobby.png`). Revealed titles over a full 10-round poster game: The Family Plan 2, Spider-Man: Across the Spider-Verse, Stargate Atlantis, Finch, KPop Demon Hunters, Der Grinch, Arrival, The Love That Remains, Snow White and the Huntsman, Teenage Mutant Hero Turtles. A second game that was started and aborted showed Die Royal Tenenbaums. The grown-up profile's limit is 12; I did not check each title's rating against the library, so "no FSK 16/18" is read from the titles, not proven.
7. **Back.** Mid-game BACK showed the lobby with the port still answering (`final-midgame.png`, `final-back1-lobby.png`); BACK in the lobby showed the library and `curl` of the old port gave connection refused (`final-back2-library.png`).
8. **Quick re-enter.** OK, BACK after 1 s, OK again after 0.8 s: the port stayed refused during the second start, answered 11.6 s later, and the lobby showed a working QR and address (`final-quick-reenter-settled.png`). A screenshot taken at the moment the page appeared shows the QR card still empty and the fallback font for a moment (`final-quick-reenter.png`); it filled in within 2 s. The old URL did not flash or answer.
9. **Background.** With the lobby open, HOME: the port answered at 0.01 s and was refused by 0.83 s after the key was sent (that includes adb's own key latency of a few hundred ms), so the night stops at once, not on return. Reopening the app shows the library, not Games (`final-reopen-after-home.png`).
10. The box is left on the app's library, rail on its default focus (`final-end-library.png`). No bots, `adb forward`/`reverse`, or recording file remain.

## Logcat

`final-box-logcat.txt`: no crash, no `AndroidRuntime` entry from the app (only my own `monkey` launches), no chromium errors, empty crash buffer. Jank seen: one `Davey! duration=732ms` at 23:38:22 and "Skipped 31 frames" at 23:39:56 when the app was brought back to the foreground after HOME; neither falls inside a game screen.

## Surprising or worth knowing

- Seats of bots that disconnected (killed between games) stay in the lobby strip, dimmed, next to the new ones (`final-kids-lobby.png`: four avatars, "2 am Tisch"). The lobby text counted 2 players and the start worked; it is only visible clutter when a phone drops and rejoins under a new name.
- A bot pair joining at the same moment can seat either one as leader; the walk script therefore lets whichever bot is leader configure.
- The first walk's note that leader changed after Back is explained the same way (old sockets of an earlier pair).

## First walk (0.143.0 at 929aedfb)

The first walk found the TV page blank on the box: the lobby, QR code, quiz and poster were never visible, only the solid green background, although the host, the WebSocket and the remote OK all worked. Root cause (proven with a debuggable build, DevTools and a layout probe): Compose's `AndroidView` added the `WebView` with `WRAP_CONTENT` layout params, so Android WebView laid the page out with a zero-height layout viewport; `html`, `body` and `#root` (`height: 100%`) became 0 px high, `vh` units were 0, and the `overflow: hidden` page clipped everything. Desktop Chrome has no such behaviour, which is why the page looked fine there. Fix, commit 19cff774: the WebView gets `MATCH_PARENT` layout params in `TvGamesFrame.kt`, with a Robolectric test pinning the layout params. That walk could only confirm Back, background and remote OK; everything else is covered by the walk above.
