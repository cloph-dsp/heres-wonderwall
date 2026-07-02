package com.wonderwall.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wonderwall.app.WonderwallWebView
import com.wonderwall.app.data.AppDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenTarget: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(WonderwallWebView.targetUrl) }
    var cacheCleared by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target URL
            Text("Target URL", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("URL") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    WonderwallWebView.targetUrl = url
                    onOpenTarget()
                }) {
                    Text("Open in WebView")
                }
                OutlinedButton(onClick = { url = "https://chordmini.me" }) {
                    Text("Reset")
                }
            }

            HorizontalDivider()

            // Clear cache
            Text("Data", style = MaterialTheme.typography.labelLarge)
            Button(
                onClick = {
                    scope.launch {
                        AppDatabase.getInstance(context).analysisDao().deleteAll()
                        cacheCleared = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All Cached Analyses")
            }
            if (cacheCleared) {
                Text(
                    "Cache cleared!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Open source link
            Text("About", style = MaterialTheme.typography.labelLarge)
            Text(
                "Here's Wonderwall v1.0.1",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Android WebView wrapper around ChordMini with native audio bridge.\n" +
                        "Record guitar, pick audio files, cache chords offline, and share results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cloph-dsp/heres-wonderwall"))
                    context.startActivity(i)
                }
            ) {
                Text("View source on GitHub")
            }
        }
    }
}
