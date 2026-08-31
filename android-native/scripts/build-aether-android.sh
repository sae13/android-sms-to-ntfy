#!/usr/bin/env bash
set -euo pipefail

AETHER_REPOSITORY="https://github.com/CluvexStudio/Aether.git"
AETHER_COMMIT="a916ff6fbbb4ebafe8314c53cf3718eb51dcae53"
RUST_TOOLCHAIN="1.88.0"
CARGO_NDK_VERSION="4.1.2"
ANDROID_PLATFORM="24"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${AETHER_SOURCE_DIR:-$ROOT/.aether-src}"
OUT="${AETHER_OUTPUT_DIR:-$ROOT/app/src/main/jniLibs}"
AETHER_ABIS="${AETHER_ABIS:-arm64-v8a armeabi-v7a}"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an Android NDK r26d installation}"
test -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"
command -v git >/dev/null
command -v cargo >/dev/null
command -v rustup >/dev/null
command -v cmake >/dev/null

if [[ -z "${LIBCLANG_PATH:-}" ]]; then
  shopt -s nullglob
  libclang_candidates=("$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/lib/libclang.so)
  shopt -u nullglob
  if [[ "${#libclang_candidates[@]}" -eq 0 ]]; then
    echo "libclang.so was not found in ANDROID_NDK_HOME" >&2
    exit 1
  fi
  export LIBCLANG_PATH="$(dirname "${libclang_candidates[0]}")"
fi

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
git -C "$SOURCE" fetch --depth 1 origin "$AETHER_COMMIT"
git -C "$SOURCE" checkout --detach --force "$AETHER_COMMIT"
git -C "$SOURCE" reset --hard "$AETHER_COMMIT"
git -C "$SOURCE" clean -ffdx
test "$(git -C "$SOURCE" rev-parse HEAD)" = "$AETHER_COMMIT"
test -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)"
test -f "$SOURCE/aether/Cargo.lock"

mkdir -p "$OUT"
for abi in "${requested_abis[@]}"; do
  rm -f "$OUT/$abi/libaether.so"
done

for abi in "${requested_abis[@]}"; do
  rust_target="${ABI_TARGETS[$abi]}"
  (
    cd "$SOURCE/aether"
    cargo +"$RUST_TOOLCHAIN" ndk \
      -t "$abi" \
      --platform "$ANDROID_PLATFORM" \
      build --locked --release --bin aether
  )
  mkdir -p "$OUT/$abi"
  install -m 0755 "$SOURCE/aether/target/$rust_target/release/aether" "$OUT/$abi/libaether.so"
  test -s "$OUT/$abi/libaether.so"
  case "$(file -b "$OUT/$abi/libaether.so")" in
    *ELF*executable*) ;;
    *) echo "Unexpected Aether artifact for $abi" >&2; exit 1 ;;
  esac
done
