#!/usr/bin/env sh
# Static guardrails for source-level security boundaries. Run explicitly or in CI.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SECURITY_STORE="$PROJECT_DIR/core/security/src/main/kotlin/com/hcloudcontrol/core/security/EncryptedHCloudTokenStore.kt"
STORAGE_BOX_STORE="$PROJECT_DIR/core/security/src/main/kotlin/com/hcloudcontrol/core/security/StorageBoxTokenStore.kt"
OBJECT_STORAGE_STORE="$PROJECT_DIR/core/security/src/main/kotlin/com/hcloudcontrol/core/security/ObjectStorageCredentialStore.kt"
OBJECT_STORAGE_UI="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/ObjectStorageScreen.kt"
OBJECT_STORAGE_CLIENT="$PROJECT_DIR/core/network/src/main/kotlin/com/hcloudcontrol/core/network/ObjectStorageS3Repository.kt"
APP_ENTRY="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/MainActivity.kt"
PROVISIONING_UI="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/UnifiedProvisioningWizard.kt"
PROVISIONING_HELPERS="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/ServerProvisioningScreen.kt"
RESIZE_UI="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/UnifiedResizeServerScreen.kt"
OPERATION_SESSION="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/operations/OperationComposition.kt"
OPERATION_PERSISTENCE="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/operations/OperationPersistence.kt"
OPERATION_SECRET="$PROJECT_DIR/app/src/main/kotlin/com/hcloudcontrol/operations/SecretResultDialog.kt"
API_ERROR_MAPPER="$PROJECT_DIR/core/network/src/main/kotlin/com/hcloudcontrol/core/network/HCloudApiErrorMapper.kt"
API_FAILURE="$PROJECT_DIR/core/domain/src/main/kotlin/com/hcloudcontrol/core/domain/model/HCloudApiFailure.kt"
API_DIAGNOSTICS="$PROJECT_DIR/core/network/src/main/kotlin/com/hcloudcontrol/core/network/HCloudApiDiagnostics.kt"

fail() {
    echo "Sensitive-data boundary check failed: $1" >&2
    exit 1
}

require_literal() {
    file="$1"
    literal="$2"
    rg -F -q "$literal" "$file" || fail "missing required contract in ${file#$PROJECT_DIR/}: $literal"
}

for file in "$SECURITY_STORE" "$STORAGE_BOX_STORE" "$OBJECT_STORAGE_STORE" "$OBJECT_STORAGE_UI" "$OBJECT_STORAGE_CLIENT" "$APP_ENTRY" "$PROVISIONING_UI" "$PROVISIONING_HELPERS" "$RESIZE_UI" "$OPERATION_SESSION" "$OPERATION_PERSISTENCE" "$OPERATION_SECRET" "$API_ERROR_MAPPER" "$API_FAILURE" "$API_DIAGNOSTICS"; do
    [ -f "$file" ] || fail "required source file is missing: ${file#$PROJECT_DIR/}"
done

# Token storage must remain encrypted and project-scoped.
require_literal "$SECURITY_STORE" "EncryptedSharedPreferences.create"
require_literal "$SECURITY_STORE" "MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)"
require_literal "$SECURITY_STORE" "EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV"
require_literal "$SECURITY_STORE" "EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM"
require_literal "$SECURITY_STORE" "fun tokenKey(connectionId: String)"
require_literal "$SECURITY_STORE" "putString(tokenKey(connection.id), token.trim())"
require_literal "$SECURITY_STORE" "remove(tokenKey(connectionId))"
require_literal "$SECURITY_STORE" "checkedPreferenceUpdate(preferences,"
require_literal "$OBJECT_STORAGE_STORE" "checkedPreferenceUpdate(preferences,"
require_literal "$PROJECT_DIR/core/security/src/main/kotlin/com/hcloudcontrol/core/security/CheckedPreferences.kt" "if (preferences.edit().apply(update).commit()) return"

