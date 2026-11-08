package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class FocusMemoryTest {
    @Test fun returnUsesLastFocusedAppAndSurvivesReordering() {
        val memory = FocusMemory("vlc")
        assertEquals("vlc", memory.resolve(listOf("tube", "prime", "vlc")))
        assertEquals("vlc", memory.resolve(listOf("vlc", "tube", "prime")))
    }
    @Test fun missingFavouriteFallsBackAndEmptyDockIsSafe() {
        val memory = FocusMemory("removed")
        assertEquals("tube", memory.resolve(listOf("tube", "vlc")))
        assertNull(memory.resolve(emptyList()))
    }
}
