package local.cleanhome.tv

import android.graphics.Bitmap

object GlassBackdrop {
    fun create(source: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(source, 240, 135, true)
        val width = small.width
        val height = small.height
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        small.recycle()
        var input = pixels
        repeat(2) {
            val horizontal = pass(input, width, height, true)
            input = pass(horizontal, width, height, false)
        }
        return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
    }

    private fun pass(input: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val output = IntArray(input.size)
        val radius = 6
        val divisor = radius * 2 + 1
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        fun index(line: Int, offset: Int) = if (horizontal) line * width + offset else offset * width + line
        for (line in 0 until lines) {
            var r = 0; var g = 0; var b = 0
            fun add(color: Int, direction: Int) {
                r += ((color shr 16) and 255) * direction
                g += ((color shr 8) and 255) * direction
                b += (color and 255) * direction
            }
            for (offset in -radius..radius) add(input[index(line, offset.coerceIn(0, length - 1))], 1)
            for (offset in 0 until length) {
                output[index(line, offset)] = 0xFF000000.toInt() or ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
                add(input[index(line, (offset - radius).coerceAtLeast(0))], -1)
                add(input[index(line, (offset + radius + 1).coerceAtMost(length - 1))], 1)
            }
        }
        return output
    }
}
