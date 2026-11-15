# Phase 03 — Kids mode stops asking the general pack (Spieltisch, behaviour change)

## Problem

The general question pack (`crates/mediagram-game/questions/allgemein.json`,
384 entries: category, text, correct, wrong, source) has no kids marking.
Kids mode blocks Kopfnuss and Dreifelder for that reason
(`room_settings.rs:27-30,110-112`), yet in kids mode:

- Zündstoff draws its deck from the general pack (`factory.rs:95-104`) and has
  no kids arm in `blocked()`.
- The Quiz in General mode draws only general questions (`quiz_mix.rs:39,45`).
- Mixed mode fills about half from the general pack; Films mode falls back to
  it when the library is too small (`quiz_mix.rs:43-45`). Only film questions
  are kids-filtered (`pool.eligible(kids_only)`).

No plan, learning or commit says this was intended.

## Decided (user, option a)

- In kids mode, Zündstoff and the General quiz are blocked with a lobby
  message, like Kopfnuss's ("… gibt es nicht im Kindermodus.").
- Mixed and Films quizzes in kids mode draw only kids-eligible film questions,
  never the general pack. When there are too few for the chosen length, the
  lobby says so instead of starting.
- Marking kid-safe general questions is later content work, not this phase.

## Steps

- [ ] `blocked()` arms for Zündstoff and Quiz-General in kids mode.
- [ ] Quiz drawing in kids mode: no general fill; readiness/lobby message when
      kids film questions are too few.
- [ ] Tests: each blocked combination; kids Mixed/Films never contain a general
      question; adult modes unchanged.
- [ ] Changelog entry (Spieltisch).
- [ ] Spieltisch patch bump; commit on the branch; merge to main; check.sh.

## Done when

No kids-mode game can put a general-pack question on screen.
