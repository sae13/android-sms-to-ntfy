Delta Chat Android JNI native library
=====================================

This application bundles architecture-specific builds from:
https://github.com/deltachat/deltachat-android

Pinned Android release tag: v2.59.1
Pinned Android source revision: 16077915337231143f9ae4c6fe7ed2fe920044b9
Pinned core submodule revision: e322fdf157d8573db6e57aeefb7d3cdb1b272b19
Build toolchain: Rust 1.91.1, Android NDK 26.3.11579264
License: Mozilla Public License 2.0 (MPL-2.0)

The checked-in `com.b44t.messenger` and `chat.delta.rpc` bindings and the JNI
wrapper come from the same pinned Android revision. For each supported ABI,
`scripts/build-deltachat-android.sh` builds the pinned core as `libdeltachat.a`
and links it statically into `libnative-utils.so` with upstream's `Android.mk`.
Java loads only `libnative-utils.so`; no standalone `libdeltachat.so` is packaged.

SHA-256 (reproducible artifacts):

- `arm64-v8a/libnative-utils.so`: `eba8ea11446226f0c45574e0f3f14843ff94c6490f650da8e05f24c4d439d007`
- `armeabi-v7a/libnative-utils.so`: `bfccaea901ef438ba5bcfb3e7c3706813b50bc77f58bd471d2ab1715f24e44ac`

The corresponding source is available at the repository above at the pinned
release/revisions. The included license text is packaged as
`assets/deltachat/LICENSE.txt`.
