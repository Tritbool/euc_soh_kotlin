package io.github.eucsoh.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.documentfile.provider.DocumentFile
import io.github.eucsoh.android.ui.PermissionManager
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.screens.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: SohViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    // Folder picker launcher
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        // Request permissions if not granted
        if (!permissionManager.hasStoragePermissions()) {
            permissionManager.requestStoragePermissions { granted ->
                if (granted) {
                    // Scan after permission granted
                    viewModel.scanWheels(forceRefresh = true)
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
        folderPickerLauncher.launch(null)
    }

    /**
     * Handles the selected folder URI and converts it to an absolute path.
     * 
     * Note: On Android 11+, we must work with DocumentFile API.
     * We try to extract the real path when possible.
     */
    private fun handleSelectedFolder(uri: Uri) {
        try {
            // Take persistable permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // Try to get the real path
            val path = getRealPathFromUri(uri)
            
            if (path != null) {
                viewModel.setScanRootPath(path)
            } else {
                // Fallback: use DocumentFile (slower but works everywhere)
                // For now, show error - proper DocumentFile implementation needed
                viewModel.clearError()
                // TODO: Implement DocumentFile-based scanning
            }
        } catch (e: Exception) {
            viewModel.clearError()
            e.printStackTrace()
        }
    }

    /**
     * Attempts to extract real filesystem path from URI.
     * Returns null if not possible (e.g., cloud storage).
     */
    private fun getRealPathFromUri(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        
        // Check if it's external storage
        if (docId.startsWith("primary:")) {
            val path = docId.substring("primary:".length)
            return "${android.os.Environment.getExternalStorageDirectory()}/$path"
        }
        
        // Check if it's a raw path (some file managers)
        if (docId.startsWith("/")) {
            return docId
        }
        
        return null
    }
}
