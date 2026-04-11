import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listApprovals } from '../../lib/api/approvalsApi';
import { getGolem } from '../../lib/api/golemsApi';
import {
  clearInspectionSession,
  compactInspectionSession,
  deleteInspectionSession,
  exportInspectionSessionTrace,
  exportInspectionSessionTraceSnapshotPayload,
  getInspectionSession,
  getInspectionSessionTrace,
  getInspectionSessionTraceSummary,
  listInspectionSessions,
} from '../../lib/api/inspectionApi';
import {
  getSelfEvolvingArtifactCompareEvidence,
  getSelfEvolvingArtifactLineage,
  getSelfEvolvingArtifactRevisionDiff,
  getSelfEvolvingArtifactRevisionEvidence,
  getSelfEvolvingArtifactTransitionDiff,
  getSelfEvolvingArtifactTransitionEvidence,
  getSelfEvolvingLineage,
  listSelfEvolvingArtifacts,
  listSelfEvolvingCampaigns,
  listSelfEvolvingCandidates,
  listSelfEvolvingRuns,
  searchSelfEvolvingTactics,
} from '../../lib/api/selfEvolvingApi';
import { InspectionPage } from './InspectionPage';

vi.mock('../../lib/api/golemsApi', () => ({
  getGolem: vi.fn(),
}));

vi.mock('../../lib/api/inspectionApi', () => ({
  listInspectionSessions: vi.fn(),
  getInspectionSession: vi.fn(),
  getInspectionSessionTraceSummary: vi.fn(),
  getInspectionSessionTrace: vi.fn(),
  exportInspectionSessionTrace: vi.fn(),
  exportInspectionSessionTraceSnapshotPayload: vi.fn(),
  compactInspectionSession: vi.fn(),
  clearInspectionSession: vi.fn(),
  deleteInspectionSession: vi.fn(),
}));

vi.mock('../../lib/api/selfEvolvingApi', () => ({
  listSelfEvolvingRuns: vi.fn(),
  listSelfEvolvingCandidates: vi.fn(),
  getSelfEvolvingLineage: vi.fn(),
  listSelfEvolvingCampaigns: vi.fn(),
  listSelfEvolvingArtifacts: vi.fn(),
  getSelfEvolvingArtifactLineage: vi.fn(),
  getSelfEvolvingArtifactRevisionDiff: vi.fn(),
  getSelfEvolvingArtifactTransitionDiff: vi.fn(),
  getSelfEvolvingArtifactRevisionEvidence: vi.fn(),
  getSelfEvolvingArtifactCompareEvidence: vi.fn(),
  getSelfEvolvingArtifactTransitionEvidence: vi.fn(),
  searchSelfEvolvingTactics: vi.fn(),
}));

vi.mock('../../lib/api/approvalsApi', () => ({
  listApprovals: vi.fn(),
}));

const getGolemMock = vi.mocked(getGolem);
const listInspectionSessionsMock = vi.mocked(listInspectionSessions);
const getInspectionSessionMock = vi.mocked(getInspectionSession);
const getInspectionSessionTraceSummaryMock = vi.mocked(getInspectionSessionTraceSummary);
const getInspectionSessionTraceMock = vi.mocked(getInspectionSessionTrace);
const listSelfEvolvingRunsMock = vi.mocked(listSelfEvolvingRuns);
const listSelfEvolvingCandidatesMock = vi.mocked(listSelfEvolvingCandidates);
const getSelfEvolvingLineageMock = vi.mocked(getSelfEvolvingLineage);
const listSelfEvolvingCampaignsMock = vi.mocked(listSelfEvolvingCampaigns);
const listSelfEvolvingArtifactsMock = vi.mocked(listSelfEvolvingArtifacts);
const getSelfEvolvingArtifactLineageMock = vi.mocked(getSelfEvolvingArtifactLineage);
const getSelfEvolvingArtifactRevisionDiffMock = vi.mocked(getSelfEvolvingArtifactRevisionDiff);
const getSelfEvolvingArtifactTransitionDiffMock = vi.mocked(getSelfEvolvingArtifactTransitionDiff);
const getSelfEvolvingArtifactRevisionEvidenceMock = vi.mocked(getSelfEvolvingArtifactRevisionEvidence);
const getSelfEvolvingArtifactCompareEvidenceMock = vi.mocked(getSelfEvolvingArtifactCompareEvidence);
const getSelfEvolvingArtifactTransitionEvidenceMock = vi.mocked(getSelfEvolvingArtifactTransitionEvidence);
const searchSelfEvolvingTacticsMock = vi.mocked(searchSelfEvolvingTactics);
const listApprovalsMock = vi.mocked(listApprovals);
const INSPECTION_PAGE_SCENARIO_TIMEOUT_MS = 10_000;

