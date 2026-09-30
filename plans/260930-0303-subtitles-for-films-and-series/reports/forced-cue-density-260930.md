# Forced-track cue density, measured 2026-09-30

How many cues an hour does an unflagged, untitled German track carry when it is the
forced one (signs and foreign speech in a German dub), against a full track? Sets the
named constants in `subtitles/arrange.rs`.

## Method

`ffprobe` for each subtitle stream (codec, tags, dispositions, container duration), then
`ffmpeg -map 0:<i> -c:s webvtt` to a scratch folder and a count of `-->` lines. Read only;
nothing was written beside a source. Shows and seasons only, no paths.

## Results (78 text tracks, 5 + 1 seasons, every episode that carries subtitles)

| Show, season | Language | Title tag | Tracks | Cues | Cues/hour (min / median / max) |
|---|---|---|---|---|---|
| Boardwalk Empire S1 | de | none | 12 | 4-43 | 4.1 / 6.9 / 48.4 |
| Boardwalk Empire S2 | de | none | 11 | 1-11 | 1.0 / 3.1 / 11.3 |
| Boardwalk Empire S2 | en | none | 2 | 2-6 | 2.1 / 7.4 / 7.4 |
| Boardwalk Empire S3 | de | none | 11 | 1-65 | 1.1 / 13.0 / 69.6 |
| Boardwalk Empire S3 | en | none | 2 | 10-57 | 10.5 / 61.0 / 61.0 |
| Boardwalk Empire S4 | de | none | 12 | 1-83 | 1.0 / 6.2 / 86.1 |
| Boardwalk Empire S4 | en | none | 2 | 2-3 | 2.0 / 3.2 / 3.2 |
| Boardwalk Empire S5 | de | none | 6 | 1-67 | 1.1 / 9.5 / 68.7 |
| Black Sails S4 | de | "Full" | 10 | 441-729 | 442 / 604 / 737 |
| Black Sails S4 | en | "Full" | 10 | 481-760 | 482 / 651 / 768 |

All Boardwalk Empire tracks are ASS, untitled, with no forced or hearing-impaired flag.
Black Sails S4 is the full-track reference (titled "Full"). Mad Men S1 and S7 and
Spartacus S1 carry no subtitle streams, and no reachable source here had a forced
disposition or a picture track, so the flag and title rules are covered by the Arcane
and Band of Brothers shapes in the unit fixtures, not by these measurements.

## Reading

- Forced-shaped tracks run 1 to 86 cues an hour (83 cues at most in a 59-minute
  episode); full tracks start at 442. The gap is a factor of five.
- The spec's first guess, 10 an hour, would have missed 20 of the 52 German Boardwalk
  tracks, and 60 an hour would still have missed 4 (up to 86 an hour in S4).
- `FORCED_MAX_CUES_PER_HOUR` is now **120**: clear of the densest sparse track (86) and
  a factor of 3.7 under the sparsest full one (442).
- `FORCED_MAX_SHARE_OF_DENSEST` stays **0.25**. No measured source had two unflagged
  tracks of one language, so nothing here argues for moving it; it only matters where a
  release ships a sparse and a full track without flagging either.

## Known ceiling

A lone unflagged, untitled full track on a near-silent film (well under 120 lines an
hour) would be taken for forced. Flag and title are checked first, and a same-language
denser track turns the relative rule on instead; a film with only such a track is not
something this library has shown.
