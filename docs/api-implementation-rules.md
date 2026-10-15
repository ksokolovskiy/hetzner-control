# API implementation and acceptance rules

These rules apply to every provider integration, not just server provisioning.

- Verify each endpoint, method, request field, response field, status code and
  precondition against current official documentation before implementing it.
  For Hetzner Cloud use https://docs.hetzner.cloud/ and its OpenAPI specification
  https://docs.hetzner.cloud/cloud.spec.json. Record intentional differences
  between design proposals and supported provider behavior.
- Cover wire contracts with synthetic HTTP fixtures as well as adapter tests.
  An application-owned fake alone cannot prove compatibility with the provider.
- Missing information is unknown, not zero, disabled protection, unassigned
  capacity or successful completion. Incomplete safety information blocks the
  affected write; it does not silently relax its preconditions.
- Bind every request and saved operation to the original project/profile and
  endpoint. Never resolve a saved operation through the currently active token.
- Keep one write path per operation through shared review, durable journal and
  result handling. Persist before sending; uncertain outcomes permit read-only
  reconciliation, never automatic replay. Track every returned original action.
- Log diagnostic request method, safe endpoint template, timing, HTTP status,
  provider error code and safe correlation identifiers for every API family.
  Do not log authorization headers, tokens, passwords, S3 credentials, raw
  request/response bodies, private keys or unfiltered exception messages.
- Keep secrets transient and out of saved UI state, operation records and
  screenshots/test artifacts. Encrypted credential storage is the only persistent
  credential boundary. Never use real credentials in test fixtures.
- Verify EN/RU/DE strings and use shared formatting/components. Preserve scoped
  navigation and scroll state, support pull-to-refresh and independent read
  failures without hiding previously loaded content.
- A successful compilation or unit suite is not visual/device acceptance.
  Record the exact verified snapshot and checks; keep issues open until their
  actual requirements, including device behavior, have been checked.
