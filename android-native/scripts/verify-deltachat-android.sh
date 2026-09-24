#!/usr/bin/env bash
set -euo pipefail

DELTACHAT_TAG="v2.59.1"
DELTACHAT_COMMIT="16077915337231143f9ae4c6fe7ed2fe920044b9"
DELTACHAT_CORE_COMMIT="e322fdf157d8573db6e57aeefb7d3cdb1b272b19"
DELTACHAT_RUST_TOOLCHAIN="1.91.1"
ANDROID_NDK_VERSION="26.3.11579264"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${DELTACHAT_SOURCE_DIR:-$ROOT/.deltachat-src}"
DESTINATION="$ROOT/app/src/main/jniLibs"
NOTICE="$ROOT/app/src/main/nativeNotices/deltachat/NOTICE.md"
DELTACHAT_ABIS="${DELTACHAT_ABIS:-arm64-v8a armeabi-v7a}"

for command in git sha256sum grep cmp; do
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
LLVM_BIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ ! -f "$NDK_PROPERTIES" ]] || ! grep -Fxq "Pkg.Revision = $ANDROID_NDK_VERSION" "$NDK_PROPERTIES"; then
  echo "Android NDK $ANDROID_NDK_VERSION is required at $NDK_HOME." >&2
  exit 1
fi
for tool in llvm-nm llvm-readelf; do
  if [[ ! -x "$LLVM_BIN/$tool" ]]; then
    echo "Missing required NDK tool: $LLVM_BIN/$tool" >&2
    exit 1
  fi
done

if [[ ! -e "$SOURCE/.git" ]] || [[ ! -e "$SOURCE/jni/deltachat-core-rust/.git" ]]; then
  echo "Pinned Delta Chat checkout is missing at $SOURCE; run build-deltachat-android.sh first." >&2
  exit 1
fi
test "$(git -C "$SOURCE" rev-parse HEAD)" = "$DELTACHAT_COMMIT"
test "$(git -C "$SOURCE" rev-parse HEAD:jni/deltachat-core-rust)" = "$DELTACHAT_CORE_COMMIT"
test "$(git -C "$SOURCE/jni/deltachat-core-rust" rev-parse HEAD)" = "$DELTACHAT_CORE_COMMIT"
test "$(tr -d '[:space:]' < "$SOURCE/scripts/rust-toolchain")" = "$DELTACHAT_RUST_TOOLCHAIN"

declare -A ABI_ELF_CLASSES=(
  [arm64-v8a]=ELF64
  [armeabi-v7a]=ELF32
)
declare -A ABI_ELF_MACHINES=(
  [arm64-v8a]=AArch64
  [armeabi-v7a]=ARM
)
read -r -a requested_abis <<< "$DELTACHAT_ABIS"
if [[ " ${requested_abis[*]} " != " arm64-v8a armeabi-v7a " ]]; then
  echo "Delta Chat verification requires exactly: arm64-v8a armeabi-v7a" >&2
  exit 1
fi

for abi in "${requested_abis[@]}"; do
  library="$DESTINATION/$abi/libnative-utils.so"
  elf_class="${ABI_ELF_CLASSES[$abi]:-}"
  elf_machine="${ABI_ELF_MACHINES[$abi]:-}"
  if [[ -z "$elf_class" || -z "$elf_machine" ]]; then
    echo "Unsupported Delta Chat ABI: $abi" >&2
    exit 1
  fi
  test -s "$library"
  elf_header="$("$LLVM_BIN/llvm-readelf" -h "$library")"
  grep -Eq "^[[:space:]]*Class:[[:space:]]+$elf_class$" <<< "$elf_header"
  grep -Eq "^[[:space:]]*Type:[[:space:]]+DYN \(Shared object file\)$" <<< "$elf_header"
  grep -Eq "^[[:space:]]*Machine:[[:space:]]+$elf_machine$" <<< "$elf_header"
  elf_notes="$("$LLVM_BIN/llvm-readelf" -n "$library")"
  grep -Fq "NT_ANDROID_TYPE_IDENT" <<< "$elf_notes"
  grep -Fq "72 32 36 64" <<< "$elf_notes"
  grep -Fq "31 31 35 37 39 32 36 34" <<< "$elf_notes"
  elf_dynamic="$("$LLVM_BIN/llvm-readelf" -d "$library")"
  grep -Fq 'Library soname: [libnative-utils.so]' <<< "$elf_dynamic"
  if grep -Fq 'Shared library: [libdeltachat.so]' <<< "$elf_dynamic"; then
    echo "$library dynamically links libdeltachat.so; the pinned core must be embedded statically." >&2
    exit 1
  fi
  jni_exports="$("$LLVM_BIN/llvm-nm" -D --defined-only "$library" | grep -c ' Java_' || true)"
  core_exports="$("$LLVM_BIN/llvm-nm" -D --defined-only "$library" | grep -c ' dc_' || true)"
  if (( jni_exports < 200 )); then
    echo "$library exposes only $jni_exports JNI symbols." >&2
    exit 1
  fi
  if (( core_exports < 250 )); then
    echo "$library exposes only $core_exports embedded Delta Chat core symbols." >&2
    exit 1
  fi
  if [[ -e "$DESTINATION/$abi/libdeltachat.so" ]]; then
    echo "Unexpected standalone $DESTINATION/$abi/libdeltachat.so; libnative-utils.so embeds the pinned core." >&2
    exit 1
  fi
done

for value in "$DELTACHAT_TAG" "$DELTACHAT_COMMIT" "$DELTACHAT_CORE_COMMIT" "Rust $DELTACHAT_RUST_TOOLCHAIN" "Android NDK $ANDROID_NDK_VERSION"; do
  grep -Fq "$value" "$NOTICE"
done
for abi in "${requested_abis[@]}"; do
  hash="$(sha256sum "$DESTINATION/$abi/libnative-utils.so" | cut -d ' ' -f 1)"
  grep -Fq "\`$abi/libnative-utils.so\`: \`$hash\`" "$NOTICE"
done

cmp "$SOURCE/LICENSE" "$ROOT/app/src/main/nativeNotices/deltachat/LICENSE.txt"

echo "Verified Delta Chat $DELTACHAT_TAG ($DELTACHAT_COMMIT; core $DELTACHAT_CORE_COMMIT) native provenance for ${requested_abis[*]}."
for abi in "${requested_abis[@]}"; do
  sha256sum "$DESTINATION/$abi/libnative-utils.so"
done
