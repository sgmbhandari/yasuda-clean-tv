package local.cleanhome.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

class GlassDrawable(private val owner: View, private val backdrop: () -> Bitmap?, private val panel: Boolean = false) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val clip = Path()
    private var focused = false
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val next = android.R.attr.state_focused in state || android.R.attr.state_pressed in state
        if (next == focused) return false
        focused = next
        invalidateSelf()
        return true
    }
    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val density = owner.resources.displayMetrics.density
        val radius = if (panel) 30f * density else rect.height() / 2
        clip.reset();clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clip)
        backdrop()?.takeUnless { it.isRecycled }?.let { image ->
            val location = IntArray(2);owner.getLocationOnScreen(location)
            val screenWidth = owner.resources.displayMetrics.widthPixels.toFloat()
            val screenHeight = owner.resources.displayMetrics.heightPixels.toFloat()
            val left = (location[0] / screenWidth * image.width).toInt().coerceIn(0, image.width - 1)
            val top = (location[1] / screenHeight * image.height).toInt().coerceIn(0, image.height - 1)
            val right = ((location[0] + rect.width()) / screenWidth * image.width).toInt().coerceIn(left + 1, image.width)
            val bottom = ((location[1] + rect.height()) / screenHeight * image.height).toInt().coerceIn(top + 1, image.height)
            paint.shader = null;paint.color = -1
            canvas.drawBitmap(image, Rect(left, top, right, bottom), rect, paint)
        }
        paint.shader = null
        paint.color = if (panel) 0xC01B2029.toInt() else if (focused) 0x70404955 else 0x85141A25.toInt()
        canvas.drawRect(rect, paint)
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(if (focused) 0x58FFFFFF else 0x32FFFFFF, 0x0FFFFFFF, 0x03FFFFFF), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(rect, paint)
        canvas.restoreToCount(saved)
        val inset = density * .8f
        rect.inset(inset, inset)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * (if (focused) 1.5f else .85f)
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(if (focused) 0xF2FFFFFF.toInt() else 0x8CFFFFFF.toInt(), 0x22FFFFFF, if (focused) 0xA0FFFFFF.toInt() else 0x45FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL;paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha;invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter;invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
