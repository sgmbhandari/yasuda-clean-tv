package local.cleanhome.tv

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class PhotoIconDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TvDesign.foreground; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        canvas.drawRoundRect(RectF(2f,3f,22f,21f),4f,4f,paint)
        canvas.drawCircle(16.5f,8.5f,1.7f,paint)
        val path=Path().apply { moveTo(3f,17f);lineTo(8f,11f);lineTo(14f,18f);lineTo(18f,14f);lineTo(21f,18f) }
        canvas.drawPath(path,paint)
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha:Int) { paint.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
