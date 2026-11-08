package local.cleanhome.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppCard(context: Context, title: String, artwork: Drawable, banner: Boolean, packageId: String, width: Int) : LinearLayout(context) {
    constructor(context: Context) : this(context, "", ColorDrawable(TvDesign.midnight), false, "", (230 * context.resources.displayMetrics.density).toInt())

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    val surface = FrameLayout(context)
    private val label = TextView(context)
    private val highlight = View(context)
    private val favouriteBadge = ImageView(context).apply {
        contentDescription = "Favourite"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageResource(R.drawable.favourite_star)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(220, 23, 27, 35))
            setStroke(dp(1), Color.argb(100, 255, 255, 255))
        }
        visibility = GONE
    }
    private val appTitle = title
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        contentDescription = title
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        surface.background = GradientDrawable().apply { setColor(colors(packageId)[0]); cornerRadius = dp(22).toFloat() }
        surface.clipToOutline = true
        surface.elevation = dp(3).toFloat()
        surface.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val image = ImageView(context).apply {
            setImageDrawable(artwork)
            scaleType = if (banner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.CENTER_INSIDE
        }
        val inset = if (banner) 0 else dp(29)
        surface.addView(image, FrameLayout.LayoutParams(-1, -1).apply { setMargins(inset, inset, inset, inset) })
        highlight.background = FocusRimDrawable(resources.displayMetrics.density)
        highlight.alpha = 0f
        surface.addView(highlight, FrameLayout.LayoutParams(-1, -1))
        surface.addView(favouriteBadge, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8); rightMargin = dp(8)
        })
        addView(surface, LayoutParams(-1, (width * .60f).toInt()))
        label.apply {
            text = title
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(TvDesign.foreground)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            alpha = .72f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(label, LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    fun setFavourite(value: Boolean) {
        favouriteBadge.visibility = if (value) VISIBLE else GONE
        contentDescription = if (value) "$appTitle, favourite" else appTitle
    }

    fun focusAppearance(focused: Boolean, immediate: Boolean = false) {
        val duration = if (immediate) 0L else TvDesign.duration(context, if (focused) TvDesign.FOCUS_ENTER else TvDesign.FOCUS_EXIT)
        surface.animate().cancel()
        label.animate().cancel()
        highlight.animate().cancel()
        val scale = if (focused) TvDesign.FOCUS_SCALE else 1f
        // Keep the card texture stable during transforms; avoid re-rasterizing a shadow every frame.
        surface.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        surface.translationZ = if (focused) dp(13).toFloat() else 0f
        highlight.alpha = if (focused) .85f else 0f
        if (duration == 0L) {
            surface.scaleX = scale
            surface.scaleY = scale
            surface.translationY = if (focused) -dp(5).toFloat() else 0f
            surface.translationZ = if (focused) dp(13).toFloat() else 0f
            label.alpha = if (focused) 1f else .72f
            label.translationY = if (focused) dp(3).toFloat() else 0f
            highlight.alpha = if (focused) .85f else 0f
            return
        }
        surface.animate().scaleX(scale).scaleY(scale).translationY(if (focused) -dp(5).toFloat() else 0f)
            .setDuration(duration).setInterpolator(TvDesign.ease).start()
        label.animate().alpha(if (focused) 1f else .72f).translationY(if (focused) dp(3).toFloat() else 0f)
            .setDuration(duration).setInterpolator(TvDesign.ease).start()

    }

    fun press(down: Boolean) {
        surface.animate().cancel()
        if (!down) { focusAppearance(hasFocus()); return }
        val scale = if (hasFocus()) 1.025f else .98f
        surface.animate().scaleX(scale).scaleY(scale).setDuration(TvDesign.duration(context, 80)).setInterpolator(TvDesign.ease).start()
    }

    fun cancelMotion() {
        surface.animate().cancel()
        label.animate().cancel()
        highlight.animate().cancel()
        surface.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun colors(id: String): IntArray = when (id) {
        "org.smarttube.stable" -> intArrayOf(Color.rgb(174, 32, 43), Color.rgb(96, 14, 27))
        "com.amazon.amazonvideo.livingroom" -> intArrayOf(Color.rgb(14, 92, 179), Color.rgb(7, 43, 94))
        "org.videolan.vlc" -> intArrayOf(Color.rgb(244, 151, 43), Color.rgb(177, 76, 11))
        "org.mozilla.firefox" -> intArrayOf(Color.rgb(72, 41, 119), Color.rgb(31, 18, 59))
        else -> intArrayOf(Color.rgb(58, 69, 94), Color.rgb(27, 32, 49))
    }
}
