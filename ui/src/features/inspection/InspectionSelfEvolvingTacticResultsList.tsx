import type { SelfEvolvingTacticSearchResult } from '../../lib/api/selfEvolvingApi';

export function InspectionSelfEvolvingTacticResultsList({
  results,
  selectedTacticId,
  onSelectTacticId,
}: {
  results: SelfEvolvingTacticSearchResult[];
  selectedTacticId: string | null;
  onSelectTacticId: (tacticId: string) => void;
}) {
  return (
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-foreground">Tactic results</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Readonly mirrored ranking from the worker workspace.
          </p>
        </div>
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
          {results.length} result{results.length === 1 ? '' : 's'}
        </span>
      </div>

      {results.length === 0 ? (
        <p className="mt-4 text-sm text-muted-foreground">No tactics matched this query.</p>
      ) : (
        <div className="mt-4 grid gap-3">
          {results.map((result) => {
            const selected = result.tacticId === selectedTacticId;
            return (
              <button
                key={result.tacticId}
                type="button"
                onClick={() => onSelectTacticId(result.tacticId)}
                className={selected
                  ? 'grid gap-2 border border-foreground bg-foreground px-3 py-3 text-left text-white'
                  : 'grid gap-2 border border-border bg-white/90 px-3 py-3 text-left text-foreground'}
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="text-sm font-semibold">{result.title ?? result.tacticId}</span>
                  <span className={selected
                    ? 'bg-white/15 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-white/90'
                    : 'bg-secondary/10 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-secondary'}
                  >
                    {formatNumber(result.score)}
                  </span>
                </div>
                <p className={selected ? 'text-xs text-white/80' : 'text-xs text-muted-foreground'}>
                  {result.intentSummary ?? result.behaviorSummary ?? 'No intent summary.'}
                </p>
                <div className="flex flex-wrap gap-2 text-[11px] font-semibold uppercase tracking-[0.12em]">
                  <span>{result.promotionState ?? 'n/a'}</span>
                  <span>{formatPercent(result.successRate)} success</span>
                  <span>{formatPercent(result.benchmarkWinRate)} benchmark</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}

function formatPercent(value: number | null): string {
  return value == null ? 'n/a' : `${Math.round(value * 100)}%`;
}

function formatNumber(value: number | null): string {
  return value == null ? 'n/a' : value.toFixed(2);
}
