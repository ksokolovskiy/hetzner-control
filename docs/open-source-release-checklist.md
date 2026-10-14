# Open-source release checklist

This is a preparation checklist only. It does not authorize a public push or a
GitHub release.

## Current audit snapshot (2026-10-13)

- The reachable Git history contains three commits.
- No `LICENSE`, `NOTICE`, `COPYING`, or third-party-notice file is currently
  tracked.
- A filename audit found no tracked environment, Android signing, PKCS, or
  service-account credential file.
- A pattern-based scan of the working tree, `HEAD`, and all reachable commits
  found no match for common private-key, GitHub token, AWS access-key, Google
  API-key, Slack token, Stripe key, JWT, or populated Bearer-token signatures.
  This is a useful guardrail, not proof that no secret exists.
- `gitleaks` is not installed in this environment. Run a maintained secret
  scanner in CI before making the repository public and protect the default
  branch.

## Decisions required from the repository owner

1. Choose an outbound license. Do not publish before adding the exact approved
   `LICENSE` text and copyright holder/year. This checklist deliberately does
   not choose a license for the owner.
2. Decide whether to accept external contributions now. If yes, add a concise
   `CONTRIBUTING.md`, issue/PR templates, and a code-of-conduct/contact path.
3. Decide whether contributors grant rights under the project license through
   the contribution policy or whether another agreement is required.
4. Confirm the public name, support email, security-reporting contact, and the
   intended GitHub visibility change.

## Third-party dependencies and notices

The version catalog currently has direct dependencies in these groups:

- AndroidX / Jetpack Compose / Android security crypto;
- Kotlin and kotlinx.coroutines;
- Square Retrofit, Moshi, and OkHttp;
- JUnit (test scope);
- Android Gradle Plugin and Kotlin Gradle plugins (build scope).

Some direct POM metadata was checked locally: Retrofit 2.11.0, OkHttp 4.12.0,
and kotlinx-coroutines-android 1.9.0 declare Apache-2.0; JUnit 4.13.2 declares
Eclipse Public License 1.0. Do not extrapolate this to the full transitive
graph. Before release, generate a resolved dependency inventory for the exact
release build, inspect every artifact's license and `NOTICE` requirements, and
publish an appropriate `THIRD_PARTY_NOTICES`/attribution file with the release.

The project's chosen outbound license and the dependencies' inbound licenses
must be reviewed together. This is a release/legal review step, not legal
advice.

## Pre-publication checklist

1. Run the full test/build suite from a clean checkout and verify the released
   source builds using the documented WSL toolchain.
2. Run `git status --short`, `git diff --check`,
   `sh tools/check-repository-hygiene.sh`, and an automated secret scan
   over the worktree and complete reachable history. Revoke and rotate any
   exposed credential before attempting history cleanup.
3. Inspect `.gitignore`; keep signing keys, keystore property files,
   service-account JSON, `.env` files, local SDK paths, generated build
   outputs, and real API tokens out of Git.
4. Review the code and generated artefacts for API tokens, root passwords,
   server IP addresses, test account data, screenshots, and logs.
5. Add or update: `README.md`, `LICENSE`, dependency notices, security and
   privacy documentation, changelog, contribution policy (if accepting
   contributions), and a clear support/security contact.
6. Create a signed release tag only after the above review; attach source,
   checksums, and any distributable APK/AAB only after verifying their
   provenance.
7. For a Play release, ensure the store privacy policy and Data safety answers
   match the final binary and [security/privacy notes](security-and-privacy.md).

## Suggested CI gates before opening the repository

- Debug build and unit tests on a clean runner.
- Secret scan of pull requests and full Git history.
- Dependency/license inventory export and review on version changes.
- `git diff --check` and a check that prohibited credential filename patterns
  are not tracked.
- A manual release checklist approval; automated checks cannot determine
  whether a token is valid or whether a privacy statement matches future data
  flows.
