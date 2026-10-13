package com.simon.harmonichackernews.ui.submissions

internal data class SubmissionFilterLayout(
    val weights: List<Float>,
    val showIcons: Boolean,
    val showLabels: Boolean,
)

/** Labels are measured at the current font scale; all segments change mode together. */
internal fun submissionFilterLayout(width: Float, labelWidths: List<Float>): SubmissionFilterLayout {
    val available = (width - 2f * (labelWidths.size - 1)).coerceAtLeast(0f)
    val withIcons = labelWidths.map { (it + 41f).coerceAtLeast(48f) }
    val textOnly = labelWidths.map { (it + 16f).coerceAtLeast(48f) }
    return when {
        withIcons.max() * labelWidths.size <= available ->
            SubmissionFilterLayout(List(labelWidths.size) { 1f }, true, true)
        withIcons.sum() <= available -> SubmissionFilterLayout(withIcons, true, true)
        textOnly.sum() <= available -> SubmissionFilterLayout(textOnly, false, true)
        else -> SubmissionFilterLayout(List(labelWidths.size) { 1f }, true, false)
    }
}
