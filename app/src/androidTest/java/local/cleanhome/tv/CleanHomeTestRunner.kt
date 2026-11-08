package local.cleanhome.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.test.InstrumentationTestRunner
import java.lang.ref.WeakReference

class CleanHomeTestRunner : InstrumentationTestRunner() {
    private var currentHome = WeakReference<HomeActivity>(null)

    override fun callActivityOnCreate(activity: Activity, state: Bundle?) {
        super.callActivityOnCreate(activity, state)
        if (activity is HomeActivity) currentHome = WeakReference(activity)
    }

    override fun callActivityOnResume(activity: Activity) {
        super.callActivityOnResume(activity)
        if (activity is HomeActivity) currentHome = WeakReference(activity)
    }

    fun openHome(): HomeActivity {
        // startActivitySync waits forever for creation when singleTask Home already exists.
        targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 8000
        var result: HomeActivity? = null
        var requestedAgain = false
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            runOnMainSync {
                result = currentHome.get()?.takeIf { !it.isDestroyed && !it.isFinishing && it.hasWindowFocus() }
            }
            if (!requestedAgain && deadline - SystemClock.elapsedRealtime() < 6000 && result == null) {
                // CVTE can open TV_HOME once after instrumentation stops the default launcher.
                targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requestedAgain = true
            }
            SystemClock.sleep(100)
        }
        return result ?: throw AssertionError("Clean Home did not acquire window focus")
    }
}
