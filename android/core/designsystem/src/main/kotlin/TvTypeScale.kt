package designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One web CSS px on a television, in dp and sp alike. The TV reads the web as
 * a 1280px viewport laid across its 960dp, so a px is 960/1280 of a dp, and any
 * `vw` or `clamp()` the TV takes from the web is evaluated at 1280px. Text and
 * spacing share it, so the two stay in step; this is the one knob to retune
 * how large the whole television draws. The web sets no minimum size, so
 * nothing here floors a small one: a 10px count is 7.5sp.
 */
const val TvScale = 960f / 1280f

/** [this] web px, in sp on a television. */
val Number.webSp: TextUnit get() = (toFloat() * TvScale).sp

/** [this] web px, in dp on a television. */
val Number.webDp: Dp get() = (toFloat() * TvScale).dp

private fun TextUnit.onTv(): TextUnit = if (isSp) this * TvScale else this

/** [this] role at the television's scale: size, line height and sp tracking; `em` already follows the size. */
fun TextStyle.onTv(): TextStyle = copy(fontSize = fontSize.onTv(), lineHeight = lineHeight.onTv(), letterSpacing = letterSpacing.onTv())

/**
 * The catalogue's roles as a television sets them: [CatalogueType] and the
 * page roles beside it, each the web's px through [TvScale]. The phone keeps
 * [CatalogueType] itself.
 */
object TvType {
    val headlineSmall: TextStyle = CatalogueType.headlineSmall.onTv()
    val titleLarge: TextStyle = CatalogueType.titleLarge.onTv()
    val titleMedium: TextStyle = CatalogueType.titleMedium.onTv()
    val titleSmall: TextStyle = CatalogueType.titleSmall.onTv()
    val bodyLarge: TextStyle = CatalogueType.bodyLarge.onTv()
    val bodyMedium: TextStyle = CatalogueType.bodyMedium.onTv()
    val bodySmall: TextStyle = CatalogueType.bodySmall.onTv()
    val labelLarge: TextStyle = CatalogueType.labelLarge.onTv()
    val labelMedium: TextStyle = CatalogueType.labelMedium.onTv()
    val labelSmall: TextStyle = CatalogueType.labelSmall.onTv()
    val Eyebrow: TextStyle = designsystem.Eyebrow.onTv()
    val SectionHead: TextStyle = designsystem.SectionHead.onTv()
    val LedgerLabel: TextStyle = designsystem.LedgerLabel.onTv()
    val LedgerValue: TextStyle = designsystem.LedgerValue.onTv()
}

/**
 * Display type: the titles the web sizes with `vw` clamps. Evaluated at a
 * 1280px viewport those clamps run near their ceilings, and a department
 * title would stand at 81.6sp, a third larger than the 60sp it had where
 * the rest of the text became a quarter smaller. So display keeps its
 * desktop proportions to the text, the web's 1920px size halved, and takes
 * the same [TvScale]: 45sp for a department title.
 */
object TvTypeScale {
    /** `.dept-title` 7.5rem (`departments.css:32`), also the Settings and System page title (`settings-page.js:55`). */
    val DepartmentTitle: TextUnit = (120 / 2).webSp

    /** `.franchise-hero .dept-title` 5rem (`departments.css:108`). */
    val FranchiseTitle: TextUnit = (80 / 2).webSp

    /** `.cover-title` 7rem (`home.css:50`). */
    val CoverTitle: TextUnit = (112 / 2).webSp

    /** `.cover-title.long` 4.6rem (`home.css:56`). */
    val CoverTitleLong: TextUnit = (73.6 / 2).webSp

    /** `.spread-title` 5.75rem (`title-page.css:89`). */
    val SpreadTitle: TextUnit = (92 / 2).webSp

    /** `.spread-title.long` 4.25rem (`title-page.css:93`). */
    val SpreadTitleLong: TextUnit = (68 / 2).webSp

    /** `.shelf-head h1` 3.6rem (`catalog.css:7`). */
    val ShelfHead: TextUnit = (57.6 / 2).webSp
}
