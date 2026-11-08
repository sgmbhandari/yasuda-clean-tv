package local.cleanhome.tv

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.test.InstrumentationTestCase

class TvHomeRouterTests : InstrumentationTestCase() {
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
    private fun expect(component: String) {
        val deadline = SystemClock.elapsedRealtime() + 8000
        var foreground: String
        do {
            SystemClock.sleep(200)
            foreground = shell("dumpsys activity activities").lineSequence().firstOrNull { "mResumedActivity" in it }.orEmpty()
        } while (!foreground.contains(component) && SystemClock.elapsedRealtime() < deadline)
        assertTrue("Expected $component, got $foreground", foreground.contains(component))
    }
    fun testRepeatedPowerAndSourceRoutesAreNotSwallowedByTheHomeTask() {
        repeat(2) {
            shell("am start -a android.intent.action.MAIN -c android.intent.category.TV_HOME --ez isLauncherGoToTv true --es android.intent.extra.REFERRER_NAME android-app://android")
            expect("local.cleanhome.tv/.HomeActivity")
            shell("am start -a android.intent.action.MAIN -c android.intent.category.TV_HOME --ez isLauncherGoToTv true --es android.intent.extra.REFERRER_NAME android-app://tv.cloudwalker.apiservice")
            expect("com.cvte.tv.setting/.TvSettingActivity")
        }
        shell("am start -a android.intent.action.MAIN -c android.intent.category.TIF_TV_HOME")
        expect("local.cleanhome.tv/.HomeActivity")
    }
}
