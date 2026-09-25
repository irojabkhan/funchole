# FuncHole Control Plane Web - Design PRD

**Status:** Draft for review  
**Date:** 2026-09-25  
**Scope:** `backend/control-plane-web` redesign direction  
**Inspiration source:** `backend/landing` brand, typography, color, agent-native concept  

---

## 1. CDO Roast

The current Control Plane Web is useful, but it does not yet feel like FuncHole.

It feels like a generated admin panel wearing a FuncHole logo. The backend has become a serious agent-native platform, but the UI still speaks in CRUD nouns: Functions, Flows, Gateways, Domains, Databases. That is accurate, but it is not emotionally or operationally helpful.

The landing page promises:

> Describe it. Your agent ships it.

The app currently answers:

> Here are tables. Good luck remembering the lifecycle.

That mismatch is the biggest design problem.

### 1.1 Brand Roast

- The landing page has a clear identity: near-black ground, warm amber accent, IBM Plex Sans, JetBrains Mono, terminal chrome, agent command energy.
- The app uses generic slate/cyan/Geist defaults, so the product feels split in two.
- The app logo is a cyan square with `F`; the landing logo is a precise ring/dot mark with amber. They do not feel related.
- Buttons use cyan as the main action color, while the public brand uses amber as the signature action color.
- Panels are flat bordered rectangles. They organize content, but they do not create a memorable product surface.
- The dashboard has no terminal, agent, execution, or gateway atmosphere even though those are the product's strongest differentiators.

### 1.2 UX Roast

- The UI exposes database structure more than user intent.
- The overview page is four count cards. That is not an overview; it is a census.
- There is no "what should I do next?" guidance for a fresh user.
- The golden lifecycle is real, but the UI does not present it as a journey:
  `Create Function -> Source -> Configure -> Deploy -> Flow -> Route -> Invoke -> Inspect`.
- Critical states are scattered across pages. A FunctionVersion status, Flow adoption status, Gateway host, route, environment inheritance, and invocation result are not connected visually.
- Empty states are too quiet. "No functions yet" does not teach the product.
- Errors are technically present but not designed as recovery experiences.
- Destructive actions use native `window.confirm`, which feels cheap and risky for a serious platform.
- Inline create forms work for speed, but they make pages jump and blur focus.
- Tables dominate every page, even where cards, timelines, route previews, or execution maps would communicate better.
- The Flow builder is powerful, but it looks like an embedded tool inside an admin app instead of the main creative surface.
- The MCP API Keys page is the best page conceptually because it gives ready-to-run commands and connects directly to agent workflows. The rest of the app should learn from it.

### 1.3 Page-by-Page Roast

| Page | Current Problem | Impact |
|---|---|---|
| Login | Brand mismatch, generic card, no product promise | First impression does not match landing |
| Overview | Count cards only, no lifecycle, no recent activity | User does not know what to do next |
| Functions list | CRUD table first, no deploy/test readiness signal | Function lifecycle feels bureaucratic |
| Function detail | Version table is useful but not outcome-oriented | User has to infer which version matters |
| FunctionVersion detail | Source, config, DB, test, artifact are stacked but not staged | User has to discover the correct order |
| Flows list | Route exists but does not feel like a live endpoint | Route/product value is underplayed |
| Flow detail | Active version, inherited config, databases, route are separate islands | Flow does not feel like one deployable unit |
| FlowVersion editor | Strongest feature, weakest surrounding guidance | Users can build, but not confidently ship |
| Gateways | Host and certificate are table columns, not operational cards | Gateway feels like metadata, not ingress |
| Domains | Verification code is shown, but DNS instructions are minimal | Verification remains a technical chore |
| Databases | Functional but generic, reveal password action is too casual | Secrets/security need stronger ceremony |
| Environments | Good concept, but split panel feels hidden and cramped | Shared config does not feel first-class |
| MCP API Keys | Best page, because it gives agent commands | This should become the design benchmark |

### 1.4 Accessibility and Interaction Roast

