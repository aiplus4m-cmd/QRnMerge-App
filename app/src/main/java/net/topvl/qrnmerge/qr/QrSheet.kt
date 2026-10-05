package net.topvl.qrnmerge.qr

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.ui.BrandBlue
import net.topvl.qrnmerge.ui.BrandOrange
import net.topvl.qrnmerge.util.QR_TOOL_URL
import net.topvl.qrnmerge.util.openUrl
import net.topvl.qrnmerge.util.safeStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrSheet(onDismiss: () -> Unit, autoScan: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { QrHistory(context) }
    var historyItems by remember { mutableStateOf(history.load()) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun handle(outcome: QrScanOutcome) {
        when (outcome) {
            is QrScanOutcome.Found -> {
                result = outcome.value
                error = null
                historyItems = history.add(outcome.value)
            }
            QrScanOutcome.NotFound -> error = context.getString(R.string.qr_not_found)
            is QrScanOutcome.Error -> error = context.getString(R.string.qr_error, outcome.message)
            QrScanOutcome.Cancelled -> Unit
        }
    }

    val scanCamera: () -> Unit = { scope.launch { handle(QrScanner.scanWithCamera(context)) } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { handle(QrScanner.scanImage(context, uri)) }
    }

    LaunchedEffect(Unit) { if (autoScan) scanCamera() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("qr_sheet")) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCodeScanner, null, tint = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.qr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            val current = result
            when {
                current != null -> QrResultCard(current)
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.qr_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = scanCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_scan_again))
                }
                OutlinedButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_from_image))
                }
            }

            Spacer(Modifier.height(16.dp))
            QrPromoCard()

            if (historyItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { history.clear(); historyItems = emptyList() }) { Text(stringResource(R.string.qr_history_clear)) }
                }
                historyItems.take(10).forEach { item ->
                    Text(
                        item,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { result = item; error = null }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun QrResultCard(raw: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val content = remember(raw) { QrContent.parse(raw) }
    val (icon, typeLabel) = typeInfo(content)
    val copy: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    Card(
        Modifier.fillMaxWidth().testTag("qr_result"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(BrandBlue), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(typeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            val display = if (content is QrContent.Wifi) {
                stringResource(R.string.qr_wifi_details, content.ssid, content.password, content.security)
            } else raw
            SelectionContainer { Text(display, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                primaryAction(content)?.let { (label, action) ->
                    Button(onClick = { action(context, copy) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(label))
                    }
                }
                OutlinedButton(onClick = { copy(raw) }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy))
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw)
                    context.safeStart(Intent.createChooser(send, null))
                }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
            }
        }
    }
}

@Composable
private fun typeInfo(c: QrContent): Pair<ImageVector, String> = when (c) {
    is QrContent.Url -> Icons.Outlined.Link to stringResource(R.string.qr_type_url)
    is QrContent.Wifi -> Icons.Outlined.Wifi to stringResource(R.string.qr_type_wifi)
    is QrContent.Email -> Icons.Outlined.Email to stringResource(R.string.qr_type_email)
    is QrContent.Phone -> Icons.Outlined.Call to stringResource(R.string.qr_type_phone)
    is QrContent.Sms -> Icons.Outlined.Sms to stringResource(R.string.qr_type_sms)
    is QrContent.Geo -> Icons.Outlined.Place to stringResource(R.string.qr_type_geo)
    is QrContent.Text -> Icons.AutoMirrored.Outlined.Notes to stringResource(R.string.qr_type_text)
}

private typealias QrAction = (android.content.Context, (String) -> Unit) -> Unit

private fun act(label: Int, f: QrAction): Pair<Int, QrAction> = label to f

private fun primaryAction(c: QrContent): Pair<Int, QrAction>? = when (c) {
    is QrContent.Url -> act(R.string.qr_open_link) { ctx, _ -> ctx.openUrl(c.url) }
    is QrContent.Email -> act(R.string.qr_email) { ctx, _ -> ctx.safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.address}"))) }
    is QrContent.Phone -> act(R.string.qr_call) { ctx, _ -> ctx.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.number}"))) }
    is QrContent.Sms -> act(R.string.qr_sms) { ctx, _ ->
        ctx.safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.number}")).putExtra("sms_body", c.body))
    }
    is QrContent.Geo -> act(R.string.qr_map) { ctx, _ -> ctx.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(c.uri))) }
    is QrContent.Wifi -> if (c.password.isNotEmpty()) act(R.string.qr_copy_password) { _, copy -> copy(c.password) } else null
    is QrContent.Text -> null
}

/** Promotion for the free NhảmStudio QR generator, shown whenever QR scanning is used. */
@Composable
fun QrPromoCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(BrandOrange, Color(0xFFE5672A))))
            .clickable { context.openUrl(QR_TOOL_URL) }
            .testTag("qr_promo")
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.QrCode2, null, tint = BrandOrange, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.qr_promo_title), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.qr_promo_body), color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { context.openUrl(QR_TOOL_URL) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandOrange),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.qr_promo_button) + " · topvl.net/qr", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
