"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { panelClass, Panel } from "@/components/Panel";
import { Button } from "@/components/Button";
import { inputClass, labelClass, fieldClass } from "@/components/Input";
import {
  ArrowLeftIcon,
  ChevronRightIcon,
  UploadIcon,
  PlayIcon,
  ZapIcon,
  CheckIcon,
} from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import type {
  FunctionResponse,
  FunctionVersionResponse,
  FunctionVersionSourceResponse,
  InvocationInspectionResponse,
} from "@/lib/types";

const DEFAULT_SOURCE = `export async function handler(input) {
  return { status: 200, body: { ok: true, input } };
}
`;

export default function FunctionVersionDetailPage() {
  const params = useParams<{ functionId: string; versionId: string }>();
  const { functionId, versionId } = params;

  const [fn, setFn] = useState<FunctionResponse | null>(null);
  const [version, setVersion] = useState<FunctionVersionResponse | null>(null);
  const [source, setSource] = useState<FunctionVersionSourceResponse | null>(null);
  const [sourceLoaded, setSourceLoaded] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [deployErrorDetails, setDeployErrorDetails] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setDeployErrorDetails(null);
      try {
        const fnData = await api.getFunction(functionId);
        if (!cancelled) setFn(fnData);
      } catch {
        if (!cancelled) setError("Failed to load function");
      }
      try {
        const versionData = await api.getFunctionVersion(functionId, versionId);
        if (!cancelled) setVersion(versionData);
      } catch {
        if (!cancelled) setError("Failed to load version");
      }
      try {
        const sourceData = await api.getFunctionVersionSource(functionId, versionId);
        if (!cancelled) setSource(sourceData);
      } catch {
        if (!cancelled) setSource(null);
      } finally {
        if (!cancelled) setSourceLoaded(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [functionId, versionId, reloadKey]);

  function refresh() {
    setReloadKey((key) => key + 1);
  }

  async function handleDeploy() {
    setError(null);
    setDeployErrorDetails(null);
    setBusy(true);
    try {
      await api.deployFunctionVersion(functionId, versionId);
      refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        if (err.details.length > 0) setDeployErrorDetails(err.details);
      } else {
        setError("Failed to deploy");
      }
      refresh();
    } finally {
      setBusy(false);
    }
  }

  if (!fn || !version) {
    return <p className="p-6 text-sm text-muted">Loading…</p>;
  }

  const isDraft = version.status === "DRAFT";

  return (
    <div className="flex flex-col gap-6">
      <nav className="flex items-center gap-1.5 text-sm text-muted">
        <Link href="/functions" className="hover:text-foreground">
          Functions
        </Link>
        <ChevronRightIcon className="h-3.5 w-3.5" />
        <Link href={`/functions/${functionId}`} className="font-medium text-foreground hover:text-cyan-600 dark:hover:text-cyan-400">
          {fn.name}
        </Link>
        <ChevronRightIcon className="h-3.5 w-3.5" />
        <span className="font-mono text-foreground">v{version.version}</span>
      </nav>

      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <Link
            href={`/functions/${functionId}`}
            className="mt-1 flex h-8 w-8 items-center justify-center rounded-lg border border-border text-muted hover:bg-surface-hover hover:text-foreground"
          >
            <ArrowLeftIcon className="h-4 w-4" />
          </Link>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-mono text-2xl font-semibold tracking-tight text-foreground">v{version.version}</h1>
              <StatusBadge status={version.status} />
            </div>
            <p className="mt-1 text-xs text-muted">{version.runtime} runtime</p>
          </div>
        </div>
        {isDraft && (
          <Button variant="primary" onClick={handleDeploy} disabled={busy || !source}>
            <PlayIcon className="h-4 w-4" />
            Deploy
          </Button>
        )}
      </div>

      {error && (
        <div className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          <p role="alert">{error}</p>
          {deployErrorDetails && (
            <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-md bg-rose-100/60 p-2 font-mono text-xs dark:bg-black/30">
              {deployErrorDetails.join("\n")}
            </pre>
          )}
        </div>
      )}

      {version.status === "FAILED" && !deployErrorDetails && (
        <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-500/10 dark:text-rose-400">
          This version failed to build. Build output isn&apos;t persisted, so it&apos;s only shown right after a deploy
          attempt in this session. Create a new draft version to try again.
        </p>
      )}

      <SourcePanel
        functionId={functionId}
        versionId={versionId}
        source={source}
        sourceLoaded={sourceLoaded}
        isDraft={isDraft}
        onSubmitted={refresh}
        onError={setError}
      />

      {version.status === "READY" && (
        <TestInvokePanel functionId={functionId} versionId={versionId} onError={setError} />
      )}

      {version.artifactSha256 && (
        <Panel className="p-4">
          <p className="text-sm font-medium text-foreground">Artifact</p>
          <dl className="mt-2 grid gap-1.5 text-xs">
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">SHA-256</dt>
              <dd className="break-all font-mono text-foreground">{version.artifactSha256}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">Size</dt>
              <dd className="text-foreground">{version.artifactSizeBytes} bytes</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-28 shrink-0 text-muted">Published</dt>
              <dd className="text-foreground">
                {version.artifactPublishedAt ? new Date(version.artifactPublishedAt).toLocaleString() : "—"}
              </dd>
            </div>
          </dl>
        </Panel>
      )}
    </div>
  );
}

