#!/bin/sh

set -eu

require_env() {
  name="$1"
  value="$(printenv "$name" 2>/dev/null || true)"
  if [ -z "$value" ]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

json_bool_field() {
  field_name="$1"
  response="$2"
  printf '%s' "$response" \
    | tr -d '\n' \
    | sed -n "s/.*\"${field_name}\"[[:space:]]*:[[:space:]]*\\(true\\|false\\).*/\\1/p"
}

json_string_field() {
  field_name="$1"
  response="$2"
  printf '%s' "$response" \
    | tr -d '\n' \
    | sed -n "s/.*\"${field_name}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p"
}

json_first_array_string() {
  field_name="$1"
  response="$2"
  printf '%s' "$response" \
    | tr -d '\n' \
    | sed -n "s/.*\"${field_name}\"[[:space:]]*:[[:space:]]*\\[[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p"
}

require_env BAO_ADDR
OPENBAO_STATE_DIR="${OPENBAO_STATE_DIR:-/openbao/bootstrap}"
ROOT_TOKEN_FILE="${OPENBAO_STATE_DIR}/root-token"
UNSEAL_KEY_FILE="${OPENBAO_STATE_DIR}/unseal-key"

mkdir -p "${OPENBAO_STATE_DIR}"

attempts=0
until curl -sS "$BAO_ADDR/v1/sys/init" >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 60 ]; then
    echo "OpenBao did not become ready in time" >&2
    exit 1
  fi
  sleep 2
done

init_status_json="$(curl -fsS "$BAO_ADDR/v1/sys/init")"
initialized="$(json_bool_field initialized "$init_status_json")"

if [ "$initialized" != "true" ]; then
  init_json="$(curl -fsS \
    -X PUT \
    -H "Content-Type: application/json" \
    --data '{"secret_shares":1,"secret_threshold":1}' \
    "$BAO_ADDR/v1/sys/init")"
  json_string_field root_token "$init_json" > "$ROOT_TOKEN_FILE"
  json_first_array_string keys_base64 "$init_json" > "$UNSEAL_KEY_FILE"
  chmod 600 "$ROOT_TOKEN_FILE" "$UNSEAL_KEY_FILE"
fi

if [ ! -f "$ROOT_TOKEN_FILE" ] || [ ! -f "$UNSEAL_KEY_FILE" ]; then
  echo "OpenBao bootstrap files are missing under $OPENBAO_STATE_DIR" >&2
  exit 1
fi

seal_status_json="$(curl -fsS "$BAO_ADDR/v1/sys/seal-status")"
seal_status="$(json_bool_field sealed "$seal_status_json")"
if [ "$seal_status" = "true" ]; then
  curl -fsS \
    -X PUT \
    -H "Content-Type: application/json" \
    --data "{\"key\":\"$(cat "$UNSEAL_KEY_FILE")\"}" \
    "$BAO_ADDR/v1/sys/unseal" >/dev/null
fi

export BAO_TOKEN="$(tr -d '\r\n' < "$ROOT_TOKEN_FILE")"

mounts_json="$(curl -fsS \
  -H "X-Vault-Token: $BAO_TOKEN" \
  "$BAO_ADDR/v1/sys/mounts")"
if ! printf '%s' "$mounts_json" | tr -d '\n' | grep -q '"secret/"'; then
  curl -fsS \
    -X POST \
    -H "X-Vault-Token: $BAO_TOKEN" \
    -H "Content-Type: application/json" \
    --data '{"type":"kv","options":{"version":"2"}}' \
    "$BAO_ADDR/v1/sys/mounts/secret" >/dev/null
fi

# Fall back to the same literal values docker-compose.dev.yml has always
# used when these aren't set, so local development needs no .env file at
# all. docker-compose.yml (production) sets all five as required variables
# (see x-app-secrets-env), so these fallbacks are never reached there.
DB_PASSWORD_VALUE="${DB_PASSWORD:-funchole}"
JWT_SECRET_VALUE="${JWT_SECRET:-ZGV2LXNlY3JldC1mb3ItZnVuY2hvbGUtYmFja2VuZC1jaGFuZ2UtbWUtYmVmb3JlLXByb2QteHl6MTIzNDU2Nzg5MDEyMw==}"
BOOTSTRAP_USERNAME_VALUE="${BOOTSTRAP_USERNAME:-admin}"
BOOTSTRAP_PASSWORD_VALUE="${BOOTSTRAP_PASSWORD:-admin12345}"
TENANT_DB_ADMIN_PASSWORD_VALUE="${TENANT_DB_ADMIN_PASSWORD:-tenant_admin}"

