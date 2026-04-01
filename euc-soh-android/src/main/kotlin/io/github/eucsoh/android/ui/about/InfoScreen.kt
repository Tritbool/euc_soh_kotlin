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

package io.github.eucsoh.android.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import io.github.eucsoh.android.R

// ---------------------------------------------------------------------------
// InfoScreen — écran d'aide principal (bouton ℹ️ de la TopBar)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onClose: () -> Unit) {

    val context = LocalContext.current
    val licensesText = remember {
        context.assets
            .open("third_party_licenses.md")
            .bufferedReader()
            .use { it.readText() }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.info_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Section 1 : Ce que fait l'outil ──────────────────────────
            InfoSection(
                icon = Icons.Default.Analytics,
                iconContentDescription = null,
                title = stringResource(R.string.info_s1_title),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                InfoBody(stringResource(R.string.info_s1_does))
                Spacer(Modifier.height(8.dp))
                InfoSubtitle(stringResource(R.string.info_s1_does_not_title))
                InfoBulletList(
                    items = listOf(
                        stringResource(R.string.info_s1_limit1),
                        stringResource(R.string.info_s1_limit2),
                        stringResource(R.string.info_s1_limit3),
                        stringResource(R.string.info_s1_limit4)
                    )
                )
            }

            // ── Section 2 : Comment agir ─────────────────────────────────
            InfoSection(
                icon = Icons.Default.Warning,
                iconContentDescription = null,
                title = stringResource(R.string.info_s2_title),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                InfoBody(stringResource(R.string.info_s2_intro))
                Spacer(Modifier.height(8.dp))
                InfoAlarmRow(
                    color = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.info_s2_alarm_danger_label),
                    description = stringResource(R.string.info_s2_alarm_danger_desc)
                )
                Spacer(Modifier.height(6.dp))
                InfoAlarmRow(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.info_s2_alarm_orange_label),
                    description = stringResource(R.string.info_s2_alarm_orange_desc)
                )
                Spacer(Modifier.height(6.dp))
                InfoAlarmRow(
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.info_s2_alarm_green_label),
                    description = stringResource(R.string.info_s2_alarm_green_desc)
                )
                Spacer(Modifier.height(8.dp))
                InfoBody(stringResource(R.string.info_s2_action))
            }

            // ── Section 3 : Résistance nominale du pack ──────────────────
            InfoSection(
                icon = Icons.Default.ElectricBolt,
                iconContentDescription = null,
                title = stringResource(R.string.info_s3_title),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                InfoBody(stringResource(R.string.info_s3_intro))
                Spacer(Modifier.height(8.dp))
                InfoFormula(stringResource(R.string.info_s3_formula))
                Spacer(Modifier.height(8.dp))
                InfoBody(stringResource(R.string.info_s3_detail))
                Spacer(Modifier.height(8.dp))
                InfoSubtitle(stringResource(R.string.info_s3_example_title))
                InfoBody(stringResource(R.string.info_s3_example))
                Spacer(Modifier.height(4.dp))
                InfoNote(stringResource(R.string.info_s3_note))
            }

            // ── Section 4 : Métriques et interprétation ──────────────────
            InfoSection(
                icon = Icons.Default.BarChart,
                iconContentDescription = null,
                title = stringResource(R.string.info_s4_title),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onContainerColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                InfoMetricBlock(
                    name = stringResource(R.string.info_m1_name),
                    description = stringResource(R.string.info_m1_desc),
                    interpretation = stringResource(R.string.info_m1_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m2_name),
                    description = stringResource(R.string.info_m2_desc),
                    interpretation = stringResource(R.string.info_m2_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m3_name),
                    description = stringResource(R.string.info_m3_desc),
                    interpretation = stringResource(R.string.info_m3_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m4_name),
                    description = stringResource(R.string.info_m4_desc),
                    interpretation = stringResource(R.string.info_m4_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m5_name),
                    description = stringResource(R.string.info_m5_desc),
                    interpretation = stringResource(R.string.info_m5_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m6_name),
                    description = stringResource(R.string.info_m6_desc),
                    interpretation = stringResource(R.string.info_m6_interp)
                )
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m7_name),
                    description = stringResource(R.string.info_m7_desc),
                    interpretation = stringResource(R.string.info_m7_interp)
                )
            }

            // ── Pied de page ─────────────────────────────────────────────
            Text(
                stringResource(R.string.info_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RichText(
            ) {
                Markdown(licensesText)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composables privés de mise en forme
// ---------------------------------------------------------------------------

@Composable
private fun InfoSection(
    icon: ImageVector,
    iconContentDescription: String?,
    title: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = onContainerColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun InfoSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun InfoBulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoAlarmRow(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(12.dp)
                .background(color = color, shape = RoundedCornerShape(3.dp))
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoFormula(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun InfoNote(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text("ℹ️", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoMetricBlock(
    name: String,
    description: String,
    interpretation: String
) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = interpretation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoMetricDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

