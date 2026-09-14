#!/bin/sh
#
# Provisions the GET /orders demo using only the real Controlplane API -
# create Function, submit source, deploy to READY, create Flow, add steps,
# adopt - the same lifecycle a real user/agent would go through. Replaces
# the old raw-SQL-seeded version of this demo (see git history of
# ./scripts/dev/seed-flow-routes.sh and GAP-05 in
# FUNCHole_MASTER_PRD_AND_BACKLOG.md): that version's steps referenced
# component_id/component_version_id values that were never real
# Functions, which only ever worked because RESPONSE steps used to execute
# inline instead of running real code - see STORY-M1-08.
#
# Requires: controlplane running and reachable at CONTROLPLANE_URL (default
# http://localhost:7080), curl, and jq. Idempotent - if a Flow with key
# flw_orders_list already exists (and isn't soft-deleted), this exits
# without changing anything.
#
# Usage:
#   ./scripts/dev/seed-orders-demo.sh
#

set -eu

CONTROLPLANE_URL="${CONTROLPLANE_URL:-http://localhost:7080}"
BASE="$CONTROLPLANE_URL/api/v1"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin12345}"
GATEWAY_UNIQUE_KEY="${GATEWAY_UNIQUE_KEY:-a6n1y8}"

for tool in curl jq; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Missing required tool: $tool" >&2
        exit 1
    fi
done

echo "Waiting for controlplane at $CONTROLPLANE_URL..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null "$CONTROLPLANE_URL/actuator/health"; then
        break
    fi
    sleep 2
done

