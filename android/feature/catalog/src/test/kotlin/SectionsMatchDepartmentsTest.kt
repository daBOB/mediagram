package catalog

import model.Kind
import model.SECTIONS
import model.sectionKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionsMatchDepartmentsTest {
    @Test
    fun theStoredKeysAndLabelsAreTheDepartmentsInOrder() {
        assertEquals(
            Department.entries.map { it.name.lowercase() to it.label },
            SECTIONS.map { it.key to it.label },
        )
    }

    @Test
    fun aSetsSectionIsTheDepartmentItsShelfIsIn() {
        val sets =
            listOf(Kind.MOVIE, Kind.EPISODE, Kind.DOCUMENTARY, Kind.TUTORIAL, Kind.DOCUMENT)
                .flatMap { kind -> listOf(fakeSet(kind, "a-$kind"), fakeSet(kind, "b-$kind").copy(anime = true)) }
        sets.forEach { set ->
            // An anime documentary is on no shelf at all; there is nothing to agree with.
            val shelf = shelvesOf(listOf(set)).singleOrNull { it.entries.isNotEmpty() } ?: return@forEach
            assertEquals(shelf.department.name.lowercase(), set.sectionKey(), set.setId)
        }
    }
}
