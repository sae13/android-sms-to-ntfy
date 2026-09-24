#!/usr/bin/env bash
set -euo pipefail

AETHER_REPOSITORY="https://github.com/CluvexStudio/Aether.git"
AETHER_TAG="v2.1.0"
AETHER_COMMIT="6398931aeaa551248530cc164aea6d5f2c5fe4a2"
RUST_TOOLCHAIN="1.98.0"
CARGO_NDK_VERSION="3.5.4"
ANDROID_NDK_VERSION="26.3.11579264"
ANDROID_PLATFORM="24"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${AETHER_SOURCE_DIR:-$ROOT/.aether-src}"
OUT="${AETHER_OUTPUT_DIR:-$ROOT/app/src/main/jniLibs}"
AETHER_ABIS="${AETHER_ABIS:-arm64-v8a armeabi-v7a}"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  : "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT/ANDROID_HOME or ANDROID_NDK_HOME}"
  ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
fi
test -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"
shopt -s nullglob
ndk_clang_candidates=("$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/clang)
shopt -u nullglob
if [[ "${#ndk_clang_candidates[@]}" -eq 0 ]] || [[ ! -x "${ndk_clang_candidates[0]}" ]]; then
  echo "clang was not found in ANDROID_NDK_HOME" >&2
  exit 1
fi
command -v git >/dev/null
command -v cargo >/dev/null
command -v rustup >/dev/null
command -v cmake >/dev/null

# bindgen loads libclang on the host, but its generated BoringSSL bindings must
# use the matching Android target and Clang resource headers for each ABI. The
# The NDK's libclang cannot reliably parse its own Android stdint.h in GitHub
# CI, so the workflow supplies a compatible host libclang. Local hosts without
# an explicit libclang use the NDK fallback; caller selections remain intact.
if [[ -z "${LIBCLANG_PATH:-}" ]]; then
  shopt -s nullglob
  libclang_candidates=("$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/lib/libclang.so)
  shopt -u nullglob
  if [[ "${#libclang_candidates[@]}" -eq 0 ]]; then
    echo "libclang.so was not found in ANDROID_NDK_HOME" >&2
    exit 1
  fi
  LIBCLANG_PATH="$(dirname "${libclang_candidates[0]}")"
  export LIBCLANG_PATH
fi

shopt -s nullglob
clang_resource_include_candidates=("$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/lib/clang/*/include)
shopt -u nullglob
if [[ "${#clang_resource_include_candidates[@]}" -eq 0 ]]; then
  echo "Clang resource headers were not found in ANDROID_NDK_HOME" >&2
  exit 1
fi
CLANG_RESOURCE_INCLUDE="${clang_resource_include_candidates[0]}"

rustup toolchain install "$RUST_TOOLCHAIN" --profile minimal
installed_cargo_ndk="$(cargo +"$RUST_TOOLCHAIN" ndk --version 2>/dev/null || true)"
if [[ "$installed_cargo_ndk" != "cargo-ndk $CARGO_NDK_VERSION" ]]; then
  cargo +"$RUST_TOOLCHAIN" install cargo-ndk --version "$CARGO_NDK_VERSION" --locked --force
fi
declare -A ABI_TARGETS=(
  [arm64-v8a]=aarch64-linux-android
  [armeabi-v7a]=armv7-linux-androideabi
  [x86_64]=x86_64-linux-android
)
read -r -a requested_abis <<< "$AETHER_ABIS"
rust_targets=()
for abi in "${requested_abis[@]}"; do
  target="${ABI_TARGETS[$abi]:-}"
  if [[ -z "$target" ]]; then
    echo "Unsupported Aether ABI: $abi" >&2
    exit 1
  fi
  rust_targets+=("$target")
done
rustup target add --toolchain "$RUST_TOOLCHAIN" "${rust_targets[@]}"

if [[ ! -d "$SOURCE/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$AETHER_REPOSITORY" "$SOURCE"
fi
git -C "$SOURCE" fetch --depth 1 origin "refs/tags/$AETHER_TAG:refs/tags/$AETHER_TAG"
test "$(git -C "$SOURCE" rev-parse "$AETHER_TAG^{}")" = "$AETHER_COMMIT"
git -C "$SOURCE" checkout --detach --force "$AETHER_COMMIT"
git -C "$SOURCE" reset --hard "$AETHER_COMMIT"
git -C "$SOURCE" clean -ffdx
test "$(git -C "$SOURCE" rev-parse HEAD)" = "$AETHER_COMMIT"
test -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)"
test -f "$SOURCE/aether/Cargo.lock"
test "$(python3 - "$SOURCE/aether/Cargo.toml" <<'PY'
import sys, tomllib
with open(sys.argv[1], "rb") as manifest:
    print(tomllib.load(manifest)["package"]["version"])
PY
)" = "2.1.0"

mkdir -p "$OUT"
for abi in "${requested_abis[@]}"; do
  rm -f "$OUT/$abi/libaether.so"
done

for abi in "${requested_abis[@]}"; do
  rust_target="${ABI_TARGETS[$abi]}"
  target_bindgen_var="BINDGEN_EXTRA_CLANG_ARGS_${rust_target//-/_}"
  target_bindgen_args="${BINDGEN_EXTRA_CLANG_ARGS:-}"
  if [[ -n "${!target_bindgen_var:-}" ]]; then
    target_bindgen_args+=" ${!target_bindgen_var}"
  fi
  # Bionic exposes fixed-width integer types according to the selected API.
  printf -v "$target_bindgen_var" -- '%s --target=%s -D__ANDROID_API__=%s -I%s' \
    "$target_bindgen_args" "$rust_target" "${ANDROID_PLATFORM#android-}" "$CLANG_RESOURCE_INCLUDE"
  export "${target_bindgen_var?}"
  (
    cd "$SOURCE/aether"
    cargo +"$RUST_TOOLCHAIN" ndk \
      -t "$abi" \
      --platform "$ANDROID_PLATFORM" \
      build --locked --release --bin aether
  )
  unset "$target_bindgen_var"
  mkdir -p "$OUT/$abi"
  install -m 0755 "$SOURCE/aether/target/$rust_target/release/aether" "$OUT/$abi/libaether.so"
  test -s "$OUT/$abi/libaether.so"
  case "$(file -b "$OUT/$abi/libaether.so")" in
    *ELF*executable*) ;;
    *) echo "Unexpected Aether artifact for $abi" >&2; exit 1 ;;
  esac
done
