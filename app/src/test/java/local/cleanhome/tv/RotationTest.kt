package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class RotationTest {
    private val entries = listOf("l1", "l2", "l3").map {
        Wallpaper(it, "landscape", it, "asset:$it", "Artist", "CC0", "https://example.org")
    }

    @Test fun cyclesThroughEveryLocalPhotoBeforeRepeating() {
        var state = RotationState()
        val rotation = Rotation(entries, Random(7))
        val ids = (1..3).map {
            val next = rotation.choose(state)
            state = rotation.commit(state, next, 1000L + it)
            next.id
        }
        assertEquals(3, ids.toSet().size)
        assertEquals(listOf("landscape", "landscape", "landscape"), ids.map { id -> entries.first { it.id == id }.group })
        assertNotEquals(ids.last(), rotation.choose(state).id)
    }

    @Test fun onlyCommitSuccessfulImageAndPreserveStateAcrossRecreation() {
        val rotation = Rotation(entries, Random(3))
        val state = RotationState()
        rotation.choose(state)
        assertEquals(emptyList<String>(), state.seen)
        val first = rotation.choose(state)
        val committed = rotation.commit(state, first, 50L)
        val recreated = committed.copy()
        assertNotEquals(first.id, rotation.choose(recreated).id)
        assertEquals(first.id, recreated.currentId)
        assertEquals(50L, recreated.changedAt)
    }

    @Test fun thirtyMinuteBoundaryPauseAndClockChanges() {
        val s = RotationState(currentId = "l1", changedAt = 10_000)
        assertFalse(Rotation.isDue(s, 1_809_999))
        assertTrue(Rotation.isDue(s, 1_810_000))
        assertTrue(Rotation.isDue(s, 9_000))
        assertFalse(Rotation.isDue(s.copy(paused = true), Long.MAX_VALUE))
        assertTrue(Rotation.isDue(RotationState(), 0))
    }

    @Test fun exhaustedCatalogueAvoidsImmediateRepeatAndFailedIdsCanBeSkipped() {
        val r = Rotation(entries, Random(4))
        val s = RotationState("l3", 100, listOf("l1", "l2", "l3"))
        assertEquals("l2", r.choose(s, setOf("l1")).id)
        try { r.choose(s, setOf("l1", "l2", "l3")); fail("No usable image remains") } catch (_: IllegalStateException) { }
    }

    @Test fun favouritesExcludeMissingAppsAndMoveWithoutDuplication() {
        assertEquals(listOf("b", "a"), Favourites.visible(listOf("b", "gone", "a"), setOf("a", "b")))
        assertEquals(listOf("b", "a", "c"), Favourites.move(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "b", "c"), Favourites.move(listOf("a", "b", "c"), "a", -1))
    }
    @Test fun reorderSkipsMissingAppsWithoutDeletingTheirSavedFavourite() {
        assertEquals(listOf("missing", "vlc", "smart", "firefox"), Favourites.move(listOf("smart", "missing", "vlc", "firefox"), "smart", 1, setOf("smart", "vlc", "firefox")))
    }

}
