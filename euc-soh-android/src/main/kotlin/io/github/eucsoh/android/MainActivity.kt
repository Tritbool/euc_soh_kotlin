package io.github.eucsoh.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
import io.github.eucsoh.android.ui.theme.EucSohTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SohViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onResume() {
        super.onResume()
        if (permissionManager.hasStoragePermissions()) {
            viewModel.scanWheels(forceRefresh = false)
        }
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
            Log.d(TAG, "Requesting storage permissions")
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
            EucSohTheme {
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
                            //Log.d(TAG, "Launching folder picker")
                            //launchFolderPicker()
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
            folderPickerLauncher.launch(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching folder picker", e)
        }
    }

    /**
     * Handles the selected folder URI.
     * Takes persistable permission and passes URI to ViewModel.
     */
    private fun handleSelectedFolder(uri: Uri) {
        try {
            Log.d(TAG, "Processing URI: $uri")
            intent
            // Take persistable permission
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                Log.d(TAG, "Persistable permission taken")
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission", e)
            }

            // Pass URI to ViewModel
            viewModel.setRootUri(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected folder", e)
        }
    }
}
