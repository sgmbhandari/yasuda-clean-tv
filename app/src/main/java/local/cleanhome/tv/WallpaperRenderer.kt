package local.cleanhome.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

object WallpaperRenderer {
    /** Consumes the decoded image; one opaque texture avoids a full-screen gradient blend per frame. */
    fun prepare(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val scale = maxOf(1920f / source.width, 1080f / source.height)
            val width = source.width * scale
            val height = source.height * scale
            val bounds = RectF((1920 - width) / 2, (1080 - height) / 2, (1920 + width) / 2, (1080 + height) / 2)
            canvas.drawBitmap(source, null, bounds, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            fun smooth(start: Float, end: Float, value: Float): Float {
                val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
                return t * t * (3 - 2 * t)
            }
            val stops = FloatArray(65) { it / 64f }
            val colors = IntArray(stops.size) { index ->
                val y = stops[index]
                val top = .54f * (1 - smooth(0f, .44f, y))
                val bottom = .76f * smooth(.44f, 1f, y)
                ((255 * (top + bottom).coerceAtMost(.8f)).roundToInt() shl 24) or 0x090D17
            }
            val shade = Paint(Paint.DITHER_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, 1080f, colors, stops, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 1920f, 1080f, shade)
            output.setHasAlpha(false)
            return output
        } catch (error: Throwable) { output.recycle(); throw error }
        finally { source.recycle() }
    }
}
