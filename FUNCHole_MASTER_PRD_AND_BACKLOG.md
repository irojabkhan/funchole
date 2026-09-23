# FuncHole — Master Product Requirements Document & Traceable Backlog

**Document status:** Living PRD  
**Baseline date:** 2026-09-13  
**Audit source:** `CURRENT_PROJECT_STATE(1).md`  
**Backlog coverage:** F001–F350, plus F351–F356 added during live product-gap work (all master-backlog candidates are represented)
**Primary product goal:** a human **and** a coding agent can take FuncHole from an empty system to a real running HTTP-backed Function/Flow lifecycle without seed scripts, manual SQL, manual S3/file placement, or internal Java calls.

---

## 1. Why this PRD exists

The current repository already contains substantial execution infrastructure, but the audit shows that product completeness and internal implementation completeness are different things.

The most important current-state finding is that FuncHole has two strong but disconnected halves:

1. **Flow/Gateway/Dispatcher/Runtime** is transport-reachable and can serve real HTTP requests.
2. **Function/Source/Build/Deploy/Artifact/Direct Invocation** is largely implemented and tested internally, but is not reachable by a real user.

The flagship demo currently bridges that gap with seeded SQL and prebuilt artifacts instead of the real product lifecycle.

This PRD converts the audit and the F001–F350 (+F351–F356) master backlog into one traceable plan with:
- epics;
- user stories;
- tasks;
- sub-tasks;
- current status;
- priorities;
- milestone assignment;
- acceptance criteria;
- progress scoring;
- explicit dependencies;
- a complete F001–F350 (+F351–F356) tracking matrix.

---

## 2. Product definition

### 2.1 Controlplane

`controlplane` is the user/agent-facing command plane.

Future entry points are peer adapters over the same application capabilities:

```text
Web / REST ─┐
CLI        ─┼──> Controlplane application use-cases
MCP        ─┘
```

Controlplane owns developer-facing lifecycle commands and orchestration for:
- Function;
- FunctionVersion;
- source;
- build/deploy;
- Flow composition;
- Gateway/route configuration;
- direct FunctionVersion test invocation;
- developer-facing inspection APIs.

Execution subsystems remain behind explicit boundaries.

### 2.2 Execution path

```text
Production:
Client → Gateway → Flow Resolution → Invocation → NATS/JetStream
      → Dispatcher → Runtime Registry → IPC → Runtime Worker → Artifact

Developer/Test:
Human/Agent → Controlplane → exact READY FunctionVersion
            → invocation-contract → Invocation → Dispatcher → Runtime
```

### 2.3 Golden first milestone

A fresh installation with an empty database must support:

```text
Create Function
→ Create FunctionVersion
→ Submit Source
→ Build + Deploy
→ READY
→ Create/Edit FlowVersion
→ Assign exact READY FunctionVersion to FUNCTION step
→ Add RESPONSE step
→ Adopt FlowVersion
→ Configure Gateway route
→ Send HTTP request
→ Execute real built artifact
→ Return HTTP response
→ Inspect Invocation
```

**No seed script. No manual SQL. No manual filesystem/S3 artifact. No direct internal Java call.**

---

## 3. Progress scoring

This document uses a feature-level implementation score:

| Status | Score | Meaning |
|---|---:|---|
| COMPLETE | 100% | Real user-facing or fully usable product capability exists |
| INTERNAL_ONLY | 60% | Core implementation exists and is tested, but lacks a usable external product path |
| PARTIAL | 50% | Some required pieces exist; product behavior is incomplete |
| SEEDED_ONLY | 25% | Demonstrated only via seed/bootstrap data |
| TEST_ONLY | 25% | Exists only in tests |
| DECISION_REQUIRED | 10% | Product/architecture decision is explicitly unresolved |
| MISSING / PLANNED / DEFERRED | 0% | Not implemented |

> This is a **planning/tracking score**, not an engineering-effort estimate. A 60% INTERNAL_ONLY item can still need substantial product wiring, while a 0% item may be small.

### 3.1 Current baseline

**Last recalculated:** 2026-09-20, after auditing the last few days of shipped work: shared Flow configuration, managed database inheritance, MCP API parity, STATIC-runtime frontend deployment, path-parameter gateway routes, runtime validation/RESPONSE contract documentation, and `get_flow_full_source`.

- **Master backlog completion:** **52.8%** (28.7% → 30.4% → 31.4% → 34.1% → 36.0% → 37.0% → 37.1% → 37.4% → 37.8% → 38.1% → 38.7% → 39.7% → 40.4% → 40.8% → 43.4% → 44.4% → 45.0% → 45.2% → 52.8%)
- **First human zero-to-running lifecycle scope:** **62.8%**
- **MVP/product-oriented scope:** **51.6%**
- **Agent/MCP scope:** **92.6%**
- **Master backlog remaining:** **47.2%**

Status count across all 356 features (350 original + F351–F356 added during live product-gap work):

- **COMPLETE:** 157
- **INTERNAL_ONLY:** 6
- **PARTIAL:** 55
- **DECISION_REQUIRED:** 6
- **MISSING:** 132

> **Note on this correction (2026-09-16):** The STORY-M2-02 PRD update marked F180 (Test invoke panel) and F173–F178 COMPLETE and left F181–F190 (Flow/Gateway/domain UI) at their old MISSING/0% status with a note calling them "not audited, out of scope." The user then hit two real problems live: Test invoke creates an invocation that never executes (it stays PENDING forever - the direct-invoke path was never wired to the Dispatcher, only the Gateway's HTTP-triggered path was), and the Flow step editor has no Function/FunctionVersion picker, just raw UUID text boxes. Auditing in response found a third, unrelated gap: the Function workspace has no UI at all for environment variables/secrets, despite that backend being 100% done. The audit also found the "not audited, out of scope" claim itself was wrong in the other direction - F181 (Flow list), F183 (visual step canvas) and F186 (Gateway+Domain CRUD) were already substantially built pre-session and are now confirmed COMPLETE; F182/F187 are PARTIAL. See GAP-17, GAP-18, GAP-19 for full detail. Net effect: F180 downgraded (COMPLETE → PARTIAL), F181/F186 upgraded to COMPLETE, F182/F183/F187 upgraded to PARTIAL, F184/F185/F188/F189/F190 confirmed still MISSING.
>
> **Progress update (2026-09-20):** The last few days moved the roadmap materially, especially for the agent path. The MCP server now exists with real tool coverage for functions, versions, source, deployment, flows, flow versions, steps, gateways, domains, invocations, managed databases, environment profiles and Flow-level configuration; it is no longer a missing epic. New backlog items were added for the shipped capabilities that did not have F-IDs yet: Flow-shared environment profiles (F353), Flow-shared database attachments (F354), STATIC-runtime frontend deployments (F355), and path-parameter Gateway routes (F356). Remaining uncertainty is now narrower: MCP-specific token provisioning/scopes, persisted build logs, CLI, production hardening, and richer Web inspection screens.

### 3.2 Important interpretation

The overall percentage is intentionally lower than the apparent maturity of the runtime engine because this backlog includes:
- Web UI;
- MCP;
- CLI;
- production security/reliability;
- additional runtimes;
- activation/traffic management;
- Git integration;
- ecosystem/commercial capabilities.

The execution core is significantly more mature than the end-user product lifecycle.

---

## 4. Current audited reality

### Already transport-complete
- Authentication/JWT.
- Function CRUD (create/get/list/update/archive) — **shipped 2026-09-13, commit `b0f7fc6`**.
- FunctionVersion create/get/list (DRAFT only; status transitions remain internal-only) — **shipped 2026-09-14**.
- FunctionVersion source submission/replacement/read-back (zip via multipart) — **shipped 2026-09-14**.
- FunctionVersion build/deploy to READY (`POST .../deploy`) — **shipped 2026-09-14**.
- JSON/generated-file source submission through MCP.
- Domain/Gateway/Certificate lifecycle.
- Flow, FlowVersion and FlowStep CRUD/lifecycle.
- Live exact-path and path-parameter Gateway routing.
- Flow-level shared environment profiles and database attachments.
- Flow Invocation creation.
- JetStream dispatch.
- Dispatcher execution.
- IPC Runtime handoff.
- Persistent Node executor.
- Artifact retrieval/cache.
- STATIC-runtime frontend artifact deployment and Gateway static serving.
- HTTP response return.

### Missing or disconnected
- MCP-specific token/API-key provisioning and fine-grained scopes.
- Persisted/queryable build logs.
- Dedicated Web invocation history/log/runtime-health screens.
- CLI.
- Worker-owned runtime registration and heartbeat.
- Full restart/reconciliation hardening for orphaned runtime reservations and stuck invocations.

---

## 5. Critical gaps

| Gap | Finding | Why it matters | Backlog |
|---|---|---|---|
| GAP-01 | ~~Function lifecycle has entity/repository scaffolding but no FunctionService or transport.~~ **RESOLVED 2026-09-13 (`b0f7fc6`)** — `FunctionService`/`FunctionController` now live. | Blocks zero-to-product lifecycle. | F003–F009 |
| GAP-02 | ~~FunctionVersion creation/read/list is not productized~~ **RESOLVED 2026-09-14** — create/get/list (F010–F013), source submission (F159), and build/deploy (F160) all now have real transports. F014–F018 (lifecycle policy decisions) remain the only open piece. | Blocks source/build/deploy from real users. | F014–F018 |
| GAP-03 | ~~Canonical source package is undefined; no ZIP/tar/multipart/CLI/MCP source ingestion exists.~~ **PARTIALLY RESOLVED 2026-09-14** — format decided (ZIP via multipart) and REST ingestion now live; CLI (F026, M4) and MCP (F025, M3) ingestion remain unbuilt by design (not yet in scope). | Blocks stable REST/CLI/MCP contract. | F025–F026 |
| GAP-04 | ~~Flow FUNCTION steps accept arbitrary UUIDs and do not validate Function/FunctionVersion existence or READY state.~~ **RESOLVED 2026-09-14 (STORY-M1-06)** — `FlowStepService` now validates FUNCTION/RESPONSE/MIDDLEWARE steps against a real, owned, READY `FunctionVersion`, and SUB_FLOW steps against a real, owned, ADOPTED `FlowVersion`. RESPONSE also became genuinely executable (its function's own code now runs on the Runtime Worker to shape `{status, body}`, replacing the old inline `metadata`-driven synthesis), MIDDLEWARE dispatches identically to FUNCTION (label-only for now), and SUB_FLOW is resolved by flattening the referenced flow's steps into the parent's flat step sequence at snapshot-build time (`JdbcInvocationRegistry`) — the Dispatcher, `ExecutionPlanner`, and `invocation_step_executions` schema needed zero changes. Live-verified end-to-end against the real Dispatcher/Runtime Worker (FUNCTION→RESPONSE chaining, and a parent Flow with a SUB_FLOW step whose child flow's step actually executed). | The two working halves of the platform are disconnected. | F066–F068 |
| GAP-05 | ~~The working /orders demo depends on raw SQL seeded Flow data and prebuilt seeded S3 artifacts.~~ **RESOLVED 2026-09-14 (STORY-M1-08)** — surfaced concretely first (GAP-04's fix made RESPONSE genuinely executable and the demo's RESPONSE step turned out to reference a placeholder that was never a real Function, fixed as a stopgap by pointing it at one real Function created through the API), then fully resolved: `scripts/dev/seed-orders-demo.sh` now provisions GET /orders entirely through the real Controlplane API - 3 real Functions, each with real source submitted and deployed to READY, a real Flow with 3 steps referencing them, adopted - with zero raw SQL. `scripts/dev/seed-flow-routes.sh` no longer seeds `flw_orders_list` at all. Live-verified: hard-deleted the old raw-SQL demo data, ran the new script, confirmed `/orders` still returns 200 with the same shape, confirmed the script is idempotent. | Demo does not prove the real Function→Build→Flow lifecycle. | F107, F307 |
| GAP-06 | ~~FunctionVersionDeploymentService and FunctionVersionSourceService are not fully wired as live application beans/transports.~~ **RESOLVED 2026-09-14** — both now have real REST transports (`FunctionVersionSourceController`, `FunctionVersionDeploymentController`); `FunctionVersionDeploymentService` is now a live `@Service` bean, backed by a new `ArtifactPublisherConfig` supplying the `ArtifactPublisher` bean it was missing. Verified against the real RustFS, not a fake. | Implemented internals remain unreachable. | F156 |
| GAP-07 | ~~Direct invocation and invocation inspection exist internally but have no external transport.~~ **RESOLVED 2026-09-15** — invocation inspection (F118/F120, STORY-M1-09) has `GET /api/v1/invocations/{invocationId}` (durable status/result/error/identity plus full step-level detail, ownership-checked transitively through the owning Flow or FunctionVersion, via a new `InvocationInspectionHandoff` contract crossing the `invocation-contract`/`dispatcher` boundary). Direct FunctionVersion invocation (F116/F117) now also has a real transport - `POST /api/v1/functions/{functionId}/versions/{versionId}/invoke`, built while wiring STORY-M2-02's "Test invoke" panel, wrapping the already-tested `FunctionVersionInvocationService` - live-verified end-to-end through the Function workspace UI. Only F119 (list/filter invocations) stays MISSING - no story has needed it yet. | Blocks test/debug UX. | F116–F120, F167 |
| GAP-08 | ~~Unsupported initial component can redeliver forever with Invocation stuck PENDING.~~ **RESOLVED 2026-09-15** — Flow adoption now prevents unsupported/non-terminating executable plans from becoming invocable: step component references are re-validated at adoption, positions must be strictly ascending, and the final step must be terminal (`RESPONSE` or a previously adopted/validated `SUB_FLOW`). Unsupported component types are rejected before dispatch, so the original poison ready-event loop is no longer reachable from adopted Flows. Retry/backoff and broader crash recovery remain separate open work (F133/F134). | Reliability defect. | F132 complete; F133–F134 remain open |
| GAP-09 | ~~Runtime Registry is static/in-memory and loses capacity state across Dispatcher restarts.~~ **RESOLVED 2026-09-15** — `JdbcRuntimeRegistry` now persists runtime registration/capacity state in PostgreSQL (`runtime_instances`) and Dispatcher uses it by default, with row-level locking (`FOR UPDATE SKIP LOCKED`) for multi-Dispatcher reservation coordination. The old `InMemoryRuntimeRegistry` remains intact as a baseline/opt-in mode. Worker self-registration, heartbeat expiry, distributed leases, orphan reservation reconciliation, and full dispatcher restart replay remain future hardening work. | Production scaling/recovery gap. | F139 complete; F135/F136 partial; F264 partial |
| GAP-10 | ~~No MCP server, CLI, or agent-facing lifecycle exists.~~ **PARTIALLY RESOLVED 2026-09-20** — the MCP server now exists and exposes the same major lifecycle surfaces as REST/API: Functions, FunctionVersions, source submit/read, deploy, Flow/FlowVersion/step management, Gateway/Domain management, direct FunctionVersion invocation, Flow test invocation, Invocation inspection, environment profiles, databases, and Flow-level config. CLI remains unbuilt, and MCP-specific token provisioning/fine-grained permission scopes remain open. | Agent-first lifecycle is now viable through MCP, but not yet hardened or available as a CLI workflow. | F191–F229 |
| GAP-11 | ~~No Controlplane Web UI exists.~~ **PARTIALLY RESOLVED 2026-09-15 (STORY-M2-02), audited and further resolved 2026-09-16** — the Web shell (F173: auth/session/nav) turned out to already be built and working; the Function workspace (F174–F180, F351) is now built and live-verified end-to-end, including a working Test invoke (GAP-17) and an environment/secrets panel (GAP-18). F179 (build log viewer) is only PARTIAL - deploy failures show their build error transiently in the current session, but nothing is persisted/queryable later, matching the backend's own F044/F045 gap. The Flow list (F181), Flow/step editor (F182, now with a real component picker - GAP-19) and Gateway/Domain CRUD (F186) were found already substantially built pre-session and are now COMPLETE; the visual step canvas (F183) is PARTIAL (core rendering confirmed, richer graph-editing capabilities not verified). Still genuinely MISSING: F185 (input-mapping UI), F188/F189/F190 (invocation history, logs, runtime health screens). F187 (route config) is only PARTIAL - set at Flow-creation time, no dedicated screen verified. | Blocks non-API human product experience. | F173–F190, F351 |
| GAP-17 | ~~Direct FunctionVersion invocations (the "Test invoke" panel shipped in STORY-M2-02, F180) durably create an Invocation row and return it to the UI, but never actually execute.~~ **RESOLVED 2026-09-16** — two real defects, found and fixed together, live-verified end-to-end. (1) `InvocationHandoffConfig` (invocation module) wired the direct-invoke path with a `NoopInvocationEventPublisher`; now wires a real `NatsJetStreamInvocationEventPublisher`, lazily (`@Lazy` Spring beans) so the NATS connection is only opened the first time a direct invocation is actually created, not at every Spring context startup — keeps the rest of controlplane's test suite untouched. (2) Even after that fix, the Dispatcher's own `InvocationDispatcher.onStepTerminal()` only marked an Invocation COMPLETED when its terminal step was RESPONSE-typed; a direct invocation's single synthetic step is FUNCTION-typed (`JdbcInvocationRegistry.directInvocationSnapshot()`), so the step executed and completed but the parent Invocation stayed PENDING forever regardless — a second, previously-undiscovered layer of the same bug. Fixed by also treating any completed step of a `DIRECT_FUNCTION`-kind invocation as terminal. Verified live: Test invoke now shows `COMPLETED` with the function's real result within ~1s, auto-polled in the UI. | The one feature whose entire purpose is "see your function run" cannot demonstrate a run. Misleading as shipped. | F116, F117, F180 |
| GAP-18 | ~~Function/FunctionVersion environment variables and secrets (F238–F243) are fully implemented end-to-end on the backend but have zero Controlplane Web UI.~~ **RESOLVED 2026-09-16** — added `FunctionVersionConfigResponse`/env/secret types, three `api.ts` client methods, and a "Environment & secrets" panel on the FunctionVersion detail page (two lists - env vars show their value, secrets show only their opaque `secretRef`, never a plaintext value - each with an inline add/update form using the existing `PUT .../config/env/{key}` / `.../config/secrets/{key}` upsert endpoints). No delete UI, matching the backend (upsert-only, no DELETE mapping exists). Live-verified: added a real env var and a real secret, confirmed the secret list shows only its `secretRef`, never the value. Tracked as new feature **F351** (this backlog had no F-ID for it at all - see the note in §18). | A function that needs an API key or a `DATABASE_URL` could not be configured without going around the UI (curl/Postman), defeating the point of the Web UI milestone. | F351 |
| GAP-19 | ~~The Flow step editor takes `Component ID`/`Component version ID` as raw free-text UUID inputs, with no picker over the user's own Functions/FunctionVersions (or Flows/FlowVersions for SUB_FLOW).~~ **RESOLVED 2026-09-16** — replaced with a `ComponentPicker` component: for FUNCTION/RESPONSE/MIDDLEWARE steps, a name-based Function dropdown followed by a Version dropdown filtered to READY versions only; for SUB_FLOW steps, a Flow dropdown (excluding the current flow) followed by a Version dropdown filtered to ADOPTED versions only - matching exactly what adoption itself requires (GAP-04). Works identically in the read-only step-detail view (now shows "fn_orders_validate_seed (fn_orders_v…) / v1" instead of two raw UUIDs) and in the create/edit form. Live-verified both: opening an existing ADOPTED step now shows real names, and creating a new step via the picker end-to-end produced a real, correctly-referenced step. | Made the visual Flow builder (F183) practically unusable for anyone without direct DB/API access - the exact "no curl/database access" bar EPIC-14 sets for itself. | F184 |
| GAP-12 | **PARTIALLY RESOLVED 2026-09-15** — FunctionVersion-scoped runtime configuration now exists end-to-end: non-secret env vars are persisted in PostgreSQL, secret values are written to OpenBao, PostgreSQL stores only secret references, Dispatcher resolves the exact FunctionVersion environment, IPC carries the resolved map, and the Node Runtime injects it into `process.env` for artifact execution. Runtime console output is now isolated from the Node executor protocol and emitted as structured, redacted runtime logs. Rotation, persisted/queryable logs, resource governance, and full logging pipeline hardening remain open. | Blocks production readiness. | F238–F282 |
| GAP-13 | ~~Neither the user-chosen entrypoint file nor the exported handler function name survived past the build step: `ArtifactPublisher.publish()` took no entrypoint parameter, `ArtifactMetadata` had no entrypoint/handler fields, and `LocalArtifactStore`/`S3ArtifactStore`/`FilesystemArtifactCache` all hardcoded `index.mjs` + `loadedModule.handler` — correct only by coincidence, because the two dev-seed artifacts happen to be named and exported that way.~~ **RESOLVED 2026-09-14** — found via user code review while wiring source submission. Fixed by giving every artifact a self-describing `ArtifactManifest` (`.funchole-artifact.json`) written by `NodeRuntimeBuilder` and read by all three artifact-resolution sites, since the Runtime Worker never queries Postgres and can only ever learn this from the artifact bytes themselves. `runtime/node/executor.mjs` now invokes `loadedModule[handler]` instead of a hardcoded `loadedModule.handler`. | Would have silently broken execution for any real function whose entrypoint wasn't literally `index.mjs` exporting `handler`, the instant Build/Deploy got wired to a transport. | F014, F040, F140–F145 |
| GAP-14 | ~~`FunctionVersionRepository.compareAndSetStatus`'s `@Modifying(clearAutomatically = true)` had no `flushAutomatically = true`, so a pending-but-unflushed write earlier in the same persistence context (e.g. a just-submitted source manifest) could be silently discarded by `clearAutomatically`'s `entityManager.clear()` before it ever reached the database — the bulk UPDATE itself would still succeed, masking the loss.~~ **RESOLVED 2026-09-14** — found while building and testing the real deploy endpoint end-to-end within one transaction (submit source → deploy): the source row vanished with a "no source submitted" error even though it had just been written and confirmed via the API response. Fixed by adding `flushAutomatically = true`. | A submitted source could be silently lost immediately before a deploy attempt in any code path that shares a transaction across both writes. | F037, F040 |
| GAP-15 | ~~`flow_steps` had a DB-level unique `(flow_version_id, position)` constraint (`uk_flow_steps_version_position`) as the only guard against duplicate step positions, but `FlowStepService.createStep`/`updateStep` never flushed immediately, so the violation surfaced later and unpredictably — as an opaque 500 `DataIntegrityViolationException` whenever Hibernate's next auto-flush happened to occur (in practice: during `adoptVersion`'s own `SELECT`, nowhere near the actual duplicate `createStep` call), instead of a clean 4xx at the point of the real mistake.~~ **RESOLVED 2026-09-14 (STORY-M1-07)** — found while writing a test for adopt-time position-ordering validation: creating two steps at the same position both returned 200, then `adopt` 500'd. Fixed by adding a proactive `FlowStepRepository.findByFlowVersion_IdAndPosition` check in `FlowStepService.createStep`/`updateStep`, so a duplicate position now fails immediately with a clear 422 naming the conflicting step. | A duplicate step position could pass step creation silently and only surface as an unexplained server error at some later, unrelated request. | F078 |
| GAP-16 | ~~`InvocationInspectionAccessService.inspect()` (STORY-M1-09) wasn't `@Transactional`, so its DIRECT_FUNCTION branch - which reads `FunctionVersion.function`, a lazy `@ManyToOne` association - threw `LazyInitializationException` outside of a transaction. Masked in every test by the test class's own `@Transactional`, which doesn't exist on a real HTTP request.~~ **RESOLVED 2026-09-15** — found live while manually testing the new direct-invoke endpoint end-to-end: `GET /api/v1/invocations/{id}` 500'd for a real DIRECT_FUNCTION invocation despite its own dedicated integration tests all passing. Fixed by adding `@Transactional(readOnly = true)`, matching `FunctionVersionInvocationService.invoke()`'s own convention. | Every DIRECT_FUNCTION invocation inspection was broken in production despite full test coverage - a class of bug this session's `@Transactional`-wrapped test classes cannot catch by construction. | F118 |

