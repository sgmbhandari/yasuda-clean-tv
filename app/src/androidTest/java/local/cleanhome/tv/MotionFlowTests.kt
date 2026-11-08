package local.cleanhome.tv

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout

class MotionFlowTests : InstrumentationTestCase() {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
    fun testFocusLiftRapidNavigationAndReturn() {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("home", 0)
        val favourites = prefs.getString("favourites", null)
        val focusedApp = prefs.getString("focused_app", null)
        prefs.edit().remove("favourites").remove("focused_app").commit()
        var home = (instrumentation as CleanHomeTestRunner).openHome()
        fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(450) }
        fun key(code: Int) { instrumentation.sendKeyDownUpSync(code) }
        val focusDeadline = SystemClock.elapsedRealtime() + 6000
        var ready = false
        while (!ready && SystemClock.elapsedRealtime() < focusDeadline) {
            instrumentation.runOnMainSync { ready = home.hasWindowFocus() }
            SystemClock.sleep(100)
        }
        assertTrue("Home must have window focus before remote input", ready)
        settle()
        key(KeyEvent.KEYCODE_BACK)
        settle()
        var dock = HomeActivity::class.java.getDeclaredField("dock").apply { isAccessible = true }.get(home) as LinearLayout
        assertTrue("At least three installed favourites for motion navigation", dock.childCount >= 3)
        val secondName = dock.getChildAt(1).contentDescription.toString()
        try {
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            SystemClock.sleep(60)
            main {
                val progress = (dock.getChildAt(1) as ViewGroup).getChildAt(0).scaleX
                assertTrue("Focus must animate through intermediate values, not jump instantly: $progress", progress > 1f && progress < 1.0649f)
            }
            settle()
            main {
                assertEquals(secondName, home.currentFocus?.contentDescription?.toString())
                val focused = dock.getChildAt(1) as ViewGroup
                assertTrue("Focused app artwork must lift and enlarge", focused.getChildAt(0).scaleX > 1.04f)
                assertTrue("Focused artwork must rise", focused.getChildAt(0).translationY < 0f)
                assertEquals("Previous artwork must return to resting scale", 1f, (dock.getChildAt(0) as ViewGroup).getChildAt(0).scaleX, .002f)
            }
            shell("dumpsys gfxinfo local.cleanhome.tv reset")
            repeat(24) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT)
                key(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            settle()
            main {
                assertEquals(secondName, home.currentFocus?.contentDescription?.toString())
                for (index in 0 until dock.childCount) {
                    val card = (dock.getChildAt(index) as ViewGroup).getChildAt(0)
                    assertEquals(if (index == 1) 1.065f else 1f, card.scaleX, .003f)
                }
            }
            java.io.File(context.filesDir, "motion-stress-gfxinfo.txt").writeText(shell("dumpsys gfxinfo local.cleanhome.tv framestats"))
            shell("dumpsys gfxinfo local.cleanhome.tv reset")
            repeat(8) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(300)
                key(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(300)
            }
            java.io.File(context.filesDir, "motion-gfxinfo.txt").writeText(shell("dumpsys gfxinfo local.cleanhome.tv framestats"))
            key(KeyEvent.KEYCODE_BACK)
            settle()
            main { assertEquals("SmartTube", home.currentFocus?.contentDescription?.toString()) }
            val vlcIndex = (0 until dock.childCount).first { dock.getChildAt(it).tag == "org.videolan.vlc" }
            repeat(vlcIndex) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            settle()
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            SystemClock.sleep(1800)
            val foreground = shell("dumpsys activity activities").lineSequence().firstOrNull { "mResumedActivity" in it }.orEmpty()
            assertTrue("VLC must launch from its card", "org.videolan.vlc" in foreground)
            shell("input keyevent 3")
            home = (instrumentation as CleanHomeTestRunner).openHome()
            dock = HomeActivity::class.java.getDeclaredField("dock").apply { isAccessible = true }.get(home) as LinearLayout
            SystemClock.sleep(500)
            settle()
            main { assertEquals("VLC", home.currentFocus?.contentDescription?.toString()) }
            val originalScale = shell("settings get global animator_duration_scale").trim()
            try {
                shell("settings put global animator_duration_scale 0")
                main {
                    dock.getChildAt(0).requestFocus()
                    assertEquals("Reduced motion applies focus without a running transition", 1.065f, (dock.getChildAt(0) as ViewGroup).getChildAt(0).scaleX, .001f)
                }
            } finally {
                if (originalScale == "null") shell("settings delete global animator_duration_scale")
                else { assertTrue(originalScale.matches(Regex("[0-9.]+"))); shell("settings put global animator_duration_scale $originalScale") }
            }
            val installed = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.toSet() + context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0).map { it.activityInfo.packageName }
            val defaults = listOf("org.smarttube.stable", "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "org.mozilla.firefox").filter { it in installed }
            val extra = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.distinct().filter { it !in defaults && it != context.packageName }.take(6 - defaults.size)
            assertEquals("Enough installed apps for the six-card layout", 6 - defaults.size, extra.size)
            prefs.edit().putString("favourites", (defaults + extra).joinToString(",")).commit()
            main { HomeActivity::class.java.getDeclaredMethod("refreshApps").apply { isAccessible = true }.invoke(home); dock.getChildAt(5).requestFocus() }
            settle()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.filesDir, "motion-six-favourites.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
            main {
                assertEquals(6, dock.childCount)
                for (index in 0 until dock.childCount) {
                    val bounds = android.graphics.Rect()
                    val surface = (dock.getChildAt(index) as ViewGroup).getChildAt(0)
                    assertTrue("Card $index visible; size=${surface.width}x${surface.height}, homeFocus=${home.hasWindowFocus()}, bounds=$bounds", surface.getGlobalVisibleRect(bounds))
                    assertTrue("App card remains within screen safe area", bounds.left >= 0 && bounds.right <= home.resources.displayMetrics.widthPixels)
                    assertTrue("Card is not vertically clipped", bounds.height() >= surface.height - 2)
                }
            }
            java.io.File(context.filesDir, "motion-flow-results.txt").writeText("PASS focus animation has intermediate frames, enlargement and elevation\nPASS rapid focus reversal settles\nPASS Back restores dock focus\nPASS VLC launch and Home returns focus to VLC\nPASS reduced motion immediate focus\nPASS six favourites stay within screen")
        } finally {
            val editor = prefs.edit()
            if (favourites == null) editor.remove("favourites") else editor.putString("favourites", favourites)
            if (focusedApp == null) editor.remove("focused_app") else editor.putString("focused_app", focusedApp)
            editor.commit()
            instrumentation.runOnMainSync { home.finish() }
        }
    }
}
