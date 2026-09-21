#!/bin/sh
#
# Backs up the production stack's durable state: the Postgres database
# (everything except Function/artifact bytes, which live in the S3-
# compatible RustFS store - see below) and OpenBao's storage volume
# (secrets, the root/unseal key, and the ACME account key).
#
# Usage (from the repo root, with the production stack already running):
#   ./scripts/backup.sh [output-directory]
#
# Defaults to ./backups. Each run produces two timestamped files:
#   funchole-db-<timestamp>.sql.gz
#   funchole-openbao-<timestamp>.tar.gz
#
# RustFS/S3 artifact data is NOT covered here - back that up with your S3
# tooling of choice (e.g. `aws s3 sync`) or your VPS provider's volume
# snapshot feature, since it's already content-addressed/immutable object
# storage rather than a database this script knows how to dump.
#
# This targets the "backend"-named compose project (the default when run
# from this repo's root with no COMPOSE_PROJECT_NAME override). If you
# started the stack with a custom -p/--project-name, pass it via
# COMPOSE_PROJECT_NAME=<name> ./scripts/backup.sh instead.

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="${1:-${SCRIPT_DIR}/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$OUTPUT_DIR"

echo "Backing up Postgres..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" exec -T db \
  pg_dump -U funchole -d funchole --clean --if-exists \
  | gzip > "${OUTPUT_DIR}/funchole-db-${TIMESTAMP}.sql.gz"
echo "  -> ${OUTPUT_DIR}/funchole-db-${TIMESTAMP}.sql.gz"

echo "Backing up OpenBao volume..."
OPENBAO_VOLUME="$(docker volume ls --filter label=com.docker.compose.volume=openbao-data --format '{{.Name}}' | head -n1)"
if [ -z "$OPENBAO_VOLUME" ]; then
  echo "Could not find the openbao-data volume. Is the stack running under this compose project?" >&2
  exit 1
fi
docker run --rm \
  -v "${OPENBAO_VOLUME}:/openbao-data:ro" \
  -v "${OUTPUT_DIR}:/backup" \
  alpine:latest \
  tar czf "/backup/funchole-openbao-${TIMESTAMP}.tar.gz" -C /openbao-data .
echo "  -> ${OUTPUT_DIR}/funchole-openbao-${TIMESTAMP}.tar.gz"

echo "Backup complete. Store these files somewhere other than this VPS."
