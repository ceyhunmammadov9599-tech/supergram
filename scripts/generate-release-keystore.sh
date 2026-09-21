#!/usr/bin/env bash
# SuperGram Phase 8 — keystore preparation.
# Generates a release keystore for signing SuperGram APKs.
# Usage:  ./scripts/generate-release-keystore.sh [path/to/supergram-release.keystore]
set -euo pipefail

KEYSTORE="${1:-supergram-release.keystore}"
ALIAS="supergram"
VALIDITY_DAYS=10950   # 30 years
KEYSIZE=2048

if [ -f "$KEYSTORE" ]; then
    echo "Refusing to overwrite existing keystore: $KEYSTORE"
    exit 1
fi

echo "Generating release keystore at: $KEYSTORE"
keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize "$KEYSIZE" \
    -validity "$VALIDITY_DAYS"

cat <<NEXT

Keystore created. Now:
1. Create keystore.properties next to the Gradle settings file with:
   storeFile=$KEYSTORE
   storePassword=<your store password>
   keyAlias=$ALIAS
   keyPassword=<your key password>
   (see keystore.properties.example)
2. keystore.properties and the .keystore file are git-ignored; keep backups.
3. Build a signed release: ./gradlew :app:assembleRelease --no-daemon
NEXT
