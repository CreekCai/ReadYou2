package me.ash.reader.ui.page.home.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeSuggestionSelectionTest {
    @Test
    fun `filters full pool before taking three suggestions`() {
        val suggestions = listOf(
            "too long one",
            "too long two",
            "too long three",
            "fits one",
            "fits two",
            "fits three",
            "fits four",
        )

        assertEquals(
            listOf("fits one", "fits two", "fits three"),
            selectVisibleSuggestions(suggestions) { it.startsWith("fits") },
        )
    }

    @Test
    fun `returns empty list when every suggestion overflows`() {
        assertEquals(
            emptyList<String>(),
            selectVisibleSuggestions(listOf("one", "two")) { false },
        )
    }
}
