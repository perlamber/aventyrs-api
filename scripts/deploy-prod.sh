#!/usr/bin/env bash
# Builds the API jar against the current aventyrs-core and deploys it to aventyrs-srv
# (systemd unit aventyrs-api, port 22696). Prompts once for the sudo password.
#
# 1. Publishes aventyrs-core (version from its build.gradle) to mavenLocal, and points this
#    API's `org.aventyrs.core:aventyrs-core:<version>` dependency at it if it lags behind.
# 2. Builds the boot jar, uploads it and runs it extracted (lib/*.jar): liquibase-mongodb can't
#    read its own version from inside Spring Boot's nested-jar format and fails at startup.
# 3. Deletes the built jar locally and the uploaded jar on the server (only app/ is kept).
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
ssh -t "$HOST" '
  set -e
  cd ~/aventyrs-api && docker-compose --env-file /etc/aventyrs/aventyrs.env up -d
  cd /opt/aventyrs && rm -rf app.new && java -Djarmode=tools -jar aventyrs-api.jar extract --destination app.new >/dev/null
  rm -f aventyrs-api.jar
  rm -rf app && mv app.new app
  sudo systemctl restart aventyrs-api
  for i in $(seq 1 36); do ss -tln | grep -q ":22696" && break; sleep 5; done
  systemctl is-active aventyrs-api
'
