package net.topvl.qrnmerge.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.topvl.qrnmerge.BuildConfig
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.qr.QrPromoCard
import net.topvl.qrnmerge.ui.BrandBlue
import net.topvl.qrnmerge.ui.BrandOrange
import net.topvl.qrnmerge.util.QR_TOOL_URL
import net.topvl.qrnmerge.util.WEBSITE_URL
import net.topvl.qrnmerge.util.openUrl

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The logo artwork has a white background, so it always sits on a white card.
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Image(
                painterResource(R.drawable.logo_nhamstudio),
                contentDescription = "NhảmStudio",
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(20.dp).align(Alignment.CenterHorizontally)
                    .testTag("about_logo"),
                contentScale = ContentScale.FillWidth,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = BrandBlue)
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_tagline), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))

        InfoCard {
            InfoRow(Icons.Outlined.Code, stringResource(R.string.about_dev), "NhảmStudio")
            HorizontalDivider()
            InfoRow(Icons.Outlined.Language, stringResource(R.string.about_web), WEBSITE_URL, link = true) { context.openUrl(WEBSITE_URL) }
            HorizontalDivider()
            InfoRow(Icons.Outlined.QrCode2, stringResource(R.string.about_qr_tool), "topvl.net/qr", link = true) { context.openUrl(QR_TOOL_URL) }
        }
        Spacer(Modifier.height(16.dp))

        InfoCard {
            Text(stringResource(R.string.about_features), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp))
            Feature(Icons.Outlined.DocumentScanner, stringResource(R.string.about_f1))
            Feature(Icons.Outlined.QrCodeScanner, stringResource(R.string.about_f2))
            Feature(Icons.Outlined.PictureAsPdf, stringResource(R.string.about_f3))
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))

        InfoCard {
            InfoRow(Icons.Outlined.Lock, stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy))
        }
        Spacer(Modifier.height(16.dp))
        QrPromoCard()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) { Column { content() } }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, link: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = BrandOrange, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (link) BrandBlue else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (link) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (link) Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = BrandBlue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BrandBlue, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.Transparent))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
