package net.topvl.qrnmerge.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EnhanceMode { ORIGINAL, AUTO, GRAY, BW }

/**
 * @param sharpness 0..1 – strength of the unsharp mask.
 * @param contrast 0..1 – how aggressively ink is pushed to black / paper to white.
 */
data class EnhanceSettings(
    val mode: EnhanceMode = EnhanceMode.AUTO,
    val sharpness: Float = 0.6f,
    val contrast: Float = 0.5f,
)

/**
 * Pure-Kotlin "paper scan" filters working on ARGB pixel arrays, so they can be unit tested
 * on the JVM. Pipeline: illumination flattening (removes shadows / makes paper white)
 * -> levels (contrast) -> unsharp mask (crisp text) -> optional adaptive threshold (B&W).
 */
object ImageEnhancer {

    fun process(pixels: IntArray, width: Int, height: Int, settings: EnhanceSettings): IntArray {
        require(pixels.size == width * height) { "pixel buffer does not match size" }
        if (width < 3 || height < 3) return pixels.copyOf()
        if (settings.mode == EnhanceMode.ORIGINAL) {
            return if (settings.sharpness > 0f) sharpenColor(pixels, width, height, settings.sharpness * 0.8f)
            else pixels.copyOf()
        }

        val n = width * height
        val lum = IntArray(n)
        for (i in 0 until n) lum[i] = luminance(pixels[i])
        val bg = estimateBackground(lum, width, height)

        val contrast = settings.contrast.coerceIn(0f, 1f)
        val black = 25f + 70f * contrast   // values below this become pure black
        val white = 245f - 30f * contrast  // values above this become pure white
        val levels = IntArray(256) { v ->
            (((v - black) * 255f) / (white - black)).roundToInt().coerceIn(0, 255)
        }
        val sharpAmount = settings.sharpness.coerceIn(0f, 1.5f) * 1.6f

        return when (settings.mode) {
            EnhanceMode.AUTO -> {
                val out = IntArray(n)
                for (i in 0 until n) {
                    val p = pixels[i]
                    val scale = 255f / max(bg[i], 90)
                    val r = levels[min(255, (((p shr 16) and 0xFF) * scale).toInt())]
                    val g = levels[min(255, (((p shr 8) and 0xFF) * scale).toInt())]
                    val b = levels[min(255, ((p and 0xFF) * scale).toInt())]
                    out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                if (sharpAmount > 0f) sharpenColor(out, width, height, sharpAmount) else out
            }
            EnhanceMode.GRAY -> {
                val g = normalized(lum, bg)
                for (i in 0 until n) g[i] = levels[g[i]]
                val s = if (sharpAmount > 0f) sharpenGray(g, width, height, sharpAmount) else g
                grayToArgb(s)
            }
            EnhanceMode.BW -> {
                val g = normalized(lum, bg)
                val s = sharpenGray(g, width, height, 0.4f + sharpAmount * 0.5f)
                grayToArgb(adaptiveThreshold(s, width, height, 0.06f + 0.12f * contrast))
            }
            EnhanceMode.ORIGINAL -> pixels.copyOf()
        }
    }

    fun luminance(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 77 + g * 150 + b * 29) shr 8
    }

    private fun normalized(lum: IntArray, bg: IntArray): IntArray =
        IntArray(lum.size) { i -> min(255, lum[i] * 255 / max(bg[i], 90)) }

    private fun grayToArgb(g: IntArray): IntArray =
        IntArray(g.size) { i -> val v = g[i]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }

