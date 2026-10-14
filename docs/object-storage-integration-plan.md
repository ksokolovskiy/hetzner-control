# Hetzner Object Storage — integration plan

Date: 2026-10-14
Status: product and security design only; no S3 credential, Bucket, or object
has been created by this project.

## Decision

Add **Object Storage** as a distinct product under **More → Separate Hetzner
products**, alongside (but never inside) Storage Boxes. It must not be called
"Storage" without qualification, and it must never reuse the selected Hetzner
Cloud API token or a Storage Box API token.

This is a separate S3-compatible service. A Bucket is standalone object
storage; it is not a Cloud Volume (block device attached to a server), and it
is not a Storage Box (separate API host and backup product).

## What the official interfaces permit

### Control planes

| Task | Official interface | App consequence |
| --- | --- | --- |
| Create S3 credentials | Hetzner Console → project → **Security → S3 Credentials** | The app cannot generate them. It can only explain the one-time Console step and accept an already-created key pair. The secret is shown once only and cannot be retrieved later. |
| Add the `protected` label to a Bucket | Hetzner Console | Do not offer a fake S3/Cloud-API toggle. Show its state only when a reliable source exists; link users to Console for this setting. |
| Bucket and object operations | Hetzner's S3-compatible API with AWS Signature Version 4 | A dedicated S3 client is required; Cloud REST DTOs and `Authorization: Bearer` are invalid here. |
| Account storage/traffic utilization or S3 credential management | No dedicated Hetzner API exists | Do not show invented totals, balance, invoices, credentials inventory, or usage figures. |

The Hetzner Cloud REST API has no Object Storage resource. Its official
overview explicitly describes Object Storage as standard S3 without
Hetzner-specific extensions. Therefore a valid Cloud project token cannot
authenticate Bucket calls.

### Endpoints and authentication

Object Storage is available at these location-bound endpoints:

| Location | Endpoint |
| --- | --- |
| Falkenstein | `https://fsn1.your-objectstorage.com` |
| Nuremberg | `https://nbg1.your-objectstorage.com` |
| Helsinki | `https://hel1.your-objectstorage.com` |

Bucket object requests use the virtual-host form
`https://<bucket>.<location>.your-objectstorage.com/<object-key>`.
Each request needs the S3 **access key** and an AWS SigV4 signature made with
the **secret key**; neither a Cloud bearer token nor a Storage Box lifecycle
request is a substitute. The endpoint region used by SigV4 is the selected location (for
example `fsn1`), and service is `s3`.

By default, one S3 key pair grants access to every Bucket in its Console
project. Hetzner documents a separate key pair per project unless deliberate
Bucket policies restrict access. This is not a cosmetic profile convention:
it is the least-privilege default the app must preserve.

## Product placement and information architecture

**Canonical home:** `More → Separate Hetzner products → Object Storage`.

This screen starts with an empty **Connect Object Storage** state, not a Cloud
project picker. It offers:

1. A short setup checklist: create a project-specific S3 credential in the
   Console, copy the secret immediately, then return to the app.
2. A connection form: local display name, endpoint/location, access key and
   secret key.
3. A connection list/switcher separate from Cloud projects and Storage Box
   connections. The display name may say which Cloud Console project it belongs
   to, but this is user metadata, not a verified relationship exposed by the
   Cloud REST API.
4. A Bucket inventory, followed by Bucket detail / object browser.

The Storage primary tab remains for **Cloud Volumes, images/snapshots and
backups**. Storage Boxes remain an advanced separate product. Object Storage
must not be silently nested in either screen, because users must immediately
see which credential and data model they are operating on.

## Credential and profile model

Introduce a third explicit credential boundary:

```
Cloud project profile       = display name + Cloud REST bearer token
Storage Box connection      = display name + Storage Box API token
Object Storage connection   = display name + location + S3 access key + S3 secret key
```

Implementation requirements for a future increment:

- Store each S3 connection in a **new encrypted preference file / Keystore
  boundary**, never as a field on `HCloudConnectionConfig` or
  `StorageBoxConnection`.
- S3 access key and secret key are both secrets. Never display either after
  save; re-entry replaces them. Never persist them in UI state, saved state,
  clipboard, logs, error messages, diagnostics, test fixtures, screenshots or
  analytics.
- The signing layer constructs authorization headers in memory per request.
  Its diagnostics must redact `Authorization`, `x-amz-*` security material,
  presigned query signatures, and error bodies that can echo request data.
