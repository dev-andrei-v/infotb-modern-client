#!/usr/bin/env bash
# Convenience wrapper to sign InfoTB via your local Signulous script
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HOME/DevProjects/maskdr/kmp/scripts/sign-signulous.sh"

if [[ ! -f "$SCRIPT" ]]; then
  echo "Error: Signulous script not found at $SCRIPT" >&2
  exit 1
fi

IPA="${1:-"$HERE/build/InfoTB.ipa"}"
exec bash "$SCRIPT" "$IPA" "${@:2}"