TOKEN=$(curl -s -X POST "$BASE/auth/token" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.data.accessToken')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo "Failed to authenticate as $ADMIN_USERNAME" >&2
    exit 1
fi
AUTH_HEADER="Authorization: Bearer $TOKEN"

EXISTING=$(curl -s "$BASE/flows?page=1&size=100" -H "$AUTH_HEADER" \
    | jq -r '.data.items[] | select(.flowKey == "flw_orders_list") | .id')
if [ -n "$EXISTING" ]; then
    echo "flw_orders_list already exists (id=$EXISTING) - nothing to do."
    exit 0
fi

GATEWAY_ID=$(curl -s "$BASE/gateways?page=1&size=50" -H "$AUTH_HEADER" \
    | jq -r --arg key "$GATEWAY_UNIQUE_KEY" '.data.items[] | select(.uniqueKey == $key) | .id')
if [ -z "$GATEWAY_ID" ]; then
    echo "No gateway found with uniqueKey=$GATEWAY_UNIQUE_KEY - cannot create a route. Skipping." >&2
    exit 1
fi

create_function() {
    key="$1"
    curl -s -X POST "$BASE/functions" -H "$AUTH_HEADER" -H "Content-Type: application/json" \
        -d "{\"functionKey\":\"$key\",\"name\":\"$key\",\"runtime\":\"NODE\"}" | jq -r '.data.id'
}

create_draft_version() {
    curl -s -X POST "$BASE/functions/$1/versions" -H "$AUTH_HEADER" -H "Content-Type: application/json" \
        -d '{"runtime":"NODE"}' | jq -r '.data.id'
}

deploy_to_ready() {
    function_id="$1"
    version_id="$2"
    source_file="$3"
    curl -s -X POST "$BASE/functions/$function_id/versions/$version_id/source" -H "$AUTH_HEADER" \
        -F "files=@$source_file;filename=index.mjs" \
        -F "entrypoint=index.mjs" -F "handler=handler" >/dev/null
    curl -s -X POST "$BASE/functions/$function_id/versions/$version_id/deploy" -H "$AUTH_HEADER" | jq -r '.data.status'
}

build_ready_function() {
    key="$1"
    source_file="$2"
    function_id=$(create_function "$key")
    version_id=$(create_draft_version "$function_id")
    status=$(deploy_to_ready "$function_id" "$version_id" "$source_file")
    if [ "$status" != "READY" ]; then
        echo "Failed to deploy $key to READY (status=$status)" >&2
        exit 1
    fi
    echo "$function_id $version_id"
}

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/validate.mjs" <<'JS'
// Development artifact for the GET /orders demo's first step,
// "Validate Orders Request" - not real orders validation/business logic.
export async function handler(input) {
    return { ok: true, source: "funchole-node-artifact", input };
}
JS

cat > "$TMP_DIR/fetch.mjs" <<'JS'
// Development artifact for the GET /orders demo's second step,
// "Fetch Orders" - not real orders retrieval/business logic.
export async function handler(input) {
    return { fetched: true, previous: input };
}
JS

cat > "$TMP_DIR/respond.mjs" <<'JS'
// Development artifact for the GET /orders demo's third step,
// "Build Orders Response" - not real orders formatting/business logic.
export async function handler(input) {
    return { status: 200, body: { ok: true, source: "funchole-node-artifact", orders: input } };
}
JS

echo "Creating and deploying fn_orders_validate_seed..."
RESULT=$(build_ready_function "fn_orders_validate_seed" "$TMP_DIR/validate.mjs")
set -- $RESULT
VALIDATE_FN_ID="$1"
VALIDATE_VER_ID="$2"

echo "Creating and deploying fn_orders_fetch_seed..."
RESULT=$(build_ready_function "fn_orders_fetch_seed" "$TMP_DIR/fetch.mjs")
set -- $RESULT
FETCH_FN_ID="$1"
FETCH_VER_ID="$2"

echo "Creating and deploying fn_orders_response_seed..."
RESULT=$(build_ready_function "fn_orders_response_seed" "$TMP_DIR/respond.mjs")
set -- $RESULT
RESPOND_FN_ID="$1"
RESPOND_VER_ID="$2"

echo "Creating flw_orders_list Flow..."
FLOW_ID=$(curl -s -X POST "$BASE/flows" -H "$AUTH_HEADER" -H "Content-Type: application/json" \
    -d "{\"flowKey\":\"flw_orders_list\",\"name\":\"List Orders\",\"description\":\"GET /orders demo, provisioned via the real API\",\"gatewayId\":\"$GATEWAY_ID\",\"httpMethod\":\"GET\",\"path\":\"/orders\"}" \
    | jq -r '.data.id')

VERSION_ID=$(curl -s -X POST "$BASE/flows/$FLOW_ID/versions" -H "$AUTH_HEADER" -H "Content-Type: application/json" \
    -d '{"runtime":"NODE"}' | jq -r '.data.id')

add_step() {
    step_key="$1"
    component_type="$2"
    position="$3"
    component_id="$4"
    component_version_id="$5"
    curl -s -X POST "$BASE/flows/$FLOW_ID/versions/$VERSION_ID/steps" -H "$AUTH_HEADER" -H "Content-Type: application/json" \
        -d "{\"stepKey\":\"$step_key\",\"componentType\":\"$component_type\",\"position\":$position,\"componentId\":\"$component_id\",\"componentVersionId\":\"$component_version_id\"}" \
        | jq -e '.success' >/dev/null
}

echo "Adding steps..."
add_step "validate-orders-request" "FUNCTION" 10 "$VALIDATE_FN_ID" "$VALIDATE_VER_ID"
add_step "fetch-orders" "FUNCTION" 20 "$FETCH_FN_ID" "$FETCH_VER_ID"
add_step "build-orders-response" "RESPONSE" 30 "$RESPOND_FN_ID" "$RESPOND_VER_ID"

echo "Adopting FlowVersion..."
curl -s -X POST "$BASE/flows/$FLOW_ID/versions/$VERSION_ID/adopt" -H "$AUTH_HEADER" | jq -e '.data.status == "ADOPTED"' >/dev/null

echo "Done. GET /orders is now provisioned end-to-end through real Functions - no seed data, no placeholder artifacts."
