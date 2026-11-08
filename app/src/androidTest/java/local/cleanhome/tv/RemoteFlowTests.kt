package local.cleanhome.tv

import android.app.AlertDialog
import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Keep one instrumentation connection: CVTE intermittently kills standalone UIAutomator dumps. */
class RemoteFlowTests : InstrumentationTestCase() {
    private lateinit var home: HomeActivity
    private val report = mutableListOf<String>()

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(250) }
    private fun key(code: Int) { instrumentation.sendKeyDownUpSync(code); settle() }

    private fun openHome() {
        home = (instrumentation as CleanHomeTestRunner).openHome()
        settle()
    }

    private fun dialog(): AlertDialog {
        return HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as? AlertDialog
            ?: throw AssertionError("Expected a visible launcher dialog")
    }

    private fun items(): List<String> {
        var values: List<String> = emptyList()
        main { val adapter = dialog().listView.adapter; values = (0 until adapter.count).map { adapter.getItem(it).toString() } }
        return values
    }

    private fun choose(label: String) {
        val active = dialog()
        main {
            val list = active.listView
            val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == label }
            list.setSelection(index)
            assertTrue(list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index)))
        }
        settle()
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun click(text: String) {
        main {
            val view = descendants(home.window.decorView).first { it.contentDescription?.toString() == text || (it is TextView && it.text.toString() == text) }
            assertTrue("Click $text", view.performClick())
        }
        settle()
    }

    private fun dockNames(): List<String> {
        var result: List<String> = emptyList()
        main {
            val dock = HomeActivity::class.java.getDeclaredField("dock").apply { isAccessible = true }.get(home) as LinearLayout
            result = (0 until dock.childCount).map { dock.getChildAt(it).contentDescription.toString() }
        }
        return result
    }

    private fun holdOk() {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        settle()
    }

    private fun passed(name: String) { report += name; android.util.Log.i("CleanHomeTests", "PASS $name") }

    fun testRemoteAndWallpaperControls() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("home", 0)
        val originalFavourites = preferences.getString("favourites", null)
        val store = WallpaperStore(context)
        val originalState = store.state()
        preferences.edit().remove("favourites").commit()
        store.save(originalState.copy(paused = false))
        try {
            openHome()
            key(KeyEvent.KEYCODE_BACK)
            val installed = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet() + context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }
            val expected = listOf("org.smarttube.stable" to "SmartTube", "com.amazon.amazonvideo.livingroom" to "Prime Video", "org.videolan.vlc" to "VLC", "org.mozilla.firefox" to "Firefox").filter { it.first in installed }.map { it.second }
            assertTrue(expected.size >= 3)
            assertEquals(expected, dockNames())
            passed("all installed default favourites visible; missing apps omitted")
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            main { assertEquals(expected[1], home.currentFocus?.contentDescription?.toString()) }
            passed("D-pad focus navigation")
            key(KeyEvent.KEYCODE_BACK)
            holdOk()
            assertTrue(items().containsAll(listOf("Move left", "Move right", "Remove from favourites")))
            passed("OK hold opens reorder")
            choose("Move right")
            assertEquals(listOf(expected[1], expected[0]) + expected.drop(2), dockNames())
            passed("favourite move persists")
            openHome()
            key(KeyEvent.KEYCODE_BACK)
            holdOk()
            choose("Move right")
            assertEquals("SmartTube", dockNames().first())
            passed("favourite order restored")
            click("All Apps")
            main {
                val listed = descendants(dialog().window!!.decorView).filterIsInstance<AppCard>().map { it.tag.toString() }.toSet()
                assertEquals("All launchable installed apps must be listed", installed - context.packageName, listed)
            }
            passed("All Apps shows installed apps")
            main { dialog().dismiss() }
            click("Wallpaper settings")
            assertTrue(items().containsAll(listOf("Next wallpaper", "Pause 30-minute rotation", "About this image")))
            choose("Pause 30-minute rotation")
            assertTrue(store.state().paused)
            click("Wallpaper settings")
            choose("Resume 30-minute rotation")
            assertFalse(store.state().paused)
            passed("pause and resume controls")
            val before = store.state()
            click("Wallpaper settings")
            choose("Next wallpaper")
            val deadline = SystemClock.elapsedRealtime() + 25000
            while (store.state().currentId == before.currentId && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val after = store.state()
            assertFalse("Wallpaper should change", before.currentId == after.currentId)
            assertEquals("landscape", WallpaperStore(context).catalogue.first { it.id == after.currentId }.group)
            passed("Next changes to another local photograph")
            click("Wallpaper settings")
            choose("About this image")
            main { assertTrue(dialog().findViewById<TextView>(android.R.id.message).text.toString().contains("http")) }
            passed("wallpaper credit and license visible")
            main { dialog().dismiss() }
            click("Inputs")
            assertTrue(items().any { "HDMI" in it.uppercase() })
            passed("native HDMI inputs listed")
            main { dialog().dismiss() }
            click("Settings")
            val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
            val activities = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            assertTrue("Native TV settings should be foreground", activities.lineSequence().any { "mResumedActivity" in it && "com.cvte.settings" in it })
            passed("native TV settings opens")
            openHome()
            val file = java.io.File(context.filesDir, "remote-flow-results.txt")
            file.writeText(report.joinToString("\n"))
        } finally {
            if (originalFavourites == null) preferences.edit().remove("favourites").commit()
            else preferences.edit().putString("favourites", originalFavourites).commit()
            store.save(originalState.copy(paused = false))
        }
    }
}
