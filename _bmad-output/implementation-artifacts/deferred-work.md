# Deferred work

The legacy Flutter implementation has been removed after the native Android APK was published and verified, preserving the user-approved delivery order.

Remaining verification: run a physical incoming-call check against the current signed build and confirm the call event reaches the configured ntfy topic without a foreground-service failure.

## Deferred from: code review of spec-update-aether-deltachat-and-android-release (2026-09-24)

- Quote bindgen resource paths so an Android NDK location containing whitespace is passed to bindgen as one argument. This behavior predates the reviewed repair.
- Define or reject the supported relationship between a caller-provided LIBCLANG_PATH and the NDK Clang resource headers. The previous implementation already allowed this mismatch.