- Use HTTPS only; pin the service base URL to the selected official endpoint
  rather than accepting arbitrary endpoint URLs. Do not offer a custom
  endpoint field in the normal product UI.
- Verify credentials with a minimally invasive, explicitly described `ListBuckets`
  request. A failed connection must not disclose a key, signature, canonical
  request, or raw S3 error XML.
- Removing a connection must delete its local encrypted access and secret keys.
  There is no mobile recovery path: the user creates a new credential in
  Console if the secret was lost.
- Do not create or distribute S3 credentials from the app. A phone compromise
  of a broadly scoped S3 key can expose every Bucket in its project; users
  should prefer a dedicated project and restrictive Bucket policy for mobile
  access when appropriate.

## Recommended phased capability

### Phase 1 — safe, useful read-only MVP

- Add and validate an Object Storage connection.
- List Buckets and open Bucket detail.
- Browse objects by prefix, paginate, display object key, size, timestamp,
  storage class/ETag only where returned; make the flat S3 namespace explicit.
- Generate neither permanent public links nor a public/private policy change.
- For public objects, show a copyable ordinary URL only after a clear
  indication that the Bucket/object policy already makes it public.
- No claimed account storage/traffic/billing number; the official API does not
  provide one.

This phase reads real data but still requires a powerful S3 secret. It should
ship only with the credential safeguards and redaction tests above.

### Phase 2 — opt-in file transfer

- Android system document picker for upload/download; stream content, enforce
  visible filename/key review and cancellation.
- Multipart transfer for large files, background-work constraints, retry only
  when idempotence is known, and no retained plaintext cache by default.
- Upload is a write. If a key already exists, the review must say that the
  existing object is replaced (or a new version is created when versioning is
  active).

### Phase 3 — reviewed management, only with the existing Changes permission

- Create Bucket with name/location/private-default and an explicit base-price
  acknowledgement; optional Object Lock only at creation, with a non-reversible
  explanation.
- Delete an object only with exact-key confirmation and a versioning/Object
  Lock warning.
- Delete a Bucket only after a fresh empty-Bucket check, explicit protection
  warning, exact-name confirmation, and the shared high-impact capability.
- Versioning, policy, public visibility, lifecycle, retention and legal hold
  require their own threat-model and review design; do not expose raw policy
  JSON as a casual mobile toggle.

## Explicit non-goals for the first implementation

- No server-side proxy and no attempt to make Cloud REST manage Buckets.
- No hidden shared credential with the active Cloud project.
- No creation/listing/recovery of S3 credentials in the app.
- No account balance, invoice, usage, Bucket protection mutation, or pricing
  estimate presented as API data.
- No custom arbitrary S3 provider endpoint; this is Hetzner Object Storage
  support, not a generic S3 manager.
- No automatic public access, policy editing, presigned URL generation,
  SSE-C key storage, object lock/retention, or lifecycle-policy mutation in
  the MVP.

## Acceptance gates before coding

1. Re-check the current official S3 compatibility matrix and the precise
   request/response form for each API call; add a contract test with no real
   credential.
2. Add source-level checks proving that S3 access/secret values cannot reach
   app logs, diagnostics, crash/error UI, bundles, or fixtures.
3. Test switching/removing Object Storage connections and ensure no S3
   request can be sent after removal.
4. Test every list → Bucket → object route with the shared back-stack and
   saved list-scroll-state requirement.
5. Localize every new setup, danger, error, empty and accessibility string in
   English, Russian and German.

## Official sources

- [Object Storage overview and official endpoints](https://docs.hetzner.com/storage/object-storage/overview/)
- [Generating S3 credentials in Hetzner Console](https://docs.hetzner.com/storage/object-storage/getting-started/generating-s3-keys/)
- [S3 authentication and per-project access-key scope](https://docs.hetzner.com/storage/object-storage/faq/s3-credentials/)
- [Official curl examples: List/Create/Delete Bucket and object operations](https://docs.hetzner.com/storage/object-storage/getting-started/using-curl/)
- [Buckets, protection, Object Lock, visibility, immutability and API limits](https://docs.hetzner.com/storage/object-storage/faq/buckets-objects/)
- [Supported S3 actions and documented limitations](https://docs.hetzner.com/storage/object-storage/supported-actions/)
- [Hetzner Cloud API overview — Object Storage is standard S3, not a Cloud REST resource](https://docs.hetzner.cloud/reference/cloud)
