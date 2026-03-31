import type { SelfEvolvingCandidate } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingCandidateQueue({ candidates }: { candidates: SelfEvolvingCandidate[] }) {
  return (
    <InspectionReadonlySection
      title="Candidate queue"
      description="Readonly promotion candidates and their latest projected state."
    >
      <div className="grid gap-2">
        {candidates.length === 0 ? (
          <p className="text-sm text-muted-foreground">No candidates queued for this golem.</p>
        ) : (
          candidates.map((candidate) => (
            <div key={candidate.id} className="border border-border/70 bg-white/70 p-3">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">{candidate.id}</span>
                <span className="text-xs text-muted-foreground">{candidate.status}</span>
              </div>
              <p className="mt-2 text-sm text-foreground">
                {(candidate.artifactType ?? 'artifact')} • {(candidate.goal ?? 'promotion')}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">{candidate.expectedImpact ?? 'No impact summary.'}</p>
            </div>
          ))
        )}
      </div>
    </InspectionReadonlySection>
  );
}
