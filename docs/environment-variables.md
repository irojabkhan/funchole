# Environment Variable Reference

Every configuration knob the four runtime services (`controlplane`, `gateway`,
`dispatcher`, `runtime`) read, plus the separate layer of variables Docker
Compose itself substitutes into `docker-compose.yml` before any container
starts. These are two different mechanisms - see [How values actually reach
each service](#how-values-actually-reach-each-service) below if a variable
you set doesn't seem to take effect.

## What you actually need to set for a first production boot

Everything else on this page has a safe default. These do not - `docker
compose -f docker-compose.yml up` refuses to start until they're set, via a
`.env` file at the repo root (see `.env.example`).

| Variable | Required for | Notes |
| --- | --- | --- |
| `S3_ARTIFACT_ACCESS_KEY` | RustFS + controlplane/gateway/runtime | generate a random value, don't reuse the example |
| `S3_ARTIFACT_SECRET_KEY` | RustFS + controlplane/gateway/runtime | same |
| `DB_PASSWORD` | Postgres + every service that talks to it | `openssl rand -hex 32` |
| `JWT_SECRET` | controlplane (via OpenBao) | must be base64 - `openssl rand -base64 48` |
| `BOOTSTRAP_USERNAME` | controlplane (via OpenBao) | leave as `admin` - renaming isn't wired up yet |
| `BOOTSTRAP_PASSWORD` | controlplane (via OpenBao) | `openssl rand -base64 24`; rotates the seeded admin account's password on first boot (see `AdminBootstrapRunner`) |

Optional, safe defaults if left unset: `S3_ARTIFACT_BUCKET` (`funchole-artifacts`),
`S3_ARTIFACT_REGION` (`us-east-1`), `CERTIFICATE_PROVIDER` (`SELF_SIGNED` - set to
`LETS_ENCRYPT` once a real domain's DNS already points at this host).

## controlplane

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/funchole` | Postgres JDBC URL |
| `DB_USERNAME` | `funchole` | DB user |
| `DB_PASSWORD` | `funchole` | DB password |
| `APP_VERSION` | `0.1.0-SNAPSHOT` | reported app/MCP server version |
| `SERVER_PORT` | `7080` | HTTP listen port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | allowed CORS origins |
| `NATS_URL` | `nats://localhost:4222` | NATS broker URL |
| `SOURCE_STORAGE_ROOT` | `/tmp/funchole-sources` | on-disk Function source storage root - mount a persistent volume here in production (already done in `docker-compose.yml`) |
| `S3_ARTIFACT_ENDPOINT` | `http://localhost:9000` | S3-compatible artifact store endpoint |
| `S3_ARTIFACT_BUCKET` | `funchole-artifacts` | artifact bucket name |
| `S3_ARTIFACT_ACCESS_KEY` | `funchole` | S3 access key |
| `S3_ARTIFACT_SECRET_KEY` | `funchole-secret` | S3 secret key |
| `S3_ARTIFACT_REGION` | `us-east-1` | S3 region |
| `S3_ARTIFACT_PATH_STYLE_ACCESS` | `true` | use path-style S3 addressing |
| `CERTIFICATE_PROVIDER` | `SELF_SIGNED` | `SELF_SIGNED` or `LETS_ENCRYPT` |
| `CERTIFICATE_SELF_SIGNED_VALIDITY_DAYS` | `30` | self-signed cert validity window |
| `CERTIFICATE_RETRY_DELAY_MS` | `60000` | retry delay on cert issuance failure; also the interval for the renewal/reconciliation scheduled jobs |
| `CERTIFICATE_RENEWAL_WINDOW_DAYS` | `10` | days-before-expiry a certificate gets renewed - deliberately kept below `CERTIFICATE_SELF_SIGNED_VALIDITY_DAYS`'s default so self-signed certs aren't perpetually "due" |
| `CERTIFICATE_ACME_SERVER_URI` | `https://acme-v02.api.letsencrypt.org/directory` | ACME directory URL - point at a staging/test server (e.g. Pebble) to test issuance without touching Let's Encrypt's real rate limits |
| `CERTIFICATE_ACME_POLL_INTERVAL_SECONDS` | `3` | ACME order/challenge status poll interval |
| `CERTIFICATE_ACME_MAX_POLL_ATTEMPTS` | `20` | max ACME poll attempts before giving up |
| `JWT_SECRET` | a known dev value (**must be overridden in production** - see above) | JWT signing secret, must be base64 |
| `JWT_EXPIRATION_SECONDS` | `3600` | JWT token lifetime |
| `BOOTSTRAP_USERNAME` | `admin` | initial admin username (renaming not wired up - see `AdminBootstrapRunner`) |
| `BOOTSTRAP_PASSWORD` | `admin12345` | if changed from this default, rotates the seeded admin account's password on next boot |
| `BAO_ADDR` | `http://localhost:8200` | OpenBao address |
| `BAO_TOKEN` | `root` | OpenBao token - in `docker-compose.yml` this arrives via `BAO_TOKEN_FILE`, not this variable directly (see below) |

## gateway

| Variable | Default | Purpose |
| --- | --- | --- |
| `GATEWAY_PORT` | `443` | HTTPS listen port |
| `ACME_HTTP01_PORT` | `80` | plain-HTTP port for serving Let's Encrypt's HTTP-01 challenge responses; a bind failure here is logged, not fatal - HTTPS traffic on `GATEWAY_PORT` is unaffected |
| `NATS_URL` | `nats://localhost:4222` | NATS broker URL |
| `BAO_ADDR` | `http://localhost:8200` | OpenBao address |
| `BAO_TOKEN` | `""` (empty) | OpenBao token - same `BAO_TOKEN_FILE` indirection as controlplane in `docker-compose.yml` |
| `GATEWAY_INVOCATION_TIMEOUT_MS` | `15000` | how long the gateway waits for a triggered invocation to complete before timing out the HTTP response |
| `GATEWAY_STATIC_SITE_CACHE_DIR` | `/tmp/funchole/gateway-static-site-cache` | local disk cache for STATIC-runtime site files fetched from S3 |
| `GATEWAY_REGISTRY_LOAD_ATTEMPTS` | `30` | retry attempts for the initial gateway-registry load at startup |
| `GATEWAY_REGISTRY_LOAD_DELAY_MS` | `2000` | delay between those retry attempts |
| `GATEWAY_REGISTRY_POLL_INTERVAL_SECONDS` | `5` | interval for the background registry refresh (picks up newly adopted Flows/certificates without a restart) |
| `S3_ARTIFACT_ENDPOINT` | **required, no default** | fetches STATIC-runtime artifacts from the same store Functions are published to |
| `S3_ARTIFACT_BUCKET` | **required, no default** | |
| `S3_ARTIFACT_ACCESS_KEY` | **required, no default** | |
| `S3_ARTIFACT_SECRET_KEY` | **required, no default** | |
| `S3_ARTIFACT_REGION` | `us-east-1` | |
| `S3_ARTIFACT_PATH_STYLE_ACCESS` | `true` | |
| `DB_URL` | `jdbc:postgresql://localhost:5432/funchole` | Postgres JDBC URL |
| `DB_USERNAME` | `funchole` | DB user |
| `DB_PASSWORD` | `funchole` | DB password |

## dispatcher

| Variable | Default | Purpose |
| --- | --- | --- |
| `NATS_URL` | `nats://localhost:4222` | NATS broker URL |
| `RUNTIME_IPC_ACCEPT_TIMEOUT_MS` | `3000` | timeout accepting a runtime worker's IPC connection |
| `BAO_ADDR` | `http://localhost:8200` | OpenBao address |
| `BAO_TOKEN` | `root` | OpenBao token - same `BAO_TOKEN_FILE` indirection as the other services |
| `DISPATCHER_POLL_TIMEOUT_MS` | `1000` | poll timeout for the main dispatch loop |
| `RUNTIME_REGISTRY_TYPE` | `jdbc` | `memory` selects an in-process-only registry (single dispatcher, loses state on restart); anything else uses the Postgres-backed one (multi-dispatcher safe) |
| `DEV_RUNTIME_INSTANCE_ID` | `runtime-node-dev-1` | **not dev-only despite the name** - this is the dispatcher's only mechanism for registering a runtime worker. Must match the actual worker's `RUNTIME_INSTANCE_ID` |
| `DEV_RUNTIME_TYPE` | `NODE` | must match the worker's `RUNTIME_TYPE` |
| `DEV_RUNTIME_CAPACITY` | `4` | concurrent invocation capacity of that runtime instance |
| `DEV_RUNTIME_SOCKET_PATH` | `/tmp/funchole/runtime-node-dev-1.sock` | must match the worker's `RUNTIME_WORKER_SOCKET_PATH`, and both containers must share a volume mounted at that path |
| `DB_URL` | `jdbc:postgresql://localhost:5432/funchole` | Postgres JDBC URL |
| `DB_USERNAME` | `funchole` | DB user |
| `DB_PASSWORD` | `funchole` | DB password |

## runtime (the Node execution worker)

| Variable | Default | Purpose |
| --- | --- | --- |
| `RUNTIME_WORKER_SOCKET_PATH` | `/tmp/funchole/runtime-node-dev-1.sock` | Unix socket this worker binds for dispatcher IPC |
| `RUNTIME_INSTANCE_ID` | `runtime-node-dev-1` | this worker's instance ID - must match dispatcher's `DEV_RUNTIME_INSTANCE_ID` |
| `RUNTIME_TYPE` | `NODE` | this worker's runtime type |
| `ARTIFACT_DIR` | `artifacts/dev` | artifact root when `ARTIFACT_STORE_TYPE=local` |
| `ARTIFACT_CACHE_DIR` | `/tmp/funchole/artifact-cache` | local cache dir when `ARTIFACT_STORE_TYPE=s3` |
| `ARTIFACT_STORE_TYPE` | `local` | `s3` or `local` - production uses `s3` |
| `NODE_COMMAND` | `node` | command used to launch the persistent Node executor process |
| `NODE_EXECUTOR_SCRIPT_PATH` | `node/executor.mjs` | path to the Node executor script (baked into the `runtime-worker` image as `/app/node/executor.mjs`) |
| `S3_ARTIFACT_ENDPOINT` | **required when `ARTIFACT_STORE_TYPE=s3`** | |
| `S3_ARTIFACT_BUCKET` | **required when `ARTIFACT_STORE_TYPE=s3`** | |
| `S3_ARTIFACT_ACCESS_KEY` | **required when `ARTIFACT_STORE_TYPE=s3`** | |
| `S3_ARTIFACT_SECRET_KEY` | **required when `ARTIFACT_STORE_TYPE=s3`** | |
| `S3_ARTIFACT_REGION` | `us-east-1` | |
| `S3_ARTIFACT_PATH_STYLE_ACCESS` | `true` | |

## How values actually reach each service

Two separate layers are involved, and it's easy to set a value in the wrong one:

1. **Docker Compose variable substitution** - `${VAR}` references inside
   `docker-compose.yml` itself, resolved from your `.env` file (or the shell
   environment) *before* any container starts. This is what `.env.example`
   documents, and it's the layer with `${VAR:?error message}` (required) and
   `${VAR:-default}` (optional) syntax.
2. **Container-level environment variables** - what the Java process inside
   each container actually reads via `System.getenv`/Spring `${VAR:default}`
   placeholders. Most of these are set directly by `docker-compose.yml`'s
   `environment:` blocks, but three of them go through an extra hop:

   - `controlplane` and `gateway` don't receive `DB_PASSWORD`, `JWT_SECRET`,
     `BOOTSTRAP_USERNAME`, or `BOOTSTRAP_PASSWORD` directly from Compose.
     Instead, `docker/openbao-init/init-openbao.sh` renders them into secret
     documents stored in OpenBao, and each service's entrypoint script
     (`docker/controlplane-entrypoint.sh`, `docker/gateway-entrypoint.sh`)
     calls `export_secret_document` (in `docker/openbao-common.sh`) to fetch
     and `export` those values as real environment variables *before*
     launching the JVM. `dispatcher` is simpler - `DB_PASSWORD` is set
     directly by Compose for it.
   - `BAO_TOKEN_FILE` (e.g. `/openbao/bootstrap/controlplane-token`) is set
     by Compose, but no Java code reads it - only `BAO_TOKEN` is. The
     entrypoint script's `load_bao_token()` reads the file named by
     `BAO_TOKEN_FILE` and exports its contents as `BAO_TOKEN`, which is what
     the JVM actually sees. Each service gets its own least-privilege token
     file (not a shared root token) - see `PRODUCTION_DEPLOYMENT_MISSING.md`
     section 4 for why.

If you're overriding a value and it doesn't seem to take effect, check which
layer it actually belongs to first.
