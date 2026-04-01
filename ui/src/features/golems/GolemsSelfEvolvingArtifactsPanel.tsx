import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import type { GolemSummary } from '../../lib/api/golemsApi';
import {
  compareSelfEvolvingArtifacts,
  searchSelfEvolvingArtifacts,
  type SelfEvolvingArtifactFleetCompare,
  type SelfEvolvingArtifactCatalogEntry,
} from '../../lib/api/selfEvolvingApi';

const ARTIFACT_TYPES = ['', 'skill', 'prompt', 'routing_policy', 'tool_policy', 'memory_policy', 'context_policy', 'governance_policy'];

export function GolemsSelfEvolvingArtifactsPanel({ golems }: { golems: GolemSummary[] }) {
  const [query, setQuery] = useState('');
  const [artifactType, setArtifactType] = useState('');
  const [leftSelection, setLeftSelection] = useState<SelfEvolvingArtifactCatalogEntry | null>(null);
  const [rightSelection, setRightSelection] = useState<SelfEvolvingArtifactCatalogEntry | null>(null);
  const deferredQuery = useDeferredValue(query);

  const searchQuery = useQuery({
    queryKey: ['fleet-self-evolving-artifacts', deferredQuery, artifactType],
    queryFn: () => searchSelfEvolvingArtifacts({
      query: deferredQuery || undefined,
      artifactType: artifactType || undefined,
    }),
  });

  const compareQuery = useQuery({
    queryKey: [
      'fleet-self-evolving-artifact-compare',
      leftSelection?.artifactStreamId,
      leftSelection?.golemId,
      rightSelection?.golemId,
      leftSelection?.activeRevisionId,
      rightSelection?.activeRevisionId,
    ],
    queryFn: () => compareSelfEvolvingArtifacts(
      leftSelection?.artifactStreamId ?? '',
      leftSelection?.golemId ?? '',
      rightSelection?.golemId ?? '',
      leftSelection?.activeRevisionId ?? leftSelection?.latestRevisionId ?? '',
      rightSelection?.activeRevisionId ?? rightSelection?.latestRevisionId ?? '',
    ),
    enabled: canCompare(leftSelection, rightSelection),
  });

  return (
    <section className="panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-foreground">Compare across golems</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Search mirrored artifact streams across the fleet and compare the same canonical stream between workers.
          </p>
        </div>
        <span className="text-xs text-muted-foreground">{golems.length} golems visible</span>
      </div>

      <div className="mt-4 grid gap-2 md:grid-cols-[minmax(0,1fr)_220px]">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search evolved artifacts"
          className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
        />
        <select
          value={artifactType}
          onChange={(event) => setArtifactType(event.target.value)}
          className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
        >
          {ARTIFACT_TYPES.map((value) => (
            <option key={value || 'all'} value={value}>
              {value || 'All artifact types'}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
        <ArtifactSearchResults
          artifacts={searchQuery.data ?? []}
          isLoading={searchQuery.isLoading}
          onSelectLeft={setLeftSelection}
          onSelectRight={setRightSelection}
        />

        <div className="grid gap-3">
          <SelectionCard label="Left" artifact={leftSelection} />
          <SelectionCard label="Right" artifact={rightSelection} />

          {!sameStream(leftSelection, rightSelection) && leftSelection && rightSelection ? (
            <div className="border border-amber-300 bg-amber-100 p-3 text-sm text-amber-900">
              Choose the same artifact stream on both sides to compare across golems.
            </div>
          ) : null}

          <CompareSummaryCard compareQuery={compareQuery} />
        </div>
      </div>
    </section>
  );
}

function ArtifactSearchResults({
  artifacts,
  isLoading,
  onSelectLeft,
  onSelectRight,
}: {
  artifacts: SelfEvolvingArtifactCatalogEntry[];
  isLoading: boolean;
  onSelectLeft: (artifact: SelfEvolvingArtifactCatalogEntry) => void;
  onSelectRight: (artifact: SelfEvolvingArtifactCatalogEntry) => void;
}) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Searching mirrored artifacts...</p>;
  }

  if (artifacts.length === 0) {
    return <p className="text-sm text-muted-foreground">No mirrored artifacts match the current filters.</p>;
  }

  return (
    <div className="grid gap-2">
      {artifacts.map((artifact) => (
        <article key={`${artifact.golemId}:${artifact.artifactStreamId}`} className="border border-border/70 bg-white/80 p-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-foreground">
                {artifact.displayName || artifact.artifactKey || artifact.artifactStreamId}
              </p>
              <p className="text-xs text-muted-foreground">
                {artifact.golemId || 'unknown golem'} · {artifact.artifactType || 'artifact'} · {artifact.currentRolloutStage || 'unknown'}
              </p>
            </div>
            {artifact.stale ? (
              <span className="inline-flex items-center border border-slate-300 bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-900">
                stale
              </span>
            ) : null}
          </div>
          {artifact.staleReason ? (
            <p className="mt-2 text-xs text-muted-foreground">{artifact.staleReason}</p>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onSelectLeft(artifact)}
              className="border border-border bg-white px-3 py-1 text-xs font-semibold text-foreground"
            >
              Set left
            </button>
            <button
              type="button"
              onClick={() => onSelectRight(artifact)}
              className="border border-border bg-white px-3 py-1 text-xs font-semibold text-foreground"
            >
              Set right
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function SelectionCard({ label, artifact }: { label: string; artifact: SelfEvolvingArtifactCatalogEntry | null }) {
  return (
    <article className="border border-border/70 bg-white/80 p-3">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      {artifact ? (
        <div className="mt-2">
          <p className="text-sm font-semibold text-foreground">
            {artifact.displayName || artifact.artifactKey || artifact.artifactStreamId}
          </p>
          <p className="text-xs text-muted-foreground">
            {artifact.golemId || 'unknown golem'} · {artifact.activeRevisionId || artifact.latestRevisionId || 'n/a'}
          </p>
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted-foreground">No artifact selected.</p>
      )}
    </article>
  );
}

function sameStream(
  leftSelection: SelfEvolvingArtifactCatalogEntry | null,
  rightSelection: SelfEvolvingArtifactCatalogEntry | null,
) {
  return leftSelection?.artifactStreamId != null
    && leftSelection.artifactStreamId === rightSelection?.artifactStreamId;
}

function canCompare(
  leftSelection: SelfEvolvingArtifactCatalogEntry | null,
  rightSelection: SelfEvolvingArtifactCatalogEntry | null,
) {
  return sameStream(leftSelection, rightSelection)
    && Boolean(leftSelection?.golemId)
    && Boolean(rightSelection?.golemId)
    && Boolean(leftSelection?.activeRevisionId ?? leftSelection?.latestRevisionId)
    && Boolean(rightSelection?.activeRevisionId ?? rightSelection?.latestRevisionId);
}

function CompareSummaryCard({
  compareQuery,
}: {
  compareQuery: UseQueryResult<SelfEvolvingArtifactFleetCompare>;
}) {
  return (
    <article className="border border-border/70 bg-white/80 p-3">
      <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">Compare summary</p>
      {compareQuery.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Computing fleet compare...</p>
      ) : compareQuery.data ? (
        <div className="mt-2 grid gap-2">
          <p className="text-sm font-semibold text-foreground">{compareQuery.data.summary || 'No compare summary available.'}</p>
          <p className="text-xs text-muted-foreground">
            {compareQuery.data.leftRevisionId} vs {compareQuery.data.rightRevisionId}
          </p>
          {compareQuery.data.warnings.map((warning) => (
            <p key={warning} className="text-xs text-amber-900">{warning}</p>
          ))}
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted-foreground">Select the same artifact stream on two golems to compare it here.</p>
      )}
    </article>
  );
}
