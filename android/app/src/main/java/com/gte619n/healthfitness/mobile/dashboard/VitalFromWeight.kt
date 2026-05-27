package com.gte619n.healthfitness.mobile.dashboard

import com.gte619n.healthfitness.domain.dashboard.WeightSummary
import java.util.Locale
import kotlin.math.abs

/**
 * Derives a phone-row weight [Vital] from a [WeightSummary]. Kept here
 * so PhoneTodayScreen doesn't carry conversion logic of its own — both
 * screens reach for the same derived value.
 *
 * Mirrors web's `weightVital` computation in `loadBodyComposition`.
 */
internal object VitalFromWeight {

    fun weightVitalOrFallback(summary: WeightSummary?): Vital {
        if (summary == null) return DashboardFallbacks.vitals[0]
        val sparkline = sparklineFor(summary.series)
        val delta = summary.sevenDayDeltaLb?.let { d ->
            VitalDelta(
                direction = if (d < 0) ArrowDir.Down else ArrowDir.Up,
                value = String.format(Locale.US, "%.1f", abs(d)),
                window = "7d",
                // For this app the working assumption is weight loss is
                // the goal — the web makes the same call. Per-user
                // preference lands with IMPL-AND-02 alongside the
                // settings screen.
                tone = if (d <= 0.0) Tone.Good else Tone.Alert,
            )
        }
        return Vital(
            label = "Weight",
            icon = DashboardIcons.Scale,
            value = String.format(Locale.US, "%.1f", summary.latestLb),
            unit = "lb",
            delta = delta,
            sparkline = sparkline,
        )
    }

    /**
     * Picks 9 evenly-spaced samples and re-scales them into the 0..20
     * viewBox the StatCard sparkline expects. Higher weight → smaller y
     * (top of viewBox).
     */
    private fun sparklineFor(series: List<Double>): List<Float> {
        if (series.isEmpty()) return List(9) { 10f }
        val n = 9
        val idxs = IntArray(n) { i -> ((i * (series.size - 1).toDouble()) / (n - 1)).toInt() }
        val ys = idxs.map { series[it] }
        val rawMin = ys.min()
        val rawMax = ys.max()
        val range = (rawMax - rawMin).takeIf { it > 0.0 } ?: 1.0
        // viewBox uses 0..20 with higher value at the top, so invert.
        return ys.map { (((rawMax - it) / range) * 20.0).toFloat() }
    }
}