# Storage Box is a Cloud-project resource. A historical second credential remains
# encrypted only for explicit removal and must never be returned or reused.
require_literal "$STORAGE_BOX_STORE" "class EncryptedLegacyStorageBoxCredentialStore"
require_literal "$STORAGE_BOX_STORE" "EncryptedSharedPreferences.create"
require_literal "$STORAGE_BOX_STORE" "MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)"
require_literal "$STORAGE_BOX_STORE" 'const val FILE_NAME = "storage_box_secure_credentials"'
require_literal "$STORAGE_BOX_STORE" ".remove(tokenKey(credentialId))"
if rg -n -e 'fun token\(' -e 'getString\(tokenKey' "$STORAGE_BOX_STORE"; then
    fail "legacy Storage Box credential secret appears readable or reusable"
fi

# The token provider may enter the network repository, but must not be read into
# Compose UI state or a Text composable.
require_literal "$APP_ENTRY" "ProjectApiClients(com.hcloudcontrol.connections.PinnedCloudConnection(tokenStore, owner))"
require_literal "app/src/main/kotlin/com/hcloudcontrol/connections/ProjectApiClients.kt" "HCloudRepositoryFactory.create(binding::token)"
require_literal "app/src/main/kotlin/com/hcloudcontrol/connections/ProjectApiClients.kt" "StorageBoxRepositoryFactory.create(binding::token)"
require_literal "app/src/main/kotlin/com/hcloudcontrol/connections/ProjectApiClients.kt" "store.tokenForConnection(connection.id)"
if rg -n -e 'tokenStore\.token\(\)' -e 'mutableStateOf\(tokenStore\.token' -e 'Text\([^)]*tokenStore\.token' "$APP_ENTRY"; then
    fail "active API token appears to cross into UI state or rendering"
fi
# Object Storage is a third credential boundary: both keys are encrypted at
# rest, never rendered, and only supplied to the SigV4 client in memory.
require_literal "$OBJECT_STORAGE_STORE" "class EncryptedObjectStorageCredentialStore"
require_literal "$OBJECT_STORAGE_STORE" "EncryptedSharedPreferences.create"
require_literal "$OBJECT_STORAGE_STORE" 'const val FILE_NAME = "object_storage_secure_credentials"'
require_literal "$OBJECT_STORAGE_STORE" ".putString(accessKey(connection.id), accessKey.trim())"
require_literal "$OBJECT_STORAGE_STORE" ".putString(secretKey(connection.id), secretKey.trim())"
require_literal "$OBJECT_STORAGE_STORE" ".remove(accessKey(connectionId)).remove(secretKey(connectionId))"
require_literal "$OBJECT_STORAGE_CLIENT" "AWS4-HMAC-SHA256 Credential="
require_literal "$OBJECT_STORAGE_CLIENT" "credentials.location.host"
if rg -n -e 'Text\([^)]*(accessKey|secretKey|credentials\.)' -e 'println\(' -e 'Log\.' "$OBJECT_STORAGE_UI" "$OBJECT_STORAGE_CLIENT" "$OBJECT_STORAGE_STORE"; then
    fail "Object Storage credential material may reach UI or logs"
fi

# Root passwords must never be introduced into encrypted token storage. The UI
# may hold one briefly to display the one-time response, but it must not use a
# persistence API in the provisioning flow.
if rg -n -i -e 'root_?password' "$SECURITY_STORE"; then
    fail "root password must not be persisted in the credential store"
fi
if rg -n -i -e 'rootPassword.*(putString|writeText|writeBytes|FileOutputStream|DataStore)' "$PROVISIONING_UI" "$PROVISIONING_HELPERS" "$RESIZE_UI"; then
    fail "root password appears to be persisted by the provisioning UI"
fi

