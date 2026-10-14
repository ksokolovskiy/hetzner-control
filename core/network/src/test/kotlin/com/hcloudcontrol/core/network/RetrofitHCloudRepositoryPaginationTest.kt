package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.CreateServerSpec
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.model.IsoSelection
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.model.CreateFirewallSpec
import com.hcloudcontrol.core.domain.model.FirewallRuleSpec
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RetrofitHCloudRepositoryPaginationTest {
    @Test
    fun `repository collects all resource pages and maps current location`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val servers = repository.getServers()
        val serverTypes = repository.getServerTypes()
        val images = repository.getSystemImages()
        val backupAndSnapshots = repository.getBackupAndSnapshotImages()
        val locations = repository.getLocations()
        val sshKeys = repository.getSshKeys()
        val volumes = repository.getVolumes()
        val primaryIps = repository.getPrimaryIps()
        val floatingIps = repository.getFloatingIps()
        val firewalls = repository.getFirewalls()
        val networks = repository.getPrivateNetworks()
        val loadBalancers = repository.getLoadBalancers()
        val placementGroups = repository.getPlacementGroups()

        assertEquals(listOf(1, 2), api.serverPages)
        assertEquals(listOf(1, 2), api.serverTypePages)
        assertEquals(
            listOf("system" to 1, "system" to 2, "backup" to 1, "backup" to 2, "snapshot" to 1, "snapshot" to 2),
            api.imageRequests,
        )
        assertEquals(listOf(1, 2), api.locationPages)
        assertEquals(listOf(1, 2), api.sshKeyPages)
        assertEquals(listOf(1, 2), api.volumePages)
        assertEquals(listOf(1, 2), api.primaryIpPages)
        assertEquals(listOf(1, 2), api.floatingIpPages)
        assertEquals(listOf(1, 2), api.firewallPages)
        assertEquals(listOf(1, 2), api.networkPages)
        assertEquals(listOf(1, 2), api.loadBalancerPages)
        assertEquals(listOf(1, 2), api.placementGroupPages)
        assertEquals(listOf("web-01", "db-01"), servers.map { it.name })
        assertEquals("fsn1-dc14", servers.first().location)
        assertEquals("2001:db8:1::/64", servers.first().networkDevices.ipv6Network)
        assertEquals(listOf(501L), servers.first().networkDevices.primaryIpIds)
        assertEquals(listOf(601L), servers.first().networkDevices.floatingIpIds)
        assertEquals("10.42.1.10", servers.first().networkDevices.privateInterfaces.single().ip)
        assertEquals("10.42.1.11", servers.first().networkDevices.privateInterfaces.single().aliasIps.single())
        assertEquals(listOf(701L), servers.first().networkDevices.attachedVolumeIds)
        assertEquals("/dev/disk/by-id/scsi-0HC_Volume_701", servers.first().networkDevices.deviceMaps.single().device)
        assertEquals(setOf("fsn1-dc14"), serverTypes.single().availableLocations)
        assertFalse(serverTypes.single().availableLocations.contains("hel1-dc2"))
        assertFalse(serverTypes.single().availableLocations.contains("nbg1-dc3"))
        assertEquals(listOf("ubuntu-24.04"), images.map { it.name })
        assertEquals(listOf(20L, 21L, 30L, 31L), backupAndSnapshots.map { it.id })
        assertEquals(listOf("backup", "backup", "snapshot", "snapshot"), backupAndSnapshots.map { it.type })
        assertEquals("2026-06-01T00:00:00+00:00", backupAndSnapshots[2].deprecatedAt)
        assertEquals(listOf("Falkenstein", "Helsinki"), locations.map { it.city })
        assertEquals(listOf("laptop", "workstation"), sshKeys.map { it.name })
        assertEquals(listOf("aa:bb", "cc:dd"), sshKeys.map { it.fingerprint })
        assertEquals(listOf("postgres-data", "unattached"), volumes.map { it.name })
        assertEquals(42L, volumes.first().serverId)
        assertTrue(volumes.first().deleteProtected)
        assertEquals("production", volumes.first().labels["environment"])
        assertNull(volumes[1].serverId)
        assertEquals(listOf("frontend-ip", "unassigned-v6"), primaryIps.map { it.name })
        assertEquals(42L, primaryIps.first().assigneeId)
        assertTrue(primaryIps.first().deleteProtected)
        assertEquals("web.example.test", primaryIps.first().dnsPointers.single().dnsPtr)
        assertEquals("unassigned", primaryIps[1].assigneeType)
        assertNull(primaryIps[1].dnsPointers.single().dnsPtr)
        assertEquals(listOf("failover-v4", "unassigned-v6"), floatingIps.map { it.name })
        assertEquals("fsn1-dc14", floatingIps.first().homeLocation)
        assertEquals(42L, floatingIps.first().assigneeId)
        assertTrue(floatingIps.first().deleteProtected)
        assertEquals("web.example.test", floatingIps.first().dnsPointers.single().dnsPtr)
        assertNull(floatingIps[1].assigneeId)
        assertEquals(listOf("web-access", "internal-only"), firewalls.map { it.name })
        assertEquals("Allow HTTPS", firewalls.first().rules.single().description)
        assertEquals(listOf("0.0.0.0/0", "::/0"), firewalls.first().rules.single().sourceIps)
        assertEquals(42L, firewalls.first().appliedTo.single().serverId)
        assertEquals("environment=production", firewalls[1].appliedTo.single().labelSelector)
        assertEquals(43L, firewalls[1].appliedTo.single().appliedToResources.single().serverId)
        assertEquals("production", firewalls.first().labels["environment"])
        assertEquals(listOf("production-private", "staging-private"), networks.map { it.name })
        assertEquals("10.42.0.0/16", networks.first().ipRange)
        assertEquals("10.42.1.1", networks.first().subnets.single().gateway)
        assertEquals("10.60.0.0/16", networks.first().routes.single().destination)
        assertEquals(listOf(42L, 43L), networks.first().serverIds)
        assertTrue(networks.first().deleteProtected)
        assertEquals("production", networks.first().labels["environment"])
        assertEquals(1234L, networks[1].subnets.single().vswitchId)
        assertEquals(listOf("public-web", "internal-api"), loadBalancers.map { it.name })
        assertEquals("lb11", loadBalancers.first().type)
        assertTrue(loadBalancers.first().publicInterface.enabled)
        assertEquals("192.0.2.80", loadBalancers.first().publicInterface.ipv4Address)
        assertEquals(801L, loadBalancers.first().privateInterfaces.single().networkId)
        assertEquals(443, loadBalancers.first().services.single().listenPort)
        assertEquals("role=web", loadBalancers.first().targets.single().labelSelector)
        assertEquals("healthy", loadBalancers.first().targets.single().healthStatuses.single().status)
        assertEquals(42L, loadBalancers.first().targets.single().targets.single().serverId)
        assertTrue(loadBalancers.first().deleteProtected)
        assertEquals("0.0083000000", loadBalancers.first().prices.single().hourly.net)
        assertEquals("10.42.2.10", loadBalancers[1].privateInterfaces.single().ip)
        assertEquals(listOf("web-spread", "database-spread"), placementGroups.map { it.name })
        assertEquals("spread", placementGroups.first().type)
        assertEquals(listOf(42L, 43L), placementGroups.first().serverIds)
        assertEquals("production", placementGroups.first().labels["environment"])
        assertNull(placementGroups.first().deleteProtected)
    }

    @Test
    fun `repository lists and details Cloud Images without image actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val images = repository.getImages()
        val detail = repository.getImage(91)

        assertEquals(listOf(90L, 91L), images.map { it.id })
        assertEquals(listOf(1, 2), api.fullImagePages)
        assertEquals("available", images.first().status)
        assertEquals(12.5, images.first().imageSize ?: 0.0, 0.0)
        assertTrue(images.first().deleteProtected!!)
        assertEquals("2026-12-01T00:00:00+00:00", images.first().deprecation?.unavailableAfter)
        assertEquals(91L, detail.id)
        assertEquals("snapshot", detail.type)
    }

    @Test
    fun `floating IP and load balancer detail map selected resource`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val floatingIp = repository.getFloatingIp(601)
        val loadBalancer = repository.getLoadBalancer(901)

        assertEquals(601L, floatingIp.id)
        assertEquals("192.0.2.15", floatingIp.address)
        assertEquals("server", floatingIp.assigneeType)
        assertEquals(901L, loadBalancer.id)
        assertEquals("public-web", loadBalancer.name)
        assertEquals("lb11", loadBalancer.type)
    }

    @Test
    fun `floating IP action history follows pages and rejects an invalid ID`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val actions = repository.getFloatingIpActions(601)
        val invalid = runCatching { repository.getFloatingIpActions(0) }.exceptionOrNull()

        assertEquals(listOf(1, 2), api.floatingIpActionPages)
        assertEquals(listOf(1701L, 1702L), actions.map { it.id })
        assertEquals("assign_floating_ip", actions.first().command)
        assertEquals("action_failed", actions.last().errorCode)
        assertTrue(invalid is IllegalArgumentException)
    }

    @Test
    fun `Primary IP detail and scoped action history are paginated read-only mappings`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val detail = repository.getPrimaryIp(601)
        val actions = repository.getPrimaryIpActions(601)
        val invalidDetail = runCatching { repository.getPrimaryIp(0) }.exceptionOrNull()
        val invalidHistory = runCatching { repository.getPrimaryIpActions(0) }.exceptionOrNull()

        assertEquals(601L, detail.id)
        assertEquals("frontend-ip", detail.name)
        assertEquals("web.example.test", detail.dnsPointers.single().dnsPtr)
        assertEquals(listOf(1, 2), api.primaryIpActionPages)
        assertEquals(listOf(1601L, 1602L), actions.map { it.id })
        assertEquals("change_dns_ptr", actions.first().command)
        assertEquals("action_failed", actions.last().errorCode)
        assertTrue(invalidDetail is IllegalArgumentException)
        assertTrue(invalidHistory is IllegalArgumentException)
    }

    @Test
    fun `load balancer action history follows pages and rejects an invalid ID`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val actions = repository.getLoadBalancerActions(901)
        val invalid = runCatching { repository.getLoadBalancerActions(0) }.exceptionOrNull()

        assertEquals(listOf(1, 2), api.loadBalancerActionPages)
        assertEquals(listOf(1401L, 1402L), actions.map { it.id })
        assertEquals("change_load_balancer_type", actions.first().command)
        assertEquals("action_failed", actions.last().errorCode)
        assertTrue(invalid is IllegalArgumentException)
    }

    @Test
    fun `certificate inventory detail and action history are paginated read-only mappings`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val certificates = repository.getCertificates()
        val detail = repository.getCertificate(701)
        val actions = repository.getCertificateActions(701)
        val invalid = runCatching { repository.getCertificateActions(0) }.exceptionOrNull()

        assertEquals(listOf(1, 2), api.certificatePages)
        assertEquals(listOf(701L, 702L), certificates.map { it.id })
        assertEquals("managed", certificates.first().type)
        assertEquals("completed", certificates.first().status?.issuance)
        assertEquals("load_balancer", certificates.first().usedBy.single().type)
        assertEquals(701L, detail.id)
        assertEquals(listOf(1, 2), api.certificateActionPages)
        assertEquals(listOf(1501L, 1502L), actions.map { it.id })
        assertEquals("action_failed", actions.last().errorCode)
        assertTrue(invalid is IllegalArgumentException)
    }

    @Test
    fun `floating IP operations return actions and reject invalid identifiers before API calls`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        assertEquals(1011L, repository.assignFloatingIp(601, 42).id)
        assertEquals(1012L, repository.unassignFloatingIp(601).id)
        assertEquals(1013L, repository.changeFloatingIpReverseDns(601, "192.0.2.15", null).id)
        val invalidAssign = runCatching { repository.assignFloatingIp(0, 42) }.exceptionOrNull()
        val invalidServer = runCatching { repository.assignFloatingIp(601, 0) }.exceptionOrNull()
        val invalidDns = runCatching { repository.changeFloatingIpReverseDns(601, " ", null) }.exceptionOrNull()

        assertEquals(42L, api.lastAssignFloatingIpRequest?.server)
        assertEquals("192.0.2.15", api.lastFloatingIpDnsPtrRequest?.ip)
        assertNull(api.lastFloatingIpDnsPtrRequest?.dnsPtr)
        assertEquals(listOf("assign" to 601L, "unassign" to 601L, "dns_ptr" to 601L), api.floatingIpActionCalls)
        assertTrue(invalidAssign is IllegalArgumentException)
        assertTrue(invalidServer is IllegalArgumentException)
        assertTrue(invalidDns is IllegalArgumentException)
    }

    @Test
    fun `server action history follows pages without deprecated nested action lookup`() = runBlocking {
        val api = PagingApi()
        val actions = RetrofitHCloudRepository(api).getServerActions(42)

        assertEquals(listOf(1, 2), api.serverActionPages)
        assertEquals(listOf(1301L, 1302L), actions.map { it.id })
        assertEquals("poweron", actions.first().command)
        assertEquals(50, actions.first().progress)
        assertEquals("action_failed", actions[1].errorCode)
    }

    @Test
    fun `server configuration patch forwards only explicitly supplied fields`() = runBlocking {
        val api = PagingApi()
        val updated = RetrofitHCloudRepository(api).updateServerConfiguration(
            42,
            ServerConfigurationPatch(name = "renamed", labels = emptyMap(), placementGroupId = 77),
        )

        assertEquals("renamed", updated.name)
        assertEquals("renamed", api.lastServerConfigurationPatch?.name)
        assertEquals(emptyMap<String, String>(), api.lastServerConfigurationPatch?.labels)
        assertEquals(77L, api.lastServerConfigurationPatch?.placementGroupId)
    }

    @Test
    fun `ISO inventory paginates and attach detach return pollable actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val isos = repository.getIsos()
        val detail = repository.getIso(101)
        val attach = repository.attachIso(42, IsoSelection(101, "rescue"))
        val detach = repository.detachIso(42)

        assertEquals(listOf(1, 2), api.isoPages)
        assertEquals(listOf(100L, 101L), isos.map { it.id })
        assertEquals("rescue", detail.name)
        assertEquals(101L, api.lastAttachIsoRequest?.iso)
        assertEquals(listOf("attach" to 42L, "detach" to 42L), api.isoActionCalls)
        assertEquals(811L, attach.id)
        assertEquals(812L, detach.id)
    }

    @Test
    fun `placement group detail and server membership actions map cloud actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val group = repository.getPlacementGroup(1001)
        val add = repository.addServerToPlacementGroup(42, 1001)
        val remove = repository.removeServerFromPlacementGroup(42, 1001)

        assertEquals("web-spread", group.name)
        assertEquals(listOf(42L, 43L), group.serverIds)
        assertEquals(1001L, api.lastPlacementGroupRequest?.placementGroupId)
        assertEquals(listOf("add" to 42L, "remove" to 42L), api.placementGroupActionCalls)
        assertEquals(821L, add.id)
        assertEquals(822L, remove.id)
    }

    @Test
    fun `placement group membership rejects non-positive IDs before network call`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val badServer = runCatching { repository.addServerToPlacementGroup(0, 1001) }.exceptionOrNull()
        val badGroup = runCatching { repository.removeServerFromPlacementGroup(42, 0) }.exceptionOrNull()

        assertTrue(badServer is IllegalArgumentException)
        assertTrue(badGroup is IllegalArgumentException)
        assertEquals(emptyList<Pair<String, Long>>(), api.placementGroupActionCalls)
    }

    @Test
    fun `server reverse DNS returns pollable action and rejects invalid input`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val action = repository.changeServerReverseDns(42, "192.0.2.42", null)
        val invalidServer = runCatching { repository.changeServerReverseDns(0, "192.0.2.42", "host.example") }.exceptionOrNull()
        val invalidIp = runCatching { repository.changeServerReverseDns(42, " ", "host.example") }.exceptionOrNull()

        assertEquals(831L, action.id)
        assertEquals("192.0.2.42", api.lastServerDnsPtrRequest?.ip)
        assertEquals(null, api.lastServerDnsPtrRequest?.dnsPtr)
        assertTrue(invalidServer is IllegalArgumentException)
        assertTrue(invalidIp is IllegalArgumentException)
    }

    @Test
    fun `server protection patch maps action without automatic extra calls`() = runBlocking {
        val api = PagingApi()
        val action = RetrofitHCloudRepository(api).changeServerProtection(
            42,
            ServerProtectionPatch(delete = true),
        )

        assertEquals(841L, action.id)
        assertEquals(true, api.lastServerProtectionPatch?.delete)
        assertEquals(null, api.lastServerProtectionPatch?.rebuild)
        assertEquals(1, api.serverProtectionCalls)
    }

    @Test
    fun `create server maps root password and all unique operation actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val created = repository.createServer(
            CreateServerSpec(
                "web-01", "cpx21", "ubuntu-24.04", "fsn1-dc14", true, false,
                sshKeyIds = listOf(101, 102),
            ),
        )

        assertEquals("temporary-root-password", created.rootPassword)
        assertEquals("fsn1-dc14", created.server.location)
        assertEquals("web-01", api.lastCreateRequest?.name)
        assertFalse(api.lastCreateRequest!!.publicNet.enableIpv6)
        assertEquals(listOf(101L, 102L), api.lastCreateRequest!!.sshKeys)
        assertEquals(listOf(701L, 702L), created.actions.map { it.id })
        assertEquals(listOf("running", "pending"), created.actions.map { it.status })
    }

    @Test
    fun `create server maps HTTP failure to safe typed error with fields`() = runBlocking {
        val api = PagingApi().apply {
            createServerFailure = HttpException(
                Response.error<Any>(
                    422,
                    """{
                      "error": {
                        "code": "invalid_input",
                        "message": "server type unavailable",
                        "details": { "fields": [{ "name": "server_type" }, { "name": "location" }] }
                      },
                      "root_password": "must-not-escape"
                    }""".trimIndent().toResponseBody("application/json".toMediaType()),
                ),
            )
        }
        val repository = RetrofitHCloudRepository(api)

        val failure = runCatching {
            repository.createServer(CreateServerSpec("web-01", "cpx21", "ubuntu-24.04", "fsn1", true, true))
        }.exceptionOrNull()

        assertTrue(failure is HCloudApiException)
        val apiFailure = (failure as HCloudApiException).failure
        assertEquals(422, apiFailure.httpStatus)
        assertEquals("invalid_input", apiFailure.code)
        assertEquals(listOf("server_type", "location"), apiFailure.fieldNames)
        assertFalse(failure.message!!.contains("must-not-escape"))
    }

    @Test
    fun `get action delegates requested action id`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val action = repository.getAction(9001)

        assertEquals(listOf(9001L), api.requestedActionIds)
        assertEquals(9001L, action.id)
        assertEquals("success", action.status)
    }

    @Test
    fun `server detail and power actions map API responses`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val server = repository.getServer(55)
        val actions = listOf(
            repository.powerOn(55),
            repository.powerOff(55),
            repository.shutdown(55),
            repository.reboot(55),
            repository.reset(55),
        )

        assertEquals(listOf(55L), api.requestedServerIds)
        assertEquals(55L, server.id)
        assertEquals("detail-55", server.name)
        assertEquals("22:00-02:00", server.backupWindow)
        assertEquals(
            listOf("poweron", "poweroff", "shutdown", "reboot", "reset"),
            api.powerActionCalls.map { it.first },
        )
        assertEquals(listOf(800L, 801L, 802L, 803L, 804L), actions.map { it.id })
        assertEquals(listOf("running", "running", "running", "running", "running"), actions.map { it.status })
    }

    @Test
    fun `backup toggles and snapshot creation map cloud actions safely`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val actions = listOf(
            repository.enableBackups(55),
            repository.disableBackups(55),
            repository.createSnapshot(55, "  Before database migration  "),
        )

        assertEquals(
            listOf("enable_backup" to 55L, "disable_backup" to 55L, "create_image" to 55L),
            api.backupImageActionCalls,
        )
        assertEquals("Before database migration", api.lastCreateImageRequest?.description)
        assertEquals("snapshot", api.lastCreateImageRequest?.type)
        assertEquals(listOf(805L, 806L, 807L), actions.map { it.id })
    }

    @Test
    fun `server type pricing uses exact type and location without conversion`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val price = repository.getServerTypePrice(serverTypeId = 21, location = "fsn1")
        val missingLocation = repository.getServerTypePrice(serverTypeId = 21, location = "hel1")

        requireNotNull(price)
        assertEquals("cpx21", price.serverTypeName)
        assertEquals("fsn1", price.location)
        assertEquals("EUR", price.currency)
        assertEquals("19.00", price.vatRate)
        assertEquals("0.0083000000", price.hourly.net)
        assertEquals("0.0098770000", price.hourly.gross)
        assertEquals("5.4900000000", price.monthly.net)
        assertEquals("6.5331000000", price.monthly.gross)
        assertNull(missingLocation)
        assertEquals(2, api.pricingRequests)
    }

    @Test
    fun `server type and location detail retain API availability metadata and prices`() = runBlocking {
        val repository = RetrofitHCloudRepository(PagingApi())

        val type = repository.getServerType(21)
        val location = repository.getLocation(1)

        assertEquals("cpx21", type.name)
        assertEquals(listOf("fsn1-dc14" to true, "hel1-dc2" to false, "nbg1-dc3" to null), type.locations.map { it.name to it.available })
        assertEquals("0.0098770000", type.prices.single().hourly.gross)
        assertEquals("fsn1-dc14", location.name)
        assertEquals("eu-central", location.networkZone)
        assertTrue(runCatching { repository.getServerType(0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.getLocation(0) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `snapshot creation rejects blank descriptions before the network call`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val error = runCatching { repository.createSnapshot(55, "   ") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(emptyList<Pair<String, Long>>(), api.backupImageActionCalls)
    }

    @Test
    fun `volume actions map cloud actions without retrying`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val actions = listOf(
            repository.attachVolume(volumeId = 501, serverId = 42),
            repository.detachVolume(volumeId = 501),
            repository.resizeVolume(volumeId = 501, currentSizeGb = 50, newSizeGb = 80),
        )

        assertEquals(
            listOf("attach" to 501L, "detach" to 501L, "resize" to 501L),
            api.volumeActionCalls,
        )
        assertEquals(42L, api.lastAttachVolumeRequest?.server)
        assertEquals(80, api.lastResizeVolumeRequest?.size)
        assertEquals(listOf(901L, 902L, 903L), actions.map { it.id })
    }

    @Test
    fun `volume resize rejects a non-increasing target before network call`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val sameSize = runCatching { repository.resizeVolume(501, currentSizeGb = 50, newSizeGb = 50) }.exceptionOrNull()
        val smallerSize = runCatching { repository.resizeVolume(501, currentSizeGb = 50, newSizeGb = 49) }.exceptionOrNull()

        assertTrue(sameSize is IllegalArgumentException)
        assertTrue(smallerSize is IllegalArgumentException)
        assertEquals(emptyList<Pair<String, Long>>(), api.volumeActionCalls)
    }

    @Test
    fun `firewall server operations use distinct official request keys and preserve actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val applied = repository.applyFirewallToServer(firewallId = 81, serverId = 42)
        val removed = repository.removeFirewallFromServer(firewallId = 81, serverId = 42)

        assertEquals(listOf("apply" to 81L, "remove" to 81L), api.firewallActionCalls)
        assertEquals(42L, api.lastApplyFirewallRequest?.apply_to?.single()?.server?.id)
        assertEquals(42L, api.lastRemoveFirewallRequest?.remove_from?.single()?.server?.id)
        assertEquals(listOf(1101L), applied.map { it.id })
        assertEquals(listOf(1102L), removed.map { it.id })
    }

    @Test
    fun `firewall lifecycle maps explicit reviewed rule requests`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)
        val rules = listOf(FirewallRuleSpec("in", "tcp", "443", sourceIps = listOf("0.0.0.0/0"), description = "HTTPS"))

        val created = repository.createFirewall(CreateFirewallSpec("edge", rules, mapOf("environment" to "test")))
        val action = repository.setFirewallRules(created.firewall.id, rules)
        repository.deleteFirewall(created.firewall.id)

        assertEquals("edge", api.lastCreateFirewallRequest?.name)
        assertEquals("443", api.lastCreateFirewallRequest?.rules?.single()?.port)
        assertEquals("0.0.0.0/0", api.lastSetFirewallRulesRequest?.rules?.single()?.sourceIps?.single())
        assertEquals(1402L, action.id)
        assertEquals(listOf(1401L), created.actions.map { it.id })
        assertEquals(88L, api.deletedFirewallId)
    }

    @Test
    fun `firewall lifecycle rejects ambiguous directional rules before network call`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)
        val failure = runCatching {
            repository.setFirewallRules(88, listOf(FirewallRuleSpec("in", "tcp", "443", destinationIps = listOf("10.0.0.0/8"))))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(null, api.lastSetFirewallRulesRequest)
    }

    @Test
    fun `repository maps private network attach and detach actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val attached = repository.attachServerToPrivateNetwork(networkId = 801, serverId = 42)
        val detached = repository.detachServerFromPrivateNetwork(networkId = 801, serverId = 42)

        assertEquals(listOf("attach" to 42L, "detach" to 42L), api.privateNetworkActionCalls)
        assertEquals(801L, api.lastAttachNetworkRequest?.network)
        assertEquals(801L, api.lastDetachNetworkRequest?.network)
        assertEquals(1201L, attached.id)
        assertEquals(1202L, detached.id)
    }

    @Test
    fun `recovery actions return consumable passwords and pollable cloud actions`() = runBlocking {
        val api = PagingApi()
        val repository = RetrofitHCloudRepository(api)

        val reset = repository.resetRootPassword(42)
        val rescue = repository.enableRescue(42, EnableRescueSpec(sshKeyIds = listOf(101)))
        val disabled = repository.disableRescue(42)

        assertEquals(808L, reset.action.id)
        assertEquals("reset-once", reset.password.revealOnce())
        assertNull(reset.password.revealOnce())
        assertEquals(809L, rescue.action.id)
        assertEquals("rescue-once", rescue.password.revealOnce())
        assertEquals("linux64", api.lastEnableRescueRequest?.type)
        assertEquals(listOf(101L), api.lastEnableRescueRequest?.sshKeys)
        assertEquals(810L, disabled.id)
    }
}

