const toneMap: Record<string, string> = {
  ONLINE: 'bg-emerald-100 text-emerald-900 border-emerald-200',
  DEGRADED: 'bg-amber-100 text-amber-900 border-amber-200',
  OFFLINE: 'bg-slate-200 text-slate-800 border-slate-300',
  PAUSED: 'bg-sky-100 text-sky-900 border-sky-200',
  REVOKED: 'bg-rose-100 text-rose-900 border-rose-200',
  PENDING_ENROLLMENT: 'bg-orange-100 text-orange-900 border-orange-200',
};

type GolemStatusBadgeProps = {
  state: string;
};

export function GolemStatusBadge({ state }: GolemStatusBadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]',
        toneMap[state] ?? 'bg-muted text-foreground border-border',
      ].join(' ')}
    >
      {state.replace(/_/g, ' ')}
    </span>
  );
}
