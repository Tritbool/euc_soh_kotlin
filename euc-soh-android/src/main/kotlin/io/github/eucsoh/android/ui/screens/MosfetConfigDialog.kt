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
    onSave: (MOSFETParams) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var rDsOn by remember { mutableStateOf(currentParams?.rDsOn25cTotal?.toString() ?: "") }
    var tempCoeff by remember { mutableStateOf(currentParams?.tempCoeffRel?.toString() ?: "0.01") }
    var rWiring by remember { mutableStateOf(currentParams?.rWiring?.toString() ?: "0.0") }
    var showHelp by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Config MOSFET")
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
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Help button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Paramètres du pont MOSFET",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Aide",
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
                                "Ces paramètres permettent de séparer R_batt de R_mosfet dans l'analyse.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "• R_ds(on) : Résistance totale du pont à 25°C (datasheet)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• Coeff temp : +1%/°C typique pour MOSFET",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "• R_wiring : Résistance fixe des câbles (optionnel)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                // R_ds(on) field
                OutlinedTextField(
                    value = rDsOn,
                    onValueChange = { rDsOn = it },
                    label = { Text("R_ds(on) @ 25°C total (Ω)") },
                    placeholder = { Text("Ex: 0.002") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Résistance du pont MOSFET à 25°C",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                
                // Temp coefficient field
                OutlinedTextField(
                    value = tempCoeff,
                    onValueChange = { tempCoeff = it },
                    label = { Text("Coefficient temp (ΔR/°C)") },
                    placeholder = { Text("0.01") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Variation relative par °C (typ. 0.01 = +1%/°C)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                
                // Wiring resistance field
                OutlinedTextField(
                    value = rWiring,
                    onValueChange = { rWiring = it },
                    label = { Text("R_wiring (Ω)") },
                    placeholder = { Text("0.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Résistance fixe du câblage (optionnel)",
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
                                "Config actuelle",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "R_ds: ${String.format("%.6f", currentParams.rDsOn25cTotal)} Ω",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Coeff: ${String.format("%.4f", currentParams.tempCoeffRel)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (currentParams.rWiring > 0) {
                                Text(
                                    "Wiring: ${String.format("%.6f", currentParams.rWiring)} Ω",
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
                    
                    if (rds != null && rds > 0) {
                        onSave(MOSFETParams(rds, tc, rw))
                    }
                },
                enabled = rDsOn.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentParams != null) {
                    TextButton(onClick = onClear) {
                        Text("Effacer")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Annuler")
                }
            }
        }
    )
}
