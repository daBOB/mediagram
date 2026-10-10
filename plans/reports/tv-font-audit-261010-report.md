# TV font audit: which roles still draw a different face than the web

2026-10-10. A read-only audit: no device was touched and no code was edited. It answers the user's "The font is still old on tv" (`tv-feedback-261010-report.md` #5).

**Inputs.**
- The box screenshot `scratchpad/tv-now/now.png`: Anime department, batch-1 build.
- The batch-1 role table.
- The web player at 1920×1080 through `bun run preview` (copies of the index and state, no Telegram, port 8796, now stopped). Computed styles were read with `getComputedStyle`.
- The font files themselves, read with fontTools.

**The box runs this code.** `git diff 2ee5f19b HEAD` touches no font code: TvThisMonth lost only a no-op weight, and the bar's layout changed. Line numbers below are HEAD's. The rail and IndexRow are cited from HEAD, not the other agent's uncommitted edits.

## Verdict

Batch 1 got the **families** right for most roles. "Still old" comes from five things it did not touch:

1. **Display titles draw Fraunces' text cut, not its display cut.**
   - The department title ("ANIME"), the franchise title and the title-spread headline go through `DisplayFace`. That is `fraunces_display_500`, instanced at **opsz 28**.
   - The web sets them at opsz = font size: **120 / 80 / 92**.
   - opsz 28 is the sturdy, wide, low-contrast cut meant for 17–26px names. It is what the TV drew before batch 1, only smaller. This is the biggest visible difference (composite, row 1).
2. **Newsreader italic does not exist on Android.** `newsreader.ttf` is roman only. Every quote is the roman with a synthetic skew (double-storey *a*, upright-style *f* and *g*). The web loads a real italic file (composite, row 2).
3. **The Anime department's "Series"/"Films" heads**, and the franchise's "In release order", are set in Fraunces 600. The web sets them in **Geist 600** (composite, row 3).
4. **Meta and figure lines are Geist 500 where the web uses 400.** `CatalogueType.labelSmall` and `labelMedium` inherit M3's Medium weight.
5. **Two smaller ones:**
   - the Home feature titles are drawn at 500 where the web uses 600;
   - the Settings page title is drawn at opsz 72 where the web uses 120.

Composite, web left and TV right, both at native 1920px: `scratchpad/font-audit/font-audit-composite.png`.

## Font files (verified)

| File | What it is | How verified |
|---|---|---|
| `res/font/fraunces_display_500.ttf` / `_600` | static, wght 500 / 600, **opsz 28** | outline match against the web woff2 instanced at a grid of opsz values: best opsz 28, distance 1.9; next best 24 at 12.3 |
| `fraunces_page_title.ttf` | static, wght 500, **opsz 112** | best 112, distance 1.9 |
| `fraunces_page_title_compact.ttf` | static, wght 500, **opsz 72** | best 72, distance 1.8 |
| `fraunces_cover_600.ttf` | static, wght 600, **opsz 144** | best 144, distance 1.7 |
| `newsreader.ttf` | variable wght/opsz, **roman only** (`fsSelection` italic bit off, STAT `ital` = Roman) | fontTools |
| `geist.ttf` | variable wght 100–900, roman | fontTools |
| web `newsreader-italic-latin.woff2` | Newsreader 16pt **Italic**, italicAngle −17 | fontTools. No Android counterpart. |

All the Android files are the web's **latin** subset only, 222–225 cmap entries. The web also ships the `latin-ext` cuts (`theme.css:11-19,31-37,47-53,63-69`). See the minor findings.

## Role table

The web column gives computed style at 1920 unless noted. The web always uses `font-optical-sizing: auto`, so its opsz equals the px size. "TV" names the style the call site resolves to, followed to its font file.

### Anime department (the screenshot)

