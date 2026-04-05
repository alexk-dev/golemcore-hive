import { apiRequest } from './httpClient';
import type {
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactEvidence,
  SelfEvolvingArtifactFleetCompare,
  SelfEvolvingArtifactLineage,
  SelfEvolvingArtifactRevisionDiff,
  SelfEvolvingArtifactTransitionDiff,
} from './selfEvolvingApi';

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

export function getSelfEvolvingArtifactRevisionEvidence(
  golemId: string,
  artifactStreamId: string,
  revisionId: string,
) {
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
  return apiRequest<SelfEvolvingArtifactFleetCompare>(
    `/api/v1/self-evolving/artifacts/compare?${params.toString()}`,
  );
}
