package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    @Test fun refreshAfterTwoDaysOrClockRewindButRespectRetryAndVisibility() {
        val selected = 10_000L
        assertFalse(RefreshPolicy.isDue(selected, selected + 48 * 60 * 60 * 1000L - 1, true, 500, 0))
        assertTrue(RefreshPolicy.isDue(selected, selected + 48 * 60 * 60 * 1000L, true, 500, 0))
        assertTrue(RefreshPolicy.isDue(selected, selected - 1, true, 500, 0))
        assertTrue(RefreshPolicy.isDue(0, selected, true, 500, 0))
        assertFalse(RefreshPolicy.isDue(0, selected, false, 500, 0))
        assertFalse(RefreshPolicy.isDue(0, selected, true, 500, 501))
    }
    @Test fun acceptOnlyHttpsCommonsEndpointsWithoutCredentialsOrExtraPorts() {
        assertTrue(RefreshPolicy.allowedUrl("https://commons.wikimedia.org/w/api.php?action=query"))
        assertTrue(RefreshPolicy.allowedUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/photo.jpg/1920px-photo.jpg"))
        assertTrue(RefreshPolicy.allowedUrl("https://upload.wikimedia.org/wikipedia/commons/a/ab/photo.jpg"))
        listOf("http://upload.wikimedia.org/a.jpg", "https://evil.example/a.jpg", "https://upload.wikimedia.org.evil/a", "https://user@commons.wikimedia.org/w/api.php", "https://upload.wikimedia.org:444/a.jpg", "https://commons.wikimedia.org/elsewhere").forEach { assertFalse(it, RefreshPolicy.allowedUrl(it)) }
    }
}