- Focus styling exists indirectly, but the design language does not make keyboard flow obvious.
- Some clickable table rows and inline controls depend too much on hover.
- Icon-only destructive actions need stronger labels, confirmation modals, and safer affordances.
- Loading states are mostly text placeholders instead of skeletons or progress states.
- Mobile behavior is not designed as a first-class experience. A fixed icon rail and wide tables will struggle on smaller screens.
- There is no visible reduced-motion strategy yet for future motion.

---

## 2. Product Design Thesis

The Control Plane Web should become an **Agent Operations Cockpit**.

It is not the primary building surface. The primary building surface is the user's coding agent through MCP.

The dashboard exists for:

- first-time MCP connection and onboarding,
- core setup that agents need before they can work safely,
- manual triggers for actions that should not always be agent-autonomous,
- source review and small manual edits when an agent is not appropriate,
- logs, invocation inspection, and operational debugging,
- credential, database, gateway, domain, and environment management,
- confidence checks before and after agent-driven changes.

So the product should not ask users to "use the dashboard to build everything." It should help users connect an agent quickly, then provide the manual controls and visibility they need when automation needs human steering.

The app should feel like:

- an operations console,
- an agent command center,
- a manual override console,
- a source/code review surface,
- and a debugging surface.

It should not feel like:

- a CRUD scaffold,
- a cloud billing dashboard,
- a database admin panel,
- or a generic Vercel clone.

---

## 3. Design Principles

### 3.1 Agent-First, Human-Steerable

Every page should answer:

1. What can my agent do here?
2. What can I safely do manually?
3. What is the next best action?

For new users, the first next action is almost always:

`Create MCP key -> connect coding agent -> verify tools are available`.

Dashboard features should support that path before they promote manual resource creation.

### 3.1.1 Manual Work Is the Exception, Not the Default

Manual UI should focus on actions where a coding agent may be inappropriate or risky:

- creating/revoking MCP keys,
- revealing or rotating sensitive credentials,
- inspecting source before deploy/adoption,
- making small source/config patches,
- manually triggering deploy/test/adopt,
- checking logs and invocation results,
- validating domain/gateway/certificate setup,
- attaching shared environments/databases,
- recovering from failed agent actions.

### 3.2 Lifecycle Over Tables

Primary UX should follow lifecycle progression:

`Author -> Configure -> Deploy -> Route -> Invoke -> Inspect`

Tables can remain for dense browsing, but they should not be the only structure.

### 3.3 Live Endpoint Mental Model

A Flow is not just a row. It is a route that can serve a request.

Every Flow surface should make the live endpoint visible:

`GET https://gw.domain/path`

### 3.4 Immutable Version Confidence

Versions are core to FuncHole. The UI should make immutability feel safe, not confusing.

Use language like:

- Draft
- Ready
- Adopted
- Archived
- Published artifact
- Exact pinned version

### 3.5 Debugging Is a First-Class Product

Invocation results, logs, step status, runtime handoff, and output should feel like a core experience, not an afterthought.

### 3.6 Brand Continuity

The app should inherit the landing page's soul:

- near-black environment,
- amber primary action,
- muted neutral surfaces,
- mono command details,
- terminal-inspired panels,
- precise developer typography.

---

## 4. Visual Direction

### 4.1 Theme Name

**Blackbox Cockpit**

### 4.2 Palette

Use the landing palette as the base:

| Token | Value | Usage |
|---|---:|---|
| `--bg` | `#0A0A0C` | App background |
| `--surface` | `#131316` | Main panels |
| `--surface-2` | `#1B1B1F` | Raised panels, editors |
| `--border` | `rgba(255,255,255,0.08)` | Default border |
| `--border-strong` | `rgba(255,255,255,0.16)` | Active border |
| `--text-primary` | `#F5F5F0` | Main text |
| `--text-secondary` | `#A3A19B` | Secondary text |
| `--text-tertiary` | `#6E6C67` | Meta text |
| `--accent` | `#F5A623` | Primary action, live route, focus |
| `--accent-hover` | `#FFBD47` | Primary hover |
| `--success` | `#4ADE80` | Ready, verified, completed |
| `--danger` | `#FB7185` | Failed, delete, rejected |
| `--info` | `#38BDF8` | Building, publishing, neutral info |
| `--violet` | `#A78BFA` | Flow/sub-flow accent |

