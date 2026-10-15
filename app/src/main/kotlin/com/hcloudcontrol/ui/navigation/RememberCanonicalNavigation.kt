package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** The same serialization is used by process recreation and pure route roundtrip tests. */
val NavigationStateSaver: Saver<NavigationState, ArrayList<String>> = Saver(
    save = { it.snapshot() },
    restore = { NavigationState.restore(it) },
)

@Composable
fun rememberCanonicalNavigation(initialRoot: RootDestination = RootDestination.SERVERS): MutableState<NavigationState> = rememberSaveable(
    stateSaver = NavigationStateSaver,
) { mutableStateOf(NavigationState().selectRoot(initialRoot)) }
