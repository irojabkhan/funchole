"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { Pagination } from "@/components/Pagination";
import { panelClass, Panel } from "@/components/Panel";
import { Button, buttonClasses } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PlusIcon, TrashIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type { FunctionResponse, PaginationResponse } from "@/lib/types";

const PAGE_SIZE = 10;

interface FunctionFormState {
  functionKey: string;
  name: string;
  description: string;
  runtime: string;
}

const EMPTY_FORM: FunctionFormState = {
  functionKey: "",
  name: "",
  description: "",
  runtime: "NODE",
};

export default function FunctionsPage() {
  const [functions, setFunctions] = useState<PaginationResponse<FunctionResponse> | null>(null);
  const [page, setPage] = useState(1);
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState<FunctionFormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listFunctions(page, PAGE_SIZE);
        if (!cancelled) setFunctions(data);
      } catch {
        if (!cancelled) setError("Failed to load functions");
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

  function closeForm() {
    setForm(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setError(null);
    setBusy(true);
    try {
      await api.createFunction({
        functionKey: form.functionKey.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        runtime: form.runtime,
      });
      closeForm();
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create function");
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(fn: FunctionResponse) {
    if (!window.confirm(`Delete function "${fn.name}"?`)) {
      return;
    }
    setError(null);
    try {
      await api.deleteFunction(fn.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to delete function");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Functions</h1>
          <p className="mt-1 text-sm text-muted">
            A function is versioned code that a Flow step or a direct test can run.
          </p>
        </div>
        <Button variant="primary" onClick={openCreate}>
          <PlusIcon className="h-4 w-4" />
          New function
        </Button>
      </div>

      {form && (
        <form onSubmit={handleSubmit} className={`${panelClass} grid gap-4 p-4 sm:grid-cols-2`}>
          <label className={fieldClass}>
            <span className={labelClass}>Function key</span>
            <input
              type="text"
              required
              maxLength={150}
              placeholder="fn_hello_world"
              pattern="[a-zA-Z0-9_.\-]+"
              value={form.functionKey}
              onChange={(e) => setForm({ ...form, functionKey: e.target.value })}
              className={`${inputClass} font-mono`}
            />
          </label>
          <label className={fieldClass}>
            <span className={labelClass}>Name</span>
            <input
              type="text"
              required
              maxLength={255}
              placeholder="Hello World"
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
            <span className={labelClass}>Runtime</span>
            <select
              value={form.runtime}
              onChange={(e) => setForm({ ...form, runtime: e.target.value })}
              className={inputClass}
            >
              <option value="NODE">NODE</option>
            </select>
          </label>
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" variant="primary" disabled={busy}>
              Create function
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
              <th className="px-4 py-3 font-medium">Runtime</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {!functions && (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted">
                  Loading…
                </td>
              </tr>
            )}
            {functions?.items.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted">
                  No functions yet.
                </td>
              </tr>
            )}
            {functions?.items.map((fn) => (
              <tr key={fn.id} className="border-b border-border last:border-0 hover:bg-surface-hover">
                <td className="px-4 py-3">
                  <Link href={`/functions/${fn.id}`} className="font-medium text-foreground hover:text-cyan-600 dark:hover:text-cyan-400">
                    {fn.name}
                  </Link>
                  <p className="font-mono text-xs text-muted">{fn.functionKey}</p>
                </td>
                <td className="px-4 py-3 text-muted">{fn.runtime}</td>
                <td className="px-4 py-3 text-muted">{new Date(fn.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <Link href={`/functions/${fn.id}`} className={buttonClasses("secondary", "sm")}>
                      Open
                    </Link>
                    <Button variant="danger" size="icon" title="Delete" onClick={() => handleDelete(fn)}>
                      <TrashIcon className="h-4 w-4" />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>

      {functions && (
        <Pagination
          page={functions.page}
          totalPages={functions.totalPages}
          totalElements={functions.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  );
}
