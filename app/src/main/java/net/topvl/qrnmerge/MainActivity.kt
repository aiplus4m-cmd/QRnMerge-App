package net.topvl.qrnmerge

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import net.topvl.qrnmerge.about.AboutScreen
import net.topvl.qrnmerge.merge.MergeScreen
import net.topvl.qrnmerge.merge.MergeViewModel
import net.topvl.qrnmerge.scan.ScanScreen
import net.topvl.qrnmerge.scan.ScanViewModel
import net.topvl.qrnmerge.ui.QRnMergeTheme

class MainViewModel : ViewModel() {
    var tab by mutableIntStateOf(TAB_SCAN)

    companion object {
        const val TAB_SCAN = 0
        const val TAB_MERGE = 1
        const val TAB_ABOUT = 2
    }
}

class MainActivity : ComponentActivity() {
    private val mainVm: MainViewModel by viewModels()
    private val mergeVm: MergeViewModel by viewModels()
    private val scanVm: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            QRnMergeTheme {
                AppRoot(mainVm, scanVm, mergeVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Images / PDFs shared from other apps go straight into the merge list. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            mergeVm.addUris(uris)
            mainVm.tab = MainViewModel.TAB_MERGE
        }
    }
}

@Composable
fun AppRoot(mainVm: MainViewModel, scanVm: ScanViewModel, mergeVm: MergeViewModel) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    modifier = Modifier.testTag("tab_scan"),
                    selected = mainVm.tab == MainViewModel.TAB_SCAN,
                    onClick = { mainVm.tab = MainViewModel.TAB_SCAN },
                    icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                    label = { Text(stringResource(R.string.tab_scan)) },
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("tab_merge"),
                    selected = mainVm.tab == MainViewModel.TAB_MERGE,
                    onClick = { mainVm.tab = MainViewModel.TAB_MERGE },
                    icon = {
                        BadgedBox(badge = {
                            if (mergeVm.items.isNotEmpty()) Badge { Text("${mergeVm.items.size}") }
                        }) { Icon(Icons.Outlined.PictureAsPdf, null) }
                    },
                    label = { Text(stringResource(R.string.tab_merge)) },
                )
                NavigationBarItem(
                    modifier = Modifier.testTag("tab_about"),
                    selected = mainVm.tab == MainViewModel.TAB_ABOUT,
                    onClick = { mainVm.tab = MainViewModel.TAB_ABOUT },
                    icon = { Icon(Icons.Outlined.Info, null) },
                    label = { Text(stringResource(R.string.tab_about)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (mainVm.tab) {
                MainViewModel.TAB_SCAN -> ScanScreen(scanVm, snackbar, onSendToMerge = { uris ->
                    mergeVm.addUris(uris)
                    mainVm.tab = MainViewModel.TAB_MERGE
                })
                MainViewModel.TAB_MERGE -> MergeScreen(mergeVm, snackbar)
                else -> AboutScreen()
            }
        }
    }
}
