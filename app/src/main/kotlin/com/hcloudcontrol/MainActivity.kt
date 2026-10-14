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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.hcloudcontrol.core.domain.usecase.PrimaryIpOperationsUseCase
import com.hcloudcontrol.core.domain.usecase.FloatingIpOperationsUseCase
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
        val repository = HCloudRepositoryFactory.create(tokenStore::token) {
            tokenStore.requireActiveConnectionConfig()
        }
        // Storage Boxes are project resources; their separate API host still uses the active
        // project's Bearer token. Legacy separate credentials are never reused.
        val storageBoxRepository = StorageBoxRepositoryFactory.create(tokenStore::token)
        val objectStorageRepository = ObjectStorageRepositoryFactory.create(objectStorageCredentialStore::activeCredentials)
        val billingRepository = BillingRateCardRepositoryFactory.create(tokenStore::token) {
            tokenStore.requireActiveConnectionConfig()
        }
        val serverMetricsRepository = ServerMetricsRepositoryFactory.create(tokenStore::token) {
            tokenStore.requireActiveConnectionConfig()
        }
        val loadBalancerMetricsRepository = LoadBalancerMetricsRepositoryFactory.create(tokenStore::token) {
            tokenStore.requireActiveConnectionConfig()
        }
        val dnsRepository = DnsRepositoryFactory.create(
            scope = DnsScope.acknowledgeReadOnly(),
            hCloudTokenProvider = tokenStore::token,
            hCloudConfigProvider = { tokenStore.requireActiveConnectionConfig() },
        )
        val appearanceStore = AppearanceStore(applicationContext)
        val highImpactOperationsStore = PersistentHighImpactOperationsStore(applicationContext)
        setContent {
            var appearanceMode by remember { mutableStateOf(appearanceStore.read()) }
            var highImpactOperations by remember { mutableStateOf(highImpactOperationsStore.capability()) }
            HCloudControlApp(
                tokenStore = tokenStore,
                repository = repository,
                legacyStorageBoxCredentialStore = legacyStorageBoxCredentialStore,
                storageBoxRepository = storageBoxRepository,
                objectStorageCredentialStore = objectStorageCredentialStore,
                objectStorageRepository = objectStorageRepository,
                billingRepository = billingRepository,
                serverMetricsRepository = serverMetricsRepository,
                loadBalancerMetricsRepository = loadBalancerMetricsRepository,
                dnsRepository = dnsRepository,
                appearanceMode = appearanceMode,
                onAppearanceModeChange = { mode -> appearanceStore.save(mode); appearanceMode = mode },
                highImpactOperations = highImpactOperations,
                onHighImpactOperationsChange = { allowed ->
                    highImpactOperationsStore.setAllowed(allowed)
                    highImpactOperations = highImpactOperationsStore.capability()
                },
            )
        }
    }
}

/** A request without a selected credential must not be routed to any Cloud host. */
internal fun HCloudTokenStore.requireActiveConnectionConfig(): HCloudConnectionConfig =
    requireNotNull(activeConnection()?.config) { "No active Cloud project connection" }

/*
 * A stable palette is intentional. Dynamic colours made the system setting inherit arbitrary
 * wallpaper hues (including low-contrast beige combinations) and obscured resource state.
 * "System" still follows the device's light/dark setting; it simply keeps the control plane's
 * semantic colours predictable.
 */
private val HCloudColors = darkColorScheme(
    primary = Color(0xFF9DC5FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004A8A),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFFB6C8E5),
    onSecondary = Color(0xFF20324A),
    secondaryContainer = Color(0xFF364A64),
    onSecondaryContainer = Color(0xFFD2E4FF),
    background = Color(0xFF10151D),
    onBackground = Color(0xFFE0E7F2),
    surface = Color(0xFF161D27),
    onSurface = Color(0xFFE0E7F2),
    surfaceVariant = Color(0xFF222D3B),
    onSurfaceVariant = Color(0xFFC0CAD9),
    outline = Color(0xFF8A98AA),
    outlineVariant = Color(0xFF3C4858),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val HCloudLightColors = lightColorScheme(
    primary = Color(0xFF0059B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF4C607E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8FF),
    onSecondaryContainer = Color(0xFF071D36),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF18202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18202B),
    surfaceVariant = Color(0xFFEAF1FA),
    onSurfaceVariant = Color(0xFF435466),
    outline = Color(0xFF738396),
    outlineVariant = Color(0xFFC3CFDD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val HCloudTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val HCloudShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private enum class AppearanceMode(val labelRes: Int) {
    SYSTEM(R.string.appearance_system),
    LIGHT(R.string.appearance_light),
    DARK(R.string.appearance_dark),
}

private enum class LanguageMode(val labelRes: Int, val tags: String?) {
    SYSTEM(R.string.language_system, null),
    ENGLISH(R.string.language_english, "en"),
    RUSSIAN(R.string.language_russian, "ru"),
    GERMAN(R.string.language_german, "de"),
}

/** A one-shot, non-secret acknowledgement that survives the onboarding-to-home transition. */
private enum class ConnectionNotice { TestServerConnected, CloudProjectConnected }

private fun applyLanguage(mode: LanguageMode) {
    AppCompatDelegate.setApplicationLocales(
        mode.tags?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
    )
}

/** AppCompat persists this setting (including after process death); this only maps it back to the UI. */
private fun currentLanguageMode(): LanguageMode = when (
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
    repository: HCloudRepository,
    legacyStorageBoxCredentialStore: LegacyStorageBoxCredentialStore,
    storageBoxRepository: StorageBoxRepository,
    objectStorageCredentialStore: ObjectStorageCredentialStore,
    objectStorageRepository: ObjectStorageRepository,
    billingRepository: BillingRateCardRepository,
    serverMetricsRepository: ServerMetricsRepository,
    loadBalancerMetricsRepository: LoadBalancerMetricsRepository,
    dnsRepository: DnsRepository,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    highImpactOperations: HighImpactOperationsCapability,
    onHighImpactOperationsChange: (Boolean) -> Unit,
) {
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
        val scrollPositionRegistry = rememberScrollPositionRegistry()
        CompositionLocalProvider(
            LocalHighImpactOperations provides highImpactOperations,
            LocalScrollPositionRegistry provides scrollPositionRegistry,
        ) {
            Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            var connections by remember { mutableStateOf(tokenStore.connections()) }
            var addingProject by remember { mutableStateOf(false) }
            var connectionNotice by remember { mutableStateOf<ConnectionNotice?>(null) }
            // A secondary-project form is a child of the home screen, not a new task root.
            // Consume the system gesture before Activity finishes.
            BackHandler(enabled = addingProject) { addingProject = false }
            if (connections.isEmpty() || addingProject) {
                Onboarding(
                    isAdditionalProject = connections.isNotEmpty(),
                    onConnect = { projectName, token, config ->
                        runCatching {
                            tokenStore.saveConnection(projectName, token, config)
                            connections = tokenStore.connections()
                            addingProject = false
                        }.onSuccess {
                            connectionNotice = if (config.kind == ConnectionKind.TEST_MOCK) {
                                ConnectionNotice.TestServerConnected
                            } else {
                                ConnectionNotice.CloudProjectConnected
                            }
                        }
                    },
                )
            } else CloudHome(
                getServers = GetServersUseCase(repository),
                repository = repository,
                legacyStorageBoxCredentialStore = legacyStorageBoxCredentialStore,
                storageBoxRepository = storageBoxRepository,
                objectStorageCredentialStore = objectStorageCredentialStore,
                objectStorageRepository = objectStorageRepository,
                billingRepository = billingRepository,
                serverMetricsRepository = serverMetricsRepository,
                loadBalancerMetricsRepository = loadBalancerMetricsRepository,
                dnsRepository = dnsRepository,
                tokenStore = tokenStore,
                connections = connections,
                onConnectionsChanged = { connections = tokenStore.connections() },
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

@Composable
private fun Onboarding(
    isAdditionalProject: Boolean,
    onConnect: (String, String, HCloudConnectionConfig) -> Result<Unit>,
) {
    var projectName by remember { mutableStateOf("") }
    var connectionKind by remember { mutableStateOf(ConnectionKind.REAL) }
    var realToken by remember { mutableStateOf("") }
    var testServerToken by remember { mutableStateOf("") }
    var realTokenVisible by remember { mutableStateOf(false) }
    var testTokenVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isTestServer = connectionKind == ConnectionKind.TEST_MOCK
    val token = if (isTestServer) testServerToken else realToken
    val testProfileName = stringResource(R.string.onboarding_test_profile_name)
    val invalidName = submitted && !isTestServer && projectName.isBlank()
    val invalidToken = submitted && token.isBlank()
    val submitConnection = {
        // Do not rely on Material Button's internal gesture node here. On a physical
        // device this CTA lives inside a scrolling card below a password field; a
        // plain clickable surface keeps touch, keyboard and accessibility activation
        // on the same explicit callback.
        focusManager.clearFocus(force = true)
        submitted = true
        connectionError = false
        onboardingConnectionRequest(
            connectionKind = connectionKind,
            projectName = projectName,
            testProfileName = testProfileName,
            token = token,
        )?.let { request ->
            connectionError = onConnect(request.displayName, request.token, request.config).isFailure
        }
        Unit
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isTestServer,
                onClick = { connectionKind = ConnectionKind.REAL; submitted = false },
                label = { Text(stringResource(R.string.onboarding_real_hetzner)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            FilterChip(
                selected = isTestServer,
                onClick = { connectionKind = ConnectionKind.TEST_MOCK; submitted = false },
                label = { Text(stringResource(R.string.onboarding_test_server)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
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
            OutlinedTextField(value = projectName, onValueChange = { projectName = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.onboarding_project_name_label)) }, supportingText = { if (invalidName) Text(stringResource(R.string.onboarding_project_name_error)) }, isError = invalidName, singleLine = true, colors = onboardingTextFieldColors())
            Spacer(Modifier.height(12.dp))
        }
        val tokenVisible = if (isTestServer) testTokenVisible else realTokenVisible
        OutlinedTextField(
            value = token,
            onValueChange = { value -> if (isTestServer) testServerToken = value else realToken = value },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (isTestServer) R.string.onboarding_test_token_label else R.string.onboarding_token_label)) },
            supportingText = { if (invalidToken) Text(stringResource(if (isTestServer) R.string.onboarding_test_token_error else R.string.onboarding_token_error)) },
            isError = invalidToken,
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { if (isTestServer) testTokenVisible = !testTokenVisible else realTokenVisible = !realTokenVisible }) { Text(stringResource(if (tokenVisible) R.string.common_hide else R.string.common_show)) } },
            colors = onboardingTextFieldColors(),
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = submitConnection),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (isTestServer) R.string.onboarding_open_test_server else R.string.onboarding_connect_securely),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (submitted && (invalidName || invalidToken || connectionError)) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when {
                        invalidName -> R.string.onboarding_project_name_error
                        invalidToken -> if (isTestServer) R.string.onboarding_test_token_error else R.string.onboarding_token_error
                        else -> R.string.onboarding_save_error
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.onboarding_scope_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorTextColor = MaterialTheme.colorScheme.onSurface,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
)

private sealed interface ServersState {
    data object Loading : ServersState
    data class Content(val servers: List<Server>) : ServersState
    data object Offline : ServersState
    data object ReadOnly : ServersState
    data class Error(val message: String) : ServersState
}

/** Read-only account metadata returned for the currently selected Cloud project. */
private sealed interface BackupSnapshotState {
    data object Loading : BackupSnapshotState
    data class Content(val images: List<BackupSnapshotImage>, val volumes: List<VolumeInventory>, val servers: List<Server>) : BackupSnapshotState
    data class Error(val message: String) : BackupSnapshotState
}
private sealed interface StorageBoxesState {
    data object Loading : StorageBoxesState
    data class Content(val boxes: List<StorageBox>) : StorageBoxesState
    data class Error(val message: String) : StorageBoxesState
}

/** A subaccount lookup is deliberately kept read-only: the API never exposes credentials here. */
private sealed interface StorageBoxSubaccountsState {
    data object Loading : StorageBoxSubaccountsState
    data class Content(val subaccounts: List<StorageBoxSubaccount>) : StorageBoxSubaccountsState
    data object Error : StorageBoxSubaccountsState
}
private sealed interface PrimaryIpState {
    data object Loading : PrimaryIpState
    data class Content(val ips: List<PrimaryIpInventory>, val floatingIps: List<FloatingIpInventory>, val servers: List<Server>, val firewalls: List<FirewallInventory>, val networks: List<PrivateNetworkInventory>, val loadBalancers: List<LoadBalancerInventory>, val placementGroups: List<PlacementGroupInventory>) : PrimaryIpState
    data class Error(val message: String) : PrimaryIpState
}

/** Detail and history are both scoped to the selected Primary IP and have no mutation surface. */
private sealed interface PrimaryIpDetailState {
    data object Loading : PrimaryIpDetailState
    data class Content(val primaryIp: PrimaryIpInventory, val actions: List<CloudAction>) : PrimaryIpDetailState
    data class Error(val message: String) : PrimaryIpDetailState
}
private data class NetworkInventoryResult(val ips: List<PrimaryIpInventory>, val floatingIps: List<FloatingIpInventory>, val servers: List<Server>, val firewalls: List<FirewallInventory>, val networks: List<PrivateNetworkInventory>, val loadBalancers: List<LoadBalancerInventory>, val placementGroups: List<PlacementGroupInventory>)
internal enum class Destination(val labelRes: Int, val marker: String) { Servers(R.string.nav_servers, "▣"), Catalog(R.string.nav_catalog, "⌕"), Storage(R.string.nav_storage, "▤"), Images(R.string.nav_images, "▧"), StorageBoxes(R.string.nav_storage_boxes, "▦"), ObjectStorage(R.string.nav_object_storage, "◉"), Tariffs(R.string.nav_tariffs, "¤"), LoadBalancers(R.string.nav_load_balancers, "⇄"), Certificates(R.string.nav_certificates, "◇"), SshKeys(R.string.nav_ssh_keys, "⌘"), Network(R.string.nav_network, "⌁"), PlacementGroups(R.string.nav_placement_groups, "⊞"), Dns(R.string.nav_dns, "≋"), Settings(R.string.nav_settings, "⚙") }

/** The deterministic root-level back priority, kept pure so it cannot regress unnoticed. */
internal enum class HomeBackAction { CloseMore, CancelServerCreate, CloseServerDetail, LeavePrimaryDestination, LetSystemHandle }

internal fun resolveHomeBack(
    showMoreDestinations: Boolean,
    showCreateServer: Boolean,
    selectedServer: Server?,
    destination: Destination,
): HomeBackAction = when {
    showMoreDestinations -> HomeBackAction.CloseMore
    showCreateServer -> HomeBackAction.CancelServerCreate
    selectedServer != null -> HomeBackAction.CloseServerDetail
    destination != Destination.Servers -> HomeBackAction.LeavePrimaryDestination
    else -> HomeBackAction.LetSystemHandle
}

/**
 * A bottom bar is intentionally limited to four destinations plus an overflow affordance.
 * Rendering all Cloud areas here made translated labels unreadable and touch targets too small.
 */
private val primaryDestinations = listOf(
    Destination.Servers,
    Destination.Catalog,
    Destination.Storage,
    Destination.Network,
)
private val overflowDestinations = Destination.entries.filterNot { it in primaryDestinations }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudHome(
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
    val activeId = tokenStore.activeConnectionId()
    val activeProject = connections.firstOrNull { it.id == activeId } ?: connections.first()
    var destination by remember { mutableStateOf(Destination.Servers) }
    var serverState by remember(activeProject.id) { mutableStateOf<ServersState>(ServersState.Loading) }
    var serverLoadGeneration by remember(activeProject.id) { mutableStateOf(0L) }
    var serverRefreshInProgress by remember(activeProject.id) { mutableStateOf(false) }
    var selectedServer by remember(activeProject.id) { mutableStateOf<Server?>(null) }
    var showCreateServer by remember(activeProject.id) { mutableStateOf(false) }
    var createServerTypePrefill by remember(activeProject.id) { mutableStateOf<ServerTypeOption?>(null) }
    var createServerLocationPrefill by remember(activeProject.id) { mutableStateOf<LocationOption?>(null) }
    // Links from the create catalog are children of that workflow.  Remember their parent
    // so the system gesture returns there instead of unexpectedly jumping to Servers.
    var primaryBackDestination by remember { mutableStateOf<Destination?>(null) }
    var showMoreDestinations by remember { mutableStateOf(false) }
    /*
     * This is the root of the in-app back stack.  Compose screens below it may add a
     * more specific BackHandler (dialogs do this through their dismiss callbacks).
     * At this level the order is deliberately: transient UI -> detail -> primary tab
     * -> let Android finish the activity.
     */
    BackHandler(enabled = resolveHomeBack(showMoreDestinations, showCreateServer, selectedServer, destination) != HomeBackAction.LetSystemHandle) {
        when (resolveHomeBack(showMoreDestinations, showCreateServer, selectedServer, destination)) {
            HomeBackAction.CloseMore -> showMoreDestinations = false
            HomeBackAction.CancelServerCreate -> showCreateServer = false
            HomeBackAction.CloseServerDetail -> selectedServer = null
            HomeBackAction.LeavePrimaryDestination -> {
                destination = primaryBackDestination ?: Destination.Servers
                primaryBackDestination = null
            }
            HomeBackAction.LetSystemHandle -> Unit
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
        // Pull-to-refresh, retries and a completed mutation can arrive nearly at
        // once.  One authoritative request is enough; starting several used to
        // let an older result overwrite the newer inventory state.
        if (serverRefreshInProgress) return
        serverRefreshInProgress = true
        val generation = ++serverLoadGeneration
        // Keep a populated inventory visible under the pull indicator.  The
        // first load still uses the dedicated loading state.
        if (serverState !is ServersState.Content) serverState = ServersState.Loading
        scope.launch {
            val result = try { ServersState.Content(withContext(Dispatchers.IO) { getServers() }) }
            catch (_: IOException) { ServersState.Offline }
            catch (error: Throwable) {
                ServersState.Error(safeApiFailureMessage(error, "Could not load servers"))
            }
            if (generation == serverLoadGeneration) {
                serverState = result
                serverRefreshInProgress = false
            }
        }
    }
    LaunchedEffect(activeProject.id) { refresh() }
    if (showCreateServer) {
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
                showCreateServer = false
                createServerTypePrefill = null
                createServerLocationPrefill = null
                refresh()
            },
        )
        return
    }
    CompositionLocalProvider(LocalScrollProjectNamespace provides activeProject.id) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = { HCloudTopBar(selectedServer, activeProject, connections, onSelectProject = { connection ->
                    if (connection.id != activeProject.id) {
                        tokenStore.selectConnection(connection.id)
                        onConnectionsChanged()
                    }
                }, onBack = { selectedServer = null }) },
                floatingActionButton = {
                    if (selectedServer == null && destination == Destination.Servers && LocalHighImpactOperations.current.isAllowed) {
                        ExtendedFloatingActionButton(onClick = {
                            createServerTypePrefill = null
                            createServerLocationPrefill = null
                            showCreateServer = true
                        }) { Text(stringResource(R.string.create_server)) }
                    }
                },
                bottomBar = {
                    if (selectedServer == null) NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        primaryDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = item == destination,
                                onClick = { primaryBackDestination = null; destination = item },
                                icon = { Text(item.marker) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        NavigationBarItem(
                            selected = destination in overflowDestinations,
                            onClick = { showMoreDestinations = true },
                            icon = { Text("⋯") },
                            label = { Text(stringResource(R.string.nav_more), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                },
            ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            selectedServer?.let { currentServer ->
                ServerDetails(
                    currentServer,
                    serverState is ServersState.ReadOnly,
                    repository,
                    serverMetricsRepository,
                    onActionCompleted = {
                        scope.launch {
                            selectedServer = runCatching { repository.getServer(currentServer.id) }.getOrDefault(currentServer)
                            refresh()
                        }
                    },
                    onDeleted = {
                        selectedServer = null
                        refresh()
                    },
                    onBack = { selectedServer = null },
                )
            } ?: when (destination) {
                Destination.Servers -> ServersScreen(serverState, serverRefreshInProgress, ::refresh) { selectedServer = it }
                Destination.Catalog -> ServerCatalogScreen(
                    repository = repository,
                    projectId = activeProject.id,
                    onConfigureServer = { type, location ->
                        createServerTypePrefill = type
                        createServerLocationPrefill = location
                        showCreateServer = true
                    },
                    onOpenStorage = { primaryBackDestination = Destination.Catalog; destination = Destination.Storage },
                    onOpenNetwork = { primaryBackDestination = Destination.Catalog; destination = Destination.Network },
                    onOpenSettings = { primaryBackDestination = Destination.Catalog; destination = Destination.Settings },
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
                Destination.ObjectStorage -> ObjectStorageScreen(store = objectStorageCredentialStore, repository = objectStorageRepository)
                Destination.Tariffs -> TariffsEstimateScreen(repository = billingRepository, projectId = activeProject.id)
                Destination.LoadBalancers -> LoadBalancersScreen(repository = repository, metricsRepository = loadBalancerMetricsRepository, projectId = activeProject.id)
                Destination.Certificates -> CertificatesScreen(repository = repository, projectId = activeProject.id)
                Destination.SshKeys -> SshKeysScreen(repository = repository, projectId = activeProject.id)
                Destination.Network -> PrimaryIpInventoryScreen(repository = repository, projectId = activeProject.id)
                Destination.PlacementGroups -> PlacementGroupsScreen(repository = repository, projectId = activeProject.id)
                Destination.Dns -> DnsReadOnlyScreen(projectId = activeProject.id, repository = dnsRepository)
                Destination.Settings -> SettingsScreen(
                    activeProject = activeProject,
                    connections = connections,
                    onAddProject = onAddProject,
                    onDisconnect = {
                        tokenStore.removeConnection(activeProject.id)
                        onConnectionsChanged()
                    },
                    appearanceMode = appearanceMode,
                    onAppearanceModeChange = onAppearanceModeChange,
                    onHighImpactOperationsChange = onHighImpactOperationsChange,
                )
            }
        }
    if (showMoreDestinations) {
        ModalBottomSheet(onDismissRequest = { showMoreDestinations = false }) {
            Text(
                text = stringResource(R.string.nav_more),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(overflowDestinations) { item ->
                    ListItem(
                        headlineContent = { Text(stringResource(item.labelRes)) },
                        leadingContent = { Text(item.marker, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                primaryBackDestination = null
                                destination = item
                                showMoreDestinations = false
                            },
                    )
                }
            }
        }
    }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HCloudTopBar(server: Server?, activeProject: CloudProjectConnection, connections: List<CloudProjectConnection>, onSelectProject: (CloudProjectConnection) -> Unit, onBack: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(title = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (server != null) {
                Text(server.name)
                if (activeProject.config.kind == ConnectionKind.TEST_MOCK) TestServerBadge()
            } else Box {
                TextButton(onClick = { expanded = true }) { Text("${activeProject.displayName} ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    connections.forEach { connection -> DropdownMenuItem(
                        text = {
                            Column {
                                Text(connection.displayName)
                                if (connection.config.kind == ConnectionKind.TEST_MOCK) TestServerBadge()
                            }
                        },
                        onClick = { expanded = false; onSelectProject(connection) },
                    ) }
                }
            }
            if (server == null && activeProject.config.kind == ConnectionKind.TEST_MOCK) TestServerBadge()
        }
    }, navigationIcon = { if (server != null) TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } })
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
    state: ServersState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onServerClick: (Server) -> Unit,
) = HCloudPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            // A scrollable child is intentional even for the non-list states.  Besides
            // making the gesture discoverable there, it gives PullToRefreshBox a nested
            // scroll participant; a static Column can consume neither the overscroll nor
            // the pull gesture on some devices.
            ServersState.Loading -> ServersRefreshStateView(
                title = stringResource(R.string.servers_loading),
                message = null,
                action = null,
                onAction = onRefresh,
                loading = true,
            )
            ServersState.Offline -> ServersRefreshStateView(
                stringResource(R.string.servers_offline_title),
                stringResource(R.string.servers_offline_message),
                stringResource(R.string.common_retry),
                onRefresh,
            )
            ServersState.ReadOnly -> ServersRefreshStateView(
                stringResource(R.string.servers_read_only_title),
                stringResource(R.string.servers_read_only_message),
                stringResource(R.string.common_retry),
                onRefresh,
            )
            is ServersState.Error -> ServersRefreshStateView(
                stringResource(R.string.servers_load_failed),
                state.message,
                stringResource(R.string.servers_try_again),
                onRefresh,
            )
            is ServersState.Content -> if (state.servers.isEmpty()) {
                ServersRefreshStateView(
                    stringResource(R.string.servers_empty_title),
                    stringResource(R.string.servers_empty_message),
                    stringResource(R.string.common_retry),
                    onRefresh,
                )
            } else LazyColumn(
            state = rememberRestorableLazyListState("servers"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HCloudScreenHeader(stringResource(R.string.servers_title), stringResource(R.string.servers_count, state.servers.size)) }
            items(
                state.servers.sortedWith(compareBy<Server> { it.name.lowercase() }.thenBy { it.id }),
                key = { it.id },
            ) { ServerCard(it) { onServerClick(it) } }
        }
    }
}

@Composable
private fun ServersRefreshStateView(
    title: String,
    message: String?,
    action: String?,
    onAction: () -> Unit,
    loading: Boolean = false,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                }
                Text(title, style = if (loading) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall)
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.let {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = onAction) { Text(it) }
                }
            }
        }
    }
}

