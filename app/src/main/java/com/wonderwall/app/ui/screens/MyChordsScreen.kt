package com.wonderwall.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChordsScreen(
    onOpenAnalysis: (videoId: String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: MyChordsViewModel = viewModel(),
) {
    val analyses by viewModel.analyses.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Chords") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pad ->
        if (analyses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No saved chords yet.\nAnalyze a song and it'll appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(analyses, key = { it.videoId }) { analysis ->
                    ChordCard(
                        title = analysis.title,
                        artist = analysis.artist,
                        key = analysis.key,
                        bpm = analysis.bpm,
                        cachedAt = analysis.cachedAt,
                        onClick = { onOpenAnalysis(analysis.videoId) },
                        onDelete = { viewModel.delete(analysis.videoId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordCard(
    title: String,
    artist: String,
    key: String,
    bpm: Float,
    cachedAt: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = remember(cachedAt) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(cachedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$key · ${bpm.toInt()} BPM · $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
