import { useState } from 'react';
import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import type {
  SelfEvolvingCampaign,
  SelfEvolvingCandidate,
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactLineage,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
  SelfEvolvingLineageResponse,
  SelfEvolvingTacticSearchResponse,
  SelfEvolvingRun,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingApprovalPanel } from './InspectionSelfEvolvingApprovalPanel';
import { InspectionSelfEvolvingArtifactWorkspace } from './InspectionSelfEvolvingArtifactWorkspace';
import { InspectionSelfEvolvingBenchmarkLab } from './InspectionSelfEvolvingBenchmarkLab';
import { InspectionSelfEvolvingCandidateQueue } from './InspectionSelfEvolvingCandidateQueue';
import { InspectionSelfEvolvingLineageGraph } from './InspectionSelfEvolvingLineageGraph';
import { InspectionSelfEvolvingRunTable } from './InspectionSelfEvolvingRunTable';
import { InspectionSelfEvolvingTacticWorkspace } from './InspectionSelfEvolvingTacticWorkspace';
import { InspectionSelfEvolvingVerdictPanel } from './InspectionSelfEvolvingVerdictPanel';
import { handleTablistArrowKeys, tabId, tabPanelId } from './tabListNavigation';

type ControlSurface =
  | 'runs'
  | 'candidates'
  | 'approvals'
  | 'artifacts'
  | 'lineage'
  | 'tactics'
  | 'benchmarks';

const SURFACE_NAMESPACE = 'self-evolving';
const SURFACE_ORDER: ControlSurface[] = [
  'runs',
  'candidates',
  'approvals',
  'artifacts',
  'lineage',
  'tactics',
  'benchmarks',
];

interface InspectionSelfEvolvingSectionProps {
  runs: SelfEvolvingRun[];
  selectedRunId: string | null;
  selectedRun: SelfEvolvingRun | null;
  candidates: SelfEvolvingCandidate[];
  campaigns: SelfEvolvingCampaign[];
  lineage: SelfEvolvingLineageResponse;
  artifacts: SelfEvolvingArtifactCatalogEntry[];
  selectedArtifactStreamId: string | null;
  artifactLineage: SelfEvolvingArtifactLineage | null;
  artifactCompareMode: 'revision' | 'transition';
  artifactRevisionDiff: SelfEvolvingArtifactRevisionDiff | null;
  artifactTransitionDiff: SelfEvolvingArtifactTransitionDiff | null;
  artifactEvidence: SelfEvolvingArtifactEvidence | null;
  tacticQuery: string;
  tacticSearchResponse: SelfEvolvingTacticSearchResponse | null;
  selectedTacticId: string | null;
  isArtifactsLoading: boolean;
  isArtifactLineageLoading: boolean;
  isArtifactDiffLoading: boolean;
  isArtifactEvidenceLoading: boolean;
  promotionApprovals: ApprovalRequest[];
  onSelectRun: (runId: string) => void;
  onSelectArtifactStream: (artifactStreamId: string) => void;
  onSelectArtifactCompareMode: (compareMode: 'revision' | 'transition') => void;
  onSelectArtifactRevisionPair: (fromRevisionId: string, toRevisionId: string) => void;
  onSelectArtifactTransitionPair: (fromNodeId: string, toNodeId: string) => void;
  onTacticQueryChange: (query: string) => void;
  onSelectTacticId: (tacticId: string) => void;
}

export function InspectionSelfEvolvingSection(props: InspectionSelfEvolvingSectionProps) {
  const {
    runs,
    candidates,
    campaigns,
    promotionApprovals,
    lineage,
    artifacts,
  } = props;

  const [surface, setSurface] = useState<ControlSurface>('runs');

  const pendingApprovals = promotionApprovals.filter((approval) => approval.status === 'PENDING').length;
  const completedRuns = runs.filter((run) => run.outcomeStatus === 'COMPLETED').length;
  const latestRun = runs[0] ?? null;

  const surfaces: Array<{ key: ControlSurface; label: string; count?: number }> = [
    { key: 'runs', label: 'Runs', count: runs.length },
    { key: 'candidates', label: 'Candidates', count: candidates.length },
    { key: 'approvals', label: 'Approvals', count: pendingApprovals },
    { key: 'artifacts', label: 'Artifacts', count: artifacts.length },
    { key: 'lineage', label: 'Lineage', count: lineage.nodes.length },
    { key: 'tactics', label: 'Tactics' },
    { key: 'benchmarks', label: 'Benchmarks', count: campaigns.length },
  ];

  return (
    <div className="grid gap-4">
      <section className="panel p-0">
        <div className="grid grid-cols-2 divide-x divide-y divide-border/40 sm:grid-cols-3 xl:grid-cols-5 xl:divide-y-0">
          <KpiTile label="Runs" value={String(runs.length)} detail={`${completedRuns} completed`} />
          <KpiTile label="Candidates" value={String(candidates.length)} detail="queued" />
          <KpiTile label="Approvals" value={String(pendingApprovals)} detail="pending gates" tone={pendingApprovals > 0 ? 'warning' : 'muted'} />
          <KpiTile label="Benchmarks" value={String(campaigns.length)} detail="campaigns" />
          <KpiTile
            label="Latest verdict"
            value={latestRun?.outcomeStatus ?? 'n/a'}
            detail={latestRun?.promotionRecommendation ?? 'no recommendation'}
          />
        </div>
      </section>

      <nav
        role="tablist"
        aria-label="Self-evolving control surfaces"
        className="flex flex-wrap gap-1 rounded-xl border border-border/60 bg-panel/70 p-1"
      >
        {surfaces.map((entry) => {
          const isActive = entry.key === surface;
          return (
            <button
              key={entry.key}
              id={tabId(SURFACE_NAMESPACE, entry.key)}
              type="button"
              role="tab"
              aria-selected={isActive}
              aria-controls={tabPanelId(SURFACE_NAMESPACE, entry.key)}
              tabIndex={isActive ? 0 : -1}
              onClick={() => setSurface(entry.key)}
              onKeyDown={(event) => handleTablistArrowKeys(event, SURFACE_ORDER, surface, setSurface)}
              className={[
                'flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold transition',
                isActive
                  ? 'bg-primary/15 text-foreground shadow-[inset_0_0_0_1px_hsl(var(--primary)/0.45)]'
                  : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground',
              ].join(' ')}
            >
              <span>{entry.label}</span>
              {entry.count != null ? (
                <span
                  className={[
                    'min-w-[1.25rem] rounded-full px-1.5 text-center text-[10px] font-bold',
                    isActive ? 'bg-primary/25 text-foreground' : 'bg-muted/80 text-muted-foreground',
                  ].join(' ')}
                >
                  {entry.count}
                </span>
              ) : null}
            </button>
          );
        })}
      </nav>

      <div
        role="tabpanel"
        id={tabPanelId(SURFACE_NAMESPACE, surface)}
        aria-labelledby={tabId(SURFACE_NAMESPACE, surface)}
        className="min-h-[320px]"
      >
        <SelfEvolvingSurface surface={surface} {...props} />
      </div>
    </div>
  );
}

