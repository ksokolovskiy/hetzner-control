package com.hcloudcontrol

import com.hcloudcontrol.ui.designsystem.*
import org.junit.Assert.*
import org.junit.Test

class MetricSeriesTest {
    @Test fun `missing invalid and nonfinite measurements never become zero or connect gaps`() {
        val data = listOf(MetricSeriesPoint(1.0, "4.5"), MetricSeriesPoint(2.0, "NaN"), MetricSeriesPoint(3.0, "8"), MetricSeriesPoint(9.0, "3"))
        val segments = metricPlotSegments(data, 1.0)
        assertEquals(listOf(listOf(4.5), listOf(8.0), listOf(3.0)), segments.map { it.map(MetricPlotPoint::value) })
        assertEquals("NaN", data[1].value)
    }
    @Test fun `provider points are sorted without resampling or invented baselines`() {
        val points = metricPlotSegments(listOf(MetricSeriesPoint(2.0, "20"), MetricSeriesPoint(1.0, "10")), 1.0).single()
        assertEquals(listOf(1.0, 2.0), points.map { it.timestampSeconds })
        assertEquals(listOf(10.0, 20.0), points.map { it.value })
    }
}
