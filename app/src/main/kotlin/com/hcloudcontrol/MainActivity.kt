package com.hcloudcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.usecase.GetServersUseCase
import com.hcloudcontrol.core.network.HCloudRepositoryFactory
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.core.security.EncryptedHCloudTokenStore
import com.hcloudcontrol.core.security.HCloudTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = EncryptedHCloudTokenStore(applicationContext)
        val repository = HCloudRepositoryFactory.create(tokenStore::token)
        setContent { HCloudControlApp(tokenStore, GetServersUseCase(repository)) }
    }
}

private val HCloudColors = darkColorScheme(
    primary = Color(0xFFFF5A1F), onPrimary = Color(0xFF241006), primaryContainer = Color(0xFF7D2A0B),
    secondary = Color(0xFFB9C5FF), background = Color(0xFF111318), surface = Color(0xFF191C22),
    surfaceVariant = Color(0xFF282C34), onBackground = Color(0xFFE2E2E9), onSurface = Color(0xFFE2E2E9), error = Color(0xFFFFB4AB),
)

@Composable
private fun HCloudControlApp(tokenStore: HCloudTokenStore, getServers: GetServersUseCase) {
    MaterialTheme(colorScheme = HCloudColors) {
        Surface(Modifier.fillMaxSize()) {
            var connections by remember { mutableStateOf(tokenStore.connections()) }
            var addingProject by remember { mutableStateOf(false) }
            if (connections.isEmpty() || addingProject) {
                Onboarding(
                    isAdditionalProject = connections.isNotEmpty(),
                    onConnect = { projectName, token ->
                        tokenStore.saveConnection(projectName, token)
                        connections = tokenStore.connections()
                        addingProject = false
                    },
                )
            } else CloudHome(
                getServers = getServers,
                tokenStore = tokenStore,
                connections = connections,
                onConnectionsChanged = { connections = tokenStore.connections() },
                onAddProject = { addingProject = true },
            )
        }
    }
}

