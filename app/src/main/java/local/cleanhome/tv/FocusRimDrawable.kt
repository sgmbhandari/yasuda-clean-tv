package local.cleanhome.tv

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class FocusRimDrawable(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val radius = 22 * density
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(rect.left,rect.top,rect.right,rect.bottom,intArrayOf(0x20FFFFFF,0x02FFFFFF),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        rect.inset(density,density)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.6f*density
        paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,intArrayOf(0xFFFFFFFF.toInt(),0x65FFFFFF,0xD0FFFFFF.toInt()),floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.shader=null
    }
    override fun setAlpha(alpha:Int) {paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter;invalidateSelf()}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
