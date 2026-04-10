import type { SelfEvolvingTacticSearchExplanation } from '../../lib/api/selfEvolvingApi';

export function InspectionSelfEvolvingTacticWhyPanel({
  explanation,
  successRate,
  benchmarkWinRate,
  regressionFlags,
  promotionState,
  recencyScore,
  golemLocalUsageSuccess,
}: {
  explanation: SelfEvolvingTacticSearchExplanation | null;
  successRate: number | null;
  benchmarkWinRate: number | null;
  regressionFlags: string[];
  promotionState: string | null;
  recencyScore: number | null;
  golemLocalUsageSuccess: number | null;
}) {
  const metricRows = buildMetricRows({
    explanation,
    successRate,
    benchmarkWinRate,
    regressionFlags,
    promotionState,
    recencyScore,
    golemLocalUsageSuccess,
  });

  return (
    <section className="panel p-4">
      <h2 className="text-sm font-bold text-foreground">Why this tactic</h2>
      <div className="mt-4 grid gap-2 text-sm">
        {metricRows.map((row) => (
          <MetricRow key={row.label} label={row.label} value={row.value} />
        ))}
      </div>
    </section>
  );
}

function buildMetricRows({
  explanation,
  successRate,
  benchmarkWinRate,
  regressionFlags,
  promotionState,
  recencyScore,
  golemLocalUsageSuccess,
}: {
  explanation: SelfEvolvingTacticSearchExplanation | null;
  successRate: number | null;
  benchmarkWinRate: number | null;
  regressionFlags: string[];
  promotionState: string | null;
  recencyScore: number | null;
  golemLocalUsageSuccess: number | null;
}) {
  return [
    ...buildOutcomeMetricRows({
      successRate,
      benchmarkWinRate,
      regressionFlags,
      promotionState,
      recencyScore,
      golemLocalUsageSuccess,
    }),
    ...buildSearchMetricRows(explanation),
  ];
}

function buildOutcomeMetricRows({
  successRate,
  benchmarkWinRate,
  regressionFlags,
  promotionState,
  recencyScore,
  golemLocalUsageSuccess,
}: {
  successRate: number | null;
  benchmarkWinRate: number | null;
  regressionFlags: string[];
  promotionState: string | null;
  recencyScore: number | null;
  golemLocalUsageSuccess: number | null;
}) {
  return [
    { label: 'Success rate', value: formatPercent(successRate) },
    { label: 'Benchmark win rate', value: formatPercent(benchmarkWinRate) },
    { label: 'Regression flags', value: formatList(regressionFlags) },
    { label: 'Promotion state', value: promotionState ?? 'n/a' },
    { label: 'Recency', value: formatNumber(recencyScore) },
    { label: 'Golem-local usage success', value: formatPercent(golemLocalUsageSuccess) },
  ];
}

function buildSearchMetricRows(explanation: SelfEvolvingTacticSearchExplanation | null) {
  return [
    ...buildSearchScoreRows(explanation),
    ...buildSearchExplainabilityRows(explanation),
  ];
}

function buildSearchScoreRows(explanation: SelfEvolvingTacticSearchExplanation | null) {
  return [
    { label: 'BM25 score', value: formatNumber(explanation?.bm25Score) },
    { label: 'Vector score', value: formatNumber(explanation?.vectorScore) },
    { label: 'RRF score', value: formatNumber(explanation?.rrfScore) },
    { label: 'Quality prior', value: formatNumber(explanation?.qualityPrior) },
    { label: 'MMR diversity adjustment', value: formatNumber(explanation?.mmrDiversityAdjustment) },
    { label: 'Negative memory penalty', value: formatNumber(explanation?.negativeMemoryPenalty) },
    { label: 'Personalization boost', value: formatNumber(explanation?.personalizationBoost) },
  ];
}

function buildSearchExplainabilityRows(explanation: SelfEvolvingTacticSearchExplanation | null) {
  return [
    { label: 'Reranker verdict', value: explanation?.rerankerVerdict ?? 'n/a' },
    { label: 'Matched query views', value: formatList(explanation?.matchedQueryViews ?? []) },
    { label: 'Matched terms', value: formatList(explanation?.matchedTerms ?? []) },
    { label: 'Eligible', value: formatBoolean(explanation?.eligible) },
    { label: 'Gating reason', value: explanation?.gatingReason ?? 'n/a' },
    { label: 'Degradation reason', value: explanation?.degradedReason ?? 'n/a' },
    { label: 'Final score', value: formatNumber(explanation?.finalScore) },
  ];
}

function MetricRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/70 py-1.5 last:border-b-0">
      <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">{label}</span>
      <span className="text-right text-sm text-foreground">{value}</span>
    </div>
  );
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? 'n/a' : `${Math.round(value * 100)}%`;
}

function formatNumber(value: number | null | undefined): string {
  return value == null ? 'n/a' : value.toFixed(2);
}

function formatList(values: string[] | null | undefined): string {
  return values == null || values.length === 0 ? 'n/a' : values.join(', ');
}

function formatBoolean(value: boolean | null | undefined): string {
  if (value == null) {
    return 'n/a';
  }
  return value ? 'yes' : 'no';
}
