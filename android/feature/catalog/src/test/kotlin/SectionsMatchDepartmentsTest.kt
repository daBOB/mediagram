package catalog

import model.SECTIONS
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
}
