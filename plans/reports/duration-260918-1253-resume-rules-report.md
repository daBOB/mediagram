# "Can we fix the duration?"

`./scripts/check.sh`: clippy clean, **33 Rust tests** (26 + 7 new), **541 bun
tests** (was 534), 0 fail.

## The premise did not hold

Every set in the library already has a duration:

```
ep   34 | movie 1 | tut 162     null: 0     zero: 0
```

So the gap I flagged last session — a set the index never measured — has no
instances. Which made the interesting question "what would produce one", and
then, having gone looking, "what is actually broken here".

## What was actually broken, and it was mine

`isFinished` read `at >= 95% || runtime - at <= 60s`. An `or` between a share
and an absolute takes whichever is **more generous**, so the minute dominated
everything short:

| Runtime | Titles | Old tail | Effect |
|---|---|---|---|
| under 1m | 3 | the whole title | finished before it started |
| 1–5m | 84 | up to 94% of it | finished seconds in |
| 5–20m | 68 | 5–20% of it | finished minutes early |
| over 20m | 42 | the last minute | correct |

155 of 197 titles affected. The consequence was not cosmetic: a "finished"
title has its position **deleted** on every save and never reaches the
Continue shelf, and nothing about it looks wrong.

The tail is now the smaller of the two. The test comment that said "a long
title is finished by the last minute, not by a percentage" had described the
right rule since the day it was written; the code underneath it did the
opposite.

`resumeAt`'s other end had the same shape — a flat half minute is a glance at
a film and half of a 64-second lesson — and now scales the same way.

## The second bug, found by probing

`runtimeOf` fell back to `video.duration` whenever the catalog had none. For a
conversion that is the length encoded **so far**, which sits a few seconds
ahead of the playhead for the whole film: read as finished, position deleted
on every save. Now `trustedRuntime` in `resume-point.js`, which trusts the
browser only when it holds the whole file, and answers 0 otherwise — a number
`isFinished` and `watchedFraction` both refuse to draw a conclusion from.

Latent rather than live, since no set is missing a duration today.

## The change that may not be worth keeping

`inspect` asked only `format.duration`. It now tries a video stream's own
duration, then an audio stream's, then `nb_frames / r_frame_rate`.

It fires on nothing I could build. MKV, MPEG-TS, fragmented MP4 and WebM all
carry a format duration; a raw H.264 elementary stream carries no duration
anywhere, not in the streams and not as a frame count, so the chain does not
save it either. It is 40 lines and 7 tests of insurance against containers
this library does not hold. **Say the word and I will revert it** — YAGNI is
the house rule and this is exactly the kind of thing it is aimed at.

## Verified

A 30-second title at 20 seconds was answering `DELETE /progress` before and
answers `PUT` after, and the store holds `at 20 of 30`.

## Unresolved

- **No repair path.** `mediagram edit` has no `--duration`, so a set whose
  duration went missing could not be corrected without re-adding it. Nothing
  needs it today; worth an hour if that ever changes.
- **20 minutes is where the two rules cross.** Anything longer gets the last
  minute, anything shorter a twentieth. That is a defensible line rather than
  a measured one.
- Still open from before: the `cache-store.test.ts` eviction-order flake, and
  4 TypeScript errors in the untracked `web/test/posters.test.ts`.
