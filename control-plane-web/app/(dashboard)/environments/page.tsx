"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Pagination } from "@/components/Pagination";
import { Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { CreatePanel } from "@/components/CreatePanel";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PageHeader } from "@/components/PageHeader";
import { ResourceList, ResourceListState } from "@/components/ResourceList";
import { KeyIcon, PencilIcon, PlusIcon, TrashIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type {
  EnvironmentProfileConfigResponse,
  EnvironmentProfileResponse,
  PaginationResponse,
} from "@/lib/types";

const PAGE_SIZE = 10;

interface EnvironmentFormState {
  id: string | null;
  environmentKey: string;
  name: string;
  description: string;
}

const EMPTY_FORM: EnvironmentFormState = {
  id: null,
  environmentKey: "",
  name: "",
  description: "",
};

export default function EnvironmentsPage() {
  const [environments, setEnvironments] = useState<PaginationResponse<EnvironmentProfileResponse> | null>(null);
  const [selected, setSelected] = useState<EnvironmentProfileResponse | null>(null);
  const [config, setConfig] = useState<EnvironmentProfileConfigResponse | null>(null);
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState<EnvironmentFormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listEnvironments(page, PAGE_SIZE);
        if (!cancelled) setEnvironments(data);
      } catch {
        if (!cancelled) setError("Failed to load environments");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, reloadKey]);

  useEffect(() => {
    if (!selected) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const data = await api.getEnvironmentConfig(selected.id);
        if (!cancelled) setConfig(data);
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Failed to load environment config");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selected, reloadKey]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  function openCreate() {
    setForm({ ...EMPTY_FORM });
  }

  function openEdit(environment: EnvironmentProfileResponse) {
    setForm({
      id: environment.id,
      environmentKey: environment.environmentKey,
      name: environment.name,
      description: environment.description ?? "",
    });
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
      if (form.id) {
        await api.updateEnvironment(form.id, {
          name: form.name.trim(),
          description: form.description.trim() || null,
        });
      } else {
        await api.createEnvironment({
          environmentKey: form.environmentKey.trim(),
          name: form.name.trim(),
          description: form.description.trim() || null,
        });
      }
      closeForm();
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save environment");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(environment: EnvironmentProfileResponse) {
    if (!window.confirm(`Delete variable set "${environment.name}"? Anything using it will stop inheriting these values.`)) {
      return;
    }
    setError(null);
    try {
      await api.deleteEnvironment(environment.id);
      if (selected?.id === environment.id) {
        setConfig(null);
        setSelected(null);
      }
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete environment");
    }
  }

  async function saveConfig(kind: "env" | "secret", key: string, value: string) {
    if (!selected) return;
    setError(null);
    try {
      const data = kind === "env"
        ? await api.upsertEnvironmentEnvVar(selected.id, key, value)
        : await api.upsertEnvironmentSecret(selected.id, key, value);
      setConfig(data);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save config");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        eyebrow="Configure"
        title="Variables & Secrets"
        description="Shared configuration for production, staging, testing, and agent-created work."
        actions={
        <Button variant="primary" onClick={openCreate}>
          <PlusIcon className="h-4 w-4" />
          New variable set
        </Button>
        }
      />

      {form && (
        <CreatePanel title={form.id ? "Edit variable set" : "New variable set"} description="Create a named profile for shared configuration. Secrets are stored protected and only references are shown later.">
          <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
            <label className={fieldClass}>
              <span className={labelClass}>Variable set key</span>
              <input
                type="text"
                required
                maxLength={150}
                disabled={form.id !== null}
                placeholder="production"
                pattern="[a-zA-Z0-9_.\-]+"
                value={form.environmentKey}
                onChange={(e) => setForm({ ...form, environmentKey: e.target.value })}
                className={`${inputClass} font-mono disabled:opacity-50`}
              />
            </label>
            <label className={fieldClass}>
              <span className={labelClass}>Name</span>
              <input
                type="text"
                required
                maxLength={255}
                placeholder="Production"
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
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" variant="primary" disabled={busy}>
                {form.id ? "Save changes" : "Create variable set"}
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

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
        <ResourceList title="Variable sets" description="Select a set to manage variables and secrets.">
          {!environments && <ResourceListState>Loading variable sets…</ResourceListState>}
          {environments?.items.length === 0 && <ResourceListState>No variable sets yet. Create one for production, staging, or testing context.</ResourceListState>}
          {environments?.items.map((environment) => (
            <div
              key={environment.id}
              className={`grid gap-4 px-5 py-4 transition-colors hover:bg-accent-soft lg:grid-cols-[1fr_auto] ${
                selected?.id === environment.id ? "bg-accent-soft" : ""
              }`}
            >
              <button
                type="button"
                onClick={() => {
                  setConfig(null);
                  setSelected(environment);
                }}
                className="min-w-0 text-left"
              >
                <p className="text-base font-bold text-foreground hover:text-accent">{environment.name}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <code className="rounded-full border border-border bg-surface-2 px-2.5 py-1 font-mono text-xs text-muted-strong">{environment.environmentKey}</code>
                  <span className="text-xs text-muted">Created {new Date(environment.createdAt).toLocaleString()}</span>
                </div>
                {environment.description && <p className="mt-2 line-clamp-1 text-sm text-muted">{environment.description}</p>}
              </button>
              <div className="flex items-center gap-2 lg:justify-end">
                <Button variant="secondary" size="icon" title="Edit" onClick={() => openEdit(environment)}>
                  <PencilIcon className="h-4 w-4" />
                </Button>
                <Button variant="danger" size="icon" title="Delete" onClick={() => handleDelete(environment)}>
                  <TrashIcon className="h-4 w-4" />
                </Button>
              </div>
            </div>
          ))}
        </ResourceList>

        <EnvironmentConfigPanel selected={selected} config={config} onSave={saveConfig} />
      </div>

      {environments && (
        <Pagination
          page={environments.page}
          totalPages={environments.totalPages}
          totalElements={environments.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  );
}

interface EnvironmentConfigPanelProps {
  selected: EnvironmentProfileResponse | null;
  config: EnvironmentProfileConfigResponse | null;
  onSave: (kind: "env" | "secret", key: string, value: string) => Promise<void>;
}

function EnvironmentConfigPanel({ selected, config, onSave }: EnvironmentConfigPanelProps) {
  if (!selected) {
    return (
      <Panel className="flex min-h-64 flex-col items-center justify-center gap-3 p-6 text-center">
        <KeyIcon className="h-8 w-8 text-muted" />
        <p className="text-sm font-medium text-foreground">Select an environment</p>
        <p className="max-w-xs text-xs text-muted">
          Choose a set to manage shared variables and secrets.
        </p>
      </Panel>
    );
  }

  return (
    <Panel className="flex flex-col gap-4 p-4">
      <div>
        <p className="text-sm font-medium text-foreground">{selected.name}</p>
        <p className="mt-1 font-mono text-xs text-muted">{selected.environmentKey}</p>
      </div>
      <ConfigList
        title="Variables"
        entries={config?.envVars.map((entry) => ({ key: entry.key, value: entry.value })) ?? null}
        placeholder="production"
        onSave={(key, value) => onSave("env", key, value)}
      />
      <ConfigList
        title="Secrets"
        entries={config?.secrets.map((entry) => ({ key: entry.key, value: entry.secretRef })) ?? null}
        placeholder="super-secret-value"
        secret
        onSave={(key, value) => onSave("secret", key, value)}
      />
    </Panel>
  );
}

interface ConfigListProps {
  title: string;
  entries: { key: string; value: string }[] | null;
  placeholder: string;
  secret?: boolean;
  onSave: (key: string, value: string) => Promise<void>;
}

function ConfigList({ title, entries, placeholder, secret, onSave }: ConfigListProps) {
  const [adding, setAdding] = useState(false);
  const [key, setKey] = useState("");
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSave() {
    setBusy(true);
    try {
      await onSave(key.trim(), value);
      setKey("");
      setValue("");
      setAdding(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-lg border border-border">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <p className="text-xs font-medium text-foreground">{title}</p>
        {!adding && (
          <Button variant="secondary" size="sm" onClick={() => setAdding(true)}>
            <PlusIcon className="h-3.5 w-3.5" />
            Add
          </Button>
        )}
      </div>

      {entries === null ? (
        <p className="px-3 py-3 text-xs text-muted">Loading…</p>
      ) : entries.length === 0 && !adding ? (
        <p className="px-3 py-3 text-xs text-muted">None set.</p>
      ) : (
        <ul className="divide-y divide-border">
          {entries.map((entry) => (
            <li key={entry.key} className="flex items-center gap-3 px-3 py-2 text-xs">
              <span className="w-36 shrink-0 truncate font-mono font-medium text-foreground">{entry.key}</span>
              <span className="truncate font-mono text-muted">{secret ? `configured (${entry.value})` : entry.value}</span>
            </li>
          ))}
        </ul>
      )}

      {adding && (
        <div className="grid gap-2 border-t border-border px-3 py-2.5">
          <label className={fieldClass}>
            <span className={labelClass}>Key</span>
            <input
              type="text"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="API_KEY"
              className={`${inputClass} font-mono text-xs`}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Value</span>
            <input
              type={secret ? "password" : "text"}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={placeholder}
              className={`${inputClass} font-mono text-xs`}
            />
          </label>
          <div className="flex gap-2">
            <Button variant="primary" size="sm" disabled={busy || !key.trim()} onClick={handleSave}>
              Save
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setAdding(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
