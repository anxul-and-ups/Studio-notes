package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Loading, rotating, cropping and blurring the user's wallpaper. */
object ThemeImageProcessor {

    /** Decodes [uri] down-sampled so the longest side is about [maxSide] px. Null on failure. */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) { null }

    fun loadFile(path: String, maxSide: Int = 1600): Bitmap? = try {
        if (path.isBlank() || !File(path).exists()) null else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) { null }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Smooth bitmap blur without the old aggressive down-scale/up-scale trick.
     * The previous implementation made the wallpaper look pixelated at higher blur
     * values because it reduced the image to a very small bitmap first. This uses
     * three inexpensive box-blur passes at the final render resolution, which
     * approximates a Gaussian blur while preserving image detail.
     */
    fun blur(src: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.01f) return src
        val radius = (amount.coerceIn(0f, 1f) * 22f).roundToInt().coerceAtLeast(1)
        var current = src
        repeat(3) {
            val next = boxBlur(current, radius)
            if (current !== src && !current.isRecycled) current.recycle()
            current = next
        }
        return current
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2) return src
        val srcPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val horizontal = IntArray(w * h)
        val output = IntArray(w * h)
        val window = radius * 2 + 1

        // Horizontal pass with a sliding window.
        for (y in 0 until h) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val x = i.coerceIn(0, w - 1)
                val c = srcPixels[y * w + x]
                a += c ushr 24; r += (c ushr 16) and 0xFF; g += (c ushr 8) and 0xFF; b += c and 0xFF
            }
            for (x in 0 until w) {
                horizontal[y * w + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val removeX = (x - radius).coerceIn(0, w - 1)
                val addX = (x + radius + 1).coerceIn(0, w - 1)
                val remove = srcPixels[y * w + removeX]
                val add = srcPixels[y * w + addX]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                g += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }

        // Vertical pass.
        for (x in 0 until w) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val y = i.coerceIn(0, h - 1)
                val c = horizontal[y * w + x]
                a += c ushr 24; r += (c ushr 16) and 0xFF; g += (c ushr 8) and 0xFF; b += c and 0xFF
            }
            for (y in 0 until h) {
                output[y * w + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val removeY = (y - radius).coerceIn(0, h - 1)
                val addY = (y + radius + 1).coerceIn(0, h - 1)
                val remove = horizontal[removeY * w + x]
                val add = horizontal[addY * w + x]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add ushr 16) and 0xFF) - ((remove ushr 16) and 0xFF)
                g += ((add ushr 8) and 0xFF) - ((remove ushr 8) and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Renders the framed area exactly as the preview shows it.
     * [zoom] >= 1, [offsetX]/[offsetY] are preview-frame pixel offsets of the image centre,
     * [frameW]/[frameH] the preview frame size in px.
     */
    fun render(
        rotatedSource: Bitmap,
        frameW: Float,
        frameH: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        blurAmount: Float,
        outW: Int = 900
    ): Bitmap {
        val outH = (outW * frameH / frameW).toInt().coerceAtLeast(1)
        val k = outW / frameW
        val base = maxOf(frameW / rotatedSource.width, frameH / rotatedSource.height)
        val scale = base * zoom * k
        val m = Matrix().apply {
            postTranslate(-rotatedSource.width / 2f, -rotatedSource.height / 2f)
            postScale(scale, scale)
            postTranslate(outW / 2f + offsetX * k, outH / 2f + offsetY * k)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(rotatedSource, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return blur(out, blurAmount)
    }

    fun saveJpeg(context: Context, bmp: Bitmap): String {
        val f = File(context.filesDir, "theme_bg.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return f.absolutePath
    }

    fun delete(context: Context) { File(context.filesDir, "theme_bg.jpg").delete() }
}
