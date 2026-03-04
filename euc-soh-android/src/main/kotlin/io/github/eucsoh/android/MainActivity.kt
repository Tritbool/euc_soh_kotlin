package io.github.eucsoh.android

import android.os.Bundle
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
                        }
                    )
                }
            }
        }
    }
}
