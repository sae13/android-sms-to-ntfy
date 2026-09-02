#!/usr/bin/env bash
set -euo pipefail

DELTACHAT_REPOSITORY="https://github.com/deltachat/deltachat-android.git"
DELTACHAT_TAG="${DELTACHAT_TAG:-v2.59.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${DELTACHAT_SOURCE_DIR:-$ROOT/.deltachat-src}"
DESTINATION="$ROOT/app/src/main/java"

command -v git >/dev/null
command -v python3 >/dev/null
[[ "$DELTACHAT_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]

if [[ ! -d "$SOURCE/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$DELTACHAT_REPOSITORY" "$SOURCE"
fi
git -C "$SOURCE" fetch --depth 1 origin "refs/tags/$DELTACHAT_TAG:refs/tags/$DELTACHAT_TAG"
git -C "$SOURCE" checkout --detach --force "$DELTACHAT_TAG"
git -C "$SOURCE" reset --hard "$DELTACHAT_TAG"
git -C "$SOURCE" clean -ffdx
test -z "$(git -C "$SOURCE" status --porcelain --untracked-files=all)"

bindings_source="$SOURCE/src/main/java"
for package in com/b44t/messenger chat/delta/rpc; do
  test -d "$bindings_source/$package"
  mkdir -p "$DESTINATION/$package"
  find "$DESTINATION/$package" -type f -name '*.java' -delete
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

python3 - "$DESTINATION/chat/delta/rpc/types/Qr.java" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
source = path.read_text()
source = re.sub(
    r'@Type\(value = Qr\.([A-Za-z0-9_]+)\.class, name="([A-Za-z0-9_]+)"\)',
    lambda match: (
        '@Type(value = Qr.'
        + match.group(1)
        + '.class, name="'
        + re.sub(r'(?<!^)(?=[A-Z])', '_', match.group(2)).lower()
        + '")'
    ),
    source,
)
path.write_text(source)
PY
