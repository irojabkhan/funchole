import type { HTMLAttributes } from "react";

export const panelClass = "rounded-2xl border border-border bg-surface/90 shadow-[0_18px_70px_rgba(0,0,0,0.22)] backdrop-blur";

export function Panel({ className = "", ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`${panelClass} ${className}`} {...props} />;
}
