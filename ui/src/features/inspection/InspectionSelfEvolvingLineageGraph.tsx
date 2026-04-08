import { formatTimestamp } from '../../lib/format';
import type { SelfEvolvingLineageResponse } from '../../lib/api/selfEvolvingApi';
import { InspectionReadonlySection } from './InspectionPageSections';

export function InspectionSelfEvolvingLineageGraph({ lineage }: { lineage: SelfEvolvingLineageResponse }) {
  return (
    <InspectionReadonlySection
      title="Lineage"
      description="Readonly lineage nodes derived from SelfEvolving projections."
    >
      <div className="grid gap-2">
        {lineage.nodes.length === 0 ? (
          <p className="text-sm text-muted-foreground">No lineage nodes available yet.</p>
        ) : (
          lineage.nodes.map((node) => (
            <div key={node.id} className="border border-border/70 bg-white/70 p-3">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-semibold text-foreground">{node.id}</span>
                <span className="text-xs text-muted-foreground">{node.status ?? 'unknown'}</span>
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {node.parentId ? `parent ${node.parentId}` : 'root node'}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                {(node.artifactType ?? 'artifact')} · {formatTimestamp(node.updatedAt)}
              </p>
            </div>
          ))
        )}
      </div>
    </InspectionReadonlySection>
  );
}
