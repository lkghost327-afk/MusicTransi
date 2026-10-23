#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ "$(uname -s)" != Darwin ]; then
  echo 'Run this launcher on your Mac.'; exit 1
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v '17+' 2>/dev/null || true)
  export JAVA_HOME
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$JAVA" -version >/dev/null 2>&1; then
  echo 'Install a JDK 17 or newer for your Mac, then run this again.'; exit 1
fi
if [ ! -d lib ]; then
  echo 'The lib folder is missing. Extract the entire Mac install kit before running this launcher.'; exit 1
fi
umask 077
if [ ! -f .bridge-token.secret ]; then
  /usr/bin/openssl rand -hex 32 > .bridge-token.secret
fi
export MUSICTRANSI_TOKEN
MUSICTRANSI_TOKEN=$(tr -d '\r\n' < .bridge-token.secret)
export MUSICTRANSI_HOST=0.0.0.0
export MUSICTRANSI_PORT=8787
HOST=$(/usr/sbin/scutil --get LocalHostName 2>/dev/null || hostname -s)
echo 'Keep this Terminal window open and your Mac awake while streaming.'
echo 'Connect your iPhone and Mac to the same trusted Wi-Fi network.'
printf '\nIn MusicTransi Settings, enter URL: http://%s.local:8787\n' "$HOST"
printf 'Private bridge token (paste into the app, do not share): %s\n\n' "$MUSICTRANSI_TOKEN"
exec "$JAVA" -cp 'lib/*' com.example.musictransi.bridge.MainKt
