import { apiRequest } from './httpClient';

export interface SelfEvolvingRun {
  id: string;
  golemId: string;
  sessionId: string | null;
  traceId: string | null;
  artifactBundleId: string | null;
  artifactBundleStatus: string | null;
  status: string;
  outcomeStatus: string | null;
  processStatus: string | null;
  promotionRecommendation: string | null;
  outcomeSummary: string | null;
  processSummary: string | null;
  confidence: number | null;
  processFindings: string[];
  startedAt: string | null;
  completedAt: string | null;
  updatedAt: string | null;
}

export interface SelfEvolvingCandidate {
  id: string;
  golemId: string;
  goal: string | null;
  artifactType: string | null;
  status: string;
  riskLevel: string | null;
  expectedImpact: string | null;
  sourceRunIds: string[];
  updatedAt: string | null;
}

export interface SelfEvolvingLineageNode {
  id: string;
  golemId: string;
  parentId: string | null;
  artifactType: string | null;
  status: string | null;
  updatedAt: string | null;
}

export interface SelfEvolvingLineageResponse {
  golemId: string;
  nodes: SelfEvolvingLineageNode[];
}

export interface SelfEvolvingCampaign {
  id: string;
  golemId: string;
  suiteId: string | null;
  baselineBundleId: string | null;
  candidateBundleId: string | null;
  status: string | null;
  runIds: string[];
  startedAt: string | null;
  completedAt: string | null;
  updatedAt: string | null;
}

export interface SelfEvolvingArtifactCatalogEntry {
  golemId: string | null;
  artifactStreamId: string;
  originArtifactStreamId: string | null;
  artifactKey: string | null;
  artifactAliases: string[];
  artifactType: string | null;
  artifactSubtype: string | null;
  displayName: string | null;
  latestRevisionId: string | null;
  activeRevisionId: string | null;
  latestCandidateRevisionId: string | null;
  currentLifecycleState: string | null;
  currentRolloutStage: string | null;
  hasRegression: boolean | null;
  hasPendingApproval: boolean | null;
  campaignCount: number | null;
  projectionSchemaVersion: number | null;
  sourceBotVersion: string | null;
  projectedAt: string | null;
  updatedAt: string | null;
  stale: boolean | null;
  staleReason: string | null;
}

export interface SelfEvolvingArtifactLineageNode {
  nodeId: string;
  contentRevisionId: string | null;
  lifecycleState: string | null;
  rolloutStage: string | null;
  promotionDecisionId: string | null;
  originBundleId: string | null;
  sourceRunIds: string[];
  campaignIds: string[];
  attributionMode: string | null;
  createdAt: string | null;
}

export interface SelfEvolvingArtifactLineageEdge {
  edgeId: string;
  fromNodeId: string;
  toNodeId: string;
  edgeType: string | null;
  createdAt: string | null;
}

export interface SelfEvolvingArtifactLineage {
  artifactStreamId: string;
  originArtifactStreamId: string | null;
  artifactKey: string | null;
  nodes: SelfEvolvingArtifactLineageNode[];
  edges: SelfEvolvingArtifactLineageEdge[];
  railOrder: string[];
  branches: string[];
  defaultSelectedNodeId: string | null;
  defaultSelectedRevisionId: string | null;
  projectionSchemaVersion: number | null;
  sourceBotVersion?: string | null;
  projectedAt: string | null;
}

export interface SelfEvolvingArtifactImpactSummary {
  artifactStreamId?: string | null;
  fromRevisionId?: string | null;
  toRevisionId?: string | null;
  attributionMode: string | null;
  qualityDelta?: number | null;
  verdictDelta?: number | null;
  costDelta?: number | null;
  costDeltaMicros?: number | null;
  latencyDelta?: number | null;
  latencyDeltaMs?: number | null;
  campaignDelta: number | null;
  regressionIntroduced: boolean | null;
  regressionResolved?: boolean | null;
  campaignIds?: string[];
  summary?: string | null;
  projectionSchemaVersion: number | null;
  projectedAt: string | null;
}

export interface SelfEvolvingArtifactRevisionDiff {
  artifactStreamId: string;
  artifactKey: string | null;
  fromRevisionId: string | null;
  toRevisionId: string | null;
  summary: string | null;
  semanticSections: string[];
  rawPatch: string | null;
  changedFields: string[];
  riskSignals: string[];
  impactSummary: SelfEvolvingArtifactImpactSummary | null;
  attributionMode?: string | null;
  projectionSchemaVersion: number | null;
  projectedAt: string | null;
}

export interface SelfEvolvingArtifactTransitionDiff {
  artifactStreamId: string;
  artifactKey: string | null;
  fromNodeId: string | null;
  toNodeId: string | null;
  fromRevisionId: string | null;
  toRevisionId: string | null;
  fromRolloutStage: string | null;
  toRolloutStage: string | null;
  contentChanged: boolean;
  summary: string | null;
  impactSummary: SelfEvolvingArtifactImpactSummary | null;
  attributionMode?: string | null;
  projectionSchemaVersion: number | null;
  projectedAt: string | null;
}

