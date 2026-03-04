package io.github.eucsoh.android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.eucsoh.android.ui.PermissionManager
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.screens.MainScreen
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: SohViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    companion object {
        private const val TAG = "MainActivity"
    }

    // Folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            Log.d(TAG, "Folder selected: $uri")
            handleSelectedFolder(uri)
        } else {
            Log.w(TAG, "No folder selected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        // Request permissions if not granted
        if (!permissionManager.hasStoragePermissions()) {
            permissionManager.requestStoragePermissions { granted ->
                if (granted) {
                    Log.d(TAG, "Permissions granted, starting scan")
                    viewModel.scanWheels(forceRefresh = true)
                } else {
                    Log.w(TAG, "Permissions denied")
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            permissionManager.requestStoragePermissions { granted ->
                                if (granted) {
                                    viewModel.scanWheels(forceRefresh = true)
                                }
                            }
                        },
                        onRequestFolderPicker = {
                            Log.d(TAG, "Launching folder picker")
                            launchFolderPicker()
                        }
                    )
                }
            }
        }
    }

    /**
     * Launches the Android folder picker.
     */
    private fun launchFolderPicker() {
        try {
            // Start with external storage by default
            val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:"
                )
            } else {
                null
            }
            folderPickerLauncher.launch(initialUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching folder picker", e)
        }
    }

    /**
     * Handles the selected folder URI and converts it to an absolute path.
     */
    private fun handleSelectedFolder(uri: Uri) {
        try {
            Log.d(TAG, "Processing URI: $uri")
            
            // Take persistable permission
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Persistable permission taken")
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission", e)
            }

            // Try to get the real path
            val path = getRealPathFromUri(uri)
            Log.d(TAG, "Extracted path: $path")
            
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.isDirectory) {
                    Log.d(TAG, "Path exists and is directory: $path")
                    viewModel.setScanRootPath(path)
                } else {
                    Log.e(TAG, "Path does not exist or is not a directory: $path")
                    // Try parent directory
                    val parent = file.parentFile
                    if (parent?.exists() == true && parent.isDirectory) {
                        Log.d(TAG, "Using parent directory: ${parent.absolutePath}")
                        viewModel.setScanRootPath(parent.absolutePath)
                    } else {
                        Log.e(TAG, "Could not find valid directory")
                    }
                }
            } else {
                Log.e(TAG, "Could not extract real path from URI")
                // Fallback: try external storage root
                val fallback = android.os.Environment.getExternalStorageDirectory()
                Log.d(TAG, "Using fallback: ${fallback.absolutePath}")
                viewModel.setScanRootPath(fallback.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected folder", e)
        }
    }

    /**
     * Attempts to extract real filesystem path from URI.
     * Returns null if not possible (e.g., cloud storage).
     */
    private fun getRealPathFromUri(uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            Log.d(TAG, "Document ID: $docId")
            
            // Check if it's external storage
            if (docId.startsWith("primary:")) {
                val path = docId.substring("primary:".length)
                val fullPath = if (path.isEmpty()) {
                    android.os.Environment.getExternalStorageDirectory().absolutePath
                } else {
                    "${android.os.Environment.getExternalStorageDirectory()}/$path"
                }
                Log.d(TAG, "Primary storage path: $fullPath")
                return fullPath
            }
            
            // Check if it's a raw path (some file managers)
            if (docId.startsWith("/")) {
                Log.d(TAG, "Raw path detected: $docId")
                return docId
            }
            
            // Try to handle other storage types
            if (docId.contains(":")) {
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val storageId = parts[0]
                    val relativePath = parts[1]
                    Log.d(TAG, "Storage ID: $storageId, Relative path: $relativePath")
                    
                    // Try common mount points
                    val possiblePaths = listOf(
                        "/storage/$storageId/$relativePath",
                        "/mnt/$storageId/$relativePath"
                    )
                    
                    for (testPath in possiblePaths) {
                        val file = File(testPath)
                        if (file.exists()) {
                            Log.d(TAG, "Found valid path: $testPath")
                            return testPath
                        }
                    }
                }
            }
            
            Log.w(TAG, "Could not determine path from document ID: $docId")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting path from URI", e)
            return null
        }
    }
}
