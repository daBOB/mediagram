# Plan: Ingest, Prepare, and Upload TV Series from ~/TK08/Videos/xstream via mediagram

**Date**: 2026-09-29  
**Directory**: `plans/260929-1639-upload-xstream-shows/`  
**Target**: Prepare (to direct-play MP4 + AAC) and sequentially ingest/upload TV series from `~/TK08/Videos/xstream` into `mediagram`.

## Overview
`~/TK08/Videos/xstream` holds 56 TV show directories (2,900+ episodes). Shows are synced from `bytefortress` in the background. The queue runner prepares each show via `mediagram prepare <show_dir> --mp4 --out <show_dir> --delete-source` (ensuring faststart MP4 containers and direct browser playback without server transcoding), then uploads via `mediagram add-show <show_dir> --tmdb <id> --yes`, with interleaved resume checkpoints.

## Items
- [x] **Phase 1: Provider ID Resolution & Show Cataloging**
  - Mapped all 56 show directories in `~/TK08/Videos/xstream` to their verified TMDB series IDs.
  - Generated structured plan catalog at `~/.local/share/mediagram/shows_upload_plan.json`.
- [x] **Phase 2: Integrity & Dry-Run Verification**
  - Ran `mediagram add-show --dry-run --tmdb <id>` on all ready series. All 25 present series validated with 0 duplicate episodes and valid season/episode numbering.
- [x] **Phase 3: Resilient Queue Runner Implementation**
  - Implemented `scripts/upload_shows_queue.py`:
    - Automatically prepares each show (`mediagram prepare ... --mp4 --out ... --delete-source`) before upload.
    - Sequentially executes `mediagram add-show <dir> --tmdb <id> --yes`.
    - Skips directories currently being synced by rsync until finished.
    - Checkpoint sweep after every show via `mediagram resume`.
    - Multi-pass execution across all 56 shows.
    - Live stdout streaming to `~/.local/share/mediagram/shows_batch_upload.log`.
- [x] **Phase 4: Verified Pilot Run (Call My Agent Berlin)**
  - Successfully prepared and uploaded `DE - Call My Agent Berlin (2025) (DE)` (6/6 episodes complete, 0 pending, published to channel index message 10415).
- [/] **Phase 5: Background Queue Execution & Monitoring**
  - Background process started (PID 135482) actively preparing and uploading the remaining queue.

## Verification
- `Call My Agent Berlin`: 6 of 6 episodes complete in `library.db` with container `mp4`, status `complete`.
- Library status: 5,602 sets total, 0 pending sets in `library.db`.
