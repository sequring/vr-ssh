#!/usr/bin/env bash
# Builds a signed release APK and publishes it as a GitHub release.
# Usage: scripts/release.sh            (version is taken from app/build.gradle.kts)
# Needs keystore.properties in the project root (see app/build.gradle.kts).
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f keystore.properties ] || { echo "keystore.properties not found — release builds must be signed"; exit 1; }
VERSION=$(sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
TAG="v$VERSION"
APK="build/vr-ssh-$VERSION.apk"

git diff --quiet && git diff --cached --quiet || { echo "Commit your changes first"; exit 1; }
git rev-parse "$TAG" >/dev/null 2>&1 && { echo "Tag $TAG already exists — bump versionCode/versionName"; exit 1; }

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
./gradlew --quiet clean assembleRelease
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk "$APK"

APKSIGNER=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/apksigner | tail -1)
"$APKSIGNER" verify --verbose "$APK" | grep -E "Verified using v2 scheme"

git tag -a "$TAG" -m "VR SSH $VERSION"
git push origin "$TAG"
gh release create "$TAG" "$APK" --title "VR SSH $VERSION" --generate-notes
