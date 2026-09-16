import type { InvocationStepInspectionResponse } from "@/lib/types";

interface OutputLogProps {
  steps: InvocationStepInspectionResponse[];
}

export function OutputLog({ steps }: OutputLogProps) {
  const totalLines = steps.reduce((sum, step) => sum + step.logs.length, 0);
  const multiStep = steps.length > 1;

  return (
    <div className="mt-3 border-t border-border pt-3">
      <p className="text-xs font-medium text-foreground">Output log</p>
      {totalLines === 0 ? (
        <p className="mt-1.5 text-xs text-muted">No console output was logged.</p>
      ) : (
        <div className="mt-1.5 space-y-2">
          {steps.map((step) => (
            <div key={step.stepId} className="rounded-md bg-background">
              {multiStep && (
                <p className="px-1 pb-1 font-mono text-[11px] text-muted">
                  #{step.position} {step.componentType}
                </p>
              )}
              {step.logs.length > 0 && (
                <pre className="max-h-64 overflow-auto rounded-md border border-border bg-surface-hover p-2 font-mono text-[11px] leading-relaxed">
                  {step.logs.map((log, index) => (
                    <div
                      key={index}
                      className={log.stream === "stderr" ? "text-rose-600 dark:text-rose-400" : "text-foreground"}
                    >
                      <span className="select-none text-muted">{log.stream === "stderr" ? "! " : "  "}</span>
                      {log.message}
                    </div>
                  ))}
                </pre>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