Do not use cyan as the main brand color. Cyan can remain a secondary technical color.

### 4.3 Typography

Match the landing page:

- Display/body: `IBM Plex Sans`
- Code/meta: `JetBrains Mono`

Usage:

- Page titles: IBM Plex Sans, 600-700, tight tracking.
- Section labels: JetBrains Mono, uppercase, small.
- IDs, keys, routes, commands: JetBrains Mono.
- Body copy: IBM Plex Sans, 1.5-1.65 line height.

### 4.4 Shape and Texture

- Keep radius moderate: 8px, 12px, 18px.
- Add subtle noise overlay or gradient texture so large dark areas do not feel flat.
- Use borders more than heavy shadows.
- Reserve glow only for active/live states.

### 4.5 Motion

Motion should communicate system state:

- page entry reveal,
- command copied,
- deployment progressing,
- invocation polling,
- flow step selected,
- status transition.

Respect `prefers-reduced-motion`.

### 4.6 Motion Language

FuncHole motion should feel like a live system, not a marketing animation dumped into a dashboard.

Motion goals:

- show that the agent is connected,
- show work moving through MCP, Gateway, Dispatcher, Runtime, and Artifact,
- show whether a manual action is safe, running, done, or failed,
- guide attention to the next step,
- make empty states educational.

Motion rules:

- Use motion to explain cause/effect.
- Use `transform` and `opacity`, not layout-shifting width/height animations.
- Keep micro-interactions between 150-240ms.
- Keep system/progress loops subtle and slow enough to avoid anxiety.
- Pause or simplify all decorative loops under `prefers-reduced-motion`.
- Never animate critical text so aggressively that it becomes hard to read.
- Never hide status changes behind animation delay.

Recommended timing:

| Motion | Duration | Easing | Usage |
|---|---:|---|---|
| Hover lift | 160ms | `cubic-bezier(0.16, 1, 0.3, 1)` | cards, buttons |
| Panel reveal | 220ms | ease-out | page and drawer entry |
| Status transition | 180ms | ease-out | badge changes |
| Copy success | 900ms total | pulse/fade | copied command/key/url |
| Agent activity loop | 1.6s-2.4s | linear/soft | waiting for first MCP connection |
| Invocation trace pulse | 1.2s | linear | running step |
| Route packet travel | 2s-3s | linear | live route SVG |

### 4.7 SVG Language

The app needs a small SVG illustration system. These should be product diagrams, not random abstract blobs.

Style:

- stroked line art,
- amber active paths,
- muted gray inactive paths,
- small dot/circle packets,
- terminal-like labels,
- mono text inside diagrams,
- dark transparent panels,
- subtle glow only for active/live pieces.

SVG assets/components to create:

| SVG Component | Where Used | What It Communicates |
|---|---|---|
| `AgentToMcpDiagram` | Overview, Agent Setup | Coding agent connects to FuncHole MCP |
| `RequestFlowDiagram` | Overview, Flow detail | Client -> Gateway -> Flow -> Dispatcher -> Runtime |
| `FunctionLifecycleDiagram` | FunctionVersion workspace | Source -> Artifact -> READY -> Test |
| `GatewayRouteDiagram` | Gateway/Flow pages | Host + path routes to adopted Flow |
| `EmptyFunctionIllustration` | Empty Functions state | Agent can create the first function |
| `EmptyFlowIllustration` | Empty Flows state | A route needs an adopted Flow |
| `NoLogsIllustration` | Invocation/log empty state | Logs appear after execution |
| `SourceReviewIllustration` | Source workspace empty state | Review generated code before manual edits |

Animation ideas:

- `AgentToMcpDiagram`: a small amber packet moves from agent terminal to MCP endpoint; once connected, packet becomes a steady green dot.
- `RequestFlowDiagram`: packet travels from client to gateway, then through flow steps, then to runtime/artifact.
- `FunctionLifecycleDiagram`: source file icon compresses into artifact block; artifact becomes READY.
- `GatewayRouteDiagram`: hostname/path text locks into a route line with active certificate shield.
- Empty-state illustrations should animate once on entry, then stop.

