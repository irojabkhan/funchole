"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { Button, buttonClasses } from "@/components/Button";
import { CreatePanel } from "@/components/CreatePanel";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PageHeader } from "@/components/PageHeader";
import { ResourceList, ResourceListState } from "@/components/ResourceList";
import { PlusIcon, TrashIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type { FlowResponse, GatewayResponse, PaginationResponse } from "@/lib/types";

const PAGE_SIZE = 10;
const HTTP_METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE"];

interface FlowFormState {
  flowKey: string;
  name: string;
  description: string;
  gatewayId: string;
  httpMethod: string;
  path: string;
  priority: string;
}

const EMPTY_FORM: FlowFormState = {
  flowKey: "",
  name: "",
  description: "",
  gatewayId: "",
  httpMethod: "GET",
  path: "/",
  priority: "100",
};

export default function FlowsPage() {
  const [flows, setFlows] = useState<PaginationResponse<FlowResponse> | null>(null);
  const [gateways, setGateways] = useState<GatewayResponse[]>([]);
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState<FlowFormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listFlows(page, PAGE_SIZE);
        if (!cancelled) setFlows(data);
      } catch {
        if (!cancelled) setError("Failed to load flows");
      }
      try {
        const gatewayData = await api.listGateways(1, 100);
        if (!cancelled) setGateways(gatewayData.items);
      } catch {
        if (!cancelled) setGateways([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, reloadKey]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  function openCreate() {
    setForm({ ...EMPTY_FORM, gatewayId: gateways[0]?.id ?? "" });
  }

  function closeForm() {
    setForm(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setError(null);
    setBusy(true);
    try {
      await api.createFlow({
        flowKey: form.flowKey.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        gatewayId: form.gatewayId,
        httpMethod: form.httpMethod,
        path: form.path.trim(),
        priority: form.priority.trim() ? Number(form.priority) : null,
      });
      closeForm();
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create flow");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(flow: FlowResponse) {
    if (!window.confirm(`Delete flow "${flow.name}"?`)) {
      return;
    }
    setError(null);
    try {
      await api.deleteFlow(flow.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete flow");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        eyebrow="Build"
        title="Workflows"
        description="Customer-facing paths that connect a request to the right actions."
        actions={
        <Button variant="primary" onClick={openCreate} disabled={gateways.length === 0}>
          <PlusIcon className="h-4 w-4" />
          New workflow
        </Button>
        }
      />

      {gateways.length === 0 && (
        <p className="rounded-2xl border border-accent-border bg-accent-soft px-4 py-3 text-sm text-accent">
          You need at least one entry point before creating a workflow.
        </p>
      )}

      {form && (
        <CreatePanel title="New workflow" description="Choose the public method and path this workflow should answer.">
          <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
            <label className={fieldClass}>
              <span className={labelClass}>Workflow key</span>
              <input
                type="text"
                required
                maxLength={150}
                placeholder="flw_orders_list"
                pattern="[a-zA-Z0-9_.\-]+"
                value={form.flowKey}
                onChange={(e) => setForm({ ...form, flowKey: e.target.value })}
                className={`${inputClass} font-mono`}
              />
            </label>
            <label className={fieldClass}>
              <span className={labelClass}>Name</span>
              <input
                type="text"
                required
                maxLength={255}
                placeholder="Orders List"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputClass}
              />
            </label>
            <label className={`${fieldClass} sm:col-span-2`}>
              <span className={labelClass}>Description</span>
              <input
                type="text"
                maxLength={1000}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className={inputClass}
              />
            </label>
            <label className={fieldClass}>
              <span className={labelClass}>Entry point</span>
              <select
                required
                value={form.gatewayId}
                onChange={(e) => setForm({ ...form, gatewayId: e.target.value })}
                className={inputClass}
              >
                {gateways.map((gateway) => (
                  <option key={gateway.id} value={gateway.id}>
                    {gateway.name} ({gateway.uniqueKey}.{gateway.domainName})
                  </option>
                ))}
              </select>
            </label>
            <div className="grid grid-cols-3 gap-3">
              <label className={fieldClass}>
                <span className={labelClass}>Method</span>
                <select
                  value={form.httpMethod}
                  onChange={(e) => setForm({ ...form, httpMethod: e.target.value })}
                  className={inputClass}
                >
                  {HTTP_METHODS.map((method) => (
                    <option key={method} value={method}>
                      {method}
                    </option>
                  ))}
                </select>
              </label>
              <label className={`${fieldClass} col-span-2`}>
                <span className={labelClass}>Path</span>
                <input
                  type="text"
                  required
                  placeholder="/orders"
                  value={form.path}
                  onChange={(e) => setForm({ ...form, path: e.target.value })}
                  className={`${inputClass} font-mono`}
                />
              </label>
            </div>
            <label className={fieldClass}>
              <span className={labelClass}>Priority</span>
              <input
                type="number"
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value })}
                className={inputClass}
              />
            </label>
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" variant="primary" disabled={busy}>
                Create workflow
              </Button>
              <Button type="button" variant="secondary" onClick={closeForm}>
                Cancel
              </Button>
            </div>
          </form>
        </CreatePanel>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          {error}
        </p>
      )}

      <ResourceList title="Workflow catalog" description="Each workflow owns one public path and becomes live after publishing.">
        {!flows && <ResourceListState>Loading workflows…</ResourceListState>}
        {flows?.items.length === 0 && <ResourceListState>No workflows yet. Create one after an entry point exists.</ResourceListState>}
        {flows?.items.map((flow) => (
          <div key={flow.id} className="grid gap-4 px-5 py-4 transition-colors hover:bg-accent-soft xl:grid-cols-[1fr_auto]">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-accent-border bg-accent-soft px-2.5 py-1 font-mono text-xs font-bold text-accent">{flow.httpMethod}</span>
                <code className="truncate font-mono text-sm text-foreground">{flow.path}</code>
                {flow.activeFlowVersionStatus ? <StatusBadge status={flow.activeFlowVersionStatus} /> : <StatusBadge status="DRAFT" />}
              </div>
              <Link href={`/flows/${flow.id}`} className="mt-3 block text-base font-bold text-foreground hover:text-accent">
                {flow.name}
              </Link>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                <code className="rounded-full border border-border bg-surface-2 px-2.5 py-1 font-mono">{flow.flowKey}</code>
                <span>Entry point: {flow.gatewayName}</span>
                <span>Priority: {flow.priority}</span>
              </div>
            </div>
            <div className="flex items-center gap-2 xl:justify-end">
              <Link href={`/flows/${flow.id}`} className={buttonClasses("secondary", "sm")}>
                Open workflow
              </Link>
              <Button variant="danger" size="icon" title="Delete" onClick={() => handleDelete(flow)}>
                <TrashIcon className="h-4 w-4" />
              </Button>
            </div>
          </div>
        ))}
      </ResourceList>

      {flows && (
        <Pagination
          page={flows.page}
          totalPages={flows.totalPages}
          totalElements={flows.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  );
}
