#!/usr/bin/env bash
set -euo pipefail

NDK_VERSION="27.0.12077973"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${DELTACHAT_SOURCE_DIR:-$ROOT/.deltachat-src}"
OUT="${DELTACHAT_OUTPUT_DIR:-$ROOT/app/src/main/jniLibs}"
DELTACHAT_ABIS="${DELTACHAT_ABIS:-arm64-v8a armeabi-v7a x86_64}"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK $NDK_VERSION}"
test -x "$ANDROID_NDK_HOME/ndk-build"
if [[ -f "$ANDROID_NDK_HOME/source.properties" ]]; then
  grep -Fq "Pkg.Revision = $NDK_VERSION" "$ANDROID_NDK_HOME/source.properties"
fi
test -d "$SOURCE/.git"
test -f "$SOURCE/jni/deltachat-core-rust/Cargo.lock"
test -f "$SOURCE/scripts/ndk-make.sh"

declare -A ABI_TARGETS=(
  [arm64-v8a]=aarch64-linux-android
  [armeabi-v7a]=armv7-linux-androideabi
  [x86]=i686-linux-android
  [x86_64]=x86_64-linux-android
)
read -r -a requested_abis <<< "$DELTACHAT_ABIS"
for abi in "${requested_abis[@]}"; do
  if [[ -z "${ABI_TARGETS[$abi]:-}" ]]; then
    echo "Unsupported Delta Chat ABI: $abi" >&2
    exit 1
  fi
done

command -v rustup >/dev/null

rust_toolchain="$(tr -d '[:space:]' < "$SOURCE/scripts/rust-toolchain")"
test -n "$rust_toolchain"
rustup toolchain install "$rust_toolchain" --profile minimal
rust_targets=()
for abi in "${requested_abis[@]}"; do
  rust_targets+=("${ABI_TARGETS[$abi]}")
done
rustup target add --toolchain "$rust_toolchain" "${rust_targets[@]}"

for abi in "${requested_abis[@]}"; do
  rm -f "$OUT/$abi/libnative-utils.so"
  (
    cd "$SOURCE"
    ANDROID_NDK_ROOT="$ANDROID_NDK_HOME" scripts/ndk-make.sh "$abi"
  )
  built="$SOURCE/libs/$abi/libnative-utils.so"
  test -s "$built"
  mkdir -p "$OUT/$abi"
  install -m 0755 "$built" "$OUT/$abi/libnative-utils.so"
  test -s "$OUT/$abi/libnative-utils.so"
  case "$(file -b "$OUT/$abi/libnative-utils.so")" in
    *ELF*shared\ object*) ;;
    *) echo "Unexpected Delta Chat artifact for $abi" >&2; exit 1 ;;
  esac
done
