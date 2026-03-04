package io.github.eucsoh.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.eucsoh.android.ui.PermissionManager
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.screens.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: SohViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    companion object {
        private const val TAG = "MainActivity"
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
                        }
                    )
                }
            }
        }
    }
}
