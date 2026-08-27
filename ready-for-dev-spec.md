# Project: SMS-to-Ntfy Android App

## Goal
Build a fully functional Android app that forwards SMS and calls to ntfy, supporting remote replies, without Google dependencies.

## Scope
- Native Android Service for SMS/Call listening.
- SSE connection for remote reply.
- Professional web dashboard.
- ZIP packaging for build.

## Implementation Tasks
1. Initialize native android project in `sms-ntfy/android-native`.
2. Implement BroadcastReceiver (priority 999).
3. Implement Foreground Service.
4. Integrate SSE for remote reply.
5. Create web dashboard UI with Sahel font.

## Code Map
- `sms-ntfy/android-native/` : Native Kotlin project
- `sms-ntfy/web/` : Web dashboard
