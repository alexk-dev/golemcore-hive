interface AssignmentPolicyBadgeProps {
  policy: string;
}

const toneMap: Record<string, string> = {
  MANUAL: 'bg-slate-100 text-slate-900 border-slate-200',
  SUGGESTED: 'bg-amber-100 text-amber-900 border-amber-200',
  AUTOMATIC: 'bg-emerald-100 text-emerald-900 border-emerald-200',
};

export function AssignmentPolicyBadge({ policy }: AssignmentPolicyBadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center rounded-full border px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em]',
        toneMap[policy] ?? 'bg-muted text-foreground border-border',
      ].join(' ')}
    >
      {policy}
    </span>
  );
}
