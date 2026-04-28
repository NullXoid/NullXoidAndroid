#!/usr/bin/env bash
set -euo pipefail

FORGEJO_API="${FORGEJO_API:-http://git.echolabs.diy/api/v1}"
FORGEJO_REPO="${FORGEJO_REPO:-EchoLabs/NullXoidAndroid}"
FORGEJO_TOKEN="${FORGEJO_TOKEN:-}"
APK_NAME="${APK_NAME:-NullXoidAndroid-debug.apk}"
APK_PATH="${APK_PATH:-release/$APK_NAME}"
APK_URL="${APK_URL:-}"
VERSION_TAG="${VERSION_TAG:-}"
VERSION_NAME="${VERSION_NAME:-}"
LATEST_TAG="${LATEST_TAG:-latest-debug}"
TARGET_COMMITISH="${TARGET_COMMITISH:-$(git rev-parse HEAD 2>/dev/null || true)}"

log() {
  printf '[%s] %s\n' "$(date -Is)" "$*"
}

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get(sys.argv[1], ""))' "$1"
}

asset_id_for() {
  python3 -c '
import json, sys
data = json.load(sys.stdin)
target = sys.argv[1]
for asset in data.get("assets", []):
    if asset.get("name") == target:
        print(asset.get("id", ""))
        break
' "$1"
}

require_token() {
  if [ -z "$FORGEJO_TOKEN" ]; then
    cat >&2 <<'EOF'
FORGEJO_TOKEN is required.

Create a Forgejo token with repository release write access, then run for example:

  export FORGEJO_TOKEN='...'
  APK_URL='https://github.com/NullXoid/NullXoidAndroid/releases/download/v0.1.37/NullXoidAndroid-debug.apk' \
  VERSION_TAG='v0.1.37' \
  scripts/publish_forgejo_apk.sh
EOF
    exit 2
  fi
}

prepare_apk() {
  if [ -f "$APK_PATH" ]; then
    return 0
  fi
  if [ -z "$APK_URL" ]; then
    echo "APK not found at $APK_PATH and APK_URL was not provided." >&2
    exit 2
  fi
  mkdir -p "$(dirname "$APK_PATH")"
  log "downloading APK from $APK_URL"
  curl -fsSL "$APK_URL" -o "$APK_PATH"
}

publish_release() {
  local tag="$1"
  local name="$2"
  local body="$3"
  local api="${FORGEJO_API%/}/repos/${FORGEJO_REPO}"
  local response payload release_id asset_id

  response="$(curl -fsS \
    -H "Authorization: token ${FORGEJO_TOKEN}" \
    -H "Accept: application/json" \
    "${api}/releases/tags/${tag}" || true)"

  if [ -z "$response" ]; then
    payload="$(TAG="$tag" NAME="$name" BODY="$body" TARGET_COMMITISH="$TARGET_COMMITISH" python3 - <<'PY'
import json
import os

print(json.dumps({
    "tag_name": os.environ["TAG"],
    "target_commitish": os.environ.get("TARGET_COMMITISH", ""),
    "name": os.environ["NAME"],
    "body": os.environ["BODY"],
    "draft": False,
    "prerelease": True,
}))
PY
)"
    log "creating Forgejo release $tag"
    response="$(curl -fsS -X POST \
      -H "Authorization: token ${FORGEJO_TOKEN}" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -d "$payload" \
      "${api}/releases")"
  else
    log "updating Forgejo release asset on $tag"
  fi

  release_id="$(printf '%s' "$response" | json_field id)"
  if [ -z "$release_id" ]; then
    echo "Could not resolve Forgejo release id for $tag" >&2
    exit 1
  fi

  asset_id="$(printf '%s' "$response" | asset_id_for "$APK_NAME")"
  if [ -n "$asset_id" ]; then
    curl -fsS -X DELETE \
      -H "Authorization: token ${FORGEJO_TOKEN}" \
      -H "Accept: application/json" \
      "${api}/releases/assets/${asset_id}" \
      >/dev/null
  fi

  curl -fsS -X POST \
    -H "Authorization: token ${FORGEJO_TOKEN}" \
    -H "Accept: application/json" \
    -F "attachment=@${APK_PATH}" \
    "${api}/releases/${release_id}/assets?name=${APK_NAME}" \
    >/dev/null
}

main() {
  require_token
  prepare_apk
  if [ -z "$VERSION_TAG" ]; then
    VERSION_TAG="v0.1.$(git rev-list --count HEAD)"
  fi
  if [ -z "$VERSION_NAME" ]; then
    VERSION_NAME="Version ${VERSION_TAG#v0.1.}"
  fi
  publish_release "$VERSION_TAG" "$VERSION_NAME" "Manual Forgejo debug APK release for ${TARGET_COMMITISH}."
  publish_release "$LATEST_TAG" "Latest Debug APK" "Moving Forgejo debug APK pointer for ${TARGET_COMMITISH}."
  log "published $APK_NAME to Forgejo releases: $VERSION_TAG and $LATEST_TAG"
}

main "$@"
