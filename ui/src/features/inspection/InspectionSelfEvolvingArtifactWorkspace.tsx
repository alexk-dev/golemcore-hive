import type {
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactLineage,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingArtifactCatalog } from './InspectionSelfEvolvingArtifactCatalog';
import { InspectionSelfEvolvingArtifactDiffPanel } from './InspectionSelfEvolvingArtifactDiffPanel';
import { InspectionSelfEvolvingArtifactLineageRail } from './InspectionSelfEvolvingArtifactLineageRail';

interface CompareOption {
  label: string;
  fromId: string;
  toId: string;
}

export function InspectionSelfEvolvingArtifactWorkspace({
  artifacts,
  selectedArtifactStreamId,
  lineage,
  compareMode,
  revisionDiff,
  transitionDiff,
  evidence,
  isCatalogLoading,
  isLineageLoading,
  isDiffLoading,
  isEvidenceLoading,
  onSelectArtifactStream,
  onSelectCompareMode,
  onSelectRevisionPair,
  onSelectTransitionPair,
}: {
  artifacts: SelfEvolvingArtifactCatalogEntry[];
  selectedArtifactStreamId: string | null;
  lineage: SelfEvolvingArtifactLineage | null;
  compareMode: 'revision' | 'transition';
  revisionDiff: SelfEvolvingArtifactRevisionDiff | null;
  transitionDiff: SelfEvolvingArtifactTransitionDiff | null;
  evidence: SelfEvolvingArtifactEvidence | null;
  isCatalogLoading: boolean;
  isLineageLoading: boolean;
  isDiffLoading: boolean;
  isEvidenceLoading: boolean;
  onSelectArtifactStream: (artifactStreamId: string) => void;
  onSelectCompareMode: (compareMode: 'revision' | 'transition') => void;
  onSelectRevisionPair: (fromRevisionId: string, toRevisionId: string) => void;
  onSelectTransitionPair: (fromNodeId: string, toNodeId: string) => void;
}) {
  const revisionOptions = buildRevisionOptions(lineage);
  const transitionOptions = buildTransitionOptions(lineage);
  const selectedRevisionValue = revisionDiff != null
    ? `${revisionDiff.fromRevisionId || ''}::${revisionDiff.toRevisionId || ''}`
    : revisionOptions.length > 0
      ? `${revisionOptions[0].fromId}::${revisionOptions[0].toId}`
      : '';
  const selectedTransitionValue = transitionDiff != null
    ? `${transitionDiff.fromNodeId || ''}::${transitionDiff.toNodeId || ''}`
    : transitionOptions.length > 0
      ? `${transitionOptions[0].fromId}::${transitionOptions[0].toId}`
      : '';

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,0.85fr)_minmax(0,1.25fr)]">
      <InspectionSelfEvolvingArtifactCatalog
        artifacts={artifacts}
        selectedArtifactStreamId={selectedArtifactStreamId}
        isLoading={isCatalogLoading}
        onSelectArtifactStream={onSelectArtifactStream}
      />

      <div className="grid gap-4">
        <InspectionSelfEvolvingArtifactLineageRail lineage={lineage} isLoading={isLineageLoading} />

        <section className="panel p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-bold text-foreground">Compare mode</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Switch between immutable revision diff and rollout-transition diff.
              </p>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onSelectCompareMode('transition')}
              className={
                compareMode === 'transition'
                  ? 'bg-foreground px-3 py-1.5 text-xs font-semibold text-white'
                  : 'border border-border bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground'
              }
            >
              Transition compare
            </button>
            <button
              type="button"
              onClick={() => onSelectCompareMode('revision')}
              className={
                compareMode === 'revision'
                  ? 'bg-foreground px-3 py-1.5 text-xs font-semibold text-white'
                  : 'border border-border bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground'
              }
            >
              Revision compare
            </button>
          </div>

          <div className="mt-4 grid gap-3">
            {compareMode === 'transition' ? (
              <label className="grid gap-1 text-xs font-semibold text-muted-foreground">
                Transition pair
                <select
                  className="border border-border bg-white px-3 py-2 text-sm text-foreground outline-none transition focus:border-primary"
                  onChange={(event) => {
                    const value = event.target.value;
                    const option = transitionOptions.find((candidate) => `${candidate.fromId}::${candidate.toId}` === value);
                    if (option) {
                      onSelectTransitionPair(option.fromId, option.toId);
                    }
                  }}
                  value={selectedTransitionValue}
                >
                  {transitionOptions.map((option) => (
                    <option key={`${option.fromId}:${option.toId}`} value={`${option.fromId}::${option.toId}`}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label className="grid gap-1 text-xs font-semibold text-muted-foreground">
                Revision pair
                <select
                  className="border border-border bg-white px-3 py-2 text-sm text-foreground outline-none transition focus:border-primary"
                  onChange={(event) => {
                    const value = event.target.value;
                    const option = revisionOptions.find((candidate) => `${candidate.fromId}::${candidate.toId}` === value);
                    if (option) {
                      onSelectRevisionPair(option.fromId, option.toId);
                    }
                  }}
                  value={selectedRevisionValue}
                >
                  {revisionOptions.map((option) => (
                    <option key={`${option.fromId}:${option.toId}`} value={`${option.fromId}::${option.toId}`}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
        </section>
      </div>

      <InspectionSelfEvolvingArtifactDiffPanel
        compareMode={compareMode}
        revisionDiff={revisionDiff}
        transitionDiff={transitionDiff}
        evidence={evidence}
        isDiffLoading={isDiffLoading}
        isEvidenceLoading={isEvidenceLoading}
      />
    </div>
  );
}

function buildRevisionOptions(lineage: SelfEvolvingArtifactLineage | null): CompareOption[] {
  if (lineage == null) {
    return [];
  }
  const orderedRevisions = lineage.railOrder
    .map((nodeId) => lineage.nodes.find((node) => node.nodeId === nodeId)?.contentRevisionId || null)
    .filter((value): value is string => value != null && value.length > 0);
  const uniqueRevisions = Array.from(new Set(orderedRevisions));
  const options: CompareOption[] = [];
  for (let index = 1; index < uniqueRevisions.length; index += 1) {
    options.push({
      label: `${uniqueRevisions[index - 1]} → ${uniqueRevisions[index]}`,
      fromId: uniqueRevisions[index - 1],
      toId: uniqueRevisions[index],
    });
  }
  if (options.length === 0 && uniqueRevisions.length === 1) {
    options.push({
      label: `${uniqueRevisions[0]} → ${uniqueRevisions[0]}`,
      fromId: uniqueRevisions[0],
      toId: uniqueRevisions[0],
    });
  }
  return options;
}

function buildTransitionOptions(lineage: SelfEvolvingArtifactLineage | null): CompareOption[] {
  if (lineage == null) {
    return [];
  }
  const options: CompareOption[] = [];
  for (let index = 1; index < lineage.railOrder.length; index += 1) {
    const fromNodeId = lineage.railOrder[index - 1];
    const toNodeId = lineage.railOrder[index];
    options.push({
      label: `${fromNodeId} → ${toNodeId}`,
      fromId: fromNodeId,
      toId: toNodeId,
    });
  }
  return options;
}
