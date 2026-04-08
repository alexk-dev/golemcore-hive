import { apiRequest } from './httpClient';

export interface PolicyProviderConfigResponse {
  apiKeyPresent: boolean;
  baseUrl: string | null;
  requestTimeoutSeconds: number | null;
  apiType: string | null;
  legacyApi: boolean | null;
}

export interface PolicyTierBindingResponse {
  model: string | null;
  reasoning: string | null;
}

export interface PolicyModelConfigResponse {
  provider: string | null;
  displayName: string | null;
  supportsVision: boolean | null;
  supportsTemperature: boolean | null;
  maxInputTokens: number | null;
}

export interface PolicyGroupSpecResponse {
  schemaVersion: number;
  llmProviders: Record<string, PolicyProviderConfigResponse>;
  modelRouter: {
    temperature: number | null;
    routing: PolicyTierBindingResponse | null;
    tiers: Record<string, PolicyTierBindingResponse>;
    dynamicTierEnabled: boolean | null;
  } | null;
  modelCatalog: {
    defaultModel: string | null;
    models: Record<string, PolicyModelConfigResponse>;
  } | null;
  checksum: string | null;
}

export interface PolicyGroup {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  status: string;
  currentVersion: number;
  draftSpec: PolicyGroupSpecResponse | null;
  createdAt: string | null;
  updatedAt: string | null;
  lastPublishedAt: string | null;
  lastPublishedBy: string | null;
  lastPublishedByName: string | null;
  boundGolemCount: number;
}

export interface PolicyGroupVersion {
  version: number;
  specSnapshot: PolicyGroupSpecResponse | null;
  checksum: string | null;
  changeSummary: string | null;
  publishedAt: string | null;
  publishedBy: string | null;
  publishedByName: string | null;
}

export interface PolicyDraftProviderConfig {
  apiKey?: string;
  baseUrl?: string | null;
  requestTimeoutSeconds?: number | null;
  apiType?: string | null;
  legacyApi?: boolean | null;
}

export interface PolicyDraftTierBinding {
  model?: string | null;
  reasoning?: string | null;
}

export interface PolicyDraftModelConfig {
  provider?: string | null;
  displayName?: string | null;
  supportsVision?: boolean | null;
  supportsTemperature?: boolean | null;
  maxInputTokens?: number | null;
}

export interface PolicyDraftSpec {
  schemaVersion: number;
  llmProviders: Record<string, PolicyDraftProviderConfig>;
  modelRouter: {
    temperature?: number | null;
    routing?: PolicyDraftTierBinding | null;
    tiers?: Record<string, PolicyDraftTierBinding>;
    dynamicTierEnabled?: boolean | null;
  } | null;
  modelCatalog: {
    defaultModel?: string | null;
    models?: Record<string, PolicyDraftModelConfig>;
  } | null;
}

export interface GolemPolicyBinding {
  policyGroupId: string;
  targetVersion: number;
  appliedVersion: number | null;
  syncStatus: string | null;
  lastSyncRequestedAt: string | null;
  lastAppliedAt: string | null;
  lastErrorDigest: string | null;
  lastErrorAt: string | null;
  driftSince: string | null;
}

export function listPolicyGroups() {
  return apiRequest<PolicyGroup[]>('/api/v1/policy-groups');
}

export function getPolicyGroup(groupId: string) {
  return apiRequest<PolicyGroup>(`/api/v1/policy-groups/${groupId}`);
}

export function createPolicyGroup(payload: { slug: string; name: string; description?: string }) {
  return apiRequest<PolicyGroup>('/api/v1/policy-groups', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updatePolicyGroupDraft(groupId: string, draftSpec: PolicyDraftSpec) {
  return apiRequest<PolicyGroup>(`/api/v1/policy-groups/${groupId}/draft`, {
    method: 'PUT',
    body: JSON.stringify(draftSpec),
  });
}

export function publishPolicyGroup(groupId: string, changeSummary: string) {
  return apiRequest<PolicyGroupVersion>(`/api/v1/policy-groups/${groupId}/publish`, {
    method: 'POST',
    body: JSON.stringify({ changeSummary }),
  });
}

export function listPolicyGroupVersions(groupId: string) {
  return apiRequest<PolicyGroupVersion[]>(`/api/v1/policy-groups/${groupId}/versions`);
}

export function rollbackPolicyGroup(groupId: string, version: number, changeSummary: string) {
  return apiRequest<PolicyGroup>(`/api/v1/policy-groups/${groupId}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ version, changeSummary }),
  });
}

export function bindGolemPolicyGroup(golemId: string, policyGroupId: string) {
  return apiRequest<GolemPolicyBinding>(`/api/v1/golems/${golemId}/policy-binding`, {
    method: 'PUT',
    body: JSON.stringify({ policyGroupId }),
  });
}

export function unbindGolemPolicyGroup(golemId: string) {
  return apiRequest<undefined>(`/api/v1/golems/${golemId}/policy-binding`, {
    method: 'DELETE',
  });
}