# Create-server failures are intentionally generic in Compose. An HTTP error may
# contain untrusted server text, so never render or assign a throwable message,
# cause, or stack trace to the provisioning error state. The mapper's exception
# message is also constrained to status/code, not the raw response body.
require_literal "$PROVISIONING_UI" 'session.rejectOrUnknown(throwable)'
require_literal "$RESIZE_UI" 'session.rejectOrUnknown(throwable)'
require_literal "$PROVISIONING_UI" 'session.beginReviewed(proof, allowed, currentContext)'
require_literal "$PROVISIONING_UI" 'OperationResultPresentation(record'
require_literal "$PROVISIONING_UI" 'SecretResultDialog(secret)'
require_literal "$PROVISIONING_HELPERS" 'internal fun safeCreateServerFieldNames(fieldNames: List<String>): List<String>'
require_literal "$OPERATION_SESSION" '(throwable as? HCloudApiException)?.failure?.httpStatus'
require_literal "$OPERATION_SESSION" 'current.stage == OperationStage.SENDING'
require_literal "$OPERATION_SECRET" 'securePolicy = SecureFlagPolicy.SecureOn'
require_literal "$OPERATION_SECRET" "chars.fill('\\u0000')"
if rg -n -i -e 'writeUTF\([^)]*(rootPassword|password|token|secret)' -e '\.(message|cause|stackTrace)' "$OPERATION_PERSISTENCE" "$OPERATION_SESSION"; then
    fail "operation persistence/error boundary may retain secret text or raw exception details"
fi
if rg -n -e 'error[[:space:]]*=[[:space:]]*(throwable|exception)(\.message|\.cause|\.stackTrace)?' \
    -e 'InlineError\([^)]*(throwable|exception)' -e 'Text\([^)]*(throwable|exception)' "$PROVISIONING_UI" "$PROVISIONING_HELPERS" "$RESIZE_UI"; then
    fail "provisioning error UI appears to render exception details"
fi
if rg -n 'failure\.apiMessage' "$PROVISIONING_UI" "$PROVISIONING_HELPERS" "$RESIZE_UI"; then
    fail "untrusted API error text must not be rendered by the provisioning UI"
fi
require_literal "$API_FAILURE" '"Hetzner Cloud API request failed (HTTP ${failure.httpStatus}'
if rg -n -e 'RuntimeException\([^)]*apiMessage' -e 'RuntimeException\([^)]*errorBody' "$API_FAILURE"; then
    fail "raw API response content appears in the mapped exception message"
fi
if rg -n --glob '*.kt' 'apiMessage' "$PROJECT_DIR/app/src/main" "$PROJECT_DIR/core/security/src/main"; then
    fail "raw API error text must not cross into the UI or credential store"
fi

# Central Logcat diagnostics must use generated metadata, not untrusted response
# text, error field names, headers, or HTTP bodies. Keep all direct Log calls in
# this one audited adapter.
require_literal "$API_DIAGNOSTICS" 'apiMessage = code?.let { "Hetzner API error code: $it" }'
require_literal "$API_DIAGNOSTICS" 'fieldNames = emptyList()'
require_literal "$API_DIAGNOSTICS" 'fields=-'
if rg -n -e 'apiMessage = apiMessage' -e 'fieldNames = fieldNames' \
    -e 'request\.headers' -e 'request\.body' -e 'response\.headers' "$API_DIAGNOSTICS"; then
    fail "diagnostics appears to retain raw HTTP or API response data"
fi

# Production sources must not introduce direct logging calls, which are a common
# accidental disclosure path for bearer tokens and one-time credentials.
LOG_SOURCES="$(rg -l --glob '*.kt' -e '\bLog\.(v|d|i|w|e|wtf)\(' -e '\bprintln\(' -e '\bprint\(' \
    "$PROJECT_DIR/app/src/main" "$PROJECT_DIR/core"/*/src/main || true)"
if [ "$LOG_SOURCES" != "$API_DIAGNOSTICS" ] && [ -n "$LOG_SOURCES" ]; then
    fail "direct production logging call found"
fi

echo "Sensitive-data boundary check passed."
