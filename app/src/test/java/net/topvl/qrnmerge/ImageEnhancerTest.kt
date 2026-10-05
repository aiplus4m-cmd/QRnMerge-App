package net.topvl.qrnmerge

import net.topvl.qrnmerge.scan.EnhanceMode
import net.topvl.qrnmerge.scan.EnhanceSettings
import net.topvl.qrnmerge.scan.ImageEnhancer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEnhancerTest {
    private val w = 240
    private val h = 320

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** Synthetic photo of a page: off-white paper with a strong shadow on the left and dark "text" bars. */
    private fun syntheticPage(): Pair<IntArray, BooleanArray> {
        val px = IntArray(w * h)
        val ink = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val shadow = 0.5f + 0.5f * x / w       // 50% brightness at the left edge
            val isInk = (y / 12) % 3 == 1 && x in 20..(w - 20) && (x / 7) % 4 != 0
            ink[y * w + x] = isInk
            val base = if (isInk) 45f else 225f
            px[y * w + x] = gray((base * shadow).toInt())
        }
        return px to ink
    }

    @Test
    fun autoModeWhitensShadowedPaperAndKeepsTextDark() {
        val (px, ink) = syntheticPage()
        val out = ImageEnhancer.process(px, w, h, EnhanceSettings(EnhanceMode.AUTO, sharpness = 0f, contrast = 0.5f))
        var paperSum = 0L; var paperN = 0; var inkSum = 0L; var inkN = 0
        // Ignore a margin of 2px around ink where sharpening/blur may bleed.
        for (y in 2 until h - 2) for (x in 2 until w - 2) {
            val i = y * w + x
            val l = ImageEnhancer.luminance(out[i])
            val nearInk = (-2..2).any { dy -> (-2..2).any { dx -> ink[(y + dy) * w + x + dx] } }
            if (ink[i]) { inkSum += l; inkN++ } else if (!nearInk) { paperSum += l; paperN++ }
        }
        val paper = paperSum / paperN
        val text = inkSum / inkN
        assertTrue("paper should become white, was $paper", paper >= 235)
        assertTrue("text should stay dark, was $text", text <= 90)
        // The shadowed left part of the paper must also be white now.
        assertTrue(ImageEnhancer.luminance(out[5 * w + 3]) >= 225)
    }

    @Test
    fun bwModeProducesPureBlackAndWhite() {
        val (px, ink) = syntheticPage()
        val out = ImageEnhancer.process(px, w, h, EnhanceSettings(EnhanceMode.BW))
        val values = out.map { ImageEnhancer.luminance(it) }.toSet()
        assertTrue("only black/white expected: $values", values.all { it == 0 || it == 255 })
        var inkBlack = 0; var inkN = 0
        for (i in ink.indices) if (ink[i]) { inkN++; if (ImageEnhancer.luminance(out[i]) == 0) inkBlack++ }
        assertTrue("most ink pixels should be black ($inkBlack/$inkN)", inkBlack > inkN * 0.85)
        assertEquals(255, ImageEnhancer.luminance(out[2 * w + 2]))
    }

    @Test
    fun grayModeOutputsNeutralPixels() {
        val (px, _) = syntheticPage()
        val out = ImageEnhancer.process(px, w, h, EnhanceSettings(EnhanceMode.GRAY))
        out.forEach { p ->
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            assertTrue(r == g && g == b)
        }
    }

    @Test
    fun originalWithoutSharpeningIsIdentity() {
        val (px, _) = syntheticPage()
        val out = ImageEnhancer.process(px, w, h, EnhanceSettings(EnhanceMode.ORIGINAL, sharpness = 0f))
        assertArrayEquals(px, out)
        assertTrue(out !== px)
    }

    @Test
    fun sharpeningIncreasesEdgeContrast() {
        val src = IntArray(20 * 20) { i -> if (i % 20 < 10) 100 else 160 }
        val out = ImageEnhancer.sharpenGray(src, 20, 20, 1.5f)
        assertTrue(out[5 * 20 + 9] < 100)
        assertTrue(out[5 * 20 + 10] > 160)
        assertEquals(100, out[5 * 20 + 2])
    }

    @Test
    fun colorIsPreservedInAutoMode() {
        // Red stamp on white paper must stay red.
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in 100..140 && y in 100..140) (0xFF shl 24) or (200 shl 16) or (30 shl 8) or 30 else gray(230)
        }
        val out = ImageEnhancer.process(px, w, h, EnhanceSettings(EnhanceMode.AUTO, sharpness = 0f))
        val p = out[120 * w + 120]
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF
        assertTrue("red channel should dominate: r=$r g=$g", r > 150 && g < 60)
    }

    @Test
    fun tinyImagesAreHandled() {
        val px = IntArray(4) { gray(128) }
        assertEquals(4, ImageEnhancer.process(px, 2, 2, EnhanceSettings()).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedBufferThrows() {
        ImageEnhancer.process(IntArray(10), 4, 4, EnhanceSettings())
    }
}
