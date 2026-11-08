package local.cleanhome.tv

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

class GlassFlowTests : InstrumentationTestCase() {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(400) }
    private fun shot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    fun testLargeTwelveHourClockAndCenteredControls() {
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        settle()
        try {
            main {
                val texts = descendants(home.window.decorView).filterIsInstance<TextView>()
                val clock = HomeActivity::class.java.getDeclaredField("clock").apply { isAccessible = true }.get(home) as TextView
                val date = HomeActivity::class.java.getDeclaredField("date").apply { isAccessible = true }.get(home) as TextView
                assertTrue("Clock uses 12-hour format with period: ${clock.text}", clock.text.toString().matches(Regex("[0-9]{1,2}:[0-9]{2}\\s.+")))
                assertTrue(clock.textSize >= 60 * home.resources.displayMetrics.scaledDensity)
                assertTrue(date.textSize >= 22 * home.resources.displayMetrics.scaledDensity)
                assertFalse(texts.any { it.text.toString() in listOf("Apps", "Hold OK to rearrange", "Wallpaper") })
                assertTrue(descendants(home.window.decorView).first { it.contentDescription == "Wallpaper settings" } is ImageButton)
                val first = texts.first { it.text.toString() == "All Apps" }
                val last = texts.first { it.text.toString() == "Settings" }
                val left = Rect(); val right = Rect()
                first.getGlobalVisibleRect(left);last.getGlobalVisibleRect(right)
                assertEquals(home.resources.displayMetrics.widthPixels / 2f, (left.left + right.right) / 2f, 6f)
                assertNull("Navigation is text-only without a button surface", first.background)
                assertFalse(first is android.widget.Button)
                assertTrue(first.textSize <= 14.5f * home.resources.displayMetrics.scaledDensity)
                first.requestFocus()
                assertTrue(first.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG != 0)
                last.requestFocus()
                assertTrue(last.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG != 0)
                assertEquals(0, first.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG)
            }
            settle()
            shot("glass-home.png")
        } finally { main { home.finish() } }
    }
    fun testAllAppsFocusRimUnclippedAtTopAndBottom() {
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        settle()
        try {
            main { descendants(home.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == "All Apps" }.performClick() }
            settle()
            val dialog = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
            val cards = descendants(dialog.window!!.decorView).filterIsInstance<AppCard>()
            assertTrue(cards.size >= 4)
            fun check(card: AppCard) {
                main { card.requestFocus() };settle()
                main {
                    val bounds=Rect();assertTrue(card.surface.getGlobalVisibleRect(bounds))
                    assertTrue("Complete focused rim remains visible: $bounds", bounds.height() >= card.surface.height * card.surface.scaleY - 2)
                    assertTrue(bounds.width() >= card.surface.width * card.surface.scaleX - 2)
                }
            }
            check(cards.first());shot("glass-all-apps-top.png")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);settle()
            main { assertTrue("D-pad reaches adjacent app", cards[1].hasFocus()) }
            check(cards.last());shot("glass-all-apps-bottom.png")
        } finally { main { home.finish() } }
    }
    fun testAllAppsFavouriteStarsUpdateImmediatelyAndStayOffDock() {
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        settle()
        val prefs = home.getSharedPreferences("home", android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString("favourites", null)
        try {
            main {
                val dock = HomeActivity::class.java.getDeclaredField("dock").apply { isAccessible = true }.get(home) as ViewGroup
                assertFalse(descendants(dock).any { it.contentDescription == "Favourite" && it.visibility == View.VISIBLE })
                descendants(home.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == "All Apps" }.performClick()
            }
            settle()
            val dialog = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
            val cards = descendants(dialog.window!!.decorView).filterIsInstance<AppCard>()
            val before = saved?.split(',') ?: listOf("org.smarttube.stable", "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "org.mozilla.firefox")
            fun marked(card: AppCard) = descendants(card.surface).any { it.contentDescription == "Favourite" && it.visibility == View.VISIBLE }
            main {
                assertTrue("All Apps favourites have a visible star", cards.any { marked(it) })
                cards.forEach { card -> assertEquals(card.tag in before, marked(card)) }
            }
            val favourite = cards.first { marked(it) }
            val id = favourite.tag
            main { favourite.requestFocus(); favourite.performLongClick() }
            settle()
            fun active() = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
            fun toggle() { main { val list = active().listView; list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)) }; settle() }
            toggle()
            val removed = descendants(active().window!!.decorView).filterIsInstance<AppCard>().first { it.tag == id }
            main { assertFalse("Removing updates the reopened grid", marked(removed)); assertTrue(removed.hasFocus()); removed.performLongClick() }
            settle(); toggle()
            val added = descendants(active().window!!.decorView).filterIsInstance<AppCard>().first { it.tag == id }
            main { assertTrue("Adding updates the star", marked(added)); assertTrue(added.hasFocus()) }
            settle(); shot("all-apps-favourite-stars.png")
        } finally {
            main {
                prefs.edit().apply { if (saved == null) remove("favourites") else putString("favourites", saved) }.commit()
                home.finish()
            }
        }
    }

}
