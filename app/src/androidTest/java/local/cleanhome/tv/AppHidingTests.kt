package local.cleanhome.tv

import android.app.AlertDialog
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class AppHidingTests : InstrumentationTestCase() {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(300) }
    fun testHideAndUnhideFavouriteWithoutChangingPackageState() {
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        val prefs = home.getSharedPreferences("home", 0)
        val original = prefs.getStringSet("hidden_apps", emptySet())!!.toSet()
        val favourites = prefs.getString("favourites", null)
        fun active() = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
        fun choose(index: Int) {
            main { val list = active().listView; list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index)) }
            settle()
        }
        try {
            main {
                descendants(home.window.decorView).filterIsInstance<TextView>().first { it.text == "All Apps" }.performClick()
            }
            settle()
            val card = descendants(active().window!!.decorView).filterIsInstance<AppCard>().first { it.tag == "org.videolan.vlc" }
            val id = card.tag.toString()
            val enabledBefore = home.packageManager.getApplicationEnabledSetting(id)
            main { card.performLongClick() }; settle(); choose(1)
            main {
                assertFalse(descendants(active().window!!.decorView).any { it is AppCard && it.tag == id })
                val dock = HomeActivity::class.java.getDeclaredField("dock").apply { isAccessible = true }.get(home) as ViewGroup
                assertFalse(descendants(dock).any { it is AppCard && it.tag == id })
                assertTrue(prefs.getStringSet("hidden_apps", emptySet())!!.contains(id))
                assertEquals(enabledBefore, home.packageManager.getApplicationEnabledSetting(id))
                assertEquals(favourites, prefs.getString("favourites", null))
                active().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            }
            settle()
            val list = active().listView
            val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString().contains("VLC") }
            choose(index)
            main {
                assertTrue(descendants(active().window!!.decorView).any { it is AppCard && it.tag == id })
                assertFalse(prefs.getStringSet("hidden_apps", emptySet())!!.contains(id))
            }
        } finally {
            prefs.edit().putStringSet("hidden_apps", original).commit()
            main { home.finish() }
        }
    }
}