---

## 6. Release milestones

| Milestone | Outcome | Priority |
|---|---|---|
| M0 | **Architecture & Audit Baseline** — Current audit accepted; invocation-contract boundary established; backlog baselined. | Current |
| M1 | **First Real Human Lifecycle** — Fresh install + empty DB → create Function → version → submit source → deploy READY → compose Flow with exact FunctionVersion → route → HTTP response → inspect Invocation. No seed/manual SQL/files/S3. | P0 |
| M2 | **Human Product Surface** — The same lifecycle is usable from Controlplane Web UI, including source, build logs, Flow builder, route management and invocation inspection. | P1 |
| M3 | **Agent/MCP Lifecycle** — A coding agent can discover capabilities and perform create → source → deploy → Flow → route/test → inspect/debug through MCP. | P1 |
| M4 | **CLI Developer Workflow** — Local directory → FuncHole via CLI, including source push, deploy, logs, Flow/gateway commands and inspection. | P2 |
| M5 | **Production Hardening** — Authz, secrets, logs, resource isolation, outbox/recovery, dynamic runtime registration, operational readiness. | P2 |
| M6 | **Advanced Delivery** — Activation, rollback, weighted/A-B/canary routing, Git integration, declarative import/export. | P3 |
| M7 | **Platform Expansion** — Additional runtimes, custom OCI, triggers, ecosystem SDKs/integrations, metering/billing. | P3 |

### Milestone gate rule

Do not start later-product work merely because a deeper infrastructure subsystem is interesting.

For M1, every upstream stage must be usable through the real product surface before the next stage is considered complete.

---

## 7. Dependency-critical implementation sequence

```text
Product/ownership decisions
        ↓
Function CRUD
        ↓
FunctionVersion CRUD
        ↓
Canonical Source Contract
        ↓
Source Submission + Storage
        ↓
Build/Deploy live wiring
        ↓
READY FunctionVersion
        ↓
FunctionVersion ↔ FlowStep validation/pinning
        ↓
Flow adoption validation
        ↓
Gateway route using real Flow
        ↓
Zero-to-HTTP-response E2E
        ↓
Invocation inspection
        ↓
Web UI
        ↓
MCP
        ↓
CLI
        ↓
Production hardening
```

Parallel work is allowed only where it does not change these contracts.

---

## 8. Epic / Story / Task / Sub-task Plan

### EPIC-01 — Product & Ownership Model

**User story:** Define the product boundary and ownership hierarchy

**Tracked features:** F001–F350  
**Current planning score:** **8.8%**  
**Feature count:** 8  
**Complete features:** 0/8

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Lock resource ownership model**
  - [ ] Choose whether Workspace/Project exists in MVP
  - [ ] Define AppUser → resources relationship
  - [ ] Define ownership checks for Function, Flow, Gateway, Invocation
- [ ] **T02 — Lock lifecycle policies**
  - [ ] Function delete/archive semantics
  - [ ] FunctionVersion FAILED/retry semantics
  - [ ] Rebuild/redeploy semantics
- [ ] **T03 — Lock public-contract policy**
  - [ ] API compatibility/versioning rules
  - [ ] MCP/CLI contract stability rules
  - [ ] OSS/commercial license decision

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F001 | Project/Workspace concept দরকার কি না finalise করা | DECISION_REQUIRED | P2 | M5 — Product hardening | 10% |
| F002 | Resource ownership model: Function/Flow/Gateway কোন scope-এর | DECISION_REQUIRED | P2 | M5 — Product hardening | 10% |
| F007 | Delete/archive Function rules | DECISION_REQUIRED | P2 | M1 — Human zero-to-running | 10% |
| F017 | FAILED version behavior/retry policy | DECISION_REQUIRED | P2 | M1 — Human zero-to-running | 10% |
| F018 | Delete/archive draft/version rules | DECISION_REQUIRED | P3 | M1 — Human zero-to-running | 10% |
| F048 | Rebuild/redeploy rules | DECISION_REQUIRED | P2 | M1 — Human zero-to-running | 10% |
| F349 | License/commercial-use policy finalisation | DECISION_REQUIRED | P2 | M6+ — Expansion | 10% |
| F350 | Stable public contracts/versioning policy | MISSING | P2 | M6+ — Expansion | 0% |

### EPIC-02 — Function Lifecycle

**User story:** A developer can create and manage Functions through Controlplane

**Tracked features:** F003–F009  
**Current planning score:** **100.0%**  
**Feature count:** 7  
**Complete features:** 7/7

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** Delivered 2026-09-13 — `FunctionService`/`FunctionController` shipped in commit `b0f7fc6`, mirroring the existing `FlowService`/`FlowController` pattern. `F007` (archive/delete policy) was resolved as soft-delete, matching the already-accepted `Flow.softDelete()` precedent, rather than left blocked on a separate decision.

#### Tasks

- [x] **T01 — Implement Function application service**
  - [x] create
  - [x] get
  - [x] list by owner
  - [x] update metadata
  - [x] archive/delete policy
- [x] **T02 — Enforce function identity**
  - [x] stable functionKey
  - [x] uniqueness
  - [x] ownership validation
- [x] **T03 — Expose Function transport**
  - [x] REST request/response DTOs
  - [x] controller
  - [x] errors
  - [x] tests

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F003 | Create Function | COMPLETE | P1 | Existing foundation | 100% |
| F004 | Get Function | COMPLETE | P1 | Existing foundation | 100% |
| F005 | List Functions | COMPLETE | P1 | Existing foundation | 100% |
| F006 | Update Function metadata | COMPLETE | P1 | Existing foundation | 100% |
| F007 | Delete/archive Function rules | COMPLETE | P2 | Existing foundation | 100% |
| F008 | Unique/stable functionKey | COMPLETE | P1 | Existing foundation | 100% |
| F009 | Function lifecycle validation | COMPLETE | P1 | Existing foundation | 100% |

### EPIC-03 — FunctionVersion Lifecycle

**User story:** A developer can create and inspect versioned executable definitions

**Tracked features:** F010–F018  
**Current planning score:** **66.7%**  
**Feature count:** 9  
**Complete features:** 4/9

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** T01 and T03 delivered 2026-09-13 — `FunctionVersionService`/`FunctionVersionController` shipped (create/get/list only, mirroring `FlowVersionService`/`FlowVersionController`). T02 ("Enforce lifecycle") is intentionally **not** newly built here: the DRAFT/PUBLISHING/READY/FAILED enforcement already existed and is already tested (`FunctionVersionLifecycleRegistry`, `FunctionVersionLifecycleRegistryTests`) — this epic exposes the resulting `status` field for reading but does not add a transport to *trigger* transitions, since that belongs to the deploy trigger in EPIC-06 (F037/F156–F161), not to FunctionVersion CRUD. F017/F018 stay `DECISION_REQUIRED`: unlike EPIC-02's F007, delete/archive for a FunctionVersion was never in this epic's own T01 scope, so it is left as a genuinely open decision rather than assumed.

#### Tasks

- [x] **T01 — Create/read/list FunctionVersions**
  - [x] creation service
  - [x] version identity/numbering
  - [x] owner/function validation
- [x] **T02 — Enforce lifecycle** *(pre-existing internal implementation, unchanged by this epic; see Status note above)*
  - [x] DRAFT mutation boundary
  - [x] PUBLISHING guard
  - [x] READY immutability
  - [x] FAILED handling
- [x] **T03 — Expose lifecycle through Controlplane**
  - [x] REST endpoints
  - [x] status DTO
  - [x] tests

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F010 | Create FunctionVersion under Function | COMPLETE | P1 | Existing foundation | 100% |
| F011 | Get FunctionVersion | COMPLETE | P1 | Existing foundation | 100% |
| F012 | List versions of Function | COMPLETE | P1 | Existing foundation | 100% |
| F013 | Version number/identity generation | COMPLETE | P1 | Existing foundation | 100% |
| F014 | DRAFT → PUBLISHING → READY/FAILED lifecycle | INTERNAL_ONLY | P2 | M1 — Human zero-to-running | 60% |
| F015 | Prevent mutation after publish boundary | INTERNAL_ONLY | P1 | M1 — Human zero-to-running | 60% |
| F016 | READY version immutability | INTERNAL_ONLY | P1 | M1 — Human zero-to-running | 60% |
| F017 | FAILED version behavior/retry policy | DECISION_REQUIRED | P2 | M1 — Human zero-to-running | 10% |
| F018 | Delete/archive draft/version rules | DECISION_REQUIRED | P3 | M1 — Human zero-to-running | 10% |

### EPIC-04 — Canonical Source Submission

**User story:** Humans, CLI and agents submit the same canonical source model

**Tracked features:** F019–F033  
**Current planning score:** **70.0%**  
**Feature count:** 15  
**Complete features:** 10/15

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** Decision made 2026-09-14 — canonical source package format over REST supports **two multipart shapes side by side**, both converging on the exact same `SourceFile` list before reaching the transport-neutral service: (1) a single **zip archive** part (`file`), for a whole packaged project, and (2) **one or more individual file parts** (`files`), for a direct no-archive upload of just the files that make up the function — each part's filename carries its path relative to the source root (subdirectories included, e.g. `src/index.mjs`), so a real folder structure is preserved without zipping first. Exactly one of the two shapes must be present per request. `runtimeType` is never re-specified in the request either way (it always comes from the owning FunctionVersion's own `runtime` field, so it can never drift from the version it's attached to); `runtimeVersion` and `entrypoint` are multipart form fields. Shipped as `FunctionVersionSourceController` (`POST`/`GET .../source`), using a new transport-only `SourceUploadReader` (zip extraction + individual-file reading, kept out of the transport-neutral `FunctionVersionSourceService`). tar.gz was explicitly not chosen (F023 is satisfied by zip alone). JSON file-list (MCP, F025) and CLI directory packaging (F026) remain for M3/M4 — this epic only committed to the REST shape. F031 (atomic replacement) and F033 (ignore patterns) are genuinely still open: `LocalSourceStore.save` deletes-then-writes, which is not atomic, and no `.git`/`node_modules`-style exclusion exists yet.

#### Tasks

- [x] **T01 — Define source package specification**
  - [x] runtime/runtimeVersion/entrypoint ownership
  - [x] manifest shape
  - [x] file/path rules
  - [ ] binary-file policy *(still open — zip entries are read as UTF-8 text; binary source files are not yet supported)*
- [ ] **T02 — Complete source storage behavior**
  - [x] DRAFT replace
  - [ ] atomic replacement
  - [x] limits *(hardcoded zip-bomb caps only — not yet a configurable policy, see F032)*
  - [ ] ignore patterns
- [ ] **T03 — Create transport adapters**
  - [x] REST multipart/archive
  - [ ] JSON file-list for MCP
  - [ ] directory packaging for CLI
- [x] **T04 — Security validation**
  - [x] path traversal *(already existed in `FunctionVersionSourceService`; now verified end-to-end through the new transport)*
  - [x] archive bomb defense *(basic entry-count/size caps in `SourceArchiveReader`, not the full F269 policy)*
  - [x] size/file-count limits

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F019 | Define canonical source package format | COMPLETE | P0 | Existing foundation | 100% |
| F020 | Decide where runtime/runtimeVersion/entrypoint belong | COMPLETE | P0 | Existing foundation | 100% |
| F021 | Submit source files | COMPLETE | P1 | Existing foundation | 100% |
| F022 | Replace source while DRAFT | COMPLETE | P1 | Existing foundation | 100% |
| F023 | Upload archive (zip/tar.gz) | COMPLETE | P1 | Existing foundation | 100% |
| F024 | Multipart source upload | COMPLETE | P1 | Existing foundation | 100% |
| F025 | JSON/generated-file submission for MCP | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F026 | Local directory source submission for CLI | MISSING | P2 | M4 — CLI | 0% |
| F027 | Nested source paths | COMPLETE | P1 | Existing foundation | 100% |
| F028 | Path traversal/security validation | COMPLETE | P1 | Existing foundation | 100% |
| F029 | Source manifest persistence | COMPLETE | P2 | Existing foundation | 100% |
| F030 | Source content storage abstraction | COMPLETE | P2 | Existing foundation | 100% |
| F031 | Atomic source replacement | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F032 | Source size/file-count limits | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F033 | Ignore/exclude patterns (.git, build dirs etc.) | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-05 — Runtime Catalog

**User story:** Clients can discover valid runtime choices

**Tracked features:** F034–F036  
**Current planning score:** **0.0%**  
**Feature count:** 3  
**Complete features:** 0/3

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Define runtime catalog contract**
  - [ ] runtime type
  - [ ] supported versions
  - [ ] capabilities
- [ ] **T02 — Expose via REST/MCP**
  - [ ] list runtimes
  - [ ] list versions
  - [ ] machine-readable capability metadata

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F034 | List supported runtime types | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F035 | List supported runtime versions | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F036 | Runtime metadata/capabilities API | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-06 — Build & Deploy

**User story:** A DRAFT FunctionVersion can be built and deployed to READY

**Tracked features:** F037–F050  
**Current planning score:** **48.6%**  
**Feature count:** 14  
**Complete features:** 5/14

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** T01/T02 delivered 2026-09-14 — `FunctionVersionDeploymentService` is now a live `@Service` bean (a new `ArtifactPublisherConfig`/`ArtifactPublisherProperties` pair supplies the `ArtifactPublisher` bean it was missing), exposed via `POST .../deploy` on `FunctionVersionDeploymentController`. Reuses the existing build→publish→finalize pipeline unchanged. A dedicated `BuildExceptionHandler` (living in `controlplane`, since `core` cannot depend on it) surfaces `NodeBuildException` as 422 with stage/command/exitCode/stdout/stderr — real capture, not persisted (F044 partial, F045 still open). Verified with a real deploy against the live dev stack's actual RustFS (S3-compatible), not a fake: real tar.gz, real SHA-256, real object key. **Found and fixed a real, separate data-loss bug while building this** (see GAP-14): `FunctionVersionRepository.compareAndSetStatus`'s `@Modifying(clearAutomatically = true)` was missing `flushAutomatically = true`, so a pending-but-unflushed write earlier in the same persistence context could be silently discarded by `clearAutomatically`'s `entityManager.clear()` before it ever reached the database. F041/F042 (npm ci/install, build timeout) stay `INTERNAL_ONLY` - only the dependency-free build path was exercised through the new transport this round, not the npm branch specifically.

#### Tasks

- [x] **T01 — Wire existing deployment services as live beans**
  - [x] FunctionVersionSourceService bean *(already existed)*
  - [x] FunctionVersionDeploymentService bean
  - [x] RuntimeBuilder registry wiring *(already existed)*
- [x] **T02 — Expose deploy command**
  - [x] REST trigger
  - [x] state response
  - [x] duplicate/retry behavior *(rejects redeploy of an already-published version with 409; no retry-after-FAILED policy yet, see F048)*
- [ ] **T03 — Build observability**
  - [x] capture stdout/stderr *(on failure, in the error response only)*
  - [ ] persist logs
  - [x] structured failure
  - [ ] history
