package com.mediagram.spieltisch

import uniffi.spieltisch_core.MediaImageKind
import uniffi.spieltisch_core.MediaImages
import java.io.File

/** A night's images: article photos from the home cache server, every other image from the media app. */
class NightImages(
    private val media: MediaImages,
    private val articles: CacheServerArticles,
    /** Where article photos are kept once fetched. */
    private val articleDir: File,
) : MediaImages {
    override fun fetch(
        key: String,
        kind: MediaImageKind,
    ): String? = if (kind == MediaImageKind.ARTICLE) articles.photo(key, articleDir) else media.fetch(key, kind)
}
