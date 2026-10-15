package com.hcloudcontrol.operations

import org.junit.Assert.*
import org.junit.Test
import com.hcloudcontrol.core.domain.model.OneTimeRootPassword
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class OperationCoordinatorTest {
    private val context = OperationContext("project-1", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
    private val resource = ResourceIdentity(context, "server", "42")
    private val intent = OperationIntent("attempt-1", resource, "shutdown", 1)
    private class Store : OperationStore {
        var data = emptyList<PersistedOperation>()
        var rejectWrites = false
        override fun load() = data
        override fun save(records: List<PersistedOperation>) { check(!rejectWrites); data = records }
    }
    private fun start(coordinator: OperationCoordinator, operation: OperationIntent = intent) =
        coordinator.beginSubmission(OperationReview(operation, 100, true), operation.resource.context, operation.revision, true)

    @Test fun `double submit and recreated unknown cannot repeat mutation`() {
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        start(coordinator)
        assertThrows(IllegalStateException::class.java) { start(coordinator) }
        val restored = OperationCoordinator(store) { 110 }
        assertEquals(OperationStage.OUTCOME_UNKNOWN, restored.get(intent.intentId)?.stage)
        assertThrows(IllegalStateException::class.java) { start(restored) }
    }

    @Test fun `read retry clears refresh warning without changing successful write or original action`() {
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88)
        coordinator.succeeded(intent.intentId)
        coordinator.refreshFailed(intent.intentId)
        val result = coordinator.refreshSucceeded(intent.intentId)
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertEquals(88L, result.providerActionId)
        assertFalse(result.refreshFailed)
        assertEquals(result, OperationCoordinator(store) { 110 }.get(intent.intentId))
    }

    @Test fun `accepted identity survives recreation with original context`() {
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88)
        val restored = OperationCoordinator(store) { 120 }
        val persisted = restored.get(intent.intentId)!!
        assertEquals(OperationStage.ACCEPTED, persisted.stage)
        assertEquals(context, persisted.intent.resource.context)
        assertEquals(88L, persisted.providerActionId)
        assertThrows(IllegalStateException::class.java) {
            restored.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatus(89, OperationCoordinator.Reconciliation.Status.SUCCEEDED))
        }
    }

    @Test fun `lost response has unknown outcome and ambiguous read cannot succeed`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.outcomeUnknown(intent.intentId)
        val record = coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.Ambiguous)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, record.stage)
        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ExistingResourceEffect(resource.copy(context = context.copy(connectionId = "project-2")), true))
        }
    }

    @Test fun `creation without action ID cannot reconcile by a matching name`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val create = intent.copy(resource = resource.copy(resourceId = null), action = "create")
        start(coordinator, create)
        coordinator.outcomeUnknown(create.intentId)
        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile(create.intentId, OperationCoordinator.Reconciliation.ExistingResourceEffect(create.resource, true))
        }
        assertEquals(OperationStage.OUTCOME_UNKNOWN, coordinator.get(create.intentId)?.stage)
    }

    @Test fun `confirmed success survives refresh error and allows done`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88)
        coordinator.running(intent.intentId)
        coordinator.succeeded(intent.intentId)
        val result = coordinator.refreshFailed(intent.intentId)
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertTrue(result.refreshFailed)
        assertTrue(result.canClose)
        assertThrows(IllegalStateException::class.java) { coordinator.outcomeUnknown(intent.intentId) }
    }

    @Test fun `gate context revision acknowledgement and age rechecked before send`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val review = OperationReview(intent, 100, true)
        assertThrows(IllegalStateException::class.java) { coordinator.beginSubmission(review, context, 1, false) }
        assertThrows(IllegalStateException::class.java) { coordinator.beginSubmission(review, context.copy(connectionId = "other"), 1, true) }
        assertThrows(IllegalStateException::class.java) { coordinator.beginSubmission(review, context, 2, true) }
        assertThrows(IllegalStateException::class.java) { coordinator.beginSubmission(review.copy(acknowledged = false), context, 1, true) }
        assertThrows(IllegalStateException::class.java) { coordinator.beginSubmission(review.copy(reviewedAtMillis = 1), context, 1, true, 10) }
        assertTrue(coordinator.all().isEmpty())
    }

    @Test fun `persistence failure prevents submission lock being committed`() {
        val store = Store().apply { rejectWrites = true }
        val coordinator = OperationCoordinator(store) { 100 }
        assertThrows(IllegalStateException::class.java) { start(coordinator) }
        assertTrue(coordinator.all().isEmpty())
    }

    @Test fun `another intent cannot mutate resource while original outcome unknown`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.outcomeUnknown(intent.intentId)
        assertThrows(IllegalStateException::class.java) { start(coordinator, intent.copy(intentId = "attempt-2", action = "delete")) }
    }

    @Test fun `transport error cannot be presented as confirmed provider failure`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.failed(intent.intentId, OperationDiagnostic(OperationDiagnostic.Code.TRANSPORT_LOST))
        }
        assertEquals(OperationStage.SENDING, coordinator.get(intent.intentId)?.stage)
    }

    @Test fun `new creation intent cannot bypass unknown scope lock after recreation`() {
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        val create = intent.copy(resource = resource.copy(resourceId = null), action = "create")
        start(coordinator, create)
        val restored = OperationCoordinator(store) { 100 }
        assertThrows(IllegalStateException::class.java) { start(restored, create.copy(intentId = "another-creation")) }
        assertEquals(OperationStage.OUTCOME_UNKNOWN, restored.get(create.intentId)?.stage)
    }

    @Test fun `read reconciliation resolves original action without replay`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88)
        coordinator.outcomeUnknown(intent.intentId)
        val result = coordinator.reconcile(intent.intentId,
            OperationCoordinator.Reconciliation.ActionStatus(88, OperationCoordinator.Reconciliation.Status.SUCCEEDED))
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertEquals(intent, result.intent)
    }

    @Test fun `safe codec roundtrips only whitelisted fields`() {
        var bytes: ByteArray? = null
        val store = OperationPersistence({ bytes }, { bytes = it })
        val coordinator = OperationCoordinator(store) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88)
        assertEquals(coordinator.all(), store.load())
        assertThrows(IllegalArgumentException::class.java) { context.copy(endpoint = "https://token:secret@api.hetzner.cloud/v1") }
        assertThrows(IllegalArgumentException::class.java) { context.copy(endpoint = "https://api.hetzner.cloud/v1?token=secret") }
    }

    @Test fun `secret result is transient redacted masked and explicitly destroyed`() {
        val source = "synthetic-password".toCharArray()
        val secret = SecretResult(source)
        source.fill('x')
        assertFalse(secret.revealed)
        assertFalse(secret.canFinish)
        assertFalse(secret.toString().contains("synthetic"))
        assertEquals("synthetic-password", String(secret.copyForExplicitUserAction()))
        secret.reveal()
        assertTrue(secret.revealed)
        secret.acknowledge()
        assertTrue(secret.canFinish)
        secret.close()
        assertFalse(secret.available)
        assertTrue(secret.canFinish)
        assertThrows(IllegalStateException::class.java) { secret.copyForExplicitUserAction() }
    }

    @Test fun `already consumed password yields lost result without any mutation callback`() {
        val password = OneTimeRootPassword.fromApiResponse("synthetic-only")
        val first = consumeSecretResult(password)
        assertNotNull(first)
        assertNull(consumeSecretResult(password))
        first!!.close()
    }

    @Test fun `all original actions must succeed and missing evidence stays unknown`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88, "42", relatedActionIds = listOf(89), secretIssued = true)
        assertThrows(IllegalStateException::class.java) { coordinator.succeeded(intent.intentId) }
        val partial = coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatuses(mapOf(88L to OperationCoordinator.Reconciliation.Status.SUCCEEDED)))
        assertEquals(OperationStage.OUTCOME_UNKNOWN, partial.stage)
        val running = coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatuses(mapOf(
            88L to OperationCoordinator.Reconciliation.Status.SUCCEEDED, 89L to OperationCoordinator.Reconciliation.Status.RUNNING)))
        assertEquals(OperationStage.RUNNING, running.stage)
        val success = coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatuses(mapOf(
            88L to OperationCoordinator.Reconciliation.Status.SUCCEEDED, 89L to OperationCoordinator.Reconciliation.Status.SUCCEEDED)))
        assertEquals(OperationStage.SUCCEEDED, success.stage)
        assertTrue(success.secretIssued)
    }
    @Test fun `unknown action ID cannot resolve original operation bundle`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        coordinator.accepted(intent.intentId, 88, relatedActionIds = listOf(89))
        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatuses(mapOf(90L to OperationCoordinator.Reconciliation.Status.SUCCEEDED)))
        }
        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile(intent.intentId, OperationCoordinator.Reconciliation.ActionStatus(88, OperationCoordinator.Reconciliation.Status.SUCCEEDED))
        }
    }
    @Test fun `accepted creation locks returned server ID against another mutation`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val create = intent.copy(resource = resource.copy(resourceId = null), action = "create_server")
        start(coordinator, create)
        coordinator.accepted(create.intentId, 88, "42")
        assertThrows(IllegalStateException::class.java) { start(coordinator, intent.copy(intentId = "delete-created-server", action = "delete")) }
    }
    @Test fun `codec version one pending action is read without resetting original identity`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { data ->
            with(data) {
                writeInt(1); writeInt(1)
                writeUTF(context.connectionId); writeUTF(context.endpoint); writeUTF(context.environment.name)
                writeUTF(resource.resourceType); writeBoolean(true); writeUTF(resource.resourceId!!)
                writeUTF(intent.intentId); writeUTF(intent.action); writeLong(intent.revision)
                writeUTF(OperationStage.ACCEPTED.name); writeLong(100); writeLong(88)
                writeBoolean(false); writeBoolean(false); writeBoolean(false)
            }
        }
        val records = OperationPersistence({ bytes.toByteArray() }, {}).load()
        assertEquals(listOf(88L), records.single().originalActionIds)
        assertEquals(intent, records.single().intent)
        assertFalse(records.single().secretIssued)
    }

    @Test fun `only sending provider rejection is failure while accepted read denial remains unknown`() {
        val denial = HCloudApiException(HCloudApiFailure(403, "forbidden", "sensitive-provider-message", emptyList()))
        val first = OperationCoordinator(Store()) { 100 }
        start(first)
        OperationSession(context, first, intent, 100).rejectOrUnknown(denial)
        assertEquals(OperationStage.FAILED, first.get(intent.intentId)?.stage)
        assertEquals(OperationDiagnostic.Code.PROVIDER_DENIED, first.get(intent.intentId)?.diagnostic?.code)
        assertFalse(first.all().toString().contains("sensitive-provider-message"))
        val accepted = OperationCoordinator(Store()) { 100 }
        start(accepted); accepted.accepted(intent.intentId, 88)
        OperationSession(context, accepted, intent, 100).rejectOrUnknown(denial)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, accepted.get(intent.intentId)?.stage)
    }

    @Test fun `gateway timeout after submission cannot be classified as rejected`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        start(coordinator)
        OperationSession(context, coordinator, intent, 100).rejectOrUnknown(
            HCloudApiException(HCloudApiFailure(408, "timeout", null, emptyList())))
        assertEquals(OperationStage.OUTCOME_UNKNOWN, coordinator.get(intent.intentId)?.stage)
    }

    @Test fun `creation without actions needs exact returned ID and stable effect evidence`() {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val create = intent.copy(resource = resource.copy(resourceId = null), action = "create_server")
        start(coordinator, create)
        coordinator.accepted(create.intentId, createdResourceId = "42")
        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile(create.intentId, OperationCoordinator.Reconciliation.CreatedResourceEffect(resource.copy(resourceId = "43"), true))
        }
        assertEquals(OperationStage.OUTCOME_UNKNOWN, coordinator.reconcile(create.intentId,
            OperationCoordinator.Reconciliation.CreatedResourceEffect(resource, false)).stage)
        assertEquals(OperationStage.SUCCEEDED, coordinator.reconcile(create.intentId,
            OperationCoordinator.Reconciliation.CreatedResourceEffect(resource, true)).stage)
    }
}
