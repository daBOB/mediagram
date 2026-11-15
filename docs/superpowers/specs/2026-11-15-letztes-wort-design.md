# Letztes Wort — design

A Spieltisch minigame. Each round is one question with many correct answers and a hidden clock. Players buzz and
say an answer out loud; a host judges it. A correct answer takes the "winning stick"; whoever holds it when the
hidden time runs out wins the round.

## Why

On 2026-11-15 the user said Zündstoff had been built wrong: Zündstoff is a 3D throwing game (its own design doc,
`2026-11-15-zuendstoff-3d-design.md`), and the "quiz under a hidden clock" idea is a separate game. This is that game.
A first version answered by tapping tiles; the user replaced that the same day with a buzzer and a human host.

## Rules (the user's)

- A game has several rounds. **Each round is one question** with **5–10 correct answers**.
- A **hidden timer** runs. No clock is shown anywhere.
- Players answer with a **buzzer on the phone, with a sound**, and say the answer out loud.
- A **host** judges the answers and keeps track of which correct answers have been given.
  The **host rotates every round**: that round they judge and don't play.
- The **first player to give a correct answer holds the winning stick**. When **another player** gives a correct
  answer, they take the stick. When the time is up, **the holder wins the round**.
- **A wrong answer puts the player out** for this round (back in for the next question).
- **The TV never shows the answers**, during the round or after it. It shows the question and who holds the stick.

## Calls I made (the user can overrule any)

- **First buzz wins the floor**: it locks every other buzzer; the TV and all phones show who buzzed. The buzzer sound
  plays on the buzzing phone (and the TV plays it too, so the room hears it).
- **The host's phone** shows the question and its list of correct answers. While someone has the floor, the host taps
  the answer they said (it counts and greys out), **Falsch**, or **Auch richtig** (correct but not on the list).
  An answer already given is wrong; the host sees it greyed and taps Falsch.
- After the verdict the buzzers open again for everyone still in, **except the stick holder** (the rule says
  *another* player takes the stick; a locked holder also can't drain the answers).
- **The hidden clock keeps running** while the host decides. A buzz that came in before time ran out is still
  judged; if it is right, that player takes the stick and wins the round.
- **A round ends early** when no one else can take the stick: every correct answer on the list is given, or everyone
  but the holder is out. The holder wins. If time runs out with nobody holding the stick, nobody wins the round.
- **Host order**: seat order, starting with a random player. **Length**: laps around the table, each player hosting once
  per lap — 2 laps; the lobby's Dauer gives 1 / 2 / 3 (`GameLength::scale(2)`). 5 players on Normal = 10 questions.
- **At least 3 players** (a host and two who can take the stick from each other).
- **Scoring**: 1 point per round won; most points wins; ties share a place.
- **Hidden time**: drawn per round between 15 and 35 s, counted from when the buzzers first open. The lobby's Tempo
  stretches it (Entspannt ×2, Speedrun ×½).
- **Away players**: an away player's buzz is never taken. An **away host** hands the round to the next player in seat
  order who is present, and the round starts over with a new question.
- **Question pack**: German, about 60 questions, a new file `questions/letztes-wort.json`, each entry
  `{"q": "...", "answers": [...]}` with 5–10 answers. Closed sets the host can check at a glance (planets, the
  Beatles, Bundesländer at the sea), never open-ended ones.
- **Name**: Letztes Wort (whoever gives the last correct answer wins). The user didn't object.
- Mockup: `plans/261115-1559-letztes-wort/reports/mockup-buzzer-and-host-screens.jpg`.

## Approaches

- **Own game (picked)**: a new `GameKind` with its own host module, question file, phone screens (buzzer, host desk)
  and TV screen, the way Grenzfall and Spruchreif are their own games.
- **A mode of the Buzzer-Quiz (rejected)**: the Quiz scores four colour keys automatically; a human judge, a stick
  holder and players dropping out would bend it out of shape.

## What gets built

- Host (`crates/mediagram-game/src/`): the game module (stage machine: round intro → open → someone has the floor →
  verdict → … → round won → … → final), its config with `at(speed)` and `sized(length)`, views, protocol types,
  tests. The new kind goes into `catalogue.rs` (`timed()` and `sized()` arms, minimum 3 players), `factory.rs`
  (`Config::default().at(speed).sized(length)`), `game_speed_tests.rs` `clocks()` and `game_length_tests.rs`
  `counts()`.
- Phone: the **buzzer** (one big key, a buzzer sound when pressed — the phones have no sound yet, so this is new;
  locked when someone else has the floor, when holding the stick, or when out) and the **host desk** (question, the
  answer list with given ones greyed, who has the floor, Falsch / Auch richtig).
- TV: the question large, who has the floor, the stick and its holder, who is out, the score, the host of the round;
  a "Zeit!" moment at the round's end with the winner. The buzzer sound on the TV too.
- Shelf: listed in the catalogue; poster and backdrop afterwards, as with the other games.

## Not in scope

No tiles, no typed answers, no answer reveal on the TV, no visible clock, no English questions, no points for single
answers, no automatic judging.

## Left to the builder

TV and phone look (mockups first: the user decides looks from rendered pictures), the buzzer sound itself, the
round-end pause lengths, the exact question list.
