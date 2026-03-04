package io.github.eucsoh.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Manages storage permissions across different Android versions.
 * 
 * - Android 13+ (API 33): READ_MEDIA_* permissions
 * - Android 10-12: READ_EXTERNAL_STORAGE
 * - Android 9-: READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
 */
class PermissionManager(private val activity: ComponentActivity) {
    
    private var onResultCallback: ((Boolean) -> Unit)? = null
    
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        onResultCallback?.invoke(allGranted)
        onResultCallback = null
    }
    
    /**
     * Checks if storage permissions are granted.
     */
    fun hasStoragePermissions(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+: Need READ_MEDIA_*
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // Android 12 and below: READ_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    /**
     * Requests storage permissions with callback.
     */
    fun requestStoragePermissions(onResult: (Boolean) -> Unit) {
        onResultCallback = onResult
        
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+: Granular media permissions
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-12: READ_EXTERNAL_STORAGE
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Android 9-: READ + WRITE
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
        
        permissionLauncher.launch(permissions)
    }
}
