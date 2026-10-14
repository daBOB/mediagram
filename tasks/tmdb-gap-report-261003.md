# TMDB gap report — incomplete series in the local index

Date: 2026-10-03 · Library: `~/.local/share/mediagram/library.db` ·
Script: `scripts/check_tmdb_gaps.py` · Raw report: `tasks/tmdb-gap-report-261003.out` ·
TMDB responses cached: `/tmp/mg-tmdb-cache/`

## Method

Every `sets` row with `kind='ep'` and a TMDB id (99 series, 7,325 episodes)
was compared against live TMDB season/episode data (language `de-DE`).
Excluded as non-gaps:

- episodes with a future air date (unaired)
- season 0 (specials)
- double-episode sets: one file whose `episode` column is a pair like `[17,18]`
  counts as both numbers (Seinfeld, Ally McBeal, King of Queens S03,
  Hardcastle pilot, Ein Colt pilot)

## Currently uploading — do not chase

The active batches (`shows_upload_plan_a/b`, status files dated 2026-10-03)
already cover:

- **Silicon Valley** (tmdb 60573) — S01E07–08 + seasons 2–6 (plan_a, show 15/18)
- **Modern Family** (1421) — 36 eps + seasons 9/11 (plan_b, show 2/21)
- **Gossip Girl** (1395) — 22 eps (plan_b)
- also queued in plans A/B: Mad Men, Pumuckl, High Potential, Matlock,
  Made in Korea, MobLand, Outer Banks, Pantheon, Ghosts, The Simpsons (id 456
  not in plan; seasons 37–38 missing below still apply)

Re-run this report after the batches finish before filing gaps for these.

## Genuine gaps — whole seasons absent

| Series | tmdb | Missing |
|---|---|---|
| Akte X (The X-Files) | 4087 | seasons 5–11 (~121 eps) |
| How I Met Your Mother | 1100 | seasons 7–9 (72 eps) |
| Star Trek: Strange New Worlds | 103516 | seasons 1–3 (30 eps) — `strange_new_worlds.log` suggests an earlier attempt |
| New Girl | 1420 | seasons 6–7 (30 eps) |
| Shameless (US) | 34307 | seasons 9–11 (38 eps) |
| Foundation | 93740 | seasons 2–3 (20 eps) |
| Der Prinz von Bel-Air | 1892 | season 6 (24 eps) |
| MacGyver | 2875 | seasons 6–7 (35 eps) |
| What We Do in the Shadows | 83631 | seasons 5–6 (21 eps) |
| Loki | 84958 | season 2 (6 eps, aired 2023-10-05) |
| Halo | 52814 | season 1 (9 eps, aired 2022) |
| SAS Rogue Heroes | 93870 | season 2 (6 eps, aired 2025-01-01) |
| Pantheon | 195339 | season 2 (8 eps, aired 2025-02-21) — queued in plan_b |
| Babylon Berlin | 66980 | season 5 (8 eps, premiered 2026-09-10) |
| For All Mankind | 87917 | season 5 (10 eps, aired 2026-03-26 — real gap, not queued) |
| Die Simpsons | 456 | seasons 37–38 (37+ eps, current) |
| American Dad | 1433 | seasons 21–22 |
| Monarch: Legacy of Monsters | 202411 | season 2 *(2026-02-26: aired, real)* |
| MobLand | 247718 | season 2 *(queued plan_b)* |
| Matlock (2024) | 226174 | season 2 *(queued plan_b)* |
| High Potential | 226637 | season 2 *(queued plan_b)* |
| Ghosts (US) | 126027 | season 5 *(queued plan_b)* |
| Outer Banks | 100757 | season 5 *(2026-08-20, aired; queued plan_b)* |
| Mad Men | 1104 | season 7 *(queued)* |
| Call My Agent Berlin | 296527 | S01E07–10 |

## Genuine gaps — individual episodes

- **21 Jump Street** (1486): S01E13, S04E16, S04E17, S04E25, S04E26,
  S05E01–03. Note: lib carries extras S04E21–24 that TMDB lists elsewhere —
  likely German vs US broadcast ordering; verify before uploading.
- **30 Rock** (4608): S04E20–21, S05E23, S06E07
- **Akte X** (4087): S04E23–24 (Dämonen, Gethsemane)
- **American Dad** (1433): S02E11–16, S14E14–22, S17E23–24 (same caveat:
  numbering offsets in some seasons)
- **Avengers: United They Stand** (59427): S05E14–23
- **Boardwalk Empire** (1621): S02E07
- **Brooklyn Nine-Nine** (48891): S03E03, S03E15, S08E07–09
- **Der Prinz von Bel-Air** (1892): S05E08–25
- **Die Simpsons** (456): S03E24, S08E25, S16E21, S21E23, S22E22, S25E22,
  S28E22, S33E22 (mostly season finales)
- **Ein Colt für alle Fälle** (5031): S04E02, S05E12
- **Eine starke Familie** (2617): S07E19
- **Gossip Girl** (1395): S01E13–18, S02E11–25, S04E13
- **How I Met Your Mother** (1100): S06E11–24
- **King of Queens** (4238): S08E17–23
- **MacGyver** (2875): S05E06–21
- **Made in Korea** (246473): S02E05–06 *(aired 2026-09-23)*
- **Meister Eder und sein Pumuckl** (30688): S01E17 (Der große Krach)
- **Mila Superstar** (46348): S01E18–19, S01E34
- **Modern Family** (1421): S05E07, S08E09–22, S10E01–22 *(uploading)*
- **Pushing Daisies** (5639): S02E11
- **Sons of Anarchy** (1409): S01E03, S01E06, S02E02, S02E09, S03E07, S03E12,
  S04E06, S04E11, S05E02, S05E05–06, S05E09–10, S05E12, S06E13, S07E05–06, S07E11
- **Spartacus: Das Haus Ashur** (240459): S01E05, S01E10
- **Yellowstone** (73586): S02E05, S05E09–14 (the 2024 finale episodes)
- **Dragonball** (12609): eps 102–153 (King Piccolo / Piccolo Jr. arc) —
  verify against German release numbering
- **Beyblade Burst** (67667): lib holds 72 of ~309 TMDB eps. The 13-ep German
  arc seasons vs TMDB's 51-ep seasons make the per-season comparison useless,
  but the raw count suggests a real shortfall too — needs manual mapping.

## Known artifacts (fix so future runs are clean)

1. **`anime_overrides` table is empty.** Dragonball Z shows 7 "missing
   seasons" purely because the library uses German arc seasons and TMDB splits
   by saga; Beyblade Burst likewise. Populate overrides (or add a mapping) so
   anime series compare correctly.
2. **Pair-file episode values** (`[17,18]`) are already handled in this
   report's script; keep it when re-running.
3. TMDB de-DE numbering differs from US for 21 Jump Street / American Dad —
   cross-check with absolute (`abs`) numbering before uploading.

## Suggested next steps

- [ ] Re-run after plan_a/plan_b uploads finish (`python3 scripts/check_tmdb_gaps.py --no-cache`)
- [ ] Populate `anime_overrides` for Dragonball Z (12971) and Beyblade Burst (67667)
- [ ] Decide acquisition for: Akte X S5–11, HIMYM S7–9, SNW S1–3, Shameless S9–11,
      Foundation S2–3, New Girl S6–7, Loki S2, What We Do in the Shadows S5–6
- [ ] File GitHub issues per show for the single-episode gaps (see
      `docs/agents/issue-tracker.md`)
