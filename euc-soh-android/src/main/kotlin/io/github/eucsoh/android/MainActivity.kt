/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.about.InfoScreen
import io.github.eucsoh.android.ui.screens.MainScreen
import io.github.eucsoh.android.ui.theme.EucSohTheme
import androidx.core.content.edit
import io.github.eucsoh.android.BuildConfig

class MainActivity : ComponentActivity() {

    private val viewModel: SohViewModel by viewModels()

    private val PREFS_NAME = "app_prefs"
    private val KEY_LAST_SEEN_VERSION = "last_seen_version_code"

    fun shouldShowUpdatePopup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenVersion = prefs.getInt(KEY_LAST_SEEN_VERSION, -1)
        val currentVersion = BuildConfig.VERSION_CODE
        return currentVersion > lastSeenVersion
    }

    fun markUpdatePopupAsShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_CODE)
        }
    }
    override fun onResume() {
        super.onResume()
        viewModel.scanWheels(forceRefresh = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EucSohTheme {
                Surface {
                    var showInfo by remember { mutableStateOf(false) }

                    when {

                        showInfo -> {
                            InfoScreen(
                                onClose = { showInfo = false }
                            )
                        }
                        //TODO always update news prompt
                        else -> {
                            if (shouldShowUpdatePopup(this)) {
                                AlertDialog.Builder(this)
<<<<<<< HEAD
                                    .setTitle("What's New in version ${BuildConfig.VERSION_NAME} rev. ${BuildConfig.VERSION_CODE/10003}")
                                    .setMessage("Welcome! This is the Google Play edition of EUC SoH Analyzer.\n" +
                                            "Due to Google Play’s storage rules, file access works via the Android file picker.\n" +
                                            "A separate F‑Droid edition offers extended storage features for power users.")
=======
                                    .setTitle("What's New in version ${BuildConfig.VERSION_NAME} rev. ${BuildConfig.VERSION_CODE}")
                                    .setMessage("- UI improvements\n- Dependencies updates")
>>>>>>> main
                                    .setPositiveButton("OK") { _, _ ->
                                        markUpdatePopupAsShown(this)
                                    }
                                    .setCancelable(false)
                                    .show()
                            }
                            MainScreen(
                                viewModel = viewModel,
                                onOpenInfo = { showInfo = true }
                            )
                        }
                    }
                }
            }
        }
    }

}
