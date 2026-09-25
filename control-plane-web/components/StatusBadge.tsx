const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "border-success/30 bg-success/10 text-success",
  VERIFIED: "border-success/30 bg-success/10 text-success",
  ADOPTED: "border-success/30 bg-success/10 text-success",
  READY: "border-success/30 bg-success/10 text-success",
  COMPLETED: "border-success/30 bg-success/10 text-success",
  PENDING: "border-accent-border bg-accent-soft text-accent",
  DRAFT: "border-accent-border bg-accent-soft text-accent",
  PUBLISHING: "border-info/30 bg-info/10 text-info",
  FAILED: "border-danger/30 bg-danger/10 text-danger",
  REVOKED: "border-danger/30 bg-danger/10 text-danger",
  REJECTED: "border-danger/30 bg-danger/10 text-danger",
  EXPIRED: "border-danger/30 bg-danger/10 text-danger",
  ARCHIVED: "border-violet/30 bg-violet/10 text-violet",
  INACTIVE: "border-border bg-surface-hover text-muted",
};

export function StatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] ?? "border-border bg-surface-hover text-muted";
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold ${style}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {status}
    </span>
  );
}
