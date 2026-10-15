package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.R

/** Provider measurements remain exact strings; invalid or absent values never become zero. */
data class MetricSeriesPoint(val timestampSeconds: Double, val value: String)
data class MetricPlotPoint(val timestampSeconds: Double, val value: Double)

fun metricPlotSegments(samples: List<MetricSeriesPoint>, stepSeconds: Double?): List<List<MetricPlotPoint>> {
    val result = mutableListOf<MutableList<MetricPlotPoint>>()
    var previous: MetricPlotPoint? = null
    samples.sortedBy { it.timestampSeconds }.forEach { sample ->
        val value = sample.value.toDoubleOrNull()
        if (!sample.timestampSeconds.isFinite() || value == null || !value.isFinite()) {
            previous = null
        } else {
            val point = MetricPlotPoint(sample.timestampSeconds, value)
            val gap = previous?.let { point.timestampSeconds - it.timestampSeconds }
            if (previous == null || (stepSeconds != null && stepSeconds > 0 && gap != null && gap > stepSeconds * 1.5)) result.add(mutableListOf())
            result.last().add(point)
            previous = point
        }
    }
    return result
}

/** One chart for Server and Load Balancer series; exact timestamp/value table is always available. */
@Composable
fun HCloudMetricSeriesCard(title: String, samples: List<MetricSeriesPoint>, stepSeconds: Double?, modifier: Modifier = Modifier) {
    val segments = remember(samples, stepSeconds) { metricPlotSegments(samples, stepSeconds) }
    val points = segments.flatten()
    var expanded by remember(title) { mutableStateOf(false) }
    val min = points.minOfOrNull { it.value }
    val max = points.maxOfOrNull { it.value }
    val latest = samples.filter { it.timestampSeconds.isFinite() }.maxByOrNull { it.timestampSeconds }
    val missing = stringResource(R.string.server_metrics_no_samples)
    val summary = stringResource(R.string.metric_series_summary, latest?.value ?: missing, min?.toString() ?: missing, max?.toString() ?: missing)
    val lineColor = MaterialTheme.colorScheme.primary
    Card(modifier.fillMaxWidth(), shape = HCloudDesignTokens.cardShape) {
        Column(Modifier.padding(HCloudDesignTokens.contentInset), verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall)
            if (points.isNotEmpty()) {
                val first = points.minOf { it.timestampSeconds }
                val last = points.maxOf { it.timestampSeconds }
                Canvas(Modifier.fillMaxWidth().height(144.dp).semantics { contentDescription = "$title. $summary" }) {
                    val low = requireNotNull(min); val high = requireNotNull(max)
                    fun position(point: MetricPlotPoint) = Offset(
                        if (first == last) size.width / 2 else ((point.timestampSeconds - first) / (last - first) * size.width).toFloat(),
                        if (high == low) size.height / 2 else (size.height - (point.value - low) / (high - low) * size.height).toFloat())
                    segments.forEach { segment ->
                        segment.zipWithNext().forEach { (a, b) -> drawLine(lineColor, position(a), position(b), strokeWidth = 2.dp.toPx()) }
                        segment.forEach { drawCircle(lineColor, 2.dp.toPx(), position(it)) }
                    }
                }
                Text("${java.time.Instant.ofEpochMilli((first * 1000).toLong())} — ${java.time.Instant.ofEpochMilli((last * 1000).toLong())}", style = MaterialTheme.typography.labelSmall)
            } else Text(missing)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.metric_series_hide_samples else R.string.metric_series_show_samples, samples.size))
            }
            if (expanded) samples.sortedBy { it.timestampSeconds }.forEach { sample ->
                Text("${sample.timestampSeconds}: ${sample.value}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
