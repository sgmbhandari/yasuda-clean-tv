package local.cleanhome.tv

import android.app.AlertDialog
import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.json.JSONObject

class RecoveryTests : InstrumentationTestCase() {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun active(home: HomeActivity) = HomeActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(home) as AlertDialog
    private fun choose(home: HomeActivity, title: String) {
        instrumentation.runOnMainSync {
            val list = active(home).listView
            val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == title }
            list.performItemClick(list.getChildAt(index - list.firstVisiblePosition), index, list.adapter.getItemId(index))
        }
        instrumentation.waitForIdleSync()
    }
    fun testResetIsInAboutAndWrongPinCannotReachConfirmation() {
        val config = java.io.File(instrumentation.targetContext.filesDir, "recovery.json")
        val original = config.readText()
        val salt = "00112233445566778899aabbccddeeff"
        config.writeText(JSONObject(original).put("pinSalt", salt).put("pinHash", ResetPin.derive("123456".toCharArray(), salt)).toString())
        val home = (instrumentation as CleanHomeTestRunner).openHome()
        try {
            instrumentation.runOnMainSync {
                assertFalse(descendants(home.window.decorView).any { it.contentDescription == "Restore original TV setup" })
                val wallpaper = descendants(home.window.decorView).first { it.contentDescription == "Wallpaper settings" }
                wallpaper.performClick()
            }
            instrumentation.waitForIdleSync()
            choose(home, "About Clean Home")
            instrumentation.runOnMainSync { active(home).getButton(AlertDialog.BUTTON_NEUTRAL).performClick() }
            instrumentation.waitForIdleSync()
            val challenge = active(home)
            instrumentation.runOnMainSync {
                val pin = descendants(challenge.window!!.decorView).filterIsInstance<EditText>().single()
                assertTrue(pin.inputType and android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD != 0)
                pin.setText("000000")
                challenge.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            android.os.SystemClock.sleep(1800)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSame("Wrong PIN stays on challenge", challenge, active(home))
                val pin = descendants(challenge.window!!.decorView).filterIsInstance<EditText>().single()
                assertNotNull(pin.error)
                pin.setText("123456")
                challenge.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 8000
            while (active(home) === challenge && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100)
            instrumentation.waitForIdleSync()
            val dialog = active(home)
            instrumentation.runOnMainSync {
                assertNotSame(challenge, dialog)
                assertEquals("Restore and restart", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
                assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus())
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("Remote can focus the Restore button", dialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
        } finally {
            config.writeText(original)
            home.getSharedPreferences("home", 0).edit().remove("reset_attempts").remove("reset_blocked_until").commit()
            instrumentation.runOnMainSync { home.finish() }
        }
    }
    fun testLocalAdbRecoveryPreflightWithoutChangingTv() {
        val spec = RecoveryConfig.read(instrumentation.targetContext)
        LocalAdb.requireRoot()
        assertTrue(LocalAdb.recovery(spec.getString("scriptSha256"), false).contains("RESTORE_CHECK_OK"))
        try { LocalAdb.recovery("0".repeat(64), false); fail("Changed restore script accepted") } catch (_: java.io.IOException) { }
    }
}
