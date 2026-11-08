#!/usr/bin/env bash
# Build an IPA of InfoTB (SwiftUI + KMP shared framework + Live Activity extension).
#
#   ./build-ipa.sh                                 # Unsigned Release IPA (for Sideloadly, iLoader, AltStore, Signulous)
#   ./build-ipa.sh --team <TEAM_ID>                # Signed IPA using automatic provisioning
#   ./build-ipa.sh --method release-testing        # Ad-hoc (requires signing team)
#   ./build-ipa.sh --method app-store-connect      # For TestFlight / App Store (requires signing team)
#   ./build-ipa.sh --debug                         # Debug build instead of Release
#   ./build-ipa.sh --clean                         # Clean build before archiving
#   ./build-ipa.sh --out ~/Desktop/ipa             # Custom output directory
#   ./build-ipa.sh --sign                          # Upload to Signulous after build
#   ./build-ipa.sh --unsigned                      # Force unsigned build
#
# Automatically selects a compatible JDK (17 or 21) required by Gradle 8.11.
# If no signing team is configured in Xcode or passed via --team, an unsigned
# IPA is created by default, ready for sideloading tools.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$HERE/settings.gradle.kts" && -d "$HERE/iosApp" ]]; then
  ROOT="$HERE"
elif [[ -f "$HERE/../settings.gradle.kts" && -d "$HERE/../iosApp" ]]; then
  ROOT="$(cd "$HERE/.." && pwd)"
elif [[ -f "$HERE/../../settings.gradle.kts" && -d "$HERE/../../iosApp" ]]; then
  ROOT="$(cd "$HERE/../.." && pwd)"
else
  ROOT="$HERE"
fi

METHOD="debugging"
OUT="$ROOT/build/ipa"
CONFIGURATION="Release"
CLEAN=0
SIGN=0
FORCE_UNSIGNED=0
TEAM="${DEVELOPMENT_TEAM:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --method) METHOD="$2"; shift 2 ;;
    --team) TEAM="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --debug) CONFIGURATION="Debug"; shift ;;
    --clean) CLEAN=1; shift ;;
    --sign) SIGN=1; shift ;;
    --unsigned) FORCE_UNSIGNED=1; shift ;;
    -h|--help)
      sed -n '2,/^set -euo pipefail/{ /^set -euo pipefail/d; p; }' "$0"
      exit 0
      ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

# --- JDK -------------------------------------------------------------------
pick_jdk() {
  local v
  for v in 21 17; do
    if /usr/libexec/java_home -v "$v" >/dev/null 2>&1; then
      /usr/libexec/java_home -v "$v"; return
    fi
  done
  return 1
}

if ! JAVA_HOME="$(pick_jdk)"; then
  echo "Error: No JDK 17 or 21 found (Gradle 8.11 cannot run on newer ones). Install Temurin 21:" >&2
  echo "  brew install --cask temurin@21" >&2
  exit 1
fi
export JAVA_HOME
echo "JDK: $JAVA_HOME"

# --- Project version & Signing Team ----------------------------------------
PBXPROJ="$ROOT/iosApp/iosApp.xcodeproj/project.pbxproj"
if [[ ! -f "$PBXPROJ" ]]; then
  echo "Error: Xcode project not found at $PBXPROJ" >&2
  exit 1
fi

VERSION="$(grep -m1 'MARKETING_VERSION = ' "$PBXPROJ" | sed -E 's/.*= ([0-9.]+);.*/\1/' || true)"
VERSION="${VERSION:-0.4.0}"

if [[ -z "$TEAM" ]]; then
  TEAM="$(grep -m1 'DEVELOPMENT_TEAM = ' "$PBXPROJ" | sed -E 's/.*= ([A-Z0-9]+);.*/\1/' || true)"
fi

IS_SIGNED=0
if [[ $FORCE_UNSIGNED -eq 0 && -n "$TEAM" ]]; then
  IS_SIGNED=1
fi

STAMP="$(date +%Y%m%d-%H%M)"
mkdir -p "$OUT"
ARCHIVE="$OUT/InfoTB-$VERSION-$STAMP.xcarchive"
EXPORT_DIR="$OUT/export-$STAMP"

if [[ $IS_SIGNED -eq 1 ]]; then
  echo "Configuration: $CONFIGURATION, Version: $VERSION, Team: $TEAM, Method: $METHOD"
else
  if [[ -z "$TEAM" && $FORCE_UNSIGNED -eq 0 ]]; then
    echo "No DEVELOPMENT_TEAM detected in Xcode project or environment."
  fi
  echo "Configuration: $CONFIGURATION, Version: $VERSION (Unsigned for sideloading)"
fi

# --- Clean if requested ----------------------------------------------------
cd "$ROOT/iosApp"
if [[ $CLEAN -eq 1 ]]; then
  echo "Cleaning…"
  xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration "$CONFIGURATION" clean -quiet || true
  (cd "$ROOT" && ./gradlew :shared:clean -q) || true
fi