render_secret_file() {
  # "|" delimiter because JWT_SECRET/DB_PASSWORD are expected to be
  # base64/alnum values that may contain "/" but never "|".
  sed \
    -e "s|__DB_PASSWORD__|${DB_PASSWORD_VALUE}|g" \
    -e "s|__JWT_SECRET__|${JWT_SECRET_VALUE}|g" \
    -e "s|__BOOTSTRAP_USERNAME__|${BOOTSTRAP_USERNAME_VALUE}|g" \
    -e "s|__BOOTSTRAP_PASSWORD__|${BOOTSTRAP_PASSWORD_VALUE}|g" \
    -e "s|__TENANT_DB_ADMIN_PASSWORD__|${TENANT_DB_ADMIN_PASSWORD_VALUE}|g" \
    "$1"
}

find /secrets -type f -name '*.json' | sort | while read -r file; do
  relative_path="${file#/secrets/}"
  secret_path="${relative_path%.json}"

  render_secret_file "$file" | curl -fsS \
    -H "X-Vault-Token: $BAO_TOKEN" \
    -H "Content-Type: application/json" \
    -X POST \
    --data-binary @- \
    "$BAO_ADDR/v1/secret/data/$secret_path" >/dev/null
done

# Least-privilege tokens for each service, instead of every container
# holding the OpenBao root token. Each service's own secret documents/paths
# are exactly what its code actually reads/writes - see PRODUCTION_
# DEPLOYMENT_MISSING.md section 4 for the reasoning. Policies are rewritten
# (idempotent) on every run; tokens are freshly minted every run (not
# idempotent - OpenBao's token/create always returns a new token), which is
# fine here since each service only ever reads its token file at its own
# startup, right after this container finishes.
create_policy() {
  policy_name="$1"
  policy_hcl="$2"
  policy_json="$(printf '%s' "$policy_hcl" | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}')"

  curl -fsS \
    -X PUT \
    -H "X-Vault-Token: $BAO_TOKEN" \
    -H "Content-Type: application/json" \
    --data "{\"policy\":\"${policy_json}\"}" \
    "$BAO_ADDR/v1/sys/policies/acl/$policy_name" >/dev/null
}

create_scoped_token() {
  policy_name="$1"
  output_file="$2"

  token_json="$(curl -fsS \
    -X POST \
    -H "X-Vault-Token: $BAO_TOKEN" \
    -H "Content-Type: application/json" \
    --data "{\"policies\":[\"${policy_name}\"],\"no_default_policy\":true,\"renewable\":false,\"ttl\":\"87600h\"}" \
    "$BAO_ADDR/v1/auth/token/create")"

  json_string_field client_token "$token_json" > "$output_file"
  chmod 600 "$output_file"
}

create_policy "controlplane" '
path "secret/data/controlplane/app" { capabilities = ["read"] }
path "secret/data/certificates/*" { capabilities = ["create", "read", "update"] }
path "secret/data/acme/*" { capabilities = ["create", "read", "update"] }
path "secret/data/function-versions/*" { capabilities = ["create", "read", "update"] }
path "secret/data/environments/*" { capabilities = ["create", "read", "update"] }
path "secret/data/databases/*" { capabilities = ["create", "read", "update"] }
'
create_policy "gateway" '
path "secret/data/gateway/app" { capabilities = ["read"] }
path "secret/data/certificates/*" { capabilities = ["read"] }
'
create_policy "dispatcher" '
path "secret/data/function-versions/*" { capabilities = ["read"] }
path "secret/data/environments/*" { capabilities = ["read"] }
path "secret/data/databases/*" { capabilities = ["read"] }
'

create_scoped_token "controlplane" "${OPENBAO_STATE_DIR}/controlplane-token"
create_scoped_token "gateway" "${OPENBAO_STATE_DIR}/gateway-token"
create_scoped_token "dispatcher" "${OPENBAO_STATE_DIR}/dispatcher-token"

echo "OpenBao secrets initialized"