| Role | Web face (file:line) | TV face (file:line) | Match? |
|---|---|---|---|
| Department pill | Geist 500 14 (shell.css:47-48) | `labelLarge`, Geist 500 (TvChromeControls.kt:70 → TypeRoles.kt:37) | ✅ |
| Avatar initial | Geist 600 17 (shell.css:151) | Geist 600 17 (TvChromeControls.kt:141) | ✅ |
| Rail label | Geist 500 14 (shell.css:47-48) | `labelLarge` (TvIndexRow.kt:80,138) | ✅ |
| Rail count | Geist 400 10 (shell.css:60) | `labelSmall` Normal 11 (TvIndexRow.kt:142) | ✅ |
| Rail tally | Geist 500 10 caps (shell.css:66) | `labelSmall` Medium (TvLibraryRail.kt:143) | ✅ |
| Wordmark | Fraunces 600 27.2 (shell.css:34) | `titleLarge` 600 27, opsz 28 (TvLibraryRail.kt:50) | ✅ (opsz 27 vs 28) |
| **Department title** | **Fraunces 500, opsz 120**, −0.03em (departments.css:32-33) | **`DisplayFace` → `fraunces_display_500`, opsz 28**, −0.1sp (TvDepartmentHero.kt:125 → TvHeadings.kt:186 → Type.kt:80-84) | ❌ optical cut and tracking |
| Department line | Newsreader 400 20 (departments.css:36) | Newsreader 400 20, opsz 16 (TvDepartmentHero.kt:191) | ✅ (opsz 20 vs 16, minor) |
| **Department quote** | **Newsreader *Italic*** 400 (departments.css:47) | Newsreader **roman + synthetic skew** (TvDepartmentHero.kt:163 → Type.kt:87) | ❌ style |
| Quote credit | Geist 500 10 caps (departments.css:48) | `Eyebrow`, Geist 500 (TvDepartmentHero.kt:170) | ✅ |
| "Continue watching" row head | Geist 600 20.8 (home.css:195) | `RowHead`, Geist 600 (TvDepartmentResumeRow.kt:48 → TvHeadings.kt:198) | ✅ |
| **"Series" / "Films" heads** | **Geist 600 20.8**, `.row-head h2` via `deptRow` (anime-department.js:61,65 → department-hero.js:45-48 → home.css:195) | **`ShelfSub`, Fraunces 600 24** (TvWall.kt:178 → TvHeadings.kt:195) | ❌ family |
| Plate name | Fraunces 500 17, opsz 17 (catalog.css:54) | `titleSmall` 500 17, opsz 28 (TvPlate.kt:171) | ✅ (opsz drift) |
| **Plate meta** | **Geist 400** 12 (catalog.css:55; body 400, theme.css:158) | `labelSmall` → M3 **Medium 500** (TvPlate.kt:174 → TypeRoles.kt:39) | ❌ weight |

### Home

| Role | Web face (file:line) | TV face (file:line) | Match? |
|---|---|---|---|
| Cover eyebrow | Geist 500 11 (theme.css:200) | `Eyebrow` (TvCoverSlide.kt:91) | ✅ |
| Cover title | Fraunces 600, opsz 144 forced (home.css:50,53) | `CoverTitle` → `fraunces_cover_600`, opsz 144 (TvCoverSlide.kt:98 → TitleType.kt:59) | ✅ |
| Cover deck | Newsreader 400 (home.css:60) | Newsreader 400 (TvCoverSlide.kt:106) | ✅ |
| **Cover meta** | **Geist 400** 13 (home.css:63-68) | `labelSmall` → **500** (TvCoverSlide.kt:116) | ❌ weight |
| Cover pills | Geist 500 15 (title-page.css:14) | `bodyMedium` Medium (TvCoverActions.kt:130) | ✅ |
| "Details" | Geist 400 14 (home.css:77-79) | `bodyMedium` 14 (TvCoverActions.kt:92) | ✅ |
| Feature eyebrow | Geist 500 11 | `Eyebrow` (TvHomeFeatures.kt:134) | ✅ |
| **Feature title** | **Fraunces 600** 46.4, opsz 46 (home.css:172) | **`DisplayFace` 500**, opsz 28 (TvHomeFeatures.kt:140) | ❌ weight (and opsz) |
| Feature deck | Newsreader 400 16 (home.css:178) | Newsreader (TvHomeFeatures.kt:149) | ✅ |
| Row head | Geist 600 20.8 (home.css:195) | `RowHead` (TvBandHeading.kt:40) | ✅ |
| Row count, "See all" | Geist 400 13 (home.css:196,203) | Normal 13 (TvBandHeading.kt:48); `bodySmall` (TvContinueBand.kt:71) | ✅ |
| Resume name / sub | Geist 500 14 / 400 12 (home.css:272-273) | TvResumeCard.kt:175-176 | ✅ |
| Resume initial | Fraunces 600 32 (home.css:247) | `fraunces_display_600` 32 (TvResumeCard.kt:179) | ✅ |
| Pull-quote mark | Fraunces 600 64, opsz 64 (home.css:283) | `CoverTitle` 600 40, opsz 144 (TvContinueBand.kt:119) | ✅ (opsz drift; it is one glyph) |
| **Pull quote** | **Newsreader *Italic*** 400 (home.css:288) | Newsreader **roman + skew** (TvContinueBand.kt:133) | ❌ style |
| Pull-quote credit | Geist 500 11 (home.css:298) | `labelSmall` → 500 (TvContinueBand.kt:128) | ✅, but only because of the M3 weight leak; see fix 4 |
| Strip name / initials | Fraunces 500 (catalog.css:41,49) | `titleSmall` / `titleLarge` 500 (TvPosterStrip.kt:101,109) | ✅ |
| **Strip meta** | **Geist 400** 12 | `labelSmall` → **500** (TvPosterStrip.kt:116) | ❌ weight |
| This month: number | Fraunces **400** (home.css:320) | `titleMedium` **500** (TvThisMonth.kt:60) | ⚠️ deliberate: DESIGN.md's Fraunces Floor (never under 500) |
| This month: name | Fraunces 500 (home.css:322) | `titleSmall` 500 (TvThisMonth.kt:65) | ✅ |
| **This month: meta** | **Geist 400** 12 (home.css:323) | `labelSmall` → **500** (TvThisMonth.kt:70) | ❌ weight |

