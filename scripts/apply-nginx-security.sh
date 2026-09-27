#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
site_source="$repo_root/config/nginx/nju-transfer-rag-site.conf"
rate_source="$repo_root/config/nginx/nju-transfer-rag-rate-limit.conf"
site_target=/etc/nginx/sites-available/default
rate_target=/etc/nginx/conf.d/nju-transfer-rag-rate-limit.conf

[[ -f "$site_source" && -f "$rate_source" ]] || {
  echo "Nginx security configuration is missing." >&2
  exit 1
}
sudo test -f "$site_target"
sudo install -d -m 700 /var/backups/nju-transfer-rag
backup_dir=$(sudo mktemp -d /var/backups/nju-transfer-rag/nginx-config.XXXXXX)
sudo cp -a "$site_target" "$backup_dir/site.conf"
had_rate_config=0
if sudo test -e "$rate_target"; then
  sudo cp -a "$rate_target" "$backup_dir/rate.conf"
  had_rate_config=1
fi

restore_config() {
  sudo cp -a "$backup_dir/site.conf" "$site_target"
  if [[ "$had_rate_config" == 1 ]]; then
    sudo cp -a "$backup_dir/rate.conf" "$rate_target"
  else
    sudo rm -f -- "$rate_target"
  fi
  sudo nginx -t
  sudo systemctl reload nginx
}

on_failure() {
  trap - ERR
  echo "New Nginx configuration failed; restoring the previous configuration." >&2
  restore_config
}
trap on_failure ERR

sudo install -m 644 "$rate_source" "$rate_target"
sudo install -m 644 "$site_source" "$site_target"
sudo nginx -t
sudo systemctl reload nginx
trap - ERR
