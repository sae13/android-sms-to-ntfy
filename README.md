# Android SMS to ntfy

A zero-Google SMS/call gateway for Android with a professional web dashboard.

## Download Android app

[Download the latest installable native Android release](https://github.com/sae13/android-sms-to-ntfy/releases/latest/download/sms-ntfy-android-native-release.apk).

## Modules

- `native-kotlin/` — Android Studio native Kotlin implementation.
- `flutter-app/` — Flutter implementation scaffold with the same settings model.
- `kmp-app/` — Kotlin Multiplatform shared core and Android app scaffold.
- `web-dashboard/` — Persian/English dashboard with Sahel font, live simulator, SSE listener, logs, settings, source-code download links.
- `dist/` — generated ZIP packages.

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
cd native-kotlin
./gradlew assembleDebug
```

Web dashboard:

```bash
cd web-dashboard
npm install
npm run build
```

Package ZIP files:

```bash
python3 scripts/package.py
```
