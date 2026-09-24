Aether core
===========

This application bundles architecture-specific builds of Aether from:
https://github.com/CluvexStudio/Aether

Pinned release tag: v2.1.0
Pinned source revision: 6398931aeaa551248530cc164aea6d5f2c5fe4a2
Build toolchain: Rust 1.98.0, cargo-ndk 3.5.4, Android NDK 26.3.11579264
License: GNU Affero General Public License v3.0 (AGPL-3.0)

Android packages the Aether executable as lib/<abi>/libaether.so so it is
installed with native libraries. The app executes it as a subprocess and does
not load it through JNI. The corresponding source for the bundled executable is
the repository above at the pinned release and revision. Reproducible build
instructions are in scripts/build-aether-android.sh.
