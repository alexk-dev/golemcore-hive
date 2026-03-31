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

export interface SelfEvolvingArtifactSearchFilters {
  golemId?: string;
  artifactType?: string;
  artifactSubtype?: string;
  hasRegression?: boolean;
  hasPendingApproval?: boolean;
  rolloutStage?: string;
  query?: string;
}

function buildArtifactSearchQuery(filters: SelfEvolvingArtifactSearchFilters) {
  const params = new URLSearchParams();
  if (filters.golemId) {
    params.set('golemId', filters.golemId);
  }
  if (filters.artifactType) {
    params.set('artifactType', filters.artifactType);
  }
  if (filters.artifactSubtype) {
    params.set('artifactSubtype', filters.artifactSubtype);
  }
  if (filters.hasRegression != null) {
    params.set('hasRegression', String(filters.hasRegression));
  }
  if (filters.hasPendingApproval != null) {
    params.set('hasPendingApproval', String(filters.hasPendingApproval));
  }
  if (filters.rolloutStage) {
    params.set('rolloutStage', filters.rolloutStage);
  }
  if (filters.query) {
    params.set('q', filters.query);
  }
  const queryString = params.toString();
  return queryString ? `?${queryString}` : '';
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

export function listSelfEvolvingArtifacts(golemId: string) {
  return apiRequest<SelfEvolvingArtifactCatalogEntry[]>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts`,
  );
}

export function getSelfEvolvingArtifactLineage(golemId: string, artifactStreamId: string) {
  return apiRequest<SelfEvolvingArtifactLineage>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/lineage`,
  );
}

export function getSelfEvolvingArtifactRevisionDiff(
  golemId: string,
  artifactStreamId: string,
  fromRevisionId: string,
  toRevisionId: string,
) {
  const params = new URLSearchParams({ fromRevisionId, toRevisionId });
  return apiRequest<SelfEvolvingArtifactRevisionDiff>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/diff?${params.toString()}`,
  );
}

export function getSelfEvolvingArtifactTransitionDiff(
  golemId: string,
  artifactStreamId: string,
  fromNodeId: string,
  toNodeId: string,
) {
  const params = new URLSearchParams({ fromNodeId, toNodeId });
  return apiRequest<SelfEvolvingArtifactTransitionDiff>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/transition-diff?${params.toString()}`,
  );
}

export function getSelfEvolvingArtifactRevisionEvidence(golemId: string, artifactStreamId: string, revisionId: string) {
  const params = new URLSearchParams({ revisionId });
  return apiRequest<SelfEvolvingArtifactEvidence>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/evidence?${params.toString()}`,
  );
}

export function getSelfEvolvingArtifactCompareEvidence(
  golemId: string,
  artifactStreamId: string,
  fromRevisionId: string,
  toRevisionId: string,
) {
  const params = new URLSearchParams({ fromRevisionId, toRevisionId });
  return apiRequest<SelfEvolvingArtifactEvidence>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/compare-evidence?${params.toString()}`,
  );
}

export function getSelfEvolvingArtifactTransitionEvidence(
  golemId: string,
  artifactStreamId: string,
  fromNodeId: string,
  toNodeId: string,
) {
  const params = new URLSearchParams({ fromNodeId, toNodeId });
  return apiRequest<SelfEvolvingArtifactEvidence>(
    `/api/v1/self-evolving/golems/${encodeURIComponent(golemId)}/artifacts/${encodeURIComponent(artifactStreamId)}/transition-evidence?${params.toString()}`,
  );
}

export function searchSelfEvolvingArtifacts(filters: SelfEvolvingArtifactSearchFilters = {}) {
  return apiRequest<SelfEvolvingArtifactCatalogEntry[]>(
    `/api/v1/self-evolving/artifacts/search${buildArtifactSearchQuery(filters)}`,
  );
}

export function compareSelfEvolvingArtifacts(
  artifactStreamId: string,
  leftGolemId: string,
  rightGolemId: string,
  leftRevisionId: string,
  rightRevisionId: string,
) {
  const params = new URLSearchParams({
    artifactStreamId,
    leftGolemId,
    rightGolemId,
    leftRevisionId,
    rightRevisionId,
  });
  return apiRequest<SelfEvolvingArtifactFleetCompare>(`/api/v1/self-evolving/artifacts/compare?${params.toString()}`);
}
