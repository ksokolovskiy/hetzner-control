package com.hcloudcontrol.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.HttpException
import retrofit2.Response

class HCloudDtoContractTest {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `server response uses location and maps snake case network fields`() {
        val response = moshi.adapter(ServersResponse::class.java).fromJson(
            """
            {
              "servers": [{
                "id": 42,
                "name": "edge-01",
                "status": "running",
                "public_net": {
                  "ipv4": { "ip": "192.0.2.42" },
                  "ipv6": { "ip": "2001:db8:42::/64", "dns_ptr": [] },
                  "primary_ips": [501, 502],
                  "floating_ips": [601]
                },
                "private_net": [{ "network": 801, "ip": "10.42.1.10", "alias_ips": ["10.42.1.11"], "mac_address": "86:00:00:2a:7d:e0" }],
                "volumes": [701],
                "device_maps": [{ "volume": 701, "device": "/dev/disk/by-id/scsi-0HC_Volume_701" }],
                "location": { "id": 1, "name": "fsn1-dc14", "city": "Falkenstein", "country": "DE" },
                "server_type": { "id": 1, "name": "cpx21" },
                "backup_window": "22:00-02:00"
              }],
              "meta": { "pagination": { "next_page": 2 } }
            }
            """.trimIndent(),
        )!!

        val server = response.servers.single()
        assertEquals("fsn1-dc14", server.location?.name)
        assertEquals("192.0.2.42", server.publicNet.ipv4?.ip)
        assertEquals("2001:db8:42::/64", server.publicNet.ipv6?.ip)
        assertEquals(listOf(501L, 502L), server.publicNet.primaryIps)
        assertEquals(listOf(601L), server.publicNet.floatingIps)
        assertEquals("10.42.1.11", server.privateNet.single().aliasIps.single())
        assertEquals("86:00:00:2a:7d:e0", server.privateNet.single().macAddress)
        assertEquals(listOf(701L), server.volumes)
        assertEquals("/dev/disk/by-id/scsi-0HC_Volume_701", server.deviceMaps.single().device)
        assertEquals("cpx21", server.serverType.name)
        assertEquals("22:00-02:00", server.backupWindow)
        assertEquals(2, response.meta?.pagination?.nextPage)
    }

    @Test
    fun `create server request and response preserve root password and action contracts`() {
        val request = CreateServerRequestDto(
            name = "web-01",
            serverType = "cpx21",
            image = "ubuntu-24.04",
            location = "fsn1-dc14",
            publicNet = PublicNetRequestDto(enableIpv4 = true, enableIpv6 = false),
            sshKeys = listOf(101, 102),
        )

        val requestJson = moshi.adapter(CreateServerRequestDto::class.java).toJson(request)
        assertTrue(requestJson.contains("\"server_type\":\"cpx21\""))
        assertTrue(requestJson.contains("\"enable_ipv4\":true"))
        assertTrue(requestJson.contains("\"enable_ipv6\":false"))
        assertTrue(requestJson.contains("\"ssh_keys\":[101,102]"))

        val response = moshi.adapter(CreateServerResponse::class.java).fromJson(
            """{
              "server": {
                "id": 99, "name": "web-01", "status": "initializing",
                "public_net": { "ipv4": null },
                "location": { "id": 1, "name": "fsn1-dc14" },
                "server_type": { "id": 1, "name": "cpx21" }
              },
              "root_password": "one-time-password",
              "action": { "id": 701, "status": "running" },
              "next_actions": [
                { "id": 702, "status": "pending" },
                { "id": 703, "status": "running" }
              ]
            }""",
        )!!

        assertEquals("one-time-password", response.rootPassword)
        assertNull(response.server.publicNet.ipv4)
        assertEquals(701L, response.action?.id)
        assertEquals(listOf(702L, 703L), response.nextActions.map(ActionDto::id))
        assertEquals("pending", response.nextActions.first().status)
    }