describe('InspectionPage', () => {
  beforeEach(() => {
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => 'blob:test'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => undefined),
    });
    vi.mocked(exportInspectionSessionTrace).mockResolvedValue({
      sessionId: 'web:conv-1',
      storageStats: {},
      traces: [],
    });
    vi.mocked(exportInspectionSessionTraceSnapshotPayload).mockResolvedValue({
      blob: new Blob(['{}'], { type: 'application/json' }),
      fileName: 'snapshot.json',
      contentType: 'application/json',
    });
    vi.mocked(compactInspectionSession).mockResolvedValue({ removed: 3 });
    vi.mocked(clearInspectionSession).mockResolvedValue(undefined);
    vi.mocked(deleteInspectionSession).mockResolvedValue(undefined);
    listSelfEvolvingRunsMock.mockResolvedValue([]);
    listSelfEvolvingCandidatesMock.mockResolvedValue([]);
    getSelfEvolvingLineageMock.mockResolvedValue({ golemId: 'golem_1', nodes: [] });
    listSelfEvolvingCampaignsMock.mockResolvedValue([]);
    listSelfEvolvingArtifactsMock.mockResolvedValue([]);
    getSelfEvolvingArtifactLineageMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      originArtifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      nodes: [],
      edges: [],
      railOrder: [],
      branches: [],
      defaultSelectedNodeId: null,
      defaultSelectedRevisionId: null,
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    getSelfEvolvingArtifactRevisionDiffMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      fromRevisionId: 'rev-1',
      toRevisionId: 'rev-2',
      summary: 'planner diff',
      semanticSections: ['routing'],
      rawPatch: '@@ -1 +1 @@',
      changedFields: ['body'],
      riskSignals: [],
      impactSummary: null,
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    getSelfEvolvingArtifactTransitionDiffMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      fromNodeId: 'candidate-1:approved',
      toNodeId: 'candidate-1:active',
      fromRevisionId: 'rev-2',
      toRevisionId: 'rev-2',
      fromRolloutStage: 'approved',
      toRolloutStage: 'active',
      contentChanged: false,
      summary: 'approved to active',
      impactSummary: null,
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    getSelfEvolvingArtifactRevisionEvidenceMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      payloadKind: 'revision',
      revisionId: 'rev-2',
      fromRevisionId: null,
      toRevisionId: null,
      fromNodeId: null,
      toNodeId: null,
      runIds: ['run-1'],
      traceIds: ['trace-1'],
      spanIds: [],
      campaignIds: ['campaign-1'],
      promotionDecisionIds: ['promo-1'],
      approvalRequestIds: ['apr-1'],
      findings: ['Anchored evidence'],
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    getSelfEvolvingArtifactCompareEvidenceMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      payloadKind: 'compare',
      revisionId: null,
      fromRevisionId: 'rev-1',
      toRevisionId: 'rev-2',
      fromNodeId: null,
      toNodeId: null,
      runIds: ['run-1'],
      traceIds: ['trace-1'],
      spanIds: [],
      campaignIds: ['campaign-1'],
      promotionDecisionIds: ['promo-1'],
      approvalRequestIds: ['apr-1'],
      findings: ['Compare evidence'],
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    getSelfEvolvingArtifactTransitionEvidenceMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      artifactKey: 'skill:planner',
      payloadKind: 'transition',
      revisionId: null,
      fromRevisionId: 'rev-2',
      toRevisionId: 'rev-2',
      fromNodeId: 'candidate-1:approved',
      toNodeId: 'candidate-1:active',
      runIds: ['run-1'],
      traceIds: ['trace-1'],
      spanIds: [],
      campaignIds: ['campaign-1'],
      promotionDecisionIds: ['promo-1'],
      approvalRequestIds: ['apr-1'],
      findings: ['Transition evidence'],
      projectionSchemaVersion: 1,
      projectedAt: '2026-03-30T20:00:00Z',
    });
    searchSelfEvolvingTacticsMock.mockResolvedValue({
      query: '',
      status: {
        mode: 'hybrid',
        reason: null,
        degraded: false,
        updatedAt: '2026-03-30T20:00:00Z',
      },
      results: [],
    });
    listApprovalsMock.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('renders session directory, messages, and trace explorer for an online golem', async () => {
    getGolemMock.mockResolvedValue(createGolem('ONLINE'));
    listInspectionSessionsMock.mockResolvedValue([
      createSessionSummary('web:conv-1', 'Conv 1'),
      createSessionSummary('hive:thread-2', 'Thread 2'),
    ]);
    getInspectionSessionMock.mockResolvedValue(createSessionDetail('web:conv-1'));
    getInspectionSessionTraceSummaryMock.mockResolvedValue(createTraceSummary('web:conv-1'));
    getInspectionSessionTraceMock.mockResolvedValue(createTrace('web:conv-1'));
    listSelfEvolvingRunsMock.mockResolvedValue([
      {
        id: 'run-1',
        golemId: 'golem_1',
        sessionId: 'web:conv-1',
        traceId: 'trace-1',
        artifactBundleId: 'bundle-1',
        artifactBundleStatus: 'active',
        status: 'COMPLETED',
        outcomeStatus: 'COMPLETED',
        processStatus: 'HEALTHY',
        promotionRecommendation: 'approve_gated',
        outcomeSummary: 'done',
        processSummary: 'good',
        confidence: 0.91,
        processFindings: ['No tier escalation required'],
        startedAt: '2026-03-30T20:00:00Z',
        completedAt: '2026-03-30T20:00:30Z',
        updatedAt: '2026-03-30T20:00:31Z',
      },
    ]);
    listSelfEvolvingCandidatesMock.mockResolvedValue([
      {
        id: 'candidate-1',
        golemId: 'golem_1',
        goal: 'fix',
        artifactType: 'skill',
        status: 'approved_pending',
        riskLevel: 'medium',
        expectedImpact: 'Reduce routing failures',
        sourceRunIds: ['run-1'],
        updatedAt: '2026-03-30T20:01:00Z',
      },
    ]);
    getSelfEvolvingLineageMock.mockResolvedValue({
      golemId: 'golem_1',
      nodes: [
        {
          id: 'lineage-1',
          golemId: 'golem_1',
          parentId: null,
          artifactType: 'skill',
          status: 'approved',
          updatedAt: '2026-03-30T20:02:00Z',
        },
      ],
    });
    listSelfEvolvingCampaignsMock.mockResolvedValue([
      {
        id: 'campaign-1',
        golemId: 'golem_1',
        suiteId: 'suite-1',
        baselineBundleId: 'bundle-1',
        candidateBundleId: 'bundle-1',
        status: 'created',
        runIds: ['run-1'],
        startedAt: '2026-03-30T20:04:00Z',
        completedAt: null,
        updatedAt: '2026-03-30T20:04:00Z',
      },
    ]);
    listApprovalsMock.mockResolvedValue([
      {
        id: 'apr-1',
        subjectType: 'SELF_EVOLVING_PROMOTION',
        commandId: null,
        runId: 'run-1',
        threadId: null,
        boardId: null,
        cardId: null,
        golemId: 'golem_1',
        requestedByActorId: 'operator-1',
        requestedByActorName: 'Hive Admin',
        riskLevel: null,
        reason: 'Awaiting approval before rollout',
        estimatedCostMicros: 0,
        commandBody: null,
        status: 'PENDING',
        requestedAt: '2026-03-30T20:03:00Z',
        updatedAt: '2026-03-30T20:03:00Z',
        decidedAt: null,
        decidedByActorId: null,
        decidedByActorName: null,
        decisionComment: null,
        promotionContext: {
          candidateId: 'candidate-1',
          goal: 'fix',
          artifactType: 'skill',
          riskLevel: 'medium',
          expectedImpact: 'Reduce routing failures',
          sourceRunIds: ['run-1'],
        },
      },
    ]);
    listSelfEvolvingArtifactsMock.mockResolvedValue([
      {
        golemId: 'golem_1',
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
      },
    ]);
    getSelfEvolvingArtifactLineageMock.mockResolvedValue({
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
    });

    renderPage();

    expect(
      await screen.findByRole(
        'heading',
        { name: /Atlas Inspect/i },
        { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS },
      ),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole(
        'button',
        { name: /Conv 1/i },
        { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS },
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Thread 2/i })).toBeInTheDocument();

    expect(
      await screen.findByText('hello from the operator', {}, { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Compact' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Clear' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export trace' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(screen.getByText('Trace summary')).toBeInTheDocument();
    expect(await screen.findByText('Judging')).toBeInTheDocument();
    expect(screen.getByText('Lineage')).toBeInTheDocument();
    expect(await screen.findByText('Artifacts')).toBeInTheDocument();
    expect(screen.getByText('Planner skill')).toBeInTheDocument();
    expect(screen.getByText('Semantic diff')).toBeInTheDocument();
    expect(screen.getByText('Candidate queue')).toBeInTheDocument();
    expect(screen.getByText('Benchmark lab')).toBeInTheDocument();
    expect(screen.getAllByText('campaign-1').length).toBeGreaterThan(0);
    expect(screen.getByText('Promotion approvals')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Load details' }));

    await waitFor(() => {
      expect(getInspectionSessionTraceMock).toHaveBeenCalledWith('golem_1', 'web:conv-1');
    });
    expect(
      await screen.findByText('Conversation + trace', {}, { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS }),
    ).toBeInTheDocument();
    expect(screen.getAllByText('trace inbound message').length).toBeGreaterThan(0);
  }, INSPECTION_PAGE_SCENARIO_TIMEOUT_MS);

  it('shows the online-only gate when the golem is offline', async () => {
    getGolemMock.mockResolvedValue(createGolem('OFFLINE'));

    renderPage();

    expect(
      await screen.findByText('Inspection unavailable', {}, { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS }),
    ).toBeInTheDocument();
    expect(screen.getByText(/only available while the golem is online/i)).toBeInTheDocument();
    expect(listInspectionSessionsMock).not.toHaveBeenCalled();
    expect(listSelfEvolvingRunsMock).not.toHaveBeenCalled();
  });

  it('shows an empty trace state when the selected session has no traces', async () => {
    getGolemMock.mockResolvedValue(createGolem('ONLINE'));
    listInspectionSessionsMock.mockResolvedValue([createSessionSummary('web:conv-1', 'Conv 1')]);
    getInspectionSessionMock.mockResolvedValue(createSessionDetail('web:conv-1'));
    getInspectionSessionTraceSummaryMock.mockResolvedValue({
      sessionId: 'web:conv-1',
      traceCount: 0,
      spanCount: 0,
      snapshotCount: 0,
      storageStats: {
        compressedSnapshotBytes: 0,
        uncompressedSnapshotBytes: 0,
        evictedSnapshots: 0,
        evictedTraces: 0,
        truncatedTraces: 0,
      },
      traces: [],
    });
    getInspectionSessionTraceMock.mockResolvedValue({
      sessionId: 'web:conv-1',
      storageStats: {
        compressedSnapshotBytes: 0,
        uncompressedSnapshotBytes: 0,
        evictedSnapshots: 0,
        evictedTraces: 0,
        truncatedTraces: 0,
      },
      traces: [],
    });

    renderPage();

    expect(
      await screen.findByText('No traces captured for this session.', {}, { timeout: INSPECTION_PAGE_SCENARIO_TIMEOUT_MS }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Export trace' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Load details' })).not.toBeInTheDocument();
  });
});

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/fleet/inspection/golem_1']}>
        <Routes>
          <Route path="/fleet/inspection/:golemId" element={<InspectionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function createGolem(state: string) {
  return {
    id: 'golem_1',
    displayName: 'Atlas Inspect',
    hostLabel: 'atlas-host',
    runtimeVersion: 'bot-1.2.3',
    buildVersion: 'build-42',
    state,
    registeredAt: '2026-03-30T20:00:00Z',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    lastHeartbeatAt: '2026-03-30T20:05:00Z',
    lastSeenAt: '2026-03-30T20:05:00Z',
    lastStateChangeAt: '2026-03-30T20:05:00Z',
    heartbeatIntervalSeconds: 30,
    missedHeartbeatCount: 0,
    pauseReason: null,
    revokeReason: null,
    controlChannelUrl: null,
    supportedChannels: ['control', 'events'],
    capabilities: {
      providers: ['openai'],
      modelFamilies: ['gpt'],
      enabledTools: ['shell'],
      enabledAutonomyFeatures: ['planning'],
      capabilityTags: ['inspection'],
      supportedChannels: ['control', 'events'],
      snapshotHash: 'abc123',
      defaultModel: 'gpt-5',
    },
    lastHeartbeat: {
      status: 'ok',
      currentRunState: null,
      currentCardId: null,
      currentThreadId: null,
      modelTier: 'high',
      inputTokens: 0,
      outputTokens: 0,
      accumulatedCostMicros: 0,
      queueDepth: 0,
      healthSummary: null,
      lastErrorSummary: null,
      uptimeSeconds: 123,
      capabilitySnapshotHash: 'abc123',
    },
    roleSlugs: ['ops'],
  };
}

function createSessionSummary(id: string, title: string) {
  return {
    id,
    channelType: id.split(':')[0],
    chatId: title.toLowerCase(),
    conversationKey: title.toLowerCase(),
    transportChatId: `${title.toLowerCase()}-transport`,
    messageCount: 3,
    state: 'ACTIVE',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    title,
    preview: `Preview for ${title}`,
  };
}

function createSessionDetail(id: string) {
  return {
    id,
    channelType: 'web',
    chatId: 'chat-1',
    conversationKey: 'conv-1',
    transportChatId: 'transport-1',
    state: 'ACTIVE',
    createdAt: '2026-03-30T20:00:00Z',
    updatedAt: '2026-03-30T20:05:00Z',
    messages: [
      {
        id: 'msg-user-1',
        role: 'user',
        content: 'hello from the operator',
        timestamp: '2026-03-30T20:00:01Z',
        hasToolCalls: false,
        hasVoice: false,
        model: null,
        modelTier: null,
        skill: null,
        reasoning: null,
        clientMessageId: 'client-1',
        attachments: [],
      },
      {
        id: 'msg-assistant-1',
        role: 'assistant',
        content: 'trace inbound message',
        timestamp: '2026-03-30T20:00:03Z',
        hasToolCalls: true,
        hasVoice: false,
        model: 'gpt-5',
        modelTier: 'high',
        skill: 'inspection',
        reasoning: 'medium',
        clientMessageId: null,
        attachments: [],
      },
    ],
  };
}

function createTraceSummary(sessionId: string) {
  return {
    sessionId,
    traceCount: 1,
    spanCount: 2,
    snapshotCount: 1,
    storageStats: {
      compressedSnapshotBytes: 256,
      uncompressedSnapshotBytes: 1024,
      evictedSnapshots: 0,
      evictedTraces: 0,
      truncatedTraces: 0,
    },
    traces: [
      {
        traceId: 'trace_1',
        rootSpanId: 'span_root',
        traceName: 'trace inbound message',
        rootKind: 'SYSTEM',
        rootStatusCode: 'OK',
        startedAt: '2026-03-30T20:00:01Z',
        endedAt: '2026-03-30T20:00:04Z',
        durationMs: 3000,
        spanCount: 2,
        snapshotCount: 1,
        truncated: false,
      },
    ],
  };
}

function createTrace(sessionId: string) {
  return {
    sessionId,
    storageStats: {
      compressedSnapshotBytes: 256,
      uncompressedSnapshotBytes: 1024,
      evictedSnapshots: 0,
      evictedTraces: 0,
      truncatedTraces: 0,
    },
    traces: [
      {
        traceId: 'trace_1',
        rootSpanId: 'span_root',
        traceName: 'trace inbound message',
        startedAt: '2026-03-30T20:00:01Z',
        endedAt: '2026-03-30T20:00:04Z',
        truncated: false,
        compressedSnapshotBytes: 256,
        uncompressedSnapshotBytes: 1024,
        spans: [
          {
            spanId: 'span_root',
            parentSpanId: null,
            name: 'root',
            kind: 'SYSTEM',
            statusCode: 'OK',
            statusMessage: null,
            startedAt: '2026-03-30T20:00:01Z',
            endedAt: '2026-03-30T20:00:04Z',
            durationMs: 3000,
            attributes: {},
            events: [],
            snapshots: [],
          },
          {
            spanId: 'span_llm',
            parentSpanId: 'span_root',
            name: 'llm.call',
            kind: 'LLM',
            statusCode: 'OK',
            statusMessage: 'resolved prompt',
            startedAt: '2026-03-30T20:00:02Z',
            endedAt: '2026-03-30T20:00:03Z',
            durationMs: 1000,
            attributes: {
              'context.skill.name': 'inspection',
            },
            events: [],
            snapshots: [
              {
                snapshotId: 'snapshot_1',
                role: 'assistant',
                contentType: 'application/json',
                encoding: 'utf-8',
                originalSize: 1024,
                compressedSize: 256,
                truncated: false,
                payloadAvailable: true,
                payloadPreview: '{"ok":true}',
                payloadPreviewTruncated: false,
              },
            ],
          },
        ],
      },
    ],
  };
}