### The same faults off this screen

| Role | Web | TV | Match? |
|---|---|---|---|
| Title-spread title | Fraunces 500, opsz 92 (long: opsz 68) (title-page.css:89,93) | `DisplayFace`, opsz 28 (TvTitleSpread.kt:100) | ❌ optical |
| Spread quote | Newsreader *Italic* (title-page.css:113) | roman + skew (TvTitleSpread.kt:140-143) | ❌ style |
| Franchise title | Fraunces 500, opsz 80 (departments.css:108) | `DisplayFace`, opsz 28 (TvDepartmentHero.kt:116) | ❌ optical |
| Franchise "In release order" | Geist 600, `deptRow` (collections-page.js:115) | `ShelfSub`, Fraunces 600 (TvFranchisePage.kt:98 → TvWall.kt:178) | ❌ family |
| Settings / System page title | Fraunces 500 at `.dept-title`, opsz 120 | `PageHead` → `PageTitleCompact`, **opsz 72**, because `maxTitleSize` 60 < 90 (TvSettingsPanes.kt:130-136 → PageHead.kt:55) | ❌ optical (less than the department title) |
| Shelf head (Genres, Latest, person, Stats) | Fraunces 600, opsz 57.6 (catalog.css:7) | `fraunces_display_600`, opsz 28 (TvHeadings.kt:178) | ⚠️ optical; no 600 cut nearer exists |
| Fact label, shelf extent, art-tile counts | Geist **400** (title-page.css:140; catalog.css:13,147; departments.css:105) | `labelSmall` / `labelMedium` → **500** (TvFactSheet.kt:53; TvHeadings.kt:66; TvArtTile.kt:94) | ❌ weight |
| Search "why" | Geist 400 12, **upright** `.badge` (search-view.js:180 → catalog.css:87) | Geist **italic** 13, synthesized: Geist has no italic (TvSearchRows.kt:100) | ❌ style |

The scan found no `Text`/`BasicText` without a style, no `MaterialTheme.typography.*` reads, and no `LocalTextStyle` overrides in `:ui-tv` (`scratchpad/font-audit/bare_text.py`). Nothing falls back to tv-material's Roboto.

## Root causes

1. **`DisplayFace` was never moved off the opsz-28 file** (TvHeadings.kt:186).
   - Batch 1 halved the display *sizes* but kept the *face*. Its concern 3 says "a face question the audit marked keep".
   - The gap audit's "keep" for the department title (gap audit row 59, Q2) was about **size** only. The user's decision on Q2 was also size only ("department 60sp…").
   - **No user decision fixes the face.** It compared family names ("Fraunces" = "Fraunces") and missed that Android pins opsz while the browser tracks the size.
   - The display cuts that match already ship: `PageTitle` at opsz 112 and `PageTitleCompact` at opsz 72. The tablet uses them (`PageHead`).
   - The TV's halved size is the 1920 look scaled down, so the right cut is the one for the **1920 size**, not for 60sp.
