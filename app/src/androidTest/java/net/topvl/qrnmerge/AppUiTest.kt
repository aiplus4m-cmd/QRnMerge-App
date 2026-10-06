package net.topvl.qrnmerge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.topvl.qrnmerge.merge.MergeStatus
import net.topvl.qrnmerge.merge.MergeViewModel
import net.topvl.qrnmerge.scan.ScanViewModel
import net.topvl.qrnmerge.util.SavedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end: drive the real UI, merge, and verify the PDF landed on the device. */
@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val saved = mutableListOf<SavedFile>()
    private val context get() = rule.activity

    @Before
    fun setUp() = TestFiles.grantLegacyStorage()

    @After
    fun tearDown() {
        saved.forEach { TestFiles.delete(context, it) }
        rule.runOnUiThread { scanVm().clear() }
    }

    private fun mergeVm() = ViewModelProvider(rule.activity)[MergeViewModel::class.java]
    private fun scanVm() = ViewModelProvider(rule.activity)[ScanViewModel::class.java]

    @Before
    fun cleanScans() = rule.runOnUiThread { scanVm().clear() }

    @Test
    fun scanTabShowsDocumentScannerAndQrCornerTile() {
        rule.onNodeWithTag("scan_start").assertIsDisplayed()
        rule.onNodeWithTag("qr_tile").assertIsDisplayed()
    }

    @Test
    fun aboutTabShowsDeveloperInfo() {
        rule.onNodeWithTag("tab_about").performClick()
        rule.onNodeWithTag("about_logo").assertIsDisplayed()
        rule.onNodeWithText("NhảmStudio").assertIsDisplayed()
        rule.onNodeWithText("https://topvl.net").assertIsDisplayed()
        rule.onNodeWithTag("qr_promo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mergeThroughUiSavesPdfOnDevice() {
        val files = TestFiles(context)
        val uris = listOf(
            files.uri(files.pdf("first.pdf", 2, 300, 400)),
            files.uri(files.image("second.jpg", 900, 1200)),
            files.uri(files.pdf("third.pdf", 1, 400, 300)),
        )
        rule.runOnUiThread { mergeVm().addUrisBlocking(uris) }
        rule.onNodeWithTag("tab_merge").performClick()
        rule.onNodeWithTag("merge_summary").assertIsDisplayed()
        rule.onNodeWithText("first.pdf").assertIsDisplayed()

        val name = "ui_merge_${System.nanoTime()}"
        rule.onNodeWithTag("merge_name").performTextReplacement(name)
        rule.onNodeWithTag("merge_button").performClick()

        rule.waitUntil(60_000) { rule.onAllNodes(hasTestTag("merge_done")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("merge_done_text").assertIsDisplayed()

        val status = mergeVm().status
        assertTrue("status was $status", status is MergeStatus.Done)
        val result = (status as MergeStatus.Done).result
        saved += result.saved
        assertEquals("$name.pdf", result.saved.displayName)
        assertEquals(4, result.pageCount)
        assertEquals(4, TestFiles.pageSizes(context, result.saved.uri).size)
        assertTrue(TestFiles.existsInDownloads(context, result.saved.displayName))

        // "Done" clears the list so a second tap on Merge cannot create a duplicate.
        rule.onNodeWithTag("merge_done_ok").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("merge_done")).fetchSemanticsNodes().isEmpty() }
        assertTrue(mergeVm().items.isEmpty())
    }

    @Test
    fun scanTabSavesOnlyTickedPagesAsImages() {
        val files = TestFiles(context)
        val uris = (1..3).map { android.net.Uri.fromFile(files.image("p$it.jpg", 900, 1200)) }
        kotlinx.coroutines.runBlocking { scanVm().importImages(uris) }
        rule.waitForIdle()

        // "Scan more" stays visible in the action bar once pages exist.
        rule.onNodeWithTag("scan_more").assertIsDisplayed()
        rule.onNodeWithTag("select_all").assertIsDisplayed()

        // Untick page 2 -> only pages 1 and 3 are saved.
        rule.onNodeWithTag("scan_grid").performScrollToNode(hasTestTag("scan_page_2"))
        rule.onNodeWithTag("scan_check_2", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("scan_save").performClick()
        rule.onNodeWithTag("save_format_images").performClick()
        rule.onNodeWithTag("save_confirm").performClick()
        rule.waitUntil(60_000) { scanVm().lastSaved.isNotEmpty() && scanVm().busyMessage == null }

        val result = scanVm().lastSaved
        saved += result
        assertEquals(2, result.size)
        assertTrue(scanVm().selected.isEmpty())
        assertEquals(listOf(true, false, true), scanVm().pages.map { it.savedAsImage })
    }

    @Test
    fun mergeErrorIsShownToUser() {
        val files = TestFiles(context)
        rule.runOnUiThread {
            mergeVm().addUrisBlocking(listOf(files.uri(files.garbage("broken.pdf"))))
        }
        rule.onNodeWithTag("tab_merge").performClick()
        rule.onNodeWithTag("merge_button").performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(hasTestTag("merge_error")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(mergeVm().status is MergeStatus.Failed)
    }
}
