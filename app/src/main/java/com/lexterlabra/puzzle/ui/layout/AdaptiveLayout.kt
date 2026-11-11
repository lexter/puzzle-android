package com.lexterlabra.puzzle.ui.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

enum class LayoutMode { COMPACT, SIDE_PANEL }

/** Fold/hinge state from the window (foldables). Null when the device has no fold. */
data class FoldInfo(val isSeparating: Boolean, val isVertical: Boolean)

@Composable
fun rememberFoldInfo(): FoldInfo? {
    val activity = LocalContext.current.findActivity() ?: return null
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let {
                FoldInfo(isSeparating = it.isSeparating, isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL)
            }
        }
    }
    val fold by flow.collectAsState(initial = null)
    return fold
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val SIDE_PANEL_MIN_WIDTH_DP = 600

/**
 * Phone portrait → [LayoutMode.COMPACT]. Wide landscape windows (tablets, unfolded foldables)
 * and any window split by a vertical hinge → [LayoutMode.SIDE_PANEL], where the board sits on
 * the start side and never crosses the hinge.
 */
fun layoutModeFor(widthDp: Int, heightDp: Int, fold: FoldInfo?): LayoutMode = when {
    fold != null && fold.isSeparating && fold.isVertical -> LayoutMode.SIDE_PANEL
    widthDp >= SIDE_PANEL_MIN_WIDTH_DP && widthDp > heightDp -> LayoutMode.SIDE_PANEL
    else -> LayoutMode.COMPACT
}
