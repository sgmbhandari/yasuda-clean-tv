package local.cleanhome.tv

import android.graphics.Bitmap
import android.graphics.Color
import android.test.InstrumentationTestCase

class WallpaperRenderTests : InstrumentationTestCase() {
    fun testScreenSizedOpaqueArtworkWithBakedShade() {
        val original = Bitmap.createBitmap(64, 36, Bitmap.Config.RGB_565).apply { eraseColor(Color.WHITE) }
        val prepared = WallpaperRenderer.prepare(original)
        try {
            assertTrue("Decoded input is released after composition", original.isRecycled)
            assertEquals(Bitmap.Config.ARGB_8888, prepared.config)
            assertEquals(1920, prepared.width)
            assertEquals(1080, prepared.height)
            assertFalse(prepared.hasAlpha())
            assertTrue("Smooth shade retains more than 128 distinct tones", (0 until prepared.height).map { prepared.getPixel(960, it) }.toSet().size > 128)
            val blurred = GlassBackdrop.create(prepared)
            assertEquals(240, blurred.width)
            assertEquals(135, blurred.height)
            assertFalse(blurred.hasAlpha())
            assertFalse("Glass blur must preserve the displayed image", prepared.isRecycled)
            blurred.recycle()
            assertTrue(Color.red(prepared.getPixel(960, 1000)) < Color.red(prepared.getPixel(960, 360)))
        } finally { prepared.recycle() }
    }
}
