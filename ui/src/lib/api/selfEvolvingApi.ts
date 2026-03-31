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
