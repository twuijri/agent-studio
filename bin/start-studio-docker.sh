#!/usr/bin/env bash
set -eu

# Docker-only: old Compose stacks override PATH, so the image ENV alone is not
# sufficient. Do not change HOME or profile/global configuration locations.
export NPM_CONFIG_PREFIX="${NPM_CONFIG_PREFIX:-${HERMES_HOME:-/home/agent/.hermes}/coding-agent/npm}"
legacy_studio_home="${HERMES_WEB_UI_HOME:-${HERMES_WEBUI_STATE_DIR:-/home/agent/.hermes-web-ui}}"
# Keep earlier persisted installs discoverable without copying, deleting or
# overwriting them. New installs use the Hermes volume; native paths stay intact.
export PATH="${NPM_CONFIG_PREFIX}/bin:${legacy_studio_home}/coding-agent/npm/bin:${PATH:-/usr/local/bin:/usr/bin:/bin}"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/start-studio-all.sh" "$@"
