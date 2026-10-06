#!/usr/bin/env bash
# Opens an SSH shell on aventyrs-srv and forwards the prod Mongo there to this machine for as
# long as the shell stays open. The local port defaults to 27019 so it doesn't clash with dev Mongo.
#   scripts/prod-ssh.sh            # shell + tunnel on localhost:27019
#   LOCAL_PORT=27020 scripts/prod-ssh.sh
set -euo pipefail
HOST=${DEPLOY_HOST:-administrador@192.168.99.99}
LOCAL_PORT=${LOCAL_PORT:-27019}

if lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $LOCAL_PORT is already in use locally; pick another with LOCAL_PORT=..." >&2
  exit 1
fi

echo "Prod Mongo: mongodb://localhost:$LOCAL_PORT/aventyrs?directConnection=true"
echo "(tunnel closes when you exit the shell)"
exec ssh -o ExitOnForwardFailure=yes -L "$LOCAL_PORT:localhost:27017" "$HOST"
