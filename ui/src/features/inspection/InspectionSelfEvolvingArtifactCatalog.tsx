import type { SelfEvolvingArtifactCatalogEntry } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingArtifactCatalog({
  artifacts,
  selectedArtifactStreamId,
  isLoading,
  onSelectArtifactStream,
}: {
  artifacts: SelfEvolvingArtifactCatalogEntry[];
  selectedArtifactStreamId: string | null;
  isLoading: boolean;
  onSelectArtifactStream: (artifactStreamId: string) => void;
}) {
  return (
    <InspectionReadonlySection
      title="Artifacts"
      description="Readonly catalog of evolved artifact streams mirrored from the selected golem."
    >
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading mirrored artifacts...</p>
      ) : artifacts.length === 0 ? (
        <p className="text-sm text-muted-foreground">No mirrored artifact streams are available yet.</p>
      ) : (
        <div className="grid gap-2">
          {artifacts.map((artifact) => {
            const selected = artifact.artifactStreamId === selectedArtifactStreamId;
            return (
              <button
                key={artifact.artifactStreamId}
                type="button"
                onClick={() => onSelectArtifactStream(artifact.artifactStreamId)}
                className={
                  selected
                    ? 'border border-primary/40 bg-primary/5 p-3 text-left'
                    : 'border border-border/70 bg-white/70 p-3 text-left transition hover:bg-white'
                }
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-foreground">
                      {artifact.displayName || artifact.artifactKey || artifact.artifactStreamId}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {artifact.artifactType || 'artifact'} · {artifact.currentRolloutStage || 'unknown'}
                    </p>
                    <p className="mt-1 truncate text-[11px] text-muted-foreground">
                      {artifact.artifactAliases.join(', ') || artifact.artifactStreamId}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    {artifact.hasPendingApproval ? (
                      <span className="inline-flex items-center border border-amber-300 bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-amber-900">
                        approval
                      </span>
                    ) : null}
                    {artifact.hasRegression ? (
                      <span className="inline-flex items-center border border-rose-300 bg-rose-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-rose-900">
                        regression
                      </span>
                    ) : null}
                    {artifact.stale ? (
                      <span className="inline-flex items-center border border-slate-300 bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-900">
                        stale
                      </span>
                    ) : null}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </InspectionReadonlySection>
  );
}
