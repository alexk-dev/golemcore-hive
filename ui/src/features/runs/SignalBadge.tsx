interface SignalBadgeProps {
  signalType: string;
}

export function SignalBadge({ signalType }: SignalBadgeProps) {
  const tone =
    signalType === 'BLOCKER_RAISED'
      ? 'bg-rose-900/40 text-rose-300'
      : signalType === 'WORK_FAILED' || signalType === 'WORK_CANCELLED'
        ? 'bg-stone-200 text-stone-900'
        : signalType === 'REVIEW_REQUESTED'
          ? 'bg-amber-900/40 text-amber-300'
      : signalType === 'WORK_COMPLETED'
        ? 'bg-emerald-900/40 text-emerald-300'
        : signalType === 'WORK_STARTED'
          ? 'bg-primary/10 text-foreground'
          : 'bg-muted text-muted-foreground';

  return (
    <span className={` px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] ${tone}`}>
      {signalType}
    </span>
  );
}
