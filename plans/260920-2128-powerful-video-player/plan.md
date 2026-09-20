# A powerful video player

The player can start a title, move through it and remember where you got to.
What it cannot do is any of the things a viewer does *while* watching: change
the language without paying for it, make a subtitle legible or on time, find
the start of the episode, or handle the picture at all.

## What the library says

566 sets, and the numbers chose the order of this plan:

| | |
|---|---|
| **312** have more than one audio language | and switching re-encodes the whole film |
| **206** have subtitles | and the only control is on and off |
| **351** need transcoding at all | so the conversion path is the common path |
| **215** the browser plays natively | and picking a language throws that away |
| **0** have chapter data | `chap` is a breadcrumb, not chapters |

## Phases

| | Phase | Where the work is | Status |
|---|---|---|---|
| 01 | [Audio without the wait](phase-01-audio-without-the-wait.md) | transcode args, registry | done |
| 02 | [What a viewer chose, remembered](phase-02-remembered-choices.md) | state schema v5 | done |
| 03 | [Subtitles that behave](phase-03-subtitles-that-behave.md) | browser only | done — size, backing, sync; no position |
| 04 | [Handling](phase-04-handling.md) | browser only | done |
| 05 | [Real chapters, and skip intro](phase-05-chapters-and-skip-intro.md) | Rust, index, both players | not started |
| 06 | [Thumbnails on the bar](phase-06-thumbnails-on-the-bar.md) | ffmpeg, a new asset kind | not started |

## Why this order

01 first because it is the largest waste and the least visible: a viewer who
switches to English on a file that was playing perfectly gets a black screen
and a GPU pass, and nothing on screen says why.

02 before 03 because both want the same thing — a place to keep what a viewer
chose, keyed to the show rather than the episode — and building it twice is
how the two get different answers about what "this show" means.

05 and 06 last because they are the only ones that leave the player: 05 needs
a spec bump, an index migration and a `metadata` re-run over 566 sets; 06
needs a new kind of generated asset. Both are worth doing and neither should
hold up the four that are not blocked on anything.

## What is deliberately not here

- **Subtitle downloading.** The library's subtitles come from the files it was
  built from, and a player that fetches them from the internet is a different
  program with a different threat model.
- **Video filters beyond framing.** Brightness and sharpening are a fight with
  a display's own calibration, and losing it silently looks like a bad rip.

## Verification

The stub harness, never the real player — it holds the account's MTProto auth
key. `scratchpad/stub-offline.ts` proves playback from cache with a throwing
upstream, which is what makes it safe to restart a transcode a hundred times.
