# Spruchreif — finish the proverb

Status: done (Spieltisch 0.192.0, branch `feat/spruchreif`, merged into `main`; installed on funstation)

## Why
On 2026-11-14 the user asked for another minigame: complete proverbs (Sprichwörter) in German and English,
with a shelf poster and backdrop.

## User decisions (2026-11-14)
- **4 endings on the phone**, Buzzer-Quiz scoring. The bluff variant (players invent endings) was offered and declined.
- **German and English mixed in every game**, by turns. A lobby language setting was offered and declined.
- Name **Spruchreif**.

## Design
- Not a new engine: `GameKind::Spruchreif` builds a `Quiz` from `src/spruchreif.rs`, like Grenzfall. Phones,
  TV card, reveal and podium are the quiz's; no new views.
- Rounds 10 (Kurz 5, Lang 15) on Grenzfall's `tiers()` shape, easiest first; the language alternates every round,
  a coin decides which opens. Category `🇩🇪 Sprichwort` / `🇬🇧 Proverb`; TV text is the start plus " …".
- Tonight's history key `spruch:<start>`; a used-up language+tier starts over, the game's own picks excepted.
- Data: `questions/sprichwoerter.json`, 246 hand-written entries (de 50/56/33, en 35/40/32 by tier), each with three
  wrong endings that fit the grammar and are no real variant of the proverb. Endings at most 40 characters.

## Steps
- [x] Proverb set and question builder, tests (`spruchreif_tests.rs`)
- [x] Game kind, catalogue (timed, sized), factory, length and tempo test lists
- [x] Shelf blurb
- [x] Browser walk: Kurz game, two phones, both languages, reveal and podium (`reports/`)
- [x] Shelf poster and backdrop (BudgetPixel gpt-image-2.6-sunburst, medium; originals in `game_poster/spruchreif_*.jpg`)
- [x] Version 0.192.0, changelog
- [x] Installed on funstation (WayDroid, `pm install -r` over 0.191.0, release key); the shelf shows the poster (`reports/funstation-shelf-with-spruchreif-poster.jpg`)
- [x] Merge into `main` (user's go, 2026-11-14)

## Not done
- Kids mode does not change Spruchreif (proverbs are harmless; the hard tier may be too hard for kids).
- The phone shows only the endings; the start is read off the TV, as the quiz does with every question.
