# Android — editorial departments parity

Status: pending (owed under CLAUDE.md § Surface Parity). Created 2026-09-26 by the web
plan `260926-1142-web-player-editorial-departments`, which is the reference: read its
web implementation before building each screen here, and match its decisions.

## Hard prerequisite (before any v9 index or package is published)

Installed Android builds read schemas 6–8 only and **refuse a v9 package**. The Rust core
in this branch (`mlib_spec::READABLE_SCHEMAS = [6,7,8,9]`) fixes that, so a phone/TV build
from 0.62.0 or later must be installed on the phone (caad49da), the TV emulator and the TV
box (192.168.0.35:5555) before the first v9 push/export. Same for both uploader machines.

## Owed screens and data (web reference in parentheses)

| Web | Android today | Owed |
|---|---|---|
| Department pages: Movies/Series/Tutorials hero + curated rows (`department-pages.js`) | shelves | hero, Featured, Genres tiles, Acclaimed, Recently added; Continue-your-series |
| Film page: spread + Overview/Cast/Similar/Details tabs (`film-page.js`) | title page | tabs, Similar (`similar.js` rule), "Part of" franchise |
| Series page: Resume SxEy + season picker + About/Cast/Similar (`series-page.js`, `series-resume.js`) | season list | resume rule, cast |
| Cast + person pages (`cast.js`) — only titles the profile can see | none | cast row, person page, kids filtering |
| Collections: franchises (≥2 held) + lists; franchise page (`collections-page.js`) | lists | franchise cards and page |
| Search: grouped results, People, Collections, filters (`search-view.js`) | flat | people + collections |
| Latest, Genres index (`utility-pages.js`) | — | both |
| Settings › Appearance (theme/accent/artwork) | settings screen | decide: TV/phone may deliberately differ — write down why |

v9 data: `shows.collection_id/collection_name/series_type`, tables `credits` (incl.
`profile`) and `franchises`; portraits `tmdb-person-<id>.jpg`. Android's on-device artwork
fetch currently skips portraits (like backdrops) — revisit when the cast row lands.
