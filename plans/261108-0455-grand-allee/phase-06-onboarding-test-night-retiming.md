# Phase 06 — Onboarding intro, test night, re-timing

**Goal:** a newcomer understands their turn from a short TV intro; one evening
with real people measures how long decisions take; the simulator is re-timed
from those measurements. Design: spieltisch `design/grand-allee-design.md`
§ Onboarding, § Pacing, § Acceptance criteria 3–5, Open question 1.

## Found (0.170.1)

- No intro: `GrandAlleeGame::new` asks seat 0 to roll at once.
- Phone options already name their effect and price ("Lagune kaufen · 1 200 Taler",
  `live_views.rs` `label`).
- No decision times survive a game: the host's `tracing` lines reach no
  subscriber on the box, so nothing lands in logcat or a file.
- The simulator charges 6 s for every question, field picks and "Lieber nicht"
  included (`play.rs` `Think`).

## Decisions (user, 2026-11-09, all recommendations accepted)

1. Three parts: **6a** before the night (intro, decision log, wording fixes,
   this sheet; one reviewed commit each with a version bump; new APK on the
   box), **6b** the test night (the user's), **6c** re-timing from the log.
2. Intro = a game stage before the first roll: three cards × 10 s over the
   board, no camera choreography. The leader's phone shows "Überspringen";
   the other phones show the card title and "Schau auf den Fernseher". Every
   game start. Simulator and tests skip it. The leader is the earliest-joined
   connected player, the room's own rule (`room_seats.rs` `leader`).
3. Card texts (round count from the game), rendered in the TV look and
   approved as images before they are wired in:
   - **Das Ziel:** Nach 35 Runden gewinnt, wer am meisten besitzt: Geld,
     Grundstücke und gebaute Hotels zählen. Wer eine Rechnung nicht mehr
     bezahlen kann, ist raus.
   - **Dein Zug:** Würfle auf dem Handy und lauf. Freies Grundstück? Kaufen.
     Baustelle? Bauen – der Genehmigungswürfel entscheidet. Am Rathaus vorbei:
     Eingänge kaufen. Eine 6 darf nochmal.
   - **Das Geld:** Landest du auf einem fremden Eingang, zahlst du: Nächte ×
     Preis des Hotels. Über die Bank: 2 000 Taler. Fehlt dir Geld,
     versteigerst du Besitz an die anderen.
4. Decision log: one JSON line per decision (seat, no names; question kind;
   think time; who answered: player, clock or house) and one per game
   (players, minutes, rounds, how it ended). Written to the box's data
   folder, served at `GET /grand-allee-log` on the game-night server
   (port 8080). Waits per seat are derived from the lines in 6c.
5. Wording before the night: the TV narrates in the third person ("Ben
   fehlen 800 Taler. Was versteigert Ben?"), the mover's own phone says
   "du", "Kein Gebot" shows once on the TV.
6. Absent bidders in a debt auction get the table's 1.5 s grace
   (`GameCtx::house_at`) instead of passing at once — the one behaviour
   change in 6a.
7. The test-night sheet below.
8. Re-timing rule (6c): the measured median think time per question kind
   replaces `Think`'s three flat values; if the 8-player longest wait (p95)
   still exceeds 2 min, the levers come to the user with numbers, in the
   design's order — shorter clocks, auto-rolling, teams. Nothing changes
   without the user's go.

## 6a — steps

| # | Step | Status |
|---|---|---|
| 1 | Wording fixes + auction grace | done (0.170.2) |
| 2 | Decision log + `/grand-allee-log` | open |
| 3 | Intro cards: render for approval, then the stage, TV and phones | approved as rendered (`reports/intro-cards/`), building |
| 4 | APK on the box | open |

## 6b — test-night sheet

**Before:** the box runs the 6a build; every phone on the box's Wi-Fi. Play
at least one game with as many people as you can get (ideally 8 — the 2-min
wait failed only at 8 in the simulation) and one with about 4.

**Watch, game by game:**

1. *Intro (criterion 5):* after the intro, can each newcomer start their first
   turn without help? Note every question asked in the first two rounds.
2. *Waiting (criterion 4):* at 6–8 players, who drifts off between turns, and
   when? The log measures the waits; note how it felt.
3. *Runaway (criterion 3):* at half time (the TV's "Runde x von y") note the
   leader; at the end, did the result feel fair?
4. *Forced purchase (decision 12):* how often, and how did the seller take it?
5. *Debt auctions:* does everyone follow what is being sold, do bids come in
   before the clock?
6. Start and end time of each game (the log has them too).

**Ask afterwards:**

1. What was unclear on your first turn?
2. When did you wait too long?
3. Would you play it again — and what would you change?

**After:** tell Claude; it fetches `http://192.168.0.37:8080/grand-allee-log`
while the game night is still open on the box.

## 6c — re-timing

Fetch the log; per question kind the median and p90 think time; feed the
medians into the simulator; re-run 1 000 games per player count; report the
acceptance table against the design's targets and, if criterion 4 still fails
at 8, the levers with numbers. Update the design's Simulation section and
Open question 1 with what the night showed.

## Acceptance (6a)

`cargo test -p mediagram-game`, clippy, `bun run typecheck`, `bun test` green;
the simulator's tables unchanged by steps 1 and 3 (the intro is skipped
there); files under 200 lines; the intro observed on the box.
