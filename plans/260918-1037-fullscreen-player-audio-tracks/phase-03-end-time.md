# Phase 3 — End time

**Priority:** independent. **Status:** done.

## Design

One pure function in `format.js`, because everything else there is pure and
tested and this is the same kind of thing:

```
endsAt(remainingSeconds, now) -> "23:41"
```

24-hour, zero-padded, deterministic — it takes the clock rather than reading
it, so a test can assert on it. Matches `clockTime`'s hand-rolled approach
for the same reason: `toLocaleTimeString` would vary by host.

The player recomputes it on `timeupdate` from the catalog's `duration` minus
`filmTime()`, divided by `playbackRate` so a viewer at 1.5× gets the truth.
Falls back to `video.duration` when the catalog has none, and shows nothing
when neither knows — a projected end time from an unknown runtime is a
guess presented as a fact.

## Files

- modify `web/public/lib/format.js`, `format.d.ts`
- modify `web/public/lib/player.js`
- modify `web/test/format.test.ts`

## Success criteria

- Crossing midnight wraps to the next day rather than printing `25:10`.
- A paused film's projected end slides later, because it does.
- No runtime, no claim.
