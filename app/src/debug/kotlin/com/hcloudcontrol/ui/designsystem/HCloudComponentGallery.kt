package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.R
import com.hcloudcontrol.operations.*

/** Debug-only synthetic fixtures. Never connect to a provider or submit an operation. */
@Composable
fun HCloudComponentGallery() {
    var choice by remember { mutableStateOf("a") }
    var dialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val fixtureIntent = remember { OperationIntent("fixture-intent", ResourceIdentity(
        OperationContext("fixture", "https://example.invalid", OperationContext.Environment.TEST), "server", "42"), "change_protection", 0) }
    val choices = listOf(ChoiceItem("a", "Overview"), ChoiceItem("b", "Activity"))
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ds_gallery), style = MaterialTheme.typography.titleLarge)
        HCloudTabs(choices, choice, { choice = it })
        HCloudSegmentedControl(choices, choice, { choice = it })
        HCloudFilterChip("Synthetic filter", choice == "a", { choice = if (choice == "a") "b" else "a" })
        HCloudResourceCard(ResourceCardModel("fixture/server/1", "Synthetic server — long readable name", "Running", "2 vCPU · 4 GB RAM · 40 GB", "12:00", true), {})
        Box {
            OutlinedButton(onClick = { menu = true }) { Text("Menu fixture") }
            HCloudMenu(menu, listOf(MenuAction("detail", "Open details")), { menu = false }, {})
        }
        OutlinedButton(onClick = { dialog = true }) { Text("Dialog fixture") }
        OutlinedButton(onClick = { picker = true }) { Text("Picker fixture") }
        OutlinedButton(onClick = { review = true }) { Text("Operation review fixture") }
        listOf(OperationStage.ACCEPTED, OperationStage.OUTCOME_UNKNOWN, OperationStage.SUCCEEDED, OperationStage.FAILED).forEach { stage ->
            OperationResultPresentation(PersistedOperation(fixtureIntent, stage, 0, refreshFailed = stage == OperationStage.SUCCEEDED),
                "Synthetic project", "Synthetic resource", {}, onCheckStatus = {}, onRefreshResource = {}, onOpenResource = {})
        }
        listOf<CostPresentation>(CostPresentation.Loading, CostPresentation.Unavailable,
            CostPresentation.Error("Synthetic read failure"),
            CostPresentation.Available(CostSnapshot("cx-fixture", "Nuremberg · nbg1", "EUR", "0.00960000", "5.99000000", "0", "12:00")),
            CostPresentation.Available(CostSnapshot("cx-fixture", "Nuremberg · nbg1", "EUR", "0.0096", null, null, "12:00",
                listOf(CostComponent("IPv4", CostComponentCoverage.UNKNOWN)))))
            .forEach { HCloudCostPresentation(it) }
        SectionKind.entries.forEach { kind ->
            HCloudSectionState(SectionPresentation(kind), onRetry = if (kind != SectionKind.LOADING) ({}) else null) { Text("Synthetic content") }
        }
        Box(Modifier.height(360.dp)) {
            HCloudFormShell("Synthetic form", "Save fixture", {}) {
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("Resource name") })
            }
        }
    }
    }
    if (dialog) HCloudDialog("Synthetic review", "Confirm fixture", {}, { dialog = false }) { Text("No provider request is sent.") }
    if (picker) HCloudResourcePicker("Synthetic resources",
        ResourcePickerState("fixture", listOf(ResourcePickerItem("fixture-1", "Example location", "Nuremberg · nbg1"))),
        null, {}, { picker = false })
    if (review) OperationReviewDialog(OperationReviewContent(fixtureIntent, 0, "Synthetic protection review", "Synthetic project",
        "Synthetic server #42", listOf(ReviewChange("Delete protection", "Off", "On")), typedIdentity = "Synthetic server",
        acknowledgements = listOf(RiskAcknowledgement("risk", "This fixture never contacts a provider"))), {}, { review = false })
}

@Preview(name = "Light", widthDp = 360, heightDp = 760)
@Composable
private fun LightGallery() = HCloudDesignTheme(false) { HCloudComponentGallery() }

@Preview(name = "Dark", widthDp = 360, heightDp = 760)
@Composable
private fun DarkGallery() = HCloudDesignTheme(true) { HCloudComponentGallery() }

@Preview(name = "Large text", widthDp = 360, heightDp = 760, fontScale = 2f)
@Composable
private fun LargeTextGallery() = HCloudDesignTheme(false) { HCloudComponentGallery() }
