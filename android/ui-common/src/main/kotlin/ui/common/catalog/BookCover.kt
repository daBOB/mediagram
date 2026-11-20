package ui.common.catalog

import androidx.compose.ui.layout.ContentScale
import model.Kind
import model.MediaSet

/**
 * A book's cover is square and is the whole picture, so it is shown whole,
 * never cropped — the web's `.thumb.square` plate and `.cover-whole` resume art.
 */
val MediaSet.coverIsWhole: Boolean get() = kind == Kind.AUDIOBOOK

/** How a card fills its frame with this set's art: a book's cover contained, anything else cropped to fill. */
val MediaSet.artScale: ContentScale get() = if (coverIsWhole) ContentScale.Fit else ContentScale.Crop
