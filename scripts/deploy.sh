#!/usr/bin/env bash
set -Eeuo pipefail

trap 'echo "Deployment failed at line $LINENO; existing running services were not intentionally replaced after a failed build." >&2' ERR

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

echo "[1/9] Checking repository state"
git status --short
if [[ -n "$(git status --porcelain)" && "${DEPLOY_ALLOW_DIRTY:-0}" != "1" ]]; then
  echo "Refusing to deploy a dirty worktree. Commit/stash changes, or set DEPLOY_ALLOW_DIRTY=1 after reviewing them." >&2
  exit 2
fi

for command_name in java npm rsync curl systemctl nginx; do
  command -v "$command_name" >/dev/null || {
    echo "Required command is missing: $command_name" >&2
    exit 3
  }
done

echo "[2/9] Building Spring Boot JAR"
./mvnw package -DskipTests

echo "[3/9] Building public Vue frontend"
(
  cd transfer-rag-web
  npm ci
  npm run build
  test -s dist/index.html
)

echo "[4/9] Building protected admin frontend"
(
  cd transfer-rag-admin
  npm ci
  npm run build
  test -s dist/index.html
  grep -q '/admin/assets/' dist/index.html
)

echo "[5/9] Validating Nginx configuration"
bash ./scripts/apply-nginx-security.sh

echo "[6/9] Publishing static assets"
sudo install -d -o root -g root -m 755 /var/www/nju-transfer-rag /var/www/admin
sudo rsync -a --delete transfer-rag-web/dist/ /var/www/nju-transfer-rag/
sudo rsync -a --delete transfer-rag-admin/dist/ /var/www/admin/
sudo chown -R root:root /var/www/nju-transfer-rag /var/www/admin
sudo find /var/www/nju-transfer-rag /var/www/admin -type d -exec chmod 755 {} +
sudo find /var/www/nju-transfer-rag /var/www/admin -type f -exec chmod 644 {} +

echo "[7/9] Restarting backend"
sudo systemctl restart nju-transfer-rag.service

echo "[8/9] Waiting for backend health"
backend_ready=0
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/api/test/vector-store >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 2
done
[[ "$backend_ready" == "1" ]] || {
  sudo systemctl status nju-transfer-rag.service --no-pager -l || true
  exit 4
}

echo "[9/9] Running HTTP smoke tests"
root_code=$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' http://127.0.0.1/)
rag_code=$(curl -sS --max-time 120 -o /dev/null -w '%{http_code}'   -X POST http://127.0.0.1/api/rag/ask   -H 'Content-Type: application/json'   --data '{"question":"Deployment smoke test"}')
admin_code=$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' http://127.0.0.1/admin/)

[[ "$root_code" == "200" ]] || { echo "Homepage check failed: HTTP $root_code" >&2; exit 5; }
[[ "$rag_code" == "200" ]] || { echo "RAG check failed: HTTP $rag_code" >&2; exit 6; }
[[ "$admin_code" == "401" ]] || { echo "Admin protection check failed: HTTP $admin_code" >&2; exit 7; }

systemctl is-active --quiet nju-transfer-rag.service
systemctl is-active --quiet nginx.service

echo "Deployment successful: homepage=200 rag=200 admin_unauthenticated=401"
