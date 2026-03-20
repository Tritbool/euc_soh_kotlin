package io.github.eucsoh.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity

class PermissionManager(private val activity: ComponentActivity) {

    fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // API 26-29 : pas de Scoped Storage, READ_EXTERNAL_STORAGE suffit
            // et il est déjà accordé implicitement sur ces versions avec requestLegacyExternalStorage
            true
        }
    }

    fun requestStoragePermissions(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ : ouvre les paramètres système, pas de callback possible
            // L'utilisateur doit revenir dans l'app manuellement
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
            // onResult ne sera jamais appelé ici automatiquement
            // MainActivity doit re-checker dans onResume()
        } else {
            // API 26-29 : déjà OK grâce à requestLegacyExternalStorage dans le manifest
            onResult(true)
        }
    }
}