Do not use GIFs. Use inline SVG React components so color tokens, reduced-motion, and theme variants remain controllable.

---

## 5. Information Architecture

### 5.1 Proposed Navigation

Current nav is resource-based. Keep resources, but reorganize around product work.

Primary nav:

| Group | Items |
|---|---|
| Ship | Overview, Functions, Flows |
| Operate | Invocations, Gateways, Domains |
| Configure | Environments, Databases, MCP Keys |

Near-term routes can stay the same. The sidebar can visually group them without changing URLs.

### 5.2 Missing First-Class Pages

Add these when backend/API support is ready or already exists:

| Page | Purpose |
|---|---|
| Invocations | History, status, logs, replay/debug entry |
| Runtime Health | Runtime registry, capacity, worker state |
| Agent Setup | MCP key flow, commands, docs, test connection |

MCP API Keys can evolve into Agent Setup.

---

## 6. New Experience Model

### 6.1 Overview Page

Replace count cards with an agent onboarding cockpit.

Sections:

1. **MCP Connection Hero**
   - "Connect your coding agent first"
   - Shows whether an MCP key exists
   - Shows whether any key was used recently
   - Primary CTA: "Create MCP key"
   - Secondary CTA: "Copy agent command"
   - Tertiary CTA: "Open manual controls"

2. **Launch Checklist**
   - MCP key created
   - Agent connected
   - Domain verified
   - Gateway active
   - Function deployed
   - Flow adopted
   - Route live

3. **Live Routes**
   - Cards for active flows:
     `GET gw.domain/path`
   - Status, last invocation, copied URL

4. **Recent Invocations**
   - Latest request status
   - Duration, flow, result state

5. **System Readiness**
   - Gateway registry
   - Runtime capacity
   - Artifact storage
   - NATS/JetStream health, when available

6. **Manual Work Queue**
   - failed deploys needing human review
   - failed invocations needing log inspection
   - functions with source changes but no READY version
   - flows with draft versions not adopted

### 6.2 Functions

Functions should be presented as agent-created capabilities that humans can inspect, patch, deploy, and test when needed.

List card fields:

- name and key,
- runtime,
- latest draft,
- latest ready version,
- linked flows,
- last test result,
- source health: missing / submitted / editable / deployed,
- primary action: "Review source" or "Open workspace".

Function detail should show:

- function identity,
- version timeline,
- latest READY version,
- next suggested action.

FunctionVersion detail should become a guided workspace:

1. Source Files
2. Review Diff / Manual Edit
3. Environment
4. Databases
5. Deploy
6. Test
7. Artifact

Use a horizontal progress rail at the top.

#### Configuration Attachment Manager

Attaching databases and environments to Functions or Flows must become a managed experience, not a raw select box bolted onto a panel.

Current problem:

- Function-level config, Flow-level inherited config, and database attachments are visually disconnected.
- Users cannot easily understand which values come from the FunctionVersion and which are inherited from the Flow.
- Attach/detach actions are too casual for credentials and shared runtime resources.
- There is no clear conflict/override story in the UI.

Target model:

- FunctionVersion workspace shows **Effective Runtime Context**.
- Flow detail shows **Shared Runtime Context**.
- The UI clearly separates:
  - direct FunctionVersion attachments,
  - inherited Flow attachments,
  - resolved/effective runtime values,
  - conflicts or overrides.

Requirements:

- one attachment manager pattern reused for FunctionVersion and Flow,
- tabs or sections for `Environment Profiles`, `Secrets`, and `Databases`,
- show inherited badges: `Inherited from Flow`, `Direct`, `Overridden`,
- show attachment scope: FunctionVersion-only vs whole Flow,
- show which steps/functions receive each attachment,
- warn before detaching a resource used by a live/adopted Flow,
- prevent casual password reveal; require explicit confirmation,
- support copy for `context.db("name")` usage examples,
- support empty states that explain where to attach config:
  - "Attach to Flow when every step needs it"
  - "Attach to FunctionVersion when only this function needs it"
