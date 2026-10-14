# Internal UI regression QA

This checklist is for maintainers before a device build is accepted. It covers
navigation and appearance regressions that are not safely represented by a
network integration test.

## Server mutation keeps the detail screen

Use a dedicated test-mock profile with its own non-production credential so
this test neither contacts Hetzner Cloud nor changes a real server. The app
does not include a built-in local sandbox.

1. Select the test-mock profile, open **Servers**, then select a server card.
2. Confirm that the detail screen shows **Server overview** and the selected
   server name.
3. Perform one safe mutation appropriate to the displayed state (for example,
   power off a running test server), confirm the dialog, and wait for the
   success state.
4. Verify that the app remains on the same server’s detail screen: it must not
   return to the server list, a different bottom-navigation tab, onboarding, or
   Settings.
5. Verify that the detail fields refresh to the new state. Navigate back only
   with the explicit back affordance, then confirm the list reflects that same
   state.
6. Repeat once with a non-power mutation available in the detail screen, such
   as enabling/disabling backups. On an error, the detail screen must remain
   visible and the operation must be retryable or dismissible.

Automated coverage target: drive a fake repository action to completion and
assert that the selected server identifier remains set while a refreshed server
model replaces its content. A future Compose UI test should use stable tags for
the detail root and selected server id; do not infer this from display text.

## Appearance selection: System, Light, Dark

Expected product contract once the appearance control is implemented:

1. In **Settings**, select **System**, **Light**, then **Dark**.
2. After each selection, verify the active choice and immediately verify the
   corresponding Material color scheme.
3. Background the app or recreate the activity; the selected choice must remain
   active.
4. Fully relaunch the app; it must restore the same explicit Light/Dark choice.
   For System, it must follow the device’s current system appearance after
   restart.
5. Switch Cloud projects and verify the appearance setting remains an app-level
   preference, not a project-specific setting. It must never be stored with an
   API token.

Current status: implemented. Appearance is an app-level, non-secret
preference and must remain independent of all Cloud, Storage Box, and
test-mock credentials. Treat a failure to restore it as a release-blocking
regression.

Automated coverage target after implementation: a pure preference-store test
for each enum value and restoration, plus a Compose/UI test that recreates the
activity and asserts the selected mode rather than pixel colors.
