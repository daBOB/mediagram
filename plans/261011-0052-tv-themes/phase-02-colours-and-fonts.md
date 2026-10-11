# Phase 02 — Colours and fonts reach every TV screen

## Context
- Spec: § One look per theme (ground, neutrals, fonts).
- ui-tv reads `Palette.*` directly 65 times (Text 20, Figures 11, Sunk 10, Ground 8, RuleStrong 5, Page 4, Rule 3, Sage 2, Ochre 2), so a style's neutrals never reach those spots.
- `TvType` (`core/designsystem/src/main/kotlin/TvTypeScale.kt:41-56`) is a static object read 146 times; `FontFamily.Resolver` is sealed, so the face has to change where the roles are read. 16 top-level styles in ui-tv are built from `TvType` (listed in Task 2.2).
- `Type.kt`'s top note: variable axes away from a font's default instance went unhonoured on-device, so every weight away from the default ships as a static cut.

## Task 2.1: TV colours read from the scheme

**Files:**
- Modify: every file under `ui-tv/src/main/kotlin` that reads `Palette.` except `ui/tv/TvTheme.kt` and `ui/tv/TvLook.kt`. List them with
  `git grep -l "Palette\." -- ui-tv/src/main ':!ui-tv/src/main/kotlin/ui/tv/TvTheme.kt' ':!ui-tv/src/main/kotlin/ui/tv/TvLook.kt'`
- Test: `ui-tv/src/test/kotlin/ui/tv/TvColoursComeFromTheSchemeTest.kt` (create)

**Interfaces:**
- Consumes: `tvColorScheme(accent, neutrals)` (1.2) — it fills each slot below from the style's neutrals.
- Produces: nothing new; every TV colour now follows the style.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui.tv

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every TV colour comes from the scheme, so a style's neutrals reach it:
 * only the theme and the looks read [designsystem.Palette] directly. A
 * direct read elsewhere keeps mediagram's ink under every style.
 */
