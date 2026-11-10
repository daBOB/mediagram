# Brainstorm: Dreifelder + price-guess mini games

Date 2026-11-09 | Worktree mediagram-kopfnuss (feat/kopfnuss, main 411a7a53, 0.163.0)
Original genre-equivalents of two TV formats: no show names, art or look.

## Decision: two sub-projects, own plan each
1. **Dreifelder** first. 2. **Preisfrage** (working name) after, once API access is confirmed.
Reason: Dreifelder reuses existing screens and data; Preisfrage needs a new phone screen and an external data source.

## 1. Dreifelder (agreed)
- 2-8 players, German UI. TV: question + 3 fields (1/2/3), one answer each. Phone: 3 buttons, 12 s.
- Wrong pick or no pick = out. Everyone left wrong = nobody out, new question.
- Out players keep guessing, no points. Place = round dropped; tie = more right guesses.
- Ends at 1 player left. Cap 12 questions: fastest total correct answers wins. Then the Kopfnuss ceremony.
- Questions: `questions/allgemein.json` + film pool; correct + 2 of 3 wrong answers (shared `Round::shuffled`). No new data file.
- Approach A: new `DreifelderGame` (like Kopfnuss), one `Quiz` per round. Rejected B (elimination mode inside `Quiz`): puts one game's rules in the shared buzzer quiz.
- Touchpoints: new `protocol_dreifelder.rs`, `dreifelder*.rs` + tests, `TvDreifelder.tsx`; edit `GameKind` (protocol_settings.rs), `room_settings.rs` (min 2), `factory.rs`, `lib.rs`, `TvScene` variant, `ui/lobby.ts`, `PhoneLobby.tsx`, Android `TvGamesShelf.kt`, changelog, version.
- Out of scope: sound (follow-up as with Kopfnuss), price game.
- Acceptance: tests-first per rule above (all-wrong round, cap, tie order, ghost guesses score 0, leaver mid-game); live run on the box with 3 bots + the tablet.

## 2. Preisfrage (direction only; not designed yet)
- Show a random Amazon item without its price; group enters a guess on the phone; closest wins (rule "without going over" still to decide).
- Phone input: **number keypad**, new `PhoneScreen` variant in shared protocol.
- Data: **live from the Amazon Creators API** (user choice). Needs: Associates account, API credentials, internet on the box, keyword/category list to draw "random" from.
- Research: PA-API 5.0 retired spring 2026, replaced by Creators API (OAuth 2.0 client credentials). Sources disagree on dates and on who can still get access.
- No scraping (terms of service).
- Credentials stay out of the repo (box settings file, never committed).
- Fallback if the API is unavailable: saved list of ~50 hand-picked items with check date (offered, not chosen).

## Unresolved questions
- Does the user have an Associates account with Creators API access? Blocks Preisfrage design.
- Do Amazon's terms allow showing item name/image/price in a game and holding a price for the length of a round?
- How "random": keyword list, categories, or best-seller lists? Item image on the TV: allowed to display?
- Rule for the winner: closest overall, or closest without going over?
- Preisfrage scoring over several items; rounds per game.
