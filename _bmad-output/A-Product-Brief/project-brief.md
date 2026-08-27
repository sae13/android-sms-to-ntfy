# Product Brief

## Project Name
SMS-to-Ntfy Android App with Web Dashboard

## Vision
Create a fully functional Android application (Native Kotlin, Flutter, KMP) that forwards incoming SMS and calls to a self-hosted ntfy server, enabling remote SMS replies via SSE, and a professional web dashboard with live event simulator, Persian Sahel font, and real-time logs.

## Target Users
- Individuals and small businesses needing SMS/call forwarding to personal servers.
- Users who prefer self-hosted, offline-capable solutions without Google dependencies.
- Administrators wanting a dashboard to monitor and configure the service.

## Success Criteria
- Reliable SMS forwarding with highest priority BroadcastReceiver.
- Incoming call alerts sent to ntfy.
- Remote SMS reply via SSE working 24/7 with battery optimizations.
- Web dashboard built with Professional Polish theme, Sahel font, live simulator, SSE logs.
- APK and source code available for direct download as ZIP for all three stacks.
- Zero dependency on Google Play Services; works on local LAN.

## Constraints
- Must run without Google Play Services.
- Must support battery saver/Doze mode via foreground service and WakeLock.
- Must auto-start on BOOT_COMPLETED.
- Must provide guidance to disable battery optimizations per brand (Xiaomi, Samsung, Huawei).
- Web dashboard must use Persian Sahel font, support bilingual layout.
- Source code must be ready to build in Android Studio, Flutter, KMP environments.

## Platform Strategy
- Android: Native Kotlin (primary), Flutter, Kotlin Multiplatform.
- Web: Responsive dashboard using modern HTML/CSS/JS (or React/Vue) with SSE client.
- Communication: Direct HTTP/SSE to self-hosted ntfy server.

## Content Language
- Primary language: Persian (Farsi) for UI and documentation.
- Secondary: English for code comments and technical documentation.

## Tone of Voice
- Clear, technical yet accessible.
- Professional but friendly.
- Instructional where needed (setup guides).

## Visual Direction
- Color palette: Slate 900/950 (dark neutral).
- Cards: white and metallic with proper padding, no unnatural gradients.
- Typography: Sahel font for Persian, fallback for English.
- Icons: simple line icons consistent with theme.

## Platform Requirements
- Android min SDK 21.
- Internet permission, RECEIVE_SMS, READ_CONTACTS, FOREGROUND_SERVICE, POST_NOTIFICATIONS, etc.
- Web: modern browser supporting SSE, ES6+.
