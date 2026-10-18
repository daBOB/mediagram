# Preview walk: casting from the web player (task 5.5)

Walked 2026-10-18 in the preview harness (`PREVIEW_CAST=accept|busy|refuse|none`, port 8795), gstack browse, profile "test", film "Spider-Man: Brand New Day", desktop 1280x800 and phone 390x844. Screenshots: `preview-cast-<mode>-<state>.png` in this folder. The preview was stopped after each mode and at the end. Real player never started.

## Per mode and state

| Mode / state | Result | Seen |
|---|---|---|
| title page, Play on TV pill | pass | Same pill as My List (same fill, height, type). Order Play, Play on TV, My List, ... (`accept-title-desktop`) |
| accept: Asking | pass | "Asking the TV..." card, centred (`accept-asking`) |
| accept: remote opens | pass | Backdrop dimmed, title top-left, "PLAYING ON TV" then X, control card at the bottom (`accept-remote-desktop`). Card matches the player's: same surface, width (200-1080), rows, icons |
| accept: seek bar / pause / +10 | pass | Starts 0:00 paused. Play: 0:01, 0:04 (moves each second). +10: 0:05 -> 0:15. Pause stops it (`accept-remote-playing`, `accept-remote-after-plus10`) |
| accept: CC menu | pass | Off / English / Deutsch, anchored over the CC button (`accept-remote-cc-menu`) |
| accept: Audio menu | pass | English 5.1 / Deutsch (`accept-remote-audio-menu`) |
| accept: Esc | pass | First Esc closes an open menu, second closes the remote and the bar appears |
| accept: X | pass | Closes, bar appears |
| accept: bar | pass with defect 2 | "Playing on TV . title" with play/pause, fixed at the bottom, width of `main` (x 224, w 1056), play/pause toggles the TV (`accept-bar-desktop`, `accept-bar-playing`) |
| accept: tap bar | pass | Remote reopens at the live position (`accept-remote-reopened`) |
| accept: Stop | pass | Remote closes, bar hidden, session gone |
| accept: Play on TV inside the player | pass | Player closes, remote opens (`accept-remote-from-player`); button sits after Watch together, same type |
| accept: bar vs page bottom | pass | `main` keeps 96px bottom padding; at scroll end the last content (y 613) is 100px above the bar (y 716) (`accept-bar-scrolled-bottom`). A "Join" label replaces "Play" on the title page while a cast is on (deliberate, commit 59460829) |
| busy: question | pass | "The TV is playing Dune (2021). Replace it?" with Cancel, Replace (`busy-question`). Cancel: no session, no bar. Replace: remote opens (`busy-after-replace-remote`) |
| refuse | pass | "The TV won't play this: it's rated above Mia's age limit", OK (`refuse-reason`). OK and Esc close it, no bar |
| none | pass | "Asking the TV..." for about 5 s (still shown at 3.2 s), then "Mediagram isn't open on the TV. Open it there and try again." with OK (`none-asking`, `none-no-answer`); no bar |
| phone: remote | partial, defect 1 | Controls fine, menus fine (`accept-remote-phone`, `accept-remote-cc-menu-phone`) |
| phone: bar, ask | pass | Bar clears the page's right/left gutter (16px), title ellipsised, no horizontal scroll (`accept-bar-phone`, `none-no-answer-phone`) |

## Defects

1. **Remote's X lands under the label on a phone.** Seen at 390x844: title, "PLAYING ON TV", then the X alone at the left on a third line (`accept-remote-phone`). The real player pins X to the top right (`player-card.css:151`, `#close { position: absolute; top: 16px; right: 16px }`, with the phone `.hud-top` getting `padding-right: 72px`). That rule is keyed to `#close`, so `#cast-remote-close` misses it. Owner: `web/public/styles/player-card.css:149-151` (add `#cast-remote-close`) or `together.css`.
2. **The bar is low contrast on the light theme.** It is the control card's surface (`--stage` at 45% plus blur), which over the cream page is a mid grey; the white title is about 3.5:1, and the poster and page text show through (`accept-bar-desktop`, `accept-bar-phone`). It is faithful to the card, but the card was designed over video. Owner: `web/public/styles/together.css:121-124` (`.cast-bar-card`, e.g. a more opaque background).
3. **The Asking/Replace/OK dialog keeps focus on itself and draws a focus ring round the whole dialog**, even once Cancel/Replace/OK exist (`document.activeElement` stays `cast-ask`; seen in `busy-question`, `refuse-reason`). The Report panel moves focus to its first control instead (`accept-report-panel-for-comparison`). A keyboard user presses Esc to dismiss but Enter does not reach OK/Replace. Owner: `web/public/lib/cast/cast-ask.js` (focus the first action button once shown) and the dialog's `:focus` outline in `together.css:85-99`.
4. **Minor: the control card differs from the player's in small ways.** The CC button has no caret (the player's has one); the remote has no "ends hh:mm"; steps are 10 s (the player's 15 s, spec says 10). Likely intended; listed so someone confirms.
5. **Observed once per fresh page, not diagnosed: the first Play on TV click after a cold load did nothing** (busy and refuse modes). The preview's cold load takes about 8-10 s (`/api/sets` 8 s, 847 KB); the button is visible earlier. After an extra 12 s wait the first click worked; the second click always worked. Probably the handler is wired late or the click was eaten by the load; not reproduced on demand. Owner guess: `web/public/lib/cast/play-on-tv.js` wiring vs `app.js` boot order.

## Not checked

- Greyed-out Play on TV with reasons ("Telegram isn't reachable", "Leave watch together first"): no way to force either state in the preview.
- The bar clearing the daily-limit notice: could not trigger the notice. CSS puts it at `bottom: 96px`, equal to the bar's height (about 84) plus its 12 px gutter, so it should sit just above; not seen.
- Bar visible on other devices of the household, the TV-side label, and a kid profile (only the "test" profile was used; the refuse note named Mia from the preview's script).
- Choosing a track in the CC/Audio menus (only the lists were checked), speed menu, and the Next-episode button (film, not an episode).
- The preview signs back to "Who's watching?" after a few idle minutes (profile lock), and needs the test PIN; unrelated to casting.
- Tablet/TV surfaces and a real TV.
