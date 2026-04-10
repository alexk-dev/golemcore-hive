import type { SelfEvolvingRun } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingVerdictPanel({ run }: { run: SelfEvolvingRun | null }) {
  return (
    <InspectionReadonlySection
      title="Judging"
      description="Outcome and process verdict for the selected SelfEvolving run."
    >
      {run == null ? (
        <p className="text-sm text-muted-foreground">Select a run to inspect outcome and process judging.</p>
      ) : (
        <div className="grid gap-3">
          <div className="grid gap-3 md:grid-cols-3">
            <VerdictCard label="Outcome" value={run.outcomeStatus ?? 'n/a'} detail={run.outcomeSummary} />
            <VerdictCard label="Process" value={run.processStatus ?? 'n/a'} detail={run.processSummary} />
            <VerdictCard
              label="Confidence"
              value={run.confidence != null ? run.confidence.toFixed(2) : 'n/a'}
              detail={run.promotionRecommendation}
            />
          </div>
          <div className="border border-border/70 bg-muted/70 p-3">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              Process findings
            </p>
            {run.processFindings.length === 0 ? (
              <p className="mt-2 text-sm text-muted-foreground">No process findings recorded.</p>
            ) : (
              <ul className="mt-2 grid gap-1 text-sm text-foreground">
                {run.processFindings.map((finding) => (
                  <li key={finding}>{finding}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </InspectionReadonlySection>
  );
}

function VerdictCard({ label, value, detail }: { label: string; value: string; detail: string | null }) {
  return (
    <div className="border border-border/70 bg-panel/80 p-3">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-lg font-bold text-foreground">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{detail ?? 'No detail recorded.'}</p>
    </div>
  );
}
