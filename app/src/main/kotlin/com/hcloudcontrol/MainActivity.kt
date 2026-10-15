package com.hcloudcontrol

import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import com.hcloudcontrol.connections.ConnectionFailure
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcloudcontrol.ui.state.SectionState
import com.hcloudcontrol.ui.state.SectionReadError
import com.hcloudcontrol.ui.state.EmptyReason
import com.hcloudcontrol.ui.state.SectionPhase
import com.hcloudcontrol.ui.designsystem.presentationKind
import com.hcloudcontrol.ui.designsystem.messageResource
import com.hcloudcontrol.ui.designsystem.HCloudSectionState
import com.hcloudcontrol.ui.designsystem.SectionPresentation
import com.hcloudcontrol.ui.designsystem.SectionKind
import com.hcloudcontrol.ui.designsystem.DirtyFormExitPrompt
import com.hcloudcontrol.connections.VerifiedConnectionResult
import com.hcloudcontrol.connections.ConnectionFailureKind
import com.hcloudcontrol.connections.verifiedCloudConnectionCoordinator
import com.hcloudcontrol.core.domain.model.BackupSnapshotImage
import com.hcloudcontrol.core.domain.model.BillingRateCard
import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.DnsScope
import com.hcloudcontrol.core.domain.model.FirewallInventory
import com.hcloudcontrol.core.domain.model.FloatingIpInventory
import com.hcloudcontrol.core.domain.model.LoadBalancerInventory
import com.hcloudcontrol.core.domain.model.PrimaryIpInventory
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.model.CreatePrivateNetworkSpec
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.ServerMetricType
import com.hcloudcontrol.core.domain.model.ServerMetrics
import com.hcloudcontrol.core.domain.model.ServerMetricsQuery
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.model.StorageBox
import com.hcloudcontrol.core.domain.model.StorageBoxSnapshot
import com.hcloudcontrol.core.domain.model.StorageBoxSubaccount
import com.hcloudcontrol.core.domain.model.CreateStorageBoxSnapshotSpec
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.model.CreateVolumeSpec
import com.hcloudcontrol.core.domain.model.CreatePrimaryIpSpec
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.repository.BillingRateCardRepository
import com.hcloudcontrol.core.domain.repository.DnsRepository
import com.hcloudcontrol.core.domain.repository.ServerMetricsRepository
import com.hcloudcontrol.core.domain.repository.LoadBalancerMetricsRepository
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository
import com.hcloudcontrol.core.domain.usecase.GetServersUseCase
import com.hcloudcontrol.core.domain.usecase.FirewallOperationsUseCase
import com.hcloudcontrol.core.domain.usecase.PrivateNetworkAttachmentUseCase
import com.hcloudcontrol.core.network.HCloudRepositoryFactory
import com.hcloudcontrol.core.network.BillingRateCardRepositoryFactory
import com.hcloudcontrol.core.network.DnsRepositoryFactory
import com.hcloudcontrol.core.network.ServerMetricsRepositoryFactory
import com.hcloudcontrol.core.network.LoadBalancerMetricsRepositoryFactory
import com.hcloudcontrol.core.network.StorageBoxRepositoryFactory
import com.hcloudcontrol.core.network.ObjectStorageRepositoryFactory
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.core.security.EncryptedHCloudTokenStore
import com.hcloudcontrol.core.security.HCloudTokenStore
import com.hcloudcontrol.core.security.EncryptedLegacyStorageBoxCredentialStore
import com.hcloudcontrol.core.security.PersistentHighImpactOperationsStore
import com.hcloudcontrol.core.security.HighImpactOperationsCapability
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.core.security.LegacyStorageBoxCredentialStore
import com.hcloudcontrol.core.security.EncryptedObjectStorageCredentialStore
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant
import androidx.browser.customtabs.CustomTabsIntent
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Locale changes made through AppCompatDelegate are delivered to active AppCompat delegates.
 * Using ComponentActivity here meant the preference could be set, but no active delegate was
 * available to recreate the screen with the new Resources configuration.  AppCompatActivity
 * also restores the persisted app locale when the process is launched again.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = EncryptedHCloudTokenStore(applicationContext)
        val legacyStorageBoxCredentialStore = EncryptedLegacyStorageBoxCredentialStore(applicationContext)
        val objectStorageCredentialStore = EncryptedObjectStorageCredentialStore(applicationContext)
        val objectStorageRepository = ObjectStorageRepositoryFactory.create(objectStorageCredentialStore::activeCredentials)
        val appearanceStore = AppearanceStore(applicationContext)
        val highImpactOperationsStore = PersistentHighImpactOperationsStore(applicationContext)
        setContent {
            var appearanceMode by remember { mutableStateOf(appearanceStore.read()) }
            var highImpactOperations by remember { mutableStateOf(highImpactOperationsStore.capability()) }
            HCloudControlApp(
                tokenStore = tokenStore,
                legacyStorageBoxCredentialStore = legacyStorageBoxCredentialStore,
                objectStorageCredentialStore = objectStorageCredentialStore,
                objectStorageRepository = objectStorageRepository,
                appearanceMode = appearanceMode,
                onAppearanceModeChange = { mode -> appearanceStore.save(mode); appearanceMode = mode },
                highImpactOperations = highImpactOperations,
                onHighImpactOperationsChange = { allowed ->
                    highImpactOperations = HighImpactOperationsCapability.Disabled
                    try {
                        highImpactOperationsStore.setAllowed(allowed)
                        highImpactOperations = highImpactOperationsStore.capability()
                    } catch (_: Exception) { throw com.hcloudcontrol.core.security.LocalPersistenceException() }
                },
            )
        }
    }
}

/** A request without a selected credential must not be routed to any Cloud host. */
internal fun HCloudTokenStore.requireActiveConnectionConfig(): HCloudConnectionConfig =
    requireNotNull(activeConnection()?.config) { "No active Cloud project connection" }

// Visual definitions live once in the shared design system.
private val HCloudColors get() = com.hcloudcontrol.ui.designsystem.HCloudDarkScheme
private val HCloudLightColors get() = com.hcloudcontrol.ui.designsystem.HCloudLightScheme
private val HCloudTypography get() = com.hcloudcontrol.ui.designsystem.HCloudDesignTypography
private val HCloudShapes get() = com.hcloudcontrol.ui.designsystem.HCloudDesignShapes

internal enum class AppearanceMode(val labelRes: Int) {
    SYSTEM(R.string.appearance_system),
    LIGHT(R.string.appearance_light),
    DARK(R.string.appearance_dark),
}

internal enum class LanguageMode(val labelRes: Int, val tags: String?) {
    SYSTEM(R.string.language_system, null),
    ENGLISH(R.string.language_english, "en"),
    RUSSIAN(R.string.language_russian, "ru"),
    GERMAN(R.string.language_german, "de"),
}

/** A one-shot, non-secret acknowledgement that survives the onboarding-to-home transition. */
private enum class ConnectionNotice { TestServerConnected, CloudProjectConnected }

internal fun applyLanguage(mode: LanguageMode) {
    AppCompatDelegate.setApplicationLocales(
        mode.tags?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
    )
}

/** AppCompat persists this setting (including after process death); this only maps it back to the UI. */
internal fun currentLanguageMode(): LanguageMode = when (
    AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').lowercase()
) {
    "en" -> LanguageMode.ENGLISH
    "ru" -> LanguageMode.RUSSIAN
    "de" -> LanguageMode.GERMAN
    else -> LanguageMode.SYSTEM
}

private class AppearanceStore(context: android.content.Context) {
    private val preferences = context.getSharedPreferences("hcloud_appearance", android.content.Context.MODE_PRIVATE)

    fun read(): AppearanceMode = runCatching {
        AppearanceMode.valueOf(preferences.getString("mode", AppearanceMode.SYSTEM.name) ?: AppearanceMode.SYSTEM.name)
    }.getOrDefault(AppearanceMode.SYSTEM)

    fun save(mode: AppearanceMode) {
        preferences.edit().putString("mode", mode.name).apply()
    }
}

/** Access gate for every UI control that can mutate resources or incur a charge. */
internal val LocalHighImpactOperations = staticCompositionLocalOf { HighImpactOperationsCapability.Disabled }

