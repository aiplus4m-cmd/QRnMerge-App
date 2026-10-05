package net.topvl.qrnmerge

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.topvl.qrnmerge.merge.MergeEngine
import net.topvl.qrnmerge.merge.MergeException
import net.topvl.qrnmerge.merge.MergeSource
import net.topvl.qrnmerge.merge.PageSizeMode
import net.topvl.qrnmerge.util.MediaSaver
import net.topvl.qrnmerge.util.SavedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MergeAndSaveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var files: TestFiles
    private val saved = mutableListOf<SavedFile>()

    @Before
    fun setUp() {
        TestFiles.grantLegacyStorage()
        files = TestFiles(context)
    }

    @After
    fun tearDown() {
        saved.forEach { TestFiles.delete(context, it) }
    }

    private fun pdfSource(name: String, pages: Int, w: Int, h: Int) =
        MergeSource(files.uri(files.pdf(name, pages, w, h)), name, isPdf = true)

    private fun imageSource(name: String, w: Int, h: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, alpha: Boolean = false) =
        MergeSource(files.uri(files.image(name, w, h, format, alpha)), name, isPdf = false)

    private fun assertSize(expected: Pair<Int, Int>, actual: Pair<Int, Int>) {
        assertTrue("expected $expected but was $actual", abs(expected.first - actual.first) <= 2 && abs(expected.second - actual.second) <= 2)
    }

    private fun run(sources: List<MergeSource>, name: String, mode: PageSizeMode = PageSizeMode.A4) =
        MergeEngine.mergeAndSave(context, sources, name, mode).also { saved += it.saved }

    @Test
    fun mergesPdfsAndImagesInOrderAndSavesToDownloads() {
        val sources = listOf(
            pdfSource("a.pdf", 3, 300, 400),
            imageSource("photo.jpg", 1200, 1600),
            pdfSource("b.pdf", 2, 500, 300),
        )
        var lastProgress = 0
        val name = "merge_test_${System.nanoTime()}"
        val result = MergeEngine.mergeAndSave(context, sources, name, PageSizeMode.A4) { d, t ->
            assertEquals(3, t); lastProgress = d
        }
        saved += result.saved
        assertEquals(3, lastProgress)
        assertEquals(6, result.pageCount)
        assertEquals("$name.pdf", result.saved.displayName)
        assertEquals("Download/QRnMerge", result.saved.folder)
        assertEquals(MediaSaver.MIME_PDF, result.saved.mimeType)

        // The saved copy on shared storage is a complete, valid PDF.
        val bytes = TestFiles.readAll(context, result.saved.uri)
        assertEquals("%PDF", String(bytes, 0, 4))
        assertEquals(result.localFile.length(), bytes.size.toLong())
        assertTrue(String(bytes, bytes.size - 1024, 1024).contains("%%EOF"))
        assertTrue("file must be visible in Download/QRnMerge", TestFiles.existsInDownloads(context, result.saved.displayName))

        val sizes = TestFiles.pageSizes(context, result.saved.uri)
        assertEquals(6, sizes.size)
        repeat(3) { assertSize(300 to 400, sizes[it]) }
        assertSize(595 to 842, sizes[3])
        assertSize(500 to 300, sizes[4])
        assertSize(500 to 300, sizes[5])
    }

    @Test
    fun savingSameNameTwiceKeepsBothFiles() {
        val name = "dup_${System.nanoTime()}"
        val first = run(listOf(pdfSource("x.pdf", 1, 200, 200)), name)
        val second = run(listOf(pdfSource("y.pdf", 2, 200, 200)), name)
        assertNotEquals(first.saved.uri, second.saved.uri)
        assertNotEquals(first.saved.displayName, second.saved.displayName)
        assertEquals(1, TestFiles.pageSizes(context, first.saved.uri).size)
        assertEquals(2, TestFiles.pageSizes(context, second.saved.uri).size)
    }

    @Test
    fun landscapeImageGetsLandscapeA4() {
        val r = run(listOf(imageSource("wide.jpg", 1600, 900)), "land_${System.nanoTime()}")
        assertSize(842 to 595, TestFiles.pageSizes(context, r.saved.uri)[0])
    }

    @Test
    fun fitImageModeKeepsImageAspectRatio() {
        val r = run(listOf(imageSource("tall.png", 600, 1500, Bitmap.CompressFormat.PNG)), "fit_${System.nanoTime()}", PageSizeMode.FIT_IMAGE)
        val (w, h) = TestFiles.pageSizes(context, r.saved.uri)[0]
        assertEquals(842.0, h.toDouble(), 2.0)
        assertEquals(600.0 / 1500.0, w.toDouble() / h, 0.01)
    }

    @Test
    fun transparentPngAndWebpAreSupported() {
        val r = run(
            listOf(
                imageSource("alpha.png", 800, 800, Bitmap.CompressFormat.PNG, alpha = true),
                imageSource("pic.webp", 640, 480, @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP),
            ),
            "alpha_${System.nanoTime()}",
        )
        assertEquals(2, r.pageCount)
        assertEquals(2, TestFiles.pageSizes(context, r.saved.uri).size)
    }

    @Test
    fun pdfWithOnlyOwnerPasswordIsMerged() {
        val f = files.encryptedPdf("owner_only.pdf", userPassword = "")
        val r = run(listOf(MergeSource(files.uri(f), f.name, true), pdfSource("plain.pdf", 1, 300, 300)), "enc_${System.nanoTime()}")
        assertEquals(3, r.pageCount)
        assertEquals(3, TestFiles.pageSizes(context, r.saved.uri).size)
    }

    @Test
    fun pdfWithUserPasswordReportsPasswordError() {
        val f = files.encryptedPdf("locked.pdf", userPassword = "secret")
        try {
            run(listOf(pdfSource("ok.pdf", 1, 300, 300), MergeSource(files.uri(f), "locked.pdf", true)), "locked_${System.nanoTime()}")
            fail("expected MergeException")
        } catch (e: MergeException) {
            assertEquals(MergeException.Reason.PASSWORD, e.reason)
            assertEquals("locked.pdf", e.fileName)
        }
    }

    @Test
    fun corruptPdfReportsCorruptError() {
        val f = files.garbage("broken.pdf")
        try {
            run(listOf(MergeSource(files.uri(f), "broken.pdf", true)), "broken_${System.nanoTime()}")
            fail("expected MergeException")
        } catch (e: MergeException) {
            assertEquals(MergeException.Reason.CORRUPT, e.reason)
        }
    }

    @Test
    fun unreadableImageReportsImageError() {
        val f = files.garbage("broken.jpg")
        try {
            run(listOf(MergeSource(files.uri(f), "broken.jpg", false)), "badimg_${System.nanoTime()}")
            fail("expected MergeException")
        } catch (e: MergeException) {
            assertEquals(MergeException.Reason.IMAGE_DECODE, e.reason)
        }
    }

    @Test
    fun emptyInputIsRejected() {
        try {
            run(emptyList(), "empty")
            fail("expected MergeException")
        } catch (e: MergeException) {
            assertEquals(MergeException.Reason.EMPTY, e.reason)
        }
    }

    @Test
    fun manyLargePhotosMergeWithoutRunningOutOfMemory() {
        val sources = (1..12).map { imageSource("big_$it.jpg", 3000, 4000) }
        val r = run(sources, "big_${System.nanoTime()}")
        assertEquals(12, r.pageCount)
        assertEquals(12, TestFiles.pageSizes(context, r.saved.uri).size)
    }

    @Test
    fun fileUrisFromTheScanTabWork() {
        val img = files.image("scan.jpg", 1000, 1400)
        val r = run(listOf(MergeSource(android.net.Uri.fromFile(img), img.name, false)), "fileuri_${System.nanoTime()}")
        assertEquals(1, r.pageCount)
    }

    @Test
    fun jpegIsSavedToPictures() {
        val img = files.image("scan_page.jpg", 800, 1000)
        val s = MediaSaver.saveJpeg(context, img, "QRnMerge_test_${System.nanoTime()}.jpg")
        saved += s
        assertEquals("Pictures/QRnMerge", s.folder)
        val bytes = TestFiles.readAll(context, s.uri)
        assertEquals(img.length(), bytes.size.toLong())
        assertEquals(0xFF.toByte(), bytes[0]); assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun saveAsCopiesToChosenUri() {
        val r = run(listOf(pdfSource("c.pdf", 2, 300, 300)), "saveas_${System.nanoTime()}")
        val target = java.io.File(files.dir, "copy.pdf").apply { createNewFile() }
        MediaSaver.copyToUri(context, r.localFile, files.uri(target))
        assertEquals(r.localFile.length(), target.length())
    }
}
