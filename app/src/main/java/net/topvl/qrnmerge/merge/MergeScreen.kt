package net.topvl.qrnmerge.merge

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.ui.BrandBlue
import net.topvl.qrnmerge.ui.BrandOrange
import net.topvl.qrnmerge.util.FileNames
import net.topvl.qrnmerge.util.MediaSaver
import net.topvl.qrnmerge.util.safeStart
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun MergeScreen(vm: MergeViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { vm.addUris(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addUris(it) }
    val addImages = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val addFiles = { pickFiles.launch(arrayOf("application/pdf", "image/*")) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.merge()
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.merge_storage_permission)) }
    }
    val startMerge = {
        if (MediaSaver.needsLegacyPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.merge()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.merge_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.items.isEmpty()) stringResource(R.string.merge_subtitle_empty)
                    else stringResource(R.string.merge_summary, vm.items.size, vm.totalPages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("merge_summary"),
                )
            }
            if (vm.items.isNotEmpty()) {
                IconButton(onClick = vm::clear) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.merge_clear)) }
            }
        }

        if (vm.items.isEmpty()) {
            EmptyMerge(addImages, addFiles, vm.loading)
        } else {
            MergeList(vm, Modifier.weight(1f))
            MergeBottomPanel(vm, addImages, addFiles, startMerge)
        }
    }

    when (val s = vm.status) {
        is MergeStatus.Working -> ProgressDialog(s)
        is MergeStatus.Done -> DoneDialog(s.result, onDismiss = vm::dismissStatus, onClear = { vm.clear() })
        is MergeStatus.Failed -> AlertDialog(
            onDismissRequest = vm::dismissStatus,
            icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.merge_failed_title)) },
            text = { Text(s.message, modifier = Modifier.testTag("merge_error")) },
            confirmButton = { TextButton(onClick = vm::dismissStatus) { Text(stringResource(R.string.ok)) } },
        )
        MergeStatus.Idle -> Unit
    }
}

@Composable
private fun EmptyMerge(addImages: () -> Unit, addFiles: () -> Unit, loading: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) CircularProgressIndicator()
                    else Icon(Icons.Outlined.MergeType, null, tint = BrandOrange, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.merge_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.merge_empty_body), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                AddButtons(addImages, addFiles, large = true)
            }
        }
    }
}

@Composable
private fun AddButtons(addImages: () -> Unit, addFiles: () -> Unit, large: Boolean) {
    val h = if (large) 52.dp else 40.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = addImages, modifier = Modifier.weight(1f).height(h).testTag("add_images"), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("+ " + stringResource(R.string.merge_add_images))
        }
        FilledTonalButton(onClick = addFiles, modifier = Modifier.weight(1f).height(h).testTag("add_files"), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.NoteAdd, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("+ " + stringResource(R.string.merge_add_files))
        }
    }
}

@Composable
private fun MergeList(vm: MergeViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        vm.move(from.key as Long, to.key as Long)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("merge_list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(vm.items, key = { _, item -> item.id }) { index, item ->
            ReorderableItem(reorderState, key = item.id) { dragging ->
                Card(
                    Modifier.fillMaxWidth().shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}", color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(CircleShape).background(if (item.isPdf) BrandBlue else BrandOrange)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Thumb(item)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (item.isPdf) stringResource(R.string.merge_item_pdf, item.pageCount?.toString() ?: "?", FileNames.humanSize(item.sizeBytes))
                                else stringResource(R.string.merge_item_image, FileNames.humanSize(item.sizeBytes)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            IconButton(onClick = { vm.moveBy(item.id, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.merge_move_up))
                            }
                            IconButton(onClick = { vm.moveBy(item.id, 1) }, enabled = index < vm.items.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.merge_move_down))
                            }
                        }
                        IconButton(onClick = { vm.remove(item.id) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.delete))
                        }
                        Icon(
                            Icons.Outlined.DragHandle, stringResource(R.string.merge_drag_hint),
                            modifier = Modifier.draggableHandle().padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(item: MergeItem) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val mod = Modifier.size(width = 46.dp, height = 60.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
    if (item.isPdf) {
        val bmp by produceState<ImageBitmap?>(null, item.uri) {
            value = withContext(Dispatchers.IO) { PdfInspector.renderFirstPage(context, item.uri, 120)?.asImageBitmap() }
        }
        Box(mod, contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.PictureAsPdf, null, tint = BrandBlue)
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).size(160).crossfade(true).build(),
            contentDescription = null, modifier = mod, contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeBottomPanel(vm: MergeViewModel, addImages: () -> Unit, addFiles: () -> Unit, onMerge: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            AddButtons(addImages, addFiles, large = false)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.outputName,
                onValueChange = { vm.outputName = it },
                label = { Text(stringResource(R.string.merge_output_name)) },
                suffix = { Text(".pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("merge_name"),
            )
            if (vm.items.any { !it.isPdf }) {
                Spacer(Modifier.height(8.dp))
                val options = listOf(PageSizeMode.A4 to R.string.merge_page_a4, PageSizeMode.FIT_IMAGE to R.string.merge_page_fit)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = vm.pageSize == mode,
                            onClick = { vm.pageSize = mode },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onMerge,
                enabled = vm.status !is MergeStatus.Working && !vm.loading,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("merge_button"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.MergeType, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.merge_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ProgressDialog(s: MergeStatus.Working) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp).width(260.dp)) {
                Text(stringResource(R.string.merge_working, s.done, s.total), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DoneDialog(result: MergeResult, onDismiss: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MediaSaver.MIME_PDF)) { uri ->
        if (uri != null) {
            runCatching { MediaSaver.copyToUri(context, result.localFile, uri) }
                .onSuccess { Toast.makeText(context, R.string.merge_saved_copy, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, context.getString(R.string.err_generic, it.message), Toast.LENGTH_LONG).show() }
        }
    }
    val saved = result.saved
    AlertDialog(
        modifier = Modifier.testTag("merge_done"),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2E9D57), modifier = Modifier.size(40.dp)) },
        title = { Text(stringResource(R.string.merge_done_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.merge_done_body, saved.displayName, result.pageCount, FileNames.humanSize(saved.sizeBytes), saved.folder),
                    modifier = Modifier.testTag("merge_done_text"),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.safeStart(MediaSaver.viewIntent(saved)) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.open))
                    }
                    OutlinedButton(
                        onClick = { context.safeStart(MediaSaver.shareIntent(listOf(saved.uri), MediaSaver.MIME_PDF)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.share))
                    }
                }
                TextButton(onClick = { saveAs.launch(saved.displayName) }) {
                    Icon(Icons.Outlined.SaveAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.merge_save_as))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("merge_done_ok")) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.merge_clear)) } },
    )
}