@Composable
private fun HCloudControlApp(
    tokenStore: HCloudTokenStore,
    legacyStorageBoxCredentialStore: LegacyStorageBoxCredentialStore,
    objectStorageCredentialStore: ObjectStorageCredentialStore,
    objectStorageRepository: ObjectStorageRepository,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    highImpactOperations: HighImpactOperationsCapability,
    onHighImpactOperationsChange: (Boolean) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val operationCoordinator = remember(appContext) {
        runCatching {
            com.hcloudcontrol.operations.OperationCoordinator(
                com.hcloudcontrol.operations.SharedPreferencesOperationStore(appContext),
            )
        }.getOrNull()
    }
    val useDarkTheme = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val colorScheme = if (useDarkTheme) {
        HCloudColors
    } else {
        HCloudLightColors
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = HCloudTypography, shapes = HCloudShapes) {
        var showJournalError by remember { mutableStateOf(operationCoordinator == null) }
        if (showJournalError) {
            com.hcloudcontrol.ui.designsystem.HCloudDialog(
                title = stringResource(R.string.operation_journal_unavailable_title),
                confirmLabel = stringResource(R.string.common_close),
                onConfirm = { showJournalError = false },
                onDismiss = { showJournalError = false },
            ) { Text(stringResource(R.string.operation_journal_unavailable_message)) }
        }
        val scrollPositionRegistry = rememberScrollPositionRegistry()
        CompositionLocalProvider(
            LocalHighImpactOperations provides if (operationCoordinator == null)
                HighImpactOperationsCapability.Disabled else highImpactOperations,
            LocalScrollPositionRegistry provides scrollPositionRegistry,
            com.hcloudcontrol.operations.LocalOperationCoordinator provides operationCoordinator,
        ) {
            Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            var connections by remember { mutableStateOf(tokenStore.connections()) }
            var selectedConnectionId by remember { mutableStateOf(tokenStore.activeConnectionId()) }
            var addingProject by rememberSaveable { mutableStateOf(false) }
            val connectionDraftHolder = rememberSaveableStateHolder()
            val connectionActivity = androidx.activity.compose.LocalActivity.current
            var connectionNotice by remember { mutableStateOf<ConnectionNotice?>(null) }
            val connectionCoordinator = remember(tokenStore) { verifiedCloudConnectionCoordinator(tokenStore) }
            if (connections.isEmpty() || addingProject) {
                connectionDraftHolder.SaveableStateProvider("new-cloud-connection") {
                Onboarding(
                    isAdditionalProject = connections.isNotEmpty(),
                    onCancel = { if (connections.isEmpty()) connectionActivity?.finish() else addingProject = false },
                    onConnect = { projectName, token, config ->
                        val result = withContext(Dispatchers.IO) { connectionCoordinator.connect(projectName, token, config) }
                        if (result is VerifiedConnectionResult.Connected) {
                            connectionDraftHolder.removeState("new-cloud-connection")
                            connections = tokenStore.connections()
                            selectedConnectionId = tokenStore.activeConnectionId()
                            addingProject = false
                            connectionNotice = if (config.kind == ConnectionKind.TEST_MOCK) {
                                ConnectionNotice.TestServerConnected
                            } else {
                                ConnectionNotice.CloudProjectConnected
                            }
                        }
                        result
                    },
                )
                }
            } else {
                val owner = connections.firstOrNull { it.id == selectedConnectionId } ?: connections.first()
                val clients = remember(tokenStore, owner.id, owner.config) {
                    com.hcloudcontrol.connections.ProjectApiClients(com.hcloudcontrol.connections.PinnedCloudConnection(tokenStore, owner))
                }
                CloudHome(
                activeProject = owner,
                getServers = GetServersUseCase(clients.cloud),
                repository = clients.cloud,
                legacyStorageBoxCredentialStore = legacyStorageBoxCredentialStore,
                storageBoxRepository = clients.storageBoxes,
                objectStorageCredentialStore = objectStorageCredentialStore,
                objectStorageRepository = objectStorageRepository,
                billingRepository = clients.billing,
                serverMetricsRepository = clients.serverMetrics,
                loadBalancerMetricsRepository = clients.loadBalancerMetrics,
                dnsRepository = clients.dns,
                tokenStore = tokenStore,
                connections = connections,
                onConnectionsChanged = {
                    connections = tokenStore.connections()
                    selectedConnectionId = tokenStore.activeConnectionId()
                },
                onAddProject = { addingProject = true },
                appearanceMode = appearanceMode,
                onAppearanceModeChange = onAppearanceModeChange,
                onHighImpactOperationsChange = onHighImpactOperationsChange,
                connectionNotice = connectionNotice,
                onConnectionNoticeShown = { connectionNotice = null },
            )
            }
            }
        }
    }
}

