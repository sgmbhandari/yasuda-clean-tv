package local.cleanhome.tv

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class InputBanner(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    var view: TextView? = null
        private set
    private val dismiss = Runnable { close() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun show(label: String) {
        close()
        if (!Settings.canDrawOverlays(context)) return
        val text = TextView(context).apply {
            this.text = label
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(TvDesign.foreground)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply { setColor(0xE61B2029.toInt()); cornerRadius = dp(12).toFloat() }
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(40); y = dp(34); title = "Clean Home input label" }
        try {
            windows.addView(text, params)
            view = text
            handler.postDelayed(dismiss, 5000)
        } catch (_: RuntimeException) { view = null }
    }

    fun close() {
        handler.removeCallbacks(dismiss)
        view?.let { try { windows.removeViewImmediate(it) } catch (_: IllegalArgumentException) { } }
        view = null
    }
}
