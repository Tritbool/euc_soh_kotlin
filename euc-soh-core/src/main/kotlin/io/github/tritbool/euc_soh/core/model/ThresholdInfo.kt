package io.github.tritbool.euc_soh.core.model

/**
 * Gaussian threshold info for alarm detection.
 *
 * @property mean Mean value (μ) of the metric in healthy state
 * @property std Standard deviation (σ)
 * @property limit Danger threshold (μ ± n·σ depending on direction)
 * @property direction "higher_is_bad" or "lower_is_bad"
 */
data class ThresholdInfo(
    val mean: Double,
    val std: Double,
    val limit: Double,
    val direction: String // "higher_is_bad" | "lower_is_bad"
)
