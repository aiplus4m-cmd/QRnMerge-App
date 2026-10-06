package net.topvl.qrnmerge.scan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.merge.PageSizeMode
import net.topvl.qrnmerge.qr.QrSheet
import net.topvl.qrnmerge.ui.BrandBlue
import net.topvl.qrnmerge.ui.BrandOrange
import net.topvl.qrnmerge.util.FileNames
import net.topvl.qrnmerge.util.MediaSaver

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun ScanScreen(vm: ScanViewModel, snackbar: SnackbarHostState, onSendToMerge: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQr by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages.orEmpty()
            vm.addImages(pages.map { it.imageUri })
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }
    val pickGallery: () -> Unit = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val startScan: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.scan_unavailable), duration = SnackbarDuration.Long) }
                }
        }
    }

    LaunchedEffect(vm.message) {
        val msg = vm.message ?: return@LaunchedEffect
        vm.message = null
        val saved = vm.lastSaved
        val result = snackbar.showSnackbar(
            msg,
            actionLabel = if (saved.isNotEmpty()) context.getString(R.string.open) else null,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed && saved.isNotEmpty()) {
            try {
                context.startActivity(MediaSaver.viewIntent(saved.first()))
            } catch (e: ActivityNotFoundException) {
                snackbar.showSnackbar(context.getString(R.string.no_app_to_open))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScanHeader(pageCount = vm.pages.size, onQr = { showQr = true })
        if (vm.pages.isEmpty()) {
            ScanHero(onScan = startScan, onGallery = pickGallery)
        } else {
            ScanWorkspace(
                vm = vm,
                onPreview = { previewIndex = it },
                onScan = startScan,
                onGallery = pickGallery,
                onSave = { showSave = true },
                onDelete = { confirmDelete = true },
                onSendToMerge = { vm.exportForMerge(onSendToMerge) },
            )
        }
    }

    if (showQr) QrSheet(onDismiss = { showQr = false })

    previewIndex?.let { index ->
        if (vm.pages.isEmpty()) {
            previewIndex = null
        } else {
            PagePreviewDialog(vm, index.coerceIn(0, vm.pages.lastIndex), onDismiss = { previewIndex = null })
        }
    }

    if (showSave) SaveSheet(vm, onDismiss = { showSave = false })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text(stringResource(R.string.scan_delete_selected, vm.selected.size)) },
            text = { Text(stringResource(R.string.scan_delete_confirm, vm.selected.size)) },
            confirmButton = {
                TextButton(onClick = { vm.removeSelected(); confirmDelete = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    vm.busyMessage?.let { BusyDialog(it) }
}

@Composable
private fun ScanHeader(pageCount: Int, onQr: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (pageCount == 0) stringResource(R.string.scan_subtitle) else stringResource(R.string.scan_pages, pageCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QrCornerTile(onQr)
    }
}

/** The small QR box in the corner of the scan tab, with an animated scan line. */
@Composable
fun QrCornerTile(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "qr")
    val linePos by transition.animateFloat(
        initialValue = 0.15f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "line",
    )
    val size = 60.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(BrandBlue, Color(0xFF15508A))))
                .border(2.dp, BrandOrange, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .testTag("qr_tile"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.qr_title), tint = Color.White, modifier = Modifier.size(32.dp))
            val offset = with(LocalDensity.current) { (size.toPx() * linePos).toDp() }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = offset).fillMaxWidth(0.75f).height(2.dp)
                    .background(BrandOrange.copy(alpha = 0.9f)),
            )
        }
        Text(stringResource(R.string.qr_tile), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BrandBlue)
    }
}

@Composable
private fun ScanHero(onScan: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(140.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = BrandBlue, modifier = Modifier.size(76.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.scan_hero_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                listOf(R.string.scan_feature_1, R.string.scan_feature_2, R.string.scan_feature_3).forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = BrandOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("scan_start"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_start), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_from_gallery))
                }
            }
        }
    }
}

