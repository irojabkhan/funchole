"use client";

import Link from "next/link";
import type { ComponentType } from "react";
import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import type { FlowResponse, GatewayResponse, ProfileResponse } from "@/lib/types";
import { Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { StatusBadge } from "@/components/StatusBadge";
import {
  DatabaseIcon,
  FunctionIcon,
  GlobeIcon,
  KeyIcon,
  PlayIcon,
  ServerIcon,
  TerminalIcon,
  WorkflowIcon,
  ZapIcon,
} from "@/components/icons";

interface AttentionItem {
  href: string;
  label: string;
  detail: string;
  icon: ComponentType<{ className?: string }>;
}

export default function OverviewPage() {
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [domainCount, setDomainCount] = useState<number | null>(null);
  const [gatewayCount, setGatewayCount] = useState<number | null>(null);
  const [flowCount, setFlowCount] = useState<number | null>(null);
  const [functionCount, setFunctionCount] = useState<number | null>(null);
  const [apiKeyCount, setApiKeyCount] = useState<number | null>(null);
  const [environmentCount, setEnvironmentCount] = useState<number | null>(null);
  const [databaseCount, setDatabaseCount] = useState<number | null>(null);
  const [flows, setFlows] = useState<FlowResponse[]>([]);
  const [gateways, setGateways] = useState<GatewayResponse[]>([]);

  useEffect(() => {
    let active = true;
    api.getProfile().then((p) => active && setProfile(p)).catch(() => {});
    api.listDomains(1, 1).then((r) => active && setDomainCount(r.totalElements)).catch(() => {});
    api.listGateways(1, 5).then((r) => {
      if (!active) return;
      setGatewayCount(r.totalElements);
      setGateways(r.items);
    }).catch(() => {});
    api.listFlows(1, 5).then((r) => {
      if (!active) return;
      setFlowCount(r.totalElements);
      setFlows(r.items);
    }).catch(() => {});
    api.listFunctions(1, 1).then((r) => active && setFunctionCount(r.totalElements)).catch(() => {});
    api.listApiKeys().then((r) => active && setApiKeyCount(r.filter((key) => !key.revokedAt).length)).catch(() => {});
    api.listEnvironments(1, 1).then((r) => active && setEnvironmentCount(r.totalElements)).catch(() => {});
    api.listDatabases(1, 1).then((r) => active && setDatabaseCount(r.totalElements)).catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  const gatewayById = useMemo(() => new Map(gateways.map((gateway) => [gateway.id, gateway])), [gateways]);

  const attentionCandidates: Array<AttentionItem | null> = [
    apiKeyCount !== null && apiKeyCount === 0
      ? { href: "/api-keys", label: "Connect an agent", detail: "Required before coding agents can safely work in this workspace.", icon: TerminalIcon }
      : null,
    domainCount !== null && domainCount === 0
      ? { href: "/domains", label: "Add a custom domain", detail: "Needed before public URLs can run on your own hostname.", icon: GlobeIcon }
      : null,
    gatewayCount !== null && gatewayCount === 0
      ? { href: "/gateways", label: "Create an entry point", detail: "Entry points receive public requests and send them to workflows.", icon: ServerIcon }
      : null,
    functionCount !== null && functionCount === 0
      ? { href: "/functions", label: "Create first action", detail: "Actions are the reusable pieces your agent can prepare and test.", icon: FunctionIcon }
      : null,
    flowCount !== null && flowCount === 0
      ? { href: "/flows", label: "Create first workflow", detail: "Workflows connect public requests to the right actions.", icon: WorkflowIcon }
      : null,
  ];
  const attentionItems = attentionCandidates.filter((item): item is AttentionItem => item !== null);

  const coreMetrics = [
    { href: "/functions", label: "Actions", value: functionCount, icon: FunctionIcon },
    { href: "/flows", label: "Workflows", value: flowCount, icon: WorkflowIcon },
    { href: "/gateways", label: "Entry Points", value: gatewayCount, icon: ServerIcon },
    { href: "/domains", label: "Custom Domains", value: domainCount, icon: GlobeIcon },
  ];

  const configMetrics = [
    { href: "/api-keys", label: "Agent Access", value: apiKeyCount, icon: TerminalIcon },
    { href: "/environments", label: "Variables & Secrets", value: environmentCount, icon: KeyIcon },
    { href: "/databases", label: "Data Sources", value: databaseCount, icon: DatabaseIcon },
  ];

  const activeRoutes = flows.filter((flow) => flow.activeFlowVersionStatus === "ADOPTED").slice(0, 4);
  const draftRoutes = flows.filter((flow) => flow.activeFlowVersionStatus !== "ADOPTED").slice(0, 3);

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-muted">Control plane</p>
          <h1 className="mt-2 text-3xl font-bold tracking-[-0.04em] text-foreground sm:text-4xl">
            Operations overview{profile ? `, ${profile.username}` : ""}
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
            Current workspace state, live URLs, and manual checks. Setup guidance only appears when something needs attention.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href="/functions">
            <Button variant="primary">
              <PlayIcon className="h-4 w-4" />
              Run a test
            </Button>
          </Link>
          <Link href="/api-keys">
            <Button variant="secondary">
              <TerminalIcon className="h-4 w-4" />
              Agent access
            </Button>
          </Link>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {coreMetrics.map((metric) => (
          <MetricCard key={metric.href} {...metric} />
        ))}
      </section>

      <section className="grid gap-5 xl:grid-cols-[1.35fr_0.65fr]">
        <Panel className="overflow-hidden">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2 className="text-lg font-bold tracking-tight text-foreground">Live URLs</h2>
              <p className="mt-1 text-sm text-muted">Public URLs that are ready to receive customer requests.</p>
            </div>
            <Link href="/flows" className="text-xs font-bold text-accent hover:text-accent-hover">
              View workflows
            </Link>
          </div>

          {activeRoutes.length > 0 ? (
            <div className="divide-y divide-border">
              {activeRoutes.map((flow) => {
                const gateway = gatewayById.get(flow.gatewayId);
                const hostname = gateway ? `${gateway.uniqueKey}.${gateway.domainName}` : flow.gatewayName;
                return (
                  <Link key={flow.id} href={`/flows/${flow.id}`} className="group grid gap-3 px-5 py-4 transition-colors hover:bg-accent-soft lg:grid-cols-[1fr_auto]">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded-full border border-accent-border bg-accent-soft px-2.5 py-1 font-mono text-xs font-bold text-accent">
                          {flow.httpMethod}
                        </span>
                        <code className="truncate font-mono text-sm text-foreground">https://{hostname}{flow.path}</code>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-foreground">{flow.name}</p>
                      <p className="mt-1 font-mono text-xs text-muted">{flow.flowKey}</p>
                    </div>
                    <div className="flex items-center gap-2 lg:justify-end">
                      <StatusBadge status={flow.activeFlowVersionStatus ?? "DRAFT"} />
                      <span className="text-xs font-bold text-muted group-hover:text-accent">Open</span>
                    </div>
                  </Link>
                );
              })}
            </div>
          ) : (
            <div className="px-5 py-8">
              <div className="rounded-2xl border border-dashed border-border bg-surface-2/45 p-5">
                <p className="text-sm font-semibold text-foreground">No live URLs yet.</p>
                <p className="mt-1 text-sm leading-6 text-muted">
                  Publish a workflow when you are ready to expose a public URL.
                </p>
                <Link href="/flows" className="mt-4 inline-flex">
                  <Button variant="secondary" size="sm">Open workflows</Button>
                </Link>
              </div>
            </div>
          )}
        </Panel>

        <Panel className="p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold tracking-tight text-foreground">Needs attention</h2>
              <p className="mt-1 text-sm text-muted">Only one-time or blocking setup appears here.</p>
            </div>
            <StatusBadge status={attentionItems.length === 0 ? "READY" : "PENDING"} />
          </div>

          <div className="mt-5 space-y-2">
            {attentionItems.length === 0 ? (
              <div className="rounded-2xl border border-success/25 bg-success/10 p-4">
                <p className="text-sm font-semibold text-success">Workspace is configured.</p>
                <p className="mt-1 text-xs leading-5 text-muted">No setup checklist noise. Use the dashboard for operations and debugging.</p>
              </div>
            ) : (
              attentionItems.map((item) => {
                const Icon = item.icon;
                return (
                  <Link key={item.href} href={item.href} className="flex gap-3 rounded-2xl border border-border bg-surface-2/55 p-3 transition-colors hover:border-accent-border hover:bg-accent-soft">
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-border bg-surface text-accent">
                      <Icon className="h-4 w-4" />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-foreground">{item.label}</span>
                      <span className="block text-xs leading-5 text-muted">{item.detail}</span>
                    </span>
                  </Link>
                );
              })
            )}
          </div>
        </Panel>
      </section>

      <section className="grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
        <Panel className="p-5">
              <h2 className="text-lg font-bold tracking-tight text-foreground">Shared setup</h2>
              <p className="mt-1 text-sm text-muted">Access, variables, secrets, and data connections used by customer-facing work.</p>
          <div className="mt-4 grid gap-2">
            {configMetrics.map((metric) => (
              <CompactMetric key={metric.href} {...metric} />
            ))}
          </div>
        </Panel>

        <Panel className="p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold tracking-tight text-foreground">Work in progress</h2>
              <p className="mt-1 text-sm text-muted">Items that exist but are not serving customer requests yet.</p>
            </div>
            <ZapIcon className="h-5 w-5 text-accent" />
          </div>
          <div className="mt-4 space-y-2">
            {draftRoutes.length === 0 ? (
              <p className="rounded-2xl border border-border bg-surface-2/45 p-4 text-sm text-muted">No work-in-progress URLs in the latest snapshot.</p>
            ) : (
              draftRoutes.map((flow) => (
                <Link key={flow.id} href={`/flows/${flow.id}`} className="flex items-center justify-between gap-3 rounded-2xl border border-border bg-surface-2/45 p-3 transition-colors hover:border-accent-border hover:bg-accent-soft">
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-semibold text-foreground">{flow.name}</span>
                    <span className="block truncate font-mono text-xs text-muted">{flow.httpMethod} {flow.path}</span>
                  </span>
                  <StatusBadge status={flow.activeFlowVersionStatus ?? "DRAFT"} />
                </Link>
              ))
            )}
          </div>
        </Panel>
      </section>
    </div>
  );
}

function MetricCard({
  href,
  label,
  value,
  icon: Icon,
}: {
  href: string;
  label: string;
  value: number | null;
  icon: ComponentType<{ className?: string }>;
}) {
  return (
    <Link href={href}>
      <Panel className="group p-5 transition-all hover:-translate-y-0.5 hover:border-accent-border">
        <div className="flex items-center justify-between">
          <span className="grid h-10 w-10 place-items-center rounded-2xl border border-border bg-surface-2 text-accent">
            <Icon className="h-5 w-5" />
          </span>
          <span className="font-mono text-xs font-bold uppercase tracking-[0.14em] text-muted group-hover:text-accent">Open</span>
        </div>
        <p className="mt-5 text-3xl font-bold text-foreground">{value ?? "—"}</p>
        <p className="mt-1 text-sm font-semibold text-muted">{label}</p>
      </Panel>
    </Link>
  );
}

function CompactMetric({
  href,
  label,
  value,
  icon: Icon,
}: {
  href: string;
  label: string;
  value: number | null;
  icon: ComponentType<{ className?: string }>;
}) {
  return (
    <Link href={href} className="flex items-center justify-between rounded-2xl border border-border bg-surface-2/45 px-4 py-3 transition-colors hover:border-accent-border hover:bg-accent-soft">
      <span className="flex items-center gap-3">
        <Icon className="h-4 w-4 text-accent" />
        <span className="text-sm font-semibold text-foreground">{label}</span>
      </span>
      <span className="font-mono text-sm text-muted-strong">{value ?? "—"}</span>
    </Link>
  );
}