# --- Archive ---------------------------------------------------------------
if [[ $IS_SIGNED -eq 1 ]]; then
  echo "Archiving ($CONFIGURATION, team $TEAM)…"
  xcodebuild \
    -project iosApp.xcodeproj \
    -scheme iosApp \
    -configuration "$CONFIGURATION" \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE" \
    -allowProvisioningUpdates \
    DEVELOPMENT_TEAM="$TEAM" \
    archive
else
  echo "Archiving ($CONFIGURATION, unsigned)…"
  xcodebuild \
    -project iosApp.xcodeproj \
    -scheme iosApp \
    -configuration "$CONFIGURATION" \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE" \
    CODE_SIGN_IDENTITY="-" \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGNING_ALLOWED=NO \
    archive
fi

# --- Package / Export IPA --------------------------------------------------
if [[ $IS_SIGNED -eq 1 ]]; then
  PLIST="$OUT/ExportOptions-$METHOD-$STAMP.plist"
  cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>$METHOD</string>
	<key>teamID</key>
	<string>$TEAM</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>compileBitcode</key>
	<false/>
	<key>stripSwiftSymbols</key>
	<true/>
	<key>thinning</key>
	<string>&lt;none&gt;</string>
</dict>
</plist>
PL

  echo "Exporting ($METHOD)…"
  xcodebuild \
    -exportArchive \
    -archivePath "$ARCHIVE" \
    -exportOptionsPlist "$PLIST" \
    -exportPath "$EXPORT_DIR" \
    -allowProvisioningUpdates \
    -quiet

  IPA="$(ls "$EXPORT_DIR"/*.ipa 2>/dev/null | head -1 || true)"
  if [[ -z "$IPA" ]]; then
    echo "Export finished but no .ipa found in $EXPORT_DIR" >&2
    exit 1
  fi

  FINAL="$OUT/InfoTB-$VERSION-$STAMP-$METHOD.ipa"
  mv "$IPA" "$FINAL"
  rm -rf "$EXPORT_DIR" "$PLIST"
else
  echo "Packaging unsigned IPA…"
  STAGE="$(mktemp -d)"
  mkdir -p "$STAGE/Payload"

  APP_PATH="$(find "$ARCHIVE/Products/Applications" -maxdepth 1 -name "*.app" 2>/dev/null | head -1 || true)"
  if [[ -z "$APP_PATH" || ! -d "$APP_PATH" ]]; then
    echo "Error: Application bundle not found in $ARCHIVE/Products/Applications" >&2
    rm -rf "$STAGE"
    exit 1
  fi

  cp -R "$APP_PATH" "$STAGE/Payload/"

  # Keep the app-group entitlement in the shipped signatures. Re-signing tools
  # discover requested capabilities from these, even for an unsigned archive.
  # Ad-hoc signatures still require re-signing with a device provisioning profile.
  STAGED_APP="$STAGE/Payload/$(basename "$APP_PATH")"
  codesign --force --sign - --timestamp=none --generate-entitlement-der \
    --entitlements "$ROOT/iosApp/InfoTBLiveActivity/InfoTBLiveActivity.entitlements" \
    "$STAGED_APP/PlugIns/InfoTBLiveActivity.appex"
  codesign --force --sign - --timestamp=none --generate-entitlement-der \
    --entitlements "$ROOT/iosApp/iosApp/iosApp.entitlements" "$STAGED_APP"
  codesign --verify --deep --strict "$STAGED_APP"
  FINAL="$OUT/InfoTB-$VERSION-$STAMP-unsigned.ipa"
  (cd "$STAGE" && zip -qry "$FINAL" Payload)
  rm -rf "$STAGE"
fi

# Create convenient static links/copies
cp -f "$FINAL" "$OUT/InfoTB.ipa"
mkdir -p "$ROOT/build"
cp -f "$FINAL" "$ROOT/build/InfoTB.ipa"

echo
echo "✓ IPA created successfully: $FINAL"
echo "  Also available at: $OUT/InfoTB.ipa and $ROOT/build/InfoTB.ipa"
echo "  Archive saved at:  $ARCHIVE"

# --- Signulous upload if requested -----------------------------------------
if [[ $SIGN -eq 1 ]]; then
  echo
  SIGN_SCRIPT=""
  for candidate in \
    "$ROOT/sign-signulous.sh" \
    "$ROOT/scripts/sign-signulous.sh" \
    "$HERE/sign-signulous.sh" \
    "$HOME/DevProjects/maskdr/kmp/scripts/sign-signulous.sh"; do
    if [[ -x "$candidate" ]]; then
      SIGN_SCRIPT="$candidate"
      break
    fi
  done

  if [[ -n "$SIGN_SCRIPT" ]]; then
    echo "Invoking Signulous script: $SIGN_SCRIPT"
    exec "$SIGN_SCRIPT" "$FINAL"
  else
    echo "Error: --sign was requested but sign-signulous.sh was not found." >&2
    echo "Looked in:" >&2
    echo "  $ROOT/sign-signulous.sh" >&2
    echo "  $ROOT/scripts/sign-signulous.sh" >&2
    echo "  $HOME/DevProjects/maskdr/kmp/scripts/sign-signulous.sh" >&2
    exit 1
  fi
fi
