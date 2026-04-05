import type { SelfEvolvingRun } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingRunTable({
  runs,
  selectedRunId,
  onSelectRun,
}: {
  runs: SelfEvolvingRun[];
  selectedRunId: string | null;
  onSelectRun: (runId: string) => void;
}) {
  return (
    <InspectionReadonlySection
      title="Runs"
      description="Readonly SelfEvolving runs captured for this golem."
    >
      <div className="grid gap-2">
        {runs.length === 0 ? (
          <p className="text-sm text-muted-foreground">No SelfEvolving runs projected yet.</p>
        ) : (
          runs.map((run) => (
            <button
              key={run.id}
              type="button"
              onClick={() => onSelectRun(run.id)}
              className={
                run.id === selectedRunId
                  ? 'border border-primary/40 bg-primary/5 p-3 text-left'
                  : 'border border-border/70 bg-white/70 p-3 text-left transition hover:bg-white'
              }
            >
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">{run.id}</span>
                <span className="text-xs text-muted-foreground">{run.status}</span>
              </div>
              <div className="mt-2 flex flex-wrap gap-3 text-xs text-muted-foreground">
                <span>{run.outcomeStatus ?? 'unknown outcome'}</span>
                <span>{run.processStatus ?? 'unknown process'}</span>
                <span>{run.promotionRecommendation ?? 'no recommendation'}</span>
              </div>
            </button>
          ))
        )}
      </div>
    </InspectionReadonlySection>
  );
}
