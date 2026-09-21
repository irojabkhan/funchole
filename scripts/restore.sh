#!/bin/sh
#
# Restores a backup produced by ./scripts/backup.sh. DESTRUCTIVE: this
# replaces the running stack's database and OpenBao state.
#
# Usage (from the repo root):
#   ./scripts/restore.sh <db-dump.sql.gz> <openbao-volume.tar.gz>
#
# Both the `db` and `openbao` services must already be up (run
# `docker compose -f docker-compose.yml up -d db openbao` first if you're
# restoring onto a fresh host) - this script does not start the stack for
# you, and does not start the app services (controlplane/gateway/
# dispatcher/runtime) until you do so yourself afterward, so nothing tries
# to use half-restored state.

set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <db-dump.sql.gz> <openbao-volume.tar.gz>" >&2
  exit 1
fi

DB_DUMP="$1"
OPENBAO_ARCHIVE="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -f "$DB_DUMP" ]; then
  echo "Database dump not found: $DB_DUMP" >&2
  exit 1
fi
if [ ! -f "$OPENBAO_ARCHIVE" ]; then
  echo "OpenBao archive not found: $OPENBAO_ARCHIVE" >&2
  exit 1
fi

echo "This will REPLACE the running stack's database and OpenBao state."
printf "Type 'yes' to continue: "
read -r confirmation
if [ "$confirmation" != "yes" ]; then
  echo "Aborted."
  exit 1
fi

echo "Restoring Postgres from ${DB_DUMP}..."
gunzip -c "$DB_DUMP" | docker compose -f "${SCRIPT_DIR}/docker-compose.yml" exec -T db \
  psql -U funchole -d funchole
echo "  Postgres restored."

echo "Stopping OpenBao before restoring its volume..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" stop openbao

OPENBAO_VOLUME="$(docker volume ls --filter label=com.docker.compose.volume=openbao-data --format '{{.Name}}' | head -n1)"
if [ -z "$OPENBAO_VOLUME" ]; then
  echo "Could not find the openbao-data volume." >&2
  exit 1
fi

echo "Restoring OpenBao volume from ${OPENBAO_ARCHIVE}..."
docker run --rm \
  -v "${OPENBAO_VOLUME}:/openbao-data" \
  -v "$(cd "$(dirname "$OPENBAO_ARCHIVE")" && pwd)/$(basename "$OPENBAO_ARCHIVE"):/backup.tar.gz:ro" \
  alpine:latest \
  sh -c "rm -rf /openbao-data/* && tar xzf /backup.tar.gz -C /openbao-data"
echo "  OpenBao volume restored."

echo "Starting OpenBao back up..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" start openbao

# OpenBao always comes back sealed after its process restarts - sealing on
# restart isn't something auto-unseal skips, only staying unsealed while
# running is. openbao-init already ran once during the original `up` and
# won't re-run on its own (its container already exited 0, satisfying
# compose's dependency condition), so re-run it explicitly here rather than
# leaving the operator to discover a sealed OpenBao the hard way.
echo "Re-running openbao-init to unseal OpenBao with the restored keys..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" run --rm openbao-init

echo "Restore complete. Start the rest of the stack with:"
echo "  docker compose -f docker-compose.yml up -d"