    @Test
    fun `system image location and server type DTOs decode nullable optional fields`() {
        val images = moshi.adapter(ImagesResponse::class.java).fromJson(
            """{
              "images": [
                { "id": 1, "name": "ubuntu-24.04", "description": "Ubuntu", "type": "system", "architecture": "x86", "created": "2026-01-01T00:00:00+00:00", "deprecated": null },
                { "id": 2, "name": null, "type": "snapshot", "created": "2026-01-02T00:00:00+00:00", "deprecated": "2026-06-01T00:00:00+00:00" }
              ],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!
        val locations = moshi.adapter(LocationsResponse::class.java).fromJson(
            """{ "locations": [{ "id": 3, "name": "hel1-dc2", "city": "Helsinki", "country": "FI" }] }""",
        )!!
        val serverTypes = moshi.adapter(ServerTypesResponse::class.java).fromJson(
            """{
              "server_types": [{
                "id": 4, "name": "cpx21", "cores": 3, "memory": 4.0, "disk": 80,
                "locations": [{ "name": "fsn1-dc14", "available": true }, { "name": "hel1-dc2", "available": false }]
              }]
            }""",
        )!!

        assertEquals("ubuntu-24.04", images.images.first().name)
        assertEquals("2026-01-02T00:00:00+00:00", images.images[1].created)
        assertEquals("2026-06-01T00:00:00+00:00", images.images[1].deprecated)
        assertNull(images.meta?.pagination?.nextPage)
        assertEquals("Helsinki", locations.locations.single().city)
        assertTrue(serverTypes.server_types.single().locations!!.first().available!!)
        assertFalse(serverTypes.server_types.single().locations!![1].available!!)
    }

    @Test
    fun `get action response decodes action id and status`() {
        val response = moshi.adapter(ActionResponse::class.java).fromJson(
            """{ "action": { "id": 9001, "status": "success" } }""",
        )!!

        assertEquals(9001L, response.action.id)
        assertEquals("success", response.action.status)
    }

    @Test
    fun `snapshot image request is constrained to description and snapshot type`() {
        val json = moshi.adapter(CreateImageRequestDto::class.java).toJson(
            CreateImageRequestDto(description = "Before database migration"),
        )

        assertEquals("{\"description\":\"Before database migration\",\"type\":\"snapshot\"}", json)
    }

    @Test
    fun `volume action requests serialize only documented fields`() {
        val attachJson = moshi.adapter(AttachVolumeRequestDto::class.java).toJson(AttachVolumeRequestDto(server = 42))
        val resizeJson = moshi.adapter(ResizeVolumeRequestDto::class.java).toJson(ResizeVolumeRequestDto(size = 80))

        assertEquals("{\"server\":42}", attachJson)
        assertEquals("{\"size\":80}", resizeJson)
    }

    @Test
    fun `ssh key response keeps only safe public metadata`() {
        val response = moshi.adapter(SshKeysResponse::class.java).fromJson(
            """{
              "ssh_keys": [{
                "id": 101,
                "name": "laptop",
                "fingerprint": "aa:bb:cc",
                "public_key": "ssh-ed25519 AAAAC3NzaC1... user@example.test",
                "labels": { "owner": "me" }
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        assertEquals(101L, response.ssh_keys.single().id)
        assertEquals("laptop", response.ssh_keys.single().name)
        assertEquals("aa:bb:cc", response.ssh_keys.single().fingerprint)
        assertNull(response.meta?.pagination?.nextPage)
    }

    @Test
    fun `pricing response preserves currency VAT and exact monetary strings`() {
        val response = moshi.adapter(PricingResponse::class.java).fromJson(
            """{
              "pricing": {
                "currency": "EUR",
                "vat_rate": "19.00",
                "server_types": [{
                  "id": 22,
                  "name": "cpx21",
                  "prices": [{
                    "location": "fsn1",
                    "price_hourly": { "net": "0.0083000000", "gross": "0.0098770000" },
                    "price_monthly": { "net": "5.4900000000", "gross": "6.5331000000" }
                  }]
                }]
              }
            }""",
        )!!

        val pricing = response.pricing
        val price = pricing.serverTypes.single().prices.single()
        assertEquals("EUR", pricing.currency)
        assertEquals("19.00", pricing.vatRate)
        assertEquals("0.0083000000", price.priceHourly.net)
        assertEquals("0.0098770000", price.priceHourly.gross)
        assertEquals("5.4900000000", price.priceMonthly.net)
        assertEquals("6.5331000000", price.priceMonthly.gross)
    }

    @Test
    fun `volume response decodes inventory fields without mutating data`() {
        val response = moshi.adapter(VolumesResponse::class.java).fromJson(
            """{
              "volumes": [{
                "id": 501,
                "name": "postgres-data",
                "size": 50,
                "location": { "id": 1, "name": "fsn1-dc14" },
                "server": 42,
                "status": "in-use",
                "protection": { "delete": true },
                "labels": { "environment": "production", "service": "postgres" }
              }, {
                "id": 502,
                "name": "unattached",
                "size": 10,
                "location": { "id": 2, "name": "hel1-dc2" },
                "server": null,
                "status": "available",
                "protection": { "delete": false },
                "labels": {}
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val attached = response.volumes.first()
        val unattached = response.volumes[1]
        assertEquals(50, attached.size)
        assertEquals("fsn1-dc14", attached.location.name)
        assertEquals(42L, attached.server)
        assertTrue(attached.protection.deleteProtected)
        assertEquals("production", attached.labels["environment"])
        assertNull(unattached.server)
        assertFalse(unattached.protection.deleteProtected)
    }

    @Test
    fun `primary IP response decodes assignment protection labels and DNS pointers`() {
        val response = moshi.adapter(PrimaryIpsResponse::class.java).fromJson(
            """{
              "primary_ips": [{
                "id": 601,
                "name": "frontend-ip",
                "type": "ipv4",
                "ip": "192.0.2.15",
                "location": { "id": 1, "name": "fsn1-dc14" },
                "assignee_id": 42,
                "assignee_type": "server",
                "protection": { "delete": true },
                "labels": { "environment": "production" },
                "dns_ptr": [{ "ip": "192.0.2.15", "dns_ptr": "web.example.test" }]
              }, {
                "id": 602,
                "name": "unassigned-v6",
                "type": "ipv6",
                "ip": "2001:db8::/64",
                "location": { "id": 2, "name": "hel1-dc2" },
                "assignee_id": null,
                "assignee_type": "unassigned",
                "protection": { "delete": false },
                "labels": {},
                "dns_ptr": [{ "ip": "2001:db8::1", "dns_ptr": null }]
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val assigned = response.primary_ips.first()
        val unassigned = response.primary_ips[1]
        assertEquals("192.0.2.15", assigned.ip)
        assertEquals(42L, assigned.assigneeId)
        assertTrue(assigned.protection.deleteProtected)
        assertEquals("web.example.test", assigned.dnsPointers.single().dnsPtr)
        assertNull(unassigned.assigneeId)
        assertEquals("unassigned", unassigned.assigneeType)
        assertNull(unassigned.dnsPointers.single().dnsPtr)
    }

    @Test
    fun `firewall response decodes rules applied resources and labels`() {
        val response = moshi.adapter(FirewallsResponse::class.java).fromJson(
            """{
              "firewalls": [{
                "id": 701,
                "name": "web-access",
                "rules": [{
                  "direction": "in", "protocol": "tcp", "port": "443",
                  "source_ips": ["0.0.0.0/0", "::/0"],
                  "description": "Allow HTTPS"
                }, {
                  "direction": "out", "protocol": "icmp",
                  "destination_ips": ["10.0.0.0/8"]
                }],
                "applied_to": [{
                  "type": "server", "server": { "id": 42 }
                }, {
                  "type": "label_selector", "label_selector": { "selector": "environment=production" },
                  "applied_to_resources": [{ "type": "server", "server": { "id": 43 } }]
                }],
                "labels": { "environment": "production" }
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val firewall = response.firewalls.single()
        assertEquals("web-access", firewall.name)
        assertEquals("443", firewall.rules.first().port)
        assertEquals(listOf("0.0.0.0/0", "::/0"), firewall.rules.first().sourceIps)
        assertEquals(emptyList<String>(), firewall.rules.first().destinationIps)
        assertNull(firewall.rules[1].port)
        assertEquals(listOf("10.0.0.0/8"), firewall.rules[1].destinationIps)
        assertEquals(42L, firewall.appliedTo.first().server?.id)
        assertEquals("environment=production", firewall.appliedTo[1].labelSelector?.selector)
        assertEquals(43L, firewall.appliedTo[1].appliedToResources.single().server?.id)
        assertEquals("production", firewall.labels["environment"])
        assertNull(response.meta?.pagination?.nextPage)
    }

    @Test
    fun `private network response decodes subnets routes servers labels and protection`() {
        val response = moshi.adapter(NetworksResponse::class.java).fromJson(
            """{
              "networks": [{
                "id": 801,
                "name": "production-private",
                "ip_range": "10.42.0.0/16",
                "subnets": [{
                  "type": "cloud", "ip_range": "10.42.1.0/24", "network_zone": "eu-central",
                  "gateway": "10.42.1.1"
                }, {
                  "type": "vswitch", "ip_range": "10.42.2.0/24", "network_zone": "eu-central",
                  "vswitch_id": 1234
                }],
                "routes": [{ "destination": "10.60.0.0/16", "gateway": "10.42.1.254" }],
                "servers": [42, 43],
                "protection": { "delete": true },
                "labels": { "environment": "production" }
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val network = response.networks.single()
        assertEquals("10.42.0.0/16", network.ipRange)
        assertEquals("cloud", network.subnets.first().type)
        assertEquals("10.42.1.1", network.subnets.first().gateway)
        assertEquals(1234L, network.subnets[1].vswitchId)
        assertNull(network.subnets[1].gateway)
        assertEquals("10.60.0.0/16", network.routes.single().destination)
        assertEquals(listOf(42L, 43L), network.servers)
        assertTrue(network.protection.deleteProtected)
        assertEquals("production", network.labels["environment"])
        assertNull(response.meta?.pagination?.nextPage)
    }

    @Test
    fun `load balancer response decodes interfaces services targets protection and prices`() {
        val response = moshi.adapter(LoadBalancersResponse::class.java).fromJson(
            """{
              "load_balancers": [{
                "id": 901,
                "name": "public-web",
                "load_balancer_type": {
                  "id": 1, "name": "lb11",
                  "prices": [{
                    "location": "fsn1", "price_hourly": { "net": "0.0083000000", "gross": "0.0098770000" },
                    "price_monthly": { "net": "5.4900000000", "gross": "6.5331000000" }
                  }]
                },
                "location": { "id": 1, "name": "fsn1-dc14" },
                "public_net": {
                  "enabled": true,
                  "ipv4": { "ip": "192.0.2.80", "dns_ptr": "lb.example.test" },
                  "ipv6": { "ip": "2001:db8::80", "dns_ptr": null }
                },
                "private_net": [{ "network": 801, "ip": "10.42.1.10" }],
                "services": [{ "protocol": "https", "listen_port": 443, "destination_port": 8443, "proxyprotocol": true }],
                "targets": [{
                  "type": "label_selector", "label_selector": { "selector": "role=web" },
                  "use_private_ip": true,
                  "health_status": [{ "listen_port": 443, "status": "healthy" }],
                  "targets": [{ "type": "server", "server": { "id": 42 } }]
                }],
                "protection": { "delete": true },
                "labels": { "environment": "production" }
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val loadBalancer = response.loadBalancers.single()
        assertEquals("lb11", loadBalancer.loadBalancerType.name)
        assertEquals("192.0.2.80", loadBalancer.publicNet.ipv4?.ip)
        assertNull(loadBalancer.publicNet.ipv6?.dnsPtr)
        assertEquals(801L, loadBalancer.privateNet.single().network)
        assertEquals(443, loadBalancer.services.single().listenPort)
        assertTrue(loadBalancer.services.single().proxyProtocol)
        assertEquals("role=web", loadBalancer.targets.single().labelSelector?.selector)
        assertEquals("healthy", loadBalancer.targets.single().healthStatuses.single().status)
        assertEquals(42L, loadBalancer.targets.single().targets.single().server?.id)
        assertTrue(loadBalancer.protection.deleteProtected)
        assertEquals("0.0083000000", loadBalancer.loadBalancerType.prices.single().priceHourly.net)
        assertEquals("production", loadBalancer.labels["environment"])
        assertNull(response.meta?.pagination?.nextPage)
    }

    @Test
    fun `placement group response decodes type servers and labels without inventing protection`() {
        val response = moshi.adapter(PlacementGroupsResponse::class.java).fromJson(
            """{
              "placement_groups": [{
                "id": 1001,
                "name": "web-spread",
                "type": "spread",
                "servers": [42, 43],
                "labels": { "environment": "production" }
              }, {
                "id": 1002,
                "name": "empty-group",
                "type": "spread"
              }],
              "meta": { "pagination": { "next_page": null } }
            }""",
        )!!

        val group = response.placementGroups.first()
        assertEquals("spread", group.type)
        assertEquals(listOf(42L, 43L), group.servers)
        assertEquals("production", group.labels["environment"])
        assertEquals(emptyList<Long>(), response.placementGroups[1].servers)
        assertEquals(emptyMap<String, String>(), response.placementGroups[1].labels)
        assertNull(response.meta?.pagination?.nextPage)
    }

    @Test
    fun `HTTP error mapper retains only structured Hetzner error information`() {
        val response = Response.error<Any>(
            422,
            """{
              "error": {
                "code": "invalid_input",
                "message": "Error while parsing or processing the input",
                "details": {
                  "fields": [
                    { "name": "server_type", "messages": ["is unavailable"] },
                    { "name": "location", "messages": ["is invalid"] },
                    { "name": "server_type", "messages": ["duplicate field"] }
                  ]
                }
              },
              "root_password": "must-not-escape",
              "request": { "Authorization": "Bearer must-not-escape" }
            }""".trimIndent().toResponseBody("application/json".toMediaType()),
        )

        val exception = HCloudApiErrorMapper.from(HttpException(response))

        assertEquals(422, exception.failure.httpStatus)
        assertEquals("invalid_input", exception.failure.code)
        assertEquals("Error while parsing or processing the input", exception.failure.apiMessage)
        assertEquals(listOf("server_type", "location"), exception.failure.fieldNames)
        assertFalse(exception.message!!.contains("must-not-escape"))
        assertFalse(exception.toString().contains("Bearer"))
    }

    @Test
    fun `server detail and power operation endpoints match Cloud API`() {
        assertEquals("servers/{id}", endpoint<HCloudApi, GET>("getServer"))
        assertEquals("servers/{id}/actions/poweron", endpoint<HCloudApi, POST>("powerOn"))
        assertEquals("servers/{id}/actions/poweroff", endpoint<HCloudApi, POST>("powerOff"))
        assertEquals("servers/{id}/actions/shutdown", endpoint<HCloudApi, POST>("shutdown"))
        assertEquals("servers/{id}/actions/reboot", endpoint<HCloudApi, POST>("reboot"))
        assertEquals("servers/{id}/actions/reset", endpoint<HCloudApi, POST>("reset"))
        assertEquals("servers/{id}/actions/enable_backup", endpoint<HCloudApi, POST>("enableBackups"))
        assertEquals("servers/{id}/actions/disable_backup", endpoint<HCloudApi, POST>("disableBackups"))
        assertEquals("servers/{id}/actions/create_image", endpoint<HCloudApi, POST>("createImage"))
        assertEquals("ssh_keys", endpoint<HCloudApi, GET>("getSshKeys"))
        assertEquals("ssh_keys", endpoint<HCloudApi, POST>("createSshKey"))
        assertEquals("ssh_keys/{id}", endpoint<HCloudApi, DELETE>("deleteSshKey"))
        assertEquals("server_types/{id}", endpoint<HCloudApi, GET>("getServerType"))
        assertEquals("locations/{id}", endpoint<HCloudApi, GET>("getLocation"))
        assertEquals("pricing", endpoint<HCloudApi, GET>("getPricing"))
        assertEquals("volumes", endpoint<HCloudApi, GET>("getVolumes"))
        assertEquals("images/{id}/actions", endpoint<HCloudApi, GET>("getImageActions"))
        assertEquals("images/{id}", endpoint<HCloudApi, DELETE>("deleteImage"))
        assertEquals("images/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changeImageProtection"))
        assertEquals("volumes/{id}", endpoint<HCloudApi, GET>("getVolume"))
        assertEquals("volumes/{id}/actions", endpoint<HCloudApi, GET>("getVolumeActions"))
        assertEquals("volumes", endpoint<HCloudApi, POST>("createVolume"))
        assertEquals("volumes/{id}", endpoint<HCloudApi, DELETE>("deleteVolume"))
        assertEquals("volumes/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changeVolumeProtection"))
        assertEquals("primary_ips", endpoint<HCloudApi, GET>("getPrimaryIps"))
        assertEquals("primary_ips", endpoint<HCloudApi, POST>("createPrimaryIp"))
        assertEquals("primary_ips/{id}", endpoint<HCloudApi, GET>("getPrimaryIp"))
        assertEquals("primary_ips/{id}", endpoint<HCloudApi, DELETE>("deletePrimaryIp"))
        assertEquals("primary_ips/{id}/actions", endpoint<HCloudApi, GET>("getPrimaryIpActions"))
        assertEquals("primary_ips/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changePrimaryIpProtection"))
        assertEquals("floating_ips/{id}/actions", endpoint<HCloudApi, GET>("getFloatingIpActions"))
        assertEquals("floating_ips/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changeFloatingIpProtection"))
        assertEquals("firewalls", endpoint<HCloudApi, GET>("getFirewalls"))
        assertEquals("networks", endpoint<HCloudApi, GET>("getNetworks"))
        assertEquals("networks/{id}", endpoint<HCloudApi, GET>("getNetwork"))
        assertEquals("networks/{id}/members", endpoint<HCloudApi, GET>("getNetworkMembers"))
        assertEquals("networks/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changeNetworkProtection"))
        assertEquals("load_balancers", endpoint<HCloudApi, GET>("getLoadBalancers"))
        assertEquals("load_balancers/{id}", endpoint<HCloudApi, DELETE>("deleteLoadBalancer"))
        assertEquals("load_balancers/{id}/actions/change_protection", endpoint<HCloudApi, POST>("changeLoadBalancerProtection"))
        assertEquals("placement_groups", endpoint<HCloudApi, GET>("getPlacementGroups"))
        assertEquals("volumes/{id}/actions/attach", endpoint<HCloudApi, POST>("attachVolume"))
        assertEquals("volumes/{id}/actions/detach", endpoint<HCloudApi, POST>("detachVolume"))
        assertEquals("volumes/{id}/actions/resize", endpoint<HCloudApi, POST>("resizeVolume"))
        assertEquals("servers/{id}/actions/attach_to_network", endpoint<HCloudApi, POST>("attachServerToNetwork"))
        assertEquals("servers/{id}/actions/detach_from_network", endpoint<HCloudApi, POST>("detachServerFromNetwork"))
    }

    @Test
    fun `create volume request only sends documented unattached volume fields`() {
        val json = moshi.adapter(CreateVolumeRequestDto::class.java).toJson(
            CreateVolumeRequestDto(
                name = "postgres-data",
                size = 50,
                location = "fsn1-dc14",
                labels = mapOf("environment" to "production"),
            ),
        )

        assertEquals(
            "{\"name\":\"postgres-data\",\"size\":50,\"location\":\"fsn1-dc14\",\"labels\":{\"environment\":\"production\"}}",
            json,
        )
    }

    @Test
    fun `create primary IP request sends only documented reviewed fields`() {
        val json = moshi.adapter(CreatePrimaryIpRequestDto::class.java).toJson(
            CreatePrimaryIpRequestDto(
                type = "ipv4", name = "edge-ip", location = "fsn1-dc14",
                assigneeType = "server", assigneeId = 42, labels = mapOf("environment" to "production"),
            ),
        )
        assertEquals("{\"type\":\"ipv4\",\"name\":\"edge-ip\",\"location\":\"fsn1-dc14\",\"assignee_type\":\"server\",\"assignee_id\":42,\"labels\":{\"environment\":\"production\"}}", json)
    }

    @Test
    fun `private network attachment requests contain only the existing network ID`() {
        val attach = moshi.adapter(AttachServerToNetworkRequestDto::class.java)
            .toJson(AttachServerToNetworkRequestDto(network = 88))
        val detach = moshi.adapter(DetachServerFromNetworkRequestDto::class.java)
            .toJson(DetachServerFromNetworkRequestDto(network = 88))

        assertEquals("{\"network\":88}", attach)
        assertEquals("{\"network\":88}", detach)
    }
}

private inline fun <reified Api, reified A : Annotation> endpoint(methodName: String): String {
    val method = Api::class.java.declaredMethods.single { it.name == methodName }
    val annotation = method.getAnnotation(A::class.java)
    return when (annotation) {
        is GET -> annotation.value
        is POST -> annotation.value
        is DELETE -> annotation.value
        else -> error("Expected Retrofit endpoint annotation")
    }
}
