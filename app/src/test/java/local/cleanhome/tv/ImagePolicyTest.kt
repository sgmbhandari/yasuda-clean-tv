package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test

class ImagePolicyTest {
    @Test fun rejectsEmptyOversizedTinyAndDecompressionBombImages() {
        assertFalse(ImagePolicy.valid(0, 1920, 1080))
        assertFalse(ImagePolicy.valid(ImagePolicy.MAX_DOWNLOAD + 1, 1920, 1080))
        assertFalse(ImagePolicy.valid(400, 1, 1))
        assertFalse(ImagePolicy.valid(1000, 100000, 100000))
        assertTrue(ImagePolicy.valid(700000, 1920, 1080))
    }
    @Test fun acceptsWideAndTallBundledPhotosWithinBounds() {
        assertTrue(ImagePolicy.valid(900000, 1920, 757))
        assertTrue(ImagePolicy.valid(900000, 1920, 1280))
        assertFalse(ImagePolicy.valid(900000, 639, 1080))
    }
}
