interface AssignmentPolicyBadgeProps {
  policy: string;
}

const toneMap: Record<string, string> = {
  MANUAL: 'bg-slate-100 text-slate-900 border-slate-200',
  SUGGESTED: 'bg-amber-900/40 text-amber-300 border-amber-200',
  AUTOMATIC: 'bg-emerald-900/40 text-emerald-300 border-emerald-200',
};

export function AssignmentPolicyBadge({ policy }: AssignmentPolicyBadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center border px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em]',
        toneMap[policy] ?? 'bg-muted text-foreground border-border',
      ].join(' ')}
    >
      {policy}
    </span>
  );
}
