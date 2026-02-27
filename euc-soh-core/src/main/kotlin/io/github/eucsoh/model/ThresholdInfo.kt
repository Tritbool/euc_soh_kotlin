package io.github.eucsoh.model

/**
 * Gaussian threshold for alarm detection.
 */
data class ThresholdInfo(
    val mean: Double,
    val std: Double,
    val limit: Double,
    val direction: String  // "higher_is_bad" or "lower_is_bad"
)
