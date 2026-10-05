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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import net.topvl.qrnmerge.qr.QrSheet
import net.topvl.qrnmerge.ui.BrandBlue
import net.topvl.qrnmerge.ui.BrandOrange
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
    var preview by remember { mutableStateOf<ScanPage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages.orEmpty()
            vm.addImages(pages.map { it.imageUri })
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }
    val pickGallery = {
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScanHeader(
                pageCount = vm.pages.size,
                onQr = { showQr = true },
                onClear = { confirmClear = true },
            )
            if (vm.pages.isEmpty()) {
                ScanHero(onScan = startScan, onGallery = pickGallery)
            } else {
                ScanWorkspace(
                    vm = vm,
                    onPreview = { preview = it },
                    onScan = startScan,
                    onGallery = pickGallery,
                    onSendToMerge = { vm.exportForMerge(onSendToMerge) },
                )
            }
        }
    }

    if (showQr) QrSheet(onDismiss = { showQr = false })

    preview?.let { page ->
        val current = vm.pages.firstOrNull { it.id == page.id }
        if (current == null) preview = null
        else PagePreviewDialog(
            page = current,
            settings = vm.settings,
            onDismiss = { preview = null },
            onRotate = { vm.rotate(current) },
            onDelete = { vm.remove(current); preview = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.scan_clear)) },
            text = { Text(stringResource(R.string.scan_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.clear(); confirmClear = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    vm.busyMessage?.let { BusyDialog(it) }
}

@Composable
private fun ScanHeader(pageCount: Int, onQr: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
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
        if (pageCount > 0) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.scan_clear)) }
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
    onPreview: (ScanPage) -> Unit,
    onScan: () -> Unit,
    onGallery: () -> Unit,
    onSendToMerge: () -> Unit,
) {
    val settings = vm.settings
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
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
                    onClick = { vm.settings = settings.copy(mode = mode) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompactSlider(stringResource(R.string.scan_sharpness), settings.sharpness, 0f..1.5f, Modifier.weight(1f)) {
                vm.settings = vm.settings.copy(sharpness = it)
            }
            CompactSlider(
                stringResource(R.string.scan_contrast), settings.contrast, 0f..1f, Modifier.weight(1f),
                enabled = settings.mode != EnhanceMode.ORIGINAL,
            ) { vm.settings = vm.settings.copy(contrast = it) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(vm.pages, key = { _, p -> p.id }) { index, page ->
                PageThumb(index + 1, page, settings, onClick = { onPreview(page) }, onRotate = { vm.rotate(page) }, onDelete = { vm.remove(page) })
            }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var addMenu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalButton(onClick = { addMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(R.string.scan_add_page))
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_start)) },
                            leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                            onClick = { addMenu = false; onScan() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_from_gallery)) },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = { addMenu = false; onGallery() },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ActionButton(Icons.Outlined.Image, stringResource(R.string.scan_save_images)) { vm.saveImages() }
                ActionButton(Icons.Outlined.PictureAsPdf, stringResource(R.string.scan_save_pdf)) { vm.savePdf() }
                Button(onClick = onSendToMerge, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.scan_to_merge))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
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
    val state = produceState<ImageBitmap?>(null, page, settings, maxSide) {
        value = withContext(Dispatchers.Default) {
            runCatching { PageRenderer.render(context, page, settings, maxSide)?.asImageBitmap() }.getOrNull()
        }
    }
    return state.value
}

@Composable
private fun PageThumb(
    number: Int,
    page: ScanPage,
    settings: EnhanceSettings,
    onClick: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    val bmp = rememberRendered(page, settings, 700)
    Card(
        Modifier.fillMaxWidth().aspectRatio(0.72f).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
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
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                IconButton(onClick = onRotate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.RotateRight, stringResource(R.string.rotate), tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PagePreviewDialog(
    page: ScanPage,
    settings: EnhanceSettings,
    onDismiss: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showOriginal by remember { mutableStateOf(false) }
    val enhanced = rememberRendered(page, settings, 1800)
    val original = rememberRendered(page, EnhanceSettings(EnhanceMode.ORIGINAL, sharpness = 0f), 1800)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0101418))) {
            val shown = if (showOriginal) original else enhanced
            Box(
                Modifier.fillMaxSize().padding(top = 64.dp, bottom = 56.dp, start = 12.dp, end = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            showOriginal = true
                            tryAwaitRelease()
                            showOriginal = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (shown == null) CircularProgressIndicator()
                else Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Row(Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRotate) { Icon(Icons.Outlined.RotateRight, stringResource(R.string.rotate), tint = Color.White) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = Color.White) }
            }
            Text(
                stringResource(R.string.scan_hold_compare),
                Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
            )
        }
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
