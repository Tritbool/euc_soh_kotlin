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

package io.github.eucsoh.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.eucsoh.model.MOSFETParams
import java.util.Locale
import androidx.compose.ui.res.stringResource
import io.github.eucsoh.android.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn


/**
 * Dialog de configuration des paramètres MOSFET pour une roue.
 * 
 * Permet de saisir :
 * - R_ds(on) @ 25°C total : Résistance drain-source du pont MOSFET
 * - Coefficient temp : Variation relative par °C (typ. 0.01 = +1%/°C)
 * - R_wiring : Résistance fixe du câblage
 */
@Composable
fun MosfetConfigDialog(
    wheelName: String,
    currentParams: MOSFETParams?,
    aliasInput: String,
    hasDataName: Boolean,
    onAliasChange: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onSave: (MOSFETParams) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var rDsOn by remember { mutableStateOf(currentParams?.rDsOn25cTotal?.toString() ?: "") }
    var tempCoeff by remember { mutableStateOf(currentParams?.tempCoeffRel?.toString() ?: "0.01") }
    var rWiring by remember { mutableStateOf(currentParams?.rWiring?.toString() ?: "0.0005") }
    var showHelp by remember { mutableStateOf(false) }
    var nParallel by remember { mutableStateOf(currentParams?.nParallel?.toString() ?: "1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.mosfet_dialog_section_title))
                Text(
                    wheelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                // Wheel name field
                OutlinedTextField(
                    value = if (hasDataName) wheelName else aliasInput,
                    onValueChange = { if (!hasDataName) onAliasChange(it) },
                    label = { Text(stringResource(R.string.wheel_name_title)) },
                    enabled = !hasDataName,
                    supportingText = {
                        if (hasDataName)
                            Text(stringResource(R.string.wheel_name_blocked),
                                color = MaterialTheme.colorScheme.outline)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!hasDataName) {
                    Button(
                        onClick = onSaveAlias,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.wheel_name_save))
                    }
                }

                // Help button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.mosfet_dialog_section_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.mosfet_help_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showHelp) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.mosfet_help_intro),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.mosfet_help_rds),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.mosfet_help_coeff),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(R.string.mosfet_help_wiring),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // R_ds(on) field
                OutlinedTextField(
                    value = rDsOn,
                    onValueChange = { rDsOn = it },
                    label = { Text(stringResource(R.string.mosfet_field_rds_label)) },
                    placeholder = { Text(stringResource(R.string.mosfet_field_rds_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.mosfet_field_rds_support),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                // Temp coefficient field
                OutlinedTextField(
                    value = tempCoeff,
                    onValueChange = { tempCoeff = it },
                    label = { Text(stringResource(R.string.mosfet_field_coeff_label)) },
                    placeholder = { Text(stringResource(R.string.mosfet_field_coeff_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "relative Variation per °C (typ. 0.01 = +1%/°C)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                // Wiring resistance field
                OutlinedTextField(
                    value = rWiring,
                    onValueChange = { rWiring = it },
                    label = { Text(stringResource(R.string.mosfet_field_wiring_label)) },
                    placeholder = { Text(stringResource(R.string.mosfet_field_wiring_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.mosfet_field_wiring_support),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                OutlinedTextField(
                    value = nParallel,
                    onValueChange = { nParallel = it },
                    label = { Text(stringResource(R.string.mosfet_field_nparallel_label)) },
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.mosfet_field_nparallel_support),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                // Current config indicator
                if (currentParams != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {


                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                stringResource(R.string.mosfet_current_config_title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(
                                    R.string.mosfet_current_rds,
                                    String.format(
                                        Locale.getDefault(),
                                        "%.6f",
                                        currentParams.rDsOn25cTotal
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(
                                    R.string.mosfet_current_coeff,
                                    String.format(
                                        Locale.getDefault(),
                                        "%.4f",
                                        currentParams.tempCoeffRel
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (currentParams.rWiring > 0) {
                                Text(
                                    stringResource(
                                        R.string.mosfet_current_wiring,
                                        String.format(
                                            Locale.getDefault(),
                                            "%.6f",
                                            currentParams.rWiring
                                        )
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (currentParams.nParallel > 1) {
                                Text(
                                    stringResource(R.string.mosfet_current_nparallel, currentParams.nParallel),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rds = rDsOn.toDoubleOrNull()
                    val tc = tempCoeff.toDoubleOrNull() ?: 0.01
                    val rw = rWiring.toDoubleOrNull() ?: 0.0
                    val np = nParallel.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    if (rds != null && rds > 0) {
                        onSave(MOSFETParams(rds, tc, rw, np))   // ← ajouter np
                    }
                    else if(np != null && np > 1){
                        onSave(MOSFETParams(null, tc, rw, np))
                    }
                },
                enabled = rDsOn.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text(stringResource(R.string.mosfet_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentParams != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.mosfet_reset))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.mosfet_cancel))
                }
            }
        }
    )
}
