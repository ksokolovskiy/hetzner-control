package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.*

/** Ephemeral UI callbacks; only CanonicalDestination belongs in saveable navigation state. */
class ChildNavigationChrome(val route: CanonicalDestination, val title: () -> String, val onBack: () -> Unit)

@Stable
class NavigationChromeHost {
    private val entries = mutableStateListOf<Pair<Any, ChildNavigationChrome>>()
    val current: ChildNavigationChrome? get() = entries.lastOrNull()?.second
    internal fun register(owner: Any, chrome: ChildNavigationChrome) { entries.removeAll { it.first === owner }; entries += owner to chrome }
    internal fun unregister(owner: Any) { entries.removeAll { it.first === owner } }
}

val LocalNavigationChrome = compositionLocalOf<NavigationChromeHost?> { null }
val LocalNavigationContext = compositionLocalOf<ResourceContext?> { null }

@Composable
fun RegisterResourceChildNavigation(root: RootDestination, type: ResourceType, id: String?, title: String, onBack: () -> Unit) {
    val context = LocalNavigationContext.current ?: return
    RegisterChildNavigation(CanonicalDestination(root, if (id == null) DestinationKind.LIST else DestinationKind.DETAIL,
        context, type, id), title, onBack)
}

/** Forms have their own Back policy; an underlying detail must not handle their gesture. */
@Composable
fun RegisterResourceFormNavigation(root: RootDestination, type: ResourceType, id: String?, title: String, onBack: () -> Unit) {
    val context = LocalNavigationContext.current ?: return
    RegisterChildNavigation(CanonicalDestination(root, DestinationKind.FORM, context, type, id), title, onBack)
}

/** Registers one child with the existing app scaffold. No nested Scaffold or duplicate BackHandler. */
@Composable
fun RegisterChildNavigation(route: CanonicalDestination, title: String, onBack: () -> Unit) {
    val host = LocalNavigationChrome.current ?: return
    val currentTitle by rememberUpdatedState(title)
    val currentBack by rememberUpdatedState(onBack)
    val owner = remember { Any() }
    DisposableEffect(host, owner, route) {
        host.register(owner, ChildNavigationChrome(route, { currentTitle }, { currentBack() }))
        onDispose { host.unregister(owner) }
    }
}
