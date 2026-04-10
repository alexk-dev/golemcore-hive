import type { SelfEvolvingTacticSearchStatus } from '../../lib/api/selfEvolvingApi';

export function InspectionSelfEvolvingTacticSearchStatusBanner({
  status,
}: {
  status: SelfEvolvingTacticSearchStatus | null;
}) {
  if (status == null) {
    return (
      <section className="border border-dashed border-border bg-muted/70 p-3 text-sm text-muted-foreground">
        Search status unavailable.
      </section>
    );
  }

  const headline = status.degraded ? 'Embeddings degraded' : 'Hybrid search healthy';

  return (
    <section className={status.degraded
      ? 'border border-amber-700 bg-amber-950/40 p-3'
      : 'border border-emerald-300 bg-emerald-50 p-3'}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold text-foreground">{headline}</span>
        <span className="border border-border bg-panel/80 px-2 py-0.5 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
          {status.mode ?? 'unknown'}
        </span>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        {status.reason ?? 'No degradation reason recorded.'}
      </p>
    </section>
  );
}
