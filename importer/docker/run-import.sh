#!/usr/bin/env bash
# Starts Postgres, runs the import once, then keeps Postgres up so the result can be inspected
# and dumped. Everything lives under /data (the volume): the database, the report and a marker
# that the import finished. On a crash Fly restarts the machine and the importer resumes from its
# last committed chunk; the marker stops a finished import from starting over after a reboot.
set -euo pipefail

: "${POSTGRES_PASSWORD:?set it as a Fly secret}"
: "${IMPORT_SOURCE:?the dump URL or file}"
DONE=/data/import-done

# Bulk-load settings: big buffers and rare checkpoints. fsync stays on. synchronous_commit=off
# can lose the last few commits in a crash; that is safe only because the Spring Batch job
# repository lives in this same database, so chunk rows and the resume checkpoint roll back
# together. max_wal_size is bounded so WAL can't eat the disk the tables need.
docker-entrypoint.sh postgres \
    -c shared_buffers="${PG_SHARED_BUFFERS:-4GB}" \
    -c max_wal_size=8GB \
    -c checkpoint_timeout=30min \
    -c wal_compression=on \
    -c synchronous_commit=off &
pg=$!
java_pid=

stop_postgres() {
    gosu postgres pg_ctl -D "$PGDATA" stop -m fast || true
}

# As PID 1, bash ignores SIGTERM unless it traps it: stop the importer, then Postgres, cleanly.
on_term() {
    [[ -n "$java_pid" ]] && kill -TERM "$java_pid" 2>/dev/null && wait "$java_pid" || true
    stop_postgres
    exit 0
}
trap on_term TERM INT

# Over TCP: during first-time setup the image runs a temporary server on the socket only.
until pg_isready -h localhost -U postgres -q; do
    kill -0 "$pg" 2>/dev/null || { echo "postgres exited during startup" >&2; exit 1; }
    sleep 2
done

if [[ "${IMPORT_PAUSED:-}" == 1 ]]; then
    # For fixing data by hand between attempts: deploy with --env IMPORT_PAUSED=1, then without.
    echo "IMPORT_PAUSED=1: Postgres only, the importer is not started"
elif [[ -e "$DONE" ]]; then
    echo "import already finished ($(cat "$DONE")); delete $DONE to run it again"
else
    # In the background so the TERM trap can run while it works; an OOM exits instead of hanging,
    # so Fly restarts and the import resumes.
    java -XX:MaxRAMPercentage="${JAVA_RAM_PERCENT:-25}" -XX:+ExitOnOutOfMemoryError \
        -jar /app/importer.jar \
        --spring.datasource.url="jdbc:postgresql://localhost:5432/$POSTGRES_DB" \
        --spring.datasource.username=postgres \
        --spring.datasource.password="$POSTGRES_PASSWORD" \
        --import.report-dir=/data &
    java_pid=$!
    if ! wait "$java_pid"; then
        stop_postgres
        exit 1
    fi
    java_pid=
    date -u +%FT%TZ > "$DONE"
    echo "import finished; report at /data/import-report.md"
fi

wait "$pg"
