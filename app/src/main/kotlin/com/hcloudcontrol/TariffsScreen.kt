package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.BillingRateCard
import com.hcloudcontrol.core.domain.repository.BillingRateCardRepository
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.*
import java.text.DateFormat
import java.util.Date

/** Rates share their cost presentation with provisioning; they never imply accrued spending. */
@Composable
internal fun TariffsScreen(repository: BillingRateCardRepository, projectId: String, cloudRepository: HCloudRepository) {
    val owner = com.hcloudcontrol.operations.LocalOperationContext.current
    val contextKey = "$projectId:${owner?.endpoint}:tariffs"
    val loader = rememberSectionLoader(contextKey, { it: BillingRateCard -> it.serverTypes.isEmpty() }) {
        repository.getRateCard()
    }
    val state = loader.state
    val locations = rememberSectionLoader("$contextKey:locations") { cloudRepository.getLocations() }
    val reading = state.refreshing || locations.state.refreshing
    val uri = LocalUriHandler.current
    val card = state.data
    val stamp = state.updatedAtMillis?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    }
    HCloudPullToRefresh(reading, { loader.refresh(); locations.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(contextKey, contentReady = state.hasSuccessfulResponse),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.tariffs_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.tariffs_disclaimer))
                TextButton(onClick = { uri.openUri("https://console.hetzner.cloud/projects") }) {
                    Text(stringResource(R.string.tariffs_usage_link))
                }
                TextButton(onClick = { uri.openUri("https://accounts.hetzner.com/") }) {
                    Text(stringResource(R.string.tariffs_invoices_link))
                }
                HCloudReadSection(loader) { /* Rate cards below retain their own stable keys. */ }
                HCloudReadSection(locations) { /* Independent metadata failure never hides known rates. */ }
            }
            if (card != null) {
                items(card.serverTypes.sortedBy { it.name.lowercase() }, key = { it.id }) { type ->
                    if (type.locations.isEmpty()) {
                        HCloudSectionState(SectionPresentation(SectionKind.EMPTY,
                            stringResource(R.string.tariffs_incomplete_message)))
                    } else {
                        type.locations.sortedBy { it.location }.forEach { rate ->
                            HCloudCostPresentation(rate.toCostPresentation(type.name, card.currency,
                                card.vatRate, stamp.orEmpty(),
                                locationLabel = locations.state.data?.firstOrNull { it.name == rate.location }?.displayName() ?: rate.location))
                        }
                    }
                }
            }
        }
    }
}
