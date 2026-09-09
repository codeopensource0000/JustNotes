package code.opensource0000.justnotes.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import code.opensource0000.justnotes.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Shows the licence texts that ship inside the APK.
//
// This screen exists for a legal reason, not a decorative one. Apache 2.0 asks
// that recipients of the work receive a copy of the licence and of the NOTICE
// attributions. Someone who clones the repository gets both at its root — but
// someone who only downloads the APK gets the two bundled Vosk speech models
// and, without this, nothing crediting Alpha Cephei and no licence text.
//
// The files are the ones at the repository root, copied into assets at build
// time (see app/build.gradle.kts), so this can never quietly drift out of step
// with what the project actually says.
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val notice by produceState(initialValue = "", context) {
        value = withContext(Dispatchers.IO) { context.readAsset("NOTICE") }
    }
    val licence by produceState(initialValue = "", context) {
        value = withContext(Dispatchers.IO) { context.readAsset("LICENSE") }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.editor_back)
                    )
                }
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.about_app_licence),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            LicenceSection(
                heading = stringResource(R.string.about_third_party),
                body = notice
            )
            LicenceSection(
                heading = stringResource(R.string.about_full_licence),
                body = licence
            )
        }
    }
}

// Monospace and horizontally scrollable: both files are hard-wrapped plain
// text whose alignment carries meaning, and reflowing them to the screen width
// would turn the licence into a wall of ragged lines.
@Composable
private fun LicenceSection(heading: String, body: String) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 24.dp)
    )
    Text(
        text = heading.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

private fun android.content.Context.readAsset(name: String): String =
    runCatching { assets.open(name).bufferedReader().use { it.readText() } }
        .getOrDefault("")