2. **No Newsreader italic resource.** `Read` declares only upright fonts (Type.kt:50-59,87). Compose's default `FontSynthesis.All` then slants the roman. The phone has the same fault: `DeptHeroText.kt`, `FeaturedSlide.kt`, `WideTitleSpread.kt`, `ResumeCard.kt` in ui-mobile.
3. **`TvWall` has one hard-coded heading style** (TvWall.kt:178, `ShelfSub`).
   - The gap audit's row 56 mapped every wall section to `.shelf-sub`. That is right for Person, Genre and Latest (cast.js:104-106, utility-pages.js:28,110,114).
   - It is wrong for the Anime department and the franchise page. Both build their sections with `deptRow`, so `.row-head` in Geist.
4. **`CatalogueType.labelSmall`/`labelMedium` don't set a weight** (TypeRoles.kt:38-39).
   - They inherit M3's `labelSmall`/`labelMedium` FontWeight.Medium.
   - The web's figures are 400, and DESIGN.md's own frontmatter says `figures`/`label` are fontWeight 400 (DESIGN.md:65-74).
   - Batch 1b ledgered this as "meta at weight 400". This is its cause.
5. **Smaller ones:**
   - Feature title: TvHomeFeatures.kt:140 copies `DisplayFace` without `SemiBold`.
   - Settings title: `PageHead` picks the cut by the *drawn* size (PageHead.kt:55). That suits the phone, but on TV 60sp stands for 120px.
   - Search "why": the italic came from the batch-1 table, which cited `.search-filter`. The web's "why" is `.badge`, upright.

## Fixes (exact)

