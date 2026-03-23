const dotColor: Record<string, string> = {
  ONLINE: 'bg-emerald-500',
  DEGRADED: 'bg-amber-500',
  OFFLINE: 'bg-slate-400',
  PAUSED: 'bg-sky-500',
  REVOKED: 'bg-rose-500',
  PENDING_ENROLLMENT: 'bg-orange-400',
};

interface GolemStatusBadgeProps {
  state: string;
}

export function GolemStatusBadge({ state }: GolemStatusBadgeProps) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className={`inline-block h-1.5 w-1.5 shrink-0 ${dotColor[state] ?? 'bg-muted-foreground'}`} />
      {state.replace(/_/g, ' ').toLowerCase()}
    </span>
  );
}
