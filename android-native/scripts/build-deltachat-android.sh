#!/usr/bin/env bash
set -euo pipefail

DELTACHAT_TAG="v2.59.1"
DELTACHAT_COMMIT="16077915337231143f9ae4c6fe7ed2fe920044b9"
DELTACHAT_CORE_COMMIT="e322fdf157d8573db6e57aeefb7d3cdb1b272b19"
DELTACHAT_RUST_TOOLCHAIN="1.91.1"
ANDROID_NDK_VERSION="26.3.11579264"
ANDROID_PLATFORM="21"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${DELTACHAT_SOURCE_DIR:-$ROOT/.deltachat-src}"
BUILD_ROOT="${DELTACHAT_BUILD_DIR:-$ROOT/.deltachat-build}"
CARGO_TARGET_DIR="${DELTACHAT_CARGO_TARGET_DIR:-$BUILD_ROOT/cargo-target}"
DESTINATION="$ROOT/app/src/main/jniLibs"
NOTICE_ASSET="$ROOT/app/src/main/nativeNotices/deltachat"
SUPPORTED_ABIS=(arm64-v8a armeabi-v7a)

if [[ -d "$HOME/.cargo/bin" ]]; then
  export PATH="$HOME/.cargo/bin:$PATH"
fi
for command in git rustup cargo python3 sha256sum install realpath; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" && -f "$ROOT/local.properties" ]]; then
  ANDROID_SDK_ROOT="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | tail -n 1)"
