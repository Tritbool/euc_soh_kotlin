package io.github.eucsoh.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.android.data.FileManager
import io.github.eucsoh.android.data.model.CsvFileInfo
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File list screen for a wheel.
 * Features:
 * - Display all CSV files
 * - Checkbox to exclude files from analysis
 * - Preview file (show first lines)
 * - Open with external app
 * - Delete file
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    wheelName: String,
    wheelDirUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<CsvFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFileForPreview by remember { mutableStateOf<CsvFileInfo?>(null) }

    // Load files on composition
    LaunchedEffect(wheelDirUri) {
        isLoading = true
        files = fileManager.listCsvFiles(wheelDirUri)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files - $wheelName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                files.isEmpty() -> {
                    Text(
                        "No CSV files found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files) { fileInfo ->
                            FileListItem(
                                fileInfo = fileInfo,
                                onPreview = { selectedFileForPreview = it },
                                onOpenWith = { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(uri), "text/csv")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open CSV"))
                                }
                            )
                        }
                    }
                }
            }
        }

        // Preview dialog
        selectedFileForPreview?.let { fileInfo ->
            var previewLines by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(fileInfo) {
                previewLines = fileManager.previewCsv(Uri.parse(fileInfo.uri))
            }

            AlertDialog(
                onDismissRequest = { selectedFileForPreview = null },
                title = { Text("Preview: ${fileInfo.name}") },
                text = {
                    LazyColumn {
                        items(previewLines) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedFileForPreview = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun FileListItem(
    fileInfo: CsvFileInfo,
    onPreview: (CsvFileInfo) -> Unit,
    onOpenWith: (String) -> Unit
) {
    val sizeFormatter = DecimalFormat("#,##0.0")
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPreview(fileInfo) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // File name
            Text(
                text = fileInfo.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${sizeFormatter.format(fileInfo.sizeBytes / 1024.0)} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = dateFormatter.format(Date(fileInfo.lastModified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPreview(fileInfo) }) {
                    Icon(Icons.Default.Visibility, "Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview")
                }

                TextButton(onClick = { onOpenWith(fileInfo.uri) }) {
                    Icon(Icons.Default.OpenInNew, "Open", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open")
                }
            }
        }
    }
}
