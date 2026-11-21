#!/bin/sh
# Writes /config.js from API_BASE_URL at container start (run by the nginx entrypoint).
# The built files stay read-only; config.js lives in a writable runtime folder.
set -eu

out_dir=/tmp/rentrix
api_base_url="${API_BASE_URL:-}"

# Only URL characters, so the value cannot break out of the JS string.
case "$api_base_url" in
  *[!A-Za-z0-9:/._~%?=\&+@-]*)
    echo "40-rentrix-config: API_BASE_URL has characters that are not allowed in a URL: refusing to start" >&2
    exit 1
    ;;
esac

mkdir -p "$out_dir"
printf 'window.__RENTRIX_CONFIG__ = { apiBaseUrl: "%s" }\n' "$api_base_url" > "$out_dir/config.js"
echo "40-rentrix-config: apiBaseUrl=${api_base_url:-<build default>}"