@Composable private fun ServerCard(server: Server, onClick: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(server.status); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(server.name, style = MaterialTheme.typography.titleMedium); Text("${server.serverType} · ${server.location}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(horizontalAlignment = Alignment.End) { StatusLabel(server.status); Text(server.ipv4Address ?: stringResource(R.string.server_no_public_ipv4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun StatusDot(status: ServerStatus) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (status == ServerStatus.RUNNING) Color(0xFF73DB9B) else Color(0xFFFFB95A))) }
@Composable private fun StatusLabel(status: ServerStatus) = Text(status.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelMedium)

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
    var storageAction by remember { mutableStateOf<ServerStorageAction?>(null) }
    var recoveryOperation by remember { mutableStateOf<ServerRecoveryOperation?>(null) }
    var showServerDnsPtr by remember { mutableStateOf(false) }
    var showResize by remember { mutableStateOf(false) }
    var observabilityRefreshKey by remember(server.id) { mutableStateOf(0) }
    var actionHistoryState by remember(server.id) { mutableStateOf<ServerActionHistoryState>(ServerActionHistoryState.Loading) }
    var metricsState by remember(server.id) { mutableStateOf<ServerMetricsState>(ServerMetricsState.Loading) }
    BackHandler(enabled = showResize) { showResize = false }
    BackHandler(enabled = !showResize) { onBack() }
    val historyLoadError = stringResource(R.string.server_action_history_load_error)
    val metricsLoadError = stringResource(R.string.server_metrics_load_error)
    LaunchedEffect(server.id, observabilityRefreshKey) {
        coroutineScope {
            val history = async(Dispatchers.IO) {
                runCatching { repository.getServerActions(server.id) }
            }
            val metrics = async(Dispatchers.IO) {
                val end = Instant.now()
                val query = ServerMetricsQuery(
                    serverId = server.id,
                    types = listOf(ServerMetricType.CPU, ServerMetricType.DISK, ServerMetricType.NETWORK),
                    start = end.minus(SERVER_METRICS_WINDOW),
                    end = end,
                )
                runCatching { serverMetricsRepository.getServerMetrics(query) }
            }
            actionHistoryState = history.await().fold(
                onSuccess = { ServerActionHistoryState.Content(it) },
                onFailure = { ServerActionHistoryState.Error(historyLoadError) },
            )
            metricsState = metrics.await().fold(
                onSuccess = { ServerMetricsState.Content(it) },
                onFailure = { ServerMetricsState.Error(metricsLoadError) },
            )
        }
    }
    if (showResize) {
        ChangeServerTypeScreen(server, repository, onCancel = { showResize = false }, onChanged = { showResize = false; onActionCompleted() })
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HCloudScreenHeader(stringResource(R.string.server_overview), server.status.name.lowercase().replaceFirstChar { it.titlecase() }) }
        item { DetailsCard(server) }
        item { ServerNetworkDeviceSection(server) }
        item { ServerObservabilitySection(actionHistoryState, metricsState, onRetry = { observabilityRefreshKey++ }) }
        if (highImpactAllowed) {
            item { ServerManagementSection(server = server, repository = repository, readOnly = readOnly, onCompleted = onActionCompleted) }
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
    action?.let { PowerConfirmation(action = it, server = server, repository = repository, canExecute = !readOnly, onCompleted = onActionCompleted, onDismiss = { action = null }) }
    storageAction?.let { StorageActionConfirmation(action = it, server = server, repository = repository, canExecute = !readOnly, onCompleted = onActionCompleted, onDismiss = { storageAction = null }) }
    recoveryOperation?.let { recovery -> ServerRecoveryDialog(server = server, operation = recovery, repository = repository, onDismiss = { recoveryOperation = null }, onActionCompleted = onActionCompleted) }
    if (showServerDnsPtr) ServerDnsPtrDialog(server = server, repository = repository, onDismiss = { showServerDnsPtr = false }, onActionCompleted = onActionCompleted)
}

/** A deliberately bounded, read-only window prevents accidental monitoring backfills. */
private val SERVER_METRICS_WINDOW: Duration = Duration.ofHours(6)

private sealed interface ServerActionHistoryState {
    data object Loading : ServerActionHistoryState
    data class Content(val actions: List<CloudAction>) : ServerActionHistoryState
    data class Error(val message: String) : ServerActionHistoryState
}

private sealed interface ServerMetricsState {
    data object Loading : ServerMetricsState
    data class Content(val metrics: ServerMetrics) : ServerMetricsState
    data class Error(val message: String) : ServerMetricsState
}

@Composable
private fun ServerObservabilitySection(
    historyState: ServerActionHistoryState,
    metricsState: ServerMetricsState,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.server_metrics_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.server_metrics_window),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (metricsState) {
            ServerMetricsState.Loading -> ObservabilityLoading(stringResource(R.string.server_metrics_loading))
            is ServerMetricsState.Error -> ObservabilityError(metricsState.message, onRetry)
            is ServerMetricsState.Content -> ServerMetricsSummary(metricsState.metrics)
        }

        Text(stringResource(R.string.server_action_history_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.server_action_history_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (historyState) {
            ServerActionHistoryState.Loading -> ObservabilityLoading(stringResource(R.string.server_action_history_loading))
            is ServerActionHistoryState.Error -> ObservabilityError(historyState.message, onRetry)
            is ServerActionHistoryState.Content -> ServerActionHistory(historyState.actions)
        }
    }
}

@Composable
private fun ObservabilityError(message: String, onRetry: () -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(message, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
}

@Composable
private fun ObservabilityLoading(message: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ServerMetricsSummary(metrics: ServerMetrics) {
    if (metrics.timeSeries.isEmpty()) {
        Text(stringResource(R.string.server_metrics_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.timeSeries.toSortedMap().forEach { (type, series) ->
                val latest = series.samples.maxByOrNull { it.timestampSeconds }
                DetailRow(
                    type.uppercase(),
                    latest?.value ?: stringResource(R.string.server_metrics_no_samples),
                )
            }
            Text(
                stringResource(R.string.server_metrics_step, metrics.stepSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable private fun DetailsCard(server: Server) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { DetailRow(stringResource(R.string.detail_type), server.serverType); DetailRow(stringResource(R.string.detail_location), server.location); DetailRow(stringResource(R.string.detail_ipv4), server.ipv4Address ?: stringResource(R.string.server_not_assigned)); DetailRow(stringResource(R.string.detail_server_id), server.id.toString()) } }
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
    var submitting by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val sendingRequest = stringResource(R.string.common_sending_request)
    val actionFailed = stringResource(R.string.common_action_failed)
    val actionStatusFormat = stringResource(R.string.action_status_format)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.power_confirmation_title, action.title(), server.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(action.explanation())
                if (action.risk == PowerRisk.Destructive) Text(stringResource(R.string.power_destructive_warning), color = MaterialTheme.colorScheme.error)
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (completed) Text(stringResource(R.string.storage_operation_completed), color = MaterialTheme.colorScheme.primary)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (completed) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            } else Button(
                onClick = {
                    submitting = true
                    errorText = null
                    statusText = sendingRequest
                    scope.launch {
                        try {
                            val cloudAction = when (action) {
                                PowerAction.Start -> repository.powerOn(server.id)
                                PowerAction.Shutdown -> repository.shutdown(server.id)
                                PowerAction.PowerOff -> repository.powerOff(server.id)
                                PowerAction.Reboot -> repository.reboot(server.id)
                                PowerAction.Reset -> repository.reset(server.id)
                            }
                            awaitCloudAction(repository, cloudAction) { actionStatus ->
                                statusText = String.format(actionStatusFormat, powerActionStatusText(actionStatus))
                            }
                            onCompleted()
                            submitting = false
                            completed = true
                        } catch (_: Throwable) {
                            errorText = actionFailed
                            submitting = false
                        }
                    }
                },
                enabled = canExecute && !submitting,
                colors = if (action.risk == PowerRisk.Destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(if (submitting) stringResource(R.string.common_in_progress) else action.title()) }
        },
        dismissButton = {
            if (!completed) TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private enum class ServerStorageAction(val titleRes: Int, val warningRes: Int) {
    EnableBackups(R.string.server_enable_backups, R.string.storage_enable_backups_warning),
    DisableBackups(R.string.server_disable_backups, R.string.storage_disable_backups_warning),
    CreateSnapshot(R.string.server_create_snapshot, R.string.storage_create_snapshot_warning),
}

@Composable
private fun StorageActionConfirmation(action: ServerStorageAction, server: Server, repository: HCloudRepository, canExecute: Boolean, onCompleted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.common_title_with_name, stringResource(action.titleRes), server.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(action.warningRes))
                if (action == ServerStorageAction.CreateSnapshot) {
                    OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.snapshot_description_label)) }, supportingText = { Text(stringResource(R.string.snapshot_description_example)) }, singleLine = true, enabled = !submitting)
                }
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (completed) Text(stringResource(R.string.storage_operation_completed), color = MaterialTheme.colorScheme.primary)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (completed) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            } else Button(
                onClick = {
                    submitting = true
                    errorText = null
                    statusText = context.getString(R.string.common_sending_request)
                    scope.launch {
                        try {
                            val cloudAction = when (action) {
                                ServerStorageAction.EnableBackups -> repository.enableBackups(server.id)
                                ServerStorageAction.DisableBackups -> repository.disableBackups(server.id)
                                ServerStorageAction.CreateSnapshot -> repository.createSnapshot(server.id, description.trim())
                            }
                            awaitCloudAction(repository, cloudAction) { actionStatus ->
                                statusText = context.getString(R.string.action_status_format, powerActionStatusText(actionStatus))
                            }
                            onCompleted()
                            submitting = false
                            completed = true
                        } catch (_: Throwable) {
                            errorText = context.getString(R.string.common_action_failed)
                            submitting = false
                        }
                    }
                },
                enabled = canExecute && !submitting && (action != ServerStorageAction.CreateSnapshot || description.isNotBlank()),
                colors = if (action == ServerStorageAction.DisableBackups) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(if (submitting) stringResource(R.string.common_in_progress) else stringResource(action.titleRes)) }
        },
        dismissButton = {
            if (!completed) TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) }
        },
    )
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
private fun StorageBoxesScreen(
    project: CloudProjectConnection,
    legacyCredentialStore: LegacyStorageBoxCredentialStore,
    repository: StorageBoxRepository,
) {
    var legacyCredentials by remember { mutableStateOf(legacyCredentialStore.credentials()) }
    var credentialToForget by remember { mutableStateOf<String?>(null) }
    // Storage Box ids are scoped to the active Hetzner Console project.
    var selectedBox by remember(project.id) { mutableStateOf<StorageBox?>(null) }
    var state by remember(project.id) { mutableStateOf<StorageBoxesState>(StorageBoxesState.Loading) }
    var refreshKey by remember(project.id) { mutableStateOf(0) }
    BackHandler(enabled = selectedBox != null) { selectedBox = null }
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.storage_boxes_load_error_project)
    fun load() {
        state = StorageBoxesState.Loading
        scope.launch {
            state = try {
                StorageBoxesState.Content(withContext(Dispatchers.IO) { repository.getStorageBoxes() })
            } catch (error: Throwable) {
                StorageBoxesState.Error(safeApiFailureMessage(error, loadError))
            }
        }
    }
    LaunchedEffect(project.id, refreshKey) { load() }

    selectedBox?.let { box ->
        StorageBoxDetailScreen(box = box, repository = repository, onBack = { selectedBox = null })
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.storage_boxes_title), style = MaterialTheme.typography.headlineMedium)
                Text(project.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.storage_boxes_project_token), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (legacyCredentials.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.storage_boxes_legacy_credentials_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.storage_boxes_legacy_credentials_message), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    legacyCredentials.forEach { credential ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(credential.displayName, modifier = Modifier.weight(1f))
                            TextButton(onClick = { credentialToForget = credential.id }) {
                                Text(stringResource(R.string.storage_boxes_legacy_credentials_remove))
                            }
                        }
                    }
                }
            }
        }
        when (val current = state) {
            StorageBoxesState.Loading -> LoadingView(stringResource(R.string.storage_boxes_loading))
            is StorageBoxesState.Error -> StateView(
                stringResource(R.string.storage_boxes_error_title), current.message,
                stringResource(R.string.storage_boxes_retry), { refreshKey++ },
            )
            is StorageBoxesState.Content -> if (current.boxes.isEmpty()) {
                StateView(stringResource(R.string.storage_boxes_empty_title), stringResource(R.string.storage_boxes_empty_message), stringResource(R.string.storage_boxes_retry), { refreshKey++ })
            } else {
                Text(stringResource(R.string.storage_boxes_pagination_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HCloudPullToRefresh(isRefreshing = false, onRefresh = { refreshKey++ }, modifier = Modifier.weight(1f)) {
                LazyColumn(state = rememberRestorableLazyListState("storage-boxes:${project.id}"), modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(current.boxes.sortedBy { it.name.orEmpty().lowercase() }, key = { it.id }) { box ->
                        StorageBoxCard(box) { selectedBox = box }
                    }
                }
                }
            }
        }
    }
    credentialToForget?.let { credentialId ->
        AlertDialog(
            onDismissRequest = { credentialToForget = null },
            title = { Text(stringResource(R.string.storage_boxes_legacy_credentials_forget_title)) },
            text = { Text(stringResource(R.string.storage_boxes_legacy_credentials_forget_message)) },
            confirmButton = {
                TextButton(onClick = {
                    legacyCredentialStore.removeCredential(credentialId)
                    legacyCredentials = legacyCredentialStore.credentials()
                    credentialToForget = null
                }) { Text(stringResource(R.string.storage_boxes_legacy_credentials_forget_confirm)) }
            },
            dismissButton = { TextButton(onClick = { credentialToForget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun StorageBoxCard(box: StorageBox, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(box.name ?: stringResource(R.string.storage_boxes_unnamed), style = MaterialTheme.typography.titleMedium)
        Text(box.status ?: stringResource(R.string.storage_boxes_status_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
        box.stats?.let { stats ->
            Text(
                stringResource(
                    R.string.storage_boxes_usage_formatted,
                    formatStorageBoxBytes(context, stats.sizeData),
                    formatStorageBoxBytes(context, stats.size),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun formatStorageBoxBytes(context: android.content.Context, value: Number): String =
    Formatter.formatFileSize(context, value.toLong().coerceAtLeast(0L))

@Composable
private fun StorageBoxDetailScreen(box: StorageBox, repository: StorageBoxRepository, onBack: () -> Unit) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var detail by remember(box.id) { mutableStateOf<StorageBox?>(null) }
    var folders by remember(box.id) { mutableStateOf<List<String>?>(null) }
    var failed by remember(box.id) { mutableStateOf(false) }
    var snapshots by remember(box.id) { mutableStateOf<List<StorageBoxSnapshot>?>(null) }
    var subaccounts by remember(box.id) { mutableStateOf<StorageBoxSubaccountsState>(StorageBoxSubaccountsState.Loading) }
    var showCreateSnapshot by remember(box.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(box.id) {
        failed = false
        subaccounts = StorageBoxSubaccountsState.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                Triple(repository.getStorageBox(box.id), repository.getStorageBoxFolders(box.id), repository.getStorageBoxSnapshots(box.id))
            }
        }.onSuccess { (loadedBox, loadedFolders, loadedSnapshots) -> detail = loadedBox; folders = loadedFolders; snapshots = loadedSnapshots }
            .onFailure { failed = true }
        runCatching {
            withContext(Dispatchers.IO) { repository.getStorageBoxSubaccounts(box.id) }
        }.onSuccess { loadedSubaccounts ->
            subaccounts = StorageBoxSubaccountsState.Content(loadedSubaccounts)
        }.onFailure {
            subaccounts = StorageBoxSubaccountsState.Error
        }
    }
    val current = detail ?: box
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← ${stringResource(R.string.storage_boxes_back)}") } }
        item { Text(current.name ?: stringResource(R.string.storage_boxes_unnamed), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.common_id, current.id), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("${stringResource(R.string.storage_boxes_status)}: ${current.status ?: stringResource(R.string.storage_boxes_status_unknown)}") }
        current.stats?.let { stats ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.storage_boxes_stats), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.storage_boxes_total_formatted, formatStorageBoxBytes(LocalContext.current, stats.size)))
                    Text(stringResource(R.string.storage_boxes_data_formatted, formatStorageBoxBytes(LocalContext.current, stats.sizeData)))
                    Text(stringResource(R.string.storage_boxes_snapshots_formatted, formatStorageBoxBytes(LocalContext.current, stats.sizeSnapshots)))
                }
            }
        } ?: item { Text(stringResource(R.string.storage_boxes_stats_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.storage_boxes_folders), style = MaterialTheme.typography.titleMedium) }
        when {
            failed -> item { Text(stringResource(R.string.storage_boxes_folders_load_error), color = MaterialTheme.colorScheme.error) }
            folders == null -> item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.storage_boxes_folders_loading)) } }
            folders!!.isEmpty() -> item { Text(stringResource(R.string.storage_boxes_folders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> items(folders!!.sorted(), key = { it }) { folder -> Text(folder, Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        item { Text(stringResource(R.string.storage_boxes_subaccounts_title), style = MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.storage_boxes_subaccounts_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        when (val currentSubaccounts = subaccounts) {
            StorageBoxSubaccountsState.Loading -> item { Text(stringResource(R.string.storage_boxes_subaccounts_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            StorageBoxSubaccountsState.Error -> item { Text(stringResource(R.string.storage_boxes_subaccounts_load_error), color = MaterialTheme.colorScheme.error) }
            is StorageBoxSubaccountsState.Content -> if (currentSubaccounts.subaccounts.isEmpty()) {
                item { Text(stringResource(R.string.storage_boxes_subaccounts_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else items(
                currentSubaccounts.subaccounts.sortedWith(compareBy<StorageBoxSubaccount> { it.name.orEmpty().lowercase() }.thenBy { it.username.orEmpty().lowercase() }),
                key = { "${it.id ?: ""}/${it.username.orEmpty()}" },
            ) { subaccount ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(subaccount.name ?: subaccount.username ?: stringResource(R.string.storage_boxes_subaccount_unnamed), style = MaterialTheme.typography.titleSmall)
                        subaccount.username?.let { username -> Text(stringResource(R.string.storage_boxes_subaccount_username, username), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        subaccount.id?.let { id -> Text(stringResource(R.string.common_id, id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.storage_boxes_snapshot_title), style = MaterialTheme.typography.titleMedium) }
        if (highImpactAllowed) {
            item {
                OutlinedButton(onClick = { showCreateSnapshot = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.storage_boxes_snapshot_create))
                }
            }
        }
        when (val currentSnapshots = snapshots) {
            null -> item { Text(stringResource(R.string.storage_boxes_snapshots_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> if (currentSnapshots.isEmpty()) {
                item { Text(stringResource(R.string.storage_boxes_snapshots_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else items(currentSnapshots.sortedByDescending { it.created.orEmpty() }, key = { it.id }) { snapshot ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(snapshot.name ?: stringResource(R.string.storage_boxes_snapshot_unnamed, snapshot.id), style = MaterialTheme.typography.titleSmall)
                        snapshot.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(stringResource(if (snapshot.isAutomatic == true) R.string.storage_boxes_snapshot_automatic else R.string.storage_boxes_snapshot_manual), style = MaterialTheme.typography.labelMedium)
                        snapshot.created?.let { Text(stringResource(R.string.storage_boxes_snapshot_created, it), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
    if (showCreateSnapshot) StorageBoxSnapshotCreateDialog(
        storageBoxId = box.id,
        repository = repository,
        onDismiss = { showCreateSnapshot = false },
        onCreated = { createdSnapshot ->
            snapshots = (snapshots.orEmpty() + createdSnapshot).distinctBy { it.id }
        },
    )
}

@Composable
private fun StorageBoxSnapshotCreateDialog(
    storageBoxId: Long,
    repository: StorageBoxRepository,
    onDismiss: () -> Unit,
    onCreated: (StorageBoxSnapshot) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestAccepted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val createError = stringResource(R.string.storage_boxes_snapshot_create_error)
    AlertDialog(onDismissRequest = { if (!submitting) onDismiss() }, title = { Text(stringResource(R.string.storage_boxes_snapshot_create)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.storage_boxes_snapshot_create_note))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.storage_boxes_snapshot_description)) },
                enabled = !submitting && !requestAccepted,
            )
            if (submitting) Text(stringResource(R.string.storage_boxes_snapshot_creating), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (requestAccepted) Text(stringResource(R.string.storage_boxes_snapshot_request_accepted), color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        if (requestAccepted) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        } else {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            val created = repository.createStorageBoxSnapshot(
                                storageBoxId,
                                CreateStorageBoxSnapshotSpec(description.ifBlank { null }),
                            )
                            onCreated(created.snapshot)
                            requestAccepted = true
                        } catch (_: Throwable) {
                            error = createError
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
            ) { Text(if (submitting) stringResource(R.string.storage_boxes_snapshot_creating) else stringResource(R.string.storage_boxes_snapshot_create)) }
        }
    }, dismissButton = {
        if (!requestAccepted) TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) }
    })
}

@Composable
private fun TariffsEstimateScreen(repository: BillingRateCardRepository, projectId: String) {
    var state by remember(projectId) { mutableStateOf<BillingRateCard?>(null) }
    var error by remember(projectId) { mutableStateOf(false) }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(projectId, refreshKey) {
        state = null
        error = false
        runCatching { withContext(Dispatchers.IO) { repository.getRateCard() } }
            .onSuccess { state = it }
            .onFailure { error = true }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HCloudScreenHeader(stringResource(R.string.tariffs_title), stringResource(R.string.tariffs_disclaimer))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://console.hetzner.cloud/projects")) }) { Text(stringResource(R.string.tariffs_usage_link)) }
            OutlinedButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse("https://accounts.hetzner.com/")) }) { Text(stringResource(R.string.tariffs_invoices_link)) }
        }
        when {
            error -> StateView(stringResource(R.string.tariffs_error_title), stringResource(R.string.tariffs_error_message), stringResource(R.string.tariffs_retry)) { refreshKey++ }
            state == null -> LoadingView(stringResource(R.string.tariffs_loading))
            else -> {
                val card = state!!
                val complete = card.serverTypes.isNotEmpty() && card.serverTypes.all { it.locations.isNotEmpty() }
                if (!complete) {
                    StateView(stringResource(R.string.tariffs_incomplete_title), stringResource(R.string.tariffs_incomplete_message), stringResource(R.string.tariffs_retry)) { refreshKey++ }
                } else {
                    Text(stringResource(R.string.tariffs_currency_vat, card.currency, card.vatRate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HCloudPullToRefresh(isRefreshing = false, onRefresh = { refreshKey++ }, modifier = Modifier.weight(1f)) {
                    LazyColumn(state = rememberRestorableLazyListState("tariffs"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(card.serverTypes.sortedBy { it.name.lowercase() }, key = { it.id }) { type ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(type.name, style = MaterialTheme.typography.titleMedium)
                                    type.locations.sortedBy { it.location }.forEach { rate ->
                                        Text(stringResource(R.string.tariffs_rate, rate.location, formatApiMoney(rate.hourly.gross), card.currency, formatApiMoney(rate.monthly.gross), card.currency), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageInventoryScreen(
    repository: HCloudRepository,
    projectId: String,
    operationsAllowed: Boolean,
) {
    val context = LocalContext.current
    var state by remember(projectId) { mutableStateOf<BackupSnapshotState>(BackupSnapshotState.Loading) }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    fun load() {
        state = BackupSnapshotState.Loading
        scope.launch {
            state = try {
                val inventory = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val images = async { repository.getBackupAndSnapshotImages() }
                        val volumes = async { repository.getVolumes() }
                        val servers = async { repository.getServers() }
                        Triple(images.await(), volumes.await(), servers.await())
                    }
                }
                BackupSnapshotState.Content(images = inventory.first, volumes = inventory.second, servers = inventory.third)
            } catch (_: Throwable) {
                BackupSnapshotState.Error(context.getString(R.string.storage_inventory_error))
            }
        }
    }
    LaunchedEffect(projectId, refreshKey) { load() }
    when (val current = state) {
        BackupSnapshotState.Loading -> LoadingView(stringResource(R.string.storage_inventory_loading))
        is BackupSnapshotState.Error -> StateView(stringResource(R.string.storage_inventory_title), current.message, stringResource(R.string.common_retry)) { refreshKey++ }
        is BackupSnapshotState.Content -> {
            var volumeAction by remember(projectId) { mutableStateOf<VolumeActionRequest?>(null) }
            var selectedVolume by remember(projectId) { mutableStateOf<VolumeInventory?>(null) }
            var creatingVolume by remember(projectId) { mutableStateOf(false) }
            var deletingVolume by remember(projectId) { mutableStateOf<VolumeInventory?>(null) }
            BackHandler(enabled = selectedVolume != null) { selectedVolume = null }
            val snapshots = current.images.filter { it.type.equals("snapshot", ignoreCase = true) }
            val backups = current.images.filter { it.type.equals("backup", ignoreCase = true) }
            selectedVolume?.let { volume ->
                VolumeDetailScreen(initial = volume, repository = repository, onBack = { selectedVolume = null })
                return
            }
            HCloudPullToRefresh(isRefreshing = false, onRefresh = { refreshKey++ }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = rememberRestorableLazyListState("storage"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.storage_inventory_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(
                            if (operationsAllowed) R.string.storage_inventory_description_active
                            else R.string.storage_inventory_description,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!operationsAllowed) item {
                    Text(
                        stringResource(R.string.storage_operations_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    if (operationsAllowed) {
                        Button(onClick = { creatingVolume = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.volume_create))
                        }
                    }
                }
                item {
                    VolumeInventorySection(
                        volumes = current.volumes,
                        canAttach = current.servers.isNotEmpty(),
                        operationsAllowed = operationsAllowed,
                        onSelect = { selectedVolume = it },
                        onAction = { volume, action -> volumeAction = VolumeActionRequest(volume, action) },
                        onDelete = { volume -> deletingVolume = volume },
                    )
                }
                item { InventorySection(stringResource(R.string.storage_snapshots), snapshots) }
                item { InventorySection(stringResource(R.string.storage_backups), backups) }
                if (snapshots.isEmpty() && backups.isEmpty() && current.volumes.isEmpty()) item {
                    StateView(stringResource(R.string.storage_empty_title), stringResource(R.string.storage_empty_message), stringResource(R.string.common_retry)) { refreshKey++ }
                }
            }
            }
            if (operationsAllowed) volumeAction?.let { request ->
                VolumeActionConfirmation(
                    request = request,
                    servers = current.servers,
                    repository = repository,
                    onCompleted = { volumeAction = null; refreshKey++ },
                    onDismiss = { volumeAction = null },
                )
            }
            if (operationsAllowed && creatingVolume) {
                CreateVolumeDialog(
                    repository = repository,
                    onCompleted = { creatingVolume = false; refreshKey++ },
                    onDismiss = { creatingVolume = false },
                )
            }
            if (operationsAllowed) deletingVolume?.let { volume ->
                DeleteVolumeDialog(
                    volume = volume,
                    repository = repository,
                    onCompleted = { deletingVolume = null; refreshKey++ },
                    onDismiss = { deletingVolume = null },
                )
            }
        }
    }
}

private enum class VolumeAction { Attach, Detach, Resize }
private data class VolumeActionRequest(val volume: VolumeInventory, val action: VolumeAction)

@Composable
private fun VolumeInventorySection(
    volumes: List<VolumeInventory>,
    canAttach: Boolean,
    operationsAllowed: Boolean,
    onSelect: (VolumeInventory) -> Unit,
    onAction: (VolumeInventory, VolumeAction) -> Unit,
    onDelete: (VolumeInventory) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.storage_volumes), style = MaterialTheme.typography.titleLarge)
    if (volumes.isEmpty()) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(stringResource(R.string.storage_volume_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        volumes.sortedWith(compareBy<VolumeInventory> { it.name.lowercase() }.thenBy { it.id }).forEach { volume ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(volume) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(volume.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.storage_volume_size_location, volume.sizeGb, volume.location), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val attachedServerId = volume.serverId
                    Text(if (attachedServerId == null) stringResource(R.string.storage_volume_unattached) else stringResource(R.string.storage_volume_attached, attachedServerId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.common_status_format, volume.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (volume.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled), style = MaterialTheme.typography.labelMedium, color = if (volume.deleteProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (operationsAllowed) {
                        Text(
                            stringResource(R.string.storage_volume_protection_details),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (operationsAllowed) {
                        if (volume.serverId == null) {
                            OutlinedButton(onClick = { onAction(volume, VolumeAction.Attach) }, enabled = canAttach, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.storage_attach)) }
                            if (!canAttach) Text(stringResource(R.string.storage_no_servers_attach), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        } else {
                            OutlinedButton(onClick = { onAction(volume, VolumeAction.Detach) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.storage_detach)) }
                        }
                        OutlinedButton(onClick = { onAction(volume, VolumeAction.Resize) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.storage_resize)) }
                        OutlinedButton(
                            onClick = { onDelete(volume) },
                            enabled = !volume.deleteProtected,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.volume_delete)) }
                        if (volume.deleteProtected) Text(stringResource(R.string.volume_delete_protected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeActionConfirmation(request: VolumeActionRequest, servers: List<Server>, repository: HCloudRepository, onCompleted: () -> Unit, onDismiss: () -> Unit) {
    var selectedServer by remember { mutableStateOf<Server?>(null) }
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var requestedSize by remember { mutableStateOf(request.volume.sizeGb.toString()) }
    var resizeAcknowledged by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parsedSize = requestedSize.toIntOrNull()
    val validResize = parsedSize != null && parsedSize > request.volume.sizeGb
    val failureMessage = when (request.action) {
        VolumeAction.Attach -> stringResource(R.string.storage_attach_failed)
        VolumeAction.Detach -> stringResource(R.string.storage_detach_failed)
        VolumeAction.Resize -> stringResource(R.string.storage_resize_failed)
    }
    val context = LocalContext.current
    val sendingRequest = stringResource(R.string.common_sending_request)
    val title = when (request.action) {
        VolumeAction.Attach -> stringResource(R.string.storage_attach_volume)
        VolumeAction.Detach -> stringResource(R.string.storage_detach_volume)
        VolumeAction.Resize -> stringResource(R.string.storage_resize_volume)
    }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.common_title_with_name, title, request.volume.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (request.action) {
                    VolumeAction.Attach -> {
                        Text(stringResource(R.string.storage_attach_explanation))
                        if (servers.isEmpty()) Text(stringResource(R.string.storage_no_servers), color = MaterialTheme.colorScheme.error)
                        else Box {
                            OutlinedButton(onClick = { serverMenuExpanded = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(selectedServer?.name ?: stringResource(R.string.common_select_server)) }
                            DropdownMenu(expanded = serverMenuExpanded, onDismissRequest = { serverMenuExpanded = false }) {
                                servers.sortedBy { it.name.lowercase() }.forEach { server ->
                                    DropdownMenuItem(text = { Text("${server.name} · ${server.location}") }, onClick = { selectedServer = server; serverMenuExpanded = false })
                                }
                            }
                        }
                    }
                    VolumeAction.Detach -> Text(stringResource(R.string.storage_detach_explanation, request.volume.serverId.toString()))
                    VolumeAction.Resize -> {
                        Text(stringResource(R.string.storage_resize_explanation, request.volume.sizeGb))
                        OutlinedTextField(value = requestedSize, onValueChange = { requestedSize = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.storage_new_size)) }, supportingText = { Text(stringResource(R.string.storage_size_must_exceed, request.volume.sizeGb)) }, isError = requestedSize.isNotBlank() && !validResize, singleLine = true, enabled = !submitting)
                        Text(stringResource(R.string.storage_resize_warning), color = MaterialTheme.colorScheme.error)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !submitting) { resizeAcknowledged = !resizeAcknowledged },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = resizeAcknowledged,
                                onCheckedChange = { resizeAcknowledged = it },
                                enabled = !submitting,
                            )
                            Text(stringResource(R.string.storage_resize_acknowledgement))
                        }
                    }
                }
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    errorText = null
                    statusText = sendingRequest
                    scope.launch {
                        try {
                            val cloudAction = when (request.action) {
                                VolumeAction.Attach -> repository.attachVolume(request.volume.id, selectedServer!!.id)
                                VolumeAction.Detach -> repository.detachVolume(request.volume.id)
                                VolumeAction.Resize -> repository.resizeVolume(request.volume.id, request.volume.sizeGb, parsedSize!!)
                            }
                            awaitCloudAction(repository, cloudAction) { actionStatus ->
                                statusText = context.getString(R.string.action_status_format, powerActionStatusText(actionStatus))
                            }
                            onCompleted()
                        } catch (_: Throwable) {
                            errorText = failureMessage
                            submitting = false
                        }
                    }
                },
                enabled = !submitting && when (request.action) {
                    VolumeAction.Attach -> selectedServer != null
                    VolumeAction.Detach -> true
                    VolumeAction.Resize -> validResize && resizeAcknowledged
                },
            ) { Text(if (submitting) stringResource(R.string.common_in_progress) else title) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Two deliberate steps for a billable resource: fields first, then a review with
 * a second explicit acknowledgement. The Volume is intentionally created
 * unattached, so an attachment has its own separately reviewed workflow.
 */
@Composable
private fun CreateVolumeDialog(
    repository: HCloudRepository,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LocationOption?>(null) }
    var locations by remember { mutableStateOf<List<LocationOption>?>(null) }
    var loadingLocations by remember { mutableStateOf(true) }
    var locationError by remember { mutableStateOf(false) }
    var locationMenuExpanded by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var acknowledged by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val parsedSize = size.toIntOrNull()
    val validForm = name.trim().isNotEmpty() && parsedSize != null && parsedSize > 0 && selectedLocation != null
    fun loadLocations() {
        loadingLocations = true
        locationError = false
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.getLocations() } }
                .onSuccess { values ->
                    locations = values.sortedBy { it.name.lowercase() }
                    loadingLocations = false
                }
                .onFailure {
                    loadingLocations = false
                    locationError = true
                }
        }
    }
    LaunchedEffect(Unit) { loadLocations() }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(if (reviewing) R.string.volume_create_review_title else R.string.volume_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!reviewing) {
                    Text(stringResource(R.string.volume_create_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.volume_name)) },
                        singleLine = true,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.volume_size_gb)) },
                        supportingText = { Text(stringResource(R.string.volume_size_minimum)) },
                        isError = size.isNotBlank() && (parsedSize == null || parsedSize <= 0),
                        singleLine = true,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (loadingLocations) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.volume_locations_loading))
                        }
                    } else if (locationError) {
                        Text(stringResource(R.string.volume_locations_failed), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = ::loadLocations) { Text(stringResource(R.string.common_retry)) }
                    } else {
                        Box {
                            OutlinedButton(
                                onClick = { locationMenuExpanded = true },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(selectedLocation?.displayName() ?: stringResource(R.string.volume_location)) }
                            DropdownMenu(expanded = locationMenuExpanded, onDismissRequest = { locationMenuExpanded = false }) {
                                locations.orEmpty().forEach { location ->
                                    DropdownMenuItem(
                                        text = { Text(location.displayName()) },
                                        onClick = { selectedLocation = location; locationMenuExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.volume_create_review_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.volume_create_review_values, name.trim(), parsedSize!!, selectedLocation!!.name), style = MaterialTheme.typography.titleSmall)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(stringResource(R.string.volume_create_billable_warning), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitting) { acknowledged = !acknowledged },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !submitting)
                        Text(stringResource(R.string.volume_create_acknowledgement))
                    }
                    progress?.let { value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!reviewing) {
                        reviewing = true
                    } else {
                        submitting = true
                        failure = null
                        progress = context.getString(R.string.common_sending_request)
                        scope.launch {
                            try {
                                val created = withContext(Dispatchers.IO) {
                                    repository.createVolume(CreateVolumeSpec(name.trim(), parsedSize!!, selectedLocation!!.name))
                                }
                                created.action?.let { action ->
                                    awaitCloudAction(repository, action) { status ->
                                        progress = context.getString(R.string.action_status_format, powerActionStatusText(status))
                                    }
                                }
                                onCompleted()
                            } catch (throwable: Throwable) {
                                failure = safeApiFailureMessage(
                                    throwable,
                                    context.getString(R.string.volume_create_failed),
                                )
                                submitting = false
                            }
                        }
                    }
                },
                enabled = !submitting && if (reviewing) acknowledged else validForm,
            ) { Text(stringResource(if (reviewing) R.string.volume_create_confirm else R.string.volume_review)) }
        },
        dismissButton = {
            TextButton(
                onClick = { if (reviewing) { reviewing = false; acknowledged = false; failure = null } else onDismiss() },
                enabled = !submitting,
            ) { Text(stringResource(if (reviewing) R.string.common_back else R.string.common_cancel)) }
        },
    )
}

@Composable
private fun DeleteVolumeDialog(
    volume: VolumeInventory,
    repository: HCloudRepository,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typedName by remember(volume.id) { mutableStateOf("") }
    var acknowledged by remember(volume.id) { mutableStateOf(false) }
    var submitting by remember(volume.id) { mutableStateOf(false) }
    var progress by remember(volume.id) { mutableStateOf<String?>(null) }
    var failure by remember(volume.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canDelete = !volume.deleteProtected && typedName == volume.name && acknowledged
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.volume_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.volume_delete_warning, volume.name), color = MaterialTheme.colorScheme.error)
                if (volume.deleteProtected) {
                    Text(stringResource(R.string.volume_delete_protected), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.volume_delete_type_name, volume.name))
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        label = { Text(stringResource(R.string.volume_delete_name_label)) },
                        singleLine = true,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !submitting) { acknowledged = !acknowledged },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !submitting)
                        Text(stringResource(R.string.volume_delete_acknowledgement))
                    }
                }
                progress?.let { value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    failure = null
                    progress = context.getString(R.string.common_sending_request)
                    scope.launch {
                        try {
                            val action = withContext(Dispatchers.IO) { repository.deleteVolume(volume.id) }
                            awaitCloudAction(repository, action) { status ->
                                progress = context.getString(R.string.action_status_format, powerActionStatusText(status))
                            }
                            onCompleted()
                        } catch (throwable: Throwable) {
                            failure = safeApiFailureMessage(
                                throwable,
                                context.getString(R.string.volume_delete_failed),
                            )
                            submitting = false
                        }
                    }
                },
                enabled = !submitting && canDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.volume_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PrimaryIpInventoryScreen(repository: HCloudRepository, projectId: String) {
    val context = LocalContext.current
    var state by remember(projectId) { mutableStateOf<PrimaryIpState>(PrimaryIpState.Loading) }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    var selectedPrimaryIp by remember(projectId) { mutableStateOf<PrimaryIpInventory?>(null) }
    var creatingPrimaryIp by remember(projectId) { mutableStateOf(false) }
    var selectedFloatingIp by remember(projectId) { mutableStateOf<FloatingIpInventory?>(null) }
    var selectedPrivateNetwork by remember(projectId) { mutableStateOf<PrivateNetworkInventory?>(null) }
    var selectedPrivateNetworkDetail by remember(projectId) { mutableStateOf<PrivateNetworkInventory?>(null) }
    var creatingPrivateNetwork by remember(projectId) { mutableStateOf(false) }
    var selectedFirewall by remember(projectId) { mutableStateOf<FirewallInventory?>(null) }
    BackHandler(enabled = selectedFirewall != null) { selectedFirewall = null }
    val scope = rememberCoroutineScope()
    fun load() {
        state = PrimaryIpState.Loading
        scope.launch {
            state = try {
                val inventory = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val ips = async { repository.getPrimaryIps() }
                        val floatingIps = async { repository.getFloatingIps() }
                        val servers = async { repository.getServers() }
                        val firewalls = async { repository.getFirewalls() }
                        val networks = async { repository.getPrivateNetworks() }
                        val loadBalancers = async { repository.getLoadBalancers() }
                        val placementGroups = async { repository.getPlacementGroups() }
                        NetworkInventoryResult(ips.await(), floatingIps.await(), servers.await(), firewalls.await(), networks.await(), loadBalancers.await(), placementGroups.await())
                    }
                }
                PrimaryIpState.Content(ips = inventory.ips, floatingIps = inventory.floatingIps, servers = inventory.servers, firewalls = inventory.firewalls, networks = inventory.networks, loadBalancers = inventory.loadBalancers, placementGroups = inventory.placementGroups)
            } catch (_: Throwable) {
                PrimaryIpState.Error(context.getString(R.string.network_error_message))
            }
        }
    }
    LaunchedEffect(projectId, refreshKey) { load() }
    when (val current = state) {
        PrimaryIpState.Loading -> LoadingView(stringResource(R.string.network_loading))
        is PrimaryIpState.Error -> StateView(stringResource(R.string.network_error_title), current.message, stringResource(R.string.common_retry)) { refreshKey++ }
        is PrimaryIpState.Content -> {
            var firewallAction by remember(projectId) { mutableStateOf<FirewallActionRequest?>(null) }
            var creatingFirewall by remember(projectId) { mutableStateOf(false) }
            selectedFirewall?.let { firewall ->
                FirewallDetailScreen(initial = firewall, repository = repository, onBack = { selectedFirewall = null })
                return
            }
            HCloudPullToRefresh(isRefreshing = false, onRefresh = { refreshKey++ }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = rememberRestorableLazyListState("network"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.network_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.network_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (LocalHighImpactOperations.current.isAllowed) {
                        OutlinedButton(onClick = { creatingPrimaryIp = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.primary_ip_create))
                        }
                        OutlinedButton(onClick = { creatingFirewall = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.firewall_create_action))
                        }
                        OutlinedButton(onClick = { creatingPrivateNetwork = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.network_create))
                        }
                    }
                }
                item {
                    FirewallInventorySection(
                        firewalls = current.firewalls,
                        servers = current.servers,
                        operationsAllowed = LocalHighImpactOperations.current.isAllowed,
                        onSelect = { selectedFirewall = it },
                        onAction = { firewall, action -> firewallAction = FirewallActionRequest(firewall, action) },
                    )
                }
                item { PrivateNetworkInventorySection(current.networks, onManage = { selectedPrivateNetwork = it }, onDetail = { selectedPrivateNetworkDetail = it }) }
                item { LoadBalancerInventorySection(current.loadBalancers) }
                item { PlacementGroupInventorySection(current.placementGroups) }
                item { FloatingIpInventorySection(current.floatingIps, onManage = { selectedFloatingIp = it }) }
                if (current.ips.isEmpty()) item { Text(stringResource(R.string.network_primary_ips_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else items(current.ips.sortedWith(compareBy<PrimaryIpInventory> { it.address }.thenBy { it.id }), key = { it.id }) { ip ->
                    PrimaryIpCard(ip, onManage = { selectedPrimaryIp = ip })
                }
                if (current.ips.isEmpty() && current.floatingIps.isEmpty() && current.firewalls.isEmpty() && current.networks.isEmpty() && current.loadBalancers.isEmpty() && current.placementGroups.isEmpty()) item {
                    StateView(stringResource(R.string.network_empty_title), stringResource(R.string.network_empty_message), stringResource(R.string.common_retry)) { refreshKey++ }
                }
            }
            }
            firewallAction?.let { request ->
                FirewallActionConfirmation(
                    request = request,
                    servers = current.servers,
                    repository = repository,
                    operations = FirewallOperationsUseCase(repository),
                    onCompleted = { firewallAction = null; refreshKey++ },
                    onDismiss = { firewallAction = null },
                )
            }
            if (creatingFirewall) FirewallCreateDialog(
                repository = repository,
                onDismiss = { creatingFirewall = false },
                onCreated = { creatingFirewall = false; refreshKey++ },
            )
        }
    }
    selectedPrimaryIp?.let { ip ->
        PrimaryIpOperationsDialog(
            ip = ip,
            servers = (state as? PrimaryIpState.Content)?.servers.orEmpty(),
            repository = repository,
            onDismiss = { selectedPrimaryIp = null },
            onCompleted = { refreshKey++ },
        )
    }
    if (creatingPrimaryIp) {
        CreatePrimaryIpDialog(repository, (state as? PrimaryIpState.Content)?.servers.orEmpty(),
            onCompleted = { creatingPrimaryIp = false; refreshKey++ }, onDismiss = { creatingPrimaryIp = false })
    }
    if (creatingPrivateNetwork) {
        CreatePrivateNetworkDialog(
            repository = repository,
            onCompleted = { creatingPrivateNetwork = false; refreshKey++ },
            onDismiss = { creatingPrivateNetwork = false },
        )
    }
    selectedFloatingIp?.let { ip ->
        FloatingIpOperationsDialog(
            ip = ip,
            servers = (state as? PrimaryIpState.Content)?.servers.orEmpty(),
            repository = repository,
            onDismiss = { selectedFloatingIp = null },
            onCompleted = { refreshKey++ },
        )
    }
    selectedPrivateNetwork?.let { network ->
        PrivateNetworkOperationsDialog(
            network = network,
            servers = (state as? PrimaryIpState.Content)?.servers.orEmpty(),
            repository = repository,
            onDismiss = { selectedPrivateNetwork = null },
            onCompleted = { refreshKey++ },
        )
    }
    selectedPrivateNetworkDetail?.let { network ->
        NetworkDetailDialog(network, repository, onDismiss = { selectedPrivateNetworkDetail = null })
    }
}

@Composable
private fun PlacementGroupInventorySection(placementGroups: List<PlacementGroupInventory>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.network_placement_groups), style = MaterialTheme.typography.titleLarge)
    if (placementGroups.isEmpty()) {
        Text(stringResource(R.string.common_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        placementGroups.sortedBy { it.name.lowercase() }.forEach { group ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.network_type_format, group.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val servers = group.serverIds.sorted()
                    Text(
                        stringResource(R.string.network_servers_in_group, servers.size, if (servers.isNotEmpty()) " (#${servers.joinToString(", #")})" else ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    group.deleteProtected?.let { protected ->
                        Text(
                            stringResource(if (protected) R.string.common_protected_enabled else R.string.common_protected_disabled),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (group.labels.isNotEmpty()) Text(stringResource(R.string.common_labels_format, group.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerInventorySection(loadBalancers: List<LoadBalancerInventory>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.network_load_balancers), style = MaterialTheme.typography.titleLarge)
    if (loadBalancers.isEmpty()) {
        Text(stringResource(R.string.common_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        loadBalancers.sortedBy { it.name.lowercase() }.forEach { loadBalancer ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(loadBalancer.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.load_balancers_type_location, loadBalancer.type, loadBalancer.location), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val publicInterface = loadBalancer.publicInterface
                    Text(
                        if (publicInterface.enabled) {
                            stringResource(
                                R.string.load_balancers_public_enabled,
                                listOfNotNull(publicInterface.ipv4Address, publicInterface.ipv6Address)
                                    .joinToString()
                                    .ifBlank { stringResource(R.string.load_balancers_public_no_address) },
                            )
                        } else stringResource(R.string.load_balancers_public_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    loadBalancer.privateInterfaces.forEach { privateInterface ->
                        Text(stringResource(R.string.network_private_interface, privateInterface.networkId, privateInterface.ip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.network_services, loadBalancer.services.size), style = MaterialTheme.typography.titleSmall)
                    if (loadBalancer.services.isEmpty()) Text(stringResource(R.string.common_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    loadBalancer.services.forEach { service -> Text("${service.protocol} ${service.listenPort?.toString() ?: "—"} → ${service.destinationPort?.toString() ?: "—"}${if (service.proxyProtocol) " · PROXY" else ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(stringResource(R.string.network_targets, loadBalancer.targets.size), style = MaterialTheme.typography.titleSmall)
                    if (loadBalancer.targets.isEmpty()) Text(stringResource(R.string.common_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    loadBalancer.targets.forEach { target ->
                        val targetName = target.serverId?.let { stringResource(R.string.load_balancers_target_server, it) }
                            ?: target.labelSelector?.let { stringResource(R.string.load_balancers_target_label_selector, it) }
                            ?: target.ip
                            ?: target.type
                        val health = target.healthStatuses.joinToString { "${it.listenPort}: ${it.status}" }
                        val label = buildString {
                            append(targetName)
                            if (target.usePrivateIp) append(" · ${stringResource(R.string.network_private_ip)}")
                            if (health.isNotBlank()) append(" · $health")
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(if (loadBalancer.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled), style = MaterialTheme.typography.labelMedium, color = if (loadBalancer.deleteProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (loadBalancer.labels.isNotEmpty()) Text(stringResource(R.string.common_labels_format, loadBalancer.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PrivateNetworkInventorySection(networks: List<PrivateNetworkInventory>, onManage: (PrivateNetworkInventory) -> Unit, onDetail: (PrivateNetworkInventory) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.network_private_networks), style = MaterialTheme.typography.titleLarge)
    if (networks.isEmpty()) {
        Text(stringResource(R.string.common_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        networks.sortedBy { it.name.lowercase() }.forEach { network ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(network.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.network_cidr, network.ipRange), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.network_servers_attached, network.serverIds.size, if (network.serverIds.isNotEmpty()) " (#${network.serverIds.joinToString(", #")})" else ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (network.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled), style = MaterialTheme.typography.labelMedium, color = if (network.deleteProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (network.labels.isNotEmpty()) Text(stringResource(R.string.common_labels_format, network.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.network_subnets, network.subnets.size), style = MaterialTheme.typography.titleSmall)
                    if (network.subnets.isEmpty()) Text(stringResource(R.string.common_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    network.subnets.forEach { subnet ->
                        Text(
                            stringResource(
                                R.string.network_subnet_detail,
                                subnet.type,
                                subnet.ipRange,
                                subnet.networkZone,
                                subnet.gateway?.let { stringResource(R.string.network_gateway, it) }.orEmpty(),
                                subnet.vswitchId?.let { stringResource(R.string.network_vswitch, it) }.orEmpty(),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.network_routes, network.routes.size), style = MaterialTheme.typography.titleSmall)
                    if (network.routes.isEmpty()) Text(stringResource(R.string.common_none), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    network.routes.forEach { route -> Text("${route.destination} → ${route.gateway}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { onDetail(network) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_detail)) }
                    if (LocalHighImpactOperations.current.isAllowed) {
                        OutlinedButton(onClick = { onManage(network) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_manage_servers)) }
                    }
                }
            }
        }
    }
}

private enum class FirewallAction { Apply, Remove }
private data class FirewallActionRequest(val firewall: FirewallInventory, val action: FirewallAction)

@Composable
private fun FirewallInventorySection(
    firewalls: List<FirewallInventory>,
    servers: List<Server>,
    operationsAllowed: Boolean,
    onSelect: (FirewallInventory) -> Unit,
    onAction: (FirewallInventory, FirewallAction) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.network_firewalls), style = MaterialTheme.typography.titleLarge)
    if (firewalls.isEmpty()) {
        Text(stringResource(R.string.common_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        firewalls.sortedBy { it.name.lowercase() }.forEach { firewall ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(firewall) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(firewall.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.network_firewall_rules, firewall.rules.size, firewall.appliedTo.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.firewall_detail_open), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (firewall.labels.isNotEmpty()) Text(stringResource(R.string.common_labels_format, firewall.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    firewall.rules.forEach { rule ->
                        val port = rule.port?.let { ":$it" }.orEmpty()
                        val source = rule.sourceIps.takeIf { it.isNotEmpty() }?.joinToString() ?: stringResource(R.string.network_any_address)
                        val destination = rule.destinationIps.takeIf { it.isNotEmpty() }?.joinToString() ?: stringResource(R.string.network_any_address)
                        Text("${rule.direction.uppercase()} ${rule.protocol}$port · $source → $destination", style = MaterialTheme.typography.labelMedium)
                        rule.description?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    firewall.appliedTo.forEach { applied ->
                        val target = when {
                            applied.serverId != null -> "server #${applied.serverId}"
                            applied.labelSelector != null -> "label selector: ${applied.labelSelector}"
                            else -> applied.type
                        }
                        val matches = applied.appliedToResources.size
                        Text(stringResource(R.string.network_firewall_applied_to, target, if (matches > 0) stringResource(R.string.network_firewall_matches, matches) else ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (operationsAllowed) {
                        val directlyAppliedIds = firewall.appliedTo.mapNotNull { it.serverId }.toSet()
                        val canApply = servers.any { it.id !in directlyAppliedIds }
                        val canRemove = servers.any { it.id in directlyAppliedIds }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onAction(firewall, FirewallAction.Apply) },
                            enabled = canApply,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.network_apply_firewall)) }
                        OutlinedButton(
                            onClick = { onAction(firewall, FirewallAction.Remove) },
                            enabled = canRemove,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.network_remove_firewall)) }
                        if (!canApply && !canRemove) {
                            Text(stringResource(R.string.network_no_servers_firewall), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirewallActionConfirmation(
    request: FirewallActionRequest,
    servers: List<Server>,
    repository: HCloudRepository,
    operations: FirewallOperationsUseCase,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val directlyAppliedIds = remember(request.firewall) { request.firewall.appliedTo.mapNotNull { it.serverId }.toSet() }
    val eligibleServers = remember(request, servers) {
        servers.filter { server ->
            when (request.action) {
                FirewallAction.Apply -> server.id !in directlyAppliedIds
                FirewallAction.Remove -> server.id in directlyAppliedIds
            }
        }.sortedBy { it.name.lowercase() }
    }
    var selectedServer by remember(request) { mutableStateOf<Server?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isRemoval = request.action == FirewallAction.Remove
    val context = LocalContext.current
    val actionTitle = stringResource(if (isRemoval) R.string.network_firewall_remove_title else R.string.network_firewall_apply_title)
    val actionNote = stringResource(if (isRemoval) R.string.network_firewall_remove_note else R.string.network_firewall_apply_note)
    val actionFailure = stringResource(if (isRemoval) R.string.network_firewall_remove_failed else R.string.network_firewall_apply_failed)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.common_title_with_name, actionTitle, request.firewall.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(actionNote)
                if (eligibleServers.isEmpty()) {
                    Text(stringResource(R.string.network_no_servers_action), color = MaterialTheme.colorScheme.error)
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(selectedServer?.let { stringResource(R.string.common_name_with_detail, it.name, it.location) } ?: stringResource(R.string.common_select_server)) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            eligibleServers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_name_with_detail, server.name, server.location)) },
                                    onClick = { selectedServer = server; expanded = false },
                                )
                            }
                        }
                    }
                }
                if (submitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val server = selectedServer ?: return@Button
                    submitting = true
                    errorText = null
                    statusText = context.getString(R.string.common_sending_request)
                    scope.launch {
                        try {
                            val actions = if (isRemoval) {
                                operations.removeFromServer(request.firewall.id, server.id)
                            } else {
                                operations.applyToServer(request.firewall.id, server.id)
                            }
                            actions.forEach { action ->
                                awaitCloudAction(repository, action) { status ->
                                    statusText = context.getString(R.string.action_status_format, powerActionStatusText(status))
                                }
                            }
                            onCompleted()
                        } catch (_: Throwable) {
                            errorText = actionFailure
                            submitting = false
                        }
                    }
                },
                enabled = !submitting && selectedServer != null,
                colors = if (isRemoval) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(if (submitting) stringResource(R.string.common_in_progress) else actionTitle) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun CreatePrivateNetworkDialog(
    repository: HCloudRepository,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // A network range is an infrastructure decision: require the user to enter it
    // explicitly instead of silently provisioning an assumed address space.
    var range by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val canSubmit = highImpactAllowed && name.isNotBlank() && range.isNotBlank() && acknowledged && !submitting
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.network_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.network_create_warning), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_create_name)) }, singleLine = true, enabled = !submitting)
                OutlinedTextField(range, { range = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_create_range)) }, singleLine = true, enabled = !submitting)
                Row(Modifier.fillMaxWidth().clickable(enabled = !submitting) { acknowledged = !acknowledged }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !submitting)
                    Text(stringResource(R.string.network_create_acknowledgement))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!highImpactAllowed) return@Button
                submitting = true; failure = null
                scope.launch {
                    try {
                        val created = withContext(Dispatchers.IO) { repository.createPrivateNetwork(CreatePrivateNetworkSpec(name.trim(), range.trim())) }
                        awaitCloudAction(repository, created.action) { }
                        onCompleted()
                    } catch (error: Throwable) {
                        failure = safeApiFailureMessage(error, context.getString(R.string.network_create_failed))
                        submitting = false
                    }
                }
            }, enabled = canSubmit) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.network_create_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private enum class PrivateNetworkOperation { Overview, Attach, Detach, Topology, Delete }
private enum class PrivateNetworkTopologyOperation { AddSubnet, DeleteSubnet, AddRoute, DeleteRoute }

/** Existing-network-only flow: no subnet, route, IP, alias or topology changes are exposed. */
@Composable
private fun PrivateNetworkOperationsDialog(
    network: PrivateNetworkInventory,
    servers: List<Server>,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var operation by remember(network.id) { mutableStateOf(PrivateNetworkOperation.Overview) }
    var selectedServerId by remember(network.id) { mutableStateOf<Long?>(null) }
    var typedNetworkName by remember(network.id) { mutableStateOf("") }
    var topologyOperation by remember(network.id) { mutableStateOf<PrivateNetworkTopologyOperation?>(null) }
    var topologyRange by remember(network.id) { mutableStateOf("") }
    var topologyZone by remember(network.id) { mutableStateOf("eu-central") }
    var topologyGateway by remember(network.id) { mutableStateOf("") }
    var topologyDestination by remember(network.id) { mutableStateOf("") }
    var submitting by remember(network.id) { mutableStateOf(false) }
    var progressText by remember(network.id) { mutableStateOf<String?>(null) }
    var errorText by remember(network.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val privateNetworkOperations = remember(repository) { PrivateNetworkAttachmentUseCase(repository) }
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val context = LocalContext.current
    val attachCandidates = servers.filter { it.id !in network.serverIds }
    val detachCandidates = servers.filter { it.id in network.serverIds }
    val sendingRequest = stringResource(R.string.common_sending_request)
    val actionCompleted = stringResource(R.string.network_action_completed)
    val mutationFailed = stringResource(R.string.network_mutation_failed)

    fun runMutation(action: suspend () -> com.hcloudcontrol.core.domain.model.CloudAction) {
        // A dialog can remain composed while the device-local safety setting is
        // disabled elsewhere. Re-check it at execution time so every network
        // mutation (including attach/detach) has the same boundary as topology
        // and deletion operations.
        if (!highImpactAllowed) return
        submitting = true
        errorText = null
        progressText = sendingRequest
        scope.launch {
            try {
                val cloudAction = action()
                awaitCloudAction(repository, cloudAction) { status ->
                    progressText = context.getString(R.string.action_status_format, powerActionStatusText(status))
                }
                progressText = actionCompleted
                onCompleted()
            } catch (_: Throwable) {
                errorText = mutationFailed
            } finally {
                submitting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.network_private_title, network.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(network.ipRange)
                when (operation) {
                    PrivateNetworkOperation.Overview -> {
                        Text(stringResource(R.string.network_private_overview), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.network_private_internet_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (highImpactAllowed) {
                            OutlinedButton(onClick = { operation = PrivateNetworkOperation.Attach }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_attach_server)) }
                            OutlinedButton(onClick = { operation = PrivateNetworkOperation.Detach }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_detach_server)) }
                            OutlinedButton(onClick = { operation = PrivateNetworkOperation.Topology }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_topology)) }
                            OutlinedButton(
                                onClick = { operation = PrivateNetworkOperation.Delete },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.network_delete)) }
                        }
                    }
                    PrivateNetworkOperation.Attach -> {
                        if (network.subnets.none { it.type == "cloud" }) {
                            Text(stringResource(R.string.network_no_cloud_subnet), color = MaterialTheme.colorScheme.error)
                        } else if (attachCandidates.isEmpty()) {
                            Text(stringResource(R.string.network_all_servers_attached), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(stringResource(R.string.network_choose_server_auto_ip))
                            attachCandidates.sortedBy { it.name.lowercase() }.forEach { server ->
                                NetworkServerChoice(server, selectedServerId == server.id, !submitting) { selectedServerId = server.id }
                            }
                        }
                    }
                    PrivateNetworkOperation.Detach -> {
                        if (detachCandidates.isEmpty()) {
                            Text(stringResource(R.string.network_no_attached_servers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(stringResource(R.string.network_detach_warning), color = MaterialTheme.colorScheme.error)
                            detachCandidates.sortedBy { it.name.lowercase() }.forEach { server ->
                                NetworkServerChoice(server, selectedServerId == server.id, !submitting) { selectedServerId = server.id }
                            }
                        }
                    }
                    PrivateNetworkOperation.Topology -> {
                        val selected = topologyOperation
                        Text(stringResource(R.string.network_topology_warning), color = MaterialTheme.colorScheme.error)
                        if (selected == null) {
                            OutlinedButton(onClick = { topologyOperation = PrivateNetworkTopologyOperation.AddSubnet }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_add_subnet)) }
                            OutlinedButton(onClick = { topologyOperation = PrivateNetworkTopologyOperation.DeleteSubnet }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_delete_subnet)) }
                            OutlinedButton(onClick = { topologyOperation = PrivateNetworkTopologyOperation.AddRoute }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_add_route)) }
                            OutlinedButton(onClick = { topologyOperation = PrivateNetworkTopologyOperation.DeleteRoute }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_delete_route)) }
                        } else if (selected == PrivateNetworkTopologyOperation.AddSubnet || selected == PrivateNetworkTopologyOperation.DeleteSubnet) {
                            OutlinedTextField(topologyRange, { topologyRange = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_subnet_range)) }, singleLine = true, enabled = !submitting)
                            OutlinedTextField(topologyZone, { topologyZone = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_subnet_zone)) }, singleLine = true, enabled = !submitting)
                            OutlinedTextField(topologyGateway, { topologyGateway = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_subnet_gateway_optional)) }, singleLine = true, enabled = !submitting)
                        } else {
                            OutlinedTextField(topologyDestination, { topologyDestination = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_route_destination)) }, singleLine = true, enabled = !submitting)
                            OutlinedTextField(topologyGateway, { topologyGateway = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.network_route_gateway)) }, singleLine = true, enabled = !submitting)
                        }
                    }
                    PrivateNetworkOperation.Delete -> {
                        Text(stringResource(R.string.network_delete_warning, network.name), color = MaterialTheme.colorScheme.error)
                        if (network.deleteProtected) {
                            Text(stringResource(R.string.network_delete_protected), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.network_delete_type_name, network.name))
                            OutlinedTextField(
                                value = typedNetworkName,
                                onValueChange = { typedNetworkName = it },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !submitting,
                                singleLine = true,
                                label = { Text(stringResource(R.string.network_delete_name_label)) },
                            )
                        }
                    }
                }
                progressText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (operation) {
                PrivateNetworkOperation.Overview -> TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_close)) }
                PrivateNetworkOperation.Attach -> Button(
                    onClick = { selectedServerId?.let { id -> attachCandidates.firstOrNull { it.id == id }?.let { server -> runMutation { privateNetworkOperations.attach(network, server) } } } },
                    enabled = highImpactAllowed && selectedServerId != null && network.subnets.any { it.type == "cloud" } && !submitting,
                ) { Text(if (submitting) stringResource(R.string.network_attaching) else stringResource(R.string.network_confirm_attach)) }
                PrivateNetworkOperation.Detach -> Button(
                    onClick = { selectedServerId?.let { id -> detachCandidates.firstOrNull { it.id == id }?.let { server -> runMutation { privateNetworkOperations.detach(network, server) } } } },
                    enabled = highImpactAllowed && selectedServerId != null && !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(if (submitting) stringResource(R.string.network_detaching) else stringResource(R.string.network_confirm_detach)) }
                PrivateNetworkOperation.Topology -> {
                    val selected = topologyOperation
                    if (selected == null) TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_close)) }
                    else {
                        val valid = when (selected) {
                            PrivateNetworkTopologyOperation.AddSubnet, PrivateNetworkTopologyOperation.DeleteSubnet -> topologyRange.isNotBlank() && topologyZone.isNotBlank()
                            PrivateNetworkTopologyOperation.AddRoute, PrivateNetworkTopologyOperation.DeleteRoute -> topologyDestination.isNotBlank() && topologyGateway.isNotBlank()
                        }
                        Button(onClick = {
                            val gateway = topologyGateway.trim().takeIf { it.isNotEmpty() }
                            when (selected) {
                                PrivateNetworkTopologyOperation.AddSubnet -> runMutation { repository.addPrivateNetworkSubnet(network.id, com.hcloudcontrol.core.domain.model.PrivateNetworkSubnetSpec(topologyRange.trim(), topologyZone.trim(), gateway)) }
                                PrivateNetworkTopologyOperation.DeleteSubnet -> runMutation { repository.deletePrivateNetworkSubnet(network.id, com.hcloudcontrol.core.domain.model.PrivateNetworkSubnetSpec(topologyRange.trim(), topologyZone.trim(), gateway)) }
                                PrivateNetworkTopologyOperation.AddRoute -> runMutation { repository.addPrivateNetworkRoute(network.id, com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec(topologyDestination.trim(), topologyGateway.trim())) }
                                PrivateNetworkTopologyOperation.DeleteRoute -> runMutation { repository.deletePrivateNetworkRoute(network.id, com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec(topologyDestination.trim(), topologyGateway.trim())) }
                            }
                        }, enabled = highImpactAllowed && valid && !submitting) { Text(stringResource(R.string.network_topology_confirm)) }
                    }
                }
                PrivateNetworkOperation.Delete -> {
                    // The actual typed confirmation is deliberately separate from the global setting gate.
                    Button(
                        onClick = { if (highImpactAllowed && typedNetworkName == network.name) runMutation { repository.deletePrivateNetwork(network.id) } },
                        enabled = !submitting && !network.deleteProtected && highImpactAllowed && typedNetworkName == network.name,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    ) { Text(if (submitting) stringResource(R.string.common_in_progress) else stringResource(R.string.network_delete_confirm)) }
                }
            }
        },
        dismissButton = {
            if (operation != PrivateNetworkOperation.Overview) {
                TextButton(onClick = { operation = PrivateNetworkOperation.Overview; selectedServerId = null; topologyOperation = null; errorText = null; progressText = null }, enabled = !submitting) { Text(stringResource(R.string.common_back_action)) }
            }
        },
    )
}

@Composable
private fun NetworkServerChoice(server: Server, selected: Boolean, enabled: Boolean, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
) { Text(stringResource(R.string.network_server_choice, if (selected) "✓ " else "", server.name, server.id, server.location)) }

@Composable
private fun FloatingIpInventorySection(floatingIps: List<FloatingIpInventory>, onManage: (FloatingIpInventory) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.network_floating_ips), style = MaterialTheme.typography.titleLarge)
    if (floatingIps.isEmpty()) {
        Text(stringResource(R.string.network_floating_ips_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        floatingIps.sortedWith(compareBy<FloatingIpInventory> { it.address }.thenBy { it.id }).forEach { ip ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val assigneeId = ip.assigneeId
                    Text(ip.name ?: stringResource(R.string.floating_ip_unnamed), style = MaterialTheme.typography.titleMedium)
                    Text("${ip.address} · ${ip.type}${ip.homeLocation?.let { " · $it" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (assigneeId == null) stringResource(R.string.floating_ip_unassigned)
                        else stringResource(R.string.floating_ip_assigned, ip.assigneeType ?: "resource", assigneeId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ip.dnsPointers.forEach { pointer ->
                        Text(stringResource(R.string.floating_ip_reverse_dns_value, pointer.ip, pointer.dnsPtr ?: stringResource(R.string.floating_ip_ptr_not_set)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { onManage(ip) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.floating_ip_manage)) }
                }
            }
        }
    }
}

private enum class FloatingIpOperation { Overview, Assign, Unassign, ReverseDns, Delete }

/** A read-only paginated history restricted to the selected Floating IP. */
private sealed interface FloatingIpActionHistoryState {
    data object Loading : FloatingIpActionHistoryState
    data class Content(val actions: List<CloudAction>) : FloatingIpActionHistoryState
    data class Error(val message: String) : FloatingIpActionHistoryState
}

/**
 * The API permits reassignment of a Floating IP without the Primary-IP power or location
 * preconditions. The dialog therefore intentionally offers all existing servers and relies on
 * Hetzner Cloud to validate the final assignment.
 */
@Composable
private fun FloatingIpOperationsDialog(
    ip: FloatingIpInventory,
    servers: List<Server>,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var operation by remember(ip.id) { mutableStateOf(FloatingIpOperation.Overview) }
    var selectedServerId by remember(ip.id) { mutableStateOf<Long?>(null) }
    var selectedPointerIp by remember(ip.id) { mutableStateOf<String?>(null) }
    var dnsPtr by remember(ip.id) { mutableStateOf("") }
    var deletionText by remember(ip.id) { mutableStateOf("") }
    var ptrReview by remember(ip.id) { mutableStateOf(false) }
    var deleteConfirmation by remember(ip.id) { mutableStateOf("") }
    var submitting by remember(ip.id) { mutableStateOf(false) }
    var progressText by remember(ip.id) { mutableStateOf<String?>(null) }
    var errorText by remember(ip.id) { mutableStateOf<String?>(null) }
    var historyState by remember(ip.id) { mutableStateOf<FloatingIpActionHistoryState>(FloatingIpActionHistoryState.Loading) }
    var historyLoadGeneration by remember(ip.id) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val operations = remember(repository) { FloatingIpOperationsUseCase(repository) }
    val context = LocalContext.current
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val pointers = ip.dnsPointers.ifEmpty { listOf(com.hcloudcontrol.core.domain.model.DnsPointer(ip.address, null)) }
    val historyLoadFailed = stringResource(R.string.floating_ip_action_history_load_failed)

    fun refreshHistory() {
        val generation = ++historyLoadGeneration
        historyState = FloatingIpActionHistoryState.Loading
        scope.launch {
            val result = try {
                FloatingIpActionHistoryState.Content(
                    withContext(Dispatchers.IO) { repository.getFloatingIpActions(ip.id) },
                )
            } catch (error: Throwable) {
                FloatingIpActionHistoryState.Error(safeApiFailureMessage(error, historyLoadFailed))
            }
            if (generation == historyLoadGeneration) historyState = result
        }
    }
    LaunchedEffect(ip.id) { refreshHistory() }

    fun runMutation(action: suspend () -> com.hcloudcontrol.core.domain.model.CloudAction) {
        if (!highImpactAllowed) {
            errorText = context.getString(R.string.floating_ip_operations_not_allowed)
            return
        }
        submitting = true
        errorText = null
        progressText = context.getString(R.string.common_sending_request)
        scope.launch {
            try {
                awaitCloudAction(repository, action()) { status ->
                    progressText = context.getString(R.string.action_status_format, powerActionStatusText(status))
                }
                progressText = context.getString(R.string.network_action_completed)
                onCompleted()
                refreshHistory()
            } catch (_: Throwable) {
                // API error details may contain server configuration. Keep a stable safe message.
                errorText = context.getString(R.string.floating_ip_mutation_failed)
            } finally {
                submitting = false
            }
        }
    }

    /** DELETE /floating_ips/{id} has no Action response; re-read detail to make the
     * documented unassigned precondition authoritative immediately before the request. */
    fun runDelete() {
        if (!highImpactAllowed) {
            errorText = context.getString(R.string.floating_ip_operations_not_allowed)
            return
        }
        submitting = true
        errorText = null
        progressText = context.getString(R.string.common_sending_request)
        scope.launch {
            try {
                val currentIp = withContext(Dispatchers.IO) { repository.getFloatingIp(ip.id) }
                operations.deleteUnassigned(currentIp)
                progressText = context.getString(R.string.floating_ip_delete_completed)
                onCompleted()
            } catch (error: Throwable) {
                errorText = safeApiFailureMessage(error, context.getString(R.string.floating_ip_delete_failed))
            } finally {
                submitting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.floating_ip_title, ip.name ?: ip.address)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${ip.address} · ${ip.type}${ip.homeLocation?.let { " · $it" }.orEmpty()}")
                when (operation) {
                    FloatingIpOperation.Overview -> {
                        val assigneeId = ip.assigneeId
                        Text(if (assigneeId == null) stringResource(R.string.floating_ip_unassigned) else stringResource(R.string.floating_ip_assigned, ip.assigneeType ?: "resource", assigneeId), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (highImpactAllowed) {
                            Text(stringResource(R.string.floating_ip_transfer_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (servers.isEmpty()) Text(stringResource(R.string.floating_ip_no_servers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else OutlinedButton(onClick = { operation = FloatingIpOperation.Assign }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.floating_ip_assign)) }
                            if (ip.assigneeId != null) OutlinedButton(onClick = { operation = FloatingIpOperation.Unassign }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.floating_ip_unassign)) }
                            OutlinedButton(onClick = { operation = FloatingIpOperation.ReverseDns }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.floating_ip_reverse_dns)) }
                            ResourceProtectionControls(
                                resourceName = ip.name ?: ip.address,
                                deleteProtected = ip.deleteProtected,
                                repository = repository,
                                onChange = { patch -> repository.changeFloatingIpProtection(ip.id, patch) },
                                onCompleted = { onCompleted(); refreshHistory() },
                            )
                        }
                        // This destructive, billable-resource operation is not reachable until the
                        // user has explicitly allowed high-impact operations in Settings.
                        if (highImpactAllowed && ip.assigneeId == null && !ip.deleteProtected) {
                            OutlinedButton(
                                onClick = { operation = FloatingIpOperation.Delete; errorText = null; progressText = null },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.floating_ip_delete)) }
                        }
                        Text(stringResource(R.string.floating_ip_action_history_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.floating_ip_action_history_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (val currentHistory = historyState) {
                            FloatingIpActionHistoryState.Loading -> ObservabilityLoading(stringResource(R.string.floating_ip_action_history_loading))
                            is FloatingIpActionHistoryState.Error -> ObservabilityError(currentHistory.message, ::refreshHistory)
                            is FloatingIpActionHistoryState.Content -> FloatingIpActionHistory(currentHistory.actions)
                        }
                    }
                    FloatingIpOperation.Assign -> {
                        Text(stringResource(R.string.floating_ip_choose_server))
                        servers.sortedBy { it.name.lowercase() }.forEach { server ->
                            NetworkServerChoice(server, selectedServerId == server.id, !submitting) { selectedServerId = server.id }
                        }
                    }
                    FloatingIpOperation.Unassign -> {
                        Text(stringResource(R.string.floating_ip_unassign_warning), color = MaterialTheme.colorScheme.error)
                    }
                    FloatingIpOperation.ReverseDns -> {
                        Text(stringResource(R.string.floating_ip_ptr_intro))
                        pointers.forEach { pointer ->
                            OutlinedButton(onClick = { selectedPointerIp = pointer.ip; dnsPtr = pointer.dnsPtr.orEmpty(); ptrReview = false }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.common_selected_value, if (selectedPointerIp == pointer.ip) "✓ " else "", stringResource(R.string.floating_ip_ptr_value, pointer.ip, pointer.dnsPtr ?: stringResource(R.string.floating_ip_ptr_not_set))))
                            }
                        }
                        selectedPointerIp?.let {
                            OutlinedTextField(value = dnsPtr, onValueChange = { dnsPtr = it; ptrReview = false }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.floating_ip_ptr_hostname)) }, singleLine = true, enabled = !submitting)
                        }
                        if (ptrReview) selectedPointerIp?.let { pointerIp ->
                            Text(stringResource(R.string.floating_ip_ptr_confirm_message, pointerIp, dnsPtr.trim().ifBlank { stringResource(R.string.floating_ip_ptr_clear_value) }), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    FloatingIpOperation.Delete -> {
                        Text(stringResource(R.string.floating_ip_delete_warning), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.floating_ip_delete_resource, ip.address, ip.type, ip.homeLocation ?: stringResource(R.string.common_none)))
                        Text(stringResource(R.string.floating_ip_delete_charge_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = deleteConfirmation,
                            onValueChange = { deleteConfirmation = it; errorText = null },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !submitting,
                            singleLine = true,
                            label = { Text(stringResource(R.string.floating_ip_delete_confirmation_label, ip.address)) },
                        )
                    }
                }
                progressText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (operation) {
                FloatingIpOperation.Overview -> TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_close)) }
                FloatingIpOperation.Assign -> Button(onClick = { selectedServerId?.let { id -> servers.firstOrNull { it.id == id }?.let { target -> runMutation { operations.assign(ip, target) } } } }, enabled = highImpactAllowed && selectedServerId != null && !submitting) { Text(if (submitting) stringResource(R.string.floating_ip_assigning) else stringResource(R.string.floating_ip_confirm_assign)) }
                FloatingIpOperation.Unassign -> Button(onClick = { runMutation { operations.unassign(ip) } }, enabled = highImpactAllowed && !submitting, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(if (submitting) stringResource(R.string.floating_ip_unassigning) else stringResource(R.string.floating_ip_confirm_unassign)) }
                FloatingIpOperation.ReverseDns -> Button(onClick = { selectedPointerIp?.let { address -> if (ptrReview) runMutation { operations.changeReverseDns(ip, address, dnsPtr) } else ptrReview = true } }, enabled = highImpactAllowed && selectedPointerIp != null && !submitting) { Text(if (submitting) stringResource(R.string.floating_ip_saving) else if (ptrReview) stringResource(R.string.floating_ip_confirm_ptr) else stringResource(R.string.floating_ip_review_ptr)) }
                FloatingIpOperation.Delete -> Button(
                    onClick = ::runDelete,
                    enabled = highImpactAllowed && !submitting && deleteConfirmation.trim() == ip.address,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(if (submitting) stringResource(R.string.floating_ip_deleting) else stringResource(R.string.floating_ip_confirm_delete)) }
            }
        },
        dismissButton = {
            if (operation != FloatingIpOperation.Overview) TextButton(onClick = { operation = FloatingIpOperation.Overview; selectedServerId = null; errorText = null; progressText = null; ptrReview = false; deleteConfirmation = "" }, enabled = !submitting) { Text(stringResource(R.string.common_back_action)) }
        },
    )
}

@Composable
private fun FloatingIpActionHistory(actions: List<CloudAction>) {
    if (actions.isEmpty()) {
        Text(stringResource(R.string.floating_ip_action_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.sortedByDescending { it.id }.forEach { action ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(action.command ?: stringResource(R.string.floating_ip_action_unknown), style = MaterialTheme.typography.titleSmall)
                    DetailRow(stringResource(R.string.server_action_status), action.status)
                    action.progress?.let { DetailRow(stringResource(R.string.server_action_progress), "$it%") }
                    action.started?.let { DetailRow(stringResource(R.string.server_action_started), it) }
                    action.finished?.let { DetailRow(stringResource(R.string.server_action_finished), it) }
                    action.errorCode?.let { DetailRow(stringResource(R.string.server_action_error_code), it) }
                }
            }
        }
    }
}

@Composable
private fun CreatePrimaryIpDialog(
    repository: HCloudRepository,
    knownServers: List<Server>,
    onCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ipv4") }
    var labelsText by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LocationOption?>(null) }
    var selectedAssignee by remember { mutableStateOf<Server?>(null) }
    var locations by remember { mutableStateOf<List<LocationOption>>(emptyList()) }
    var locationsLoading by remember { mutableStateOf(true) }
    var review by remember { mutableStateOf(false) }
    var acknowledged by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var locationMenu by remember { mutableStateOf(false) }
    var serverMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val labels = parsePrimaryIpLabels(labelsText)
    val allowed = LocalHighImpactOperations.current.isAllowed
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { repository.getLocations() } }
            .onSuccess { locations = it.sortedBy { location -> location.name.lowercase() } }
            .onFailure { errorText = context.getString(R.string.primary_ip_locations_failed) }
        locationsLoading = false
    }
    val eligibleServers = selectedLocation?.let { location -> knownServers.filter { it.location == location.name }.sortedBy { it.name.lowercase() } }.orEmpty()
    val valid = allowed && name.trim().isNotEmpty() && selectedLocation != null && labels != null
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(if (review) R.string.primary_ip_create_review_title else R.string.primary_ip_create_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!review) {
                    Text(stringResource(R.string.primary_ip_create_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.primary_ip_create_name)) }, singleLine = true, enabled = !submitting)
                    Text(stringResource(R.string.primary_ip_create_type), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ipv4", "ipv6").forEach { candidate ->
                            FilterChip(selected = type == candidate, onClick = { type = candidate }, enabled = !submitting, label = { Text(candidate.uppercase()) })
                        }
                    }
                    if (locationsLoading) CircularProgressIndicator(Modifier.size(20.dp)) else Box {
                        OutlinedButton(onClick = { locationMenu = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(selectedLocation?.displayName() ?: stringResource(R.string.primary_ip_create_location)) }
                        DropdownMenu(locationMenu, { locationMenu = false }) { locations.forEach { location -> DropdownMenuItem(text = { Text(location.displayName()) }, onClick = { selectedLocation = location; selectedAssignee = null; locationMenu = false }) } }
                    }
                    selectedLocation?.let {
                        Box {
                            OutlinedButton(onClick = { serverMenu = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(selectedAssignee?.let { server -> "${server.name} (#${server.id})" } ?: stringResource(R.string.primary_ip_create_unassigned)) }
                            DropdownMenu(serverMenu, { serverMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.primary_ip_create_unassigned)) }, onClick = { selectedAssignee = null; serverMenu = false })
                                eligibleServers.forEach { server -> DropdownMenuItem(text = { Text("${server.name} (#${server.id})") }, onClick = { selectedAssignee = server; serverMenu = false }) }
                            }
                        }
                    }
                    OutlinedTextField(labelsText, { labelsText = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.primary_ip_create_labels)) }, supportingText = { Text(stringResource(R.string.primary_ip_create_labels_hint)) }, enabled = !submitting, isError = labelsText.isNotBlank() && labels == null)
                } else {
                    Text(stringResource(R.string.primary_ip_create_review_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DetailRow(stringResource(R.string.primary_ip_create_name), name.trim())
                    DetailRow(stringResource(R.string.primary_ip_create_type), type.uppercase())
                    DetailRow(stringResource(R.string.primary_ip_create_location), selectedLocation!!.displayName())
                    DetailRow(stringResource(R.string.primary_ip_create_assignee), selectedAssignee?.let { "${it.name} (#${it.id})" } ?: stringResource(R.string.primary_ip_create_unassigned))
                    DetailRow(stringResource(R.string.primary_ip_create_labels), labels!!.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { stringResource(R.string.common_none) })
                    Text(stringResource(R.string.primary_ip_create_price_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.clickable(enabled = !submitting) { acknowledged = !acknowledged }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(acknowledged, { acknowledged = it }, enabled = !submitting)
                        Text(stringResource(R.string.primary_ip_create_acknowledgement))
                    }
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            if (!review) review = true else if (allowed) {
                submitting = true; errorText = null
                scope.launch {
                    try {
                        val created = withContext(Dispatchers.IO) { repository.createPrimaryIp(CreatePrimaryIpSpec(type, name.trim(), selectedLocation!!.name, selectedAssignee?.id, labels!!)) }
                        created.action?.let { action -> awaitCloudAction(repository, action) { } }
                        onCompleted()
                    } catch (error: Throwable) { errorText = safeApiFailureMessage(error, context.getString(R.string.primary_ip_create_failed)); submitting = false }
                }
            }
        }, enabled = !submitting && if (review) acknowledged && allowed else valid) { Text(stringResource(if (review) R.string.primary_ip_create_confirm else R.string.primary_ip_review)) } },
        dismissButton = { TextButton(onClick = { if (review) { review = false; acknowledged = false } else onDismiss() }, enabled = !submitting) { Text(stringResource(if (review) R.string.common_back else R.string.common_cancel)) } },
    )
}

