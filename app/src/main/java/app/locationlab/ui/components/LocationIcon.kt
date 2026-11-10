package app.locationlab.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.locationlab.R

internal enum class Glyph(val path: String, val filled: Boolean = false) {
    Menu("M4 5h16M4 12h16M4 19h11"),
    More("M5 12h.01M12 12h.01M19 12h.01"),
    Back("m15 5-7 7 7 7"),
    Next("m9 5 7 7-7 7"),
    Close("M6 6l12 12M18 6 6 18"),
    List("M3.5 6h.1m4.4 0h12m-16.5 6h.1m4.4 0h12m-16.5 6h.1m4.4 0h12"),
    Star("M12 2.75l2.8 5.69 6.28.91-4.54 4.42 1.07 6.25L12 17.07l-5.61 2.95 1.07-6.25L2.92 9.35l6.28-.91L12 2.75z"),
    StarFilled(Star.path, true),
    Pin("M9 3h6m-5 0v6l-3 4v1h10v-1l-3-4V3m-2 11v7"),
    Unpin("M4 4l16 16M9 3h6m-1 0v6l3 4v1h-3m-4-7v2l-3 4v1h3m2 1v6"),
    Trash("M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"),
    Appearance("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18ZM12 3v18"),
    Notification("M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4"),
    Info("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18ZM12 11v5m0-8h.01"),
    Layers("m12 3 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 16l9 5 9-5"),
    Alert("M12 5v8m0 5h.01"),
    Plus("M12 5v14M5 12h14"),
    Minus("M5 12h14"),
    Locate("M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16Zm0 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8ZM12 2v2m0 16v2M2 12h2m16 0h2"),
    Down("m5 9 7 7 7-7"),
    Search("M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13Zm5 11.5L21 21"),
    History("M3 12a9 9 0 1 1 3 6.7M3 4v8h7m2-5v5l4 2"),
    Edit("m4 16-1 5 5-1L20 8l-4-4L4 16Zm10-10 4 4"),
    Copy("M9 9h12v12H9ZM15 5V3H3v12h2"),
    Paste("M9 3h6v4H9ZM9 5H5v16h14V5h-4M9 13h6m-6 4h6"),
    Share("M5 12m0-2a2 2 0 1 0 0 4 2 2 0 1 0 0-4M19 5m0-2a2 2 0 1 0 0 4 2 2 0 1 0 0-4m0 14a2 2 0 1 0 0 4 2 2 0 1 0 0-4M7 11l10-5M7 13l10 5"),
    Refresh("M20 11a8 8 0 1 0-2 7M20 5v6h-6"),
    Check("m4 12 5 5L20 6"),
    Stop("M5 5h14v14H5Z"),
    Position(""),
}

@Composable
internal fun LocationIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = LocalContentColor.current,
) {
    if (glyph == Glyph.Position) {
        Icon(painterResource(R.drawable.ic_position_pin), null, modifier.size(size), tint = color)
        return
    }
    val path: Path = remember(glyph) { PathParser().parsePathString(glyph.path).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (glyph.filled) drawPath(path, color)
            else drawPath(path, color, style = Stroke(1.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
