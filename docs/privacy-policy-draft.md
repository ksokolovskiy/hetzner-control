# HCloud Control privacy policy — draft for publication

**Effective date:** replace before publication.

HCloud Control is an independent Android application for managing Hetzner
Cloud resources. It is not affiliated with Hetzner.

## Data the app handles

The app asks you to enter a separate Hetzner Cloud API token for each Cloud
project you choose to connect. If you use Object Storage, it also asks for the
S3 access key and secret key for that service. It processes resource metadata
returned by the connected services, such as server names, IP addresses,
locations, storage names, network configuration, DNS data, and SSH-key
metadata. A one-time root password may be displayed after server creation when
Hetzner returns one.

## How data is used and stored

Tokens and Object Storage credentials are used only to authenticate requests to
the selected Hetzner API. They are stored encrypted on the device using
Android Keystore-backed encryption. Resource metadata is held in memory to
show the current screen. The app does not operate an application backend, sync
credentials, sell data, use advertising, or include analytics or
crash-reporting SDKs.

The app does not intentionally write API tokens or one-time root passwords to
its diagnostics. A root password is shown only in the immediate successful
creation flow and is not stored by the app.

## Sharing

The app sends requests directly from your device to Hetzner endpoints needed
for the features you use. It does not otherwise share your information with
third parties.

## Your choices

You can remove a configured Cloud project in the app. Removing it removes the
corresponding locally stored token and connection metadata from the app. You
can also revoke a token through Hetzner Cloud. Android system backups are
disabled for this app.

## Security

No method of storage or transmission is completely secure. Keep your device
secure, use separate limited-scope tokens where available, and revoke a token
if you believe a device has been compromised.

## Changes

This policy may be updated when the app changes. The effective date will be
updated with the published version.

## Published policy

The reviewed public policy is maintained as
[`docs/privacy-policy/index.html`](privacy-policy/index.html) and is intended
to be published through GitHub Pages. The public policy directs privacy
questions to the repository's issue tracker and explicitly prohibits posting
credentials there. A monitored support email can be added to the policy later
without changing its data-handling commitments.
