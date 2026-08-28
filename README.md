# Android SMS to ntfy

A zero-Google SMS/call gateway for Android with a professional web dashboard.

## Download Android app

[Download the latest installable native Android release](https://github.com/sae13/android-sms-to-ntfy/releases/latest/download/sms-ntfy-android-native-release.apk).

## Modules

- `android-native/` — production Android Studio native Kotlin implementation.
- `kmp/` — experimental Kotlin Multiplatform shared core and Android app scaffold; not production-parity.
- `web/` — Persian/English static dashboard with Sahel font, live simulator, SSE listener, logs, and settings.

## Core behavior

- Incoming SMS forwarding through a high-priority receiver.
- Incoming and missed-call alerting.
- Persistent foreground service.
- Raw HTTP POST to ntfy.
- SSE reply listener.
- Remote SMS reply through Android `SmsManager`.
- No Firebase, no Google Play Services, LAN/self-hosted ntfy compatible.

## Build

Native Android:

```bash
cd android-native
./gradlew assembleDebug
```

Web dashboard:

```bash
cd web
python3 -m http.server 8080
```
