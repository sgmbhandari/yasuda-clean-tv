package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class AppVisibilityTest {
    @Test fun hidingFiltersGridAndFavouritesWithoutRemovingBookmarks() {
        val installed = listOf("a", "b", "c")
        val saved = listOf("missing", "b", "a")
        assertEquals(listOf("a", "c"), AppVisibility.visible(installed, setOf("b", "missing")))
        assertEquals(listOf("a"), Favourites.visible(saved, AppVisibility.visible(installed, setOf("b")).toSet()))
        assertEquals(listOf("b", "a"), Favourites.visible(saved, AppVisibility.visible(installed, emptySet()).toSet()))
        assertEquals(listOf("missing", "b", "a"), saved)
    }
    @Test fun allHiddenAndMissingAppsRemainRecoverable() {
        assertTrue(AppVisibility.visible(listOf("a"), setOf("a", "missing")).isEmpty())
        assertEquals(listOf("a"), AppVisibility.visible(listOf("a"), setOf("missing")))
    }
}