@Composable
private fun Onboarding(
    isAdditionalProject: Boolean,
    onCancel: () -> Unit,
    onConnect: suspend (String, String, HCloudConnectionConfig) -> VerifiedConnectionResult,
) {
    var projectName by rememberSaveable { mutableStateOf("") }
    var connectionKind by rememberSaveable { mutableStateOf(ConnectionKind.REAL) }
    var realToken by remember { mutableStateOf("") }
    var testServerToken by remember { mutableStateOf("") }
    var realTokenVisible by remember { mutableStateOf(false) }
    var testTokenVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<ConnectionFailure?>(null) }
    var checkingConnection by remember { mutableStateOf(false) }
    var checkJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val connectionScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    val tokenFocus = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current
    val isTestServer = connectionKind == ConnectionKind.TEST_MOCK
    val token = if (isTestServer) testServerToken else realToken
    val testProfileName = stringResource(R.string.onboarding_test_profile_name)
    val invalidName = submitted && !isTestServer && projectName.isBlank()
    val invalidToken = submitted && token.isBlank()
    val exitGuard = rememberDirtyFormExitGuard(
        isDirty = projectName.isNotBlank() || realToken.isNotBlank() || testServerToken.isNotBlank(),
        onRetain = { realToken = ""; testServerToken = "" },
        onDiscard = { projectName = ""; connectionKind = ConnectionKind.REAL; realToken = ""; testServerToken = "" },
        onLeave = { checkJob?.cancel(); onCancel() },
        contextKey = "new-cloud-connection",
    )
    DirtyFormExitPrompt(exitGuard)
    BackHandler { if (checkingConnection) checkJob?.cancel() else exitGuard.requestLeave() }
    val submitConnection = {
        // Do not rely on Material Button's internal gesture node here. On a physical
        // device this CTA lives inside a scrolling card below a password field; a
        // plain clickable surface keeps touch, keyboard and accessibility activation
        // on the same explicit callback.
        focusManager.clearFocus(force = true)
        submitted = true
        connectionError = null
        onboardingConnectionRequest(
            connectionKind = connectionKind,
            projectName = projectName,
            testProfileName = testProfileName,
            token = token,
        )?.let { request ->
            if (!checkingConnection) {
                checkingConnection = true
                checkJob = connectionScope.launch {
                    try {
                        val result = onConnect(request.displayName, request.token, request.config)
                        if (result is VerifiedConnectionResult.Failed) connectionError = result.failure
                    } finally { checkingConnection = false }
                }
            }
        }
        Unit
    }
    LaunchedEffect(submitted, connectionError, checkingConnection) {
        if (!checkingConnection && submitted) {
            if (invalidName) nameFocus.requestFocus()
            else if (invalidToken || connectionError?.kind == ConnectionFailureKind.INVALID_TOKEN) tokenFocus.requestFocus()
        }
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        if (isAdditionalProject) TextButton(onClick = { if (checkingConnection) checkJob?.cancel() else exitGuard.requestLeave() }) { Text(stringResource(R.string.common_back)) }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(if (isAdditionalProject) R.string.onboarding_add_project else R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
        Text(stringResource(R.string.onboarding_connect_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onboarding_connection_mode), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        com.hcloudcontrol.ui.designsystem.HCloudSegmentedControl(
            items = listOf(
                com.hcloudcontrol.ui.designsystem.ChoiceItem(ConnectionKind.REAL.name, stringResource(R.string.onboarding_real_hetzner), !checkingConnection),
                com.hcloudcontrol.ui.designsystem.ChoiceItem(ConnectionKind.TEST_MOCK.name, stringResource(R.string.onboarding_test_server), !checkingConnection),
            ),
            selectedId = connectionKind.name,
            onSelected = { connectionKind = ConnectionKind.valueOf(it); submitted = false; connectionError = null },
        )
        Spacer(Modifier.height(12.dp))
        if (isTestServer) {
            TestServerBadge()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.onboarding_test_server_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                HCloudConnectionConfig.TEST_MOCK_BASE_URL,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        } else {
            com.hcloudcontrol.ui.designsystem.HCloudFormField(value = projectName, onValueChange = { projectName = it }, enabled = !checkingConnection,
                modifier = Modifier.focusRequester(nameFocus), label = stringResource(R.string.onboarding_project_name_label),
                error = stringResource(R.string.onboarding_project_name_error).takeIf { invalidName }, singleLine = true)
            Spacer(Modifier.height(12.dp))
        }
        val tokenVisible = if (isTestServer) testTokenVisible else realTokenVisible
        com.hcloudcontrol.ui.designsystem.HCloudFormField(
            value = token,
            enabled = !checkingConnection,
            onValueChange = { value -> if (isTestServer) testServerToken = value else realToken = value; connectionError = null },
            modifier = Modifier.focusRequester(tokenFocus),
            label = stringResource(if (isTestServer) R.string.onboarding_test_token_label else R.string.onboarding_token_label),
            error = stringResource(if (isTestServer) R.string.onboarding_test_token_error else R.string.onboarding_token_error)
                .takeIf { invalidToken || connectionError?.kind == ConnectionFailureKind.INVALID_TOKEN },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { if (isTestServer) testTokenVisible = !testTokenVisible else realTokenVisible = !realTokenVisible }) { Text(stringResource(if (tokenVisible) R.string.common_hide else R.string.common_show)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitConnection() }),
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = !checkingConnection, role = Role.Button, onClick = submitConnection),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (checkingConnection) R.string.connection_checking else if (isTestServer) R.string.onboarding_open_test_server else R.string.onboarding_connect_securely),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (checkingConnection) {
            TextButton(onClick = { checkJob?.cancel() }) { Text(stringResource(R.string.common_cancel)) }
        }
        if (submitted && (invalidName || invalidToken || connectionError != null)) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when {
                        invalidName -> R.string.onboarding_project_name_error
                        invalidToken -> if (isTestServer) R.string.onboarding_test_token_error else R.string.onboarding_token_error
                        connectionError?.kind == ConnectionFailureKind.OFFLINE -> R.string.connection_check_offline
                        connectionError?.kind == ConnectionFailureKind.INVALID_TOKEN -> R.string.connection_check_invalid_token
                        connectionError?.kind == ConnectionFailureKind.PERMISSION_DENIED -> R.string.connection_check_denied
                        connectionError?.kind == ConnectionFailureKind.SECURE_STORAGE -> R.string.onboarding_save_error
                        else -> R.string.connection_check_service
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        connectionError?.let { failure ->
            val safeDiagnostics = listOfNotNull(failure.httpStatus?.let { "HTTP $it" }, failure.code, failure.requestId).joinToString(" · ")
            if (safeDiagnostics.isNotBlank()) Text(stringResource(R.string.connection_safe_diagnostics, safeDiagnostics), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { uriHandler.openUri("https://console.hetzner.com/") }) { Text(stringResource(R.string.connection_official_console_help)) }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.onboarding_scope_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** A subaccount lookup is deliberately kept read-only: the API never exposes credentials here. */
private sealed interface StorageBoxSubaccountsState {
    data object Loading : StorageBoxSubaccountsState
    data class Content(val subaccounts: List<StorageBoxSubaccount>) : StorageBoxSubaccountsState
    data object Error : StorageBoxSubaccountsState
}
internal enum class Destination(val labelRes: Int, val marker: String) { Servers(R.string.nav_servers, "▣"), Catalog(R.string.nav_catalog, "⌕"), Storage(R.string.nav_storage, "▤"), Images(R.string.nav_images, "▧"), StorageBoxes(R.string.nav_storage_boxes, "▦"), ObjectStorage(R.string.nav_object_storage, "◉"), Tariffs(R.string.nav_tariffs, "¤"), LoadBalancers(R.string.nav_load_balancers, "⇄"), Certificates(R.string.nav_certificates, "◇"), SshKeys(R.string.nav_ssh_keys, "⌘"), Network(R.string.nav_network, "⌁"), PlacementGroups(R.string.nav_placement_groups, "⊞"), Dns(R.string.nav_dns, "≋"), Settings(R.string.nav_settings, "⚙"), More(R.string.nav_more, "⋯") }

/** Exactly five roots; secondary resource areas live under More or their canonical aliases. */
private val primaryDestinations = listOf(
    Destination.Servers,
    Destination.Catalog,
    Destination.Storage,
    Destination.Network,
    Destination.More,
)

/** One canonical destination per row, shared with the feature entry points. */
internal val moreDestinationGroups = listOf(
    R.string.more_group_access to listOf(Destination.SshKeys, Destination.Certificates),
    R.string.more_group_cloud_resources to listOf(Destination.Images, Destination.PlacementGroups, Destination.Dns, Destination.LoadBalancers),
    R.string.more_group_products to listOf(Destination.StorageBoxes, Destination.ObjectStorage),
    R.string.more_group_application to listOf(Destination.Tariffs, Destination.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudHome(
    activeProject: CloudProjectConnection,
    getServers: GetServersUseCase,
    repository: HCloudRepository,
    legacyStorageBoxCredentialStore: LegacyStorageBoxCredentialStore,
    storageBoxRepository: StorageBoxRepository,
    objectStorageCredentialStore: ObjectStorageCredentialStore,
    objectStorageRepository: ObjectStorageRepository,
    billingRepository: BillingRateCardRepository,
    serverMetricsRepository: ServerMetricsRepository,
    loadBalancerMetricsRepository: LoadBalancerMetricsRepository,
    dnsRepository: DnsRepository,
    tokenStore: HCloudTokenStore,
    connections: List<CloudProjectConnection>,
    onConnectionsChanged: () -> Unit,
    onAddProject: () -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onHighImpactOperationsChange: (Boolean) -> Unit,
    connectionNotice: ConnectionNotice?,
    onConnectionNoticeShown: () -> Unit,
) {
    var navigation by rememberCanonicalNavigation()
    val navigationChrome = remember { NavigationChromeHost() }
    var showProjectPicker by remember { mutableStateOf(false) }
    val destination = navigation.current.legacyDestination()
    val resourceContext = ResourceContext(activeProject.id, activeProject.config.baseUrl, ContextKind.CLOUD)
    val rootPages = rememberSaveableStateHolder()
    var previousRootOwner by rememberSaveable { mutableStateOf(resourceContext.ownerId) }
    var previousRootEndpoint by rememberSaveable { mutableStateOf(resourceContext.endpoint) }
    LaunchedEffect(resourceContext) {
        val oldContext = runCatching { ResourceContext(previousRootOwner, previousRootEndpoint, ContextKind.CLOUD) }.getOrNull()
        oldContext?.let { retiredCloudRootKeys(it, resourceContext).forEach(rootPages::removeState) }
        previousRootOwner = resourceContext.ownerId
        previousRootEndpoint = resourceContext.endpoint
    }
    fun openDestination(target: Destination) {
        navigation = navigation.open(target.canonicalRoute(resourceContext))
    }
    val serverInventoryScope = "${activeProject.id}:${activeProject.config.baseUrl}:servers"
    val serverLoader = com.hcloudcontrol.ui.state.rememberSectionLoader(serverInventoryScope,
        { servers: List<Server> -> servers.isEmpty() }) { getServers() }
    val serverState = serverLoader.state
    val serverRefreshInProgress = serverState.refreshing
    var serverQuery by rememberContextualText("$serverInventoryScope:query")
    var serverFilterName by rememberContextualText("$serverInventoryScope:filter", ServerInventoryFilter.ALL.name)
    val serverFilter = ServerInventoryFilter.entries.firstOrNull { it.name == serverFilterName } ?: ServerInventoryFilter.ALL
    var selectedServerId by rememberContextualResourceId("server:${activeProject.id}:${activeProject.config.baseUrl}")
    val provisioningDraftHolder = rememberSaveableStateHolder()
    val provisioningDraftKey = "create-server:${activeProject.id}:${activeProject.config.baseUrl}"
    var lastProvisioningDraftKey by rememberSaveable { mutableStateOf(provisioningDraftKey) }
    LaunchedEffect(provisioningDraftKey) {
        if (lastProvisioningDraftKey != provisioningDraftKey) {
            provisioningDraftHolder.removeState(lastProvisioningDraftKey)
            lastProvisioningDraftKey = provisioningDraftKey
        }
    }
    var returnToProvisioningContext by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateServer by rememberContextualFlag(provisioningDraftKey)
    var createServerTypePrefill by remember(activeProject.id) { mutableStateOf<ServerTypeOption?>(null) }
    var createServerLocationPrefill by remember(activeProject.id) { mutableStateOf<LocationOption?>(null) }
    // Links from the create catalog are children of that workflow.  Remember their parent
    // so the system gesture returns there instead of unexpectedly jumping to Servers.
    var primaryBackDestination by remember { mutableStateOf<Destination?>(null) }
    // Dialogs dismiss themselves; hosted children share this scaffold and Back entry point.
    fun navigateBack() {
        when {
            navigationChrome.current != null -> navigationChrome.current?.onBack?.invoke()
            showCreateServer -> showCreateServer = false
            selectedServerId != null -> selectedServerId = null
            navigation.stack.size > 1 -> {
                navigation = navigation.up()
                if (destination == Destination.SshKeys && returnToProvisioningContext == provisioningDraftKey) {
                    returnToProvisioningContext = null
                    showCreateServer = true
                }
            }
            primaryBackDestination != null -> {
                openDestination(primaryBackDestination!!)
                primaryBackDestination = null
            }
            destination != Destination.Servers -> navigation = navigation.selectRoot(RootDestination.SERVERS)
        }
    }
    BackHandler(enabled = navigationChrome.current != null || showCreateServer || selectedServerId != null || navigation.decideBack() != BackDecision.EXIT) { navigateBack() }
    LaunchedEffect(activeProject.id) {
        val previousContext = navigation.current.context
        val expectedContext = destination.canonicalRoute(resourceContext).context
        if (previousContext?.kind == ContextKind.CLOUD && previousContext != expectedContext) {
            navigation = navigation.switchCloudContext(resourceContext)
        }
        primaryBackDestination = null
    }
    val s3RouteContext = navigationChrome.current?.route?.context?.takeIf { it.kind == ContextKind.S3 }
        ?: navigation.current.context?.takeIf { it.kind == ContextKind.S3 }
    val s3ConnectionId = s3RouteContext?.ownerId ?: objectStorageCredentialStore.activeConnectionId()
    val s3Connection = if (destination == Destination.ObjectStorage) objectStorageCredentialStore.connections().firstOrNull {
        it.id == s3ConnectionId && (s3RouteContext == null || s3RouteContext.endpoint.trimEnd('/') == "https://${it.location.host}")
    } else null
    val inspectedCloudConnection = navigationChrome.current?.route?.takeIf { it.resourceType == ResourceType.PROJECT }?.resourceId
        ?.let { profileId -> connections.firstOrNull { it.id == profileId } }
    val rootPageKey = if (destination == Destination.ObjectStorage) {
        // Product-level owner stays stable while S3 switches profiles or displays an orphan result.
        // S3's internal drafts/routes validate their own credential context independently.
        "root-state:S3:connections:MORE"
    } else rootContentStateKey(resourceContext, navigation.current.root)
    val snackbarHostState = remember { SnackbarHostState() }
    var localPersistenceFailure by remember { mutableStateOf(0) }
    val localPersistenceFailureText = stringResource(R.string.settings_local_save_failed)
    LaunchedEffect(localPersistenceFailure) {
        if (localPersistenceFailure > 0) snackbarHostState.showSnackbar(localPersistenceFailureText)
    }
    val connectionNoticeText = connectionNotice?.let {
        stringResource(
            if (it == ConnectionNotice.TestServerConnected) {
                R.string.onboarding_test_server_connected
            } else {
                R.string.onboarding_project_connected
            },
        )
    }
    LaunchedEffect(connectionNoticeText) {
        connectionNoticeText?.let { message ->
            onConnectionNoticeShown()
            snackbarHostState.showSnackbar(message)
        }
    }
    fun refresh() {
        serverLoader.refresh()
    }
    CompositionLocalProvider(
        LocalScrollProjectNamespace provides if (destination == Destination.ObjectStorage) "independent-s3" else activeProject.id,
        LocalNavigationChrome provides navigationChrome,
        LocalNavigationContext provides resourceContext,
        com.hcloudcontrol.operations.LocalOperationContext provides com.hcloudcontrol.operations.OperationContext(
            activeProject.id, activeProject.config.baseUrl,
            if (activeProject.config.kind == ConnectionKind.TEST_MOCK)
                com.hcloudcontrol.operations.OperationContext.Environment.TEST
            else com.hcloudcontrol.operations.OperationContext.Environment.CLOUD,
        ),
    ) {
            com.hcloudcontrol.ui.designsystem.HCloudAppScaffold(
                title = if (showCreateServer) stringResource(R.string.provision_title) else navigationChrome.current?.title?.invoke() ?: stringResource(destination.labelRes),
                project = if (destination == Destination.ObjectStorage) com.hcloudcontrol.ui.designsystem.ProjectContext(
                    s3Connection?.displayName ?: s3RouteContext?.ownerId?.let { "S3 #$it" } ?: stringResource(R.string.object_storage_connection),
                    s3Connection?.location?.id ?: s3RouteContext?.endpoint?.let { endpoint -> runCatching { java.net.URI(endpoint).host }.getOrNull() } ?: "S3",
                ) else com.hcloudcontrol.ui.designsystem.ProjectContext((inspectedCloudConnection ?: activeProject).displayName,
                    stringResource(R.string.test_server_badge).takeIf { (inspectedCloudConnection ?: activeProject).config.kind == ConnectionKind.TEST_MOCK }),
                isRoot = !showCreateServer && selectedServerId == null && navigation.showBottomNavigation && navigationChrome.current == null,
                onBack = ::navigateBack,
                onProjectClick = if (destination == Destination.ObjectStorage) null else ({ showProjectPicker = true }),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (!showCreateServer && selectedServerId == null && destination == Destination.Servers && LocalHighImpactOperations.current.isAllowed) {
                        ExtendedFloatingActionButton(onClick = {
                            createServerTypePrefill = null
                            createServerLocationPrefill = null
                            showCreateServer = true
                        }) { Text(stringResource(R.string.create_server)) }
                    }
                },
                rootNavigation = {
                    if (selectedServerId == null) NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        primaryDestinations.forEach { item ->
                            val fullLabel = stringResource(item.labelRes)
                            NavigationBarItem(
                                modifier = Modifier.semantics { contentDescription = fullLabel },
                                selected = item == destination,
                                onClick = { primaryBackDestination = null; navigation = navigation.selectRoot(item.canonicalRoute(resourceContext).root) },
                                icon = { com.hcloudcontrol.ui.designsystem.RootNavigationIcon(item.canonicalRoute(resourceContext).root) },
                                label = { Text(fullLabel, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {}) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
    if (showCreateServer) {
        provisioningDraftHolder.SaveableStateProvider(provisioningDraftKey) {
        ServerProvisioningScreen(
            repository = repository,
            isTestServer = activeProject.config.kind == ConnectionKind.TEST_MOCK,
            initialServerType = createServerTypePrefill,
            initialLocation = createServerLocationPrefill,
            onCancel = {
                showCreateServer = false
                createServerTypePrefill = null
                createServerLocationPrefill = null
            },
            onCreated = {
                provisioningDraftHolder.removeState(provisioningDraftKey)
                showCreateServer = false
                createServerTypePrefill = null
                createServerLocationPrefill = null
                refresh()
            },
            onCreatedResource = { serverId ->
                provisioningDraftHolder.removeState(provisioningDraftKey)
                showCreateServer = false
                createServerTypePrefill = null
                createServerLocationPrefill = null
                navigation = navigation.selectRoot(RootDestination.SERVERS)
                selectedServerId = serverId
                refresh()
            },
            onManageSshKeys = {
                returnToProvisioningContext = provisioningDraftKey
                showCreateServer = false
                openDestination(Destination.SshKeys)
            },
        )
        }
    } else
            selectedServerId?.let { serverId ->
                val metadata = com.hcloudcontrol.ui.state.rememberSectionLoader("${activeProject.id}:${activeProject.config.baseUrl}:server:$serverId:metadata") {
                    repository.getServer(serverId)
                }
                RegisterResourceChildNavigation(RootDestination.SERVERS, ResourceType.SERVER, serverId.toString(),
                    metadata.state.data?.name ?: stringResource(R.string.server_overview)) { selectedServerId = null }
                val currentServer = metadata.state.data
                if (currentServer == null) com.hcloudcontrol.ui.designsystem.HCloudReadSection(metadata) {}
                else Column(Modifier.fillMaxSize()) {
                    if (metadata.state.isStale || metadata.state.refreshing) com.hcloudcontrol.ui.designsystem.HCloudReadSection(metadata) {}
                    Box(Modifier.weight(1f)) {
                        ServerDetails(
                            currentServer,
                            !metadata.state.canOfferMutation,
                            repository,
                            serverMetricsRepository,
                            onActionCompleted = { metadata.refresh(); refresh() },
                            onDeleted = { selectedServerId = null; refresh() },
                            onBack = { selectedServerId = null },
                        )
                    }
                }
            } ?: rootPages.SaveableStateProvider(rootPageKey) { when (destination) {
                Destination.More -> MoreScreen(onOpen = ::openDestination)
                Destination.Servers -> ServersScreen(serverState, serverRefreshInProgress, ::refresh,
                    serverQuery, { serverQuery = it }, serverFilter, { serverFilterName = it.name }) { selectedServerId = it.id }
                Destination.Catalog -> ServerCatalogScreen(
                    repository = repository,
                    projectId = activeProject.id,
                    onConfigureServer = { type, location ->
                        createServerTypePrefill = type
                        createServerLocationPrefill = location
                        showCreateServer = true
                    },
                    onOpenStorage = { primaryBackDestination = Destination.Catalog; openDestination(Destination.Storage) },
                    onOpenNetwork = { primaryBackDestination = Destination.Catalog; openDestination(Destination.Network) },
                    onOpenSettings = { openDestination(Destination.Settings) },
                )
                Destination.Storage -> StorageInventoryScreen(
                    repository = repository,
                    projectId = activeProject.id,
                    operationsAllowed = LocalHighImpactOperations.current.isAllowed,
                )
                Destination.Images -> CloudImagesScreen(repository = repository, projectId = activeProject.id)
                Destination.StorageBoxes -> StorageBoxesScreen(
                    project = activeProject,
                    legacyCredentialStore = legacyStorageBoxCredentialStore,
                    repository = storageBoxRepository,
                )
                Destination.ObjectStorage -> ObjectStorageScreen(store = objectStorageCredentialStore, repository = objectStorageRepository,
                    onBackToMore = { navigation = navigation.up() })
                Destination.Tariffs -> TariffsEstimateScreen(repository = billingRepository, projectId = activeProject.id, cloudRepository = repository)
                Destination.LoadBalancers -> LoadBalancersScreen(repository = repository, metricsRepository = loadBalancerMetricsRepository, projectId = activeProject.id, billingRepository = billingRepository)
                Destination.Certificates -> CertificatesScreen(repository = repository, projectId = activeProject.id)
                Destination.SshKeys -> SshKeysScreen(repository = repository, projectId = activeProject.id)
                Destination.Network -> NetworkWorkspaceScreen(repository = repository, projectId = activeProject.id,
                    onOpenLoadBalancers = { openDestination(Destination.LoadBalancers) },
                    onOpenPlacementGroups = { openDestination(Destination.PlacementGroups) })
                Destination.PlacementGroups -> PlacementGroupsScreen(repository = repository, projectId = activeProject.id)
                Destination.Dns -> DnsReadOnlyScreen(projectId = activeProject.id, repository = dnsRepository)
                Destination.Settings -> SettingsScreen(
                    activeProject = activeProject,
                    connections = connections,
                    onAddProject = onAddProject,
                    onDisconnect = { connectionId ->
                        tokenStore.removeConnection(connectionId)
                        onConnectionsChanged()
                    },
                    onSelectProject = { connectionId ->
                        tokenStore.selectConnection(connectionId)
                        onConnectionsChanged()
                    },
                    appearanceMode = appearanceMode,
                    onAppearanceModeChange = onAppearanceModeChange,
                    onHighImpactOperationsChange = onHighImpactOperationsChange,
                )
            }
            }
        }
        if (showProjectPicker) com.hcloudcontrol.ui.designsystem.HCloudChoiceSheet(
            title = stringResource(R.string.settings_all_connections),
            items = connections.map { connection -> com.hcloudcontrol.ui.designsystem.ChoiceItem(connection.id,
                connection.displayName + if (connection.config.kind == ConnectionKind.TEST_MOCK) " · " + stringResource(R.string.test_server_badge) else "") },
            selectedId = activeProject.id,
            onSelected = { connectionId -> if (connectionId != activeProject.id) {
                try { tokenStore.selectConnection(connectionId); onConnectionsChanged() }
                catch (_: Exception) { localPersistenceFailure++ }
            } },
            onDismiss = { showProjectPicker = false }, searchable = true,
        )
    }
    }
}

@Composable
private fun TestServerBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            stringResource(R.string.test_server_badge),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A shared hierarchy for inventory, detail and settings screens. */
@Composable
private fun HCloudScreenHeader(
    title: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        supporting?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServersScreen(
    state: SectionState<List<Server>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ServerInventoryFilter,
    onFilterChange: (ServerInventoryFilter) -> Unit,
    onServerClick: (Server) -> Unit,
) = HCloudPullToRefresh(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,
    modifier = Modifier.fillMaxSize(),
) {
    val servers = state.data.orEmpty()
    val visibleServers = remember(servers, query, filter) { filterServers(servers, query, filter) }
    val updatedAt = state.updatedAtMillis?.let {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
            LocalContext.current.resources.configuration.locales[0],
        ).format(java.util.Date(it))
    }
    val kind = when {
        state.hasSuccessfulResponse && servers.isNotEmpty() && visibleServers.isEmpty() -> SectionKind.NO_RESULTS
        state.hasSuccessfulResponse && servers.isNotEmpty() -> SectionKind.CONTENT
        state.hasSuccessfulResponse -> SectionKind.EMPTY
        else -> state.presentationKind()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberRestorableLazyListState("servers", state.hasSuccessfulResponse),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            if (LocalNavigationChrome.current == null) HCloudScreenHeader(stringResource(R.string.servers_title),
                if (state.hasSuccessfulResponse) stringResource(R.string.servers_count, servers.size) else null)
            else if (state.hasSuccessfulResponse) Text(stringResource(R.string.servers_count, servers.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.hcloudcontrol.ui.designsystem.HCloudFormField(query, onQueryChange, stringResource(R.string.ds_search))
            com.hcloudcontrol.ui.designsystem.HCloudSegmentedControl(
                items = listOf(
                    com.hcloudcontrol.ui.designsystem.ChoiceItem("ALL", stringResource(R.string.server_filter_all)),
                    com.hcloudcontrol.ui.designsystem.ChoiceItem("RUNNING", stringResource(R.string.server_filter_running)),
                    com.hcloudcontrol.ui.designsystem.ChoiceItem("OFF", stringResource(R.string.server_filter_off)),
                ),
                selectedId = filter.name,
                onSelected = { onFilterChange(ServerInventoryFilter.valueOf(it)) },
            )
        }
        item(key = "read-state") {
            HCloudSectionState(
                SectionPresentation(
                    kind = kind,
                    stale = state.isStale,
                    updatedAt = updatedAt,
                    refreshing = isRefreshing && state.hasSuccessfulResponse,
                    detail = state.error?.takeIf { state.hasSuccessfulResponse }?.let { stringResource(it.messageResource()) },
                ),
                onRetry = onRefresh,
            )
        }
        items(
            visibleServers,
            key = { it.id },
        ) { ServerCard(it) { onServerClick(it) } }
    }
}

@Composable private fun ServerCard(server: Server, onClick: () -> Unit) {
    com.hcloudcontrol.ui.designsystem.HCloudResourceCard(
        com.hcloudcontrol.ui.designsystem.ResourceCardModel(
            identity = "${LocalScrollProjectNamespace.current}:server:${server.id}",
            title = server.name,
            status = stringResource(serverStatusLabelResource(server.status)),
            summary = "${server.serverType} · ${server.location}\n${server.ipv4Address ?: stringResource(R.string.server_no_public_ipv4)}",
        ), onClick = onClick,
    )
}

@Composable
private fun ServerDetails(
    server: Server,
    readOnly: Boolean,
    repository: HCloudRepository,
    serverMetricsRepository: ServerMetricsRepository,
    onActionCompleted: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var action by remember { mutableStateOf<PowerAction?>(null) }
    var storageAction by rememberSaveable(LocalNavigationContext.current?.ownerId,
        LocalNavigationContext.current?.endpoint, server.id) { mutableStateOf<ServerStorageAction?>(null) }
    var recoveryOperation by remember { mutableStateOf<ServerRecoveryOperation?>(null) }
    val ptrDraftScope = "${LocalNavigationContext.current?.ownerId}:${LocalNavigationContext.current?.endpoint}:server:${server.id}:ptr"
    var showServerDnsPtr by rememberContextualFlag(ptrDraftScope)
    val serverFormDrafts = rememberSaveableStateHolder()
    var showResize by remember { mutableStateOf(false) }
    val detailContext = "${LocalScrollProjectNamespace.current}:server:${server.id}"
    var detailTab by rememberSaveable(detailContext) { mutableStateOf("overview") }
    val linkedScope = "${LocalNavigationContext.current?.ownerId}:${LocalNavigationContext.current?.endpoint}:server:${server.id}:linked-resource"
    var linkedResource by rememberServerResourceLink(linkedScope)
    val historyLoader = com.hcloudcontrol.ui.state.rememberSectionLoader(
        "$detailContext:history", isEmpty = { actions: List<CloudAction> -> actions.isEmpty() },
    ) { repository.getServerActions(server.id) }
    val metricsLoader = com.hcloudcontrol.ui.state.rememberSectionLoader("$detailContext:metrics") {
        val end = Instant.now()
        serverMetricsRepository.getServerMetrics(ServerMetricsQuery(
            serverId = server.id,
            types = listOf(ServerMetricType.CPU, ServerMetricType.DISK, ServerMetricType.NETWORK),
            start = end.minus(SERVER_METRICS_WINDOW), end = end,
        ))
    }
    val hosted = LocalNavigationChrome.current != null
    BackHandler(enabled = !hosted && showResize && linkedResource == null) { showResize = false }
    BackHandler(enabled = !hosted && !showResize) { if (linkedResource != null) linkedResource = null else onBack() }
    linkedResource?.let { link ->
        val returnToServer: () -> Unit = { linkedResource = null }
        when (link.type) {
            ServerLinkedResource.PRIMARY_IP -> PublicIpDetailScreen(repository, PublicIpKind.PRIMARY, link.id, linkedScope,
                onBack = returnToServer, onChanged = onActionCompleted, onDeleted = { linkedResource = null; onActionCompleted() })
            ServerLinkedResource.FLOATING_IP -> PublicIpDetailScreen(repository, PublicIpKind.FLOATING, link.id, linkedScope,
                onBack = returnToServer, onChanged = onActionCompleted, onDeleted = { linkedResource = null; onActionCompleted() })
            ServerLinkedResource.PRIVATE_NETWORK -> PrivateNetworkDetailScreen(link.id, repository,
                onBack = returnToServer, onDeleted = { linkedResource = null; onActionCompleted() })
            ServerLinkedResource.VOLUME -> VolumeDetailScreen(link.id, repository, onBack = returnToServer)
        }
        return
    }
    if (showResize) {
        LocalNavigationContext.current?.let { resourceContext ->
            RegisterChildNavigation(CanonicalDestination(RootDestination.SERVERS, DestinationKind.FORM, resourceContext,
                ResourceType.SERVER, server.id.toString()), stringResource(R.string.server_change_type)) { showResize = false }
        }
        ChangeServerTypeScreen(server, repository, onCancel = { showResize = false }, onChanged = { showResize = false; onActionCompleted() })
        return
    }
    Column(Modifier.fillMaxSize()) {
    com.hcloudcontrol.ui.designsystem.HCloudTabs(listOf(
        com.hcloudcontrol.ui.designsystem.ChoiceItem("overview", stringResource(R.string.server_overview)),
        com.hcloudcontrol.ui.designsystem.ChoiceItem("metrics", stringResource(R.string.server_metrics_title)),
        com.hcloudcontrol.ui.designsystem.ChoiceItem("actions", stringResource(R.string.server_detail_actions)),
    ), detailTab, { detailTab = it })
    LazyColumn(Modifier.fillMaxSize(), state = rememberRestorableLazyListState("$detailContext:$detailTab"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (detailTab == "overview") {
        item { HCloudScreenHeader(stringResource(R.string.server_overview), stringResource(serverStatusLabelResource(server.status))) }
        item { DetailsCard(server) }
            item { ServerNetworkDeviceSection(server,
                onOpenResource = { linkedResource = it }, onManagePtr = { showServerDnsPtr = true },
                canManagePtr = highImpactAllowed && !readOnly && server.publicDnsPointers.isNotEmpty(), showPtrAction = highImpactAllowed) }
        }
        if (detailTab == "metrics") {
        item {
            Text(stringResource(R.string.server_metrics_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.server_metrics_window), style = MaterialTheme.typography.bodySmall)
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(metricsLoader) { ServerMetricsSummary(it) }
        }
        }
        if (detailTab == "actions") {
        item {
            Text(stringResource(R.string.server_action_history_title), style = MaterialTheme.typography.titleLarge)
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(historyLoader) { ServerActionHistory(it) }
        }
        if (highImpactAllowed) {
            item { ServerManagementSection(server = server, repository = repository, readOnly = readOnly, onCompleted = onActionCompleted) }
            item { ServerPlacementGroupSection(server, repository, readOnly, onActionCompleted) }
            item { ServerDestructiveOperationsSection(server = server, repository = repository, readOnly = readOnly, onDeleted = onDeleted, onCompleted = onActionCompleted) }
            item { ServerIsoSection(server = server, repository = repository, readOnly = readOnly, onActionCompleted = onActionCompleted) }
            item { OutlinedButton(onClick = { showResize = true }, enabled = !readOnly && server.status == ServerStatus.OFF, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_change_type)) } }
            item { if (server.status != ServerStatus.OFF) Text(stringResource(R.string.server_power_off_before_resize), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text(stringResource(R.string.server_backups_snapshots), style = MaterialTheme.typography.titleLarge) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (server.backupWindow == null) stringResource(R.string.server_backup_disabled_legacy) else stringResource(R.string.server_backup_enabled_legacy, server.backupWindow.orEmpty()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (server.backupWindow == null) {
                        OutlinedButton(onClick = { storageAction = ServerStorageAction.EnableBackups }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_enable_backups)) }
                    } else {
                        OutlinedButton(onClick = { storageAction = ServerStorageAction.DisableBackups }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_disable_backups)) }
                    }
                    OutlinedButton(onClick = { storageAction = ServerStorageAction.CreateSnapshot }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_create_snapshot)) }
                }
            }
            item { Text(stringResource(R.string.power_title), style = MaterialTheme.typography.titleLarge) }; item { PowerActions(server.status, readOnly) { action = it } }
            if (readOnly) item { Text(stringResource(R.string.server_read_only_power), color = MaterialTheme.colorScheme.error) }
            item { Text(stringResource(R.string.server_recovery_title), style = MaterialTheme.typography.titleLarge) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { recoveryOperation = ServerRecoveryOperation.RESET_ROOT_PASSWORD }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_recovery_reset_password)) }
                    OutlinedButton(onClick = { recoveryOperation = ServerRecoveryOperation.ENABLE_RESCUE }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_recovery_enable_rescue)) }
                    OutlinedButton(onClick = { recoveryOperation = ServerRecoveryOperation.DISABLE_RESCUE }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_recovery_disable_rescue)) }
                }
            }
            item { Text(stringResource(R.string.server_dns_ptr_section_title), style = MaterialTheme.typography.titleLarge) }
            item {
                OutlinedButton(onClick = { showServerDnsPtr = true }, enabled = !readOnly && server.publicDnsPointers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.server_dns_ptr_manage))
                }
            }
            if (server.publicDnsPointers.isEmpty()) item { Text(stringResource(R.string.server_dns_ptr_no_public_addresses), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        }
    }
    }
    action?.let { PowerConfirmation(action = it, server = server, repository = repository, canExecute = !readOnly, onCompleted = onActionCompleted, onDismiss = { action = null }) }
    storageAction?.let { currentAction ->
        serverFormDrafts.SaveableStateProvider("${LocalNavigationContext.current}:server:${server.id}:storage:$currentAction") {
            StorageActionConfirmation(action = currentAction, server = server, repository = repository, canExecute = !readOnly,
                onCompleted = onActionCompleted, onDismiss = { storageAction = null })
        }
    }
    recoveryOperation?.let { recovery -> ServerRecoveryDialog(server = server, operation = recovery, repository = repository, onDismiss = { recoveryOperation = null }, onActionCompleted = onActionCompleted) }
    if (showServerDnsPtr) serverFormDrafts.SaveableStateProvider(ptrDraftScope) {
        ServerDnsPtrDialog(server = server, repository = repository, onDismiss = { showServerDnsPtr = false }, onActionCompleted = onActionCompleted)
    }
}