- use side drawer/modal for attach flow, not inline row clutter.

This is one of the most important manual dashboard jobs because coding agents should not blindly manage production secrets without human review.

#### Invoke/Test Console

Invoke test must become a proper console, not just a small panel with a JSON box and a polling result.

Current problem:

- Function test and Flow test are separate implementations with similar UX.
- Users do not clearly see whether they are testing a direct FunctionVersion, a FlowVersion, or the public Gateway route.
- PENDING/COMPLETED state is not visually strong enough.
- Invocation output, logs, and step trace are cramped under the form.

Target model:

- unified `InvokeConsole` component for FunctionVersion, FlowVersion, and eventually Gateway route tests,
- clear mode selector:
  - `Direct Function`
  - `Direct Flow`
  - `Gateway Route`
- request payload editor,
- run button with live running state,
- invocation id and status,
- step trace,
- result body,
- logs,
- error recovery guidance.

Requirements:

- JSON editor with validation and example payload presets,
- "Run test" creates invocation and immediately opens live result panel,
- polling state shown as a timeline, not hidden text,
- clear distinction between:
  - invocation accepted,
  - queued/pending,
  - running step,
  - completed,
  - failed,
- copy invocation id,
- copy result,
- re-run with same payload,
- "open full logs" action,
- show exact execution path:
  - Direct Function: FunctionVersion -> Dispatcher -> Runtime
  - Direct Flow: FlowVersion -> Steps -> Dispatcher -> Runtime
  - Gateway Route: Host/Path -> Gateway -> Invocation -> Dispatcher -> Runtime

Invoke/test is a manual trigger surface. It must be safe, visible, and inspectable.

#### Logs and Trace Console

Logs must become a real product surface.

Current problem:

- Logs are nested under a test result.
- There is no dedicated log viewer.
- Step output is hard to scan.
- stdout/stderr distinction exists but is visually minimal.
- Users cannot quickly understand what failed and where.

Target model:

- reusable `LogTraceConsole` for direct invocation, Flow invocation, and future invocation history page,
- timeline-first display:
  - invocation created,
  - step ready,
  - runtime selected,
  - execution started,
  - stdout/stderr,
  - result/error,
  - completed/failed,
- logs grouped by step,
- filters for all/stdout/stderr/errors,
- search within logs,
- copy log block,
- collapse/expand steps,
- show duration when backend provides it,
- show "no logs yet" as a live pending state, not a dead empty state.

Logs should feel like a terminal trace, matching the landing page's terminal motif.

#### Source File Workspace

The current source panel is not enough. Users need a real source-file review/editing experience because this is one of the most important manual dashboard jobs.

Requirements:

- file tree sidebar, not only file chips,
- read-only mode for non-DRAFT versions,
- editable mode for DRAFT versions,
- clear "agent generated" vs "manually edited" context when metadata exists,
- multi-file code editor,
- entrypoint and handler controls near the file tree,
- source search,
- copy file path and copy file content,
- dirty-state indicator,
- save/submit source action,
- optional "create new draft from this version" CTA when viewing READY/FAILED versions,
- build/deploy errors shown next to the relevant source workflow,
- no pretending old source is editable if backend cannot read the content for that version.

Longer-term:

- source diff between versions,
- inline comments/notes for human review,
- "ask agent to fix this" handoff once agent workflow support is ready.

### 6.3 Flows

Flows are live route contracts.

Flow list should emphasize:

- method + route,
- gateway host,
- active/adopted version,
- last invocation,
- whether it is publicly callable.

Flow detail should become the route control room:

- route card at top,
- active version card,
- managed shared runtime context for environments/databases,
- version timeline,
- live test action.

FlowVersion editor should become full-screen and cockpit-like:

- left: component palette,
- center: visual graph,
- right: inspector,
- bottom or drawer: test output/logs.

The current implementation is close structurally. It needs stronger visual hierarchy, better empty states, and better distinction between "draft authoring" and "adopted read-only operation".

### 6.4 Gateways and Domains

Gateways are ingress, not CRUD.

Gateway cards should show:

