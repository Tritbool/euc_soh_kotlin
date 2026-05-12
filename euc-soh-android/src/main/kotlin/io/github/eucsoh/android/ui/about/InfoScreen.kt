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

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import io.github.eucsoh.Constants.project_url
import io.github.eucsoh.android.R
import io.github.eucsoh.android.BuildConfig

// ---------------------------------------------------------------------------
// InfoScreen — écran d'aide principal (bouton ℹ️ de la TopBar)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onClose: () -> Unit) {


    val localColorScheme = darkColorScheme(
        primary = Color(0xFF4CAF50),
        tertiary = Color(0xFFFF9800),
        error = Color(0xFFF44336),
        secondary = if (isSystemInDarkTheme()) Color(0xFF3B3B42) else Color(0xFF9C9CB0)
        // ...
    )


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
            Text("Google Play Store Version ${BuildConfig.VERSION_NAME} rev. ${BuildConfig.VERSION_CODE}")
            // ── Section 1 : Ce que fait l'outil ──────────────────────────
            InfoSection(
                icon = Icons.Default.Analytics,
                iconContentDescription = null,
                title = stringResource(R.string.offline_claim),
                containerColor = MaterialTheme.colorScheme.primary,
                onContainerColor = MaterialTheme.colorScheme.onPrimary
            ) {
                InfoBody(stringResource(R.string.offline_claim_exlain))
                Spacer(Modifier.height(8.dp))
                InfoSubtitle(stringResource(R.string.offline_claim_text))
                InfoBulletList(
                    items = listOf(
                        stringResource(R.string.offline_claim_b1),
                        stringResource(R.string.offline_claim_b2),
                        stringResource(R.string.offline_claim_b3),
                        stringResource(R.string.offline_claim_b4)
                    )
                )
            }

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
                    color = localColorScheme.error,
                    label = stringResource(R.string.info_s2_alarm_danger_label),
                    description = stringResource(R.string.info_s2_alarm_danger_desc)
                )
                Spacer(Modifier.height(6.dp))
                InfoAlarmRow(
                    color = localColorScheme.tertiary,
                    label = stringResource(R.string.info_s2_alarm_orange_label),
                    description = stringResource(R.string.info_s2_alarm_orange_desc)
                )
                Spacer(Modifier.height(6.dp))
                InfoAlarmRow(
                    color = localColorScheme.primary,
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
                InfoBody(stringResource(R.string.info_s3_detail))
                ExpandableSection(
                    expandLabel = stringResource(R.string.info_expand_label),
                    collapseLabel = stringResource(R.string.info_collapse_label)
                ) {
                    InfoFormula(stringResource(R.string.info_s3_formula))
                    Spacer(Modifier.height(6.dp))
                    InfoBody(stringResource(R.string.info_s3_tech))
                    Spacer(Modifier.height(4.dp))
                    InfoSubtitle(stringResource(R.string.info_s3_example_title))
                    InfoBody(stringResource(R.string.info_s3_example))
                    Spacer(Modifier.height(4.dp))
                    InfoNote(stringResource(R.string.info_s3_note))
                }
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
                    interpretation = null
                )
                ExpandableSection(
                    expandLabel = stringResource(R.string.info_expand_label),
                    collapseLabel = stringResource(R.string.info_collapse_label)
                ) {
                    InfoFormula(stringResource(R.string.info_m3_tech))
                }
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
                InfoMetricDivider()
                InfoMetricBlock(
                    name = stringResource(R.string.info_m8_name),
                    description = stringResource(R.string.info_m8_desc),
                    interpretation = stringResource(R.string.info_m8_interp)
                )
            }

            // ── Section 5 : Exemples de graphiques ───────────────────────────
            InfoSection(
                icon = Icons.Default.ImageSearch,
                iconContentDescription = null,
                title = stringResource(R.string.info_s5_title),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onContainerColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                // Légende commune aux deux graphiques
                InfoLegendRow(
                    color = localColorScheme.primary,
                    label = stringResource(R.string.info_graph_legend_green)
                )
                InfoLegendRow(
                    color = localColorScheme.tertiary,
                    label = stringResource(R.string.info_graph_legend_orange)
                )
                InfoLegendRow(
                    color = localColorScheme.error,
                    label = stringResource(R.string.info_graph_legend_red)
                )

                Spacer(Modifier.height(12.dp))

                // Exemple OK
                InfoSubtitle(stringResource(R.string.info_graph_ok_title))
                InfoGraphImage(drawableRes = R.drawable.ok)
                InfoBody(stringResource(R.string.info_graph_ok_desc))

                Spacer(Modifier.height(14.dp))

                // Exemple KO
                InfoSubtitle(stringResource(R.string.info_graph_ko_title))
                InfoGraphImage(drawableRes = R.drawable.ko)
                InfoBody(stringResource(R.string.info_graph_ko_desc))
            }

            // ── Section 6 : Méthodologie de calcul ──────────────────────
            InfoSection(
                icon = Icons.Default.Calculate,
                iconContentDescription = null,
                title = stringResource(R.string.info_s6_title),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                InfoSubtitle(stringResource(R.string.info_s6_req_title))
                InfoBody(stringResource(R.string.info_s6_req_body))

                Spacer(Modifier.height(10.dp))

                InfoSubtitle(stringResource(R.string.info_s6_vidle_title))
                InfoBody(stringResource(R.string.info_s6_vidle_body))

                Spacer(Modifier.height(10.dp))

                InfoSubtitle(stringResource(R.string.info_s6_mosfet_title))
                InfoBody(stringResource(R.string.info_s6_mosfet_body))
                ExpandableSection(
                    expandLabel = stringResource(R.string.info_expand_label),
                    collapseLabel = stringResource(R.string.info_collapse_label)
                ) {
                    InfoFormula(stringResource(R.string.info_s6_mosfet_tech))
                }

                Spacer(Modifier.height(10.dp))

                InfoSubtitle(stringResource(R.string.info_s6_arrhenius_title))
                InfoBody(stringResource(R.string.info_s6_arrhenius_body))

                Spacer(Modifier.height(10.dp))

                InfoSubtitle(stringResource(R.string.info_s6_i2int_title))
                InfoBody(stringResource(R.string.info_s6_i2int_body))
                ExpandableSection(
                    expandLabel = stringResource(R.string.info_expand_label),
                    collapseLabel = stringResource(R.string.info_collapse_label)
                ) {
                    InfoFormula(stringResource(R.string.info_s6_i2int_tech))
                }
            }

            InfoSection(
                containerColor = localColorScheme.secondary,
                onContainerColor = localColorScheme.secondary
            ) {
            }
            Spacer(Modifier.height(10.dp))
            // ── Pied de page ─────────────────────────────────────────────────

            InfoSection(
                icon = Icons.Filled.Info,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    stringResource(R.string.info_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            InfoSection(
                icon = Icons.Filled.ReportProblem,
                title = "Report problems",
                containerColor = MaterialTheme.colorScheme.primary,
                onContainerColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "Tap here to report problems on GitHub",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clickable(onClick = { ->
                        val uri = project_url.toUri()
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                        return@clickable Unit
                    })
                )

            }

            Spacer(Modifier.height(10.dp))
            InfoSection(
                containerColor = localColorScheme.secondary,
                onContainerColor = localColorScheme.secondary
            ) {
            }

            InfoSection(
                icon = Icons.Filled.LocalLibrary,
                iconContentDescription = null,
                title = "Third party software",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onContainerColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                RichText(
                ) {
                    Markdown(licensesText)
                }
            }

        }
    }
}

// ---------------------------------------------------------------------------
// Composables privés de mise en forme
// ---------------------------------------------------------------------------

/**
 * Expandable "Technical details" block. Collapsed by default.
 * Shows a chevron button labelled with [expandLabel]/[collapseLabel].
 */
@Composable
fun ExpandableSection(
    expandLabel: String = "Technical details",
    collapseLabel: String = "Hide details",
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (expanded) collapseLabel else expandLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InfoSection(
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    title: String? = null,
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
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = onContainerColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainerColor
                    )
                }

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
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoMetricBlock(
    name: String,
    description: String,
    interpretation: String? = null
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
        if (interpretation != null) {
            Text(
                text = interpretation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoMetricDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun InfoLegendRow(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 3.dp)
                .background(color = color, shape = RoundedCornerShape(2.dp))
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoGraphImage(@androidx.annotation.DrawableRes drawableRes: Int) {
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}