fi
if [[ -z "$ANDROID_SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT, ANDROID_HOME, or sdk.dir in local.properties must point to an Android SDK." >&2
  exit 1
fi
NDK_HOME="${ANDROID_NDK_HOME:-${NDK_HOME:-$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION}}"
NDK_PROPERTIES="$NDK_HOME/source.properties"
if [[ ! -f "$NDK_PROPERTIES" ]] || ! grep -Fxq "Pkg.Revision = $ANDROID_NDK_VERSION" "$NDK_PROPERTIES"; then
  echo "Android NDK $ANDROID_NDK_VERSION is required at $NDK_HOME." >&2
  exit 1
fi

"$ROOT/scripts/sync-deltachat-android.sh"
test "$(git -C "$SOURCE" rev-parse HEAD)" = "$DELTACHAT_COMMIT"
test "$(git -C "$SOURCE" rev-parse HEAD:jni/deltachat-core-rust)" = "$DELTACHAT_CORE_COMMIT"
test "$(git -C "$SOURCE/jni/deltachat-core-rust" rev-parse HEAD)" = "$DELTACHAT_CORE_COMMIT"
test "$(tr -d '[:space:]' < "$SOURCE/scripts/rust-toolchain")" = "$DELTACHAT_RUST_TOOLCHAIN"
test -z "$(git -C "$SOURCE" status --porcelain --untracked-files=no)"

rustup toolchain install "$DELTACHAT_RUST_TOOLCHAIN" --profile minimal
rustup target add --toolchain "$DELTACHAT_RUST_TOOLCHAIN" \
  aarch64-linux-android armv7-linux-androideabi

# Follow upstream's reproducible-build settings while pinning the NDK path too.
PINNED_NDK_LINK="${TMPDIR:-/tmp}/android-ndk-root"
if [[ -e "$PINNED_NDK_LINK" && ! -L "$PINNED_NDK_LINK" ]]; then
  echo "Refusing to replace non-symlink $PINNED_NDK_LINK." >&2
  exit 1
fi
ndk_link_target="$(realpath "$NDK_HOME")"
if [[ -L "$PINNED_NDK_LINK" ]]; then
  if [[ "$(realpath "$PINNED_NDK_LINK")" != "$ndk_link_target" ]]; then
    echo "$PINNED_NDK_LINK already points at a different NDK." >&2
    exit 1
  fi
else
  ln -s "$ndk_link_target" "$PINNED_NDK_LINK"
fi
NDK_HOME="$PINNED_NDK_LINK"
LLVM_BIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for tool in llvm-ar llvm-ranlib llvm-strip llvm-nm llvm-readelf; do
  test -x "$LLVM_BIN/$tool"
done

mkdir -p "$BUILD_ROOT" "$CARGO_TARGET_DIR"
BINUTILS_SHIMS="$BUILD_ROOT/android-binutils"
rm -rf "$BINUTILS_SHIMS"
mkdir -p "$BINUTILS_SHIMS"
for triple in aarch64-linux-android arm-linux-androideabi; do
  ln -s "$LLVM_BIN/llvm-ar" "$BINUTILS_SHIMS/$triple-ar"
  ln -s "$LLVM_BIN/llvm-ranlib" "$BINUTILS_SHIMS/$triple-ranlib"
  ln -s "$LLVM_BIN/llvm-strip" "$BINUTILS_SHIMS/$triple-strip"
done
export PATH="$BINUTILS_SHIMS:$PATH"
export CARGO_TARGET_DIR
export CARGO_PROFILE_RELEASE_LTO=on
export CFLAGS="-fno-unwind-tables -fno-exceptions -fno-asynchronous-unwind-tables -fomit-frame-pointer -fvisibility=hidden"
export RUSTFLAGS="-C link-args=-Wl,--build-id=none --remap-path-prefix=$HOME/.cargo= --remap-path-prefix=$SOURCE= --remap-path-prefix=$CARGO_TARGET_DIR="
export SOURCE_DATE_EPOCH=1
export RUSTUP_TOOLCHAIN="$DELTACHAT_RUST_TOOLCHAIN"
unset CPATH

declare -A ABI_TARGETS=(
  [arm64-v8a]=aarch64-linux-android
  [armeabi-v7a]=armv7-linux-androideabi
)
declare -A ABI_CLANG_PREFIXES=(
  [arm64-v8a]=aarch64-linux-android
  [armeabi-v7a]=armv7a-linux-androideabi
)
declare -A ABI_ELF_CLASSES=(
  [arm64-v8a]=ELF64
  [armeabi-v7a]=ELF32
)
declare -A ABI_ELF_MACHINES=(
  [arm64-v8a]=AArch64
  [armeabi-v7a]=ARM
)

for abi in "${SUPPORTED_ABIS[@]}"; do
  target="${ABI_TARGETS[$abi]}"
  target_env="${target//-/_}"
  target_env_upper="${target_env^^}"
  linker="$LLVM_BIN/${ABI_CLANG_PREFIXES[$abi]}${ANDROID_PLATFORM}-clang"
  test -x "$linker"
  export "CARGO_TARGET_${target_env_upper}_LINKER=$linker"
  export "CC_${target_env}=$linker"
  export "AR_${target_env}=$LLVM_BIN/llvm-ar"

  TARGET_CC="$linker" \
  TARGET_AR="$LLVM_BIN/llvm-ar" \
  TARGET_RANLIB="$LLVM_BIN/llvm-ranlib" \
    cargo build \
      --manifest-path "$SOURCE/jni/deltachat-core-rust/Cargo.toml" \
      --package deltachat_ffi \
      --profile release \
      --target "$target" \
      --locked
done

# Install only after both Cargo builds finish. This keeps both static archives
# present together for the single two-ABI NDK invocation.
for abi in "${SUPPORTED_ABIS[@]}"; do
  target="${ABI_TARGETS[$abi]}"
  static_library="$CARGO_TARGET_DIR/$target/release/libdeltachat.a"
  test -s "$static_library"
  mkdir -p "$SOURCE/jni/$abi"
  install -m 0644 "$static_library" "$SOURCE/jni/$abi/libdeltachat.a"
done

NDK_BUILD_ROOT="$BUILD_ROOT/ndk"
rm -rf "$NDK_BUILD_ROOT"
mkdir -p "$NDK_BUILD_ROOT/obj" "$NDK_BUILD_ROOT/libs"
"$NDK_HOME/ndk-build" \
  -C "$SOURCE" \
  -B \
  "APP_ABI=${SUPPORTED_ABIS[*]}" \
  "NDK_PROJECT_PATH=$SOURCE" \
  "NDK_OUT=$NDK_BUILD_ROOT/obj" \
  "NDK_LIBS_OUT=$NDK_BUILD_ROOT/libs"

for abi in "${SUPPORTED_ABIS[@]}"; do
  built_library="$NDK_BUILD_ROOT/libs/$abi/libnative-utils.so"
  test -s "$built_library"
  elf_header="$("$LLVM_BIN/llvm-readelf" -h "$built_library")"
  grep -Eq "^[[:space:]]*Class:[[:space:]]+${ABI_ELF_CLASSES[$abi]}$" <<< "$elf_header"
  grep -Eq "^[[:space:]]*Type:[[:space:]]+DYN \(Shared object file\)$" <<< "$elf_header"
  grep -Eq "^[[:space:]]*Machine:[[:space:]]+${ABI_ELF_MACHINES[$abi]}$" <<< "$elf_header"
  mkdir -p "$DESTINATION/$abi"
  install -m 0644 "$built_library" "$DESTINATION/$abi/libnative-utils.so"
  # The JNI wrapper statically embeds libdeltachat.a and is the only library loaded by Java.
  rm -f "$DESTINATION/$abi/libdeltachat.so"
done

mkdir -p "$NOTICE_ASSET"
install -m 0644 "$SOURCE/LICENSE" "$DESTINATION/DELTACHAT-LICENSE.txt"
install -m 0644 "$SOURCE/LICENSE" "$NOTICE_ASSET/LICENSE.txt"
arm64_hash="$(sha256sum "$DESTINATION/arm64-v8a/libnative-utils.so" | cut -d ' ' -f 1)"
armv7_hash="$(sha256sum "$DESTINATION/armeabi-v7a/libnative-utils.so" | cut -d ' ' -f 1)"
python3 - "$DESTINATION/DELTACHAT-NOTICE.md" "$arm64_hash" "$armv7_hash" <<'PY'
from pathlib import Path
import sys

notice = f"""Delta Chat Android JNI native library
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

- `arm64-v8a/libnative-utils.so`: `{sys.argv[2]}`
- `armeabi-v7a/libnative-utils.so`: `{sys.argv[3]}`

The corresponding source is available at the repository above at the pinned
release/revisions. The included license text is packaged as
`assets/deltachat/LICENSE.txt`.
"""
Path(sys.argv[1]).write_text(notice)
PY
install -m 0644 "$DESTINATION/DELTACHAT-NOTICE.md" "$NOTICE_ASSET/NOTICE.md"

"$ROOT/scripts/verify-deltachat-android.sh"
echo "Built Delta Chat $DELTACHAT_TAG JNI wrapper ($DELTACHAT_COMMIT; embedded core $DELTACHAT_CORE_COMMIT) for ${SUPPORTED_ABIS[*]}."