private class PagingApi : HCloudApi {
    val serverPages = mutableListOf<Int>()
    val serverTypePages = mutableListOf<Int>()
    val imageRequests = mutableListOf<Pair<String, Int>>()
    val fullImagePages = mutableListOf<Int>()
    val isoPages = mutableListOf<Int>()
    val locationPages = mutableListOf<Int>()
    val sshKeyPages = mutableListOf<Int>()
    val volumePages = mutableListOf<Int>()
    val primaryIpPages = mutableListOf<Int>()
    val primaryIpActionPages = mutableListOf<Int>()
    val floatingIpPages = mutableListOf<Int>()
    val floatingIpActionPages = mutableListOf<Int>()
    val certificatePages = mutableListOf<Int>()
    val certificateActionPages = mutableListOf<Int>()
    val firewallPages = mutableListOf<Int>()
    val firewallActionCalls = mutableListOf<Pair<String, Long>>()
    var lastApplyFirewallRequest: FirewallResourcesRequestDto? = null
    var lastRemoveFirewallRequest: FirewallRemoveResourcesRequestDto? = null
    var lastCreateFirewallRequest: CreateFirewallRequestDto? = null
    var lastSetFirewallRulesRequest: SetFirewallRulesRequestDto? = null
    var deletedFirewallId: Long? = null
    val networkPages = mutableListOf<Int>()
    val loadBalancerPages = mutableListOf<Int>()
    val loadBalancerActionPages = mutableListOf<Int>()
    val placementGroupPages = mutableListOf<Int>()
    val requestedActionIds = mutableListOf<Long>()
    val requestedServerIds = mutableListOf<Long>()
    val serverActionPages = mutableListOf<Int>()
    val powerActionCalls = mutableListOf<Pair<String, Long>>()
    val isoActionCalls = mutableListOf<Pair<String, Long>>()
    val placementGroupActionCalls = mutableListOf<Pair<String, Long>>()
    val backupImageActionCalls = mutableListOf<Pair<String, Long>>()
    val volumeActionCalls = mutableListOf<Pair<String, Long>>()
    val privateNetworkActionCalls = mutableListOf<Pair<String, Long>>()
    var lastCreateRequest: CreateServerRequestDto? = null
    var lastCreateImageRequest: CreateImageRequestDto? = null
    var lastAttachVolumeRequest: AttachVolumeRequestDto? = null
    var lastResizeVolumeRequest: ResizeVolumeRequestDto? = null
    var lastCreateVolumeRequest: CreateVolumeRequestDto? = null
    var lastAttachNetworkRequest: AttachServerToNetworkRequestDto? = null
    var lastDetachNetworkRequest: DetachServerFromNetworkRequestDto? = null
    var lastEnableRescueRequest: EnableRescueRequestDto? = null
    var lastServerConfigurationPatch: ServerConfigurationPatchDto? = null
    var lastAttachIsoRequest: AttachIsoRequestDto? = null
    var lastPlacementGroupRequest: PlacementGroupRequestDto? = null
    var lastServerDnsPtrRequest: ServerDnsPtrRequestDto? = null
    var lastAssignFloatingIpRequest: AssignFloatingIpRequestDto? = null
    var lastFloatingIpDnsPtrRequest: ChangeDnsPtrRequestDto? = null
    val floatingIpActionCalls = mutableListOf<Pair<String, Long>>()
    var lastServerProtectionPatch: ServerProtectionPatchDto? = null
    var serverProtectionCalls = 0
    var createServerFailure: Throwable? = null
    var pricingRequests = 0