@Composable
private fun ScanWorkspace(
    vm: ScanViewModel,
    onPreview: (Int) -> Unit,
    onScan: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onSendToMerge: () -> Unit,
) {
    val settings = vm.settings
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    val selectedCount = vm.selected.size
    Column(Modifier.fillMaxSize()) {
        // Filters and the selection bar scroll with the pages so small screens keep room for the grid.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("scan_grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                EnhanceMode.AUTO to R.string.scan_mode_auto,
                                EnhanceMode.GRAY to R.string.scan_mode_gray,
                                EnhanceMode.BW to R.string.scan_mode_bw,
                                EnhanceMode.ORIGINAL to R.string.scan_mode_original,
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.mode == mode,
                                    onClick = { vm.updateSettings(settings.copy(mode = mode)) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                        IconButton(onClick = { showAdjust = !showAdjust }) {
                            Icon(
                                Icons.Outlined.Tune, stringResource(R.string.scan_adjust),
                                tint = if (showAdjust) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AnimatedVisibility(showAdjust) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CompactSlider(stringResource(R.string.scan_sharpness), settings.sharpness, 0f..1.5f, Modifier.weight(1f)) {
                                vm.updateSettings(vm.settings.copy(sharpness = it))
                            }
                            CompactSlider(
                                stringResource(R.string.scan_contrast), settings.contrast, 0f..1f, Modifier.weight(1f),
                                enabled = settings.mode != EnhanceMode.ORIGINAL,
                            ) { vm.updateSettings(vm.settings.copy(contrast = it)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val state = when (selectedCount) {
                            0 -> ToggleableState.Off
                            vm.pages.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        TriStateCheckbox(
                            state = state,
                            onClick = { vm.selectAll(state != ToggleableState.On) },
                            modifier = Modifier.testTag("select_all"),
                        )
                        Text(
                            stringResource(R.string.scan_selected, selectedCount, vm.pages.size),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.scan_delete_selected, selectedCount))
                        }
                    }
                }
            }
            itemsIndexed(vm.pages, key = { _, p -> p.id }) { index, page ->
                PageThumb(
                    number = index + 1,
                    page = page,
                    settings = settings,
                    selected = page.id in vm.selected,
                    onClick = { onPreview(index) },
                    onToggle = { vm.toggleSelected(page) },
                    onRotate = { vm.rotate(page) },
                )
            }
        }

        // Action bar – scanning more is always one tap away, saving acts on the selection only.
        Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("scan_more"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandOrange, contentColor = Color.White),
                    ) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_more), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalIconButton(onClick = onGallery, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.scan_import))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (selectedCount == 0) {
                    Text(
                        stringResource(R.string.select_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSendToMerge, enabled = selectedCount > 0,
                        modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.MergeType, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_send_merge, selectedCount))
                    }
                    Button(
                        onClick = onSave, enabled = selectedCount > 0,
                        modifier = Modifier.weight(1f).height(44.dp).testTag("scan_save"), shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_save_selected, selectedCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit,
) {
    // Local state while dragging; heavy re-rendering only happens when the finger is lifted.
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = local, onValueChange = { local = it }, valueRange = range, enabled = enabled,
            onValueChangeFinished = { onCommit(local) },
        )
    }
}

@Composable
private fun rememberRendered(page: ScanPage, settings: EnhanceSettings, maxSide: Int): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(null, page.id, page.rotation, settings, maxSide) {
        value = withContext(Dispatchers.Default) {
            runCatching { PageRenderer.render(context, page, settings, maxSide)?.asImageBitmap() }.getOrNull()
        }
    }
    return state.value
}

