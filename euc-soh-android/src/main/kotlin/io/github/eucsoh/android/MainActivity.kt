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

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eucsoh.android.ui.PermissionManager
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.about.LicensesScreen
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
                    var showLicenses by remember { mutableStateOf(false) }

                    if (showLicenses) {
                        LicensesScreen(
                            onClose = { showLicenses = false }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onRequestPermissions = {
                                permissionManager.requestStoragePermissions { granted ->
                                    if (granted) {
                                        viewModel.scanWheels(forceRefresh = true)
                                    }
                                }
                            },
                            onOpenLicenses = {
                                showLicenses = true
                            }
                        )
                    }
                }
            }
        }
    }

}
