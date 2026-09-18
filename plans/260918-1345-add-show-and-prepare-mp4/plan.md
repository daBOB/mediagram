# add-show, prepare --mp4/--out, and the quality label

Three changes that came out of adding three series by hand. Each one closes a
gap the manual run exposed.

## Why

Uploading a series meant a shell loop, because `add-course` is the only
command that walks a directory and it marks everything a course. The loop was
written twice and wrong twice: once it paired a show with another show's TMDB
id, once it walked a root holding shows nobody asked for. A command with a
dry-run table makes both impossible.

The conversion that makes a title direct-play — Matroska to mp4, E-AC-3 to
AAC — has no home either. `prepare` drops tracks but stays Matroska with the
original codecs, so it does half the job.

And 1080p content wider than 16:9 is labelled 720p, because the label is
picked from height alone.

## Phases

- [x] **01** Direct-play policy into `mlib-spec`, player holds the pinned copy
- [x] **02** `prepare --mp4 --out <dir>`
- [x] **03** `add-show <dir> --tmdb <id>`, warning before it uploads
- [x] **04** Quality label from the larger dimension, not height

## Key decisions

`add-show` warns, it does not convert. Whether to convert is a per-show
judgement: a HEVC show gains nothing from a container change, and which
languages to keep is a choice. An expensive lossy step does not belong
hidden inside "upload this".

The warning must separate what `prepare` can fix (container, audio codec)
from what it cannot (the video codec itself). Telling someone to run
`prepare` on an HEVC show would cost them hours for nothing.

## Success criteria

- `add-show <dir> --tmdb <id> --dry-run` prints the file/season/episode table
- A run over Matroska + E-AC-3 warns and names `prepare --mp4`
- A run over HEVC warns that `prepare` will not fix it
- `prepare --mp4 --out <dir>` writes direct-playable mp4 beside untouched sources
- Scope-ratio 1080p no longer reads as 720p
- Policy drift between Rust and the player fails a test

## Review

All four phases done 2026-09-18. 656 Rust tests, 407 web tests, clippy clean.
The one failure, `config_requires_api_hash_and_channel`, predates this work and
is unrelated: it asserts `config::load` rejects an empty `api_hash`, and it
does not.

Both warning branches were checked against real libraries rather than
fixtures. 30 Rock reports `hevc video ... prepare cannot fix that`; the
Spartacus originals report `Matroska container` and name the prepare command;
the already-converted mp4s report nothing.

Three bugs found by testing the work rather than trusting it:

- `--mp4` skipped files that already fit their part limit. Right for a size
  command, wrong for a playability one.
- `--out` given a single file renamed the output directory into the result,
  because stripping the root off a path that is the root leaves nothing.
- `add-show` counted a folder holding both an mkv and its mp4 as sixteen
  episodes rather than eight. It now refuses instead of letting sort order
  decide which copy reaches the channel.