1. **Display titles take the display cuts.** Import `designsystem.PageTitle` / `PageTitleCompact`.
   - TvDepartmentHero.kt:125: `style = PageTitle.copy(color = MaterialTheme.colorScheme.onSurface)`.
     - `PageTitle` already carries `.dept-title`'s −0.03em and 0.86 line height, with `Trim.None`.
     - It is static, so `TextAutoSize` is safe (TitleType.kt's own note).
   - TvDepartmentHero.kt:116 (franchise, web opsz 80): `PageTitleCompact.copy(fontSize = TvTypeScale.FranchiseTitle, lineHeight = 0.92.em)`.
   - TvTitleSpread.kt:100 (web opsz 92 / 68): `(if (long) PageTitleCompact else PageTitle).copy(fontSize = …, lineHeight = …, letterSpacing = (-0.025).em)`.
   - PageHead.kt:46-55: add `titleStyle: TextStyle = if (maxTitleSize.value >= WideTitleFloor) PageTitle else PageTitleCompact`. TvSettingsPanes.kt:130 passes `titleStyle = PageTitle`. The phone keeps its default.
   - TvHomeFeatures.kt:140: `DisplayFace.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.025).em, fontSize = …)`. `fraunces_display_600` is the nearest 600 cut to opsz 46.
   - After this, `DisplayFace` is used only by `ResumeInitial`. Fold it into `CatalogueType.headlineSmall.copy(…)` there and delete it, so nobody reaches for the text cut for a title again.
   - Expect `TvDepartmentHeroStateTest` and any width or autosize test to move. Opsz 112 is narrower, so "DOCUMENTARIES" steps down less.
2. **Real Newsreader italic.**
   - Decompress `web/public/font/newsreader-italic-latin.woff2` to `android/core/designsystem/src/main/res/font/newsreader_italic.ttf`, the same way `newsreader.ttf` was made:

     ```python
     t = TTFont(src); t.flavor = None; t.save(dst)
     ```

     It needs `brotli`, which system python3 has.
   - In Type.kt, give `read()` a style and add one italic face (only 400 italic is used):

     ```kotlin
     private fun read(weight: Int, italic: Boolean = false) = Font(
         resId = if (italic) R.font.newsreader_italic else R.font.newsreader,
         weight = FontWeight(weight),
         style = if (italic) FontStyle.Italic else FontStyle.Normal,
         variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", READ_OPTICAL)),
     )
     internal val Read = FontFamily(read(400), read(500), read(600), read(400, italic = true))
     ```

   - This fixes the TV's three quotes and the phone's four italic sites with no call-site change. DESIGN.md's Typography should then name the italic file.
3. **Department-section heads in Geist.**
   - TvWall.kt:79-87: add `headingStyle: TextStyle = ShelfSub`, and use it at :178.
   - TvAnimeDepartmentPage.kt (the `TvWall(` call at :73-80) and TvFranchisePage.kt (at :98) pass `headingStyle = RowHead`.
   - Person, Genre and Latest keep `ShelfSub`.
4. **Figures at 400.**
   - TypeRoles.kt:38-39: add `fontWeight = FontWeight.Normal` to `labelMedium` and `labelSmall`.
   - Call sites whose web role really is 500 then say so: TvContinueBand.kt:128 gets `fontWeight = FontWeight.Medium` (home.css:298). The rail tally and IndexRow already set their weights.
   - Phone side: 34 uses in 25 ui-mobile files pick this up. That matches DESIGN.md, but it needs a tablet look.
   - If the phone must not move in this batch, set `FontWeight.Normal` at the TV call sites listed in the tables instead: TvPlate.kt:174, TvPosterStrip.kt:116, TvThisMonth.kt:70, TvCoverSlide.kt:116, TvFactSheet.kt:53, TvHeadings.kt:66, TvArtTile.kt:94.
5. **Search "why"** (TvSearchRows.kt:100): drop `fontStyle = FontStyle.Italic` and set 12sp (`.badge`).

Fixes 1–3 are what the user is seeing on the screen they were looking at. Fixes 4–5 are small.

## Minor, and not face family/weight/style

- **Opsz drift on small text.** Plate names: web opsz 17, TV 28. Newsreader decks and lines: web 17–21, TV 16. Same family, weight and style; low priority.
- **The shelf head at 600** (web opsz 57.6) has no nearer cut than 28. If it still reads "old" after fix 1, instance a `fraunces_shelf_head_600.ttf` (wght 600, opsz 58) from the web woff2 with `fontTools.varLib.instancer`, the same way the others were made.
- **Glyph coverage.** The Android files are latin-only. 551 distinct titles or names in the index have characters outside them. On TV those characters fall back to the system font; the web draws them from its latin-ext cuts. In the Latin script, examples are the plate "Shōgun", credits such as "Ichirō Nagai" and "Krzysztof Kieślowski", "33⅓", and narrow no-break spaces in taglines. CJK and Cyrillic fall back on both surfaces. A fix needs the full source fonts (latin + latin-ext); a variable font cannot be merged from the two woff2 subsets with `fontTools.merge`.
- **M3 tracking leaks in.** `CatalogueType` starts every role from M3's baseline (TypeRoles.kt:24), so the roles carry the baseline's letter-spacing where the web has `normal`:

  | Role | Inherited tracking |
  |---|---|
  | `bodyLarge` (Newsreader: the department line, decks) | +0.5sp |
  | `bodyMedium` | +0.25sp |
  | `bodySmall` | +0.4sp |
  | `labelLarge` (pills, rail) | +0.1sp |
  | `labelSmall`/`labelMedium` | +0.5sp |
  | `titleMedium`/`titleSmall` | +0.15 / +0.1sp |

  That is tracking, not face, but it loosens every line against the web. Setting `letterSpacing = 0.sp` in each role would match. It moves the phone too.

## Evidence files

All in `scratchpad/font-audit/`:
- `font-audit-composite.png`: department title, quote and "Series" head, web against TV;
- `web-anime.png`, `web-home.png`: the web at 1920×1080;
- `tv-*.png`: crops of `tv-now/now.png` and of the batch-1 `after-home-continue.png`;
- scripts: `fontinfo.py`, `stat.py`, `opsz_match.py` (the opsz identification), `cov.py`, `bare_text.py`.

## Unresolved questions

1. Fix 4: should the phone move with it? It is DESIGN.md-correct, but it changes 34 phone call sites. Or should this batch be TV-only call sites?
2. The spread headline at opsz 92 falls exactly between the 72 and 112 cuts. I picked 112 for normal and 72 for long. Check it on the box.
3. A new static cut for the shelf head (fix noted above), or accept opsz 28?
4. Is the tracking reset (M3 baseline letter-spacing) in scope for "the font", or a separate ticket?
5. Latin-ext coverage: is it worth fetching the full font sources for "Shōgun" and the credits?

**Status:** DONE
**Summary:** Five face mismatches found and their root causes traced. The main ones: display titles on the opsz-28 Fraunces cut, synthetic italic quotes, and the Anime wall heads in Fraunces where the web uses Geist.

## Fixed (0.120.2, 2026-10-10)

Branch `feat/tv-parity`, release commit `2262e3da`. Not installed anywhere yet. No size, `TvScale` or spacing changed.

| # | Mismatch | What changed | Commit |
|---|---|---|---|
| 1 | Display titles on the opsz-28 text cut | Department title → `PageTitle` (112 cut). Franchise title → `PageTitleCompact` (72). Spread → `PageTitle`, or `PageTitleCompact` when long. `PageHead` takes a `titleStyle`, and TV Settings passes `PageTitle`; the phone keeps the size-based pick. `DisplayFace` is now `DisplaySemiBold` (600 text cut), used only by the feature titles and the resume initial. | `c6df5627` |
| 2 | Synthetic italic quotes | `res/font/newsreader_italic.ttf`, from `woff2_decompress web/public/font/newsreader-italic-latin.woff2`. It is variable wght/opsz, italicAngle −17, 224 glyphs. It joins `Read` as 400 italic, so the TV's three quotes and the phone's four use it with no call-site change. The licence README and DESIGN.md name it; the OFL already covers it. | `4a2d3b04` |
| 3 | "Series"/"Films"/"In release order" in Fraunces | `TvWall(headingStyle = ShelfSub)`. The Anime department and the franchise page pass `RowHead` (Geist 600). Person, Genre and Latest keep `ShelfSub`. | `3aadf30f` |
| 4 | Meta and figures at 500 | **TV only, one place:** `TvType.labelSmall`/`labelMedium` are `FontWeight.Normal`. That covers the plate, strip, cover, This month, fact sheet, shelf extent, art tile, course, stats and offline lines. The two TV sites that are 500 on the web now set it: the pull-quote credit (`home.css:298`) and the Stats "Next" head (`stats.css:32`). The rail count's own `Normal` is now redundant and was removed. | `a75e323b` |
| 4b | M3 tracking leak | One line per role: `letterSpacing = 0.sp` on CatalogueType's `titleMedium`, `titleSmall`, `body*` and `label*`. Both surfaces move. This made the bar-overflow test's pills fit, so its fixture got one longer department name. | `a75e323b` |
| 5 | Feature title 500; Settings opsz 72; search "why" italic | Feature title → `DisplaySemiBold` with −0.025em (`c6df5627`). Settings → the 112 cut (`c6df5627`). The "why" line is upright (`307249d1`). | |

**Tests.**
- `CatalogueFacesTest` covers: the italic is its own file at 400 with opsz; the roman weights stay on the roman file; the TV figure roles are Geist 400; the roles track at 0.
- `TvDisplayFacesTest` reads the laid-out `TextStyle`. It covers: department title → 112 cut, franchise → 72, spread → 112 (long → 72), the department quote → Newsreader italic, and `DisplaySemiBold` → Fraunces 600.
- The Anime and franchise page tests check that their heads are Geist 600.
- Totals: designsystem 27, ui-tv 612, ui-mobile 395, all passing. `lintDebug` is clean of new issues on ui-tv, ui-mobile and designsystem.

**Left for later.**
- **Phone figures at 400.** The phone needs its own call-site work before `CatalogueType` can carry the weight. Its web-500 sites rely on the inherited Medium: `CastPanel` person name (`.person-name` 500), `SettingsIndex` tally (`.rail-masthead` 500) and `AchievementItems` "Next" (`.stats-achievements h3` 500). About ten more of the 34 sites still need checking against the web. feat/motion is rewriting ui-mobile. Once those sites set Medium, move `FontWeight.Normal` from `TvType` into `CatalogueType`.
- **A stale phone comment.** `DeptHeroText.kt:161-167` still says the italic is synthetic. feat/motion moves that quote to `DeptQuote.kt:88`, so drop the comment there after the merge rather than conflict with it now.
- **Search "why" size.** The web's `.badge` is 12px. The TV still draws it at `bodySmall`, 13, because this batch kept every size.
- **Not done:** opsz drift on small text, a separate shelf-head 600 cut (the nearest shipping cut is kept, per the ruling) and latin-ext coverage.
- **Box check.** The spread title at the 112 cut (web opsz 92) wants a look on the box.
