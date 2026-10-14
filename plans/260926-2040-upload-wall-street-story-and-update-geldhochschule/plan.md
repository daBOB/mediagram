# Upload Wall Street Story and Update Geldhochschule

Date: 2026-09-26
Target: Ingest Wall Street Story course (864 lessons, 10 documents, ~113 GB) and update Geldhochschule custom artwork in mediagram.

## Items

- [x] Migrate `library.db` to schema v10 (`artwork` table support)
- [x] Optimize user-provided artwork (< 1MB) for Geldhochschule and Wall Street Story:
  - `geldhochschule_plakat_2x3.png` -> `title-geldhochschule` (poster)
  - `geldhochschule_backdrop_16x9.png` -> `title-geldhochschule-bg` (backdrop)
  - `wallstreet story.poster.png` -> `title-wall-street-story` (poster)
  - `wallstreet story.backdrop.png` -> `title-wall-street-story-bg` (backdrop)
- [x] Store artwork in `library.db` and place webp companion files in course root folders
- [x] Publish updated index to Telegram channel (`message_id: 5919`)
- [x] Update all 162 newly revised summaries (.summary.md) for Geldhochschule in `library.db` `assets`
- [x] Import all 162 WebVTT subtitles (.vtt) for Geldhochschule in `library.db` `assets`
- [x] Probe media integrity of all 864 videos + 10 documents in Wall Street Story (0 errors)
- [x] Author resilient background upload runner `scripts/upload_wallstreet_story.py`
- [x] Run upload runner in background and track progress (completed cleanly with 0 errors)
- [x] Verify completed set count in `library.db` (864 lessons, 10 documents, 0 pending)
- [x] Final index publish and verification (pushed to channel as message 6815)

## Verification Results

- `mediagram status`: 2,262 sets total (11,744.7 GB).
- `Geldhochschule`: 162/162 lessons complete, 162/162 revised summaries (`.summary.md`), 162/162 subtitles (`.vtt`), custom poster (`title-geldhochschule`) and backdrop (`title-geldhochschule-bg`).
- `Wall Street Story`: 864/864 lessons complete, 10/10 documents complete, 864 subtitles, 863 summaries, custom poster (`title-wall-street-story`) and backdrop (`title-wall-street-story-bg`).
- Total pending sets in library: 0.
- Channel index message: 6815.