@Composable
private fun SavedBadges(page: ScanPage, modifier: Modifier = Modifier) {
    if (!page.savedAsImage && !page.savedInPdf) return
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2E9D57)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        val kinds = listOfNotNull(if (page.savedAsImage) "JPG" else null, if (page.savedInPdf) "PDF" else null).joinToString(" · ")
        Text(kinds, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PageThumb(
    number: Int,
    page: ScanPage,
    settings: EnhanceSettings,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRotate: () -> Unit,
) {
    val bmp = rememberRendered(page, settings, 700)
    Card(
        Modifier.fillMaxWidth().aspectRatio(0.72f).clickable(onClick = onClick).testTag("scan_page_$number"),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(3.dp, BrandBlue) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (bmp == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
            } else {
                Image(bmp, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            }
            Text(
                "$number",
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(BrandOrange)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            ) {
                Checkbox(
                    checked = selected, onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("scan_check_$number"),
                    colors = CheckboxDefaults.colors(checkedColor = BrandBlue),
                )
            }
            SavedBadges(page, Modifier.align(Alignment.BottomStart).padding(8.dp))
            IconButton(
                onClick = onRotate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(36.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Outlined.RotateRight, stringResource(R.string.rotate), tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PagePreviewDialog(vm: ScanViewModel, startIndex: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex) { vm.pages.size }
    var showOriginal by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF5101418))) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(top = 64.dp, bottom = 88.dp),
                key = { vm.pages.getOrNull(it)?.id ?: it },
            ) { i ->
                val page = vm.pages.getOrNull(i) ?: return@HorizontalPager
                val enhanced = rememberRendered(page, vm.settings, 1800)
                val original = if (showOriginal) rememberRendered(page, EnhanceSettings(EnhanceMode.ORIGINAL, sharpness = 0f), 1800) else null
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp).pointerInput(page.id) {
                        detectTapGestures(onPress = {
                            showOriginal = true
                            tryAwaitRelease()
                            showOriginal = false
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = original ?: enhanced
                    if (shown == null) CircularProgressIndicator()
                    else Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }

            val current = vm.pages.getOrNull(pager.currentPage)
            Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = Color.White) }
                Text(
                    stringResource(R.string.page_x_of_y, pager.currentPage + 1, vm.pages.size),
                    color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                if (current != null) SavedBadges(current, Modifier.padding(end = 8.dp))
                IconButton(onClick = { current?.let(vm::rotate) }) {
                    Icon(Icons.Outlined.RotateRight, stringResource(R.string.rotate), tint = Color.White)
                }
                IconButton(onClick = {
                    current?.let(vm::remove)
                    if (vm.pages.isEmpty()) onDismiss()
                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = Color.White) }
            }

            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            current?.let { vm.move(it, -1); scope.launch { pager.scrollToPage(pager.currentPage - 1) } }
                        },
                        enabled = pager.currentPage > 0,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.move_earlier), tint = Color.White) }
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f))
                            .clickable { current?.let(vm::toggleSelected) }.padding(end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = current != null && current.id in vm.selected,
                            onCheckedChange = { current?.let(vm::toggleSelected) },
                            colors = CheckboxDefaults.colors(checkedColor = BrandOrange, uncheckedColor = Color.White),
                        )
                        Text(stringResource(R.string.select_page), color = Color.White)
                    }
                    IconButton(
                        onClick = {
                            current?.let { vm.move(it, 1); scope.launch { pager.scrollToPage(pager.currentPage + 1) } }
                        },
                        enabled = pager.currentPage < vm.pages.lastIndex,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.move_later), tint = Color.White) }
                }
                Text(
                    stringResource(R.string.scan_hold_compare),
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(vm: ScanViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var format by rememberSaveable { mutableStateOf(SaveFormat.PDF) }
    var name by rememberSaveable { mutableStateOf(FileNames.defaultPdfName("Scan")) }
    var pageSize by rememberSaveable { mutableStateOf(PageSizeMode.A4) }
    val targets = vm.selectedPages
    val alreadySaved = targets.count { if (format == SaveFormat.IMAGES) it.savedAsImage else it.savedInPdf }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("save_sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.save_sheet_title, targets.size),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            FormatOption(
                selected = format == SaveFormat.PDF, icon = Icons.Outlined.PictureAsPdf, tint = BrandBlue,
                title = stringResource(R.string.save_as_pdf), desc = stringResource(R.string.save_as_pdf_desc),
                tag = "save_format_pdf",
            ) { format = SaveFormat.PDF }
            Spacer(Modifier.height(10.dp))
            FormatOption(
                selected = format == SaveFormat.IMAGES, icon = Icons.Outlined.Image, tint = BrandOrange,
                title = stringResource(R.string.save_as_images), desc = stringResource(R.string.save_as_images_desc),
                tag = "save_format_images",
            ) { format = SaveFormat.IMAGES }

            AnimatedVisibility(format == SaveFormat.PDF) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.merge_output_name)) },
                        suffix = { Text(".pdf") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    val options = listOf(PageSizeMode.A4 to R.string.merge_page_a4, PageSizeMode.FIT_IMAGE to R.string.merge_page_fit)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = pageSize == mode, onClick = { pageSize = mode },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
            }

            if (alreadySaved > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = BrandOrange)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.save_dup_warning, alreadySaved), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    vm.saveSelected(format, name, pageSize)
                    onDismiss()
                },
                enabled = targets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_confirm"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FormatOption(
    selected: Boolean,
    icon: ImageVector,
    tint: Color,
    title: String,
    desc: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) tint else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
fun BusyDialog(message: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
