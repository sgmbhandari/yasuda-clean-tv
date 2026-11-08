package local.cleanhome.tv

import android.app.AlertDialog
import android.content.Intent
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class InputFlowTests : InstrumentationTestCase() {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    fun testEveryExposedInputSelectsItsHardwareId() {
        val context = instrumentation.targetContext
        val inputs = context.getSystemService(TvInputManager::class.java).tvInputList
            .filter { it.isPassthroughInput && it.type in listOf(TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT) }.sortedBy { it.id }
        assertTrue(inputs.size >= 3)
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        val checked = mutableListOf<String>()
        try {
            for ((index, input) in inputs.withIndex()) {
                context.startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                instrumentation.waitForIdleSync()
                SystemClock.sleep(400)
                instrumentation.runOnMainSync {
                    val button = descendants(home.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Inputs" }
                    assertTrue(button.performClick())
                }
                instrumentation.waitForIdleSync()
                val dialog = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
                instrumentation.runOnMainSync {
                    val list = dialog.listView
                    assertTrue(list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index)))
                }
                SystemClock.sleep(600)
                instrumentation.runOnMainSync {
                    val banner = HomeActivity::class.java.getDeclaredMethod("getInputBanner").apply { isAccessible = true }.invoke(home) as InputBanner
                    assertEquals("Launcher input label must identify the selected source", input.loadLabel(context).toString(), banner.view?.text?.toString())
                }
                if (input.type == TvInputInfo.TYPE_HDMI && checked.none { it.startsWith("HDMI") }) {
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    java.io.File(context.filesDir, "input-label-hdmi.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    screenshot.recycle()
                }
                val expected = InputRouting.sourceId(input.id) ?: throw AssertionError("Input hardware ID missing")
                val deadline = SystemClock.elapsedRealtime() + 6000
                var activeSessions: String
                do {
                    SystemClock.sleep(250)
                    val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys tv_input")
                    val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                    activeSessions = dump.substringAfter("sessionStateMap:").substringBefore("callbackSet:")
                } while (!activeSessions.contains("inputId: ${input.id}") && SystemClock.elapsedRealtime() < deadline)
                assertTrue("Input ${input.loadLabel(context)} must have an active hardware session: $activeSessions", activeSessions.contains("inputId: ${input.id}"))
                checked += "${input.loadLabel(context)} -> HW$expected"
                android.util.Log.i("CleanHomeTests", "PASS input ${checked.last()}")
            }
            java.io.File(context.filesDir, "input-flow-results.txt").writeText(checked.joinToString("\n"))
        } finally { instrumentation.runOnMainSync { home.finish() } }
    }
}
