import type { SelfEvolvingArtifactLineage } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingArtifactLineageRail({
  lineage,
  isLoading,
}: {
  lineage: SelfEvolvingArtifactLineage | null;
  isLoading: boolean;
}) {
  return (
    <InspectionReadonlySection
      title="Lineage rail"
      description="Readonly rollout chain from proposal through activation and revert."
    >
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading lineage...</p>
      ) : lineage == null || lineage.nodes.length === 0 ? (
        <p className="text-sm text-muted-foreground">Select an artifact stream to inspect rollout history.</p>
      ) : (
        <div className="grid gap-2">
          {lineage.railOrder.map((nodeId) => {
            const node = lineage.nodes.find((candidateNode) => candidateNode.nodeId === nodeId);
            if (node == null) {
              return null;
            }
            return (
              <article key={node.nodeId} className="border border-border/70 bg-panel/80 p-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-foreground">{node.rolloutStage || node.nodeId}</p>
                    <p className="text-xs text-muted-foreground">
                      {node.lifecycleState || 'unknown'} · {node.attributionMode || 'unclassified'}
                    </p>
                  </div>
                  <span className="inline-flex items-center border border-border bg-panel px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {node.contentRevisionId || 'n/a'}
                  </span>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </InspectionReadonlySection>
  );
}