class TvColoursComeFromTheSchemeTest {
    @Test
    fun onlyTheThemeAndTheLooksReadThePalette() {
        val readers =
            File("src/main/kotlin")
                .walk()
                .filter { it.extension == "kt" && it.name !in setOf("TvTheme.kt", "TvLook.kt") }
                .filter { file ->
                    file.readLines().any { line ->
                        val code = line.trimStart()
                        "Palette." in code && !code.startsWith("*") && !code.startsWith("//")
                    }
                }.map { it.name }
                .sorted()
                .toList()
        assertEquals(emptyList(), readers)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest --tests 'ui.tv.TvColoursComeFromTheSchemeTest'`
Expected: FAIL, listing the files that still read `Palette.`.

- [ ] **Step 3: Replace each read with its scheme slot**

The slots are exactly the ones `tvColorScheme` (`TvTheme.kt:41-72`) fills from each role, so mediagram's colours are unchanged:

| `Palette.` | `MaterialTheme.colorScheme.` |
|---|---|
| `Ground` | `background` |
| `Text` | `onSurface` |
| `Figures` | `onSurfaceVariant` |
| `Sunk` | `surfaceVariant` |
| `Page` | `surface` |
| `RuleStrong` | `border` |
| `Rule` | `borderVariant` |
| `Sage` | `tertiary` |
| `Ochre` | `error` |

`MaterialTheme` is `androidx.tv.material3.MaterialTheme` (already imported in most of these files). Where a read sits outside composition — inside `drawBehind`/`Canvas`/`drawWithContent` lambdas, a `remember { }` block or a top-level `val` — the compiler reports "@Composable invocations can only happen from the context of a @Composable function":
- in a draw lambda or `remember`: read the slot into a `val` in the enclosing composable just above it, and use that `val` inside;
- in a top-level `val`: make it a getter, e.g. `private val Fill: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
Remove each `import designsystem.Palette` left unused. Comments that name a palette role (`[Palette]`, "Palette roles") stay.

- [ ] **Step 4: Run the whole ui-tv suite**

Run: `cd android && ./gradlew :ui-tv:testDebugUnitTest`
Expected: PASS — the new test, and every existing one (the slots hold the same colours under mediagram).

- [ ] **Step 5: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add ui-tv/src
git commit -m "refactor(tv): every TV colour reads the theme's scheme"
```

## Task 2.2: Each style's face, through `TvType`

**Files:**
- Create: `core/designsystem/src/main/res/font/geist_700.ttf`, `google_sans_flex_400.ttf`, `google_sans_flex_500.ttf`, `google_sans_flex_600.ttf`, `google_sans_flex_700.ttf`
- Create: `core/designsystem/licenses/OFL-google-sans-flex.txt`; modify `core/designsystem/licenses/README.md`
- Create: `core/designsystem/src/main/kotlin/TvStyleType.kt`
- Modify: `core/designsystem/src/main/kotlin/Type.kt:65` (`interfaceFont` private → internal), `core/designsystem/src/main/kotlin/TvTypeScale.kt:31-56`
- Modify: `ui-tv/src/main/kotlin/ui/tv/TvTheme.kt:74-94,117-121` and the 16 top-level styles below; every ui-tv use of `CoverTitle`, `PageTitle`, `PageTitleCompact`
- Test: `core/designsystem/src/test/kotlin/TvStyleTypeTest.kt` (create), `core/designsystem/src/test/kotlin/TvTypeScaleTest.kt`, `ui-tv/src/test/kotlin/ui/tv/TvThemeTest.kt`, and any ui-tv test the compiler names

**Interfaces:**
- Consumes: `TvStyle`, `LocalTvStyle` (1.1).
- Produces: `TvType.Base` (today's roles, plain values); `TvType.<role>` as `@Composable @ReadOnlyComposable` getters; `fun TextStyle.inStyle(style: TvStyle): TextStyle`; `@Composable @ReadOnlyComposable fun TextStyle.inTvStyle(): TextStyle`; `internal val AppleTvFamily`, `internal val GoogleTvFamily`.

- [ ] **Step 1: Cut the fonts**

```bash
cd android/core/designsystem
CUTS=$(mktemp -d)
curl -fsSL -o "$CUTS/gsf.ttf" "https://raw.githubusercontent.com/google/fonts/main/ofl/googlesansflex/GoogleSansFlex%5BROND%2Copsz%2Cslnt%2Cwdth%2Cwght%5D.ttf"
curl -fsSL -o licenses/OFL-google-sans-flex.txt "https://raw.githubusercontent.com/google/fonts/main/ofl/googlesansflex/OFL.txt"
LATIN="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"
for w in 400 500 600 700; do
  fonttools varLib.instancer "$CUTS/gsf.ttf" wght=$w --static -o "$CUTS/gsf-$w.ttf"
  pyftsubset "$CUTS/gsf-$w.ttf" --unicodes="$LATIN" --layout-features='*' --output-file="src/main/res/font/google_sans_flex_$w.ttf"
done
fonttools varLib.instancer src/main/res/font/geist.ttf wght=700 --static -o src/main/res/font/geist_700.ttf
ls -l src/main/res/font/geist_700.ttf src/main/res/font/google_sans_flex_*.ttf
python3 -c "from fontTools.ttLib import TTFont; import glob; [print(f, 'fvar' in TTFont(f)) for f in sorted(glob.glob('src/main/res/font/google_sans_flex_*.ttf'))+['src/main/res/font/geist_700.ttf']]"
```

Expected: five files, each under 150 KB, each printing `False` (static, no `fvar`). `geist.ttf` is already the Latin cut, so its bold needs no subsetting.

Append to `licenses/README.md`:

```markdown
## Faces the TV styles add

`res/font/geist_700.ttf` is Geist pinned at weight 700, the bold Apple TV
style's cover title asks for; `res/font/google_sans_flex_{400,500,600,700}.ttf`
are Google Sans Flex (OFL 1.1, `OFL-google-sans-flex.txt`; no Reserved Font
Name) pinned at each weight the TV asks for and cut to the same Latin range as
the web's faces. Static, not variable: see `Type.kt`'s top note. Regenerate
with the commands in `plans/261011-0052-tv-themes/phase-02-colours-and-fonts.md`
(Task 2.2, Step 1). "Google Sans" is Google's trademark; the app names the
style "Google TV style" and never the font.
```

- [ ] **Step 2: Write the failing test**

Create `core/designsystem/src/test/kotlin/TvStyleTypeTest.kt`:

```kotlin
package designsystem

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextStyle
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** A TV role read under a style is the same role — size, weight, spacing — in that style's one face; mediagram's is untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvStyleTypeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null

    @After
    fun close() {
        compose.runOnUiThread { controller?.close() }
        controller = null
    }

    private fun read(
        style: TvStyle,
        role: @Composable () -> TextStyle,
    ): TextStyle {
        var read: TextStyle? = null
        compose.runOnUiThread {
            val built = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller = built
            built.get().setContent { CompositionLocalProvider(LocalTvStyle provides style) { read = role() } }
        }
        compose.waitForIdle()
        close()
        return checkNotNull(read)
    }

    @Test
    fun mediagramKeepsEveryRolesOwnFace() {
        assertEquals(TvType.Base.bodyLarge, read(TvStyle.MEDIAGRAM) { TvType.bodyLarge })
        assertEquals(TvType.Base.labelLarge, read(TvStyle.MEDIAGRAM) { TvType.labelLarge })
        assertEquals(CoverTitle, read(TvStyle.MEDIAGRAM) { CoverTitle.inTvStyle() })
    }

    @Test
    fun appleSetsEachRoleInGeistAtItsOwnSizeAndWeight() {
        val body = read(TvStyle.APPLE) { TvType.bodyLarge }
        assertEquals(AppleTvFamily, body.fontFamily)
        assertEquals(TvType.Base.bodyLarge.fontSize, body.fontSize)
        assertEquals(TvType.Base.bodyLarge.fontWeight, body.fontWeight)
    }

    @Test
    fun googleSetsTitlesAndRolesInGoogleSansFlex() {
        assertEquals(GoogleTvFamily, read(TvStyle.GOOGLE) { CoverTitle.inTvStyle() }.fontFamily)
        assertEquals(GoogleTvFamily, read(TvStyle.GOOGLE) { TvType.Eyebrow }.fontFamily)
    }
}
```

In `TvTypeScaleTest.kt`, change every `TvType.` to `TvType.Base.` (it reads the roles outside composition).

- [ ] **Step 3: Run to see it fail**

Run: `cd android && ./gradlew :core:designsystem:testDebugUnitTest --tests 'designsystem.TvStyleTypeTest' --tests 'designsystem.TvTypeScaleTest'`
Expected: compilation fails — `TvType.Base`, `inTvStyle`, `AppleTvFamily`, `GoogleTvFamily` are unresolved.

- [ ] **Step 4: Implement in designsystem**

In `Type.kt`, `private fun interfaceFont(weight: Int)` becomes `internal fun interfaceFont(weight: Int)`.

Create `TvStyleType.kt`:

```kotlin
package designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mediagram.android.core.designsystem.R

/**
 * Geist, the whole of Apple TV style's type: Apple's own face is not
 * licensed off Apple's devices, and Geist is cut in the same grotesk
 * manner. 400–600 come from the variable file, which reads true at those
 * weights on the box; the cover title's bold is a static cut, for the
 * reason `Type.kt`'s top note gives.
 */
internal val AppleTvFamily =
    FontFamily(interfaceFont(400), interfaceFont(500), interfaceFont(600), Font(resId = R.font.geist_700, weight = FontWeight.Bold))

/**
 * Google Sans Flex, the whole of Google TV style's type — Google's own
 * face, under the OFL since 2025-11. One static cut per weight the TV asks
 * for, for the same on-device reason.
 */
internal val GoogleTvFamily =
    FontFamily(
        Font(resId = R.font.google_sans_flex_400, weight = FontWeight.Normal),
        Font(resId = R.font.google_sans_flex_500, weight = FontWeight.Medium),
        Font(resId = R.font.google_sans_flex_600, weight = FontWeight.SemiBold),
        Font(resId = R.font.google_sans_flex_700, weight = FontWeight.Bold),
    )

/** The one face [style] sets every TV word in, or null where each role keeps its own (mediagram's three). */
private fun familyOf(style: TvStyle): FontFamily? =
    when (style) {
        TvStyle.MEDIAGRAM -> null
        TvStyle.APPLE -> AppleTvFamily
        TvStyle.GOOGLE -> GoogleTvFamily
    }

/** [this] role in [style]'s face; its size, weight and spacing stay its own. Unchanged for mediagram. */
fun TextStyle.inStyle(style: TvStyle): TextStyle = familyOf(style)?.let { copy(fontFamily = it) } ?: this

/** [inStyle] for the style the TV draws in now — [LocalTvStyle]. */
@Composable
@ReadOnlyComposable
fun TextStyle.inTvStyle(): TextStyle = inStyle(LocalTvStyle.current)
```

In `TvTypeScale.kt`, `object TvType` becomes (imports `androidx.compose.runtime.Composable`, `androidx.compose.runtime.ReadOnlyComposable`):

```kotlin
object TvType {
    /**
     * The roles exactly as mediagram sets them — the sizes, weights and
     * faces every style starts from. Read here wherever no composition is
     * running (a test, the theme's typography); a screen reads the getters
     * below, which set the same role in the TV's chosen style.
     */
    object Base {
        val headlineSmall: TextStyle = CatalogueType.headlineSmall.onTv()
        val titleLarge: TextStyle = CatalogueType.titleLarge.onTv()
        val titleMedium: TextStyle = CatalogueType.titleMedium.onTv()
        val titleSmall: TextStyle = CatalogueType.titleSmall.onTv()
        val bodyLarge: TextStyle = CatalogueType.bodyLarge.onTv()
        val bodyMedium: TextStyle = CatalogueType.bodyMedium.onTv()
        val bodySmall: TextStyle = CatalogueType.bodySmall.onTv()
        val labelLarge: TextStyle = CatalogueType.labelLarge.onTv()
        val labelMedium: TextStyle = CatalogueType.labelMedium.onTv().copy(fontWeight = FontWeight.Normal)
        val labelSmall: TextStyle = CatalogueType.labelSmall.onTv().copy(fontWeight = FontWeight.Normal)
        val Eyebrow: TextStyle = designsystem.Eyebrow.onTv()
        val SectionHead: TextStyle = designsystem.SectionHead.onTv()
        val LedgerLabel: TextStyle = designsystem.LedgerLabel.onTv()
        val LedgerValue: TextStyle = designsystem.LedgerValue.onTv()
    }