function SelfEvolvingSurface({
  surface,
  runs,
  selectedRunId,
  selectedRun,
  candidates,
  campaigns,
  lineage,
  artifacts,
  selectedArtifactStreamId,
  artifactLineage,
  artifactCompareMode,
  artifactRevisionDiff,
  artifactTransitionDiff,
  artifactEvidence,
  tacticQuery,
  tacticSearchResponse,
  selectedTacticId,
  isArtifactsLoading,
  isArtifactLineageLoading,
  isArtifactDiffLoading,
  isArtifactEvidenceLoading,
  promotionApprovals,
  onSelectRun,
  onSelectArtifactStream,
  onSelectArtifactCompareMode,
  onSelectArtifactRevisionPair,
  onSelectArtifactTransitionPair,
  onTacticQueryChange,
  onSelectTacticId,
}: InspectionSelfEvolvingSectionProps & { surface: ControlSurface }) {
  if (surface === 'runs') {
    return (
      <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <InspectionSelfEvolvingRunTable runs={runs} selectedRunId={selectedRunId} onSelectRun={onSelectRun} />
        <InspectionSelfEvolvingVerdictPanel run={selectedRun} />
      </div>
    );
  }

  if (surface === 'candidates') {
    return <InspectionSelfEvolvingCandidateQueue candidates={candidates} />;
  }

  if (surface === 'approvals') {
    return <InspectionSelfEvolvingApprovalPanel approvals={promotionApprovals} />;
  }

  if (surface === 'artifacts') {
    return (
      <InspectionSelfEvolvingArtifactWorkspace
        artifacts={artifacts}
        selectedArtifactStreamId={selectedArtifactStreamId}
        lineage={artifactLineage}
        compareMode={artifactCompareMode}
        revisionDiff={artifactRevisionDiff}
        transitionDiff={artifactTransitionDiff}
        evidence={artifactEvidence}
        isCatalogLoading={isArtifactsLoading}
        isLineageLoading={isArtifactLineageLoading}
        isDiffLoading={isArtifactDiffLoading}
        isEvidenceLoading={isArtifactEvidenceLoading}
        onSelectArtifactStream={onSelectArtifactStream}
        onSelectCompareMode={onSelectArtifactCompareMode}
        onSelectRevisionPair={onSelectArtifactRevisionPair}
        onSelectTransitionPair={onSelectArtifactTransitionPair}
      />
    );
  }

  if (surface === 'lineage') {
    return <InspectionSelfEvolvingLineageGraph lineage={lineage} />;
  }

  if (surface === 'tactics') {
    return (
      <InspectionSelfEvolvingTacticWorkspace
        query={tacticQuery}
        onQueryChange={onTacticQueryChange}
        response={tacticSearchResponse}
        selectedTacticId={selectedTacticId}
        onSelectTacticId={onSelectTacticId}
        onOpenArtifactStream={onSelectArtifactStream}
      />
    );
  }

  return (
    <InspectionSelfEvolvingBenchmarkLab
      campaigns={campaigns}
      selectedArtifactStreamId={selectedArtifactStreamId}
    />
  );
}

function KpiTile({
  label,
  value,
  detail,
  tone = 'muted',
}: {
  label: string;
  value: string;
  detail: string;
  tone?: 'muted' | 'warning';
}) {
  const valueColor = tone === 'warning' ? 'text-amber-200' : 'text-foreground';
  return (
    <div className="p-4">
      <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">{label}</p>
      <p className={`mt-1 text-lg font-bold ${valueColor}`}>{value}</p>
      <p className="mt-0.5 text-[11px] text-muted-foreground">{detail}</p>
    </div>
  );
}
