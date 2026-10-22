#!/usr/bin/env bash
# ONE-TIME: create the permanent signing key and store it in GitHub Secrets.
# Run it on your own device (Termux or laptop); the key never leaves your machine except as an encrypted GitHub secret.
# Needs: keytool (Termux: pkg install openjdk-17) and gh (logged in).
set -euo pipefail
REPO="${REPO:-paukuman/trader-launcher}"
KS="${KS:-$HOME/trader-launcher-release.jks}"
ALIAS="trader"

command -v keytool >/dev/null || { echo "keytool not found. In Termux run: pkg install openjdk-17"; exit 1; }
command -v gh >/dev/null || { echo "gh not found. In Termux run: pkg install gh && gh auth login"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in. Run: gh auth login"; exit 1; }
if [ -e "$KS" ]; then
  echo "$KS already exists. Refusing to overwrite a signing key."
  echo "If the secrets are missing, set them from the existing file instead of creating a new key."
  exit 1
fi

# head first: a reader that closes early (tr ... | head) makes pipefail exit silently with 141.
PASS="$(head -c 18 /dev/urandom | od -An -tx1 | tr -d "[:space:]")"
keytool -genkeypair -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Trader Launcher, O=devarz, C=ID"

umask 077
printf 'keystore: %s\nalias: %s\npassword: %s\n' "$KS" "$ALIAS" "$PASS" > "${KS%.jks}-password.txt"

gh secret set KEYSTORE_BASE64 -R "$REPO" --body "$(base64 -w0 "$KS")"
gh secret set KEYSTORE_PASSWORD -R "$REPO" --body "$PASS"
gh secret set KEY_PASSWORD -R "$REPO" --body "$PASS"
gh secret set KEY_ALIAS -R "$REPO" --body "$ALIAS"

echo
echo "Done. Secrets are set on $REPO."
echo "BACK UP THESE TWO FILES NOW (private cloud or password manager). If they are lost you cannot update the app again:"
echo "  $KS"
echo "  ${KS%.jks}-password.txt"