@Composable
private fun Onboarding(isAdditionalProject: Boolean, onConnect: (String, String) -> Unit) {
    var projectName by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val invalidName = submitted && projectName.isBlank()
    val invalidToken = submitted && token.isBlank()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("HCloud Control", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(if (isAdditionalProject) "Add a Cloud project" else "A focused control room for your Hetzner Cloud projects.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Text("Connect a Cloud project", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Create a separate API token in each Hetzner Cloud project. This app stores every token separately, encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(value = projectName, onValueChange = { projectName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Project name (for example, Production)") }, supportingText = { if (invalidName) Text("Name this project to continue") }, isError = invalidName, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API token for this project") }, supportingText = { if (invalidToken) Text("Enter this project’s API token to continue") }, isError = invalidToken, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } })
        Spacer(Modifier.height(20.dp))
        Button(onClick = { submitted = true; if (projectName.isNotBlank() && token.isNotBlank()) onConnect(projectName, token) }, modifier = Modifier.fillMaxWidth()) { Text("Connect project securely") }
        Spacer(Modifier.height(14.dp))
        Text("This app supports Hetzner Cloud only. Robot, Storage Box, DNS Console and other products are intentionally excluded.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private sealed interface ServersState {
    data object Loading : ServersState
    data class Content(val servers: List<Server>) : ServersState
    data object Offline : ServersState
    data object ReadOnly : ServersState
    data class Error(val message: String) : ServersState
}
private enum class Destination(val label: String, val marker: String) { Servers("Servers", "▣"), Storage("Storage", "▤"), Network("Network", "⌁"), Settings("Settings", "⚙") }

@Composable
private fun CloudHome(getServers: GetServersUseCase, tokenStore: HCloudTokenStore, connections: List<CloudProjectConnection>, onConnectionsChanged: () -> Unit, onAddProject: () -> Unit) {
    var destination by remember { mutableStateOf(Destination.Servers) }
    var serverState by remember { mutableStateOf<ServersState>(ServersState.Loading) }
    var selectedServer by remember { mutableStateOf<Server?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        serverState = ServersState.Loading
        scope.launch {
            serverState = try { ServersState.Content(withContext(Dispatchers.IO) { getServers() }) }
            catch (_: IOException) { ServersState.Offline }
            catch (error: Throwable) { ServersState.Error(error.message?.takeIf { it.isNotBlank() } ?: "Could not load servers") }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val activeId = tokenStore.activeConnectionId()
    val activeProject = connections.firstOrNull { it.id == activeId } ?: connections.first()
    Scaffold(topBar = { HCloudTopBar(selectedServer, activeProject, connections, onSelectProject = { connection -> tokenStore.selectConnection(connection.id); onConnectionsChanged(); refresh() }, onBack = { selectedServer = null }) }, bottomBar = {
        if (selectedServer == null) NavigationBar { Destination.entries.forEach { item -> NavigationBarItem(selected = item == destination, onClick = { destination = item }, icon = { Text(item.marker) }, label = { Text(item.label) }) } }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            selectedServer?.let { ServerDetails(it, serverState is ServersState.ReadOnly) { selectedServer = null } } ?: when (destination) {
                Destination.Servers -> ServersScreen(serverState, ::refresh) { selectedServer = it }
                Destination.Storage -> ProductScope("Storage", "Volumes and snapshots will appear here when their Cloud API support is added.")
                Destination.Network -> ProductScope("Network", "Private networks and floating IPs will appear here when their Cloud API support is added.")
                Destination.Settings -> SettingsScreen(activeProject, connections, onAddProject, onDisconnect = { tokenStore.removeConnection(activeProject.id); onConnectionsChanged() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HCloudTopBar(server: Server?, activeProject: CloudProjectConnection, connections: List<CloudProjectConnection>, onSelectProject: (CloudProjectConnection) -> Unit, onBack: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(title = {
        if (server != null) Text(server.name) else Box {
            TextButton(onClick = { expanded = true }) { Text("${activeProject.displayName} ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                connections.forEach { connection -> DropdownMenuItem(text = { Text(connection.displayName) }, onClick = { expanded = false; onSelectProject(connection) }) }
            }
        }
    }, navigationIcon = { if (server != null) TextButton(onClick = onBack) { Text("Back") } })
}

@Composable
private fun ServersScreen(state: ServersState, onRefresh: () -> Unit, onServerClick: (Server) -> Unit) = when (state) {
    ServersState.Loading -> LoadingView("Loading your servers…")
    ServersState.Offline -> StateView("You’re offline", "Check your connection and try again.", "Retry", onRefresh)
    ServersState.ReadOnly -> StateView("Read-only connection", "This token can list resources, but actions are unavailable.", "Refresh", onRefresh)
    is ServersState.Error -> StateView("Couldn’t load servers", state.message, "Try again", onRefresh)
    is ServersState.Content -> if (state.servers.isEmpty()) StateView("No servers yet", "Create a server in Hetzner Cloud Console, then refresh here.", "Refresh", onRefresh) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Servers", style = MaterialTheme.typography.headlineMedium); Text("${state.servers.size} in this project", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.servers, key = { it.id }) { ServerCard(it) { onServerClick(it) } }
    }
}

@Composable private fun ServerCard(server: Server, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(server.status); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(server.name, style = MaterialTheme.typography.titleMedium); Text("${server.serverType} · ${server.location}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(horizontalAlignment = Alignment.End) { StatusLabel(server.status); Text(server.ipv4Address ?: "No public IPv4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun StatusDot(status: ServerStatus) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (status == ServerStatus.RUNNING) Color(0xFF73DB9B) else Color(0xFFFFB95A))) }
@Composable private fun StatusLabel(status: ServerStatus) = Text(status.name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelMedium)

@Composable
private fun ServerDetails(server: Server, readOnly: Boolean, onBack: () -> Unit) {
    var action by remember { mutableStateOf<PowerAction?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Server overview", style = MaterialTheme.typography.headlineMedium); StatusLabel(server.status) }
        item { DetailsCard(server) }; item { Text("Power", style = MaterialTheme.typography.titleLarge) }; item { PowerActions(server.status, readOnly) { action = it } }
        if (readOnly) item { Text("Your token is read-only. Power controls are unavailable.", color = MaterialTheme.colorScheme.error) }
    }
    action?.let { PowerConfirmation(it, server.name, !readOnly) { action = null } }
}
@Composable private fun DetailsCard(server: Server) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { DetailRow("Type", server.serverType); DetailRow("Location", server.location); DetailRow("IPv4", server.ipv4Address ?: "Not assigned"); DetailRow("Server ID", server.id.toString()) } }
@Composable private fun DetailRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) }
private enum class PowerAction(val title: String, val explanation: String, val destructive: Boolean) { Start("Start server", "The server will boot and begin accepting traffic when ready.", false), Shutdown("Shut down server", "The operating system will receive a clean shutdown request.", true), Reset("Reset server", "This immediately restarts the server. Unsaved work may be lost.", true) }
@Composable private fun PowerActions(status: ServerStatus, readOnly: Boolean, onAction: (PowerAction) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { PowerButton(PowerAction.Start, status == ServerStatus.OFF && !readOnly, onAction); PowerButton(PowerAction.Shutdown, status == ServerStatus.RUNNING && !readOnly, onAction); PowerButton(PowerAction.Reset, status == ServerStatus.RUNNING && !readOnly, onAction) }
@Composable private fun RowScope.PowerButton(action: PowerAction, enabled: Boolean, onAction: (PowerAction) -> Unit) = OutlinedButton(onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.weight(1f)) { Text(action.title.substringBefore(" ")) }
@Composable private fun PowerConfirmation(action: PowerAction, serverName: String, canExecute: Boolean, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text(action.title) }, text = { Text("${action.explanation}\n\nServer: $serverName") }, confirmButton = { Button(onClick = onDismiss, enabled = canExecute, colors = if (action.destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors()) { Text("Confirm") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
@Composable private fun LoadingView(message: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(message) } }
@Composable private fun StateView(title: String, message: String, action: String, onAction: () -> Unit) = Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); OutlinedButton(onClick = onAction) { Text(action) } }
@Composable private fun ProductScope(title: String, description: String) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(8.dp)); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable
private fun SettingsScreen(activeProject: CloudProjectConnection, connections: List<CloudProjectConnection>, onAddProject: () -> Unit, onDisconnect: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Text("${connections.size} Cloud project(s) configured. Each project has its own encrypted API token.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Active project: ${activeProject.displayName}", style = MaterialTheme.typography.titleMedium)
    Button(onClick = onAddProject, modifier = Modifier.fillMaxWidth()) { Text("Add another Cloud project") }
    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Remove ${activeProject.displayName}") }
}
@Preview(showBackground = true) @Composable private fun ServerCardPreview() { MaterialTheme(colorScheme = HCloudColors) { ServerCard(Server(1, "web-01", ServerStatus.RUNNING, "203.0.113.42", "fsn1", "cpx31"), {}) } }
