#!/bin/sh
# Writes /config.js from API_BASE_URL and ADMIN_API_BASE_URL at container start (run by the nginx
# entrypoint). ADMIN_API_BASE_URL is where the admin console (admin.* host) calls the API; empty =
# same-origin /api/v1 on the admin host.
# The built files stay read-only; config.js lives in a writable runtime folder.
set -eu

out_dir=/tmp/rentrix
api_base_url="${API_BASE_URL:-}"
admin_api_base_url="${ADMIN_API_BASE_URL:-}"

# Only URL characters, so a value cannot break out of the JS string.
for name in API_BASE_URL ADMIN_API_BASE_URL; do
  eval "value=\${$name:-}"
  case "$value" in
    *[!A-Za-z0-9:/._~%?=\&+@-]*)
      echo "40-rentrix-config: $name has characters that are not allowed in a URL: refusing to start" >&2
      exit 1
      ;;
  esac
done

mkdir -p "$out_dir"
printf 'window.__RENTRIX_CONFIG__ = { apiBaseUrl: "%s", adminApiBaseUrl: "%s" }\n' \
  "$api_base_url" "$admin_api_base_url" > "$out_dir/config.js"
echo "40-rentrix-config: apiBaseUrl=${api_base_url:-<build default>} adminApiBaseUrl=${admin_api_base_url:-<same origin>}"
