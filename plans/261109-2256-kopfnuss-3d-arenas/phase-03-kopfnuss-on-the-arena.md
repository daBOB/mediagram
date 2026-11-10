# Phase 3 — Kopfnuss on the arena

**Status:** pending.

## Goal

`TvKopfnuss.tsx` mounts the arena once for the whole game and turns each `KopfnussStage` into the arena's view model;
the HTML on top keeps today's texts, restyled for the dark studio (navy instead of felt for dimmed answers).

## Per stage

- **Pick / Resolve**: today's `Vote` card on top; signs on the podiums; `done` lights pads; at Resolve the chosen
  sign rises, chips stack by `votes`, each `TrickHit` flies as an arc from `from` to `to` (staggered as today's list,
  450 ms), the target plays `Headache_Relief`, a bounced trick returns and its sender plays `Falling_Down`.
- **Question**: `TvQuiz`'s card and answers on top, without its seat strip (the arena's name tags replace it); trick
  badges from `stage.tricks` over the heads. Reveal: pads in the picked colour, poses by right/wrong, points.
- **Climb**: today's `KopfnussClimb` 2D trail replaced by the tower; `climbers[].step` of `steps` places them.
- **Fact**: winner on the pedestal (`Happy_Jump` now and then), the others dimmed; today's fact text on top.
- **Final**: unmount the arena, the shared ceremony as today.

Sounds (`kopfnuss-sound*.ts`) unchanged.

## Todo

- [ ] stage → view model mapping (pure, tested)
- [ ] TvQuiz without the seat strip (an option, not a copy)
- [ ] studio styling of the HTML on top
- [ ] dev host run with bots (`tools/smoke.mjs --game kopfnuss`), screenshots of every stage vs the mockups
