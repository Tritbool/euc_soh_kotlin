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

package io.github.eucsoh.android.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import io.github.eucsoh.android.R

@Composable
fun SpotlightOverlay(
    steps: List<OnboardingStep>,
    currentStep: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit
) {
    if (steps.isEmpty()) return
    val step = steps[currentStep]
    val isLast = currentStep == steps.lastIndex
    val isFirst = currentStep == 0
    val hasTarget = step.targetBounds != Rect.Zero

    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val cornerRadiusPx = with(density) { 12.dp.toPx() }
    val bubblePadding = 16.dp
    val bubbleMargin = 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            // Consume all taps so the UI underneath is not interactive during onboarding
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // Semi-transparent overlay with cutout hole
        // graphicsLayer on the Canvas itself with Offscreen strategy so BlendMode.Clear
        // correctly punches through the black fill regardless of GPU/driver behaviour.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            // Draw semi-transparent black over entire surface
            drawRect(color = Color.Black.copy(alpha = 0.7f))

            // Cut out rounded rect hole if target is valid
            if (hasTarget) {
                drawRoundRectHole(step.targetBounds, cornerRadiusPx)
            }
        }

        // Tooltip bubble
        if (hasTarget) {
            val bubbleAbove = step.targetBounds.center.y > screenHeightPx / 2
            val bubbleYOffset = if (bubbleAbove) {
                // Position above the target
                step.targetBounds.top.toInt() - with(density) { bubbleMargin.toPx() }.toInt()
            } else {
                // Position below the target
                step.targetBounds.bottom.toInt() + with(density) { bubbleMargin.toPx() }.toInt()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = bubblePadding)
                    .offset { IntOffset(0, bubbleYOffset) },
                contentAlignment = if (bubbleAbove) Alignment.BottomCenter else Alignment.TopCenter
            ) {
                BubbleCard(
                    step = step,
                    isFirst = isFirst,
                    isLast = isLast,
                    onNext = onNext,
                    onPrev = onPrev,
                    onDismiss = onDismiss
                )
            }
        } else {
            // No target: center the bubble on screen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.padding(horizontal = bubblePadding)) {
                    BubbleCard(
                        step = step,
                        isFirst = isFirst,
                        isLast = isLast,
                        onNext = onNext,
                        onPrev = onPrev,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleCard(
    step: OnboardingStep,
    isFirst: Boolean,
    isLast: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Row {
                    if (!isFirst) {
                        TextButton(onClick = onPrev) {
                            Text(stringResource(R.string.onboarding_prev))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onNext) {
                        Text(
                            stringResource(
                                if (isLast) R.string.onboarding_finish
                                else R.string.onboarding_next
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRoundRectHole(bounds: Rect, cornerRadius: Float) {
    val padding = 8f
    val expandedBounds = Rect(
        left = bounds.left - padding,
        top = bounds.top - padding,
        right = bounds.right + padding,
        bottom = bounds.bottom + padding
    )
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = expandedBounds,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        )
    }
    drawPath(
        path = path,
        color = Color.Black,
        blendMode = BlendMode.Clear
    )
}
