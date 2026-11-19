# Kritzelei browser walk

Server: `GAME_PORT=8093 cargo run -p mediagram-game --bin game-night-dev` after `bun run build`. Seats: TV `localhost:8093/tv`, Ada `127.0.0.1`, Bo `192.168.0.118`, Cy `localhost`. Two players (Ada, Bo) plus Cy as the board, so two rounds = four turns. TV 1280x720, phones 390x800. Screenshots: `web-walk/`.

## How the fake bridge was installed
`Page.addScriptToEvaluateOnNewDocument` is not on browse's CDP allowlist, so nothing could run before boot. Instead, after Cy had joined as a normal phone, `$B js` installed the brief's `mediagramApp` and a wrapper on `WebSocket.prototype.send` that stores `this` in `window.__ws` on the next ping (at most 5 s). The page reads `board` at render, so "Als Zeichenbrett verwenden" appeared with no reload. The brief's step 3 order (bridge first) is therefore reversed; behaviour was the same. No reload of Cy's tab ever happened.

## Steps
| Step | Expected | Seen | Shots |
|---|---|---|---|
| 3.2 Ada picks Kritzelei | TV "Zeichenbrett fehlt", start blocked "Kritzelei braucht das iPad als Zeichenbrett." | as expected; Ada's phone shows the same block text | 02-* |
| 3.3 Cy taps „Als Zeichenbrett verwenden“ | "Zeichenbrett bereit", start unblocks | as expected; Cy shows "Zeichenbrett / Doch mitspielen"; start button enabled | 03-* |
| 4 start | Ada draws; handover | TV "Ada ist dran / zeichnet gleich", Ada "Du zeichnest – hol dir das iPad", Bo "Rate laut!" | 04a-* |
| 4.1 `ready` | clock runs | TV "Ruft laut, was ihr seht!", countdown | 04b-* |
| 4.2 line + 2 more | strokes on TV | a ring in red appears on the TV paper, none on phones | 04c-* |
| 4.3 `guessed` | picking | TV "Wer hat's erraten?", clock stopped | 04d-* |
| 4.4 `pick` Bo | Bo "Richtig!" +2, turn 2 after 6 s | TV reveal "Ring", "Bo +2 · Ada +1"; Bo "Richtig! +2 Ring Stand: 2 Punkte"; after ~6 s "Bo ist dran" on a blank paper (board got handover resync, 0 ops) | 04e-*, 04f-* |
| 5 TV reload mid-turn | whole drawing back | two strokes (blue line, green double stroke) identical after reload | 05a, 05b |
| 5 Cy socket dropped | last `__shown` has `resync:true` and all ops | `{kind:"drawing",turn:2,resync:true,ops:4}` | 05c-* |
| 6 play to the end | ceremony | turn 3 with `keep_drawing` and "Niemand" (0 points), turn 4; podium "Ada gewinnt!" Ada 5, Bo 4; phones show 1./2. place; Cy "Spiel vorbei" and board hidden | 06-*, 07-* |

## Word leak check
For every drawing and picking screen (turns 2, 3, 4; turn 1 checked by text) the word was searched for in the full `outerHTML` of the TV and of Ada, Bo and Cy. It was never found before the reveal (words: Ring, Mumie, Tannenzapfen, Sessel). It appears only in the board's `__shown` entries and, after the pick, on the TV and phone reveal screens.

## TV paper
Paper on the 1280x720 TV: 704x396 px, 16:9, strokes undistorted (the ring is round-cornered symmetric), left edge at 23.5 % and width 55 % of the screen. The look spec has left 24, width 52 cqw, so it is within a few percent. Roughly centred between the player and the score list. Fine.

## Findings
1. **Medium, fixed.** TV lobby: the "Zeichenbrett bereit/fehlt" note (and the "Filme nicht erreichbar" note) was drawn on top of the last seat's name from 3 players up (screenshots 02-tv-no-board, 03-tv-board-ready). The seat list shrank below its content inside the 55vh panel. Fix: with a note line the table goes two-seats-per-row from 3 players (`isCrowded`). After: 08-tv-lobby-after-fix-3-seats (3 seats, was overlapping), -4-seats (4 seats; a first attempt that only shrank the code still overlapped, so the threshold approach was used). Test `table-panel-crowding.test.ts` (RED = missing export, GREEN after). The look was confirmed in the browser only; there is no DOM test harness.
2. **Low, not fixed.** After a restart of the box mid-lobby the first-seat Ada lost host to the player who reconnected first (Bo, then Di). That is the box's host rule, not Kritzelei; noted only.
3. Not covered here: app-kill recovery, real iPad rendering (phase 06 Task 4). Elapsed clock check: reconnect resync carries `left_ms` stopped/running as the box sends; not inspected in detail.
