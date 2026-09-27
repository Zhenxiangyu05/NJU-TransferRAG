#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

jar_path=$(find target -maxdepth 1 -type f -name 'Transfer-RAG-*.jar' ! -name '*.original' | head -n 1)
[[ -n "$jar_path" ]] || {
  echo "Build the application first: ./mvnw package -DskipTests" >&2
  exit 2
}

dry_run=true
if [[ "${1:-}" == "--apply" ]]; then
  dry_run=false
elif [[ $# -ne 0 ]]; then
  echo "Usage: $0 [--apply]" >&2
  exit 2
fi

exec java -jar "$jar_path" \
  --spring.main.web-application-type=none \
  --app.qdrant-backfill.enabled=true \
  --app.qdrant-backfill.dry-run="$dry_run"
