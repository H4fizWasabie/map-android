package app.map.android

import android.graphics.Bitmap
import android.graphics.Color

data class CropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

object ScanImageProcessor {
    fun clean(bitmap: Bitmap): Bitmap {
        val scale = minOf(1f, 1200f / maxOf(bitmap.width, bitmap.height))
        val working = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val bounds = detectBounds(working)
        val result = bounds?.let {
            val left = (it.left / scale).toInt().coerceIn(0, bitmap.width - 1)
            val top = (it.top / scale).toInt().coerceIn(0, bitmap.height - 1)
            val right = (it.right / scale).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (it.bottom / scale).toInt().coerceIn(top + 1, bitmap.height)
            if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) bitmap
            else Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } ?: bitmap
        if (working !== bitmap) working.recycle()
        return result
    }

    private fun detectBounds(bitmap: Bitmap): CropBounds? {
        if (bitmap.width < 100 || bitmap.height < 100) return null
        val background = averageCorners(bitmap)
        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 300)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                if (colorDistance(bitmap.getPixel(x, y), background) < 28) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right <= left || bottom <= top) return null
        val width = right - left
        val height = bottom - top
        if (width < bitmap.width * 0.55f || height < bitmap.height * 0.55f) return null
        if (width * height < bitmap.width * bitmap.height * 0.45f) return null
        val padding = maxOf(step * 2, minOf(width, height) / 100)
        return CropBounds(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding).coerceAtMost(bitmap.width),
            (bottom + padding).coerceAtMost(bitmap.height)
        )
    }

    private fun averageCorners(bitmap: Bitmap): Int {
        val size = minOf(24, bitmap.width / 10, bitmap.height / 10).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        listOf(
            0 to 0,
            bitmap.width - size to 0,
            0 to bitmap.height - size,
            bitmap.width - size to bitmap.height - size
        ).forEach { (startX, startY) ->
            for (y in startY until startY + size) for (x in startX until startX + size) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count++
            }
        }
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun colorDistance(first: Int, second: Int): Int =
        (kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))) / 3
}