- full hostname,
- certificate status,
- linked flows count,
- live/inactive state,
- copy URL action,
- local/dev hint when domain is `.test`.

Domain verification should become a guided DNS checklist:

1. Add TXT record name
2. Add TXT record value
3. Verify
4. Troubleshooting hints

### 6.5 Environments and Databases

These should feel like shared runtime resources.

Environment page:

- card grid for profiles,
- selected profile side panel,
- variable/secret sections,
- "Attached to flows" list.
- usage map showing which Flows and FunctionVersions depend on it.

Database page:

- connection cards,
- engine badge,
- SSL/security status,
- attached flows/functions,
- test connection when backend supports it.
- usage map showing inherited vs direct attachments.

Password reveal should use a stronger confirmation modal and timeout/copy affordance.

### 6.6 MCP Keys / Agent Setup

This is the most important first-run experience.

Newbie users should be guided here before they are expected to understand Functions, Flows, Gateways, or Environments.

Revamp into "Agent Setup":

- create key,
- choose agent: Claude Code, Codex, opencode,
- copy command,
- test connection,
- show available tools,
- explain what the agent can do,
- explain what still requires manual dashboard review,
- show last used time,
- revoke key.

Use terminal-card visuals like the landing page.

First-run sequence:

1. User lands on dashboard.
2. Dashboard asks them to create an MCP key.
3. User picks their coding agent.
4. UI gives one exact command.
5. User copies command.
6. UI shows a "waiting for first connection" state.
7. When the key is used, dashboard marks agent as connected.
8. UI recommends the first agent prompt.

Example first prompt:

```text
Create a simple hello-world function in FuncHole, deploy it, create a Flow, and expose it through my gateway.
```

---

## 7. Component System

### 7.1 Core Components

Build these before page redesign:

| Component | Purpose |
|---|---|
| `AppShell` | grouped nav, top status, profile |
| `BrandMark` | landing-consistent ring/dot logo |
| `PageHero` | title, intent, primary CTA, secondary CTA |
| `CommandCard` | terminal-style command/copy card |
| `AgentConnectWizard` | first-run MCP connection flow |
| `ConnectionStatusCard` | MCP key created/last used/tool readiness |
| `MotionGlyph` | reusable animated SVG packet/dot/trace primitives |
| `AgentToMcpDiagram` | first-run MCP connection diagram |
| `RequestFlowDiagram` | animated request path from Gateway to Runtime |
| `FunctionLifecycleDiagram` | source-to-artifact lifecycle visual |
| `GatewayRouteDiagram` | host/path/certificate route visual |
| `RouteCard` | method, host, path, status, copy/open |
| `LifecycleRail` | staged progress for FunctionVersion/FlowVersion |
| `SourceFileExplorer` | source file tree, active file, dirty state |
| `SourceEditorWorkspace` | code editor shell for review/manual edits |
| `AttachmentManager` | managed attach/detach for environments and databases |
| `EffectiveRuntimeContext` | shows direct + inherited env/secrets/databases |
| `InvokeConsole` | unified Function/Flow/Gateway test invocation surface |
| `LogTraceConsole` | structured logs, step trace, stdout/stderr, result/error |
| `ManualActionCard` | highlights manual triggers like deploy/test/adopt |
| `ResourceCard` | replacement for table-first resource pages |
| `StatusPill` | stronger status semantics |
| `DangerDialog` | replaces `window.confirm` |
| `EmptyState` | guided empty states with CTAs |
| `InlineError` | recovery-focused errors |
| `InvocationTrace` | logs/result/step timeline |
| `SecretValueField` | secret ceremony and copy/reveal behavior |

### 7.2 Tables

Tables remain useful for dense data, but should be secondary.

Rules:

- Use cards for primary browsing when total items are low.
- Use tables for history, logs, and dense audit data.
- Every table needs an empty state, loading skeleton, row action labels, and mobile fallback.

### 7.3 Status System

Unify statuses:

