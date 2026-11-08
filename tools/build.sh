#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. tools/env.sh
python3 tools/init_signing.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest :startup-overlay:assembleRelease :startup-overlay:lintRelease --console=plain
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Clean-Home-1.4.0.apk
cp startup-overlay/build/outputs/apk/release/startup-overlay-release.apk dist/Clean-Home-Power-1.0.0.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose dist/Clean-Home-1.4.0.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose dist/Clean-Home-Power-1.0.0.apk
shasum -a 256 dist/Clean-Home-1.4.0.apk dist/Clean-Home-Power-1.0.0.apk > dist/SHA256SUMS
