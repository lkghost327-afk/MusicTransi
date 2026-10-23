#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ "$(uname -s)" != Darwin ]; then
  echo 'This check requires macOS and the full Xcode app.'; exit 1
fi
if ! /usr/bin/xcrun --find xcodebuild >/dev/null 2>&1; then
  echo 'Install and open Xcode first, then choose Xcode in Settings > Locations > Command Line Tools.'; exit 1
fi
if ! /usr/bin/xcrun --sdk iphoneos --show-sdk-path >/dev/null 2>&1; then
  echo 'The iOS SDK is missing. Open Xcode, install iOS support, and select Xcode in Settings > Locations > Command Line Tools.'; exit 1
fi
echo 'Checking compilation without signing. This does not install the app.'
/usr/bin/xcodebuild -project MusicTransi.xcodeproj -scheme MusicTransi \
  -configuration Debug -destination 'generic/platform=iOS' \
  -derivedDataPath .build-check CODE_SIGNING_ALLOWED=NO build 2>&1 | tee Mac-build.log
echo 'Build passed. Open MusicTransi.xcodeproj to select your signing team and iPhone, then Run.'
