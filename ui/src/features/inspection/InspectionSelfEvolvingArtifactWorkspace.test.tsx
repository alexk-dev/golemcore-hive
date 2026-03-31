import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactLineage,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingArtifactWorkspace } from './InspectionSelfEvolvingArtifactWorkspace';

describe('InspectionSelfEvolvingArtifactWorkspace', () => {
  it('renders readonly artifact browser and switches compare mode', async () => {
    const onSelectArtifactStream = vi.fn();
    const onSelectCompareMode = vi.fn();
    const onSelectRevisionPair = vi.fn();
    const onSelectTransitionPair = vi.fn();

    render(
      <InspectionSelfEvolvingArtifactWorkspace
        artifacts={[createArtifact()]}
        selectedArtifactStreamId="stream-1"
        lineage={createLineage()}
        compareMode="transition"
        revisionDiff={createRevisionDiff()}
        transitionDiff={createTransitionDiff()}
        evidence={createEvidence('transition')}
        isCatalogLoading={false}
        isLineageLoading={false}
        isDiffLoading={false}
        isEvidenceLoading={false}
        onSelectArtifactStream={onSelectArtifactStream}
        onSelectCompareMode={onSelectCompareMode}
        onSelectRevisionPair={onSelectRevisionPair}
        onSelectTransitionPair={onSelectTransitionPair}
      />,
    );

    expect(screen.getByText('Artifacts')).toBeInTheDocument();
    expect(screen.getByText('Planner skill')).toBeInTheDocument();
    expect(screen.getByText('Lineage rail')).toBeInTheDocument();
    expect(screen.getByText('Compare mode')).toBeInTheDocument();
    expect(screen.getByText('Benchmark impact')).toBeInTheDocument();
    expect(screen.getByText('Transition evidence')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /revision compare/i }));
    expect(onSelectCompareMode).toHaveBeenCalledWith('revision');
  });
});

function createArtifact(): SelfEvolvingArtifactCatalogEntry {
  return {
    golemId: 'golem-1',
    artifactStreamId: 'stream-1',
    originArtifactStreamId: 'stream-1',
    artifactKey: 'skill:planner',
    artifactAliases: ['skill:planner', 'planner'],
    artifactType: 'skill',
    artifactSubtype: 'skill',
    displayName: 'Planner skill',
    latestRevisionId: 'rev-2',
    activeRevisionId: 'rev-2',
    latestCandidateRevisionId: 'rev-2',
    currentLifecycleState: 'active',
    currentRolloutStage: 'active',
    hasRegression: false,
    hasPendingApproval: false,
    campaignCount: 1,
    projectionSchemaVersion: 1,
    sourceBotVersion: 'bot-1',
    projectedAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:00:00Z',
    stale: false,
    staleReason: null,
  };
}

function createLineage(): SelfEvolvingArtifactLineage {
  return {
    artifactStreamId: 'stream-1',
    originArtifactStreamId: 'stream-1',
    artifactKey: 'skill:planner',
    nodes: [
      {
        nodeId: 'candidate-1:approved',
        contentRevisionId: 'rev-2',
        lifecycleState: 'approved',
        rolloutStage: 'approved',
        promotionDecisionId: 'promo-1',
        originBundleId: 'bundle-1',
        sourceRunIds: ['run-1'],
        campaignIds: ['campaign-1'],
        attributionMode: 'judge',
        createdAt: '2026-03-30T20:01:00Z',
      },
      {
        nodeId: 'candidate-1:active',
        contentRevisionId: 'rev-2',
        lifecycleState: 'active',
        rolloutStage: 'active',
        promotionDecisionId: 'promo-2',
        originBundleId: 'bundle-1',
        sourceRunIds: ['run-1'],
        campaignIds: ['campaign-1'],
        attributionMode: 'judge',
        createdAt: '2026-03-30T20:02:00Z',
      },
    ],
    edges: [
      {
        edgeId: 'edge-1',
        fromNodeId: 'candidate-1:approved',
        toNodeId: 'candidate-1:active',
        edgeType: 'rollout',
        createdAt: '2026-03-30T20:02:00Z',
      },
    ],
    railOrder: ['candidate-1:approved', 'candidate-1:active'],
    branches: [],
    defaultSelectedNodeId: 'candidate-1:active',
    defaultSelectedRevisionId: 'rev-2',
    projectionSchemaVersion: 1,
    projectedAt: '2026-03-30T20:02:00Z',
  };
}

function createRevisionDiff(): SelfEvolvingArtifactRevisionDiff {
  return {
    artifactStreamId: 'stream-1',
    artifactKey: 'skill:planner',
    fromRevisionId: 'rev-1',
    toRevisionId: 'rev-2',
    summary: 'Planner routing improved',
    semanticSections: ['routing'],
    rawPatch: '@@ -1 +1 @@',
    changedFields: ['body'],
    riskSignals: [],
    impactSummary: {
      attributionMode: 'judge',
      campaignDelta: 1,
      regressionIntroduced: false,
      verdictDelta: 0.2,
      latencyDeltaMs: -50,
      costDeltaMicros: -100,
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:03:00Z',
    },
    projectionSchemaVersion: 1,
    projectedAt: '2026-03-30T20:03:00Z',
  };
}

function createTransitionDiff(): SelfEvolvingArtifactTransitionDiff {
  return {
    artifactStreamId: 'stream-1',
    artifactKey: 'skill:planner',
    fromNodeId: 'candidate-1:approved',
    toNodeId: 'candidate-1:active',
    fromRevisionId: 'rev-2',
    toRevisionId: 'rev-2',
    fromRolloutStage: 'approved',
    toRolloutStage: 'active',
    contentChanged: false,
    summary: 'Approved revision promoted to active',
    impactSummary: {
      attributionMode: 'promotion',
      campaignDelta: 0,
      regressionIntroduced: false,
      verdictDelta: 0.0,
      latencyDeltaMs: 0,
      costDeltaMicros: 0,
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:04:00Z',
    },
    projectionSchemaVersion: 1,
    projectedAt: '2026-03-30T20:04:00Z',
  };
}

function createEvidence(payloadKind: 'revision' | 'compare' | 'transition'): SelfEvolvingArtifactEvidence {
  return {
    artifactStreamId: 'stream-1',
    artifactKey: 'skill:planner',
    payloadKind,
    revisionId: payloadKind === 'revision' ? 'rev-2' : null,
    fromRevisionId: 'rev-1',
    toRevisionId: 'rev-2',
    fromNodeId: payloadKind === 'transition' ? 'candidate-1:approved' : null,
    toNodeId: payloadKind === 'transition' ? 'candidate-1:active' : null,
    runIds: ['run-1'],
    traceIds: ['trace-1'],
    spanIds: [],
    campaignIds: ['campaign-1'],
    promotionDecisionIds: ['promo-1'],
    approvalRequestIds: ['apr-1'],
    findings: ['Anchored evidence'],
    projectionSchemaVersion: 1,
    projectedAt: '2026-03-30T20:05:00Z',
  };
}
