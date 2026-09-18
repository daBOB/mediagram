# Verification

Run 2026-09-18. `./scripts/check.sh`: clippy clean, 26 Rust tests, **453 bun
tests, 0 fail** (was 431 before this work).

## End-to-end proof of the audio chooser

A clip was built with the exact stream layout that defeats an `alang` mapping
— stream 0 untagged, stream 1 German and marked default, stream 2 English —
and each stream given a different tone so the output could be measured rather
than trusted.

`AudioTrackReader` (the real one, real `ffprobe`, through the real Range
route) reported:

```
0  lang null   default false
1  lang deu    default true
2  lang eng    default false
```

The player opened on ordinal **1**, the file's own default. Picking English
sent `?seek=0&audio=2`. Running the command line `transcodeArgs` builds for
each ordinal and measuring the dominant frequency of what came out:

```
-map 0:a:0  ->  220.1 Hz   untagged
-map 0:a:1  ->  439.9 Hz   German
-map 0:a:2  ->  660.0 Hz   English
```

`alang` for this file is `["deu","eng"]`. Mapping position to ordinal would
have sent a viewer asking for German to 0:a:0 — the untagged tone. This is the
decision in `plan.md` §1, demonstrated rather than argued.

## Player state, observed in the browser

| Checked | Result |
|---|---|
| Opens preloaded, not playing | `paused: true`, `readyState: 4` |
| Preload readout | `ready · 0:14 ahead` |
| End time | `ends 12:48`, from catalog runtime |
| Chooser rows | `Track 1 · stereo · aac` / `German · …` / `English · …` |
| Starts on file's default | `value === "1"` |
| HUD up while paused | `resting` absent |
| HUD rests while playing | `resting` present after 2.6 s |
| Notes toggle | panel shown, `aria-expanded="true"` |
| Jump bar on a direct title | `display: none` |

## Bug found by looking rather than asserting

`jump.hidden` was `true` while computed `display` was `flex`. `[hidden]` is a
user-agent rule of the lowest specificity, and `.jump`/`.picker` both set
`display`, so the attribute did nothing. The transcode scrub bar was visible
on titles that play directly and have nothing to jump.

Pre-existing, and previously mis-diagnosed in this repo as stale dialog state
because the check asserted on the property and never on the rendering.
`[hidden] { display: none !important }` now covers the page.

## Unresolved

- `web/test/posters.test.ts` (untracked, from the poster work in flight) has 4
  TypeScript errors: a `ReadableStream | Uint8Array` union passed where bytes
  are wanted. Not touched — not this change, and `tsc` is not in the gate.
  Worth deciding whether `check.sh` should run `tsc` at all.
- The audio probe is cached per process with no bound. One entry per opened
  title is small, but nothing evicts it.
- A non-default track on a direct-play title starts an encode the viewer did
  not explicitly ask for. It is announced, and it is the only way to honour
  the choice, but it is the one place the player spends real CPU on a click.
