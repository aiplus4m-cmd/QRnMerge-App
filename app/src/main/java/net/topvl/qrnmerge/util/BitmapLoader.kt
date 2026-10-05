package net.topvl.qrnmerge.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapLoader {

    /** Decodes [uri] honouring EXIF rotation, scaled so the longest side is at most [maxSide]. */
    fun decode(context: Context, uri: Uri, maxSide: Int, extraRotation: Int = 0): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null

        val exifRotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)

        val scaled = scaleDown(decoded, maxSide)
        return rotate(scaled, (exifRotation + extraRotation) % 360)
    }

    fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val f = maxSide.toFloat() / longest
        val out = Bitmap.createScaledBitmap(
            src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true,
        )
        if (out !== src) src.recycle()
        return out
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    /** Returns an opaque copy (transparent areas become white) – required for JPEG output. */
    fun flattenOnWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        return out
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            if (!flattenOnWhite(bitmap).compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw java.io.IOException("JPEG encoding failed")
            }
        }
    }

    fun pixels(bitmap: Bitmap): IntArray {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return px
    }

    fun fromPixels(px: IntArray, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
}
