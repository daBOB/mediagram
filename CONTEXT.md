# mediagram

A personal video library kept in a private Telegram channel: an uploader puts
titles into the channel, and players (web, Android phone and TV) read them back.

## Language

### The library in the channel

**Set**:
One title's uploaded parts in the channel, identified by its set id, and the row
that describes it in an index.
_Avoid_: upload, file, item

**Local index**:
This machine's own `library.db`: every set it knows of, including uploads it has
not finished.
_Avoid_: database, DB, local library

**Channel index**:
The newest index snapshot the channel itself posted, by push time, which every
player reads. A publish pins it, but a snapshot left unpinned still counts.
_Avoid_: remote index, pinned index, pinned DB, published index

**Pull**:
Merging the channel index into the local index, so this machine holds what other
machines published.
_Avoid_: sync, fetch, download (for the whole act)

**Upload session**:
One command's run of uploads: the sets it walks, one connection, and at most
one publish at the end.
_Avoid_: uploader, batch, job

**Publish**:
Replacing the channel index with a snapshot of the local index, after pulling so
nothing another machine published is dropped.
_Avoid_: push (except as the `push-index` command's name), upload (reserved for sets)

**Category**:
A hand-set label on a course, a documentary collection or a standalone
documentary, one each, that files it into a row on its department page.
_Avoid_: genre, tag

**Subtitle bundle**:
One set's subtitle tracks, gzip'd JSON, sent as its own small channel
document and fetched once. The index records where it lives and a summary
of each track; the bundle itself is the durable copy.
_Avoid_: subtitle file, subtitle asset (reserved for the older, inline rows
a bundle replaces)

**Forced track**:
A subtitle track meant to show even when subtitles are off — on-screen text
in a language the audience is assumed not to read, not a second copy of the
dialogue.
_Avoid_: default track

**SDH track**:
A subtitle track written for a deaf or hard-of-hearing audience: dialogue
plus the sound a hearing viewer would otherwise infer.
_Avoid_: CC, closed captions (this format carries only text tracks, never a
broadcast-style closed-caption stream)
