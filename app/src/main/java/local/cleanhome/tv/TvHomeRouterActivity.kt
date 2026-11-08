package local.cleanhome.tv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** CVTE also sends TV_HOME when the user chooses a source. Do not swallow it. */
class TvHomeRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val original = intent
        val isTif = original.hasCategory("android.intent.category.TIF_TV_HOME")
        val hasSource = original.hasExtra("sourceid") || original.hasExtra("sourceId")
        val power = original.getBooleanExtra("isLauncherGoToTv", false)
        val caller = referrer?.host
        val home = TvHomeRoute.home(isTif, power, hasSource, caller)
        android.util.Log.i("CleanHomeRoute", "tif=$isTif power=$power source=$hasSource caller=$caller destination=${if (home) "home" else "player"}")
        try {
            val destination = if (home) Intent(this, HomeActivity::class.java) else Intent(original).apply {
                component = ComponentName("com.cvte.tv.setting", "com.cvte.tv.setting.TvSettingActivity")
            }
            startActivity(destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            Toast.makeText(this, "The TV player could not be opened. Use Home → Inputs.", Toast.LENGTH_LONG).show()
        // Remove the trampoline task as well as its activity. A retained alias
        // task can consume a subsequent TV_HOME/TIF_TV_HOME launch on this ROM.
        } finally { finishAndRemoveTask() }
    }
}
