package com.hcloudcontrol.ui.navigation

fun rootContentStateKey(context: ResourceContext, root: RootDestination): String =
    "root-state:${context.kind}:${context.ownerId}:${context.endpoint}:$root"

fun retiredCloudRootKeys(previous: ResourceContext, current: ResourceContext): List<String> =
    if (previous == current || previous.kind != ContextKind.CLOUD) emptyList()
    else RootDestination.entries.map { rootContentStateKey(previous, it) }
