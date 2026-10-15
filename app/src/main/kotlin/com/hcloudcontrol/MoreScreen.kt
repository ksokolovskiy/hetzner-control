package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.ui.designsystem.*

/** T02 is a real fifth root. Enlarged text switches to one column. */
@Composable
internal fun MoreScreen(onOpen: (Destination) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth / LocalDensity.current.fontScale >= 320.dp) 2 else 1
        LazyColumn(state = rememberRestorableLazyListState("more"), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(HCloudDesignTokens.contentInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            moreDestinationGroups.forEach { (group, destinations) ->
                item(key = "group-$group") {
                    Text(stringResource(group), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                }
                destinations.chunked(columns).forEach { row ->
                    item(key = row.joinToString { it.name }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { destination ->
                                HCloudResourceCard(
                                    ResourceCardModel("more:${destination.name}", stringResource(destination.labelRes), "", ""),
                                    onClick = { onOpen(destination) }, modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
