#!/usr/bin/env bash
# Server half of deploy-prod.sh — uploaded to ~/aventyrs-api and run there over `ssh -t` (sudo prompts).
# Swaps in the uploaded jar, restarts the unit and waits until the API has really started; on any
# failure it prints the new run's journal, puts the previous app/ back and exits non-zero.
#
# "Started" means all of: Spring logged "Started AventyrsApiApplication", Liquibase (which runs after
# that line) finished, Tomcat answers HTTP on the port, and the process kept the same PID and stayed
# active for STABLE_SECONDS afterwards (a crash after startup is caught, not reported as success).
set -euo pipefail
UNIT=aventyrs-api
PORT=${SERVER_PORT:-22696}
BASE=/opt/aventyrs
START_TIMEOUT=${START_TIMEOUT:-240}
STABLE_SECONDS=${STABLE_SECONDS:-15}

fail() { echo "!! $*" >&2; }

journal() { journalctl -u "$UNIT" --since "$since" --no-pager -o cat 2>/dev/null; }

dump_journal() {
  echo "---- $UNIT journal since $since (last 80 lines) ----" >&2
  journal | tail -n 80 >&2
  echo "----" >&2
}

# Waits for the run that began at $since; returns 0 when started, 1 otherwise (reason on stderr).
wait_for_start() {
  local pid deadline log code
  pid=$(systemctl show -p MainPID --value "$UNIT")
  deadline=$((SECONDS + START_TIMEOUT))
  while :; do
    if [ "$(systemctl is-active "$UNIT" || true)" = failed ]; then
      fail "$UNIT entered the failed state"; return 1
    fi
    local now_pid
    now_pid=$(systemctl show -p MainPID --value "$UNIT")
    if [ "$now_pid" != "$pid" ]; then
      fail "$UNIT restarted during startup (PID $pid -> $now_pid) — it crashed"; return 1
    fi
    log=$(journal)
    if grep -qE 'APPLICATION FAILED TO START|Application run failed|Liquibase.*(failed|error)' <<<"$log"; then
      fail "startup error in the journal"; return 1
    fi
    if grep -q 'Started AventyrsApiApplication' <<<"$log" \
        && grep -q 'Liquibase: Update has been successful' <<<"$log"; then
      code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' "http://localhost:$PORT/" || true)
      [ "$code" != 000 ] && break
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
      fail "not started after ${START_TIMEOUT}s"; return 1
    fi
    sleep 3
  done
  sleep "$STABLE_SECONDS"
  if [ "$(systemctl is-active "$UNIT" || true)" != active ] \
      || [ "$(systemctl show -p MainPID --value "$UNIT")" != "$pid" ]; then
    fail "$UNIT died within ${STABLE_SECONDS}s of starting"; return 1
  fi
  echo "==> $UNIT started (PID $pid, HTTP $code on :$PORT)"
}

restart() {
  since=$(date '+%Y-%m-%d %H:%M:%S')
  sudo systemctl restart "$UNIT"
}

# --- swap in the new build, keeping the previous one for a rollback -----------------------------
cd "$BASE"
rm -rf app.new
java -Djarmode=tools -jar aventyrs-api.jar extract --destination app.new >/dev/null
rm -f aventyrs-api.jar
rm -rf app.prev
[ -d app ] && mv app app.prev
mv app.new app

restart
if wait_for_start; then
  exit 0
fi
dump_journal

if [ ! -d app.prev ]; then
  fail "no previous build to roll back to — prod is DOWN"; exit 1
fi
fail "rolling back to the previous build"
rm -rf app.failed && mv app app.failed && mv app.prev app
restart
if wait_for_start; then
  fail "DEPLOY FAILED — the previous build is running again (failed build kept in $BASE/app.failed)"
  exit 2
fi
dump_journal
fail "DEPLOY FAILED and the rollback did not start either — prod is DOWN"
exit 3