- [ ] **T04 — Hardening**
  - [ ] cancel
  - [ ] reproducibility metadata
  - [ ] dependency cache

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F037 | Trigger FunctionVersion build/deploy | COMPLETE | P1 | Existing foundation | 100% |
| F038 | Create isolated build workspace | COMPLETE | P2 | Existing foundation | 100% |
| F039 | RuntimeBuilder selection | COMPLETE | P2 | Existing foundation | 100% |
| F040 | Node build | COMPLETE | P2 | Existing foundation | 100% |
| F041 | npm ci / npm install handling | INTERNAL_ONLY | P2 | M1 — Human zero-to-running | 60% |
| F042 | Build timeout | INTERNAL_ONLY | P2 | M1 — Human zero-to-running | 60% |
| F043 | Build cancellation | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F044 | Build logs capture | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F045 | Build logs persistence | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F046 | Structured build errors | COMPLETE | P1 | Existing foundation | 100% |
| F047 | Build status/history | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F048 | Rebuild/redeploy rules | DECISION_REQUIRED | P2 | M1 — Human zero-to-running | 10% |
| F049 | Reproducible build metadata | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F050 | Dependency/cache strategy | MISSING | P3 | M1 — Human zero-to-running | 0% |

### EPIC-07 — Artifact Lifecycle

**User story:** Published artifacts are immutable, verifiable and maintainable

**Tracked features:** F051–F059, F355
**Current planning score:** **70.0%**
**Feature count:** 10
**Complete features:** 7/10

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** T01 delivered 2026-09-14 — every item was already correctly implemented internally; this epic's own contribution was proving it end-to-end through the real product path (`POST .../deploy`) against the real RustFS, not a fake `ArtifactPublisher`. T02 (GC/retention/provenance) remains untouched and out of scope for M1.

#### Tasks

- [x] **T01 — Preserve current artifact correctness**
  - [x] tar.gz packaging
  - [x] S3 object key
  - [x] checksum/size
  - [x] transactional finalization
  - [x] compensation
- [ ] **T02 — Artifact maintenance**
  - [ ] garbage collection
  - [ ] retention
  - [ ] provenance metadata
- [x] **T03 — Static frontend artifact support**
  - [x] STATIC runtime builder
  - [x] static artifact publishing
  - [x] Gateway static-site cache and serving

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F051 | Package prepared artifact | COMPLETE | P2 | Existing foundation | 100% |
| F052 | S3-compatible publishing | COMPLETE | P2 | Existing foundation | 100% |
| F053 | Artifact checksum/size verification | COMPLETE | P2 | Existing foundation | 100% |
| F054 | Immutable object naming | COMPLETE | P2 | Existing foundation | 100% |
| F055 | Deployment finalization transaction | COMPLETE | P2 | Existing foundation | 100% |
| F056 | Remote publish compensation | COMPLETE | P2 | Existing foundation | 100% |
| F057 | Artifact garbage collection | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F058 | Artifact retention policy | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F059 | Artifact provenance/build metadata | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F355 | STATIC-runtime frontend deployment | COMPLETE | P1 | M2 — Web product | 100% |

### EPIC-08 — Flow Composition

**User story:** Users compose real deployed FunctionVersions into executable Flows

**Tracked features:** F060–F089  
**Current planning score:** **68.3%**  
**Feature count:** 30  
**Complete features:** 19/30

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [x] **T01 — Join Function and Flow domains**
  - [x] FUNCTION step must resolve real Function
  - [x] pin exact FunctionVersion
  - [x] READY validation
  - [x] ownership validation
- [x] **T02 — Strengthen FlowVersion validation**
  - [x] response/end-state rules *(last step must be RESPONSE or SUB_FLOW, enforced at adopt)*
  - [x] contiguous positions *(unique + strictly ascending enforced at both create/update and adopt; gaps like 10/20/30 remain intentionally allowed, matching existing convention/tests - true gap-free numbering was never the goal)*
  - [x] invalid component rejection *(re-checked at adopt time too, catching drift like a soft-deleted Function after a step was authored)*
  - [x] immutable adopted version *(already held structurally - unchanged)*
- [ ] **T03 — Authoring mechanics**
  - [ ] step reorder
  - [ ] static input
  - [ ] request input mapping
  - [ ] previous-output mapping
  - [ ] response mapping
- [ ] **T04 — Advanced flow capabilities**
  - [x] sub-flow
  - [ ] retry
  - [ ] branch
  - [ ] parallel
  - [ ] fallback
  - [ ] timeouts

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F060 | Create Flow | COMPLETE | P0 | Existing foundation | 100% |
| F061 | Get/List Flow | COMPLETE | P1 | Existing foundation | 100% |
| F062 | Update Flow metadata | COMPLETE | P1 | Existing foundation | 100% |
| F063 | Create FlowVersion | COMPLETE | P0 | Existing foundation | 100% |
| F064 | Get/List FlowVersions | COMPLETE | P1 | Existing foundation | 100% |
| F065 | Draft FlowVersion editing | COMPLETE | P1 | Existing foundation | 100% |
| F066 | Add FUNCTION step | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F067 | Assign exact FunctionVersion to FUNCTION step | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F068 | Validate FunctionVersion is executable/READY | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F069 | Add RESPONSE step | COMPLETE | P0 | Existing foundation | 100% |
| F070 | Remove step | COMPLETE | P1 | Existing foundation | 100% |
| F071 | Reorder steps | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F072 | Update step configuration | COMPLETE | P1 | Existing foundation | 100% |
| F073 | Step input mapping | PARTIAL | P0 | M1 — Human zero-to-running | 50% |
| F074 | Previous-step output → next-step input mapping | PARTIAL | P0 | M1 — Human zero-to-running | 50% |
| F075 | Static input/config values | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F076 | Request/body/header/query input mapping | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F077 | RESPONSE status/header/body mapping | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F078 | Flow validation before publish/adopt | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F079 | Adopt/publish FlowVersion | COMPLETE | P0 | Existing foundation | 100% |
| F080 | Immutable adopted FlowVersion | COMPLETE | P1 | Existing foundation | 100% |
| F081 | Active/adopted version resolution | COMPLETE | P1 | Existing foundation | 100% |
| F082 | Invocation pins exact FlowVersion graph | COMPLETE | P2 | Existing foundation | 100% |
| F083 | Sub-Flow step | COMPLETE | P2 | M1 — Human zero-to-running | 100% |
| F084 | Exact Sub-Flow version pinning | COMPLETE | P3 | M1 — Human zero-to-running | 100% |
| F085 | Retry policy per step | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F086 | Conditional/branch step | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F087 | Parallel steps | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F088 | Error/fallback path | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F089 | Timeout per step | MISSING | P3 | M1 — Human zero-to-running | 0% |

### EPIC-09 — Gateway, Domain, DNS & Certificates

**User story:** Users publish adopted Flows behind managed HTTP gateways

**Tracked features:** F090–F109, F356
**Current planning score:** **90.5%**
**Feature count:** 21
**Complete features:** 17/21

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Preserve current gateway/domain lifecycle**
  - [ ] domain verification
  - [ ] gateway CRUD
  - [ ] certificate load/provision
  - [ ] hostname
- [ ] **T02 — Complete route productization**
  - [ ] create/update/delete route
  - [ ] conflict checks
  - [ ] adopted Flow resolution
  - [ ] remove seed dependency
  - [x] path-parameter matching
- [ ] **T03 — Certificate production hardening**
  - [ ] Let's Encrypt
  - [ ] renewal
  - [ ] retry/failure recovery

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F090 | Create Gateway | COMPLETE | P1 | Existing foundation | 100% |
| F091 | Get/List/Update Gateway | COMPLETE | P1 | Existing foundation | 100% |
| F092 | Create/manage Domain | COMPLETE | P1 | Existing foundation | 100% |
| F093 | Domain ownership/validation | COMPLETE | P1 | Existing foundation | 100% |
| F094 | TXT validation flow | COMPLETE | P1 | Existing foundation | 100% |
| F095 | Generate local self-signed cert | COMPLETE | P2 | Existing foundation | 100% |
| F096 | Let's Encrypt provisioning | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F097 | Certificate loading | COMPLETE | P2 | Existing foundation | 100% |
| F098 | Certificate renewal | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F099 | Certificate failure/retry handling | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F100 | Create Gateway route | COMPLETE | P0 | Existing foundation | 100% |
| F101 | HTTP method + exact path → Flow | COMPLETE | P0 | Existing foundation | 100% |
| F102 | Update route | COMPLETE | P1 | Existing foundation | 100% |
| F103 | Delete route | COMPLETE | P1 | Existing foundation | 100% |
| F104 | Route conflict validation | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F105 | Route → adopted Flow resolution | COMPLETE | P1 | Existing foundation | 100% |
| F106 | Gateway in-memory snapshot refresh | COMPLETE | P2 | Existing foundation | 100% |
| F107 | Remove dependency on seed scripts | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F108 | Custom hostname handling | COMPLETE | P2 | Existing foundation | 100% |
| F109 | Gateway final HTTP response correlation | COMPLETE | P2 | Existing foundation | 100% |
| F356 | Gateway path-parameter routes (`:name`) | COMPLETE | P1 | M2 — Web product | 100% |

### EPIC-10 — Invocation Product Surface

**User story:** Users can run and inspect Flow and direct Function invocations

**Tracked features:** F110–F124  
**Current planning score:** **66.7%**  
**Feature count:** 15  
**Complete features:** 10/15

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Preserve durable invocation model**
  - [ ] kind
  - [ ] identity
  - [ ] snapshot
  - [ ] status
  - [ ] step execution
  - [ ] result/error
- [x] **T02 — Expose direct FunctionVersion invocation**
  - [x] REST adapter *(`POST /api/v1/functions/{functionId}/versions/{versionId}/invoke`)*
  - [x] READY exact-version validation *(already enforced by the underlying service, surfaces as 409)*
  - [x] acceptance response *(invocationId/functionVersionId/initialStatus)*
- [ ] **T03 — Expose inspection**
  - [x] get invocation
  - [ ] list/filter
  - [x] step details
  - [ ] machine-readable errors
- [ ] **T04 — Later controls**
  - [ ] cancel
  - [ ] timeout
  - [ ] replay/retry
  - [ ] idempotency key

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F110 | Create FLOW Invocation | COMPLETE | P2 | Existing foundation | 100% |
| F111 | Immutable execution snapshot | COMPLETE | P2 | Existing foundation | 100% |
| F112 | FLOW / DIRECT_FUNCTION identity | COMPLETE | P2 | Existing foundation | 100% |
| F113 | Invocation status lifecycle | COMPLETE | P2 | Existing foundation | 100% |
| F114 | StepExecution persistence | COMPLETE | P2 | Existing foundation | 100% |
| F115 | Invocation result/error persistence | COMPLETE | P2 | Existing foundation | 100% |
| F116 | Direct FunctionVersion invocation service | COMPLETE | P2 | M1 — Human zero-to-running | 100% |
| F117 | Direct FunctionVersion invoke API | COMPLETE | P2 | M1 — Human zero-to-running | 100% |
| F118 | Get Invocation | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F119 | List/filter Invocations | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F120 | Step-by-step execution details | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F121 | Cancel invocation | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F122 | Invocation timeout | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F123 | Replay/retry invocation | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F124 | Idempotency key for invocation | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-11 — Dispatcher Reliability

**User story:** Dispatcher executes and recovers deterministic invocation plans

**Tracked features:** F125–F134  
**Current planning score:** **80.0%**  
**Feature count:** 10  
**Complete features:** 8/10

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Preserve current execution**
  - [ ] ready consumer
  - [ ] planner
  - [ ] FUNCTION
  - [ ] RESPONSE
  - [ ] capacity handling
- [x] **T02 — Fix unsupported-initial-step loop**
  - [x] prevent unsupported/non-terminating plans at Flow adoption
  - [x] make poison ready-event loop unreachable for adopted Flows
  - [x] cover through adoption validation and zero-to-response E2E
- [ ] **T03 — Add retries/recovery**
  - [ ] retry/backoff policy
  - [ ] attempt rows
  - [ ] restart/resume

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F125 | Ready-event consumer | COMPLETE | P2 | Existing foundation | 100% |
| F126 | Execution planning | COMPLETE | P2 | Existing foundation | 100% |
| F127 | FUNCTION execution | COMPLETE | P2 | Existing foundation | 100% |
| F128 | RESPONSE execution | COMPLETE | P2 | Existing foundation | 100% |
| F129 | Runtime selection | COMPLETE | P2 | Existing foundation | 100% |
| F130 | Capacity reserve/release | COMPLETE | P2 | Existing foundation | 100% |
| F131 | Exceptional completion handling | COMPLETE | P2 | Existing foundation | 100% |
| F132 | Unsupported step failure | COMPLETE | P2 | Existing foundation | 100% |
| F133 | Retry/backoff orchestration | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F134 | Crash recovery / resume | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-12 — Runtime Registry & Runtime

**User story:** Runtime execution is discoverable, isolated and recoverable

**Tracked features:** F135–F155  
**Current planning score:** **52.4%**  
**Feature count:** 21  
**Complete features:** 9/21

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Dynamic runtime discovery**
  - [x] persist runtime registration/capacity state in PostgreSQL
  - [x] coordinate reservations with row-level locks
  - [ ] worker-owned registration
  - [ ] health heartbeat
  - [ ] stale runtime expiry/capacity reconciliation
- [ ] **T02 — Execution hardening**
  - [ ] timeout/kill
  - [ ] crash recovery
  - [ ] warm/cold lifecycle
- [ ] **T03 — Resource boundaries**
  - [ ] CPU
  - [ ] memory
  - [ ] max duration
  - [ ] concurrency
- [ ] **T04 — Future runtimes**
  - [ ] Python
  - [ ] Go/Rust
  - [ ] custom OCI
  - [ ] digest pinning

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F135 | Runtime registration | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F136 | Health/status | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F137 | Capacity reporting | COMPLETE | P2 | Existing foundation | 100% |
| F138 | Least-in-flight selection | COMPLETE | P2 | Existing foundation | 100% |
| F139 | Persistent/distributed registry | COMPLETE | P3 | Existing foundation | 100% |
| F140 | Runtime worker lifecycle | COMPLETE | P2 | Existing foundation | 100% |
| F141 | IPC/UDS execution | COMPLETE | P2 | Existing foundation | 100% |
| F142 | INVOKE/ACCEPTED/RESULT/ERROR protocol | COMPLETE | P2 | Existing foundation | 100% |
| F143 | Persistent Node executor | COMPLETE | P2 | Existing foundation | 100% |
| F144 | Artifact local cache | COMPLETE | P2 | Existing foundation | 100% |
| F145 | Concurrent cache miss handling | COMPLETE | P2 | Existing foundation | 100% |
| F146 | Process/function resource boundary | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F147 | CPU limit | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F148 | Memory limit | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F149 | Execution timeout/kill | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F150 | Runtime crash recovery | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F151 | Runtime warm/cold lifecycle | PARTIAL | P3 | M1 — Human zero-to-running | 50% |
| F152 | Python runtime | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F153 | Go/Rust compiled runtime | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F154 | Custom OCI/Docker runtime | MISSING | P3 | M1 — Human zero-to-running | 0% |
| F155 | OCI digest pinning | MISSING | P3 | M1 — Human zero-to-running | 0% |

### EPIC-13 — Controlplane REST/API

**User story:** All core application use-cases are reachable through one user-facing command plane

**Tracked features:** F156–F172  
**Current planning score:** **67.6%**  
**Feature count:** 17  
**Complete features:** 9/17

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Complete Web/API wiring**
  - [ ] Function
  - [ ] FunctionVersion
  - [ ] source
  - [ ] deploy
  - [ ] invocation
- [ ] **T02 — Standardize API contracts**
  - [ ] request/response envelope
  - [ ] error model
  - [ ] pagination/filtering
  - [ ] versioning
- [ ] **T03 — API discoverability**
  - [ ] OpenAPI
  - [ ] examples
  - [ ] auth requirements

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F156 | Wire Controlplane to Web/API layer | PARTIAL | P0 | M1 — Human zero-to-running | 50% |
| F157 | Function REST API | COMPLETE | P0 | Existing foundation | 100% |
| F158 | FunctionVersion REST API | COMPLETE | P0 | Existing foundation | 100% |
| F159 | Source upload REST API | COMPLETE | P0 | Existing foundation | 100% |
| F160 | Build/deploy REST API | COMPLETE | P0 | Existing foundation | 100% |
| F161 | Build/status/log REST API | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F162 | Flow REST API | COMPLETE | P0 | Existing foundation | 100% |
| F163 | FlowVersion REST API | COMPLETE | P0 | Existing foundation | 100% |
| F164 | Flow-step management REST API | COMPLETE | P0 | Existing foundation | 100% |
| F165 | Gateway REST API | COMPLETE | P0 | Existing foundation | 100% |
| F166 | Route management REST API | COMPLETE | P0 | Existing foundation | 100% |
| F167 | Invocation REST API | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F168 | Consistent request/response envelope | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F169 | Error contract | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F170 | Pagination/filtering conventions | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F171 | API versioning strategy | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F172 | OpenAPI specification | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-14 — Controlplane Web UI

**User story:** A human can perform the full lifecycle without curl or database access

**Tracked features:** F173–F190, F351  
**Current planning score:** **72.4%**  
**Feature count:** 19  
**Complete features:** 12/19

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status (2026-09-16):** This epic's own score was left at a stale 0.0% after STORY-M2-02 shipped F173–F180 — an oversight in that story's PRD update, caught during this pass. Live user testing of STORY-M2-02 then surfaced three real gaps, all now fixed (see GAP-17/GAP-18/GAP-19): the Test invoke panel (F180) never actually executed a direct invocation (now does, via a NATS-publishing + Dispatcher fix); the Flow step editor had no Function/FunctionVersion picker (F184, now built); and the Function workspace had no environment-variable/secrets UI at all despite a complete backend (new feature **F351**, now built). Separately, auditing F181–F190 against the running app found the Flow list, Flow/step editor, visual step canvas, Gateway CRUD and Domain CRUD were already substantially built pre-session and simply never tracked — GAP-11's original "not audited, out of scope" note undersold what already existed.

#### Tasks

