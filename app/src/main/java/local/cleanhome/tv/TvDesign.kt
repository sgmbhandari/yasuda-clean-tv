package local.cleanhome.tv

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.view.animation.PathInterpolator

object TvDesign {
    val foreground = Color.rgb(245, 246, 250)
    val secondary = Color.rgb(196, 199, 208)
    val midnight = Color.rgb(9, 13, 23)
    const val FOCUS_SCALE = 1.065f
    const val FOCUS_ENTER = 220L
    const val FOCUS_EXIT = 180L
    const val WALLPAPER_FADE = 450L
    val ease = PathInterpolator(.18f, .8f, .25f, 1f)
    fun duration(context: Context, millis: Long): Long =
        if (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) 0 else millis
}
