"use client";

import { useState } from "react";
import { Button } from "@/components/Button";
import { CheckIcon, CopyIcon } from "@/components/icons";

export function CopyableCommand({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard access can be denied by the browser - the value is
      // still shown on screen, so this isn't fatal, just a lost convenience.
    }
  }

  return (
    <div className="flex items-start gap-2">
      {/* pre-wrap (not plain pre): preserves real embedded newlines (e.g. a
          multi-line command) while still wrapping an overly long single
          line instead of forcing horizontal scroll. */}
      <pre className="flex-1 whitespace-pre-wrap break-all rounded-xl border border-border bg-[#070709] px-3 py-2 font-mono text-xs leading-5 text-muted-strong shadow-inner shadow-black/40">
        {value}
      </pre>
      <Button variant="secondary" size="icon" title="Copy" onClick={copy}>
        {copied ? <CheckIcon className="h-4 w-4 text-success" /> : <CopyIcon className="h-4 w-4" />}
      </Button>
    </div>
  );
}