- [x] **T01 — Application shell and auth**
  - [x] connect to Controlplane API *(already built pre-session - a typed API client with 401 handling)*
  - [x] session handling *(cookie-based token + server-side route guarding via `proxy.ts`, Next.js 16's renamed `middleware.ts`)*
  - [x] navigation *(sidebar nav, now including Functions)*
- [x] **T02 — Function workflow**
  - [x] create/list/detail
  - [x] version history
  - [x] source editor/upload
  - [x] deploy/logs *(deploy status + transient session-scoped build-error display; no persisted/historical log viewer yet - see F179)*
  - [x] direct test *(GAP-17 fixed - real NATS publish + Dispatcher terminal-step fix; live-verified COMPLETED with a real result)*
  - [x] environment variables / secrets UI *(GAP-18/F351 - env vars + secrets panel, upsert-only matching the backend)*
- [x] **T03 — Flow workflow**
  - [x] flow/version editor *(pre-existing, now with a real component picker)*
  - [x] visual builder *(pre-existing React-Flow-style canvas with palette, zoom, step inspector)*
  - [x] function/version selector *(GAP-19 fixed - name-based Function/Version and Flow/Version pickers, live-verified)*
  - [ ] input mapping
- [~] **T04 — Gateway and operations**
  - [x] domain/gateway *(pre-existing, both CRUD, live-verified)*
  - [~] routes *(route method/path/gateway set at Flow-creation time; no dedicated route-management screen verified)*
  - [ ] invocation history
  - [ ] logs/errors
  - [ ] runtime health

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F173 | Controlplane web application wiring | COMPLETE | P0 | M2 — Web product | 100% |
| F174 | Function list/create screen | COMPLETE | P1 | M2 — Web product | 100% |
| F175 | Function detail | COMPLETE | P1 | M2 — Web product | 100% |
| F176 | FunctionVersion create/history | COMPLETE | P1 | M2 — Web product | 100% |
| F177 | Source editor/upload UI | COMPLETE | P1 | M2 — Web product | 100% |
| F178 | Build/deploy UI | COMPLETE | P1 | M2 — Web product | 100% |
| F179 | Build log viewer | PARTIAL | P1 | M2 — Web product | 50% |
| F180 | Function test/invoke panel | COMPLETE | P1 | M2 — Web product | 100% |
| F181 | Flow list/create | COMPLETE | P1 | M2 — Web product | 100% |
| F182 | FlowVersion editor | COMPLETE | P1 | M2 — Web product | 100% |
| F183 | Visual Flow builder | PARTIAL | P1 | M2 — Web product | 75% |
| F184 | Function selector/version selector | COMPLETE | P1 | M2 — Web product | 100% |
| F185 | Step config/input mapping UI | MISSING | P1 | M2 — Web product | 0% |
| F186 | Gateway/domain UI | COMPLETE | P1 | M2 — Web product | 100% |
| F187 | Route configuration UI | PARTIAL | P1 | M2 — Web product | 50% |
| F188 | Invocation/history viewer | MISSING | P1 | M2 — Web product | 0% |
| F189 | Logs/error viewer | MISSING | P1 | M2 — Web product | 0% |
| F190 | Runtime/health overview | MISSING | P3 | M2 — Web product | 0% |
| F351 | Function/FunctionVersion environment variables & secrets UI | COMPLETE | P1 | M2 — Web product | 100% |

### EPIC-15 — MCP Server

**User story:** Coding agents can perform the same lifecycle as humans

**Tracked features:** F191–F217  
**Current planning score:** **92.6%**
**Feature count:** 27  
**Complete features:** 23/27

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [x] **T01 — MCP foundation**
  - [x] server transport
  - [x] self-hosted connection model
  - [x] auth/session mapping through the current user
  - [x] capability discovery through annotated tools
- [~] **T02 — Function tools**
  - [x] create/get/list
  - [x] create version
  - [x] submit/replace/read source
  - [x] deploy/status
  - [ ] persisted build logs
- [x] **T03 — Flow/Gateway tools**
  - [x] create/edit/adopt Flow
  - [x] steps
  - [x] route management
  - [x] Gateway/Domain management
- [~] **T04 — Execution/debug loop**
  - [x] direct invoke
  - [x] Flow invoke
  - [x] inspection
  - [x] step/log/error retrieval
  - [~] iterative fix-deploy-test through composable tools, without a dedicated agent workflow runner
- [~] **T05 — Security/schema quality**
  - [ ] MCP-specific token provisioning
  - [ ] fine-grained MCP scopes
  - [x] agent-friendly schemas
  - [x] function/flow/config resources

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F191 | MCP server foundation | COMPLETE | P0 | M3 — Agent/MCP | 100% |
| F192 | Authentication/session mapping | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F193 | Capability/resource discovery | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F194 | create_function tool | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F195 | list/get_function | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F196 | create_function_version | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F197 | Source file submission | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F198 | Source replacement | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F199 | Deploy FunctionVersion | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F200 | Get deployment/build status | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F201 | Get build logs/errors | PARTIAL | P2 | M3 — Agent/MCP | 50% |
| F202 | Create Flow | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F203 | Create/edit FlowVersion | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F204 | Add/modify Flow step | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F205 | Resolve/select FunctionVersion | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F206 | Adopt FlowVersion | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F207 | Create/configure Gateway route | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F208 | Direct invoke FunctionVersion | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F209 | Invoke/test Flow | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F210 | Inspect Invocation | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F211 | Inspect step/log/error | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F212 | Iterative code → deploy → test loop | PARTIAL | P2 | M3 — Agent/MCP | 50% |
| F213 | Tool schemas optimised for coding agents | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F214 | MCP resources for functions/flows/runtimes | COMPLETE | P2 | M3 — Agent/MCP | 100% |
| F215 | Self-hosted user-specific MCP connection model | COMPLETE | P0 | M3 — Agent/MCP | 100% |
| F216 | MCP token/API-key provisioning | PARTIAL | P2 | M3 — Agent/MCP | 50% |
| F217 | MCP permissions/scopes | PARTIAL | P2 | M3 — Agent/MCP | 50% |

### EPIC-16 — CLI

**User story:** Developers can drive FuncHole from a local project directory

**Tracked features:** F218–F229  
**Current planning score:** **0.0%**  
**Feature count:** 12  
**Complete features:** 0/12

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — CLI foundation**
  - [ ] config
  - [ ] auth
  - [ ] server selection
- [ ] **T02 — Function workflow**
  - [ ] function commands
  - [ ] version create
  - [ ] source push
  - [ ] .funcholeignore
  - [ ] deploy/logs
  - [ ] invoke
- [ ] **T03 — Composition workflow**
  - [ ] Flow commands
  - [ ] Gateway/route commands
  - [ ] Invocation inspect
  - [ ] YAML import/export

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F218 | CLI foundation/auth/config | MISSING | P1 | M4 — CLI | 0% |
| F219 | function create/list/get | MISSING | P2 | M4 — CLI | 0% |
| F220 | version create | MISSING | P2 | M4 — CLI | 0% |
| F221 | source push <directory> | MISSING | P2 | M4 — CLI | 0% |
| F222 | .funcholeignore | MISSING | P2 | M4 — CLI | 0% |
| F223 | deploy | MISSING | P2 | M4 — CLI | 0% |
| F224 | deploy/build logs | MISSING | P2 | M4 — CLI | 0% |
| F225 | direct invoke/test | MISSING | P2 | M4 — CLI | 0% |
| F226 | Flow create/edit/apply | MISSING | P2 | M4 — CLI | 0% |
| F227 | Gateway/route commands | MISSING | P2 | M4 — CLI | 0% |
| F228 | Invocation inspect/logs | MISSING | P2 | M4 — CLI | 0% |
| F229 | export/import Flow YAML | MISSING | P3 | M4 — CLI | 0% |

### EPIC-17 — Identity, Authorization & Multi-tenancy

**User story:** Every command is authenticated and scoped to owned resources

**Tracked features:** F230–F237  
**Current planning score:** **18.8%**  
**Feature count:** 8  
**Complete features:** 1/8

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Token surfaces**
  - [ ] API tokens
  - [ ] MCP tokens
  - [ ] CLI login/token
  - [ ] rotation/revocation
- [ ] **T02 — Authorization**
  - [ ] resource ownership guards
  - [ ] roles/permissions
- [ ] **T03 — Multi-tenant model**
  - [ ] workspace/tenant isolation
  - [ ] queries scoped by tenant
  - [ ] cross-tenant denial tests

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F230 | User authentication for Controlplane | COMPLETE | P1 | Existing foundation | 100% |
| F231 | API tokens | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F232 | MCP tokens | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F233 | CLI token/login | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F234 | Token revocation/rotation | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F235 | Resource-level authorization | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F236 | Roles/permissions | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F237 | Tenant/workspace isolation | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-18 — Configuration, Secrets & Networking

**User story:** Functions receive secure runtime configuration

**Tracked features:** F238–F245, F352–F354
**Current planning score:** **86.4%**
**Feature count:** 11
**Complete features:** 9/11

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** Runtime injection delivered 2026-09-15 — `FunctionVersionConfigController` exposes authenticated get/upsert APIs under the exact FunctionVersion resource. `function_version_env_vars` stores plain non-secret values in PostgreSQL. `function_version_secrets` stores only OpenBao secret references, while `OpenBaoFunctionSecretStore` writes the actual secret value to OpenBao. Dispatcher resolves the exact FunctionVersion environment, reads secret values through OpenBao, sends the resolved map over IPC, and the Node Runtime injects it into `process.env` for the artifact handler. Secret redaction, rotation, resource governance, and network-policy hardening remain future work.

**Status (2026-09-16 — new Database resource, F352):** Following a design discussion about shared/warm DB connections across Functions, built a first-class `Database` resource so FuncHole - not the function author - owns the connection. A `Database` (top-level, owned by `AppUser`, like `Function`) stores host/port/credentials; the password is written to OpenBao via a new `FunctionSecretStore.saveForDatabase(databaseId, key, value)` method (never stored in Postgres, matching the existing `function_version_secrets` discipline) and referenced by `password_secret_ref`. A new many-to-many `function_version_database_attachments` join table lets a FunctionVersion attach zero or more Databases (`PUT`/`DELETE`/`GET` under `/api/v1/functions/{functionId}/versions/{versionId}/databases`). At invocation time, a new `JdbcFunctionVersionDatabaseResolver` (dispatcher module) resolves the attached databases (joining `function_version_database_attachments` ⋈ `databases`, reading the password back from OpenBao) into a new `DatabaseConnectionInfo` record, threaded through the full Dispatcher → Runtime Worker → Node executor IPC chain (`RuntimeExecutionRequest` → `IpcInvokePayload` → `RuntimeInvokePayload` → `NodeExecutionRequest` → `NodeExecuteMessage`, each side of the process boundary independently declaring the same field, matching the existing `environment` field's pattern - no shared Java types across the boundary). `executor.mjs` now calls `handler(input, context)` (a non-breaking second argument - existing single-argument handlers are unaffected) where `context.db(name)` returns a warm `pg.Pool`, cached per distinct database resource for the life of the Node process (added the `pg` npm dependency under `runtime/node/`, wired into the Docker image's `runtime-worker` stage via `npm ci`). Database passwords are added to the existing console-log redactor alongside secrets. Scope, confirmed with the user before building: all four engines (Postgres, Supabase, MongoDB, MySQL) are intended, but only **Postgres, external connections only** is built now - internal/Docker-provisioned databases and the other three engines are deliberately deferred, with the schema (`databases.type` as a plain extensible VARCHAR) and the resolver/executor scaffolding built to add them later without a redesign. Frontend: a new `/databases` CRUD page (mirroring the existing Functions page exactly) plus a "Databases" panel on the FunctionVersion detail page (attach/detach, mirroring the existing "Environment & secrets" panel). Live-verified end-to-end against the real running dev stack: created a `devdb` Database pointed at the dev Postgres container, attached it to a real FunctionVersion, deployed, and invoked a function whose handler ran `context.db("devdb").query(...)` - both through `curl` and through the actual browser UI - confirming a real SQL round-trip (`{"row": {"db": "funchole", "sum": 2}}`) and pool reuse across repeated invocations (no new Node executor process per call).

**Status (2026-09-20 — Flow-shared configuration, F353/F354):** Environment profiles and database resources can now be attached at the Flow level so every FunctionVersion executed inside that Flow can inherit the same environment/secrets and database context without duplicating attachments on each function. The Dispatcher-side resolvers merge Flow-level configuration with exact FunctionVersion configuration before IPC, preserving the current storage boundary: non-secret values in PostgreSQL, secret values in OpenBao, and runtime execution receiving only resolved values. This closes the product gap raised during Flow adoption work: credentials and shared settings can now be modeled once per Flow.

#### Tasks

- [ ] **T01 — Environment variables**
  - [x] non-secret vars
  - [x] scope model
  - [x] runtime injection
- [ ] **T02 — Secrets**
  - [x] secret references in PostgreSQL with values stored in OpenBao
  - [ ] redaction
  - [x] runtime injection
  - [ ] rotation
- [ ] **T03 — Network policy**
  - [ ] outbound policy
  - [ ] DNS/network access rules
  - [ ] build-network policy
- [~] **T04 — Managed Database resource**
  - [x] `Database` resource CRUD (external Postgres connections only)
  - [x] password stored via OpenBao, never plaintext in Postgres
  - [x] FunctionVersion↔Database attachment (many-to-many)
  - [x] warm connection pooling via `context.db(name)`, non-breaking handler signature
  - [x] Databases web UI + FunctionVersion attachment panel
  - [ ] internal/Docker-provisioned databases
  - [ ] MySQL/MongoDB/Supabase-client engines
- [x] **T05 — Flow-shared configuration**
  - [x] EnvironmentProfile CRUD and secret/env storage
  - [x] Flow↔EnvironmentProfile attachment with priority
  - [x] Flow↔Database attachment
  - [x] Dispatcher/runtime inheritance for all steps under the Flow
  - [x] REST and MCP surfaces for Flow configuration

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F238 | Function environment variables | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F239 | Secret values | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F240 | Secret storage/encryption | COMPLETE | P2 | M1 — Human zero-to-running | 100% |
| F241 | Secret injection into runtime | COMPLETE | P2 | M1 — Human zero-to-running | 100% |
| F242 | Version/environment scoped config | COMPLETE | P3 | M1 — Human zero-to-running | 100% |
| F243 | Non-secret environment variables | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F244 | Outbound network policy | MISSING | P2 | M5 — Product hardening | 0% |
| F245 | Runtime DNS/network access | PARTIAL | P2 | M5 — Product hardening | 50% |
| F352 | Managed Database resource (`context.db()`) | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F353 | Flow-shared environment profiles | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F354 | Flow-shared database attachments | COMPLETE | P1 | M1 — Human zero-to-running | 100% |

### EPIC-19 — Observability

**User story:** Humans and agents can understand every build and invocation failure

**Tracked features:** F246–F256  
**Current planning score:** **36.4%**  
**Feature count:** 11  
**Complete features:** 1/11

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

**Status:** Runtime logging foundation delivered 2026-09-15 — user `console.log`/`console.error` no longer writes raw text onto the Node executor stdout protocol. The Node bridge converts console output into structured `LOG` protocol messages with `executionId`, stream and redacted message; Java consumes those messages without completing the execution and writes them to Runtime Worker logs.
>
> **Progress update (2026-09-23):** Build logs (F250) are now fully persisted and queryable — found unblocked and started directly from user request after a real STATIC deploy failure turned out to be undiagnosable live (build output only ever existed transiently in one deploy response, and a separate bug meant `GlobalExceptionHandler` wasn't even logging unexpected 500s server-side; see `MCP_TESTING_FEEDBACK.md` item 10). New `function_version_build_logs` table (migration V25); every `npm ci`/`npm install`/`npm run build` stage a deploy attempt runs is persisted the moment it completes, success or failure, via a new `BuildLogRecorder` seam threaded through `RuntimeBuilder.build(...)` (both `NodeRuntimeBuilder` and `StaticRuntimeBuilder`), independent of whether the overall deploy attempt itself later succeeds. Exposed on both surfaces this backlog tracks separately: `GET .../build-logs` (REST) and `get_function_version_build_logs` (MCP) - the latter is also referenced directly from every `BuildFailureException` message, since that is the only place an MCP-calling agent ever sees error detail (`BuildExceptionHandler`'s richer REST 422 response never fires for MCP tool calls). Live-verified end-to-end against the real running dev stack: a real failed `npm install` (malformed/missing config) and a real successful one both produced correctly-persisted, correctly-read-back rows; the new MCP tool confirmed registered via the real MCP protocol's `tools/list`. Invocation/runtime-level logs (F246/F247/F249) and telemetry (T02/T03) remain open - this closed the build-log slice of F248 specifically, not the whole epic.

#### Tasks

- [ ] **T01 — Logs**
  - [x] function stdout/stderr at runtime protocol boundary
  - [x] build logs
  - [x] persistence *(build logs only - invocation/runtime logs not yet persisted)*
  - [x] query API *(build logs only, via REST `GET .../build-logs` and MCP `get_function_version_build_logs`)*
- [ ] **T02 — Execution telemetry**
  - [ ] step timing
  - [ ] latency
  - [ ] error rates
  - [ ] runtime health
- [ ] **T03 — Correlation/tracing**
  - [ ] gateway→invocation→runtime correlation
  - [ ] trace IDs
  - [ ] OpenTelemetry

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F246 | Invocation structured logs | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F247 | Function stdout/stderr capture | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F248 | Logs persisted/queryable | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F249 | Step-level timing | PARTIAL | P1 | M1 — Human zero-to-running | 50% |
| F250 | Build logs | COMPLETE | P1 | M1 — Human zero-to-running | 100% |
| F251 | Runtime health metrics | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F252 | Invocation latency metrics | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F253 | Error rate metrics | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F254 | Trace/correlation IDs | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F255 | Gateway → Invocation → Runtime correlation | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F256 | OpenTelemetry integration | MISSING | P3 | M5 — Product hardening | 0% |

### EPIC-20 — Events & Reliability

**User story:** State and events remain correct across failures and restarts

**Tracked features:** F257–F268  
**Current planning score:** **25.0%**  
**Feature count:** 12  
**Complete features:** 1/12

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Event contracts**
  - [ ] schema/versioning
  - [ ] consumer compatibility
- [ ] **T02 — Atomic delivery**
  - [ ] outbox
  - [ ] terminal-event durability
  - [ ] lost/replayed handling
  - [ ] idempotency
- [ ] **T03 — Recovery**
  - [ ] dispatcher restart *(registry state now persists; invocation replay/reconciliation still open)*
  - [ ] runtime failure
  - [ ] graceful drain
  - [ ] orphan/stuck invocation reconciliation

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F257 | Durable command/event contracts | PARTIAL | P2 | M5 — Production hardening | 50% |
| F258 | NATS JetStream stream/consumer configuration | COMPLETE | P2 | M5 — Production hardening | 100% |
| F259 | Event schema/versioning | MISSING | P2 | M5 — Production hardening | 0% |
| F260 | DB + event atomicity/outbox | MISSING | P1 | M5 — Production hardening | 0% |
| F261 | Gateway terminal-event durability | MISSING | P1 | M5 — Production hardening | 0% |
| F262 | Lost/replayed event handling | PARTIAL | P2 | M5 — Production hardening | 50% |
| F263 | Consumer idempotency | PARTIAL | P2 | M5 — Production hardening | 50% |
| F264 | Dispatcher restart recovery | PARTIAL | P2 | M5 — Production hardening | 50% |
| F265 | Runtime failure recovery | MISSING | P2 | M5 — Production hardening | 0% |
| F266 | Graceful shutdown/drain | MISSING | P2 | M5 — Production hardening | 0% |
| F267 | Orphan Invocation detection | MISSING | P2 | M5 — Production hardening | 0% |
| F268 | Stuck PENDING/RUNNING reconciliation | MISSING | P2 | M5 — Production hardening | 0% |

### EPIC-21 — Security & Resource Governance

**User story:** Untrusted user code cannot compromise the host or exhaust the platform

**Tracked features:** F269–F282  
**Current planning score:** **10.7%**  
**Feature count:** 14  
**Complete features:** 0/14

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Source/build security**
  - [ ] archive bombs
  - [ ] sandbox
  - [ ] dependency/network controls
- [ ] **T02 — Runtime isolation**
  - [ ] process/container isolation
  - [ ] CPU/memory/max-duration
  - [ ] concurrency/backpressure
- [ ] **T03 — API security**
  - [ ] body limits
  - [ ] rate limits
  - [x] runtime-boundary secret redaction
  - [ ] audit log
- [ ] **T04 — Scale model**
  - [ ] autoscaling design

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F269 | Source archive bomb protection | MISSING | P2 | M5 — Production hardening | 0% |
| F270 | Build sandbox/isolation | MISSING | P2 | M5 — Production hardening | 0% |
| F271 | Runtime execution isolation | MISSING | P2 | M5 — Production hardening | 0% |
| F272 | Dependency/network restrictions during build | MISSING | P3 | M5 — Production hardening | 0% |
| F273 | Secret redaction from logs | PARTIAL | P2 | M5 — Production hardening | 50% |
| F274 | Request/body size limits | MISSING | P2 | M5 — Production hardening | 0% |
| F275 | Rate limiting | MISSING | P2 | M5 — Production hardening | 0% |
| F276 | Audit log | MISSING | P2 | M5 — Production hardening | 0% |
| F277 | Per-function CPU configuration | MISSING | P3 | M5 — Production hardening | 0% |
| F278 | Per-function memory configuration | MISSING | P3 | M5 — Production hardening | 0% |
| F279 | Max execution duration | PARTIAL | P1 | M5 — Production hardening | 50% |
| F280 | Concurrency configuration | MISSING | P3 | M5 — Production hardening | 0% |
| F281 | Queue/backpressure | PARTIAL | P2 | M5 — Production hardening | 50% |
| F282 | Runtime autoscaling model | MISSING | P3 | M5 — Production hardening | 0% |

### EPIC-22 — Developer & Agent Experience

**User story:** The platform is easy to understand, bootstrap and iterate against

**Tracked features:** F283–F292  
**Current planning score:** **25.0%**  
**Feature count:** 10  
**Complete features:** 0/10

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Developer onboarding**
  - [ ] first-run
  - [ ] examples
  - [ ] starter templates
  - [ ] local test
  - [ ] local→prod parity
- [ ] **T02 — Error quality**
  - [ ] human actionable errors
  - [ ] structured agent errors
  - [ ] machine-readable status
- [ ] **T03 — Agent guidance**
  - [ ] discover next action
  - [ ] build/deploy/test feedback loop

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F283 | First-run onboarding | PARTIAL | P1 | M5 — Product hardening | 50% |
| F284 | Runtime/function examples | PARTIAL | P1 | M5 — Product hardening | 50% |
| F285 | Starter templates | MISSING | P1 | M5 — Product hardening | 0% |
| F286 | Function local test | MISSING | P3 | M5 — Product hardening | 0% |
| F287 | Local → production same source/config workflow | PARTIAL | P2 | M5 — Product hardening | 50% |
| F288 | Error messages actionable for humans | PARTIAL | P1 | M5 — Product hardening | 50% |
| F289 | Structured errors actionable for agents | MISSING | P2 | M3 — Agent/MCP | 0% |
| F290 | Machine-readable lifecycle/status | PARTIAL | P2 | M3 — Agent/MCP | 50% |
| F291 | Discover required next action | MISSING | P2 | M3 — Agent/MCP | 0% |
| F292 | Build/deploy/test feedback loop | MISSING | P2 | M3 — Agent/MCP | 0% |

### EPIC-23 — Documentation & Testing

**User story:** The product behavior is specified and regression-safe

**Tracked features:** F293–F311  
**Current planning score:** **34.7%**  
**Feature count:** 19  
**Complete features:** 3/19

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Specifications**
  - [ ] architecture
  - [ ] Function lifecycle
  - [ ] source package
  - [ ] runtime contract
  - [ ] Flow model
  - [ ] REST/MCP/CLI docs
- [ ] **T02 — Vertical E2E tests**
  - [ ] empty DB create Function
  - [ ] Function→Version→Source
  - [ ] Source→Build→READY
  - [ ] Flow composition
  - [ ] route creation
  - [ ] zero-to-HTTP response
- [ ] **T03 — Execution/recovery tests**
  - [ ] direct invocation
  - [ ] MCP E2E
  - [ ] failure paths
  - [ ] restart recovery

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F293 | Architecture documentation | PARTIAL | P2 | M5 — Product hardening | 50% |
| F294 | Function lifecycle docs | MISSING | P2 | M5 — Product hardening | 0% |
| F295 | Source package specification | MISSING | P0 | M5 — Product hardening | 0% |
| F296 | Runtime contract | PARTIAL | P2 | M5 — Product hardening | 50% |
| F297 | Flow model specification | PARTIAL | P0 | M5 — Product hardening | 50% |
| F298 | REST API docs | PARTIAL | P1 | M5 — Product hardening | 50% |
| F299 | MCP usage docs | MISSING | P2 | M5 — Product hardening | 0% |
| F300 | CLI docs | MISSING | P2 | M5 — Product hardening | 0% |
| F301 | Self-host deployment docs | PARTIAL | P2 | M5 — Product hardening | 50% |
| F302 | Empty DB → create Function test | MISSING | P0 | M1 — Human zero-to-running | 0% |
| F303 | Function → Version → Source test | MISSING | P0 | M1 — Human zero-to-running | 0% |
| F304 | Source → Build → READY test | INTERNAL_ONLY | P0 | M1 — Human zero-to-running | 60% |
| F305 | Flow composition through public API test | COMPLETE | P0 | Existing foundation | 100% |
| F306 | Route creation through public API test | COMPLETE | P0 | Existing foundation | 100% |
| F307 | Zero-to-HTTP-response E2E without seeds | COMPLETE | P0 | M1 — Human zero-to-running | 100% |
| F308 | Direct invocation E2E | MISSING | P1 | M1 — Human zero-to-running | 0% |
| F309 | MCP zero-to-running Function E2E | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F310 | Failure-path E2E | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F311 | Restart/recovery tests | MISSING | P2 | M1 — Human zero-to-running | 0% |

### EPIC-24 — Platform Operations

**User story:** A self-hosted install can start, validate, upgrade and recover safely

**Tracked features:** F312–F320  
**Current planning score:** **33.3%**  
**Feature count:** 9  
**Complete features:** 2/9

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Self-host stack**
  - [ ] Docker Compose
  - [ ] Postgres migrations
  - [ ] NATS/RustFS
  - [ ] health/readiness
  - [ ] config validation
- [ ] **T02 — Operations**
  - [ ] backup/restore
  - [ ] upgrade strategy
- [ ] **T03 — Later orchestration**
  - [ ] Kubernetes
  - [ ] Helm

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F312 | Docker Compose complete self-host stack | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F313 | Database migration startup | COMPLETE | P2 | Existing foundation | 100% |
| F314 | NATS/RustFS/local dependencies setup | COMPLETE | P2 | Existing foundation | 100% |
| F315 | Health/readiness endpoints | PARTIAL | P2 | M1 — Human zero-to-running | 50% |
| F316 | Configuration validation | MISSING | P2 | M1 — Human zero-to-running | 0% |
| F317 | Backup/restore guidance | MISSING | P2 | M5 — Production hardening | 0% |
| F318 | Upgrade/migration strategy | MISSING | P2 | M5 — Production hardening | 0% |
| F319 | Kubernetes deployment | MISSING | P3 | M5 — Production hardening | 0% |
| F320 | Helm chart | MISSING | P3 | M5 — Production hardening | 0% |

### EPIC-25 — Version Activation & Traffic

**User story:** Future requests can move safely between READY FunctionVersions

**Tracked features:** F321–F327  
**Current planning score:** **0.0%**  
**Feature count:** 7  
**Complete features:** 0/7

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Activation**
  - [ ] one active version
  - [ ] READY-only activation
  - [ ] atomic switch
  - [ ] publication revision
- [ ] **T02 — Release control**
  - [ ] rollback
  - [ ] weighted selection
  - [ ] deterministic A/B
  - [ ] canary

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F321 | READY FunctionVersion activation | MISSING | P3 | M6+ — Expansion | 0% |
| F322 | One active version per Function | MISSING | P3 | M6+ — Expansion | 0% |
| F323 | Rollback | MISSING | P3 | M6+ — Expansion | 0% |
| F324 | Activation publication revision | MISSING | P3 | M6+ — Expansion | 0% |
| F325 | Weighted FunctionVersion selection | MISSING | P3 | M6+ — Expansion | 0% |
| F326 | Deterministic A/B routing | MISSING | P3 | M6+ — Expansion | 0% |
| F327 | Canary deployment | MISSING | P3 | M6+ — Expansion | 0% |

### EPIC-26 — Git Integration & Declarative Portability

**User story:** Source and composition can be connected to Git and portable manifests

**Tracked features:** F328–F334  
**Current planning score:** **0.0%**  
**Feature count:** 7  
**Complete features:** 0/7

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Git deploy**
  - [ ] repository connection
  - [ ] branch/commit deploy
  - [ ] webhook auto-deploy
  - [ ] commit provenance
- [ ] **T02 — Import/export**
  - [ ] Flow YAML export/import
  - [ ] Function/project manifest

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F328 | GitHub repository connection | MISSING | P3 | M6+ — Expansion | 0% |
| F329 | Deploy from commit/branch | MISSING | P3 | M6+ — Expansion | 0% |
| F330 | Webhook auto-deploy | MISSING | P3 | M6+ — Expansion | 0% |
| F331 | Commit SHA provenance | MISSING | P3 | M6+ — Expansion | 0% |
| F332 | Flow YAML export | MISSING | P3 | M6+ — Expansion | 0% |
| F333 | Flow YAML import/apply | MISSING | P3 | M6+ — Expansion | 0% |
| F334 | Function/project manifest | MISSING | P3 | M6+ — Expansion | 0% |

### EPIC-27 — Commercial Platform Capabilities

**User story:** Hosted/commercial deployments can account for and limit usage

**Tracked features:** F335–F338  
**Current planning score:** **0.0%**  
**Feature count:** 4  
**Complete features:** 0/4

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Metering**
  - [ ] invocation/build/runtime usage
  - [ ] usage records
- [ ] **T02 — Commercial controls**
  - [ ] quotas
  - [ ] billing hooks

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F335 | Usage accounting | MISSING | P3 | M6+ — Expansion | 0% |
| F336 | Invocation/build/runtime metering | MISSING | P3 | M6+ — Expansion | 0% |
| F337 | Quotas | MISSING | P3 | M6+ — Expansion | 0% |
| F338 | Billing hooks | MISSING | P3 | M6+ — Expansion | 0% |

### EPIC-28 — Extensibility & Triggers

**User story:** FuncHole can grow beyond HTTP-only Functions and Flows

**Tracked features:** F339–F343  
**Current planning score:** **0.0%**  
**Feature count:** 5  
**Complete features:** 0/5

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Extensibility**
  - [ ] plugin/runtime provider model
  - [ ] webhook/event trigger abstraction
- [ ] **T02 — Triggers**
  - [ ] cron
  - [ ] queue/event
  - [ ] manual

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F339 | Plugin/runtime provider model | MISSING | P3 | M6+ — Expansion | 0% |
| F340 | Webhook/event triggers beyond HTTP | MISSING | P3 | M6+ — Expansion | 0% |
| F341 | Scheduled/cron Flow trigger | MISSING | P3 | M6+ — Expansion | 0% |
| F342 | Queue/event trigger | MISSING | P3 | M6+ — Expansion | 0% |
| F343 | Manual trigger | MISSING | P3 | M6+ — Expansion | 0% |

### EPIC-29 — Ecosystem Integrations

**User story:** Existing application ecosystems can invoke FuncHole Flows

**Tracked features:** F344–F346  
**Current planning score:** **0.0%**  
**Feature count:** 3  
**Complete features:** 0/3

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — SDK foundation**
  - [ ] PHP package / Flow execution client
- [ ] **T02 — Integrations**
  - [ ] WordPress remote-functions
  - [ ] Shopify/app integration

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F344 | PHP SDK / package to execute Flow | MISSING | P3 | M6+ — Expansion | 0% |
| F345 | WordPress remote-function integration | MISSING | P3 | M6+ — Expansion | 0% |
| F346 | Shopify/app integration path | MISSING | P3 | M6+ — Expansion | 0% |

### EPIC-30 — OSS Governance

**User story:** The project is contributor-friendly and has stable governance

**Tracked features:** F347–F350  
**Current planning score:** **27.5%**  
**Feature count:** 4  
**Complete features:** 0/4

**Acceptance outcome:** All P0/P1 features in this epic are externally usable through the intended product boundary, covered by focused tests, and no seeded/manual workaround is required for the corresponding lifecycle stage.

#### Tasks

- [ ] **T01 — Contributor workflow**
  - [ ] dev setup
  - [ ] contribution guide
  - [ ] issue templates
- [ ] **T02 — Governance**
  - [ ] license/commercial-use policy
  - [ ] public contract/versioning policy

#### Feature tracking

| ID | Feature | Status | Priority | Target/Milestone | Score |
|---|---|---|---|---|---:|
| F347 | Contributor development setup | PARTIAL | P2 | M6+ — Expansion | 50% |
| F348 | Contribution guidelines | PARTIAL | P2 | M6+ — Expansion | 50% |
| F349 | License/commercial-use policy finalisation | DECISION_REQUIRED | P2 | M6+ — Expansion | 10% |
| F350 | Stable public contracts/versioning policy | MISSING | P2 | M6+ — Expansion | 0% |


---

## 9. M1 — First Real Human Lifecycle: detailed delivery stories

This is the critical path. These stories are intentionally small enough to become implementation issues/tasks.

### STORY-M1-01 — Create Function

**As a developer**, I can create a Function owned by me so that future FunctionVersions have a durable parent.

**Tasks**
- [ ] Add `FunctionService` or equivalent application use-case.
  - [ ] Validate authenticated owner.
  - [ ] Generate/validate stable `functionKey`.
  - [ ] Persist Function.
  - [ ] Define duplicate-key error.
- [ ] Add get/list operations.
  - [ ] Owner-scoped query.
  - [ ] Pagination contract if required by M1.
- [ ] Add REST adapter.
  - [ ] Create request DTO.
  - [ ] Response DTO.
  - [ ] Controller.
  - [ ] Error mapping.
- [ ] Tests.
  - [ ] Empty DB create.
  - [ ] Duplicate key.
  - [ ] Cross-owner access.
  - [ ] List/get.

**Done when:** a user can create and retrieve a Function using only the Controlplane API.

### STORY-M1-02 — Create FunctionVersion

**As a developer**, I can create a DRAFT FunctionVersion under a Function.

**Tasks**
- [ ] Add creation/read/list application use-cases.
  - [ ] Verify Function ownership.
  - [ ] Define version number/identity behavior.
  - [ ] Initial status = DRAFT.
- [ ] Add REST endpoints.
- [ ] Tests for ownership, ordering and DRAFT initialization.

**Done when:** a user can create a DRAFT version without internal code/test fixtures.

### STORY-M1-03 — Lock Canonical Source Package Contract

**As a developer or agent**, I can submit source through different transports without changing the domain source model.

**Decision tasks**
- [ ] Decide canonical manifest.
  - [ ] `runtimeType`.
  - [ ] `runtimeVersion`.
  - [ ] `entrypoint`.
  - [ ] file paths.
- [ ] Decide binary-file handling.
- [ ] Decide REST upload form for M1.
  - [ ] Recommended: archive/multipart adapter → canonical `SourceBundle`.
- [ ] Define max archive/file count/size.
- [ ] Define replacement semantics.
- [ ] Publish `SOURCE_PACKAGE_SPEC.md`.

**Done when:** REST, future CLI and future MCP can all normalize into one documented source model.

### STORY-M1-04 — Submit/Replace Source

**As a developer**, I can upload source to a DRAFT FunctionVersion.

**Tasks**
- [ ] Wire `FunctionVersionSourceService` as a live application bean.
- [ ] Build REST source adapter.
  - [ ] Parse/normalize canonical package.
  - [ ] Reject unsafe paths.
  - [ ] Reject invalid entrypoint.
  - [ ] Reject non-DRAFT update.
- [ ] Make replacement safe.
  - [ ] Preserve previous source if replacement fails.
  - [ ] Add atomic publication strategy if required.
- [ ] Tests using real filesystem + Postgres.

**Done when:** raw source is stored through the API and the manifest is durable.

### STORY-M1-05 — Build & Deploy to READY

**As a developer**, I can deploy a source-backed DRAFT FunctionVersion and receive READY or a structured failure.

**Tasks**
- [ ] Wire deployment/build dependencies as Spring beans.
- [ ] Add deploy API.
- [ ] Reuse existing build → publish → finalizer pipeline.
- [ ] Add build status response.
- [ ] Add minimum useful failure/log output.
- [ ] Tests from submitted source through READY artifact.

**Done when:** the user-created source produces the same object-key/checksum-protected artifact that Runtime consumes.

### STORY-M1-06 — Bind Real FunctionVersion to Flow ✅ SHIPPED 2026-09-14

**As a Flow author**, I can select an actual Function and exact READY FunctionVersion instead of typing arbitrary UUIDs.

**Tasks**
- [x] Define Flow FUNCTION-step contract. *(widened to FUNCTION/RESPONSE/MIDDLEWARE → Function, SUB_FLOW → Flow)*
- [x] Resolve `functionId` and exact `functionVersionId`.
- [x] Verify both exist.
- [x] Verify version belongs to function.
- [x] Verify version is READY.
- [x] Verify ownership/access.
- [x] Persist exact pin.
- [x] Reject arbitrary/nonexistent UUIDs.
- [x] Decide DB-level referential strategy. *(app-level validation in `FlowStepService`, no FK — consistent with existing conventions)*
- [x] Add API and integration tests.

**Done when:** a Flow cannot adopt a FUNCTION step pointing at a nonexistent/unready FunctionVersion. **Verified live** against the real Dispatcher/Runtime Worker (FUNCTION→RESPONSE chain, SUB_FLOW flattening). Scope also grew to make RESPONSE genuinely executable and MIDDLEWARE dispatch identically to FUNCTION — see GAP-04 in §5 for full detail.

### STORY-M1-07 — Strengthen Flow Adoption ✅ SHIPPED 2026-09-14

**As a developer**, I cannot publish an invalid FlowVersion.

**Tasks**
- [x] Validate positions/ordering. *(unique + strictly ascending, enforced both at step create/update time - fixing a real bug found along the way, see GAP-15 - and again at adopt time)*
- [x] Validate supported component types. *(already structurally guaranteed by the `FlowStepComponentType` enum at deserialization; confirmed, no new code needed)*
- [x] Validate FUNCTION references. *(re-validated at adopt time via the extracted `FlowStepReferenceValidator`, catching drift like a soft-deleted Function since the step was authored)*
- [x] Define/validate terminal RESPONSE rules. *(the last step of an adopted FlowVersion must be RESPONSE or SUB_FLOW - sound by induction for SUB_FLOW, since it can only reference an already-ADOPTED, already-validated FlowVersion)*
- [x] Preserve immutable adopted version semantics. *(validation runs before any mutation; unchanged)*
- [ ] Add atomic/reliable reorder if M1 UI requires it. *(skipped - no M1 UI exists yet, EPIC-11/GAP-11 is still 0%, so the story's own conditional doesn't hold)*

**Done when:** an invalid FlowVersion (out-of-order/duplicate positions, a non-terminal last step, or a step whose Function/Flow reference has since gone stale) cannot be adopted. **Verified live** against the real Controlplane API (6/6 scenarios: rejects non-terminal last step, rejects duplicate position at create time, rejects adopt after a referenced Function is soft-deleted, accepts a SUB_FLOW-terminated flow, and the FUNCTION→RESPONSE baseline still adopts).

### STORY-M1-08 — Eliminate Demo Seeds From Product Path ✅ SHIPPED 2026-09-14

**As a developer**, I can produce the same `/orders`-style behavior using only public APIs.

**Tasks**
- [x] Create real Functions.
- [x] Create real FunctionVersions.
- [x] Upload their source.
- [x] Deploy real artifacts.
- [x] Create Flow through API.
- [x] Reference those exact FunctionVersions.
- [x] Adopt Flow.
- [x] Route via real Gateway. *(already automatic - a Flow's own `gatewayId`/`httpMethod`/`path` is all `GatewayRegistryLoader` needs; no separate route entity exists)*
- [x] Remove seed dependency from E2E path.

**Done when:** GET /orders works with zero raw-SQL-seeded Flow/Function/artifact data. New script `scripts/dev/seed-orders-demo.sh` drives the real Controlplane API end-to-end (create 3 Functions → submit source → deploy to READY → create Flow → add 3 steps → adopt) to provision the exact same GET /orders demo, replacing the old raw-SQL version. `scripts/dev/seed-flow-routes.sh` no longer seeds `flw_orders_list` at all (only the two unrelated, always-stepless `flw_orders_create`/`flw_checkout` routes it also seeds, kept as-is - out of scope, and harmless since they were never invocable either way). **Verified live**: hard-deleted the old raw-SQL-seeded `/orders` Flow/FunctionVersion/steps from the shared dev DB, ran the new script against a real (if not literally fresh) volume, confirmed `curl https://a6n1y8.funchole.test/orders` returns 200 with the same response shape as before - now produced entirely by real deployed Function code, not seed data - and confirmed the script is idempotent (a second run detects the existing Flow and no-ops). GAP-05 is now resolved for GET /orders; `runtime/artifacts/dev/{...861,...862}` (the old fake pre-seeded RustFS artifacts) are now unreferenced dead weight, left in place as harmless generic dev-artifact scaffolding rather than removed (out of scope for this story).

### STORY-M1-09 — Invocation Inspection API ✅ SHIPPED 2026-09-14

**As a developer**, I can inspect execution status/result/error after a test or Gateway request.

**Tasks**
- [x] Expose `InvocationInspectionService` through a narrow transport boundary. *(a new `InvocationInspectionHandoff` contract in `invocation-contract`, implemented in `dispatcher` - the only module that legitimately sees both Invocation-level and step-level durable state - and wired onto controlplane's runtime classpath the same way `FunctionVersionInvocationHandoff` already was)*
- [x] Define user-facing DTO. *(`InvocationInspectionResponse`/`InvocationStepInspectionResponse`)*
- [x] Preserve FLOW vs DIRECT_FUNCTION identities. *(exactly one identity group populated per the existing `InvocationInspection` contract, unchanged)*
- [x] Add endpoint and authz. *(`GET /api/v1/invocations/{invocationId}`; ownership checked transitively through the owning Flow or FunctionVersion - an Invocation carries no owner column of its own)*
- [x] Add step-level inspection where available. *(chosen over deferring it - see the session decision: required a new `dispatcher`→Spring wiring seam, `InvocationStepExecutionRegistry.findAllByInvocationId`, and a new controlplane→dispatcher runtime dependency)*

**Done when:** a caller who owns the invocation's Flow/FunctionVersion can read its full durable state, including per-step detail; a caller who doesn't gets the same 404 as a nonexistent id. **Verified live**: real GET /orders invocation returned full status/result/error plus all 3 steps' individual results; nonexistent id → 404; unauthenticated → 401. Direct FunctionVersion invocation still has no external transport (F116/F117) and there is still no list/filter endpoint (F119) - neither was in scope.

### STORY-M1-10 — Zero-to-HTTP-Response E2E ✅ SHIPPED 2026-09-14

**As the product team**, we have one regression test proving the platform lifecycle.

**Test must start with:**
- [x] empty application data;
- [x] no Function/Flow seed;
- [x] no prebuilt artifact seed.

**Test must perform:**
1. [x] authenticate;
2. [x] create Function;
3. [x] create FunctionVersion;
4. [x] upload source;
5. [x] deploy;
6. [x] assert READY;
7. [x] create Flow/FlowVersion;
8. [x] add exact FunctionVersion step; *(the single step is RESPONSE itself, pinned to the exact READY FunctionVersion - a separate plain FUNCTION step wasn't needed to prove the lifecycle)*
9. [x] add RESPONSE;
10. [x] adopt;
11. [x] configure Gateway route; *(a real Gateway/AppDomain/Certificate row, self-signed cert generated via the real `certificate` module's `SelfSignedCertificateGenerator` - no OpenBao)*
12. [x] send real HTTP request; *(a hand-rolled raw-socket HTTPS/1.1 client with a trust-everything SSLContext, matching this repo's existing style of hand-rolling protocol clients over raw sockets in tests)*
13. [x] execute real Runtime Worker/Node artifact; *(a real `PersistentNodeExecutor` spawning a real `node` child process against the real `runtime/node/executor.mjs`)*
14. [x] receive final response;
15. [x] inspect Invocation. *(via the real `GET /api/v1/invocations/{id}` from STORY-M1-09)*

**Done when:** this is green in CI or a reproducible integration environment. **Shipped as `controlplane/src/test/java/.../ZeroToHttpResponseE2ETest.java`**, tagged `@Tag("e2e")` and run via a new, separate `./gradlew :controlplane:e2eTest` task (excluded from the default `test` task - this is far heavier than every other test in the suite: multiple testcontainers, a real Node spawn, a real TLS handshake). Real Dispatcher/Runtime Worker/Gateway instances are wired **in-process** inside the test JVM (their actual production classes, real Unix sockets, a real TCP port - not separate OS processes, which has zero precedent anywhere in this repo and would be far more fragile for CI); Postgres/NATS/MinIO (standing in for RustFS - both are S3-API-compatible, same AWS SDK v2 client) are real via testcontainers. Required two small, justified production visibility changes: `InvocationDispatcher`'s previously package-private 6-arg constructor (the one that accepts a real `RuntimeExecutionGateway`) is now `public`, and `GatewayServer` gained a `boundPort()` accessor (needed since the test binds to an OS-assigned ephemeral port to avoid collisions, matching how the Postgres/NATS/MinIO containers already pick their own ports) - neither changes any existing behavior. Verified green on two consecutive runs (not flaky); confirmed the default `./gradlew test` still excludes it and runs unaffected; confirmed `./gradlew build -x test` still succeeds.

---

## 10. M2 — Controlplane Web stories

### STORY-M2-01 — Web shell ✅ ALREADY SHIPPED (verified 2026-09-15)
- [x] Authentication/session. *(cookie-based token, 401 → redirect)*
- [x] API client. *(typed, `ApiError` with status/message/details)*
- [x] Error handling.
- [x] Navigation/resource scoping. *(sidebar nav + server-side route guarding via `proxy.ts`)*

**Note:** found already fully built and live-verified working (login → dashboard → real data) when scoping STORY-M2-02 - not built this session, but confirmed done.

### STORY-M2-02 — Function workspace ✅ SHIPPED 2026-09-15
- [x] Function create/list/detail.
- [x] Version history.
- [x] Source upload/editor.
- [x] Deploy status.
- [x] Build logs. *(transient, session-scoped only - see F179; no persisted/historical log storage exists on the backend yet)*
- [x] Direct test invocation. *(required also building the missing `POST .../invoke` REST endpoint - F116/F117 - since the backend service existed but was never exposed; closes GAP-07 fully)*

**Done when:** a user can create a Function, submit source, deploy it to READY, and directly test-invoke it, entirely through the UI. **Verified live**, including dark mode: created a real Function → draft version → submitted source via the editor → deployed to a real READY artifact → ran a direct test invocation → inspected its durable status via the STORY-M1-09 endpoint. Found and fixed two real bugs along the way: a `LazyInitializationException` in the already-shipped Invocation Inspection endpoint (GAP-16, never caught by its own tests since they're `@Transactional` and a real request isn't), and a source-editor render race where the editor didn't wait for its initial fetch to resolve before deciding whether to show itself.

### STORY-M2-03 — Flow builder
- [ ] Flow/FlowVersion editor.
- [ ] Function selector.
- [ ] Exact version selector.
- [ ] Step reorder.
- [ ] Input/output mapping.
- [ ] RESPONSE configuration.
- [ ] Validation feedback.

### STORY-M2-04 — Gateway & operations
- [ ] Domain/Gateway.
- [ ] Route configuration.
- [ ] Invocation history.
- [ ] Runtime/build/error logs.

---

## 11. M3 — MCP stories

### STORY-M3-01 — Self-hosted MCP connection

- [ ] Decide per-user/self-hosted endpoint model.
- [ ] Token/API-key auth.
- [ ] Scopes/permissions.
- [ ] Capability discovery.
- [ ] MCP server health/version resource.

### STORY-M3-02 — Function development tools

- [ ] `create_function`.
- [ ] `get_function`.
- [ ] `list_functions`.
- [ ] `create_function_version`.
- [ ] `submit_source`.
- [ ] `replace_source`.
- [ ] `deploy_function_version`.
- [ ] `get_build_status`.
- [ ] `get_build_logs`.

### STORY-M3-03 — Composition tools

- [ ] `create_flow`.
- [ ] `create_flow_version`.
- [ ] `add_function_step`.
- [ ] `update_flow_step`.
- [ ] `adopt_flow_version`.
- [ ] `configure_gateway_route`.

### STORY-M3-04 — Agent execution/debug loop

- [ ] `invoke_function_version`.
- [ ] `invoke/test_flow`.
- [ ] `get_invocation`.
- [ ] `get_step_execution`.
- [ ] structured error output.
- [ ] agent can edit source and redeploy based on failure.

### MCP milestone acceptance

A coding agent can start with no Function and complete:

```text
discover runtimes
→ create Function
→ create version
→ submit source
→ deploy
→ inspect build
→ create Flow
→ pin FunctionVersion
→ adopt
→ route/test
→ inspect failure/result
→ modify source
→ redeploy new version
→ retest
```

without raw database, S3, filesystem or internal Java access.

---

## 12. M4 — CLI stories

- [ ] Server/auth config.
- [ ] Function commands.
- [ ] Version commands.
- [ ] `source push <directory>`.
- [ ] `.funcholeignore`.
- [ ] Deploy and build logs.
- [ ] Direct invoke.
- [ ] Flow apply/edit.
- [ ] Gateway/route management.
- [ ] Invocation inspection.
- [ ] YAML export/import.

CLI, REST and MCP must reuse the same application semantics and must not fork business rules.

---

## 13. M5 — Production-hardening stories

### Reliability
- [ ] Transactional outbox or equivalent DB/event atomicity.
- [ ] Durable Gateway terminal delivery.
- [ ] Poison-event handling.
- [ ] Dispatcher restart recovery.
- [ ] Runtime crash recovery.
- [ ] Stuck invocation reconciliation.
- [ ] Graceful drain/shutdown.

### Security
- [ ] Build sandbox.
- [ ] Runtime isolation.
- [ ] CPU/memory/time boundaries.
- [ ] Archive limits.
- [ ] Request/body limits.
- [ ] Rate limiting.
- [ ] Audit log.
- [ ] Secrets encryption and log redaction.

### Runtime operations
- [ ] Dynamic worker registration.
- [ ] Heartbeats.
- [ ] Capacity reconciliation.
- [ ] Distributed registry strategy.

### Observability
- [ ] Build logs.
- [ ] Function stdout/stderr.
- [ ] Persisted/queryable logs.
- [ ] Step timings.
- [ ] Metrics.
- [ ] correlation/trace IDs.
- [ ] OpenTelemetry.

---

## 14. Explicitly preserved later work

The following are intentionally **not** allowed to interrupt M1:

- READY FunctionVersion activation.
- One-active-version semantics.
- Rollback.
- publication revision.
- weighted traffic.
- A/B.
- canary.
- Python runtime.
- Go/Rust runtime.
- custom OCI runtime.
- Git deploy.
- cron/queue triggers.
- billing/metering.

They remain fully tracked in F321–F343 and F335–F338.

---

## 15. Definition of Done by layer

### Domain/Application
- lifecycle validation lives outside transport adapters;
- ownership/authorization enforced;
- errors are explicit and machine-readable;
- no seed/manual dependency.

### REST
- request/response DTO;
- validation;
- documented status/error behavior;
- auth;
- controller test;
- integration test where persistence matters.

### Web
- complete user path;
- error/retry states;
- loading/progress;
- no hidden manual prerequisite.

### MCP
- stable tool schema;
- deterministic identifiers;
- structured failures;
- no business logic duplicated in tool handler;
- agent can continue from returned state.

### CLI
- scriptable output;
- clear exit codes;
- source packaging consistent with canonical source spec.

### Execution
- exact versions pinned;
- durable invocation identity;
- recoverable terminal semantics;
- resource capacity released exactly once.

---

## 16. Complete F001–F350 (+F351–F356) master tracking matrix

This is the authoritative checklist for this PRD.

| ID | Epic | Feature | Status | Score | Priority | Milestone |
|---|---|---|---|---:|---|---|
| F001 | Project Model | Project/Workspace concept দরকার কি না finalise করা | DECISION_REQUIRED | 10% | P2 | M5 — Product hardening |
| F002 | Project Model | Resource ownership model: Function/Flow/Gateway কোন scope-এর | DECISION_REQUIRED | 10% | P2 | M5 — Product hardening |
| F003 | Function | Create Function | COMPLETE | 100% | P1 | Existing foundation |
| F004 | Function | Get Function | COMPLETE | 100% | P1 | Existing foundation |
| F005 | Function | List Functions | COMPLETE | 100% | P1 | Existing foundation |
| F006 | Function | Update Function metadata | COMPLETE | 100% | P1 | Existing foundation |
| F007 | Function | Delete/archive Function rules | COMPLETE | 100% | P2 | Existing foundation |
| F008 | Function | Unique/stable functionKey | COMPLETE | 100% | P1 | Existing foundation |
| F009 | Function | Function lifecycle validation | COMPLETE | 100% | P1 | Existing foundation |
| F010 | FunctionVersion | Create FunctionVersion under Function | COMPLETE | 100% | P1 | Existing foundation |
| F011 | FunctionVersion | Get FunctionVersion | COMPLETE | 100% | P1 | Existing foundation |
| F012 | FunctionVersion | List versions of Function | COMPLETE | 100% | P1 | Existing foundation |
| F013 | FunctionVersion | Version number/identity generation | COMPLETE | 100% | P1 | Existing foundation |
| F014 | FunctionVersion | DRAFT → PUBLISHING → READY/FAILED lifecycle | INTERNAL_ONLY | 60% | P2 | M1 — Human zero-to-running |
| F015 | FunctionVersion | Prevent mutation after publish boundary | INTERNAL_ONLY | 60% | P1 | M1 — Human zero-to-running |
| F016 | FunctionVersion | READY version immutability | INTERNAL_ONLY | 60% | P1 | M1 — Human zero-to-running |
| F017 | FunctionVersion | FAILED version behavior/retry policy | DECISION_REQUIRED | 10% | P2 | M1 — Human zero-to-running |
| F018 | FunctionVersion | Delete/archive draft/version rules | DECISION_REQUIRED | 10% | P3 | M1 — Human zero-to-running |
| F019 | Source | Define canonical source package format | COMPLETE | 100% | P0 | Existing foundation |
| F020 | Source | Decide where runtime/runtimeVersion/entrypoint belong | COMPLETE | 100% | P0 | Existing foundation |
| F021 | Source | Submit source files | COMPLETE | 100% | P1 | Existing foundation |
| F022 | Source | Replace source while DRAFT | COMPLETE | 100% | P1 | Existing foundation |
| F023 | Source | Upload archive (zip/tar.gz) | COMPLETE | 100% | P1 | Existing foundation |
| F024 | Source | Multipart source upload | COMPLETE | 100% | P1 | Existing foundation |
| F025 | Source | JSON/generated-file submission for MCP | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F026 | Source | Local directory source submission for CLI | MISSING | 0% | P2 | M4 — CLI |
| F027 | Source | Nested source paths | COMPLETE | 100% | P1 | Existing foundation |
| F028 | Source | Path traversal/security validation | COMPLETE | 100% | P1 | Existing foundation |
| F029 | Source | Source manifest persistence | COMPLETE | 100% | P2 | Existing foundation |
| F030 | Source | Source content storage abstraction | COMPLETE | 100% | P2 | Existing foundation |
| F031 | Source | Atomic source replacement | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F032 | Source | Source size/file-count limits | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F033 | Source | Ignore/exclude patterns (.git, build dirs etc.) | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F034 | Runtime Catalog | List supported runtime types | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F035 | Runtime Catalog | List supported runtime versions | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F036 | Runtime Catalog | Runtime metadata/capabilities API | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F037 | Build | Trigger FunctionVersion build/deploy | COMPLETE | 100% | P1 | Existing foundation |
| F038 | Build | Create isolated build workspace | COMPLETE | 100% | P2 | Existing foundation |
| F039 | Build | RuntimeBuilder selection | COMPLETE | 100% | P2 | Existing foundation |
| F040 | Build | Node build | COMPLETE | 100% | P2 | Existing foundation |
| F041 | Build | npm ci / npm install handling | INTERNAL_ONLY | 60% | P2 | M1 — Human zero-to-running |
| F042 | Build | Build timeout | INTERNAL_ONLY | 60% | P2 | M1 — Human zero-to-running |
| F043 | Build | Build cancellation | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F044 | Build | Build logs capture | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F045 | Build | Build logs persistence | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F046 | Build | Structured build errors | COMPLETE | 100% | P1 | Existing foundation |
| F047 | Build | Build status/history | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F048 | Build | Rebuild/redeploy rules | DECISION_REQUIRED | 10% | P2 | M1 — Human zero-to-running |
| F049 | Build | Reproducible build metadata | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F050 | Build | Dependency/cache strategy | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F051 | Artifact | Package prepared artifact | COMPLETE | 100% | P2 | Existing foundation |
| F052 | Artifact | S3-compatible publishing | COMPLETE | 100% | P2 | Existing foundation |
| F053 | Artifact | Artifact checksum/size verification | COMPLETE | 100% | P2 | Existing foundation |
| F054 | Artifact | Immutable object naming | COMPLETE | 100% | P2 | Existing foundation |
| F055 | Artifact | Deployment finalization transaction | COMPLETE | 100% | P2 | Existing foundation |
| F056 | Artifact | Remote publish compensation | COMPLETE | 100% | P2 | Existing foundation |
| F057 | Artifact | Artifact garbage collection | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F058 | Artifact | Artifact retention policy | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F059 | Artifact | Artifact provenance/build metadata | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F355 | Artifact | STATIC-runtime frontend deployment | COMPLETE | 100% | P1 | M2 — Web product |
| F060 | Flow | Create Flow | COMPLETE | 100% | P0 | Existing foundation |
| F061 | Flow | Get/List Flow | COMPLETE | 100% | P1 | Existing foundation |
| F062 | Flow | Update Flow metadata | COMPLETE | 100% | P1 | Existing foundation |
| F063 | FlowVersion | Create FlowVersion | COMPLETE | 100% | P0 | Existing foundation |
| F064 | FlowVersion | Get/List FlowVersions | COMPLETE | 100% | P1 | Existing foundation |
| F065 | FlowVersion | Draft FlowVersion editing | COMPLETE | 100% | P1 | Existing foundation |
| F066 | Flow Step | Add FUNCTION step | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F067 | Flow Step | Assign exact FunctionVersion to FUNCTION step | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F068 | Flow Step | Validate FunctionVersion is executable/READY | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F069 | Flow Step | Add RESPONSE step | COMPLETE | 100% | P0 | Existing foundation |
| F070 | Flow Step | Remove step | COMPLETE | 100% | P1 | Existing foundation |
| F071 | Flow Step | Reorder steps | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F072 | Flow Step | Update step configuration | COMPLETE | 100% | P1 | Existing foundation |
| F073 | Flow Step | Step input mapping | PARTIAL | 50% | P0 | M1 — Human zero-to-running |
| F074 | Flow Step | Previous-step output → next-step input mapping | PARTIAL | 50% | P0 | M1 — Human zero-to-running |
| F075 | Flow Step | Static input/config values | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F076 | Flow Step | Request/body/header/query input mapping | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F077 | Flow Step | RESPONSE status/header/body mapping | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F078 | Flow | Flow validation before publish/adopt | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F079 | Flow | Adopt/publish FlowVersion | COMPLETE | 100% | P0 | Existing foundation |
| F080 | Flow | Immutable adopted FlowVersion | COMPLETE | 100% | P1 | Existing foundation |
| F081 | Flow | Active/adopted version resolution | COMPLETE | 100% | P1 | Existing foundation |
| F082 | Flow | Invocation pins exact FlowVersion graph | COMPLETE | 100% | P2 | Existing foundation |
| F083 | Flow | Sub-Flow step | COMPLETE | 100% | P2 | M1 — Human zero-to-running |
| F084 | Flow | Exact Sub-Flow version pinning | COMPLETE | 100% | P3 | M1 — Human zero-to-running |
| F085 | Flow | Retry policy per step | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F086 | Flow | Conditional/branch step | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F087 | Flow | Parallel steps | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F088 | Flow | Error/fallback path | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F089 | Flow | Timeout per step | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F090 | Gateway | Create Gateway | COMPLETE | 100% | P1 | Existing foundation |
| F091 | Gateway | Get/List/Update Gateway | COMPLETE | 100% | P1 | Existing foundation |
| F092 | Domain | Create/manage Domain | COMPLETE | 100% | P1 | Existing foundation |
| F093 | Domain | Domain ownership/validation | COMPLETE | 100% | P1 | Existing foundation |
| F094 | DNS | TXT validation flow | COMPLETE | 100% | P1 | Existing foundation |
| F095 | Certificate | Generate local self-signed cert | COMPLETE | 100% | P2 | Existing foundation |
| F096 | Certificate | Let's Encrypt provisioning | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F097 | Certificate | Certificate loading | COMPLETE | 100% | P2 | Existing foundation |
| F098 | Certificate | Certificate renewal | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F099 | Certificate | Certificate failure/retry handling | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F100 | Route | Create Gateway route | COMPLETE | 100% | P0 | Existing foundation |
| F101 | Route | HTTP method + exact path → Flow | COMPLETE | 100% | P0 | Existing foundation |
| F102 | Route | Update route | COMPLETE | 100% | P1 | Existing foundation |
| F103 | Route | Delete route | COMPLETE | 100% | P1 | Existing foundation |
| F104 | Route | Route conflict validation | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F105 | Route | Route → adopted Flow resolution | COMPLETE | 100% | P1 | Existing foundation |
| F106 | Route | Gateway in-memory snapshot refresh | COMPLETE | 100% | P2 | Existing foundation |
| F107 | Route | Remove dependency on seed scripts | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F108 | Gateway | Custom hostname handling | COMPLETE | 100% | P2 | Existing foundation |
| F109 | Gateway | Gateway final HTTP response correlation | COMPLETE | 100% | P2 | Existing foundation |
| F356 | Route | Gateway path-parameter routes (`:name`) | COMPLETE | 100% | P1 | M2 — Web product |
| F110 | Invocation | Create FLOW Invocation | COMPLETE | 100% | P2 | Existing foundation |
| F111 | Invocation | Immutable execution snapshot | COMPLETE | 100% | P2 | Existing foundation |
| F112 | Invocation | FLOW / DIRECT_FUNCTION identity | COMPLETE | 100% | P2 | Existing foundation |
| F113 | Invocation | Invocation status lifecycle | COMPLETE | 100% | P2 | Existing foundation |
| F114 | Invocation | StepExecution persistence | COMPLETE | 100% | P2 | Existing foundation |
| F115 | Invocation | Invocation result/error persistence | COMPLETE | 100% | P2 | Existing foundation |
| F116 | Invocation | Direct FunctionVersion invocation service | COMPLETE | 100% | P2 | M1 — Human zero-to-running |
| F117 | Invocation | Direct FunctionVersion invoke API | COMPLETE | 100% | P2 | M1 — Human zero-to-running |
| F118 | Invocation | Get Invocation | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F119 | Invocation | List/filter Invocations | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F120 | Invocation | Step-by-step execution details | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F121 | Invocation | Cancel invocation | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F122 | Invocation | Invocation timeout | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F123 | Invocation | Replay/retry invocation | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F124 | Invocation | Idempotency key for invocation | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F125 | Dispatcher | Ready-event consumer | COMPLETE | 100% | P2 | Existing foundation |
| F126 | Dispatcher | Execution planning | COMPLETE | 100% | P2 | Existing foundation |
| F127 | Dispatcher | FUNCTION execution | COMPLETE | 100% | P2 | Existing foundation |
| F128 | Dispatcher | RESPONSE execution | COMPLETE | 100% | P2 | Existing foundation |
| F129 | Dispatcher | Runtime selection | COMPLETE | 100% | P2 | Existing foundation |
| F130 | Dispatcher | Capacity reserve/release | COMPLETE | 100% | P2 | Existing foundation |
| F131 | Dispatcher | Exceptional completion handling | COMPLETE | 100% | P2 | Existing foundation |
| F132 | Dispatcher | Unsupported step failure | COMPLETE | 100% | P2 | Existing foundation |
| F133 | Dispatcher | Retry/backoff orchestration | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F134 | Dispatcher | Crash recovery / resume | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F135 | Runtime Registry | Runtime registration | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F136 | Runtime Registry | Health/status | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F137 | Runtime Registry | Capacity reporting | COMPLETE | 100% | P2 | Existing foundation |
| F138 | Runtime Registry | Least-in-flight selection | COMPLETE | 100% | P2 | Existing foundation |
| F139 | Runtime Registry | Persistent/distributed registry | COMPLETE | 100% | P3 | Existing foundation |
| F140 | Runtime | Runtime worker lifecycle | COMPLETE | 100% | P2 | Existing foundation |
| F141 | Runtime | IPC/UDS execution | COMPLETE | 100% | P2 | Existing foundation |
| F142 | Runtime | INVOKE/ACCEPTED/RESULT/ERROR protocol | COMPLETE | 100% | P2 | Existing foundation |
| F143 | Runtime | Persistent Node executor | COMPLETE | 100% | P2 | Existing foundation |
| F144 | Runtime | Artifact local cache | COMPLETE | 100% | P2 | Existing foundation |
| F145 | Runtime | Concurrent cache miss handling | COMPLETE | 100% | P2 | Existing foundation |
| F146 | Runtime | Process/function resource boundary | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F147 | Runtime | CPU limit | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F148 | Runtime | Memory limit | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F149 | Runtime | Execution timeout/kill | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F150 | Runtime | Runtime crash recovery | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F151 | Runtime | Runtime warm/cold lifecycle | PARTIAL | 50% | P3 | M1 — Human zero-to-running |
| F152 | Runtime | Python runtime | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F153 | Runtime | Go/Rust compiled runtime | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F154 | Runtime | Custom OCI/Docker runtime | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F155 | Runtime | OCI digest pinning | MISSING | 0% | P3 | M1 — Human zero-to-running |
| F156 | Controlplane API | Wire Controlplane to Web/API layer | PARTIAL | 50% | P0 | M1 — Human zero-to-running |
| F157 | Controlplane API | Function REST API | COMPLETE | 100% | P0 | Existing foundation |
| F158 | Controlplane API | FunctionVersion REST API | COMPLETE | 100% | P0 | Existing foundation |
| F159 | Controlplane API | Source upload REST API | COMPLETE | 100% | P0 | Existing foundation |
| F160 | Controlplane API | Build/deploy REST API | COMPLETE | 100% | P0 | Existing foundation |
| F161 | Controlplane API | Build/status/log REST API | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F162 | Controlplane API | Flow REST API | COMPLETE | 100% | P0 | Existing foundation |
| F163 | Controlplane API | FlowVersion REST API | COMPLETE | 100% | P0 | Existing foundation |
| F164 | Controlplane API | Flow-step management REST API | COMPLETE | 100% | P0 | Existing foundation |
| F165 | Controlplane API | Gateway REST API | COMPLETE | 100% | P0 | Existing foundation |
| F166 | Controlplane API | Route management REST API | COMPLETE | 100% | P0 | Existing foundation |
| F167 | Controlplane API | Invocation REST API | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F168 | Controlplane API | Consistent request/response envelope | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F169 | Controlplane API | Error contract | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F170 | Controlplane API | Pagination/filtering conventions | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F171 | Controlplane API | API versioning strategy | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F172 | Controlplane API | OpenAPI specification | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F173 | Web UI | Controlplane web application wiring | COMPLETE | 100% | P0 | M2 — Web product |
| F174 | Web UI | Function list/create screen | COMPLETE | 100% | P1 | M2 — Web product |
| F175 | Web UI | Function detail | COMPLETE | 100% | P1 | M2 — Web product |
| F176 | Web UI | FunctionVersion create/history | COMPLETE | 100% | P1 | M2 — Web product |
| F177 | Web UI | Source editor/upload UI | COMPLETE | 100% | P1 | M2 — Web product |
| F178 | Web UI | Build/deploy UI | COMPLETE | 100% | P1 | M2 — Web product |
| F179 | Web UI | Build log viewer | PARTIAL | 50% | P1 | M2 — Web product |
| F180 | Web UI | Function test/invoke panel | COMPLETE | 100% | P1 | M2 — Web product |
| F181 | Web UI | Flow list/create | COMPLETE | 100% | P1 | M2 — Web product |
| F182 | Web UI | FlowVersion editor | COMPLETE | 100% | P1 | M2 — Web product |
| F183 | Web UI | Visual Flow builder | PARTIAL | 75% | P1 | M2 — Web product |
| F184 | Web UI | Function selector/version selector | COMPLETE | 100% | P1 | M2 — Web product |
| F185 | Web UI | Step config/input mapping UI | MISSING | 0% | P1 | M2 — Web product |
| F186 | Web UI | Gateway/domain UI | COMPLETE | 100% | P1 | M2 — Web product |
| F187 | Web UI | Route configuration UI | PARTIAL | 50% | P1 | M2 — Web product |
| F188 | Web UI | Invocation/history viewer | MISSING | 0% | P1 | M2 — Web product |
| F189 | Web UI | Logs/error viewer | MISSING | 0% | P1 | M2 — Web product |
| F190 | Web UI | Runtime/health overview | MISSING | 0% | P3 | M2 — Web product |
| F351 | Web UI | Function/FunctionVersion environment variables & secrets UI | COMPLETE | 100% | P1 | M2 — Web product |
| F191 | MCP | MCP server foundation | COMPLETE | 100% | P0 | M3 — Agent/MCP |
| F192 | MCP | Authentication/session mapping | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F193 | MCP | Capability/resource discovery | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F194 | MCP | create_function tool | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F195 | MCP | list/get_function | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F196 | MCP | create_function_version | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F197 | MCP | Source file submission | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F198 | MCP | Source replacement | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F199 | MCP | Deploy FunctionVersion | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F200 | MCP | Get deployment/build status | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F201 | MCP | Get build logs/errors | PARTIAL | 50% | P2 | M3 — Agent/MCP |
| F202 | MCP | Create Flow | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F203 | MCP | Create/edit FlowVersion | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F204 | MCP | Add/modify Flow step | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F205 | MCP | Resolve/select FunctionVersion | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F206 | MCP | Adopt FlowVersion | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F207 | MCP | Create/configure Gateway route | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F208 | MCP | Direct invoke FunctionVersion | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F209 | MCP | Invoke/test Flow | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F210 | MCP | Inspect Invocation | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F211 | MCP | Inspect step/log/error | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F212 | MCP | Iterative code → deploy → test loop | PARTIAL | 50% | P2 | M3 — Agent/MCP |
| F213 | MCP | Tool schemas optimised for coding agents | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F214 | MCP | MCP resources for functions/flows/runtimes | COMPLETE | 100% | P2 | M3 — Agent/MCP |
| F215 | MCP | Self-hosted user-specific MCP connection model | COMPLETE | 100% | P0 | M3 — Agent/MCP |
| F216 | MCP | MCP token/API-key provisioning | PARTIAL | 50% | P2 | M3 — Agent/MCP |
| F217 | MCP | MCP permissions/scopes | PARTIAL | 50% | P2 | M3 — Agent/MCP |
| F218 | CLI | CLI foundation/auth/config | MISSING | 0% | P1 | M4 — CLI |
| F219 | CLI | function create/list/get | MISSING | 0% | P2 | M4 — CLI |
| F220 | CLI | version create | MISSING | 0% | P2 | M4 — CLI |
| F221 | CLI | source push <directory> | MISSING | 0% | P2 | M4 — CLI |
| F222 | CLI | .funcholeignore | MISSING | 0% | P2 | M4 — CLI |
| F223 | CLI | deploy | MISSING | 0% | P2 | M4 — CLI |
| F224 | CLI | deploy/build logs | MISSING | 0% | P2 | M4 — CLI |
| F225 | CLI | direct invoke/test | MISSING | 0% | P2 | M4 — CLI |
| F226 | CLI | Flow create/edit/apply | MISSING | 0% | P2 | M4 — CLI |
| F227 | CLI | Gateway/route commands | MISSING | 0% | P2 | M4 — CLI |
| F228 | CLI | Invocation inspect/logs | MISSING | 0% | P2 | M4 — CLI |
| F229 | CLI | export/import Flow YAML | MISSING | 0% | P3 | M4 — CLI |
| F230 | Auth | User authentication for Controlplane | COMPLETE | 100% | P1 | Existing foundation |
| F231 | Auth | API tokens | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F232 | Auth | MCP tokens | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F233 | Auth | CLI token/login | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F234 | Auth | Token revocation/rotation | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F235 | Authorization | Resource-level authorization | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F236 | Authorization | Roles/permissions | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F237 | Multi-tenancy | Tenant/workspace isolation | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F238 | Secrets | Function environment variables | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F239 | Secrets | Secret values | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F240 | Secrets | Secret storage/encryption | COMPLETE | 100% | P2 | M1 — Human zero-to-running |
| F241 | Secrets | Secret injection into runtime | COMPLETE | 100% | P2 | M1 — Human zero-to-running |
| F242 | Secrets | Version/environment scoped config | COMPLETE | 100% | P3 | M1 — Human zero-to-running |
| F243 | Configuration | Non-secret environment variables | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F244 | Networking | Outbound network policy | MISSING | 0% | P2 | M5 — Product hardening |
| F245 | Networking | Runtime DNS/network access | PARTIAL | 50% | P2 | M5 — Product hardening |
| F352 | Database | Managed Database resource (`context.db()`) | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F353 | Configuration | Flow-shared environment profiles | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F354 | Database | Flow-shared database attachments | COMPLETE | 100% | P1 | M1 — Human zero-to-running |
| F246 | Observability | Invocation structured logs | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F247 | Observability | Function stdout/stderr capture | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F248 | Observability | Logs persisted/queryable | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F249 | Observability | Step-level timing | PARTIAL | 50% | P1 | M1 — Human zero-to-running |
| F250 | Observability | Build logs | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F251 | Observability | Runtime health metrics | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F252 | Observability | Invocation latency metrics | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F253 | Observability | Error rate metrics | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F254 | Observability | Trace/correlation IDs | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F255 | Observability | Gateway → Invocation → Runtime correlation | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F256 | Observability | OpenTelemetry integration | MISSING | 0% | P3 | M5 — Product hardening |
| F257 | Events | Durable command/event contracts | PARTIAL | 50% | P2 | M5 — Production hardening |
| F258 | Events | NATS JetStream stream/consumer configuration | COMPLETE | 100% | P2 | M5 — Production hardening |
| F259 | Events | Event schema/versioning | MISSING | 0% | P2 | M5 — Production hardening |
| F260 | Reliability | DB + event atomicity/outbox | MISSING | 0% | P1 | M5 — Production hardening |
| F261 | Reliability | Gateway terminal-event durability | MISSING | 0% | P1 | M5 — Production hardening |
| F262 | Reliability | Lost/replayed event handling | PARTIAL | 50% | P2 | M5 — Production hardening |
| F263 | Reliability | Consumer idempotency | PARTIAL | 50% | P2 | M5 — Production hardening |
| F264 | Reliability | Dispatcher restart recovery | PARTIAL | 50% | P2 | M5 — Production hardening |
| F265 | Reliability | Runtime failure recovery | MISSING | 0% | P2 | M5 — Production hardening |
| F266 | Reliability | Graceful shutdown/drain | MISSING | 0% | P2 | M5 — Production hardening |
| F267 | Reliability | Orphan Invocation detection | MISSING | 0% | P2 | M5 — Production hardening |
| F268 | Reliability | Stuck PENDING/RUNNING reconciliation | MISSING | 0% | P2 | M5 — Production hardening |
| F269 | Security | Source archive bomb protection | MISSING | 0% | P2 | M5 — Production hardening |
| F270 | Security | Build sandbox/isolation | MISSING | 0% | P2 | M5 — Production hardening |
| F271 | Security | Runtime execution isolation | MISSING | 0% | P2 | M5 — Production hardening |
| F272 | Security | Dependency/network restrictions during build | MISSING | 0% | P3 | M5 — Production hardening |
| F273 | Security | Secret redaction from logs | PARTIAL | 50% | P2 | M5 — Production hardening |
| F274 | Security | Request/body size limits | MISSING | 0% | P2 | M5 — Production hardening |
| F275 | Security | Rate limiting | MISSING | 0% | P2 | M5 — Production hardening |
| F276 | Security | Audit log | MISSING | 0% | P2 | M5 — Production hardening |
| F277 | Resource Mgmt | Per-function CPU configuration | MISSING | 0% | P3 | M5 — Production hardening |
| F278 | Resource Mgmt | Per-function memory configuration | MISSING | 0% | P3 | M5 — Production hardening |
| F279 | Resource Mgmt | Max execution duration | PARTIAL | 50% | P1 | M5 — Production hardening |
| F280 | Resource Mgmt | Concurrency configuration | MISSING | 0% | P3 | M5 — Production hardening |
| F281 | Resource Mgmt | Queue/backpressure | PARTIAL | 50% | P2 | M5 — Production hardening |
| F282 | Resource Mgmt | Runtime autoscaling model | MISSING | 0% | P3 | M5 — Production hardening |
| F283 | Developer UX | First-run onboarding | PARTIAL | 50% | P1 | M5 — Product hardening |
| F284 | Developer UX | Runtime/function examples | PARTIAL | 50% | P1 | M5 — Product hardening |
| F285 | Developer UX | Starter templates | MISSING | 0% | P1 | M5 — Product hardening |
| F286 | Developer UX | Function local test | MISSING | 0% | P3 | M5 — Product hardening |
| F287 | Developer UX | Local → production same source/config workflow | PARTIAL | 50% | P2 | M5 — Product hardening |
| F288 | Developer UX | Error messages actionable for humans | PARTIAL | 50% | P1 | M5 — Product hardening |
| F289 | Agent UX | Structured errors actionable for agents | MISSING | 0% | P2 | M3 — Agent/MCP |
| F290 | Agent UX | Machine-readable lifecycle/status | PARTIAL | 50% | P2 | M3 — Agent/MCP |
| F291 | Agent UX | Discover required next action | MISSING | 0% | P2 | M3 — Agent/MCP |
| F292 | Agent UX | Build/deploy/test feedback loop | MISSING | 0% | P2 | M3 — Agent/MCP |
| F293 | Docs | Architecture documentation | PARTIAL | 50% | P2 | M5 — Product hardening |
| F294 | Docs | Function lifecycle docs | MISSING | 0% | P2 | M5 — Product hardening |
| F295 | Docs | Source package specification | MISSING | 0% | P0 | M5 — Product hardening |
| F296 | Docs | Runtime contract | PARTIAL | 50% | P2 | M5 — Product hardening |
| F297 | Docs | Flow model specification | PARTIAL | 50% | P0 | M5 — Product hardening |
| F298 | Docs | REST API docs | PARTIAL | 50% | P1 | M5 — Product hardening |
| F299 | Docs | MCP usage docs | MISSING | 0% | P2 | M5 — Product hardening |
| F300 | Docs | CLI docs | MISSING | 0% | P2 | M5 — Product hardening |
| F301 | Docs | Self-host deployment docs | PARTIAL | 50% | P2 | M5 — Product hardening |
| F302 | Testing | Empty DB → create Function test | MISSING | 0% | P0 | M1 — Human zero-to-running |
| F303 | Testing | Function → Version → Source test | MISSING | 0% | P0 | M1 — Human zero-to-running |
| F304 | Testing | Source → Build → READY test | INTERNAL_ONLY | 60% | P0 | M1 — Human zero-to-running |
| F305 | Testing | Flow composition through public API test | COMPLETE | 100% | P0 | Existing foundation |
| F306 | Testing | Route creation through public API test | COMPLETE | 100% | P0 | Existing foundation |
| F307 | Testing | Zero-to-HTTP-response E2E without seeds | COMPLETE | 100% | P0 | M1 — Human zero-to-running |
| F308 | Testing | Direct invocation E2E | MISSING | 0% | P1 | M1 — Human zero-to-running |
| F309 | Testing | MCP zero-to-running Function E2E | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F310 | Testing | Failure-path E2E | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F311 | Testing | Restart/recovery tests | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F312 | Platform Ops | Docker Compose complete self-host stack | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F313 | Platform Ops | Database migration startup | COMPLETE | 100% | P2 | Existing foundation |
| F314 | Platform Ops | NATS/RustFS/local dependencies setup | COMPLETE | 100% | P2 | Existing foundation |
| F315 | Platform Ops | Health/readiness endpoints | PARTIAL | 50% | P2 | M1 — Human zero-to-running |
| F316 | Platform Ops | Configuration validation | MISSING | 0% | P2 | M1 — Human zero-to-running |
| F317 | Platform Ops | Backup/restore guidance | MISSING | 0% | P2 | M5 — Production hardening |
| F318 | Platform Ops | Upgrade/migration strategy | MISSING | 0% | P2 | M5 — Production hardening |
| F319 | Platform Ops | Kubernetes deployment | MISSING | 0% | P3 | M5 — Production hardening |
| F320 | Platform Ops | Helm chart | MISSING | 0% | P3 | M5 — Production hardening |
| F321 | Versions | READY FunctionVersion activation | MISSING | 0% | P3 | M6+ — Expansion |
| F322 | Versions | One active version per Function | MISSING | 0% | P3 | M6+ — Expansion |
| F323 | Versions | Rollback | MISSING | 0% | P3 | M6+ — Expansion |
| F324 | Versions | Activation publication revision | MISSING | 0% | P3 | M6+ — Expansion |
| F325 | Traffic | Weighted FunctionVersion selection | MISSING | 0% | P3 | M6+ — Expansion |
| F326 | Traffic | Deterministic A/B routing | MISSING | 0% | P3 | M6+ — Expansion |
| F327 | Traffic | Canary deployment | MISSING | 0% | P3 | M6+ — Expansion |
| F328 | Git Integration | GitHub repository connection | MISSING | 0% | P3 | M6+ — Expansion |
| F329 | Git Integration | Deploy from commit/branch | MISSING | 0% | P3 | M6+ — Expansion |
| F330 | Git Integration | Webhook auto-deploy | MISSING | 0% | P3 | M6+ — Expansion |
| F331 | Git Integration | Commit SHA provenance | MISSING | 0% | P3 | M6+ — Expansion |
| F332 | Import/Export | Flow YAML export | MISSING | 0% | P3 | M6+ — Expansion |
| F333 | Import/Export | Flow YAML import/apply | MISSING | 0% | P3 | M6+ — Expansion |
| F334 | Import/Export | Function/project manifest | MISSING | 0% | P3 | M6+ — Expansion |
| F335 | Business | Usage accounting | MISSING | 0% | P3 | M6+ — Expansion |
| F336 | Business | Invocation/build/runtime metering | MISSING | 0% | P3 | M6+ — Expansion |
| F337 | Business | Quotas | MISSING | 0% | P3 | M6+ — Expansion |
| F338 | Business | Billing hooks | MISSING | 0% | P3 | M6+ — Expansion |
| F339 | Extensibility | Plugin/runtime provider model | MISSING | 0% | P3 | M6+ — Expansion |
| F340 | Extensibility | Webhook/event triggers beyond HTTP | MISSING | 0% | P3 | M6+ — Expansion |
| F341 | Triggers | Scheduled/cron Flow trigger | MISSING | 0% | P3 | M6+ — Expansion |
| F342 | Triggers | Queue/event trigger | MISSING | 0% | P3 | M6+ — Expansion |
| F343 | Triggers | Manual trigger | MISSING | 0% | P3 | M6+ — Expansion |
| F344 | Ecosystem | PHP SDK / package to execute Flow | MISSING | 0% | P3 | M6+ — Expansion |
| F345 | Ecosystem | WordPress remote-function integration | MISSING | 0% | P3 | M6+ — Expansion |
| F346 | Ecosystem | Shopify/app integration path | MISSING | 0% | P3 | M6+ — Expansion |
| F347 | OSS | Contributor development setup | PARTIAL | 50% | P2 | M6+ — Expansion |
| F348 | OSS | Contribution guidelines | PARTIAL | 50% | P2 | M6+ — Expansion |
| F349 | OSS | License/commercial-use policy finalisation | DECISION_REQUIRED | 10% | P2 | M6+ — Expansion |
| F350 | OSS | Stable public contracts/versioning policy | MISSING | 0% | P2 | M6+ — Expansion |

---

## 17. Epic progress dashboard

| Epic | Feature count | Current progress |
|---|---:|---:|
| Agent UX | 4 | 12.5% |
| Artifact | 10 | 70.0% |
| Auth | 5 | 20.0% |
| Authorization | 2 | 25.0% |
| Build | 14 | 48.6% |
| Business | 4 | 0.0% |
| CLI | 12 | 0.0% |
| Certificate | 5 | 70.0% |
| Configuration | 2 | 100.0% |
| Controlplane API | 17 | 67.6% |
| Database | 2 | 100.0% |
| DNS | 1 | 100.0% |
| Developer UX | 6 | 33.3% |
| Dispatcher | 10 | 75.0% |
| Docs | 9 | 27.8% |
| Domain | 2 | 100.0% |
| Ecosystem | 3 | 0.0% |
| Events | 3 | 50.0% |
| Extensibility | 2 | 0.0% |
| Flow | 15 | 56.7% |
| Flow Step | 12 | 41.7% |
| FlowVersion | 3 | 100.0% |
| Function | 7 | 100.0% |
| FunctionVersion | 9 | 66.7% |
| Gateway | 4 | 100.0% |
| Git Integration | 4 | 0.0% |
| Import/Export | 3 | 0.0% |
| Invocation | 15 | 51.3% |
| MCP | 27 | 92.6% |
| Multi-tenancy | 1 | 0.0% |
| Networking | 2 | 25.0% |
| OSS | 4 | 27.5% |
| Observability | 11 | 22.7% |
| Platform Ops | 9 | 33.3% |
| Project Model | 2 | 10.0% |
| Reliability | 9 | 16.7% |
| Resource Mgmt | 6 | 16.7% |
| Route | 9 | 83.3% |
| Runtime | 16 | 43.8% |
| Runtime Catalog | 3 | 0.0% |
| Runtime Registry | 5 | 80.0% |
| Secrets | 5 | 100.0% |
| Security | 8 | 6.2% |
| Source | 15 | 76.7% |
| Testing | 10 | 31.0% |
| Traffic | 3 | 0.0% |
| Triggers | 3 | 0.0% |
| Versions | 4 | 0.0% |
| Web UI | 19 | 72.4% |

---

## 18. Tracking summary

### Overall

- **356 / 356** master backlog candidates are represented in this PRD (F001–F350, plus F351–F356 added during live product-gap work).
- **Current master-backlog implementation score:** **52.8%** (28.7% → 30.4% → 31.4% → 34.1% → 36.0% → 37.0% → 37.1% → 37.4% → 37.8% → 38.1% → 38.7% → 39.7% → 40.4% → 40.8% → 43.4% → 44.4% → 45.0% → 45.2% → 52.8%)
- **Remaining master-backlog scope by score:** **47.2%**
- **First human zero-to-running lifecycle score:** **62.8%**
- **Agent/MCP score:** **92.6%**

> **Note on this update (2026-09-15):** M1 is fully shipped (all 10 stories), and M2 (Controlplane Web) has now started with **STORY-M2-02 (Function workspace)**. Scoping it first surfaced that STORY-M2-01 (Web shell) was already built and working - not by this session, but verified live (login, session, typed API client, nav) - so the story list reflects that as already done rather than reopening it. The Function workspace itself (`/functions`, `/functions/[id]`, `/functions/[id]/versions/[id]`) is now built and live-verified end-to-end: create Function → draft version → source editor (upload or paste-and-edit) → deploy to a real READY artifact → direct test invocation → inspect the resulting invocation via STORY-M1-09's endpoint - reusing the shell's existing design system/components exactly, in both light and dark mode. This closed **F173–F178** and, as a necessary side effect, **F116/F117** (direct invocation had a tested service but no REST transport until now) via a new `POST /api/v1/functions/{functionId}/versions/{versionId}/invoke`. **F179** (build log viewer) is only PARTIAL - deploy failures show their build error transiently in the session that triggered them, nothing persisted/queryable, matching the backend's own F044/F045 gap. Two real bugs were found and fixed live, not part of any story's original scope: **GAP-16** (`InvocationInspectionAccessService.inspect()` threw `LazyInitializationException` on a real HTTP request despite its own test suite passing, since those tests are `@Transactional` and a real request isn't - fixed by adding the same annotation), and a frontend source-editor race (it decided whether to show itself before its initial fetch had resolved, so it always opened at least once regardless of whether source already existed - fixed by gating that decision on the fetch settling).
>
> **Correction and fixes (2026-09-16):** STORY-M2-02's own update was wrong in two directions, both found live by the user testing the shipped feature, not by this session's own review - and then fixed in the same session, not just documented. (1) **GAP-17**: the Test invoke panel (F180) created a durable Invocation that never executed. Root cause had two layers. First, `InvocationHandoffConfig` (invocation module) wired the direct-invoke path to a `NoopInvocationEventPublisher`; now wires a real `NatsJetStreamInvocationEventPublisher`, lazily (`@Lazy` Spring beans) so the NATS connection is only opened the first time a direct invocation is actually created, not at every Spring context startup - keeps the rest of controlplane's test suite untouched. Second, once that was fixed, the Dispatcher's own `InvocationDispatcher.onStepTerminal()` only marked an Invocation COMPLETED when its terminal step was RESPONSE-typed; a direct invocation's single synthetic step is FUNCTION-typed, so the step executed and completed but the parent Invocation stayed PENDING forever regardless, a second, previously-undiscovered layer of the same bug. Fixed by also treating any completed step of a `DIRECT_FUNCTION`-kind invocation as terminal. F180 restored to COMPLETE. (2) An audit of F181–F190 (Flow/Gateway/domain UI), prompted by the user separately hitting a raw-UUID step editor, found the original "not audited, out of scope" note undersold what already existed: the Flow list (**F181**), Flow/step editor, and visual step canvas (**F183**), plus Gateway and Domain CRUD (**F186**), were already substantially built pre-session - now COMPLETE or PARTIAL instead of MISSING. What the audit did confirm genuinely missing was **F184**: no Function/FunctionVersion (or Flow/FlowVersion, for SUB_FLOW steps) picker in the Flow step editor, just raw UUID text boxes - fixed with a `ComponentPicker` (name-based dropdowns, filtered to READY FunctionVersions / ADOPTED FlowVersions), F184 now COMPLETE, F182 raised to COMPLETE alongside it. (3) **GAP-18**, found the same way: the Function workspace had zero UI for environment variables/secrets despite a fully complete backend (F238–F243) - built an "Environment & secrets" panel (env vars show their value; secrets show only their opaque `secretRef`, never a plaintext value), tracked as new feature **F351** since no F-ID existed for it at all. All three fixes were live-verified end-to-end against the real running dev stack (real NATS, real Dispatcher, real Postgres) - not mocked. At that point, the master backlog score and status counts were recalculated from the F001–F350(+F351) table; the current 2026-09-20 recalculation supersedes those older percentages.
>
> **New feature (2026-09-16): managed Database resource, F352.** Prompted by a design discussion about shared/warm DB connections across Functions (opening a connection per invocation doesn't scale, and multiple Functions often need the same connection), built a first-class `Database` resource - scoped and confirmed with the user before implementation (all four engines - Postgres/Supabase/MongoDB/MySQL - are the intended eventual scope; **Postgres, external connections only** is what's actually built now). FuncHole owns the connection, not the function author: a `Database` (top-level resource, like `Function`) stores host/port/credentials with the password held only in OpenBao; a FunctionVersion attaches zero or more Databases via a new many-to-many join table; at invocation time the Dispatcher resolves attached databases (password included, read back from OpenBao) and threads them through the existing IPC chain exactly like `environment` already is (each of the three module boundaries - dispatcher, runtime, and the final Node stdin JSON - independently declaring the same `databases` field, no shared Java type, matching this codebase's established IPC pattern). The Node executor exposes a non-breaking `handler(input, context)` second argument, where `context.db(name)` returns a `pg.Pool` cached for the life of the warm Node process - so repeated invocations of the same (or a different) Function reuse the same pool rather than reconnecting. Shipped with a `/databases` CRUD page and a FunctionVersion attachment panel in the web UI, mirroring existing pages/panels exactly. Live-verified end-to-end against the real dev stack, through both `curl` and the browser: created a Database pointed at the dev Postgres container, attached it to a real FunctionVersion, deployed, and invoked a function whose handler ran a real SQL query through `context.db(...)` - confirmed the correct result and pool reuse across repeated invocations. Tracked as **F352** under EPIC-18 (Configuration, Secrets & Networking), which had no existing F-ID for this capability.
>
> **Progress update (2026-09-20):** Re-audited recent commits through `b14d2b0` and updated the backlog for four newly shipped capabilities that were not represented by prior feature IDs: Flow-shared environment profiles (F353), Flow-shared database attachments (F354), STATIC-runtime frontend deployment (F355), and Gateway path-parameter routes (F356). MCP was also recalculated from the actual `controlplane/mcp` tool surface instead of the old placeholder status, moving EPIC-15 from effectively missing to mostly complete. Remaining MCP gaps are now focused on dedicated token provisioning, fine-grained permission scopes, persisted build logs, and a first-class iterative workflow wrapper; the core tools are present.

### What the percentage does *not* mean

It does not mean `28.7%` of engineering hours are spent.

It means that, under the explicit status-to-score model, the current repository has completed that proportion of the **planned capability surface**.

A future planning pass may add estimates/story points. If estimates are added, maintain two independent metrics:

1. **Capability completeness** — this document's current metric.
2. **Effort burn-up** — completed story points / total story points.

Do not replace one with the other.

---

## 19. Immediate next planning actions

Before adding more execution features:

1. [ ] Accept/revise F001/F002 ownership model.
2. [ ] Decide F019/F020 canonical source contract.
3. [ ] Create implementation issues for STORY-M1-01 through STORY-M1-10.
4. [ ] Make each issue reference its F-IDs.
5. [ ] Do not mark INTERNAL_ONLY items COMPLETE merely after DI wiring; they become COMPLETE only when their product path is usable.
6. [ ] Add the seed-free E2E as the M1 release gate.
7. [ ] Start M2/M3 work only after shared M1 application contracts stabilize.

---

## 20. Risks that must stay visible

### R-01 — Two-halves risk
~~Flow execution and Function build/deploy can continue evolving independently and remain disconnected.~~ **RESOLVED 2026-09-14** — STORY-M1-06 joined the two halves: FlowStep now validates real Function/FunctionVersion (FUNCTION/RESPONSE/MIDDLEWARE) and real Flow/FlowVersion (SUB_FLOW) references, live-verified end-to-end.  
**Mitigation:** F067/F068 and STORY-M1-06 are release blockers. *(done)*

### R-02 — Demo-success illusion
~~Seeded artifacts can make execution look product-complete.~~ **RESOLVED 2026-09-14** — STORY-M1-10's `ZeroToHttpResponseE2ETest` proves the full lifecycle from empty application data, with zero seeds of any kind.  
**Mitigation:** F307 must be seed-free. *(done)*

### R-03 — Adapter duplication
REST, MCP and CLI can accidentally reimplement business logic.  
**Mitigation:** all adapters call the same Controlplane application use-cases.

### R-04 — Source-contract churn
Implementing REST/CLI/MCP source upload before F019/F020 are decided can create three incompatible source formats.  
**Mitigation:** canonical source spec first.

### R-05 — Agent ergonomics afterthought
Building MCP only as thin HTTP wrappers may expose poor schemas/errors.  
**Mitigation:** F213, F289–F292 are explicit stories.

### R-06 — Production code execution risk
Untrusted builds/functions without sandbox/resource/secrets controls are unsafe for multi-user hosting.  
**Mitigation:** M5 is required before production multi-tenant claims.

---

## 21. Architecture decisions already considered stable

Unless a concrete defect appears, do not reopen these during M1:

- `invocation-contract` isolates Controlplane direct-invocation contracts from Invocation implementation.
- Flow Invocation captures immutable execution state.
- exact FunctionVersion identity is pinned for direct invocation.
- remote S3-compatible object storage is artifact source of truth.
- runtime filesystem is disposable cache.
- Node execution uses persistent runtime process + IPC.
- NATS/JetStream is distributed coordination; IPC is local hot path.
- Gateway is production ingress; Controlplane is developer/agent command plane.
- Human REST/Web, CLI and MCP are peers over the same application semantics.

---

## 22. Final release criteria

### First useful product
FuncHole is not considered product-complete merely because `/orders` returns 200.

It reaches the first useful-product milestone when:

> A new developer on a fresh FuncHole installation can create a Function, create a FunctionVersion, submit real source, build/deploy it, compose that exact READY FunctionVersion into a Flow, expose it through a Gateway route, send an HTTP request, receive the Runtime result, and inspect the Invocation — without seeds, SQL, direct S3/filesystem manipulation, or internal JVM calls.

### Agent-ready product
FuncHole is agent-ready when the same lifecycle can be completed and debugged over MCP.

### Production-ready product
FuncHole is production-ready only after isolation, secrets, authz, logs/metrics, failure recovery and event/state reliability gates are met.

---

## 23. Change-management rules for this PRD

When a feature changes:

1. Update the corresponding F-ID status.
2. Update the relevant story/task checkbox.
3. Recalculate capability progress.
4. Link the implementing commit/PR beside the F-ID in a future `Evidence` column.
5. Do not delete deferred features; move milestone/priority instead.
6. New features get new IDs after F350; never recycle old IDs.
7. Every Codex implementation prompt should reference:
   - Story ID;
   - affected F-IDs;
   - prerequisites;
   - explicit Do-Not-Implement scope;
   - acceptance tests.

This keeps future development traceable and prevents the project from jumping ahead of its actual lifecycle.