interface SourcePanelProps {
  functionId: string;
  versionId: string;
  source: FunctionVersionSourceResponse | null;
  sourceLoaded: boolean;
  isDraft: boolean;
  onSubmitted: () => void;
  onError: (message: string) => void;
}

function SourcePanel({ functionId, versionId, source, sourceLoaded, isDraft, onSubmitted, onError }: SourcePanelProps) {
  const [editing, setEditing] = useState(false);
  const [code, setCode] = useState(DEFAULT_SOURCE);
  const [entrypoint, setEntrypoint] = useState("index.mjs");
  const [handler, setHandler] = useState("handler");
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Derive the initial editing mode once the real fetch settles, adjusting
  // state during render (React's documented pattern for this) rather than
  // in an effect - `initializedFor` guards it to fire only on that one
  // loading->loaded transition, so a user's own "Replace source"/"Cancel"
  // toggle is never overwritten by a later refresh.
  const [initializedFor, setInitializedFor] = useState(false);
  if (sourceLoaded && !initializedFor) {
    setInitializedFor(true);
    setEditing(!source && isDraft);
  }

  if (!sourceLoaded) {
    return (
      <Panel className="p-4">
        <p className="text-sm text-muted">Loading source…</p>
      </Panel>
    );
  }

  async function handleFilePicked(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    setCode(text);
    setEntrypoint(file.name);
  }

  async function handleSubmit() {
    onError("");
    setBusy(true);
    try {
      const file = new File([code], entrypoint, { type: "text/javascript" });
      await api.submitFunctionVersionSource(functionId, versionId, file, entrypoint, handler);
      setEditing(false);
      onSubmitted();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to submit source");
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <Panel className="p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-foreground">Source</p>
          {isDraft && (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              <UploadIcon className="h-3.5 w-3.5" />
              Replace source
            </Button>
          )}
        </div>
        {source ? (
          <dl className="mt-2 grid gap-1.5 text-xs">
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Entrypoint</dt>
              <dd className="font-mono text-foreground">{source.entrypoint}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Handler</dt>
              <dd className="font-mono text-foreground">{source.handler}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="w-24 shrink-0 text-muted">Files</dt>
              <dd className="font-mono text-foreground">{source.relativePaths.join(", ")}</dd>
            </div>
          </dl>
        ) : (
          <p className="mt-2 text-sm text-muted">No source submitted yet.</p>
        )}
      </Panel>
    );
  }

  return (
    <div className={`${panelClass} flex flex-col gap-4 p-4`}>
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-foreground">Source editor</p>
        <div className="flex gap-2">
          <input ref={fileInputRef} type="file" accept=".mjs,.js,.ts" className="hidden" onChange={handleFilePicked} />
          <Button variant="secondary" size="sm" onClick={() => fileInputRef.current?.click()}>
            <UploadIcon className="h-3.5 w-3.5" />
            Upload file
          </Button>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className={fieldClass}>
          <span className={labelClass}>Entrypoint</span>
          <input
            type="text"
            value={entrypoint}
            onChange={(e) => setEntrypoint(e.target.value)}
            className={`${inputClass} font-mono`}
          />
        </label>
        <label className={fieldClass}>
          <span className={labelClass}>Handler (exported function name)</span>
          <input
            type="text"
            value={handler}
            onChange={(e) => setHandler(e.target.value)}
            className={`${inputClass} font-mono`}
          />
        </label>
      </div>

      <label className={fieldClass}>
        <span className={labelClass}>Code</span>
        <textarea
          value={code}
          onChange={(e) => setCode(e.target.value)}
          rows={10}
          spellCheck={false}
          className={`${inputClass} h-auto resize-y py-2 font-mono text-xs leading-relaxed`}
        />
      </label>

      <div className="flex gap-2">
        <Button variant="primary" size="sm" disabled={busy} onClick={handleSubmit}>
          Submit source
        </Button>
        {source && (
          <Button variant="secondary" size="sm" onClick={() => setEditing(false)}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}

interface TestInvokePanelProps {
  functionId: string;
  versionId: string;
  onError: (message: string) => void;
}

function TestInvokePanel({ functionId, versionId, onError }: TestInvokePanelProps) {
  const [input, setInput] = useState("{}");
  const [busy, setBusy] = useState(false);
  const [invocationId, setInvocationId] = useState<string | null>(null);
  const [initialStatus, setInitialStatus] = useState<string | null>(null);
  const [inspection, setInspection] = useState<InvocationInspectionResponse | null>(null);
  const [inspecting, setInspecting] = useState(false);

  async function handleRun() {
    onError("");
    setInspection(null);
    setBusy(true);
    try {
      const result = await api.invokeFunctionVersion(functionId, versionId, input);
      setInvocationId(result.invocationId);
      setInitialStatus(result.initialStatus);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to invoke");
    } finally {
      setBusy(false);
    }
  }

  async function handleInspect() {
    if (!invocationId) return;
    setInspecting(true);
    try {
      const data = await api.getInvocation(invocationId);
      setInspection(data);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to inspect invocation");
    } finally {
      setInspecting(false);
    }
  }

  return (
    <Panel className="flex flex-col gap-4 p-4">
      <div className="flex items-center gap-2">
        <ZapIcon className="h-4 w-4 text-amber-600 dark:text-amber-400" />
        <p className="text-sm font-medium text-foreground">Test invoke</p>
      </div>
      <p className="text-xs text-muted">
        Runs this exact version directly, with no Flow or Gateway involved. The invocation is durably recorded, but
        (by design, for now) is not picked up by the Dispatcher for real execution - it will stay PENDING.
      </p>

      <label className={fieldClass}>
        <span className={labelClass}>Input payload (JSON)</span>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          rows={4}
          spellCheck={false}
          className={`${inputClass} h-auto resize-y py-2 font-mono text-xs leading-relaxed`}
        />
      </label>

      <div>
        <Button variant="primary" size="sm" disabled={busy} onClick={handleRun}>
          <PlayIcon className="h-3.5 w-3.5" />
          Run
        </Button>
      </div>

      {invocationId && (
        <div className="rounded-lg border border-border bg-background p-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs">
              <CheckIcon className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
              <span className="text-muted">Invocation created</span>
              <StatusBadge status={initialStatus ?? "PENDING"} />
            </div>
            <Button variant="secondary" size="sm" disabled={inspecting} onClick={handleInspect}>
              Inspect
            </Button>
          </div>
          <p className="mt-1.5 font-mono text-xs text-muted">{invocationId}</p>

          {inspection && (
            <div className="mt-3 border-t border-border pt-3">
              <dl className="grid gap-1.5 text-xs">
                <div className="flex gap-2">
                  <dt className="w-20 shrink-0 text-muted">Status</dt>
                  <dd>
                    <StatusBadge status={inspection.status} />
                  </dd>
                </div>
                <div className="flex gap-2">
                  <dt className="w-20 shrink-0 text-muted">Input</dt>
                  <dd className="break-all font-mono text-foreground">{inspection.inputPayload}</dd>
                </div>
                {inspection.result && (
                  <div className="flex gap-2">
                    <dt className="w-20 shrink-0 text-muted">Result</dt>
                    <dd className="break-all font-mono text-foreground">{inspection.result}</dd>
                  </div>
                )}
                {inspection.error && (
                  <div className="flex gap-2">
                    <dt className="w-20 shrink-0 text-muted">Error</dt>
                    <dd className="break-all font-mono text-rose-600 dark:text-rose-400">{inspection.error}</dd>
                  </div>
                )}
              </dl>
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}
