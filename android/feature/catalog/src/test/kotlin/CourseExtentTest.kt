package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals

/** `courseExtentOf`: a course page head's count, in the words `course-view.js`'s `extentOf` uses. */
class CourseExtentTest {
    @Test
    fun countsLessonsAtEveryDepthAndSpellsThem() {
        val course = listOf(Division("1. Basics", null, listOf(set("a", Kind.TUTORIAL)), listOf(Division("Deeper", null, listOf(set("b", Kind.TUTORIAL), set("c", Kind.TUTORIAL)), emptyList()))))

        assertEquals("three lessons", courseExtentOf(course))
    }

    @Test
    fun documentsAreCountedApartAndOnlyWhenThereAreAny() {
        val course = listOf(Division("Basics", null, listOf(set("a", Kind.TUTORIAL), set("w", Kind.DOCUMENT)), emptyList()))

        assertEquals("one lesson · one document", courseExtentOf(course))
    }

    /** A folder of nothing but workbooks says so, rather than leaving the documents out. */
    @Test
    fun aFolderOfOnlyDocumentsStillSaysBoth() {
        val course = listOf(Division("Handouts", null, listOf(set("w", Kind.DOCUMENT), set("x", Kind.DOCUMENT)), emptyList()))

        assertEquals("zero lessons · two documents", courseExtentOf(course))
    }

    @Test
    fun aDocumentaryCollectionCountsDocumentaries() {
        val docs = listOf(Division("Nature", null, (1..22).map { set("d$it", Kind.DOCUMENTARY) }, emptyList()))

        assertEquals("22 documentaries", courseExtentOf(docs))
    }

    private fun set(
        id: String,
        kind: Kind,
    ) = MediaSet(
        setId = id,
        kind = kind,
        title = id,
        show = "Course",
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
}
