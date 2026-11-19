# Zündstoff — the bomb passed until one is left

Status: done (Spieltisch 0.187.0, branch `feat/zuendstoff`)

## Why
Last unbuilt item of the user's first minigame list ("throwing a bomb between players until the last
player"); asked for on 2026-11-14 together with Grenzfall.

## User decisions (2026-11-14)
- To throw, the holder **answers a quick 4-choice question** on the phone; a wrong answer costs a 2 s wait.
- **The thrower picks** who catches.
- The holder at the bang **loses one of 3 lives**; out at zero, last one left wins.

## Design
- `src/zuendstoff.rs` (rules), `zuendstoff_config.rs` (timing, stage types, `QuestionDeck`),
  `zuendstoff_views.rs` (phones, TV), `protocol_zuendstoff.rs` (TV wire types), `zuendstoff_tests.rs`.
- Stages: `Lit { holder, since, step: Answer | Throw }`, `Boom { victim }`, `Final`. Hidden fuse per round,
  uniform 18–40 s. Catch delay 1.2 s, intro 2.5 s, bang 4.5 s.
- Questions: the general pack through `QuestionSupply` (marked asked one at a time in the night's history),
  falling back on the whole pack. The holder's phone shows the question as the choice prompt; the TV shows it big.
- Throw targets: everyone else still in, seat order, as choice keys (colours cycle).
- Away holder: the house throws to a random player after the house grace (`GameCtx::house_at`).
- Final: winner, then the others in reverse order of going out; Dreifelder's `placement_scores`.
- Buzz: `Turn` while holding, `Wrong` at the bang.
- TV: `TvZuendstoff.tsx` + `tv/zuendstoff/zuendstoff.css` — SVG bomb with a wobble and spark, SVG burst.

## Steps
- [x] Rules, views, wire types, factory and catalogue; tests (6) for answering, pause, throw, fuse,
      lives, elimination order, house throw, views and buzz
- [x] TV scene, shelf entry
- [x] Browser walk, 3 phones: question, wrong pause, throw, catch, bang, out, final (`reports/`)
- [x] Shelf poster and backdrop
- [x] Version 0.187.0, changelog (0.186.0 went to Preisfrage's tempo meanwhile)

## Found in the walk
- After a wrong answer the "wait" line stayed for the whole next question; the phone line now reads right
  with the keys live, and the TV says "Falsch!" only during the pause.
- The bang graphic and the TV section shared a class (`zs-boom`), which shrank the whole page; renamed.

## Not done
- No sound yet (a ticking fuse would add a lot); Preisfrage's and Kopfnuss's cue hook could carry it.
- Not tried on real phones or the TV box.
