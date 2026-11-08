package local.cleanhome.tv

import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.Gravity
import android.view.WindowManager

class InputBannerTests : InstrumentationTestCase() {
    fun testInputLabelIsTopLeftNonInteractiveAndExpires() {
        val context = instrumentation.targetContext
        assertTrue(android.provider.Settings.canDrawOverlays(context))
        var banner: InputBanner? = null
        try {
            instrumentation.runOnMainSync { banner = InputBanner(context); banner!!.show("HDMI 1") }
            SystemClock.sleep(500)
            instrumentation.runOnMainSync {
                val label = banner!!.view ?: throw AssertionError("Input label missing")
                assertEquals("HDMI 1", label.text.toString())
                val params = label.layoutParams as WindowManager.LayoutParams
                assertEquals(Gravity.TOP or Gravity.START, params.gravity)
                assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            }
            SystemClock.sleep(4700)
            instrumentation.runOnMainSync { assertNull("Input label expires after five seconds", banner!!.view) }
        } finally { instrumentation.runOnMainSync { banner?.close() } }
    }
}
