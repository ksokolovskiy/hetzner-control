package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.ui.navigation.RootDestination

internal enum class RootNavigationGlyph { SERVERS, CREATE, STORAGE, NETWORK, MORE }
internal fun rootNavigationGlyph(root: RootDestination): RootNavigationGlyph = when (root) {
    RootDestination.SERVERS -> RootNavigationGlyph.SERVERS
    RootDestination.CREATE -> RootNavigationGlyph.CREATE
    RootDestination.STORAGE -> RootNavigationGlyph.STORAGE
    RootDestination.NETWORK -> RootNavigationGlyph.NETWORK
    RootDestination.MORE -> RootNavigationGlyph.MORE
}

/** Decorative geometry only. The owning navigation item supplies its full localized label. */
@Composable
fun RootNavigationIcon(root: RootDestination, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).clearAndSetSemantics {}) {
        val scale = size.minDimension / 24f
        val stroke = Stroke(1.8f * scale)
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, point(x1, y1), point(x2, y2), stroke.width)
        when (rootNavigationGlyph(root)) {
            RootNavigationGlyph.SERVERS -> {
                listOf(3f, 13f).forEach { y ->
                    drawRoundRect(color, point(3f, y), Size(18f * scale, 8f * scale), CornerRadius(2f * scale), style = stroke)
                    drawCircle(color, scale, point(7f, y + 4f)); line(11f, y + 4f, 17f, y + 4f)
                }
            }
            RootNavigationGlyph.CREATE -> {
                drawRoundRect(color, point(3f, 3f), Size(18f * scale, 18f * scale), CornerRadius(3f * scale), style = stroke)
                line(7f, 12f, 17f, 12f); line(12f, 7f, 12f, 17f)
            }
            RootNavigationGlyph.STORAGE -> {
                drawOval(color, point(3f, 3f), Size(18f * scale, 6f * scale), style = stroke)
                line(3f, 6f, 3f, 18f); line(21f, 6f, 21f, 18f)
                drawArc(color, 0f, 180f, false, point(3f, 9f), Size(18f * scale, 6f * scale), style = stroke)
                drawArc(color, 0f, 180f, false, point(3f, 15f), Size(18f * scale, 6f * scale), style = stroke)
            }
            RootNavigationGlyph.NETWORK -> {
                line(12f, 7f, 5f, 17f); line(12f, 7f, 19f, 17f); line(5f, 17f, 19f, 17f)
                listOf(point(12f, 5f), point(4f, 19f), point(20f, 19f)).forEach { drawCircle(color, 2.5f * scale, it, style = stroke) }
            }
            RootNavigationGlyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.7f * scale, point(it, 12f)) }
        }
    }
}
