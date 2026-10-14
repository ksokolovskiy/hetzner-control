package com.hcloudcontrol

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The shared refresh affordance for every inventory-style screen.
 *
 * A screen owns the actual loading work; this component only provides the
 * consistent swipe-down gesture and progress indicator.  Callers must expose
 * an [isRefreshing] value that stays true until their one in-flight request
 * finishes.  This keeps the gesture from looking like a successful refresh
 * when a request was intentionally coalesced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HCloudPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}
