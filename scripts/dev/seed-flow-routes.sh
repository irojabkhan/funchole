#!/bin/sh
#
# Local-only seed data for Flow route-resolution testing.
# Safe to commit because it only touches local development seed data.
#
# Attaches two exact-match routes (flows) with one adopted-but-stepless
# flow version each to the existing development gateway
# (unique_key = 'a6n1y8', Primary Gateway). No-ops if that gateway does not
# exist. These two routes (POST /orders, POST /checkout) have no steps and
# are not meant to be invoked - they exist only for Gateway route-resolution
# tests that need more than one registered route on the same gateway.
#
# GET /orders used to be seeded here too, with a fake step chain and
# placeholder component_id/component_version_id that were never real
# Functions. As of STORY-M1-08 that whole demo is provisioned for real
# instead, via ./scripts/dev/seed-orders-demo.sh, which drives the actual
# Controlplane API (create Function, submit source, deploy, create Flow,
# add steps, adopt) - see that script for the replacement. GAP-05 tracked
# the raw-SQL version of this demo; run seed-orders-demo.sh, not this
# script, to get a working GET /orders.
#
# Runs psql inside the dev DB container so no local psql client is required.
#
# Usage:
#   ./scripts/dev/seed-flow-routes.sh
#

set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.dev.yml}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_NAME="${DB_NAME:-funchole}"
DB_USERNAME="${DB_USERNAME:-funchole}"

if ! docker compose -f "${COMPOSE_FILE}" ps "${DB_SERVICE}" 2>/dev/null | grep -q "running\|Up"; then
    echo "Starting ${DB_SERVICE} service from ${COMPOSE_FILE}..."
    docker compose -f "${COMPOSE_FILE}" up -d "${DB_SERVICE}"
    for i in $(seq 1 30); do
        if docker compose -f "${COMPOSE_FILE}" exec -T "${DB_SERVICE}" pg_isready -U "${DB_USERNAME}" -d "${DB_NAME}" >/dev/null 2>&1; then
            echo "${DB_SERVICE} ready"
            break
        fi
        sleep 2
    done
fi

PSQL="docker compose -f ${COMPOSE_FILE} exec -T ${DB_SERVICE} psql -U ${DB_USERNAME} -d ${DB_NAME}"

$PSQL <<'EOF'
INSERT INTO flow_versions (
    id,
    flow_id,
    version,
    status,
    runtime,
    adopted_at
)
SELECT
    v.id,
    v.flow_id,
    1,
    'ADOPTED',
    'NODE',
    CURRENT_TIMESTAMP
FROM (
    VALUES
        ('66666666-6666-6666-6666-666666666662'::uuid, '55555555-5555-5555-5555-555555555552'::uuid),
        ('66666666-6666-6666-6666-666666666663'::uuid, '55555555-5555-5555-5555-555555555553'::uuid)
) AS v (id, flow_id)
WHERE EXISTS (SELECT 1 FROM gateways WHERE unique_key = 'a6n1y8')
ON CONFLICT DO NOTHING;

INSERT INTO flows (
    id,
    app_user_id,
    gateway_id,
    active_flow_version_id,
    active_flow_version_status,
    flow_key,
    name,
    description,
    http_method,
    path
)
SELECT
    f.id,
    u.id,
    g.id,
    f.active_flow_version_id,
    'ADOPTED',
    f.flow_key,
    f.name,
    f.description,
    f.http_method,
    f.path
FROM (
    VALUES
        (
            '55555555-5555-5555-5555-555555555552'::uuid,
            '66666666-6666-6666-6666-666666666662'::uuid,
            'flw_orders_create',
            'Create Order',
            'Seed flow for POST /orders',
            'POST',
            '/orders'
        ),
        (
            '55555555-5555-5555-5555-555555555553'::uuid,
            '66666666-6666-6666-6666-666666666663'::uuid,
            'flw_checkout',
            'Checkout',
            'Seed flow for POST /checkout',
            'POST',
            '/checkout'
        )
) AS f (id, active_flow_version_id, flow_key, name, description, http_method, path)
JOIN gateways g ON g.unique_key = 'a6n1y8'
JOIN app_users u ON u.username = 'admin'
ON CONFLICT (flow_key) DO NOTHING;

SELECT flow_key, http_method, path, active_flow_version_id, active_flow_version_status
FROM flows
ORDER BY flow_key;
EOF
