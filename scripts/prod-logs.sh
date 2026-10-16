#!/usr/bin/env bash
# Opens Dozzle (the prod log viewer, docker-compose service `dozzle`) on this machine through an
# SSH tunnel, for as long as this script runs (Ctrl-C closes it).
#   scripts/prod-logs.sh             # http://localhost:9999
#   LOCAL_PORT=9998 scripts/prod-logs.sh
# Containers: aventyrs-api-logs = the API (INFO+; AVENTYRS_LOG_LEVEL=DEBUG in
# /etc/aventyrs/aventyrs.env + restart for more), aventyrs-seaweedfs = image storage, aventyrs-mongo.
set -euo pipefail
HOST=${DEPLOY_HOST:-administrador@192.168.99.99}
LOCAL_PORT=${LOCAL_PORT:-9999}

if lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $LOCAL_PORT is already in use locally; pick another with LOCAL_PORT=..." >&2
  exit 1
fi

url="http://localhost:$LOCAL_PORT"
echo "Prod logs (Dozzle): $url   (Ctrl-C closes the tunnel)"
(sleep 2 && open "$url") >/dev/null 2>&1 &
exec ssh -N -o ExitOnForwardFailure=yes -L "$LOCAL_PORT:localhost:9999" "$HOST"
