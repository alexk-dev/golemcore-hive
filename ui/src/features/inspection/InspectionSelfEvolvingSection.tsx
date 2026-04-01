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
  SelfEvolvingRun,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingApprovalPanel } from './InspectionSelfEvolvingApprovalPanel';
import { InspectionSelfEvolvingArtifactWorkspace } from './InspectionSelfEvolvingArtifactWorkspace';
import { InspectionSelfEvolvingBenchmarkLab } from './InspectionSelfEvolvingBenchmarkLab';
import { InspectionSelfEvolvingCandidateQueue } from './InspectionSelfEvolvingCandidateQueue';
import { InspectionSelfEvolvingLineageGraph } from './InspectionSelfEvolvingLineageGraph';
import { InspectionSelfEvolvingOverview } from './InspectionSelfEvolvingOverview';
import { InspectionSelfEvolvingRunTable } from './InspectionSelfEvolvingRunTable';
import { InspectionSelfEvolvingVerdictPanel } from './InspectionSelfEvolvingVerdictPanel';

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
}

export function InspectionSelfEvolvingSection({
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
}: InspectionSelfEvolvingSectionProps) {
  return (
    <>
      <InspectionSelfEvolvingOverview
        runs={runs}
        candidates={candidates}
        campaigns={campaigns}
        approvals={promotionApprovals}
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <InspectionSelfEvolvingRunTable
          runs={runs}
          selectedRunId={selectedRunId}
          onSelectRun={onSelectRun}
        />
        <InspectionSelfEvolvingVerdictPanel run={selectedRun} />
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <InspectionSelfEvolvingCandidateQueue candidates={candidates} />
        <InspectionSelfEvolvingLineageGraph lineage={lineage} />
        <InspectionSelfEvolvingApprovalPanel approvals={promotionApprovals} />
      </div>

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

      <InspectionSelfEvolvingBenchmarkLab
        campaigns={campaigns}
        selectedArtifactStreamId={selectedArtifactStreamId}
      />
    </>
  );
}