    val headlineSmall: TextStyle @Composable @ReadOnlyComposable get() = Base.headlineSmall.inTvStyle()
    val titleLarge: TextStyle @Composable @ReadOnlyComposable get() = Base.titleLarge.inTvStyle()
    val titleMedium: TextStyle @Composable @ReadOnlyComposable get() = Base.titleMedium.inTvStyle()
    val titleSmall: TextStyle @Composable @ReadOnlyComposable get() = Base.titleSmall.inTvStyle()
    val bodyLarge: TextStyle @Composable @ReadOnlyComposable get() = Base.bodyLarge.inTvStyle()
    val bodyMedium: TextStyle @Composable @ReadOnlyComposable get() = Base.bodyMedium.inTvStyle()
    val bodySmall: TextStyle @Composable @ReadOnlyComposable get() = Base.bodySmall.inTvStyle()
    val labelLarge: TextStyle @Composable @ReadOnlyComposable get() = Base.labelLarge.inTvStyle()
    val labelMedium: TextStyle @Composable @ReadOnlyComposable get() = Base.labelMedium.inTvStyle()
    val labelSmall: TextStyle @Composable @ReadOnlyComposable get() = Base.labelSmall.inTvStyle()
    val Eyebrow: TextStyle @Composable @ReadOnlyComposable get() = Base.Eyebrow.inTvStyle()
    val SectionHead: TextStyle @Composable @ReadOnlyComposable get() = Base.SectionHead.inTvStyle()
    val LedgerLabel: TextStyle @Composable @ReadOnlyComposable get() = Base.LedgerLabel.inTvStyle()
    val LedgerValue: TextStyle @Composable @ReadOnlyComposable get() = Base.LedgerValue.inTvStyle()
}
```

Keep the object's existing KDoc and add one line: "Each role is read through the TV's style ([inTvStyle]); [Base] holds them as mediagram sets them."

Run the Step 3 command. Expected: PASS.

- [ ] **Step 5: Move ui-tv onto the getters**

In `TvTheme.kt`, replace `private val TvTypography = Typography().copy(...)` with a function of the style, and remember it in `TvTheme` (`typography = remember(style) { tvTypography(style) }`, import `androidx.compose.runtime.remember`, `designsystem.inStyle`):

```kotlin
private fun tvTypography(style: TvStyle): Typography =
    Typography().copy(
        headlineSmall = TvType.Base.headlineSmall.inStyle(style),
        titleLarge = TvType.Base.titleLarge.inStyle(style),
        titleMedium = TvType.Base.titleMedium.inStyle(style),
        titleSmall = TvType.Base.titleSmall.inStyle(style),
        bodyLarge = TvType.Base.bodyLarge.inStyle(style),
        bodyMedium = TvType.Base.bodyMedium.inStyle(style),
        bodySmall = TvType.Base.bodySmall.inStyle(style),
        labelLarge = TvType.Base.labelLarge.inStyle(style),
        labelMedium = TvType.Base.labelMedium.inStyle(style),
        labelSmall = TvType.Base.labelSmall.inStyle(style),
    )
```

These 16 top-level styles become getters — same right-hand side, `val X: TextStyle @Composable @ReadOnlyComposable get() = …` (import `androidx.compose.runtime.Composable`, `androidx.compose.runtime.ReadOnlyComposable`, `androidx.compose.ui.text.TextStyle` where missing):

| File | Styles |
|---|---|
| `ui/tv/TvTextField.kt:125` | `FieldText` |
| `ui/tv/catalog/TvDepartmentHero.kt:197` | `DeptLine` |
| `ui/tv/catalog/TvHeadings.kt:190-202` | `DisplaySemiBold`, `SeasonHeading`, `ShelfSub`, `RowHead` |
| `ui/tv/catalog/TvPlate.kt:171-174` | `PlateName`, `PlateMeta` |
| `ui/tv/catalog/TvStatsPage.kt:121` | `StatsTotal` |
| `ui/tv/catalog/home/TvContinueBand.kt:141` | `QuoteTagline` |
| `ui/tv/catalog/home/TvResumeCard.kt:177-178` | `ResumeName`, `ResumeSub` |
| `ui/tv/chrome/TvLibraryRail.kt:62` | `TvWordmark` |
| `ui/tv/profile/TvPinPrompt.kt:182` | `PinFigures` |
| `ui/tv/profile/TvProfileTiles.kt:144-147` | `WhoHeading`, `WhoNote` |

Then every ui-tv read of the three shared title faces takes the style: list them with
`git grep -nE "(^|[^.A-Za-z])(CoverTitle|PageTitle|PageTitleCompact)\b" -- ui-tv/src/main`
and append `.inTvStyle()` to the face at each read (e.g. `CoverTitle.copy(…)` → `CoverTitle.inTvStyle().copy(…)`, `style = PageTitleCompact` → `style = PageTitleCompact.inTvStyle()`), importing `designsystem.inTvStyle`. `TvTypeScale.CoverTitle` is a size and is not touched.

Compile: `cd android && ./gradlew :ui-tv:compileDebugUnitTestKotlin`. Any remaining "@Composable invocations can only happen…" error names a read outside composition: in main code, make that value a getter as above; in a test that compares against a role outside composition, read `TvType.Base.<role>` instead (e.g. `TvThemeTest`'s `TvType.titleLarge.fontSize` → `TvType.Base.titleLarge.fontSize`). Also check `git grep -n "TvType\." -- ui-common ui-mobile` prints nothing; if it does, the compiler has named those too.

- [ ] **Step 6: Pin the theme's slots per style**

In `TvThemeTest` (imports `androidx.compose.ui.text.TextStyle`, `designsystem.inStyle`), add:

```kotlin
    @Test
    fun theTypographySlotsTakeTheStylesFace() {
        var body: TextStyle? = null
        show(style = TvStyle.APPLE) { body = MaterialTheme.typography.bodyMedium }
        assertEquals(TvType.Base.bodyMedium.inStyle(TvStyle.APPLE), body)
    }
```

- [ ] **Step 7: Run everything touched**

Run: `cd android && ./gradlew :core:designsystem:testDebugUnitTest :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest`
Expected: PASS. `:ui-mobile` must pass untouched (the phone never reads `TvType`).

- [ ] **Step 8: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add core/designsystem ui-tv/src
git commit -m "feat(tv): each TV style sets its words in its own face"
```

## Success criteria
- No ui-tv file but the theme and the looks reads `Palette.` (pinned by a test).
- Under Apple and Google every TV role, and the three title faces, draw in Geist / Google Sans Flex at the role's own size and weight; mediagram's roles are equal to `TvType.Base`.
- Five static font cuts, Latin-only, licence beside them.
