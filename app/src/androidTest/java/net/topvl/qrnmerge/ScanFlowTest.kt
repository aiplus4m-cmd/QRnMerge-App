package net.topvl.qrnmerge

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.topvl.qrnmerge.scan.EnhanceMode
import net.topvl.qrnmerge.scan.EnhanceSettings
import net.topvl.qrnmerge.scan.SaveFormat
import net.topvl.qrnmerge.scan.ScanViewModel
import net.topvl.qrnmerge.util.MediaSaver
import net.topvl.qrnmerge.util.SavedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScanFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as Application
    private lateinit var files: TestFiles
    private lateinit var vm: ScanViewModel
    private val saved = mutableListOf<SavedFile>()

    @Before
    fun setUp() {
        files = TestFiles(context)
        ScanViewModel(app).clear()
        vm = ScanViewModel(app)
    }

    @After
    fun tearDown() {
        saved.forEach { TestFiles.delete(context, it) }
        ScanViewModel(app).clear()
    }

    /** Three portrait "scans" (1000x1400). */
    private fun importThree() = runBlocking {
        val uris = (1..3).map { Uri.fromFile(files.image("scan$it.jpg", 1000, 1400)) }
        assertEquals(0, vm.importImages(uris))
    }

    @Test
    fun importedPagesAreSelectedByDefault() {
        importThree()
        assertEquals(3, vm.pages.size)
        assertEquals(vm.pages.map { it.id }.toSet(), vm.selected)
    }

    @Test
    fun newestScanBatchIsShownFirst() = runBlocking {
        val first = (1..2).map { files.image("a$it.jpg", 800, 1000) }
        val second = (1..2).map { files.image("b$it.jpg", 800, 1000) }
        vm.importImages(first.map { Uri.fromFile(it) })
        val firstIds = vm.pages.map { it.id }
        vm.importImages(second.map { Uri.fromFile(it) })
        val secondIds = vm.pages.map { it.id }.filterNot { it in firstIds }
        // Latest batch on top, pages inside a batch keep their scan order.
        assertEquals(secondIds + firstIds, vm.pages.map { it.id })
        assertTrue(secondIds[0] < secondIds[1])
        assertEquals(secondIds + firstIds, ScanViewModel(app).pages.map { it.id })
    }

    @Test
    fun rotationOrderAndFilterSurviveAppRestart() {
        importThree()
        val second = vm.pages[1]
        vm.rotate(second)
        vm.rotate(vm.pages[1])                       // 180°
        vm.move(vm.pages[2], -2)                     // third page becomes first
        vm.updateSettings(EnhanceSettings(EnhanceMode.BW, 0.9f, 0.3f))
        val order = vm.pages.map { it.id }

        val reopened = ScanViewModel(app)             // simulates killing and reopening the app
        assertEquals(order, reopened.pages.map { it.id })
        assertEquals(180, reopened.pages.first { it.id == second.id }.rotation)
        assertEquals(EnhanceMode.BW, reopened.settings.mode)
        assertEquals(0.9f, reopened.settings.sharpness, 0.001f)
    }

    @Test
    fun savingImagesOnlySavesSelectedPagesAndMarksThem() = runBlocking {
        importThree()
        vm.selectAll(false)
        vm.toggleSelected(vm.pages[0])
        vm.toggleSelected(vm.pages[2])

        val result = vm.save(SaveFormat.IMAGES, "")
        saved += result
        assertEquals(2, result.size)
        result.forEach {
            assertEquals("Pictures/Scan2PDF", it.folder)
            assertEquals(MediaSaver.MIME_JPEG, it.mimeType)
            assertTrue(TestFiles.readAll(context, it.uri).size > 1000)
        }
        assertTrue(vm.pages[0].savedAsImage)
        assertFalse(vm.pages[1].savedAsImage)
        assertTrue(vm.pages[2].savedAsImage)
        assertTrue("selection is cleared after saving so a second tap cannot duplicate", vm.selected.isEmpty())
        // Saved flags are persisted too.
        assertTrue(ScanViewModel(app).pages[2].savedAsImage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun savingWithNothingSelectedIsRejected() {
        importThree()
        vm.selectAll(false)
        runBlocking { vm.save(SaveFormat.PDF, "nothing") }
    }

    @Test
    fun pdfContainsSelectedPagesWithRotationApplied() = runBlocking {
        importThree()
        vm.rotate(vm.pages[1])                       // 90° -> landscape page
        val name = "scan_pdf_${System.nanoTime()}"
        val result = vm.save(SaveFormat.PDF, name)
        saved += result
        assertEquals(1, result.size)
        assertEquals("$name.pdf", result[0].displayName)
        assertEquals("Download/Scan2PDF", result[0].folder)
        val sizes = TestFiles.pageSizes(context, result[0].uri)
        assertEquals(3, sizes.size)
        fun near(a: Pair<Int, Int>, b: Pair<Int, Int>) = abs(a.first - b.first) <= 2 && abs(a.second - b.second) <= 2
        assertTrue(near(595 to 842, sizes[0]))
        assertTrue("rotated page must be landscape: ${sizes[1]}", near(842 to 595, sizes[1]))
        assertTrue(near(595 to 842, sizes[2]))
        assertTrue(vm.pages.all { it.savedInPdf && !it.savedAsImage })
    }

    @Test
    fun deletingSelectedPagesRemovesFiles() {
        importThree()
        val victims = vm.pages.take(2)
        vm.selectAll(false)
        victims.forEach(vm::toggleSelected)
        vm.removeSelected()
        assertEquals(1, vm.pages.size)
        victims.forEach { assertFalse(it.file.exists()) }
        assertEquals(1, ScanViewModel(app).pages.size)
    }
}
