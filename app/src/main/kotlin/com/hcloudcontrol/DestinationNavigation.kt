package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.*

internal fun Destination.canonicalRoute(context: ResourceContext): CanonicalDestination = when (this) {
    Destination.Servers -> CanonicalDestination(RootDestination.SERVERS)
    Destination.Catalog -> CanonicalDestination(RootDestination.CREATE)
    Destination.Storage -> CanonicalDestination(RootDestination.STORAGE)
    Destination.Network -> CanonicalDestination(RootDestination.NETWORK)
    Destination.More -> CanonicalDestination(RootDestination.MORE)
    Destination.StorageBoxes -> CanonicalDestination(RootDestination.MORE, DestinationKind.LIST,
        context.copy(endpoint = com.hcloudcontrol.core.network.StorageBoxRepositoryFactory.API_BASE_URL), ResourceType.STORAGE_BOX)
    // Device-local connection picker has no selected endpoint yet; bucket routes do.
    Destination.ObjectStorage -> CanonicalDestination(RootDestination.MORE, DestinationKind.LIST, resourceType = ResourceType.S3_CONNECTIONS)
    else -> CanonicalDestination(RootDestination.MORE, DestinationKind.LIST, context, when (this) {
        Destination.Images -> ResourceType.IMAGE
        Destination.Tariffs -> ResourceType.PRICING
        Destination.LoadBalancers -> ResourceType.LOAD_BALANCER
        Destination.Certificates -> ResourceType.CERTIFICATE
        Destination.SshKeys -> ResourceType.SSH_KEY
        Destination.PlacementGroups -> ResourceType.PLACEMENT_GROUP
        Destination.Dns -> ResourceType.DNS_ZONE
        Destination.Settings -> ResourceType.SETTINGS
        else -> error("Root destination handled above")
    })
}

internal fun CanonicalDestination.legacyDestination(): Destination = if (kind == DestinationKind.ROOT) when (root) {
    RootDestination.SERVERS -> Destination.Servers
    RootDestination.CREATE -> Destination.Catalog
    RootDestination.STORAGE -> Destination.Storage
    RootDestination.NETWORK -> Destination.Network
    RootDestination.MORE -> Destination.More
} else when (resourceType) {
    ResourceType.IMAGE -> Destination.Images
    ResourceType.STORAGE_BOX -> Destination.StorageBoxes
    ResourceType.BUCKET, ResourceType.S3_CONNECTIONS -> Destination.ObjectStorage
    ResourceType.PRICING -> Destination.Tariffs
    ResourceType.LOAD_BALANCER -> Destination.LoadBalancers
    ResourceType.CERTIFICATE -> Destination.Certificates
    ResourceType.SSH_KEY -> Destination.SshKeys
    ResourceType.PLACEMENT_GROUP -> Destination.PlacementGroups
    ResourceType.DNS_ZONE -> Destination.Dns
    ResourceType.SETTINGS -> Destination.Settings
    else -> error("Feature child route is owned by its canonical feature, not a root alias")
}