export interface SelfEvolvingArtifactEvidence {
  artifactStreamId: string;
  artifactKey: string | null;
  payloadKind: 'revision' | 'compare' | 'transition';
  revisionId: string | null;
  fromRevisionId: string | null;
  toRevisionId: string | null;
  fromNodeId: string | null;
  toNodeId: string | null;
  runIds: string[];
  traceIds: string[];
  spanIds: string[];
  campaignIds: string[];
  promotionDecisionIds: string[];
  approvalRequestIds: string[];
  findings: string[];
  projectionSchemaVersion: number | null;
  projectedAt: string | null;
}

export interface SelfEvolvingArtifactFleetCompare {
  artifactStreamId: string;
  leftGolemId: string;
  rightGolemId: string;
  leftRevisionId: string;
  rightRevisionId: string;
  leftNormalizedHash: string | null;
  rightNormalizedHash: string | null;
  sameContent: boolean | null;
  leftStale: boolean | null;
  rightStale: boolean | null;
  summary: string | null;
  normalizationSchemaVersion: number | null;
  projectedAt: string | null;
  warnings: string[];
}

export interface SelfEvolvingTacticSearchStatus {
  mode: string | null;
  reason: string | null;
  degraded: boolean | null;
  updatedAt: string | null;
}

export interface SelfEvolvingTacticSearchExplanation {
  searchMode: string | null;
  degradedReason: string | null;
  bm25Score: number | null;
  vectorScore: number | null;
  rrfScore: number | null;
  qualityPrior: number | null;
  mmrDiversityAdjustment: number | null;
  negativeMemoryPenalty: number | null;
  personalizationBoost: number | null;
  rerankerVerdict: string | null;
  matchedQueryViews: string[];
  matchedTerms: string[];
  eligible: boolean | null;
  gatingReason: string | null;
  finalScore: number | null;
}

export interface SelfEvolvingTactic {
  tacticId: string;
  artifactStreamId: string | null;
  originArtifactStreamId: string | null;
  artifactKey: string | null;
  artifactType: string | null;
  title: string | null;
  aliases: string[];
  contentRevisionId: string | null;
  intentSummary: string | null;
  behaviorSummary: string | null;
  toolSummary: string | null;
  outcomeSummary: string | null;
  benchmarkSummary: string | null;
  approvalNotes: string | null;
  evidenceSnippets: string[];
  taskFamilies: string[];
  tags: string[];
  promotionState: string | null;
  rolloutStage: string | null;
  successRate: number | null;
  benchmarkWinRate: number | null;
  regressionFlags: string[];
  recencyScore: number | null;
  golemLocalUsageSuccess: number | null;
  embeddingStatus: string | null;
  updatedAt: string | null;
}

export interface SelfEvolvingTacticSearchResult extends SelfEvolvingTactic {
  score: number | null;
  explanation: SelfEvolvingTacticSearchExplanation | null;
}

export interface SelfEvolvingTacticSearchResponse {
  query: string | null;
  status: SelfEvolvingTacticSearchStatus | null;
  results: SelfEvolvingTacticSearchResult[];
}

export function listSelfEvolvingRuns(golemId: string) {
  return apiRequest<SelfEvolvingRun[]>(`/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/runs`);
}

export function listSelfEvolvingCandidates(golemId: string) {
  return apiRequest<SelfEvolvingCandidate[]>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/candidates`,
  );
}

export function getSelfEvolvingLineage(golemId: string) {
  return apiRequest<SelfEvolvingLineageResponse>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/lineage`,
  );
}

export function listSelfEvolvingCampaigns(golemId: string) {
  return apiRequest<SelfEvolvingCampaign[]>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/benchmarks/campaigns`,
  );
}

export function searchSelfEvolvingTactics(golemId: string, query: string) {
  const params = new URLSearchParams();
  if (query.length > 0) {
    params.set('q', query);
  }
  const queryString = params.toString();
  return apiRequest<SelfEvolvingTacticSearchResponse>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/tactics/search${queryString ? `?${queryString}` : ''}`,
  );
}

export type { SelfEvolvingArtifactSearchFilters } from './selfEvolvingArtifactApi';
export {
  compareSelfEvolvingArtifacts,
  getSelfEvolvingArtifactCompareEvidence,
  getSelfEvolvingArtifactLineage,
  getSelfEvolvingArtifactRevisionDiff,
  getSelfEvolvingArtifactRevisionEvidence,
  getSelfEvolvingArtifactTransitionDiff,
  getSelfEvolvingArtifactTransitionEvidence,
  listSelfEvolvingArtifacts,
  searchSelfEvolvingArtifacts,
} from './selfEvolvingArtifactApi';
