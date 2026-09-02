# Delta Chat native core

This directory contains `libnative-utils.so` for `arm64-v8a` and `armeabi-v7a`, built from the official Delta Chat projects at release **2.59.0**:

- Core: https://github.com/deltachat/deltachat-core-rust/tree/v2.59.0
- Android JNI bindings: https://github.com/deltachat/deltachat-android/tree/v2.59.0

The native binaries and the Java JSON-RPC/JNI bindings are pinned to the same upstream release. CI can rebuild both native binaries from another stable tag with `scripts/build-deltachat-android.sh`, and compatibility with the checked-in Java bindings is gated by the Android build and tests before a release is published. Delta Chat core is licensed under MPL-2.0; see `DELTACHAT-LICENSE.txt` in this directory.

SHA-256:

- `arm64-v8a/libnative-utils.so`: `50ad1dc0e8e99d989871f53253d6bee6d13f4cce1d2ab32412d3033ef36893ae`
- `armeabi-v7a/libnative-utils.so`: `703cd67584c8987c0efc668194499270512e8f16aaac8ce16f7c1239be5d5f64`
