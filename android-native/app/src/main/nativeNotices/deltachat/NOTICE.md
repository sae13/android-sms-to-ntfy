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

- `arm64-v8a/libnative-utils.so`: `0944b5459f7f690d527d5d86ad65d2f5ce8b5aa9ca3e76a725bf334109b9b093`
- `armeabi-v7a/libnative-utils.so`: `4421eca8dabb808e9b5dc0aa04eb99ba7b59404b66c931c71d4baf2800662493`

The corresponding source is available at the repository above at the pinned
release/revisions. The included license text is packaged as
`assets/deltachat/LICENSE.txt`.
