#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# alioss4droid - generate release keystore (Linux / macOS / WSL)
#
# Usage (from the project root):
#   bash scripts/gen-release-keystore.sh
#
# The script will:
#   1. Prompt for passwords (stored only in the generated keystore.properties)
#   2. Create alioss4droid-release.jks in the project root
#   3. Create keystore.properties in the project root
#
# Do NOT commit the generated .jks or keystore.properties to git.
# -----------------------------------------------------------------------------
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"
KEYSTORE_FILE="$PROJECT_ROOT/alioss4droid-release.jks"
PROPS_FILE="$PROJECT_ROOT/keystore.properties"
KEY_ALIAS="alioss4droid"
DNAME="CN=alioss4droid, O=jinlongliao, C=CN"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "keystore already exists at $KEYSTORE_FILE"
    echo "Remove it manually before running this script again."
    exit 1
fi

read -r -s -p "Keystore password (min 6 chars): " STORE_PWD
echo
read -r -s -p "Key password (can be same as keystore): " KEY_PWD
echo

echo "Generating keystore..."
keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA -keysize 2048 -validity 36500 \
    -alias "$KEY_ALIAS" \
    -dname "$DNAME" \
    -storetype PKCS12 \
    -storepass "$STORE_PWD" \
    -keypass "$KEY_PWD"

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "keytool failed; make sure JDK is on PATH."
    exit 1
fi

cat > "$PROPS_FILE" <<EOF
storeFile=alioss4droid-release.jks
storePassword=$STORE_PWD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PWD
EOF

echo
echo "Done."
echo "Keystore: $KEYSTORE_FILE"
echo "Props:    $PROPS_FILE"
echo "Both are git-ignored. Keep them safe (losing them means Play Console cannot accept future updates)."