    override suspend fun getServers(page: Int, perPage: Int): ServersResponse {
        serverPages += page
        return if (page == 1) {
            ServersResponse(listOf(server(1, "web-01")), paginationMeta(nextPage = 2))
        } else {
            ServersResponse(listOf(server(2, "db-01")))
        }
    }

    override suspend fun getServer(serverId: Long): ServerResponse {
        requestedServerIds += serverId
        return ServerResponse(server(serverId, "detail-$serverId", backupWindow = "22:00-02:00"))
    }

    override suspend fun updateServerConfiguration(serverId: Long, patch: ServerConfigurationPatchDto): ServerResponse {
        lastServerConfigurationPatch = patch
        return ServerResponse(server(serverId, patch.name ?: "unchanged"))
    }

    override suspend fun getServerActions(serverId: Long, page: Int, perPage: Int): ActionsResponse {
        serverActionPages += page
        return if (page == 1) {
            ActionsResponse(
                actions = listOf(ActionDto(1301, "running", command = "poweron", started = "2026-10-13T10:00:00Z", progress = 50)),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            ActionsResponse(
                actions = listOf(
                    ActionDto(
                        1302,
                        "error",
                        command = "create_image",
                        finished = "2026-10-13T10:01:00Z",
                        error = ActionErrorDto("action_failed", "The API action failed"),
                    ),
                ),
            )
        }
    }

    override suspend fun getServerMetrics(
        serverId: Long,
        types: String,
        start: String,
        end: String,
        step: String?,
    ): ServerMetricsResponse = error("Metrics are not used by PagingApi")

    override suspend fun getLoadBalancerMetrics(
        loadBalancerId: Long,
        types: String,
        start: String,
        end: String,
        step: String?,
    ): LoadBalancerMetricsResponse = error("Metrics are not used by PagingApi")

    override suspend fun getServerTypes(page: Int, perPage: Int): ServerTypesResponse {
        serverTypePages += page
        return if (page == 1) {
            ServerTypesResponse(
                listOf(
                    ServerTypeDto(
                        1,
                        "cpx21",
                        locations = listOf(
                            ServerTypeLocationDto("fsn1-dc14", true),
                            ServerTypeLocationDto("hel1-dc2", false),
                            ServerTypeLocationDto("nbg1-dc3", null),
                        ),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else ServerTypesResponse(emptyList())
    }

    override suspend fun getServerType(serverTypeId: Long): ServerTypeResponse = ServerTypeResponse(
        ServerTypeDto(
            id = serverTypeId, name = "cpx21", description = "Regular performance", cores = 2,
            memory = 4.0, disk = 80, category = "shared", architecture = "x86",
            locations = listOf(
                ServerTypeLocationDto("fsn1-dc14", true),
                ServerTypeLocationDto("hel1-dc2", false),
                ServerTypeLocationDto("nbg1-dc3", null),
            ),
        ),
    )

    override suspend fun getImages(type: String?, page: Int, perPage: Int): ImagesResponse {
        if (type == null) {
            fullImagePages += page
            return if (page == 1) {
                ImagesResponse(
                    listOf(
                        ImageDto(
                            id = 90,
                            name = "ubuntu-24.04",
                            description = "Ubuntu",
                            type = "system",
                            status = "available",
                            imageSize = 12.5,
                            diskSize = 25.0,
                            created = "2026-10-01T00:00:00+00:00",
                            protection = ImageProtectionDto(deleteProtected = true),
                            deprecation = ImageDeprecationDto(
                                announced = "2026-10-10T00:00:00+00:00",
                                unavailableAfter = "2026-12-01T00:00:00+00:00",
                            ),
                        ),
                    ),
                    paginationMeta(nextPage = 2),
                )
            } else {
                ImagesResponse(listOf(ImageDto(id = 91, name = "before-upgrade", type = "snapshot", status = "available")))
            }
        }
        imageRequests += type to page
        return when (type) {
            "system" -> if (page == 1) {
                ImagesResponse(listOf(ImageDto(1, "ubuntu-24.04", "Ubuntu", "system", "x86")), paginationMeta(nextPage = 2))
            } else {
                ImagesResponse(listOf(ImageDto(2, null)))
            }
            "backup" -> if (page == 1) {
                ImagesResponse(listOf(ImageDto(20, null, "Nightly backup", "backup", created = "2026-01-01T00:00:00+00:00")), paginationMeta(nextPage = 2))
            } else {
                ImagesResponse(listOf(ImageDto(21, null, "Weekly backup", "backup", created = "2026-01-07T00:00:00+00:00")))
            }
            "snapshot" -> if (page == 1) {
                ImagesResponse(listOf(ImageDto(30, "before-upgrade", "Snapshot", "snapshot", created = "2026-02-01T00:00:00+00:00", deprecated = "2026-06-01T00:00:00+00:00")), paginationMeta(nextPage = 2))
            } else {
                ImagesResponse(listOf(ImageDto(31, "after-upgrade", "Snapshot", "snapshot", created = "2026-02-02T00:00:00+00:00")))
            }
            else -> error("Unexpected image type: $type")
        }
    }

    override suspend fun getImage(imageId: Long): ImageResponse = ImageResponse(
        ImageDto(id = imageId, name = "before-upgrade", type = "snapshot", status = "available"),
    )

    override suspend fun getImageActions(imageId: Long, page: Int, perPage: Int): ActionsResponse = ActionsResponse(emptyList())

    override suspend fun deleteImage(imageId: Long) = Unit

    override suspend fun changeImageProtection(imageId: Long, patch: ImageProtectionPatchDto): ActionResponse =
        ActionResponse(ActionDto(911, "running"))

    override suspend fun getIsos(page: Int, perPage: Int): IsosResponse {
        isoPages += page
        return if (page == 1) {
            IsosResponse(listOf(IsoDto(100, "debian-installer", "Debian", "public", "x86")), paginationMeta(nextPage = 2))
        } else {
            IsosResponse(listOf(IsoDto(101, "rescue", "Rescue", "private", "x86")))
        }
    }

    override suspend fun getIso(isoId: Long): IsoResponse = IsoResponse(IsoDto(isoId, "rescue", "Rescue", "private", "x86"))

    override suspend fun getLocations(page: Int, perPage: Int): LocationsResponse {
        locationPages += page
        return if (page == 1) {
            LocationsResponse(listOf(LocationDto(1, "fsn1-dc14", city = "Falkenstein")), paginationMeta(nextPage = 2))
        } else {
            LocationsResponse(listOf(LocationDto(2, "hel1-dc2", city = "Helsinki")))
        }
    }

    override suspend fun getLocation(locationId: Long): LocationResponse = LocationResponse(
        LocationDto(locationId, "fsn1-dc14", "Falkenstein DC 14", "Falkenstein", "DE", "eu-central"),
    )

    override suspend fun getSshKeys(page: Int, perPage: Int): SshKeysResponse {
        sshKeyPages += page
        return if (page == 1) {
            SshKeysResponse(listOf(SshKeyDto(101, "laptop", "aa:bb")), paginationMeta(nextPage = 2))
        } else {
            SshKeysResponse(listOf(SshKeyDto(102, "workstation", "cc:dd")))
        }
    }

    override suspend fun createSshKey(request: CreateSshKeyRequestDto): SshKeyResponse =
        SshKeyResponse(SshKeyDto(103, request.name, "ee:ff"))

    override suspend fun deleteSshKey(sshKeyId: Long) = Unit

    override suspend fun applyFirewallToResources(firewallId: Long, request: FirewallResourcesRequestDto): ActionsResponse {
        firewallActionCalls += "apply" to firewallId
        lastApplyFirewallRequest = request
        return ActionsResponse(listOf(ActionDto(1101, "running")))
    }

    override suspend fun removeFirewallFromResources(firewallId: Long, request: FirewallRemoveResourcesRequestDto): ActionsResponse {
        firewallActionCalls += "remove" to firewallId
        lastRemoveFirewallRequest = request
        return ActionsResponse(listOf(ActionDto(1102, "success")))
    }

    override suspend fun createFirewall(request: CreateFirewallRequestDto): CreatedFirewallResponse {
        lastCreateFirewallRequest = request
        return CreatedFirewallResponse(FirewallDto(id = 88, name = request.name, rules = request.rules.map { FirewallRuleDto(it.direction, it.protocol, it.port, it.sourceIps, it.destinationIps, it.description) }, labels = request.labels), listOf(ActionDto(1401, "success")))
    }

    override suspend fun deleteFirewall(firewallId: Long) { deletedFirewallId = firewallId }

    override suspend fun setFirewallRules(firewallId: Long, request: SetFirewallRulesRequestDto): ActionResponse {
        lastSetFirewallRulesRequest = request
        return ActionResponse(ActionDto(1402, "running", command = "set_firewall_rules"))
    }

    override suspend fun getPricing(): PricingResponse {
        pricingRequests += 1
        return PricingResponse(
            PricingDto(
                currency = "EUR",
                vatRate = "19.00",
                serverTypes = listOf(
                    PricingServerTypeDto(
                        id = 21,
                        name = "cpx21",
                        prices = listOf(
                            PricingServerTypePriceDto(
                                location = "fsn1",
                                priceHourly = MoneyDto(net = "0.0083000000", gross = "0.0098770000"),
                                priceMonthly = MoneyDto(net = "5.4900000000", gross = "6.5331000000"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    override suspend fun getVolumes(page: Int, perPage: Int): VolumesResponse {
        volumePages += page
        return if (page == 1) {
            VolumesResponse(
                listOf(
                    VolumeDto(
                        id = 501,
                        name = "postgres-data",
                        size = 50,
                        location = LocationDto(1, "fsn1-dc14"),
                        server = 42,
                        status = "in-use",
                        protection = VolumeProtectionDto(deleteProtected = true),
                        labels = mapOf("environment" to "production"),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            VolumesResponse(
                listOf(
                    VolumeDto(
                        id = 502,
                        name = "unattached",
                        size = 10,
                        location = LocationDto(2, "hel1-dc2"),
                        server = null,
                        status = "available",
                    ),
                ),
            )
        }
    }

    override suspend fun getVolume(volumeId: Long): VolumeResponse = VolumeResponse(
        VolumeDto(
            id = volumeId,
            name = "volume-$volumeId",
            size = 10,
            location = LocationDto(1, "fsn1-dc14"),
            server = null,
            status = "available",
        ),
    )

    override suspend fun getVolumeActions(volumeId: Long, page: Int, perPage: Int): ActionsResponse =
        ActionsResponse(emptyList())

    override suspend fun createVolume(request: CreateVolumeRequestDto): CreateVolumeResponse {
        lastCreateVolumeRequest = request
        return CreateVolumeResponse(
            volume = VolumeDto(
                id = 503,
                name = request.name,
                size = request.size,
                location = LocationDto(3, request.location),
                status = "creating",
                labels = request.labels,
            ),
            action = ActionDto(904, "running"),
        )
    }

    override suspend fun deleteVolume(volumeId: Long): ActionResponse = volumeAction("delete", volumeId, 905)

    override suspend fun changeVolumeProtection(volumeId: Long, patch: DeleteProtectionPatchDto): ActionResponse =
        volumeAction("change_protection", volumeId, 906)

    override suspend fun getPrimaryIps(page: Int, perPage: Int): PrimaryIpsResponse {
        primaryIpPages += page
        return if (page == 1) {
            PrimaryIpsResponse(
                listOf(
                    PrimaryIpDto(
                        id = 601,
                        name = "frontend-ip",
                        type = "ipv4",
                        ip = "192.0.2.15",
                        location = LocationDto(1, "fsn1-dc14"),
                        assigneeId = 42,
                        assigneeType = "server",
                        protection = PrimaryIpProtectionDto(deleteProtected = true),
                        labels = mapOf("environment" to "production"),
                        dnsPointers = listOf(DnsPointerDto("192.0.2.15", "web.example.test")),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            PrimaryIpsResponse(
                listOf(
                    PrimaryIpDto(
                        id = 602,
                        name = "unassigned-v6",
                        type = "ipv6",
                        ip = "2001:db8::/64",
                        location = LocationDto(2, "hel1-dc2"),
                        assigneeId = null,
                        assigneeType = "unassigned",
                        dnsPointers = listOf(DnsPointerDto("2001:db8::1", null)),
                    ),
                ),
            )
        }
    }

    override suspend fun createPrimaryIp(request: CreatePrimaryIpRequestDto): CreatePrimaryIpResponse =
        CreatePrimaryIpResponse(
            primaryIp = PrimaryIpDto(
                id = 603,
                name = request.name,
                type = request.type,
                ip = "192.0.2.77",
                location = LocationDto(3, request.location),
                assigneeId = request.assigneeId,
                assigneeType = request.assigneeType,
                labels = request.labels,
            ),
            action = request.assigneeId?.let { ActionDto(1004, "running") },
        )

    override suspend fun getPrimaryIp(primaryIpId: Long): PrimaryIpResponse = PrimaryIpResponse(
        PrimaryIpDto(
            id = primaryIpId,
            name = "frontend-ip",
            type = "ipv4",
            ip = "192.0.2.15",
            location = LocationDto(1, "fsn1-dc14"),
            assigneeId = 42,
            assigneeType = "server",
            protection = PrimaryIpProtectionDto(deleteProtected = true),
            labels = mapOf("environment" to "production"),
            dnsPointers = listOf(DnsPointerDto("192.0.2.15", "web.example.test")),
        ),
    )

    override suspend fun deletePrimaryIp(primaryIpId: Long) = Unit

    override suspend fun getPrimaryIpActions(primaryIpId: Long, page: Int, perPage: Int): ActionsResponse {
        primaryIpActionPages += page
        return if (page == 1) {
            ActionsResponse(
                actions = listOf(ActionDto(1601, "running", command = "change_dns_ptr", progress = 50)),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            ActionsResponse(
                actions = listOf(
                    ActionDto(
                        1602,
                        "error",
                        command = "change_dns_ptr",
                        error = ActionErrorDto("action_failed", "PTR update failed"),
                    ),
                ),
            )
        }
    }

    override suspend fun getFloatingIps(page: Int, perPage: Int): FloatingIpsResponse {
        floatingIpPages += page
        return if (page == 1) {
            FloatingIpsResponse(
                floatingIps = listOf(floatingIp(601, "failover-v4", "192.0.2.15", 42L, "server")),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            FloatingIpsResponse(
                floatingIps = listOf(floatingIp(602, "unassigned-v6", "2001:db8::/64", null, null)),
            )
        }
    }

    override suspend fun getFloatingIp(floatingIpId: Long): FloatingIpResponse =
        FloatingIpResponse(floatingIp(floatingIpId, "failover-v4", "192.0.2.15", 42L, "server"))

    override suspend fun getFloatingIpActions(floatingIpId: Long, page: Int, perPage: Int): ActionsResponse {
        floatingIpActionPages += page
        return if (page == 1) {
            ActionsResponse(
                actions = listOf(ActionDto(1701, "running", command = "assign_floating_ip", progress = 25)),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            ActionsResponse(
                actions = listOf(
                    ActionDto(
                        1702,
                        "error",
                        command = "assign_floating_ip",
                        error = ActionErrorDto("action_failed", "Assignment failed"),
                    ),
                ),
            )
        }
    }

    override suspend fun getCertificates(page: Int, perPage: Int): CertificatesResponse {
        certificatePages += page
        return if (page == 1) {
            CertificatesResponse(
                certificates = listOf(certificate(701, "managed-web", "managed")),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            CertificatesResponse(certificates = listOf(certificate(702, "uploaded-api", "uploaded")))
        }
    }

    override suspend fun getCertificate(certificateId: Long): CertificateResponse =
        CertificateResponse(certificate(certificateId, "managed-web", "managed"))

    override suspend fun getCertificateActions(certificateId: Long, page: Int, perPage: Int): ActionsResponse {
        certificateActionPages += page
        return if (page == 1) {
            ActionsResponse(
                actions = listOf(ActionDto(1501, "running", command = "retry_certificate_issuance", progress = 50)),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            ActionsResponse(
                actions = listOf(
                    ActionDto(
                        1502,
                        "error",
                        command = "retry_certificate_issuance",
                        error = ActionErrorDto("action_failed", "Issuance failed"),
                    ),
                ),
            )
        }
    }

    private fun certificate(id: Long, name: String, type: String) = CertificateDto(
        id = id,
        name = name,
        type = type,
        created = "2026-10-01T00:00:00Z",
        notValidBefore = "2026-10-01T00:00:00Z",
        notValidAfter = "2027-10-01T00:00:00Z",
        domainNames = listOf("example.test"),
        fingerprint = "AA:BB:CC",
        status = CertificateStatusDto("completed", "scheduled"),
        usedBy = listOf(CertificateUsageDto(901, "load_balancer")),
        labels = mapOf("environment" to "production"),
    )

    private fun floatingIp(
        id: Long,
        name: String,
        address: String,
        assigneeId: Long?,
        assigneeType: String?,
    ) = FloatingIpDto(
        id = id,
        name = name,
        type = if (address.contains(':')) "ipv6" else "ipv4",
        ip = address,
        homeLocation = LocationDto(1, "fsn1-dc14"),
        assigneeId = assigneeId,
        assigneeType = assigneeType,
        protection = FloatingIpProtectionDto(deleteProtected = true),
        labels = mapOf("environment" to "production"),
        dnsPointers = listOf(DnsPointerDto(address, if (assigneeId == null) null else "web.example.test")),
    )

    override suspend fun getFirewalls(page: Int, perPage: Int): FirewallsResponse {
        firewallPages += page
        return if (page == 1) {
            FirewallsResponse(
                listOf(
                    FirewallDto(
                        id = 701,
                        name = "web-access",
                        rules = listOf(
                            FirewallRuleDto(
                                direction = "in",
                                protocol = "tcp",
                                port = "443",
                                sourceIps = listOf("0.0.0.0/0", "::/0"),
                                description = "Allow HTTPS",
                            ),
                        ),
                        appliedTo = listOf(
                            FirewallAppliedResourceDto(type = "server", server = FirewallServerDto(42)),
                        ),
                        labels = mapOf("environment" to "production"),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            FirewallsResponse(
                listOf(
                    FirewallDto(
                        id = 702,
                        name = "internal-only",
                        rules = listOf(
                            FirewallRuleDto(
                                direction = "out",
                                protocol = "icmp",
                                destinationIps = listOf("10.0.0.0/8"),
                            ),
                        ),
                        appliedTo = listOf(
                            FirewallAppliedResourceDto(
                                type = "label_selector",
                                labelSelector = FirewallLabelSelectorDto("environment=production"),
                                appliedToResources = listOf(
                                    FirewallAppliedResourceDto(type = "server", server = FirewallServerDto(43)),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    override suspend fun getFirewall(firewallId: Long): FirewallResponse = FirewallResponse(
        FirewallDto(id = firewallId, name = "detail-$firewallId"),
    )

    override suspend fun getFirewallActions(firewallId: Long, page: Int, perPage: Int): ActionsResponse =
        ActionsResponse(emptyList())

    override suspend fun getNetworks(page: Int, perPage: Int): NetworksResponse {
        networkPages += page
        return if (page == 1) {
            NetworksResponse(
                listOf(
                    NetworkDto(
                        id = 801,
                        name = "production-private",
                        ipRange = "10.42.0.0/16",
                        subnets = listOf(
                            NetworkSubnetDto(
                                type = "cloud",
                                ipRange = "10.42.1.0/24",
                                networkZone = "eu-central",
                                gateway = "10.42.1.1",
                            ),
                        ),
                        routes = listOf(NetworkRouteDto("10.60.0.0/16", "10.42.1.254")),
                        servers = listOf(42, 43),
                        protection = NetworkProtectionDto(deleteProtected = true),
                        labels = mapOf("environment" to "production"),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            NetworksResponse(
                listOf(
                    NetworkDto(
                        id = 802,
                        name = "staging-private",
                        ipRange = "10.43.0.0/16",
                        subnets = listOf(
                            NetworkSubnetDto(
                                type = "vswitch",
                                ipRange = "10.43.1.0/24",
                                networkZone = "eu-central",
                                vswitchId = 1234,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    override suspend fun getNetwork(networkId: Long): NetworkResponse = NetworkResponse(
        NetworkDto(networkId, "network-$networkId", "10.0.0.0/16"),
    )

    override suspend fun getNetworkMembers(networkId: Long, page: Int, perPage: Int): NetworkMembersResponse =
        NetworkMembersResponse(emptyList())

    override suspend fun getNetworkActions(networkId: Long, page: Int, perPage: Int): ActionsResponse = ActionsResponse(emptyList())
    override suspend fun createNetwork(request: CreateNetworkRequestDto): CreateNetworkResponse = error("unused")
    override suspend fun deleteNetwork(networkId: Long): ActionResponse = error("unused")
    override suspend fun addNetworkSubnet(networkId: Long, request: NetworkSubnetRequestDto): ActionResponse = error("unused")
    override suspend fun deleteNetworkSubnet(networkId: Long, request: NetworkSubnetRequestDto): ActionResponse = error("unused")
    override suspend fun addNetworkRoute(networkId: Long, request: NetworkRouteRequestDto): ActionResponse = error("unused")
    override suspend fun deleteNetworkRoute(networkId: Long, request: NetworkRouteRequestDto): ActionResponse = error("unused")
    override suspend fun changeNetworkProtection(networkId: Long, patch: DeleteProtectionPatchDto): ActionResponse =
        ActionResponse(ActionDto(1203, "running"))

    override suspend fun getLoadBalancers(page: Int, perPage: Int): LoadBalancersResponse {
        loadBalancerPages += page
        return if (page == 1) {
            LoadBalancersResponse(
                listOf(
                    LoadBalancerDto(
                        id = 901,
                        name = "public-web",
                        loadBalancerType = LoadBalancerTypeDto(
                            id = 1,
                            name = "lb11",
                            prices = listOf(
                                LoadBalancerTypePriceDto(
                                    location = "fsn1",
                                    priceHourly = MoneyDto("0.0083000000", "0.0098770000"),
                                    priceMonthly = MoneyDto("5.4900000000", "6.5331000000"),
                                ),
                            ),
                        ),
                        location = LocationDto(1, "fsn1-dc14"),
                        publicNet = LoadBalancerPublicNetDto(
                            enabled = true,
                            ipv4 = LoadBalancerPublicIpDto("192.0.2.80", "lb.example.test"),
                            ipv6 = LoadBalancerPublicIpDto("2001:db8::80", null),
                        ),
                        privateNet = listOf(LoadBalancerPrivateNetDto(801, "10.42.1.10")),
                        services = listOf(LoadBalancerServiceDto("https", 443, 8443, true)),
                        targets = listOf(
                            LoadBalancerTargetDto(
                                type = "label_selector",
                                labelSelector = LoadBalancerTargetLabelSelectorDto("role=web"),
                                usePrivateIp = true,
                                healthStatuses = listOf(LoadBalancerTargetHealthDto(443, "healthy")),
                                targets = listOf(
                                    LoadBalancerTargetDto(type = "server", server = LoadBalancerTargetServerDto(42)),
                                ),
                            ),
                        ),
                        protection = LoadBalancerProtectionDto(deleteProtected = true),
                        labels = mapOf("environment" to "production"),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            LoadBalancersResponse(
                listOf(
                    LoadBalancerDto(
                        id = 902,
                        name = "internal-api",
                        loadBalancerType = LoadBalancerTypeDto(2, "lb21"),
                        location = LocationDto(2, "hel1-dc2"),
                        privateNet = listOf(LoadBalancerPrivateNetDto(802, "10.42.2.10")),
                    ),
                ),
            )
        }
    }

    override suspend fun getLoadBalancer(loadBalancerId: Long): LoadBalancerResponse = LoadBalancerResponse(
        LoadBalancerDto(
            id = loadBalancerId,
            name = "public-web",
            loadBalancerType = LoadBalancerTypeDto(1, "lb11"),
            location = LocationDto(1, "fsn1-dc14"),
            publicNet = LoadBalancerPublicNetDto(enabled = true),
        ),
    )

    override suspend fun deleteLoadBalancer(loadBalancerId: Long): ActionResponse = error("unused")

    override suspend fun getLoadBalancerActions(loadBalancerId: Long, page: Int, perPage: Int): ActionsResponse {
        loadBalancerActionPages += page
        return if (page == 1) {
            ActionsResponse(
                actions = listOf(ActionDto(1401, "running", command = "change_load_balancer_type", progress = 30)),
                meta = paginationMeta(nextPage = 2),
            )
        } else {
            ActionsResponse(
                actions = listOf(
                    ActionDto(
                        1402,
                        "error",
                        command = "add_target",
                        error = ActionErrorDto("action_failed", "Target health check failed"),
                    ),
                ),
            )
        }
    }

    override suspend fun getLoadBalancerTypes(page: Int, perPage: Int): LoadBalancerTypesResponse =
        LoadBalancerTypesResponse(emptyList())

    override suspend fun getLoadBalancerType(loadBalancerTypeId: Long): LoadBalancerTypeResponse =
        LoadBalancerTypeResponse(LoadBalancerTypeDto(loadBalancerTypeId, "lb$loadBalancerTypeId"))

    override suspend fun changeLoadBalancerProtection(loadBalancerId: Long, patch: DeleteProtectionPatchDto): ActionResponse =
        ActionResponse(ActionDto(1403, "running"))

    override suspend fun getPlacementGroups(page: Int, perPage: Int): PlacementGroupsResponse {
        placementGroupPages += page
        return if (page == 1) {
            PlacementGroupsResponse(
                listOf(
                    PlacementGroupDto(
                        id = 1001,
                        name = "web-spread",
                        type = "spread",
                        servers = listOf(42, 43),
                        labels = mapOf("environment" to "production"),
                    ),
                ),
                paginationMeta(nextPage = 2),
            )
        } else {
            PlacementGroupsResponse(
                listOf(PlacementGroupDto(1002, "database-spread", "spread", servers = listOf(44))),
            )
        }
    }

    override suspend fun createPlacementGroup(request: CreatePlacementGroupRequestDto): CreatePlacementGroupResponse = error("unused")

    override suspend fun getPlacementGroup(placementGroupId: Long): PlacementGroupResponse = PlacementGroupResponse(
        PlacementGroupDto(placementGroupId, "web-spread", "spread", servers = listOf(42, 43), labels = mapOf("environment" to "production")),
    )

    override suspend fun updatePlacementGroup(placementGroupId: Long, request: UpdatePlacementGroupRequestDto): PlacementGroupResponse = error("unused")
    override suspend fun deletePlacementGroup(placementGroupId: Long) = error("unused")

    override suspend fun addServerToPlacementGroup(serverId: Long, request: PlacementGroupRequestDto): ActionResponse {
        lastPlacementGroupRequest = request
        placementGroupActionCalls += "add" to serverId
        return ActionResponse(ActionDto(821, "running"))
    }

    override suspend fun removeServerFromPlacementGroup(serverId: Long, request: PlacementGroupRequestDto): ActionResponse {
        lastPlacementGroupRequest = request
        placementGroupActionCalls += "remove" to serverId
        return ActionResponse(ActionDto(822, "running"))
    }

    override suspend fun attachServerToNetwork(serverId: Long, request: AttachServerToNetworkRequestDto): ActionResponse {
        privateNetworkActionCalls += "attach" to serverId
        lastAttachNetworkRequest = request
        return ActionResponse(ActionDto(1201, "running"))
    }

    override suspend fun detachServerFromNetwork(serverId: Long, request: DetachServerFromNetworkRequestDto): ActionResponse {
        privateNetworkActionCalls += "detach" to serverId
        lastDetachNetworkRequest = request
        return ActionResponse(ActionDto(1202, "running"))
    }

    override suspend fun attachVolume(volumeId: Long, request: AttachVolumeRequestDto): ActionResponse {
        lastAttachVolumeRequest = request
        return volumeAction("attach", volumeId, 901)
    }

    override suspend fun detachVolume(volumeId: Long): ActionResponse = volumeAction("detach", volumeId, 902)

    override suspend fun resizeVolume(volumeId: Long, request: ResizeVolumeRequestDto): ActionResponse {
        lastResizeVolumeRequest = request
        return volumeAction("resize", volumeId, 903)
    }

    override suspend fun assignPrimaryIp(primaryIpId: Long, request: AssignPrimaryIpRequestDto): ActionResponse =
        ActionResponse(ActionDto(1001, "running"))

    override suspend fun unassignPrimaryIp(primaryIpId: Long): ActionResponse =
        ActionResponse(ActionDto(1002, "running"))

    override suspend fun changePrimaryIpReverseDns(primaryIpId: Long, request: ChangeDnsPtrRequestDto): ActionResponse =
        ActionResponse(ActionDto(1003, "running"))

    override suspend fun changePrimaryIpProtection(primaryIpId: Long, patch: DeleteProtectionPatchDto): ActionResponse =
        ActionResponse(ActionDto(1005, "running"))

    override suspend fun assignFloatingIp(floatingIpId: Long, request: AssignFloatingIpRequestDto): ActionResponse {
        lastAssignFloatingIpRequest = request
        floatingIpActionCalls += "assign" to floatingIpId
        return ActionResponse(ActionDto(1011, "running"))
    }

    override suspend fun unassignFloatingIp(floatingIpId: Long): ActionResponse {
        floatingIpActionCalls += "unassign" to floatingIpId
        return ActionResponse(ActionDto(1012, "running"))
    }

    override suspend fun changeFloatingIpReverseDns(floatingIpId: Long, request: ChangeDnsPtrRequestDto): ActionResponse {
        lastFloatingIpDnsPtrRequest = request
        floatingIpActionCalls += "dns_ptr" to floatingIpId
        return ActionResponse(ActionDto(1013, "running"))
    }

    override suspend fun changeFloatingIpProtection(floatingIpId: Long, patch: DeleteProtectionPatchDto): ActionResponse =
        ActionResponse(ActionDto(1014, "running"))

    override suspend fun deleteFloatingIp(floatingIpId: Long) {
        floatingIpActionCalls += "delete" to floatingIpId
    }

    override suspend fun deleteServer(serverId: Long) = Unit

    override suspend fun rebuildServer(serverId: Long, request: RebuildServerRequestDto): RootPasswordActionResponse =
        RootPasswordActionResponse(ActionDto(901, "running"), "rebuild-once")

    override suspend fun createServer(request: CreateServerRequestDto): CreateServerResponse {
        createServerFailure?.let { throw it }
        lastCreateRequest = request
        return CreateServerResponse(
            server = server(99, request.name),
            rootPassword = "temporary-root-password",
            action = ActionDto(701, "running"),
            nextActions = listOf(ActionDto(702, "pending"), ActionDto(701, "running")),
        )
    }

    override suspend fun changeServerType(serverId: Long, request: ChangeServerTypeRequestDto): ActionResponse =
        ActionResponse(ActionDto(1, "running"))

    override suspend fun getAction(actionId: Long): ActionResponse {
        requestedActionIds += actionId
        return ActionResponse(ActionDto(actionId, "success"))
    }

    override suspend fun powerOn(serverId: Long): ActionResponse = powerAction("poweron", serverId, 800)

    override suspend fun powerOff(serverId: Long): ActionResponse = powerAction("poweroff", serverId, 801)

    override suspend fun shutdown(serverId: Long): ActionResponse = powerAction("shutdown", serverId, 802)

    override suspend fun reboot(serverId: Long): ActionResponse = powerAction("reboot", serverId, 803)

    override suspend fun reset(serverId: Long): ActionResponse = powerAction("reset", serverId, 804)

    override suspend fun attachIso(serverId: Long, request: AttachIsoRequestDto): ActionResponse {
        lastAttachIsoRequest = request
        isoActionCalls += "attach" to serverId
        return ActionResponse(ActionDto(811, "running"))
    }

    override suspend fun detachIso(serverId: Long): ActionResponse {
        isoActionCalls += "detach" to serverId
        return ActionResponse(ActionDto(812, "running"))
    }

    override suspend fun changeServerReverseDns(serverId: Long, request: ServerDnsPtrRequestDto): ActionResponse {
        lastServerDnsPtrRequest = request
        return ActionResponse(ActionDto(831, "running"))
    }

    override suspend fun changeServerProtection(serverId: Long, request: ServerProtectionPatchDto): ActionResponse {
        lastServerProtectionPatch = request
        serverProtectionCalls += 1
        return ActionResponse(ActionDto(841, "running"))
    }

    override suspend fun resetRootPassword(serverId: Long): RootPasswordActionResponse =
        RootPasswordActionResponse(ActionDto(808, "running"), "reset-once")

    override suspend fun enableRescue(serverId: Long, request: EnableRescueRequestDto): RootPasswordActionResponse {
        lastEnableRescueRequest = request
        return RootPasswordActionResponse(ActionDto(809, "running"), "rescue-once")
    }

    override suspend fun disableRescue(serverId: Long): ActionResponse = ActionResponse(ActionDto(810, "success"))

    override suspend fun enableBackups(serverId: Long): ActionResponse = backupImageAction("enable_backup", serverId, 805)

    override suspend fun disableBackups(serverId: Long): ActionResponse = backupImageAction("disable_backup", serverId, 806)

    override suspend fun createImage(serverId: Long, request: CreateImageRequestDto): ActionResponse {
        lastCreateImageRequest = request
        return backupImageAction("create_image", serverId, 807)
    }

    private fun powerAction(name: String, serverId: Long, actionId: Long): ActionResponse {
        powerActionCalls += name to serverId
        return ActionResponse(ActionDto(actionId, "running"))
    }

    private fun backupImageAction(name: String, serverId: Long, actionId: Long): ActionResponse {
        backupImageActionCalls += name to serverId
        return ActionResponse(ActionDto(actionId, "running"))
    }

    private fun volumeAction(name: String, volumeId: Long, actionId: Long): ActionResponse {
        volumeActionCalls += name to volumeId
        return ActionResponse(ActionDto(actionId, "running"))
    }
}

private fun server(id: Long, name: String, backupWindow: String? = null) = ServerDto(
    id = id,
    name = name,
    status = "running",
    publicNet = PublicNetDto(
        ipv4 = Ipv4Dto("192.0.2.$id"),
        ipv6 = Ipv6Dto("2001:db8:$id::/64"),
        floatingIps = listOf(600L + id),
        primaryIps = listOf(500L + id),
    ),
    location = LocationDto(1, "fsn1-dc14", city = "Falkenstein"),
    serverType = ServerTypeDto(1, "cpx21"),
    backupWindow = backupWindow,
    privateNet = listOf(ServerPrivateNetDto(801, "10.42.1.10", aliasIps = listOf("10.42.1.11"), macAddress = "86:00:00:2a:7d:e0")),
    volumes = listOf(700L + id),
    deviceMaps = listOf(ServerDeviceMapDto(700L + id, "/dev/disk/by-id/scsi-0HC_Volume_${700L + id}")),
)

private fun paginationMeta(nextPage: Int): MetaDto = MetaDto(PaginationDto(nextPage))
