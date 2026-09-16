"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Pagination } from "@/components/Pagination";
import { panelClass, Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PlusIcon, TrashIcon, PencilIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type { DatabaseResponse, PaginationResponse } from "@/lib/types";

const PAGE_SIZE = 10;

const ENGINE_OPTIONS = [
  { value: "POSTGRES", label: "PostgreSQL" },
  { value: "MYSQL", label: "MySQL" },
  { value: "MONGODB", label: "MongoDB" },
  { value: "SUPABASE", label: "Supabase" },
];

interface DatabaseFormState {
  id: string | null;
  name: string;
  type: string;
  host: string;
  port: string;
  databaseName: string;
  username: string;
  password: string;
  sslEnabled: boolean;
}

const EMPTY_FORM: DatabaseFormState = {
  id: null,
  name: "",
  type: "POSTGRES",
  host: "",
  port: "5432",
  databaseName: "",
  username: "",
  password: "",
  sslEnabled: true,
};

export default function DatabasesPage() {
  const [databases, setDatabases] = useState<PaginationResponse<DatabaseResponse> | null>(null);
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState<DatabaseFormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listDatabases(page, PAGE_SIZE);
        if (!cancelled) setDatabases(data);
      } catch {
        if (!cancelled) setError("Failed to load databases");
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
    setForm({ ...EMPTY_FORM });
  }

  function openEdit(db: DatabaseResponse) {
    setForm({
      id: db.id,
      name: db.name,
      type: db.type,
      host: db.host,
      port: String(db.port),
      databaseName: db.databaseName,
      username: db.username,
      password: "",
      sslEnabled: db.sslEnabled,
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
      const port = Number.parseInt(form.port, 10);
      if (form.id) {
        await api.updateDatabase(form.id, {
          name: form.name.trim(),
          host: form.host.trim(),
          port,
          databaseName: form.databaseName.trim(),
          username: form.username.trim(),
          password: form.password.trim() || null,
          sslEnabled: form.sslEnabled,
        });
      } else {
        await api.createDatabase({
          name: form.name.trim(),
          type: form.type,
          host: form.host.trim(),
          port,
          databaseName: form.databaseName.trim(),
          username: form.username.trim(),
          password: form.password,
          sslEnabled: form.sslEnabled,
        });
      }
      closeForm();
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save database");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(db: DatabaseResponse) {
    if (!window.confirm(`Delete database "${db.name}"? Functions attached to it will lose this connection.`)) {
      return;
    }
    setError(null);
    try {
      await api.deleteDatabase(db.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete database");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Databases</h1>
          <p className="mt-1 text-sm text-muted">
            FuncHole manages the connection - attach a database to a function version and it&apos;s ready via{" "}
            <code className="rounded bg-surface-hover px-1 py-0.5 font-mono text-xs">context.db(name)</code>.
          </p>
        </div>
        <Button variant="primary" onClick={openCreate}>
          <PlusIcon className="h-4 w-4" />
          New database
        </Button>
      </div>

      {form && (
        <form onSubmit={handleSubmit} className={`${panelClass} grid gap-4 p-4 sm:grid-cols-2`}>
          <label className={fieldClass}>
            <span className={labelClass}>Name</span>
            <input
              type="text"
              required
              maxLength={150}
              placeholder="primary"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className={`${inputClass} font-mono`}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Engine</span>
            <select
              value={form.type}
              disabled={form.id !== null}
              onChange={(e) => setForm({ ...form, type: e.target.value })}
              className={`${inputClass} disabled:opacity-50`}
            >
              {ENGINE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value} disabled={option.value !== "POSTGRES"}>
                  {option.label}
                  {option.value !== "POSTGRES" ? " (coming soon)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Host</span>
            <input
              type="text"
              required
              placeholder="db.example.com"
              value={form.host}
              onChange={(e) => setForm({ ...form, host: e.target.value })}
              className={inputClass}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Port</span>
            <input
              type="number"
              required
              min={1}
              max={65535}
              value={form.port}
              onChange={(e) => setForm({ ...form, port: e.target.value })}
              className={inputClass}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Database name</span>
            <input
              type="text"
              required
              value={form.databaseName}
              onChange={(e) => setForm({ ...form, databaseName: e.target.value })}
              className={inputClass}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Username</span>
            <input
              type="text"
              required
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              className={inputClass}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Password{form.id ? " (leave blank to keep current)" : ""}</span>
            <input
              type="password"
              required={!form.id}
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              className={inputClass}
            />
          </label>
          <label className="flex items-center gap-2 self-end pb-2">
            <input
              type="checkbox"
              checked={form.sslEnabled}
              onChange={(e) => setForm({ ...form, sslEnabled: e.target.checked })}
              className="h-4 w-4 rounded border-border"
            />
            <span className="text-sm text-foreground/90">Require SSL</span>
          </label>
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" variant="primary" disabled={busy}>
              {form.id ? "Save changes" : "Create database"}
            </Button>
            <Button type="button" variant="secondary" onClick={closeForm}>
              Cancel
            </Button>
          </div>
        </form>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          {error}
        </p>
      )}

      <Panel className="overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-muted">
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Engine</th>
              <th className="px-4 py-3 font-medium">Host</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {!databases && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-muted">
                  Loading…
                </td>
              </tr>
            )}
            {databases?.items.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-muted">
                  No databases yet.
                </td>
              </tr>
            )}
            {databases?.items.map((db) => (
              <tr key={db.id} className="border-b border-border last:border-0 hover:bg-surface-hover">
                <td className="px-4 py-3 font-medium text-foreground">{db.name}</td>
                <td className="px-4 py-3 text-muted">{db.type}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted">
                  {db.host}:{db.port}/{db.databaseName}
                </td>
                <td className="px-4 py-3 text-muted">{new Date(db.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <Button variant="secondary" size="icon" title="Edit" onClick={() => openEdit(db)}>
                      <PencilIcon className="h-4 w-4" />
                    </Button>
                    <Button variant="danger" size="icon" title="Delete" onClick={() => handleDelete(db)}>
                      <TrashIcon className="h-4 w-4" />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>

      {databases && (
        <Pagination
          page={databases.page}
          totalPages={databases.totalPages}
          totalElements={databases.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  );
}