/** A deliberately bounded, read-only window prevents accidental monitoring backfills. */
private val SERVER_METRICS_WINDOW: Duration = Duration.ofHours(6)


@Composable
private fun ServerMetricsSummary(metrics: ServerMetrics) {
    if (metrics.timeSeries.isEmpty()) {
        Text(stringResource(R.string.server_metrics_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.timeSeries.toSortedMap().forEach { (type, series) ->
            com.hcloudcontrol.ui.designsystem.HCloudMetricSeriesCard(type.uppercase(),
                series.samples.map { com.hcloudcontrol.ui.designsystem.MetricSeriesPoint(it.timestampSeconds, it.value) }, metrics.stepSeconds)
        }
        Text(stringResource(R.string.server_metrics_step, metrics.stepSeconds), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ServerActionHistory(actions: List<CloudAction>) {
    if (actions.isEmpty()) {
        Text(stringResource(R.string.server_action_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.sortedByDescending { it.id }.forEach { action ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(action.command ?: stringResource(R.string.server_action_unknown), style = MaterialTheme.typography.titleSmall)
                    DetailRow(stringResource(R.string.server_action_status), action.status)
                    action.progress?.let { DetailRow(stringResource(R.string.server_action_progress), "$it%") }
                    action.started?.let { DetailRow(stringResource(R.string.server_action_started), it) }
                    action.finished?.let { DetailRow(stringResource(R.string.server_action_finished), it) }
                    // Do not surface server-provided error messages here: this history must stay safe to share.
                    action.errorCode?.let { DetailRow(stringResource(R.string.server_action_error_code), it) }
                }
            }
        }
    }
}

@Composable private fun DetailsCard(server: Server) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailRow(stringResource(R.string.detail_type), server.serverType)
        val humanLocation = server.locationDetails?.let { listOfNotNull(it.name, it.city?.takeIf(String::isNotBlank), it.country?.takeIf(String::isNotBlank)).joinToString(" · ") } ?: server.location
        DetailRow(stringResource(R.string.detail_location), humanLocation)
        server.hardware?.let { hardware ->
            hardware.cores?.let { DetailRow(stringResource(R.string.server_detail_cpu), it.toString()) }
            hardware.memoryGb?.let { DetailRow(stringResource(R.string.server_detail_memory), java.text.NumberFormat.getNumberInstance().format(it)) }
            hardware.architecture?.let { DetailRow(stringResource(R.string.server_detail_architecture), it) }
            hardware.cpuType?.let { DetailRow(stringResource(R.string.server_detail_cpu_type), it) }
            hardware.storageType?.let { DetailRow(stringResource(R.string.server_detail_storage_type), it) }
        }
        server.primaryDiskSizeGb?.let { DetailRow(stringResource(R.string.server_detail_actual_disk), java.text.NumberFormat.getNumberInstance().format(it)) }
        server.image?.let { DetailRow(stringResource(R.string.server_detail_image), it.description ?: it.name ?: "#${it.id}") }
        val unknownProtection = stringResource(R.string.server_protection_unavailable)
        DetailRow(stringResource(R.string.server_protection_delete), server.protection?.delete?.let {
            stringResource(if (it) R.string.common_protected_enabled else R.string.common_protected_disabled)
        } ?: unknownProtection)
        DetailRow(stringResource(R.string.server_protection_rebuild), server.protection?.rebuild?.let {
            stringResource(if (it) R.string.common_protected_enabled else R.string.common_protected_disabled)
        } ?: unknownProtection)
        DetailRow(stringResource(R.string.detail_ipv4), server.ipv4Address ?: stringResource(R.string.server_not_assigned))
        DetailRow(stringResource(R.string.detail_server_id), server.id.toString())
    }
}
@Composable private fun DetailRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(label, modifier = Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, modifier = Modifier.weight(0.58f), fontWeight = FontWeight.Medium)
}
private enum class PowerRisk { Standard, Destructive }
private enum class PowerAction(val risk: PowerRisk) {
    Start(PowerRisk.Standard),
    Shutdown(PowerRisk.Standard),
    PowerOff(PowerRisk.Destructive),
    Reboot(PowerRisk.Standard),
    Reset(PowerRisk.Destructive),
}

@Composable
private fun PowerAction.title(): String = stringResource(when (this) {
    PowerAction.Start -> R.string.power_start
    PowerAction.Shutdown -> R.string.power_shutdown
    PowerAction.PowerOff -> R.string.power_power_off
    PowerAction.Reboot -> R.string.power_reboot
    PowerAction.Reset -> R.string.power_reset
})

@Composable
private fun PowerAction.explanation(): String = stringResource(when (this) {
    PowerAction.Start -> R.string.power_start_explanation
    PowerAction.Shutdown -> R.string.power_shutdown_explanation
    PowerAction.PowerOff -> R.string.power_power_off_explanation
    PowerAction.Reboot -> R.string.power_reboot_explanation
    PowerAction.Reset -> R.string.power_reset_explanation
})

@Composable
private fun PowerActions(status: ServerStatus, readOnly: Boolean, onAction: (PowerAction) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (status == ServerStatus.OFF) {
        PowerButton(PowerAction.Start, !readOnly, onAction)
    } else {
        PowerButton(PowerAction.Shutdown, !readOnly, onAction)
        PowerButton(PowerAction.Reboot, !readOnly, onAction)
        PowerButton(PowerAction.PowerOff, !readOnly, onAction)
        PowerButton(PowerAction.Reset, !readOnly, onAction)
    }
}

@Composable
private fun PowerButton(action: PowerAction, enabled: Boolean, onAction: (PowerAction) -> Unit) = OutlinedButton(
    onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
) { Text(action.title()) }

@Composable
private fun PowerConfirmation(action: PowerAction, server: Server, repository: HCloudRepository, canExecute: Boolean, onCompleted: () -> Unit, onDismiss: () -> Unit) {
    val routine = when (action) {
        PowerAction.Start -> com.hcloudcontrol.operations.ServerRoutineAction.POWER_ON
        PowerAction.Shutdown -> com.hcloudcontrol.operations.ServerRoutineAction.SHUTDOWN
        PowerAction.PowerOff -> com.hcloudcontrol.operations.ServerRoutineAction.HARD_OFF
        PowerAction.Reboot -> com.hcloudcontrol.operations.ServerRoutineAction.REBOOT
        PowerAction.Reset -> com.hcloudcontrol.operations.ServerRoutineAction.HARD_RESET
    }
    ServerRoutineFlow(server, repository, routine, action.title(), action.explanation(), canExecute, onCompleted, onDismiss)
}

private enum class ServerStorageAction(val titleRes: Int, val warningRes: Int) {
    EnableBackups(R.string.server_enable_backups, R.string.storage_enable_backups_warning),
    DisableBackups(R.string.server_disable_backups, R.string.storage_disable_backups_warning),
    CreateSnapshot(R.string.server_create_snapshot, R.string.storage_create_snapshot_warning),
}

@Composable
private fun StorageActionConfirmation(action: ServerStorageAction, server: Server, repository: HCloudRepository, canExecute: Boolean, onCompleted: () -> Unit, onDismiss: () -> Unit) {
    val routine = when (action) {
        ServerStorageAction.EnableBackups -> com.hcloudcontrol.operations.ServerRoutineAction.BACKUPS_ENABLE
        ServerStorageAction.DisableBackups -> com.hcloudcontrol.operations.ServerRoutineAction.BACKUPS_DISABLE
        ServerStorageAction.CreateSnapshot -> com.hcloudcontrol.operations.ServerRoutineAction.SNAPSHOT
    }
    ServerRoutineFlow(server, repository, routine, stringResource(action.titleRes), stringResource(action.warningRes), canExecute, onCompleted, onDismiss)
}

internal suspend fun awaitCloudAction(
    repository: HCloudRepository,
    initial: com.hcloudcontrol.core.domain.model.CloudAction,
    onProgress: (String) -> Unit,
) = awaitCloudAction(initial, repository::getAction, onProgress)

/**
 * Waits only for the action submitted from the current detail workflow. The caller owns the
 * screen state, so a terminal success returns to that same workflow rather than navigating away.
 */
internal suspend fun awaitCloudAction(
    initial: com.hcloudcontrol.core.domain.model.CloudAction,
    getAction: suspend (Long) -> com.hcloudcontrol.core.domain.model.CloudAction,
    onProgress: (String) -> Unit,
    delayMillis: Long = 1_000,
    maxAttempts: Int = 600,
) {
    var action = initial
    repeat(maxAttempts) {
        onProgress(action.status)
        when (action.status.lowercase()) {
            "success" -> return
            "error" -> throw IllegalStateException("Hetzner power action failed")
        }
        delay(delayMillis)
        action = getAction(action.id)
    }
    throw IllegalStateException("Hetzner power action timed out")
}

private fun powerActionStatusText(status: String): String = when (status.lowercase()) {
    "running" -> "выполняется"
    "pending" -> "ожидает выполнения"
    "success" -> "завершена"
    "error" -> "завершилась с ошибкой"
    else -> "обрабатывается"
}

@Composable
private fun StorageBoxesScreen(project: CloudProjectConnection, legacyCredentialStore: LegacyStorageBoxCredentialStore, repository: StorageBoxRepository) =
    StorageBoxesInventory(project, legacyCredentialStore, repository)

private fun formatStorageBoxBytes(context: android.content.Context, value: Number): String =
    Formatter.formatFileSize(context, value.toLong().coerceAtLeast(0L))

@Composable
internal fun StorageBoxDetailScreen(boxId: Long, repository: StorageBoxRepository, onBack: () -> Unit) {
    val contextId = LocalScrollProjectNamespace.current + ":" +
        com.hcloudcontrol.operations.LocalOperationContext.current?.endpoint + ":storage-box:" + boxId
    val detail = com.hcloudcontrol.ui.state.rememberSectionLoader(contextId + ":metadata") {
        repository.getStorageBox(boxId)
    }
    val folders = com.hcloudcontrol.ui.state.rememberSectionLoader(contextId + ":folders", { it: List<String> -> it.isEmpty() }) {
        repository.getStorageBoxFolders(boxId)
    }
    val snapshots = com.hcloudcontrol.ui.state.rememberSectionLoader(contextId + ":snapshots", { it: List<StorageBoxSnapshot> -> it.isEmpty() }) {
        repository.getStorageBoxSnapshots(boxId)
    }
    val subaccounts = com.hcloudcontrol.ui.state.rememberSectionLoader(contextId + ":subaccounts", { it: List<StorageBoxSubaccount> -> it.isEmpty() }) {
        repository.getStorageBoxSubaccounts(boxId)
    }
    var showCreateSnapshot by rememberContextualFlag("$contextId:create-snapshot")
    val snapshotDrafts = rememberSaveableStateHolder()
    var selectedBoxTab by rememberContextualText("$contextId:tab", "overview")
    val hosted = LocalNavigationChrome.current != null
    LocalNavigationContext.current?.let { cloudContext ->
        RegisterChildNavigation(CanonicalDestination(RootDestination.MORE, DestinationKind.DETAIL,
            cloudContext.copy(endpoint = StorageBoxRepositoryFactory.API_BASE_URL), ResourceType.STORAGE_BOX, boxId.toString()),
            detail.state.data?.name ?: stringResource(R.string.storage_boxes_unnamed), onBack)
    }
    HCloudPullToRefresh(detail.state.refreshing || folders.state.refreshing || snapshots.state.refreshing || subaccounts.state.refreshing,
        onRefresh = { detail.refresh(); folders.refresh(); snapshots.refresh(); subaccounts.refresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = rememberRestorableLazyListState("$contextId:$selectedBoxTab", contentReady = when (selectedBoxTab) {
            "snapshots" -> snapshots.state.hasSuccessfulResponse
            "access" -> detail.state.hasSuccessfulResponse &&
                (folders.state.hasSuccessfulResponse || folders.state.error != null) &&
                (subaccounts.state.hasSuccessfulResponse || subaccounts.state.error != null)
            else -> detail.state.hasSuccessfulResponse
        }),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hosted) item { TextButton(onClick = onBack) { Text(stringResource(R.string.storage_boxes_back)) } }
        item(key = "tabs") { com.hcloudcontrol.ui.designsystem.HCloudTabs(listOf(
            com.hcloudcontrol.ui.designsystem.ChoiceItem("overview", stringResource(R.string.box_redesign_overview)),
            com.hcloudcontrol.ui.designsystem.ChoiceItem("snapshots", stringResource(R.string.storage_boxes_snapshot_title)),
            com.hcloudcontrol.ui.designsystem.ChoiceItem("access", stringResource(R.string.box_redesign_access)),
        ), selectedBoxTab, { selectedBoxTab = it }) }
        if (selectedBoxTab == "overview") item {
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(detail) { current ->
                if (!hosted) Text(current.name ?: stringResource(R.string.storage_boxes_unnamed), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.common_id, current.id))
                Text(current.status ?: stringResource(R.string.storage_boxes_status_unknown))
                current.stats?.let { stats ->
                    Text(stringResource(R.string.storage_boxes_total_formatted, formatStorageBoxBytes(LocalContext.current, stats.size)))
                    Text(stringResource(R.string.storage_boxes_data_formatted, formatStorageBoxBytes(LocalContext.current, stats.sizeData)))
                    Text(stringResource(R.string.storage_boxes_snapshots_formatted, formatStorageBoxBytes(LocalContext.current, stats.sizeSnapshots)))
                } ?: Text(stringResource(R.string.storage_boxes_stats_unavailable))
            }
            com.hcloudcontrol.ui.designsystem.HCloudResourceCard(com.hcloudcontrol.ui.designsystem.ResourceCardModel(
                "$contextId:folders", stringResource(R.string.storage_boxes_folders), "", ""), { selectedBoxTab = "access" })
            com.hcloudcontrol.ui.designsystem.HCloudResourceCard(com.hcloudcontrol.ui.designsystem.ResourceCardModel(
                "$contextId:subaccounts", stringResource(R.string.storage_boxes_subaccounts_title), "", ""), { selectedBoxTab = "access" })
            com.hcloudcontrol.ui.designsystem.HCloudResourceCard(com.hcloudcontrol.ui.designsystem.ResourceCardModel(
                "$contextId:snapshots", stringResource(R.string.storage_boxes_snapshot_title), "", ""), { selectedBoxTab = "snapshots" })
            Text(stringResource(R.string.box_redesign_read_only_access))
        }
        if (selectedBoxTab == "access") item {
            Text(stringResource(R.string.storage_boxes_folders), style = MaterialTheme.typography.titleMedium)
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(folders) { values ->
                values.sorted().forEach { Text(it, Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
            }
        }
        if (selectedBoxTab == "access") item {
            Text(stringResource(R.string.storage_boxes_subaccounts_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.storage_boxes_subaccounts_description))
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(subaccounts) { values ->
                values.sortedBy { it.name.orEmpty().lowercase() }.forEach { account ->
                    Text(account.name ?: account.username ?: stringResource(R.string.storage_boxes_subaccount_unnamed))
                    account.username?.let { Text(stringResource(R.string.storage_boxes_subaccount_username, it)) }
                }
            }
        }
        if (selectedBoxTab == "snapshots") item {
            Text(stringResource(R.string.storage_boxes_snapshot_title), style = MaterialTheme.typography.titleMedium)
            if (LocalHighImpactOperations.current.isAllowed && detail.state.canOfferMutation) {
                OutlinedButton(onClick = { showCreateSnapshot = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.storage_boxes_snapshot_create))
                }
            }
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(snapshots) { values ->
                values.sortedByDescending { it.created.orEmpty() }.forEach { snapshot ->
                    Text(snapshot.name ?: stringResource(R.string.storage_boxes_snapshot_unnamed, snapshot.id),
                        style = MaterialTheme.typography.titleSmall)
                    snapshot.description?.let { Text(it) }
                    snapshot.created?.let { Text(stringResource(R.string.storage_boxes_snapshot_created, it)) }
                }
            }
        }
    }
    }
    if (showCreateSnapshot) snapshotDrafts.SaveableStateProvider("$contextId:create-snapshot") { StorageBoxSnapshotCreateDialog(
        storageBoxId = boxId, repository = repository,
        onDismiss = { showCreateSnapshot = false },
        onCreated = { snapshots.refresh() },
    ) }
}

@Composable
private fun StorageBoxSnapshotCreateDialog(
    storageBoxId: Long,
    repository: StorageBoxRepository,
    onDismiss: () -> Unit,
    onCreated: (StorageBoxSnapshot) -> Unit,
) {
    val cloudContext = com.hcloudcontrol.operations.LocalOperationContext.current
    val navigationContext = LocalNavigationContext.current
    CompositionLocalProvider(com.hcloudcontrol.operations.LocalOperationContext provides
        cloudContext?.copy(endpoint = StorageBoxRepositoryFactory.API_BASE_URL),
        LocalNavigationContext provides navigationContext?.copy(endpoint = StorageBoxRepositoryFactory.API_BASE_URL)) {
        StorageBoxSnapshotOperation(storageBoxId, repository, onDismiss, onCreated)
    }
}

@Composable
private fun StorageBoxSnapshotOperation(
    storageBoxId: Long,
    repository: StorageBoxRepository,
    onDismiss: () -> Unit,
    onCreated: (StorageBoxSnapshot) -> Unit,
) {
    val operationScope = com.hcloudcontrol.operations.LocalOperationContext.current
    val draftScope = "${operationScope?.connectionId}:${operationScope?.endpoint}:storage-box:$storageBoxId:snapshot"
    var description by rememberContextualText(draftScope)
    var reviewing by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0L) }
    var readFailed by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<com.hcloudcontrol.operations.OperationReviewContent?>(null) }
    var reviewedBox by remember { mutableStateOf<StorageBox?>(null) }
    val session = com.hcloudcontrol.operations.rememberOperationSession("storage_box", storageBoxId.toString(), "create_snapshot", revision)
    val coordinator = com.hcloudcontrol.operations.LocalOperationCoordinator.current
    val currentContext by rememberUpdatedState(com.hcloudcontrol.operations.LocalOperationContext.current)
    val changesAllowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let {
        com.hcloudcontrol.operations.BoxSnapshotAdapter(repository, it, { currentContext }, { changesAllowed })
    } }
    var record by remember(session) { mutableStateOf(session.record) }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.storage_boxes_snapshot_create)
    val note = stringResource(R.string.storage_boxes_snapshot_create_note)
    val fallback = stringResource(R.string.storage_boxes_snapshot_create_error)
    suspend fun refreshSnapshot(id: Long) {
        check(currentContext == session.context)
        val snapshot = withContext(Dispatchers.IO) { repository.getStorageBoxSnapshot(storageBoxId, id) }
        onCreated(snapshot)
        if (session.record?.stage == com.hcloudcontrol.operations.OperationStage.SUCCEEDED) session.refreshSucceeded()
        record = session.record
    }
    val exitGuard = rememberDirtyFormExitGuard(description.isNotBlank(), onRetain = {},
        onDiscard = { description = "" }, onLeave = onDismiss, submitted = record != null, contextKey = draftScope)
    RegisterResourceFormNavigation(RootDestination.MORE, ResourceType.STORAGE_BOX, storageBoxId.toString(), title) {
        when {
            record != null -> if (record!!.canClose) { onDismiss(); session.startNewAttemptAfterTerminalClose() }
            review != null -> if (!reviewing) review = null
            else -> exitGuard.requestLeave()
        }
    }
    com.hcloudcontrol.ui.designsystem.DirtyFormExitPrompt(exitGuard)
    if (record != null) {
        com.hcloudcontrol.operations.OperationResultDialog(
                title, record!!, session.context?.connectionId.orEmpty(), storageBoxId.toString(), onDismiss,
                session = session, busy = reviewing,
                onCheckStatus = {
                    val original = record ?: return@OperationResultDialog
                    reviewing = true
                    readFailed = false
                    scope.launch {
                        try {
                            record = withContext(Dispatchers.IO) { checkNotNull(adapter).reconcile(original) }
                            record?.createdResourceId?.toLongOrNull()?.let { id ->
                                refreshSnapshot(id)
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                          catch (_: Exception) {
                            if (session.record?.stage == com.hcloudcontrol.operations.OperationStage.SUCCEEDED) session.refreshFailed()
                            record = session.record
                            readFailed = true
                        } finally { reviewing = false }
                    }
                },
                onRefreshResource = {
                    val snapshotId = record?.createdResourceId?.toLongOrNull()
                    if (snapshotId != null && !reviewing) {
                        reviewing = true; readFailed = false
                        scope.launch {
                            try { refreshSnapshot(snapshotId) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { readFailed = true }
                            finally { reviewing = false }
                        }
                    }
                },
            ) {
            if (record!!.stage == com.hcloudcontrol.operations.OperationStage.ACCEPTED) {
                Text(stringResource(R.string.storage_boxes_snapshot_request_accepted))
            }
            if (readFailed) Text(fallback, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val currentReview = review
    if (currentReview != null) {
        com.hcloudcontrol.operations.OperationReviewDialog(
            currentReview,
            onConfirm = { confirmed ->
                val frozenDescription = description.ifBlank { null }
                reviewing = true
                scope.launch {
                    try {
                        record = withContext(Dispatchers.IO) {
                            checkNotNull(adapter).submit(confirmed, checkNotNull(reviewedBox), CreateStorageBoxSnapshotSpec(frozenDescription)) {
                                withContext(Dispatchers.Main) { record = session.record }
                            }
                        }
                        record?.createdResourceId?.toLongOrNull()?.let { id ->
                            try {
                                refreshSnapshot(id)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                              catch (_: Exception) {
                                if (session.record?.stage == com.hcloudcontrol.operations.OperationStage.SUCCEEDED) session.refreshFailed()
                                record = session.record
                                readFailed = true
                            }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        session.unknownIfPending()
                        throw cancelled
                    } catch (failure: Exception) {
                        session.rejectOrUnknown(failure)
                        record = session.record
                        readFailed = true
                        review = null
                    } finally { reviewing = false }
                }
            },
            onDismiss = { if (!reviewing) review = null },
            submitEnabled = !reviewing && changesAllowed,
        )
        return
    }
    com.hcloudcontrol.ui.designsystem.HCloudDialog(
        title = title, confirmLabel = stringResource(R.string.resize_review),
        onConfirm = {
            if (!reviewing) {
                reviewing = true
                scope.launch {
                    try {
                        val fresh = repository.getStorageBox(storageBoxId)
                        reviewedBox = fresh
                        review = com.hcloudcontrol.operations.OperationReviewContent(
                            intent = checkNotNull(session.intent), reviewedAtMillis = System.currentTimeMillis(),
                            title = title, projectLabel = session.context?.connectionId.orEmpty(),
                            resourceLabel = fresh.name ?: fresh.id.toString(),
                            effects = listOf(com.hcloudcontrol.operations.ReviewChange(title, null, description)),
                            acknowledgements = listOf(com.hcloudcontrol.operations.RiskAcknowledgement("snapshot", note)),
                        )
                        readFailed = false
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                      catch (_: Exception) { readFailed = true }
                    finally { reviewing = false }
                }
            }
        },
        onDismiss = exitGuard::requestLeave, confirmEnabled = !reviewing && changesAllowed && session.configured &&
            com.hcloudcontrol.operations.validBoxSnapshotDescription(description),
    ) {
        Text(note)
        com.hcloudcontrol.ui.designsystem.HCloudFormField(
            description, { description = it; revision++ },
            stringResource(R.string.storage_boxes_snapshot_description), enabled = !reviewing,
            error = stringResource(R.string.box_snapshot_description_invalid).takeUnless {
                com.hcloudcontrol.operations.validBoxSnapshotDescription(description)
            },
        )
        if (reviewing) CircularProgressIndicator()
        if (readFailed) Text(fallback, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TariffsEstimateScreen(repository: BillingRateCardRepository, projectId: String, cloudRepository: HCloudRepository) =
    TariffsScreen(repository, projectId, cloudRepository)

@Composable
private fun StorageInventoryScreen(repository: HCloudRepository, projectId: String, operationsAllowed: Boolean) =
    StorageInventoryRedesign(repository, projectId, operationsAllowed)

internal enum class VolumeAction { Attach, Detach, Resize }
internal data class VolumeActionRequest(val volume: VolumeInventory, val action: VolumeAction)


@Composable
internal fun VolumeActionConfirmation(request: VolumeActionRequest, servers: List<Server>, repository: HCloudRepository, onCompleted: () -> Unit, onDismiss: () -> Unit) =
    VolumeMutationFlow(request.volume, when (request.action) {
        VolumeAction.Attach -> com.hcloudcontrol.operations.VolumeMutation.ATTACH
        VolumeAction.Detach -> com.hcloudcontrol.operations.VolumeMutation.DETACH
        VolumeAction.Resize -> com.hcloudcontrol.operations.VolumeMutation.RESIZE
    }, servers, repository, onCompleted, onDismiss)
@Composable
internal fun CreateVolumeDialog(repository: HCloudRepository, onCompleted: () -> Unit, onDismiss: () -> Unit, onCreated: ((Long) -> Unit)? = null) =
    VolumeCreationFlow(repository, onCompleted, onDismiss, onCreated)

@Composable
internal fun DeleteVolumeDialog(volume: VolumeInventory, repository: HCloudRepository, onCompleted: () -> Unit, onDismiss: () -> Unit) =
    VolumeMutationFlow(volume, com.hcloudcontrol.operations.VolumeMutation.DELETE, emptyList(), repository, onCompleted, onDismiss)


@Composable fun LoadingView(message: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(message) } }
@Composable fun StateView(title: String, message: String, action: String, onAction: () -> Unit) = Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = onAction) { Text(action) } }
@Composable private fun ProductScope(title: String, description: String) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Preview(showBackground = true) @Composable private fun ServerCardPreview() { MaterialTheme(colorScheme = HCloudColors) { ServerCard(Server(1, "web-01", ServerStatus.RUNNING, "203.0.113.42", "fsn1", "cpx31"), {}) } }
