interface SignalBadgeProps {
  signalType: string;
}

export function SignalBadge({ signalType }: SignalBadgeProps) {
  const tone =
    signalType === 'BLOCKER_RAISED'
      ? 'bg-rose-100 text-rose-900'
      : signalType === 'WORK_FAILED' || signalType === 'WORK_CANCELLED'
        ? 'bg-stone-200 text-stone-900'
        : signalType === 'REVIEW_REQUESTED'
          ? 'bg-amber-100 text-amber-900'
      : signalType === 'WORK_COMPLETED'
        ? 'bg-emerald-100 text-emerald-900'
        : signalType === 'WORK_STARTED'
          ? 'bg-primary/10 text-foreground'
          : 'bg-muted text-muted-foreground';

  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] ${tone}`}>
      {signalType}
    </span>
  );
}