| Category | Statuses |
|---|---|
| Drafting | DRAFT |
| Progress | PENDING, PUBLISHING, RUNNING |
| Good | READY, ACTIVE, VERIFIED, ADOPTED, COMPLETED |
| Bad | FAILED, REJECTED, EXPIRED |
| Inactive | ARCHIVED, INACTIVE, REVOKED |

Statuses should include:

- color,
- icon/dot,
- one-line explanation,
- next action when relevant.

### 7.4 Animation Components

Build animation as reusable primitives instead of one-off CSS snippets.

Required primitives:

| Primitive | Purpose |
|---|---|
| `AnimatedPacket` | Dot moving along an SVG path |
| `PulseDot` | Running/connected/waiting state |
| `TraceLine` | Active route or execution path |
| `RevealGroup` | Staggered page/card reveal |
| `CopyPulse` | Copy success feedback |
| `StatusTransition` | Smooth status badge changes |

Implementation notes:

- Prefer CSS keyframes and SVG stroke-dash animations.
- Do not introduce a heavy animation library in Phase 1 unless CSS proves insufficient.
- Keep animation tokens in CSS variables: duration, easing, glow, packet color.
- Provide reduced-motion variants that show static active states.

---

## 8. Interaction Requirements

### 8.1 Empty States

Every empty state must include:

- what this resource is,
- why it matters,
- primary action,
- dependency hint if blocked.

Example:

> No flows yet. A Flow maps a gateway route to executable steps. Create one after you have a Gateway and at least one READY FunctionVersion.

### 8.2 Loading States

Replace text-only "Loading..." with:

- skeleton panels,
- skeleton table rows,
- disabled actions while loading,
- polling indicators for invocations/deploys.

### 8.3 Confirmations

Replace `window.confirm` with `DangerDialog`.

Destructive actions must show:

- resource name,
- impact,
- irreversible warning,
- typed confirmation only for high-risk actions.

### 8.4 Copy Actions

Any key, URL, command, route, or verification value should have a copy button with:

- copied state,
- keyboard accessibility,
- monospace presentation.

### 8.5 Mobile Behavior

Minimum targets:

- 375px phone,
- 768px tablet,
- 1024px laptop,
- 1440px desktop.

Mobile shell:

- bottom nav or collapsible drawer,
- no fixed 64px icon rail on small screens,
- cards over tables,
- horizontal editors guarded behind explicit full-screen mode.

---

## 9. Development Phases

### Phase 1 - Design Foundation

Goal: Make the app look like FuncHole.

Deliverables:

- global theme tokens from landing,
- IBM Plex Sans and JetBrains Mono,
- dark-first shell,
- BrandMark component,
- updated Button/Input/Panel/Status components,
- CommandCard and EmptyState,
- animation tokens and reduced-motion rules,
- base SVG primitives: `PulseDot`, `AnimatedPacket`, `TraceLine`,
- remove cyan as primary brand action.

Acceptance:

- Login and overview visually match the landing concept.
- Hover, reveal, copy, and status transitions feel intentional.
- Existing pages still work with the new primitives.
- `git diff --check`, lint, and build pass.

### Phase 2 - Agent Setup and Overview

Goal: Make the first screen get users connected to MCP.

Deliverables:

- grouped sidebar nav,
- top bar with workspace/user/system hints,
- Agent Setup hero,
- animated `AgentToMcpDiagram`,
- MCP key creation path,
- per-agent command cards,
- connection status / last-used state,
- newbie launch checklist,
- live route cards after setup,
- recent invocation placeholder or real data if API exists.

Acceptance:

- A new user knows they should connect an MCP agent within 10 seconds.
- A new user can create a key and copy the correct agent command without reading docs.
- The first-run screen visually shows agent -> MCP connection.
- A returning user sees what is live and what failed.

### Phase 3 - Functions Workspace

Goal: Make source review, manual patching, deploy, and test guided.

Deliverables:

- Function cards/list revamp,
- Function detail version timeline,
- FunctionVersion LifecycleRail,
- `FunctionLifecycleDiagram`,
- source file explorer,
- full code-editor workspace,
- managed FunctionVersion attachment manager,
- effective runtime context summary,
- unified InvokeConsole,
- LogTraceConsole for test results,
- read-only source inspection for READY/FAILED versions,
- manual edit/save flow for DRAFT versions,
- reorganized panels: Source, Config, Databases, Deploy, Test, Artifact,
- better deploy/test state feedback,
- clear "agent should do this" vs "manual override" messaging.

