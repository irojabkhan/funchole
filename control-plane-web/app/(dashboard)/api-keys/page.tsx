"use client";

import { useEffect, useState, type FormEvent } from "react";
import { panelClass, Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import { PlusIcon, TrashIcon, CopyIcon, CheckIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type { ApiKeyResponse } from "@/lib/types";

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyResponse[] | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revealedKey, setRevealedKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.listApiKeys();
        if (!cancelled) setKeys(data);
      } catch {
        if (!cancelled) setError("Failed to load API keys");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const created = await api.createApiKey({ name: name.trim() });
      setRevealedKey(created.rawKey);
      setName("");
      setCreating(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create API key");
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke(key: ApiKeyResponse) {
    if (!window.confirm(`Revoke "${key.name}"? Anything using it (e.g. an MCP client) will stop working immediately.`)) {
      return;
    }
    setError(null);
    try {
      await api.revokeApiKey(key.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to revoke API key");
    }
  }

  async function copyRevealedKey() {
    if (!revealedKey) return;
    try {
      await navigator.clipboard.writeText(revealedKey);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard access can be denied by the browser - the key is still
      // shown on screen, so this isn't fatal, just a lost convenience.
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">API Keys</h1>
          <p className="mt-1 text-sm text-muted">
            Long-lived credentials for machine clients - point your MCP-compatible coding agent at{" "}
            <code className="rounded bg-surface-hover px-1 py-0.5 font-mono text-xs">/api/mcp</code> with one of
            these as its bearer token.
          </p>
        </div>
        <Button variant="primary" onClick={() => setCreating(true)}>
          <PlusIcon className="h-4 w-4" />
          New API key
        </Button>
      </div>

      {revealedKey && (
        <div className="flex flex-col gap-2 rounded-xl border border-cyan-300 bg-cyan-50 p-4 dark:border-cyan-500/40 dark:bg-cyan-500/10">
          <p className="text-sm font-medium text-foreground">
            Copy this key now - it won&apos;t be shown again.
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 truncate rounded-lg border border-border bg-surface px-3 py-2 font-mono text-xs text-foreground">
              {revealedKey}
            </code>
            <Button variant="secondary" size="icon" title="Copy" onClick={copyRevealedKey}>
              {copied ? <CheckIcon className="h-4 w-4 text-emerald-600 dark:text-emerald-400" /> : <CopyIcon className="h-4 w-4" />}
            </Button>
          </div>
          <Button variant="ghost" className="self-start" onClick={() => setRevealedKey(null)}>
            Done
          </Button>
        </div>
      )}

      {creating && (
        <form onSubmit={handleCreate} className={`${panelClass} flex flex-wrap items-end gap-3 p-4`}>
          <label className={`${fieldClass} flex-1`}>
            <span className={labelClass}>Name</span>
            <input
              type="text"
              required
              maxLength={150}
              placeholder="My coding agent"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className={inputClass}
            />
          </label>
          <Button type="submit" variant="primary" disabled={busy}>
            Create
          </Button>
          <Button type="button" variant="secondary" onClick={() => setCreating(false)}>
            Cancel
          </Button>
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
              <th className="px-4 py-3 font-medium">Key</th>
              <th className="px-4 py-3 font-medium">Created</th>
              <th className="px-4 py-3 font-medium">Last used</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {!keys && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-muted">
                  Loading…
                </td>
              </tr>
            )}
            {keys?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-muted">
                  No API keys yet.
                </td>
              </tr>
            )}
            {keys?.map((key) => (
              <tr key={key.id} className="border-b border-border last:border-0 hover:bg-surface-hover">
                <td className="px-4 py-3 font-medium text-foreground">{key.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted">{key.keyPrefix}…</td>
                <td className="px-4 py-3 text-muted">{new Date(key.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-muted">
                  {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : "Never"}
                </td>
                <td className="px-4 py-3">
                  {key.revokedAt ? (
                    <span className="text-rose-600 dark:text-rose-400">Revoked</span>
                  ) : (
                    <span className="text-emerald-600 dark:text-emerald-400">Active</span>
                  )}
                </td>
                <td className="px-4 py-3 text-right">
                  {!key.revokedAt && (
                    <Button variant="danger" size="icon" title="Revoke" onClick={() => handleRevoke(key)}>
                      <TrashIcon className="h-4 w-4" />
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Panel>
    </div>
  );
}
