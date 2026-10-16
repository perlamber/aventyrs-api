#!/usr/bin/env bash
# Builds the API jar against the current aventyrs-core and deploys it to aventyrs-srv
# (systemd unit aventyrs-api, port 22696). Prompts once for the sudo password.
#
# 1. Publishes aventyrs-core (version from its build.gradle) to mavenLocal, and points this
#    API's `org.aventyrs.core:aventyrs-core:<version>` dependency at it if it lags behind.
# 2. Builds the boot jar, uploads it and runs it extracted (lib/*.jar): liquibase-mongodb can't
#    read its own version from inside Spring Boot's nested-jar format and fails at startup.
# 3. scripts/deploy-remote.sh (run on the server) restarts the unit and waits until the API has really
#    started — Spring "Started", Liquibase done, HTTP answering, still alive 15s later. On failure it
#    prints the journal, rolls back to the previous build (app.prev) and this script exits non-zero.
# 4. Deletes the built jar locally and the uploaded jar on the server (app/ + app.prev/ are kept).
set -euo pipefail
HOST=${DEPLOY_HOST:-administrador@192.168.99.99}
cd "$(dirname "$0")/.."
CORE_DIR=${CORE_DIR:-../aventyrs-core/aventyrs-core}

# --- 1. core version -------------------------------------------------------------------------
core_version=$(sed -nE "s/^version *= *'([^']+)'.*/\1/p" "$CORE_DIR/build.gradle")
[ -n "$core_version" ] || { echo "Can't read version from $CORE_DIR/build.gradle" >&2; exit 1; }
dep_version=$(sed -nE "s/.*'org\.aventyrs\.core:aventyrs-core:([^']+)'.*/\1/p" build.gradle)

echo "==> Publishing aventyrs-core $core_version to mavenLocal"
(cd "$CORE_DIR" && ./gradlew publishToMavenLocal -q)

if [ "$dep_version" != "$core_version" ]; then
  echo "==> build.gradle: aventyrs-core $dep_version -> $core_version"
  sed -i '' -E "s/('org\.aventyrs\.core:aventyrs-core:)[^']+'/\1$core_version'/" build.gradle
fi

# --- 2. build & deploy -----------------------------------------------------------------------
rm -rf build/libs
trap 'rm -rf build/libs' EXIT
./gradlew bootJar -q
jar=$(ls build/libs/aventyrs-api-*.jar | grep -v -- '-plain\.jar$')
echo "==> Deploying $(basename "$jar") (core $core_version)"

scp -q "$jar" "$HOST":/opt/aventyrs/aventyrs-api.jar
scp -q docker-compose.yml "$HOST":aventyrs-api/docker-compose.yml
scp -q scripts/deploy-remote.sh "$HOST":aventyrs-api/deploy-remote.sh
ssh -t "$HOST" '
  set -e
  mkdir -p /opt/aventyrs/logs
  cd ~/aventyrs-api && API_LOG_DIR=/opt/aventyrs/logs docker compose --env-file /etc/aventyrs/aventyrs.env up -d
  bash ~/aventyrs-api/deploy-remote.sh
' || { status=$?; echo "==> DEPLOY FAILED (exit $status) — see the journal above" >&2; exit "$status"; }
echo "==> Deployed $(basename "$jar") (core $core_version)"
