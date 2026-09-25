"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/Button";
import { CreatePanel } from "@/components/CreatePanel";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PageHeader } from "@/components/PageHeader";
import { ResourceList, ResourceListState } from "@/components/ResourceList";
import { PlusIcon, PencilIcon, TrashIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type {
  DomainResponse,
  GatewayResponse,
  GatewayStatus,
  PaginationResponse,
} from "@/lib/types";

const PAGE_SIZE = 10;

interface GatewayFormState {
  name: string;
  description: string;
  appDomainId: string;
  status: GatewayStatus;
}

const EMPTY_FORM: GatewayFormState = {
  name: "",
  description: "",
  appDomainId: "",
  status: "ACTIVE",
};

export default function GatewaysPage() {
  const [gateways, setGateways] = useState<PaginationResponse<GatewayResponse> | null>(null);
  const [domains, setDomains] = useState<DomainResponse[]>([]);
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState<GatewayFormState | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listGateways(page, PAGE_SIZE);
        if (!cancelled) setGateways(data);
      } catch {
        if (!cancelled) setError("Failed to load gateways");
      }
      try {
        const domainData = await api.listDomains(1, 100);
        if (!cancelled) {
          setDomains(domainData.items.filter((d) => d.status === "VERIFIED"));
        }
      } catch {
        if (!cancelled) setDomains([]);
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
    setEditingId(null);
    setForm({ ...EMPTY_FORM, appDomainId: domains[0]?.id ?? "" });
  }

  function openEdit(gateway: GatewayResponse) {
    setEditingId(gateway.id);
    setForm({
      name: gateway.name,
      description: gateway.description ?? "",
      appDomainId: gateway.appDomainId,
      status: gateway.status,
    });
  }

  function closeForm() {
    setForm(null);
    setEditingId(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setError(null);
    setBusy(true);
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      appDomainId: form.appDomainId,
      status: form.status,
    };
    try {
      if (editingId) {
        await api.updateGateway(editingId, payload);
      } else {
        await api.createGateway(payload);
      }
      closeForm();
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save gateway");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(gateway: GatewayResponse) {
    if (!window.confirm(`Delete gateway "${gateway.name}"?`)) {
      return;
    }
    setError(null);
    try {
      await api.deleteGateway(gateway.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete gateway");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        eyebrow="Operate"
        title="Entry Points"
        description="Public hosts with certificates. An entry point becomes the stable hostname for customer-facing workflows."
        actions={
        <Button variant="primary" onClick={openCreate} disabled={domains.length === 0}>
          <PlusIcon className="h-4 w-4" />
          New entry point
        </Button>
        }
      />

      {domains.length === 0 && (
        <p className="rounded-2xl border border-accent-border bg-accent-soft px-4 py-3 text-sm text-accent">
          You need at least one verified domain before creating an entry point.
        </p>
      )}

      {form && (
        <CreatePanel title={editingId ? "Edit entry point" : "New entry point"} description="Choose a verified domain. FuncHole generates the unique host key and certificate metadata.">
          <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
            <label className={fieldClass}>
              <span className={labelClass}>Name</span>
              <input
                type="text"
                required
                maxLength={100}
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className={inputClass}
              />
            </label>
            <label className={fieldClass}>
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
              <span className={labelClass}>Domain</span>
              <select
                required
                value={form.appDomainId}
                onChange={(e) => setForm({ ...form, appDomainId: e.target.value })}
                className={inputClass}
              >
                {domains.map((domain) => (
                  <option key={domain.id} value={domain.id}>
                    {domain.domainName}
                  </option>
                ))}
              </select>
            </label>
            <label className={fieldClass}>
              <span className={labelClass}>Status</span>
              <select
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as GatewayStatus })}
                className={inputClass}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </label>
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" variant="primary" disabled={busy}>
              {editingId ? "Save changes" : "Create entry point"}
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

      <ResourceList title="Entry point registry" description="Hosts available for live workflows and certificate-backed traffic.">
        {!gateways && <ResourceListState>Loading entry points…</ResourceListState>}
        {gateways?.items.length === 0 && <ResourceListState>No entry points yet. Add a verified domain first, then create a public host.</ResourceListState>}
        {gateways?.items.map((gateway) => (
          <div key={gateway.id} className="grid gap-4 px-5 py-4 transition-colors hover:bg-accent-soft lg:grid-cols-[1fr_auto]">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-base font-bold text-foreground">{gateway.name}</p>
                <StatusBadge status={gateway.status} />
                {gateway.certificate ? <StatusBadge status={gateway.certificate.status} /> : <span className="rounded-full border border-border px-2.5 py-1 text-xs text-muted">No certificate</span>}
              </div>
              <code className="mt-3 block truncate font-mono text-sm text-accent">
                {gateway.uniqueKey}.{gateway.domainName}
              </code>
              {gateway.description && <p className="mt-2 text-sm leading-6 text-muted">{gateway.description}</p>}
            </div>
            <div className="flex items-center gap-2 lg:justify-end">
              <Button variant="secondary" size="sm" onClick={() => openEdit(gateway)}>
                <PencilIcon className="h-3.5 w-3.5" />
                Edit
              </Button>
              <Button variant="danger" size="icon" title="Delete" onClick={() => handleDelete(gateway)}>
                <TrashIcon className="h-4 w-4" />
              </Button>
            </div>
          </div>
        ))}
      </ResourceList>

      {gateways && (
        <Pagination
          page={gateways.page}
          totalPages={gateways.totalPages}
          totalElements={gateways.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  );
}
