---
title: 'Delta Chat notification destination'
type: 'feature'
created: '2026-08-30'
status: 'in-progress'
baseline_commit: '6424760516e4543fabfed5ab3a2426f944b325fe'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app can only forward SMS and call notifications through ntfy. Users need an embedded Delta Chat destination configured with a Delta Chat login code and a contact or group invitation link.

**Approach:** Embed the official Delta Chat core and its Java RPC bindings, retain ntfy as the default destination, and allow Delta Chat to be enabled independently. Configure the account from the login QR payload, join the invitation, persist only non-secret destination state outside the core database, and send readable notifications to the resulting chat.

## Boundaries & Constraints

**Always:** Use the official Delta Chat core and protocol implementation; keep TLS and end-to-end encryption behavior owned by the core; store the account in the app-private Delta Chat database; never log or display the login code after submission; support contact and group securejoin links; keep current ntfy behavior unchanged when Delta Chat is disabled; report setup and send failures visibly without leaking credentials.

**Never:** Require a separately installed Delta Chat app; implement IMAP, SMTP, OpenPGP, Autocrypt, or SecureJoin manually; send the login code to ntfy or any unrelated server; commit user credentials; claim delivery when the core rejects or cannot queue a message; implement replies from Delta Chat in this slice.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Setup succeeds | Valid `dclogin:` payload and valid Delta Chat contact/group invite | Account is configured, invite returns a chat ID, I/O starts, destination becomes ready | Login payload is cleared from the UI and not retained in preferences |
| Invalid login | Missing or non-`dclogin:` payload | Setup does not invoke core and no account is marked ready | Show a validation error without echoing input |
| Invalid invite | Missing or unsupported invite | Setup does not mark destination ready | Show a validation/core error without echoing secrets |
| SMS send | Delta Chat enabled and ready | Readable sender, contact, timestamp, reply ID and body are queued to configured chat | Return failure and write a redacted event-log entry |
| Call send | Delta Chat enabled and ready | Readable caller, state and timestamp are queued to configured chat | Return failure and write a redacted event-log entry |
| Core unavailable | ABI unsupported or native initialization fails | ntfy path remains operational | Delta Chat is reported unavailable; app does not crash |
| Existing install | ntfy configured before upgrade | ntfy remains enabled and settings remain intact | No migration required from user |

</frozen-after-approval>

## Code Map

- `android-native/app/build.gradle.kts` -- package official native library and Java binding dependencies.
- `android-native/app/src/main/java/com/smsntfy/deltachat/` -- core adapter, validation, payload formatting, and RPC/native boundary.
- `android-native/app/src/main/java/com/smsntfy/data/Preferences.kt` -- Delta Chat enablement and non-secret destination state.
- `android-native/app/src/main/java/com/smsntfy/SmsNtfyApplication.kt` -- lazy Delta Chat client lifecycle.
- `android-native/app/src/main/java/com/smsntfy/service/SmsForwardingService.kt` -- fan out SMS and call events to enabled destinations.
- `android-native/app/src/main/java/com/smsntfy/ui/SettingsActivity.kt` -- setup inputs and action.
- `android-native/app/src/main/java/com/smsntfy/ui/SettingsViewModel.kt` -- asynchronous setup and test operations.
- `android-native/app/src/main/res/layout/activity_settings.xml` -- Delta Chat settings controls.
- `android-native/app/src/test/java/com/smsntfy/deltachat/` -- validation, formatter, destination policy, and adapter contract tests.

## Tasks & Acceptance

**Execution:**
- [x] Add failing tests for login/invite validation and readable Delta Chat payloads, then implement the pure policy layer.
- [x] Package the pinned official Delta Chat native core and minimal Java RPC bindings with license/provenance metadata.
- [x] Add failing adapter contract tests, then implement account setup, securejoin, I/O startup, and text sending.
- [x] Add destination preferences and settings UI that never persists the login payload in shared preferences.
- [x] Fan out SMS and calls to ntfy and/or Delta Chat according to independent enablement.
- [ ] Build, run unit tests, inspect APK native entries, install on the physical test phone, configure from the private lab inputs, and verify an actual Delta Chat message.

**Acceptance Criteria:**
- Given an upgraded existing install, when the app starts, then ntfy forwarding behaves exactly as before and Delta Chat is disabled.
- Given valid setup inputs, when setup completes, then the configured chat ID survives process restart while the login payload is absent from shared preferences and logs.
- Given Delta Chat is enabled and ready, when an SMS or call arrives, then the official core queues one readable message to the configured chat.
- Given Delta Chat setup or sending fails, when ntfy is also enabled, then ntfy forwarding still runs and the service remains alive.
- Given a supported physical device, when the built APK is installed and configured, then a real test message arrives in the invited Delta Chat conversation.

## Spec Change Log

## Review Triage Log

## Design Notes

The app uses the official in-process JSON-RPC interface exposed by Delta Chat core. The expected setup sequence is `add_account`, `add_transport_from_qr`, `secure_join`, `start_io`; sending uses `misc_send_text_message`. Native binaries and generated bindings are pinned to the same upstream release. The login code is handed directly to the core and must not be copied to SharedPreferences.

## Verification

**Commands:**
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest` -- expected: all unit tests pass.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug` -- expected: APK builds.
- `unzip -l app/build/outputs/apk/debug/app-debug.apk` -- expected: supported `libnative-utils.so` entries and Delta Chat classes are packaged.
- Physical Android test using values loaded from the private test-lab environment -- expected: setup succeeds and a unique test message arrives in the target conversation.