Acceptance:

- User can inspect function source files like a code editor.
- User can safely make small manual source changes on DRAFT versions.
- User can understand direct vs inherited env/database context before running.
- User can run a test and see invocation status, result, and logs in one console.
- Source -> deploy -> test state is visible through motion and lifecycle visuals.
- User can deploy, test, and inspect without guessing order.
- Failed deploy and failed invocation have clear recovery paths.

### Phase 4 - Flow Control Room

Goal: Make routes and flow versions feel live.

Deliverables:

- Flow cards with route/host/adopted status,
- Flow detail route control room,
- `RequestFlowDiagram`,
- `GatewayRouteDiagram`,
- inherited env/database summary,
- managed Flow-level attachment manager,
- FlowVersion editor visual refresh,
- stronger step inspector,
- InvokeConsole for Flow tests,
- LogTraceConsole invocation trace drawer.

Acceptance:

- User can understand what URL a Flow serves and which version is live.
- User can see how a request travels through Gateway, Flow, Dispatcher, and Runtime.
- User can manage shared Flow env/database context without confusing it with FunctionVersion-only config.
- Draft vs adopted mode is visually obvious.

### Phase 5 - Infrastructure and Agent Setup

Goal: Make Gateways, Domains, DBs, Environments, MCP keys understandable.

Deliverables:

- Domain verification checklist,
- Gateway operational cards,
- SVG empty states for domains/gateways/databases/environments,
- Database connection cards,
- Environment profile editor improvements,
- usage maps for environments/databases,
- MCP Keys page becomes Agent Setup,
- DangerDialog across all destructive actions.

Acceptance:

- Infrastructure setup feels guided, not like raw data entry.
- Agent setup mirrors landing page commands and product promise.

---

## 10. Design Acceptance Criteria

### Brand

- App uses landing-inspired dark/amber design system.
- Logo, typography, and terminal language are consistent with landing.
- Primary actions use amber, not cyan.
- SVG visuals use the same stroke, packet, glow, and terminal-label language across pages.

### UX

- Overview presents lifecycle readiness, not only counts.
- Every major page has a clear primary action.
- Empty states teach the product.
- Resource pages show relationships, not only isolated rows.
- FunctionVersion and FlowVersion pages guide the execution lifecycle.

### Accessibility

- Text contrast meets WCAG AA.
- All icon buttons have accessible names.
- Focus states are visible.
- Keyboard navigation is preserved.
- Motion respects `prefers-reduced-motion`.
- Animated SVGs have accessible labels when meaningful and are hidden from assistive tech when decorative.

### Responsiveness

- No horizontal overflow at 375px except intentional code/editor surfaces.
- Tables have mobile alternatives or scroll containers.
- Sidebar adapts on small screens.

### Engineering

- Existing API contracts remain unchanged in the redesign.
- Refactor starts with shared components before page-by-page changes.
- No backend dependency is introduced for visual-only phases.

---

## 11. Open Product Questions

1. Should the overview prioritize "agent setup" or "first deploy" for new users?
2. Should Function and Flow pages support a unified "Ship" wizard, or stay as expert workspaces?
3. Should invocation history become a top-level page before or after visual redesign?
4. Should STATIC frontend functions get a distinct UI track from NODE functions?
5. Should `Environments` be renamed to `Runtime Config` for clarity?
6. Should `MCP API Keys` become `Agents` or `Agent Setup`?

---

## 12. Recommended Immediate Next Step

Start with **Phase 1 + Phase 2**, then immediately continue into the source-file workspace from Phase 3.

Reason:

- They establish the design system.
- They make the app finally match the landing page.
- They solve the highest-priority newbie flow: connecting MCP.
- They do not require backend changes.
- They give us reusable components for all later pages.
- They immediately improve first impression and product comprehension.
- The next biggest manual-control need is source inspection/editing, so it should not wait until late redesign.