    /**
     * Estimates the paper brightness at every pixel: max-pool into coarse blocks (text disappears),
     * dilate + smooth the coarse grid, then upsample bilinearly.
     */
    fun estimateBackground(lum: IntArray, width: Int, height: Int): IntArray {
        val block = max(4, max(width, height) / 48)
        val sw = (width + block - 1) / block
        val sh = (height + block - 1) / block
        var grid = IntArray(sw * sh)
        for (y in 0 until height) {
            val gy = y / block
            val row = y * width
            for (x in 0 until width) {
                val gi = gy * sw + x / block
                val v = lum[row + x]
                if (v > grid[gi]) grid[gi] = v
            }
        }
        grid = maxFilter3(grid, sw, sh)
        grid = boxBlur3(boxBlur3(grid, sw, sh), sw, sh)

        val out = IntArray(width * height)
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                val top = grid[y0 * sw + x0] * (1 - tx) + grid[y0 * sw + x1] * tx
                val bot = grid[y1 * sw + x0] * (1 - tx) + grid[y1 * sw + x1] * tx
                out[y * width + x] = (top * (1 - ty) + bot * ty).roundToInt()
            }
        }
        return out
    }

    private fun maxFilter3(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dy in -1..1) {
                val yy = (y + dy).coerceIn(0, h - 1)
                for (dx in -1..1) {
                    val v = src[yy * w + (x + dx).coerceIn(0, w - 1)]
                    if (v > m) m = v
                }
            }
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur3(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (dy in -1..1) {
                val yy = (y + dy).coerceIn(0, h - 1)
                for (dx in -1..1) s += src[yy * w + (x + dx).coerceIn(0, w - 1)]
            }
            out[y * w + x] = s / 9
        }
        return out
    }

    /** Unsharp mask on a single channel using a 3x3 box blur. */
    fun sharpenGray(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val ym = max(0, y - 1) * w; val y0 = y * w; val yp = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val xm = max(0, x - 1); val xp = min(w - 1, x + 1)
                val blur = (src[ym + xm] + src[ym + x] + src[ym + xp] +
                    src[y0 + xm] + src[y0 + x] + src[y0 + xp] +
                    src[yp + xm] + src[yp + x] + src[yp + xp]) / 9f
                val v = src[y0 + x]
                out[y0 + x] = (v + amount * (v - blur)).roundToInt().coerceIn(0, 255)
            }
        }
        return out
    }

    fun sharpenColor(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val ym = max(0, y - 1) * w; val y0 = y * w; val yp = min(h - 1, y + 1) * w
            for (x in 0 until w) {
                val xm = max(0, x - 1); val xp = min(w - 1, x + 1)
                val a = src[ym + xm]; val b = src[ym + x]; val c = src[ym + xp]
                val d = src[y0 + xm]; val e = src[y0 + x]; val f = src[y0 + xp]
                val g = src[yp + xm]; val hh = src[yp + x]; val i = src[yp + xp]
                var res = 0xFF shl 24
                var shift = 16
                while (shift >= 0) {
                    val sum = ((a shr shift) and 0xFF) + ((b shr shift) and 0xFF) + ((c shr shift) and 0xFF) +
                        ((d shr shift) and 0xFF) + ((e shr shift) and 0xFF) + ((f shr shift) and 0xFF) +
                        ((g shr shift) and 0xFF) + ((hh shr shift) and 0xFF) + ((i shr shift) and 0xFF)
                    val v = (e shr shift) and 0xFF
                    res = res or ((v + amount * (v - sum / 9f)).roundToInt().coerceIn(0, 255) shl shift)
                    shift -= 8
                }
                out[y0 + x] = res
            }
        }
        return out
    }

    /** Local-mean threshold (integral image): pixel is ink when darker than (1-k) * local mean. */
    fun adaptiveThreshold(src: IntArray, w: Int, h: Int, k: Float): IntArray {
        val radius = max(6, min(w, h) / 40)
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += src[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum
            }
        }
        val out = IntArray(src.size)
        for (y in 0 until h) {
            val y0 = max(0, y - radius); val y1 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x0 = max(0, x - radius); val x1 = min(w - 1, x + radius)
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)] - integral[y0 * (w + 1) + (x1 + 1)] -
                    integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = sum.toFloat() / count
                val v = src[y * w + x]
                out[y * w + x] = if (v < mean * (1f - k) && v < 225) 0 else 255
            }
        }
        return out
    }
}
