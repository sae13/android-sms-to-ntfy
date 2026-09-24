#!/usr/bin/env bash
set -euo pipefail

DELTACHAT_REPOSITORY="https://github.com/deltachat/deltachat-android.git"
DELTACHAT_TAG="v2.59.1"
DELTACHAT_COMMIT="16077915337231143f9ae4c6fe7ed2fe920044b9"
DELTACHAT_CORE_COMMIT="e322fdf157d8573db6e57aeefb7d3cdb1b272b19"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${DELTACHAT_SOURCE_DIR:-$ROOT/.deltachat-src}"
DESTINATION="$ROOT/app/src/main/java"

command -v git >/dev/null
command -v python3 >/dev/null

if [[ ! -e "$SOURCE/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$DELTACHAT_REPOSITORY" "$SOURCE"
fi
git -C "$SOURCE" fetch --depth 1 origin "refs/tags/$DELTACHAT_TAG:refs/tags/$DELTACHAT_TAG"
test "$(git -C "$SOURCE" rev-parse "$DELTACHAT_TAG^{}")" = "$DELTACHAT_COMMIT"
git -C "$SOURCE" checkout --detach --force "$DELTACHAT_COMMIT"
git -C "$SOURCE" reset --hard "$DELTACHAT_COMMIT"
git -C "$SOURCE" clean -ffdx
git -C "$SOURCE" submodule sync --recursive
git -C "$SOURCE" submodule update --init --recursive --depth 1
test "$(git -C "$SOURCE" rev-parse HEAD)" = "$DELTACHAT_COMMIT"
test "$(git -C "$SOURCE" rev-parse HEAD:jni/deltachat-core-rust)" = "$DELTACHAT_CORE_COMMIT"
test "$(git -C "$SOURCE/jni/deltachat-core-rust" rev-parse HEAD)" = "$DELTACHAT_CORE_COMMIT"
test -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)"

bindings_source="$SOURCE/src/main/java"
for package in com/b44t/messenger chat/delta/rpc; do
  test -d "$bindings_source/$package"
  rm -rf "${DESTINATION:?}/$package"
  mkdir -p "$DESTINATION/$package"
  while IFS= read -r -d '' binding; do
    relative="${binding#"$bindings_source/"}"
    destination="$DESTINATION/$relative"
    mkdir -p "$(dirname "$destination")"
    python3 - "$binding" "$destination" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
normalized = "\n".join(line.rstrip() for line in source.splitlines()) + "\n"
Path(sys.argv[2]).write_text(normalized)
PY
  done < <(find "$bindings_source/$package" -type f -name '*.java' -print0 | sort -z)
done

# Delta Chat's DcChat wrapper has one dependency on the full Android client.
# Keep the generated API intact while replacing that utility call locally.
python3 - "$DESTINATION/com/b44t/messenger/DcChat.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
source = source.replace("import org.thoughtcrime.securesms.util.Util;\n\n", "")
source = source.replace(
    "      return Util.contains(members, DcContact.DC_CONTACT_ID_SELF);",
    "      for (int member : members) {\n"
    "        if (member == DcContact.DC_CONTACT_ID_SELF) return true;\n"
    "      }\n"
    "      return false;",
)
path.write_text(source)
PY

# Generated wrappers otherwise expose native handles only to finalizers. Add
# idempotent explicit cleanup for bounded smoke tests and deterministic callers.
python3 - "$DESTINATION/com/b44t/messenger" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
wrappers = {
    "DcEvent.java": ("eventCPtr", "unrefEventCPtr"),
    "DcEventEmitter.java": ("eventEmitterCPtr", "unrefEventEmitterCPtr"),
    "DcJsonrpcInstance.java": ("jsonrpcInstanceCPtr", "unrefJsonrpcInstanceCPtr"),
}
for filename, (pointer, native_unref) in wrappers.items():
    path = root / filename
    source = path.read_text()
    old = (
        "  @Override\n"
        "  protected void finalize() throws Throwable {\n"
        "    super.finalize();\n"
        f"    {native_unref}();\n"
        f"    {pointer} = 0;\n"
        "  }\n"
    )
    new = (
        "  @Override\n"
        "  protected void finalize() throws Throwable {\n"
        "    super.finalize();\n"
        "    unref();\n"
        "  }\n\n"
        "  public void unref() {\n"
        f"    if ({pointer} != 0) {{\n"
        f"      {native_unref}();\n"
        f"      {pointer} = 0;\n"
        "    }\n"
        "  }\n"
    )
    if source.count(old) != 1:
        raise SystemExit(f"Unexpected cleanup shape in {path}")
    path.write_text(source.replace(old, new))
PY

# The Java generator loses serde's type-specific enum rename rules. Restore the
# wire discriminators from the pinned core schema instead of applying one global
# case conversion. Keep this table explicit so a schema change fails visibly.
python3 - "$DESTINATION/chat/delta/rpc/types" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
pattern = re.compile(r'@Type\(value = ([^,]+), name="([A-Za-z0-9_]+)"\)')
camel_case_types = {"EphemeralTimer", "MessageListItem", "MessageLoadResult", "Qr"}
pascal_case_types = {
    "Account",
    "CallState",
    "ChatListItemFetchResult",
    "EventType",
    "MessageQuote",
    "MuteDuration",
}

def camel_case(name: str) -> str:
    return name[:1].lower() + name[1:]

for path in sorted(root.glob("*.java")):
    source = path.read_text()
    if not pattern.search(source):
        continue
    if path.stem in camel_case_types:
        convert = camel_case
    elif path.stem in pascal_case_types:
        convert = lambda name: name
    else:
        raise SystemExit(f"Unhandled polymorphic wire type: {path.name}")
    source = pattern.sub(
        lambda match: f'@Type(value = {match.group(1)}, name="{convert(match.group(2))}")',
        source,
    )
    path.write_text(source)
PY

# BaseRpcTransport is generated upstream but needs Android-side reliability
# hardening. Apply it deterministically after every binding refresh.
python3 - "$DESTINATION/chat/delta/rpc/BaseRpcTransport.java" "$ROOT/scripts/deltachat/BaseRpcTransport.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
template = Path(sys.argv[2])
if not path.is_file():
    raise SystemExit(f"Missing generated transport: {path}")
path.write_text(template.read_text())
PY

expected_manifest="$(mktemp)"
actual_manifest="$(mktemp)"
trap 'rm -f "$expected_manifest" "$actual_manifest"' EXIT
(
  cd "$bindings_source"
  find com/b44t/messenger chat/delta/rpc -type f -name '*.java' -print | sort
) > "$expected_manifest"
(
  cd "$DESTINATION"
  find com/b44t/messenger chat/delta/rpc -type f -name '*.java' -print | sort
) > "$actual_manifest"
cmp "$expected_manifest" "$actual_manifest"

echo "Synchronized Delta Chat $DELTACHAT_TAG bindings from $DELTACHAT_COMMIT (core $DELTACHAT_CORE_COMMIT)."
