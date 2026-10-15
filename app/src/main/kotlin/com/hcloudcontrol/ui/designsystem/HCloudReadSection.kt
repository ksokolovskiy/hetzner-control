package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.hcloudcontrol.ui.state.SectionLoader
import com.hcloudcontrol.ui.state.SectionPhase
import com.hcloudcontrol.ui.state.SectionReadFailureKind
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.R
import java.text.DateFormat
import java.util.Date

/** All independently loaded detail sections use the same presentation and read-only retry. */
@Composable
fun <T> HCloudReadSection(loader: SectionLoader<T>, content: @Composable ColumnScope.(T) -> Unit) {
    val state = loader.state
    HCloudSectionState(SectionPresentation(
        kind = state.presentationKind(), stale = state.isStale, refreshing = state.refreshing,
        detail = state.error?.takeIf { state.hasSuccessfulResponse }?.let { stringResource(it.messageResource()) },
        updatedAt = state.updatedAtMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        },
    ), onRetry = loader::refresh) { state.data?.let { content(it) } }
}
