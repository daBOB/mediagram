# A film page, and genres you can follow

Status: **web done** 2026-09-23 — Android port is the follow-up

## Why
TMDB genres and user score are already fetched (`mediagram metadata`) and
travel in every channel snapshot (`shows.genres`, `shows.rating`: 540/540 films
have genres, 539 a score), and `/api/shows/<key>` already serves them. The web
player shows them only on a series header, as plain text. Films have no page:
a click plays.

## Decided (user)
- Clicking a film opens a **film detail page** (poster, year, runtime, ★ score,
  genre links, description, Play) — the Android app's title page is the model.
- A genre link opens a **genre shelf**: films *and* series tagged with it.
- The score shows **only on the title's own page** (film page, series header).
- **Web now, Android next**: the Android app owes the same links and shelf
  (Surface Parity); a follow-up plan ports it.

## Phases
| # | Phase | Status |
|---|-------|--------|
| 01 | Server: each `/api/sets` row carries its title's `genres` (from `shows`, keyed like the poster) | done |
| 02 | Page: `#/film/<setId>` detail page; film cards on the Movies shelf and the start page open it | done |
| 03 | Page: `#/genre/<name>` shelf of films and series; genre links on the film page and the series header | done |
| 04 | Tests, stub-browser check, docs, version | done |

## Notes
- Continue shelf keeps playing directly: it exists to resume.
- Genre names are TMDB's in the configured language (`Komödie`, `Krimi`),
  matched exactly as stored; the link carries the name.
- Android follow-up: `TitleDetailScreen` genres → links; genre shelf.
