package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Documentaries split the way Tutorials groups, and then split further: a
 * folder with a show name becomes a collection exactly like a course does,
 * and a documentary uploaded on its own — no show name to group it under —
 * stays a plain set for the department page's own row.
 */
class DocumentariesTest {
    private fun grouped(
        show: String,
        title: String,
    ) = set(show = show, title = title)

    private fun single(title: String) = set(show = null, title = title)

    private fun set(
        show: String?,
        title: String,
    ) = MediaSet(
        setId = "docu-$show-$title",
        kind = Kind.DOCUMENTARY,
        title = title,
        show = show,
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
    )

    /** A folder of three, and two standalone documentaries — the fixture every test here shares. */
    private val library = groupDocumentaries(
        listOf(
            grouped("Terra X", "Episode One"),
            grouped("Terra X", "Episode Two"),
            grouped("Terra X", "Episode Three"),
            single("Nomad"),
            single("Baraka"),
        ),
    )

    @Test
    fun aFolderOfDocumentariesGroupsByShowLikeACourse() {
        val collection = library.collections.single()
        assertEquals("Terra X", collection.name)
        assertEquals(3, collection.count)
        assertEquals(CollectionKind.COURSE, collection.kind)
    }

    @Test
    fun aStandaloneDocumentaryStaysAPlainSetSortedByTitle() {
        assertEquals(listOf("Baraka", "Nomad"), library.singles.map(MediaSet::title))
    }

    @Test
    fun countSumsEveryCollectionsItemsPlusEverySingle() {
        val entries: List<Entry> = library.collections + library.singles.map(Entry::Film)
        assertEquals(5, documentaryCountOf(entries))
    }

    @Test
    fun anEmptyLibraryGroupsAndCountsToNothing() {
        val empty = groupDocumentaries(emptyList())
        assertEquals(emptyList(), empty.collections)
        assertEquals(emptyList(), empty.singles)
        assertEquals(0, documentaryCountOf(emptyList()))
    }

    /**
     * A course and a documentary folder are free to share a name — nothing
     * about either one rules the other's name out. `CatalogUiState.collection`
     * resolves a key across every shelf, so the two must not resolve to the
     * same one: a saved COLLECTION frame, or a tap on either card, would
     * land on whichever shelf happens to be searched first.
     */
    @Test
    fun aFolderNeverSharesItsKeyWithACourseOfTheSameName() {
        val course = collections(
            listOf(
                MediaSet(
                    setId = "tut-1", kind = Kind.TUTORIAL, title = "Lesson", show = "Nature", chapter = null,
                    path = "Chapter 1", season = null, episodeFirst = null, episodeLast = null, year = null,
                    durationSecs = null, posterPath = null, totalBytes = 0,
                ),
            ),
            CollectionKind.COURSE,
            "",
        ).single()
        val folder = groupDocumentaries(listOf(grouped("Nature", "Part One"))).collections.single()

        assertEquals("COURSE/Nature", course.key)
        assertEquals("DOCUMENTARY/Nature", folder.key)
    }

    @Test
    fun aSetWithABlankShowIsStillASingle() {
        val library = groupDocumentaries(listOf(set(show = "  ", title = "Blank")))
        assertEquals(listOf("Blank"), library.singles.map(MediaSet::title))
        assertEquals(emptyList(), library.collections)
    }

    @Test
    fun aFolderMirrorsACoursesOwnChapterGrouping() {
        val course = collections(
            listOf(
                MediaSet(
                    setId = "tut-1", kind = Kind.TUTORIAL, title = "Lesson", show = "Course", chapter = null,
                    path = "Chapter 1", season = null, episodeFirst = null, episodeLast = null, year = null,
                    durationSecs = null, posterPath = null, totalBytes = 0,
                ),
            ),
            CollectionKind.COURSE,
            "",
        )
        val documentary = groupDocumentaries(
            listOf(
                MediaSet(
                    setId = "docu-1", kind = Kind.DOCUMENTARY, title = "Part", show = "Series", chapter = null,
                    path = "Chapter 1", season = null, episodeFirst = null, episodeLast = null, year = null,
                    durationSecs = null, posterPath = null, totalBytes = 0,
                ),
            ),
        )
        assertEquals(course.single().divisions.map { it.title }, documentary.collections.single().divisions.map { it.title })
        assertIs<Entry.Collection>(documentary.collections.single())
    }
}