private fun parsePrimaryIpLabels(text: String): Map<String, String>? = buildMap {
    text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val pivot = line.indexOf('=')
        if (pivot <= 0 || pivot == line.lastIndex) return null
        val key = line.substring(0, pivot).trim(); val value = line.substring(pivot + 1).trim()
        if (key.isBlank() || value.isBlank() || put(key, value) != null) return null
    }
}

private enum class PrimaryIpOperation { Overview, Assign, Unassign, ReverseDns, Delete }

/**
 * Primary IP assignment is intentionally constrained to an explicit, stopped server in the
 * same Location. Hetzner validates the final state again; this UI preflight avoids offering
 * an operation that will certainly interrupt connectivity.
 */
@Composable
private fun PrimaryIpOperationsDialog(
    ip: PrimaryIpInventory,
    servers: List<Server>,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var operation by remember(ip.id) { mutableStateOf(PrimaryIpOperation.Overview) }
    var selectedServerId by remember(ip.id) { mutableStateOf<Long?>(null) }
    var selectedPointerIp by remember(ip.id) { mutableStateOf<String?>(null) }
    var dnsPtr by remember(ip.id) { mutableStateOf("") }
    var deletionText by remember(ip.id) { mutableStateOf("") }
    var ptrReview by remember(ip.id) { mutableStateOf(false) }
    var submitting by remember(ip.id) { mutableStateOf(false) }
    var progressText by remember(ip.id) { mutableStateOf<String?>(null) }
    var errorText by remember(ip.id) { mutableStateOf<String?>(null) }
    var detailState by remember(ip.id) { mutableStateOf<PrimaryIpDetailState>(PrimaryIpDetailState.Loading) }
    var detailLoadGeneration by remember(ip.id) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val currentServer = ip.assigneeId?.let { id -> servers.firstOrNull { it.id == id } }
    val eligibleServers = servers.filter { it.location == ip.location && it.status == ServerStatus.OFF }
    val pointerAddresses = ip.dnsPointers.ifEmpty { listOf(com.hcloudcontrol.core.domain.model.DnsPointer(ip.address, null)) }
    val primaryIpOperations = remember(repository) { PrimaryIpOperationsUseCase(repository) }
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val context = LocalContext.current
    val sendingRequest = stringResource(R.string.common_sending_request)
    val actionCompleted = stringResource(R.string.network_action_completed)
    val mutationFailed = stringResource(R.string.primary_ip_mutation_failed)
    val detailLoadFailed = stringResource(R.string.primary_ip_detail_load_failed)

    fun refreshDetail() {
        val generation = ++detailLoadGeneration
        detailState = PrimaryIpDetailState.Loading
        scope.launch {
            val result = try {
                val detail = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val primaryIp = async { repository.getPrimaryIp(ip.id) }
                        val actions = async { repository.getPrimaryIpActions(ip.id) }
                        PrimaryIpDetailState.Content(primaryIp.await(), actions.await())
                    }
                }
                detail
            } catch (error: Throwable) {
                PrimaryIpDetailState.Error(safeApiFailureMessage(error, detailLoadFailed))
            }
            if (generation == detailLoadGeneration) detailState = result
        }
    }
    LaunchedEffect(ip.id) { refreshDetail() }

    fun runMutation(action: suspend () -> com.hcloudcontrol.core.domain.model.CloudAction) {
        if (!highImpactAllowed) return
        submitting = true
        errorText = null
        progressText = sendingRequest
        scope.launch {
            try {
                val cloudAction = action()
                awaitCloudAction(repository, cloudAction) { status ->
                    progressText = context.getString(R.string.action_status_format, powerActionStatusText(status))
                }
                progressText = actionCompleted
                onCompleted()
                refreshDetail()
            } catch (_: Throwable) {
                errorText = mutationFailed
            } finally {
                submitting = false
            }
        }
    }

    fun runDelete() {
        if (!highImpactAllowed || deletionText != ip.name) return
        submitting = true
        errorText = null
        progressText = sendingRequest
        scope.launch {
            try {
                withContext(Dispatchers.IO) { primaryIpOperations.deleteUnassigned(ip) }
                progressText = actionCompleted
                onCompleted()
                onDismiss()
            } catch (_: Throwable) {
                errorText = mutationFailed
                submitting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.primary_ip_title, ip.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${ip.address} · ${ip.type} · ${ip.location}")
                when (operation) {
                    PrimaryIpOperation.Overview -> {
                        val assignedId = ip.assigneeId
                        Text(
                            if (assignedId == null) stringResource(R.string.primary_ip_unassigned) else stringResource(R.string.primary_ip_assigned, ip.assigneeType ?: "resource", assignedId),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (highImpactAllowed) {
                            Text(stringResource(R.string.primary_ip_transfer_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ip.assigneeId == null) {
                                OutlinedButton(onClick = { operation = PrimaryIpOperation.Assign }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.primary_ip_assign)) }
                            } else {
                                OutlinedButton(onClick = { operation = PrimaryIpOperation.Unassign }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.primary_ip_unassign)) }
                                currentServer?.let { server ->
                                    Text(stringResource(R.string.primary_ip_current_server, server.name, server.status.name.lowercase()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(onClick = { operation = PrimaryIpOperation.ReverseDns }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.primary_ip_reverse_dns)) }
                            ResourceProtectionControls(
                                resourceName = ip.name,
                                deleteProtected = ip.deleteProtected,
                                repository = repository,
                                onChange = { patch -> repository.changePrimaryIpProtection(ip.id, patch) },
                                onCompleted = { onCompleted(); refreshDetail() },
                            )
                            if (highImpactAllowed && ip.assigneeId == null && !ip.deleteProtected) {
                                OutlinedButton(
                                    onClick = { operation = PrimaryIpOperation.Delete }, enabled = !submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) { Text(stringResource(R.string.primary_ip_delete)) }
                            }
                        }
                        Text(stringResource(R.string.primary_ip_detail_title), style = MaterialTheme.typography.titleSmall)
                        when (val currentDetail = detailState) {
                            PrimaryIpDetailState.Loading -> ObservabilityLoading(stringResource(R.string.primary_ip_detail_loading))
                            is PrimaryIpDetailState.Error -> ObservabilityError(currentDetail.message, ::refreshDetail)
                            is PrimaryIpDetailState.Content -> {
                                val detail = currentDetail.primaryIp
                                DetailRow(stringResource(R.string.primary_ip_detail_id), detail.id.toString())
                                DetailRow(stringResource(R.string.primary_ip_detail_location), detail.location)
                                DetailRow(
                                    stringResource(R.string.primary_ip_detail_assignee),
                                    detail.assigneeId?.let { assigneeId -> stringResource(R.string.primary_ip_assigned, detail.assigneeType ?: "resource", assigneeId) }
                                        ?: stringResource(R.string.primary_ip_unassigned),
                                )
                                Text(
                                    stringResource(if (detail.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (detail.deleteProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(stringResource(R.string.primary_ip_action_history), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.primary_ip_action_history_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PrimaryIpActionHistory(currentDetail.actions)
                            }
                        }
                    }
                    PrimaryIpOperation.Assign -> {
                        Text(stringResource(R.string.primary_ip_choose_off_server, ip.location))
                        if (eligibleServers.isEmpty()) {
                            Text(stringResource(R.string.primary_ip_no_off_servers), color = MaterialTheme.colorScheme.error)
                        } else {
                            eligibleServers.sortedBy { it.name.lowercase() }.forEach { server ->
                                val selected = selectedServerId == server.id
                                OutlinedButton(onClick = { selectedServerId = server.id }, enabled = !submitting, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                                    Text(stringResource(R.string.primary_ip_selected_server, if (selected) "✓ " else "", server.name, server.id, stringResource(R.string.primary_ip_server_off)))
                                }
                            }
                        }
                    }
                    PrimaryIpOperation.Unassign -> {
                        Text(stringResource(R.string.primary_ip_unassign_confirm, ip.address, currentServer?.name ?: stringResource(R.string.common_server_id, ip.assigneeId ?: 0)))
                        Text(stringResource(R.string.primary_ip_unassign_warning), color = MaterialTheme.colorScheme.error)
                        if (currentServer?.status != ServerStatus.OFF) {
                            Text(stringResource(R.string.primary_ip_unassign_requires_off), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    PrimaryIpOperation.ReverseDns -> {
                        Text(stringResource(R.string.primary_ip_ptr_intro))
                        pointerAddresses.forEach { pointer ->
                            OutlinedButton(
                                onClick = { selectedPointerIp = pointer.ip; dnsPtr = pointer.dnsPtr.orEmpty(); ptrReview = false },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.common_selected_value, if (selectedPointerIp == pointer.ip) "✓ " else "", stringResource(R.string.primary_ip_ptr_value, pointer.ip, pointer.dnsPtr ?: stringResource(R.string.primary_ip_ptr_not_set)))) }
                        }
                        selectedPointerIp?.let {
                            OutlinedTextField(
                                value = dnsPtr,
                                onValueChange = { value -> dnsPtr = value; ptrReview = false },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.primary_ip_ptr_hostname)) },
                                singleLine = true,
                                enabled = !submitting,
                            )
                        }
                        if (ptrReview) selectedPointerIp?.let { pointerIp ->
                            Text(
                                stringResource(
                                    R.string.primary_ip_ptr_confirm_message,
                                    pointerIp,
                                    dnsPtr.trim().ifBlank { stringResource(R.string.primary_ip_ptr_clear_value) },
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    PrimaryIpOperation.Delete -> {
                        Text(stringResource(R.string.primary_ip_delete_warning, ip.address), color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(
                            value = deletionText, onValueChange = { deletionText = it }, enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.primary_ip_delete_type, ip.name)) },
                        )
                    }
                }
                progressText?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (operation) {
                PrimaryIpOperation.Overview -> TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_close)) }
                PrimaryIpOperation.Assign -> Button(
                    onClick = { selectedServerId?.let { serverId -> eligibleServers.firstOrNull { it.id == serverId }?.let { server -> runMutation { primaryIpOperations.assign(ip, server) } } } },
                    enabled = selectedServerId != null && !submitting,
                ) { Text(if (submitting) stringResource(R.string.primary_ip_assigning) else stringResource(R.string.primary_ip_confirm_assign)) }
                PrimaryIpOperation.Unassign -> Button(
                    onClick = { runMutation { primaryIpOperations.unassign(ip, currentServer) } },
                    enabled = currentServer?.status == ServerStatus.OFF && !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(if (submitting) stringResource(R.string.primary_ip_unassigning) else stringResource(R.string.primary_ip_confirm_unassign)) }
                PrimaryIpOperation.ReverseDns -> Button(
                    onClick = { selectedPointerIp?.let { pointerIp -> if (ptrReview) runMutation { primaryIpOperations.changeReverseDns(ip, pointerIp, dnsPtr) } else ptrReview = true } },
                    enabled = selectedPointerIp != null && !submitting,
                ) { Text(if (submitting) stringResource(R.string.primary_ip_saving) else if (ptrReview) stringResource(R.string.primary_ip_confirm_ptr) else stringResource(R.string.primary_ip_review_ptr)) }
                PrimaryIpOperation.Delete -> Button(
                    onClick = ::runDelete,
                    enabled = highImpactAllowed && deletionText == ip.name && !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(if (submitting) stringResource(R.string.common_in_progress) else stringResource(R.string.primary_ip_delete_confirm)) }
            }
        },
        dismissButton = {
            if (operation != PrimaryIpOperation.Overview) {
                TextButton(onClick = { operation = PrimaryIpOperation.Overview; errorText = null; progressText = null; ptrReview = false }, enabled = !submitting) { Text(stringResource(R.string.common_back_action)) }
            }
        },
    )
}

@Composable
private fun PrimaryIpActionHistory(actions: List<CloudAction>) {
    if (actions.isEmpty()) {
        Text(stringResource(R.string.primary_ip_action_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.sortedByDescending { it.id }.forEach { action ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(action.command ?: stringResource(R.string.primary_ip_action_unknown), style = MaterialTheme.typography.titleSmall)
                    DetailRow(stringResource(R.string.server_action_status), action.status)
                    action.progress?.let { DetailRow(stringResource(R.string.server_action_progress), "$it%") }
                    action.started?.let { DetailRow(stringResource(R.string.server_action_started), it) }
                    action.finished?.let { DetailRow(stringResource(R.string.server_action_finished), it) }
                    action.errorCode?.let { DetailRow(stringResource(R.string.server_action_error_code), it) }
                }
            }
        }
    }
}

@Composable
private fun PrimaryIpCard(ip: PrimaryIpInventory, onManage: () -> Unit) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(ip.name, style = MaterialTheme.typography.titleMedium)
        Text("${ip.address} · ${ip.type} · ${ip.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val assignedId = ip.assigneeId
        Text(if (assignedId == null) stringResource(R.string.server_not_assigned) else stringResource(R.string.primary_ip_assigned, ip.assigneeType ?: "resource", assignedId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(if (ip.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled), style = MaterialTheme.typography.labelMedium, color = if (ip.deleteProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        ip.dnsPointers.forEach { pointer ->
            Text(stringResource(R.string.primary_ip_reverse_dns_value, pointer.ip, pointer.dnsPtr ?: stringResource(R.string.primary_ip_ptr_not_set)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.primary_ip_manage)) }
    }
}

@Composable
private fun InventorySection(title: String, images: List<BackupSnapshotImage>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    if (images.isEmpty()) {
        Text(stringResource(R.string.common_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        images.sortedByDescending { it.createdAt.orEmpty() }.forEach { image ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(image.name ?: image.description ?: stringResource(R.string.image_unnamed), style = MaterialTheme.typography.titleMedium)
                    image.description?.takeIf { it != image.name }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    image.createdAt?.let { Text(stringResource(R.string.image_created, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    image.deprecatedAt?.let { Text(stringResource(R.string.image_deprecated, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
@Composable fun LoadingView(message: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(message) } }
@Composable fun StateView(title: String, message: String, action: String, onAction: () -> Unit) = Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = onAction) { Text(action) } }
@Composable private fun ProductScope(title: String, description: String) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable
private fun SettingsScreen(
    activeProject: CloudProjectConnection,
    connections: List<CloudProjectConnection>,
    onAddProject: () -> Unit,
    onDisconnect: () -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onHighImpactOperationsChange: (Boolean) -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf(false) }
    var pendingHighImpactChange by remember { mutableStateOf<Boolean?>(null) }
    val highImpactOperations = LocalHighImpactOperations.current
    var languageMode by remember { mutableStateOf(currentLanguageMode()) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HCloudScreenHeader(stringResource(R.string.settings_title))
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = appearanceMode == mode,
                    onClick = { onAppearanceModeChange(mode) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(stringResource(mode.labelRes), Modifier.clickable { onAppearanceModeChange(mode) })
            }
        }
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = languageMode == mode, onClick = { languageMode = mode; applyLanguage(mode) })
                Text(stringResource(mode.labelRes), Modifier.clickable { languageMode = mode; applyLanguage(mode) })
            }
        }
        Text(stringResource(R.string.settings_high_impact_title), style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (highImpactOperations.isAllowed) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (highImpactOperations.isAllowed) R.string.settings_high_impact_enabled
                        else R.string.settings_high_impact_disabled,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.settings_high_impact_warning),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { pendingHighImpactChange = !highImpactOperations.isAllowed },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (highImpactOperations.isAllowed) {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Text(
                        stringResource(
                            if (highImpactOperations.isAllowed) R.string.settings_high_impact_disable
                            else R.string.settings_high_impact_enable,
                        ),
                    )
                }
            }
        }
        Text(pluralStringResource(R.plurals.settings_projects_configured, connections.size, connections.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_active_project, activeProject.displayName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_profile_title), style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activeProject.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (activeProject.config.kind == ConnectionKind.REAL) {
                            R.string.settings_profile_project_connection
                        } else {
                            R.string.settings_profile_test_connection
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_profile_api_scope_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (activeProject.config.kind == ConnectionKind.TEST_MOCK) {
            TestServerBadge()
            Text(
                HCloudConnectionConfig.TEST_MOCK_BASE_URL,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onAddProject, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_add_project)) }
        OutlinedButton(onClick = { confirmRemoval = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_remove_project, activeProject.displayName)) }
    }
    if (confirmRemoval) AlertDialog(
        onDismissRequest = { confirmRemoval = false },
        title = { Text(stringResource(R.string.settings_remove_project_title, activeProject.displayName)) },
        text = { Text(stringResource(R.string.settings_remove_project_message)) },
        confirmButton = {
            Button(
                onClick = {
                    // The composable can immediately receive the next active project; do not
                    // leave a stale confirmation open against that different profile.
                    confirmRemoval = false
                    onDisconnect()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.settings_remove_project_confirm)) }
        },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    pendingHighImpactChange?.let { allow ->
        AlertDialog(
            onDismissRequest = { pendingHighImpactChange = null },
            title = {
                Text(
                    stringResource(
                        if (allow) R.string.settings_high_impact_enable_title
                        else R.string.settings_high_impact_disable_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (allow) R.string.settings_high_impact_enable_message
                        else R.string.settings_high_impact_disable_message,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onHighImpactOperationsChange(allow)
                        pendingHighImpactChange = null
                    },
                    colors = if (allow) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) else ButtonDefaults.buttonColors(),
                ) {
                    Text(stringResource(if (allow) R.string.settings_high_impact_enable_confirm else R.string.settings_high_impact_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHighImpactChange = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
@Preview(showBackground = true) @Composable private fun ServerCardPreview() { MaterialTheme(colorScheme = HCloudColors) { ServerCard(Server(1, "web-01", ServerStatus.RUNNING, "203.0.113.42", "fsn1", "cpx31"), {}) } }
