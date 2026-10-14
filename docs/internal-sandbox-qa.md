# Internal sandbox QA checklist

This document is for maintainers only. External test-mock services and fixtures
are test infrastructure; they are not an application feature and must not be
presented as a configurable endpoint to users. The app may offer its explicit
test-mock connection kind, but its route is fixed in source and requires a
separate test credential; it must never be a generic endpoint switch.

Before accepting a sandbox-backed test run:

- Confirm the sandbox is isolated from production accounts and data.
- Use test-only credentials held outside the repository and never record them
  in terminal output, screenshots, issue comments, or CI logs.
- Verify that unauthenticated requests are rejected and that a valid test
  request returns the expected status without logging its response body.
- Exercise the app only through its supported configuration flow; do not add
  hidden endpoint switches or embed test credentials in the APK.
- Check that error and diagnostic output excludes authorization headers, API
  tokens, request bodies, root passwords, and SSH keys.
- Remove temporary local credentials and rotate sandbox credentials after a
  suspected disclosure.

This checklist deliberately contains no hostnames, endpoints, account details,
or credential-retrieval procedures. Those operational details belong in the
restricted infrastructure runbook, not this repository.

## Unit-test plan

Keep sandbox validation repeatable by testing the following contracts locally:

- API error mapping returns a user-safe message and never includes request
  headers, authorization values, request bodies, root passwords, or SSH keys.
- Token stores persist only encrypted credential data and expose no token value
  through UI state or diagnostic output.
- Server DTO parsing accepts optional or absent API fields where the public API
  permits them, producing a controlled domain failure rather than crashing.
- Provisioning request construction keeps a one-time root password and any SSH
  key material out of persisted models and loggable failure objects.
- Pure presentation helpers, such as price formatting, are covered with normal
  and malformed input without making pricing or network assertions.

Run the relevant JVM tests and the source-level sensitive-data guard before a
sandbox manual check. Do not make external service reachability a required unit
test: that belongs to restricted operational validation.
