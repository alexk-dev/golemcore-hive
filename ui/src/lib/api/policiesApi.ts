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

export interface PolicyEnvironmentVariableResponse {
  name: string | null;
  valuePresent: boolean;
}

export interface PolicyToolsConfigResponse {
  filesystemEnabled: boolean | null;
  shellEnabled: boolean | null;
  skillManagementEnabled: boolean | null;
  skillTransitionEnabled: boolean | null;
  tierEnabled: boolean | null;
  goalManagementEnabled: boolean | null;
  shellEnvironmentVariables: PolicyEnvironmentVariableResponse[];
}

export interface PolicyMemoryConfigResponse {
  version: number | null;
  enabled: boolean | null;
  softPromptBudgetTokens: number | null;
  maxPromptBudgetTokens: number | null;
  workingTopK: number | null;
  episodicTopK: number | null;
  semanticTopK: number | null;
  proceduralTopK: number | null;
  promotionEnabled: boolean | null;
  promotionMinConfidence: number | null;
  decayEnabled: boolean | null;
  decayDays: number | null;
  retrievalLookbackDays: number | null;
  codeAwareExtractionEnabled: boolean | null;
  disclosure: PolicyMemoryDisclosureResponse | null;
  reranking: PolicyMemoryRerankingResponse | null;
  diagnostics: PolicyMemoryDiagnosticsResponse | null;
}

export interface PolicyMemoryDisclosureResponse {
  mode: string | null;
  promptStyle: string | null;
  toolExpansionEnabled: boolean | null;
  disclosureHintsEnabled: boolean | null;
  detailMinScore: number | null;
}

export interface PolicyMemoryRerankingResponse {
  enabled: boolean | null;
  profile: string | null;
}

export interface PolicyMemoryDiagnosticsResponse {
  verbosity: string | null;
}

export interface PolicyMcpConfigResponse {
  enabled: boolean | null;
  defaultStartupTimeout: number | null;
  defaultIdleTimeout: number | null;
  catalog: PolicyMcpCatalogEntryResponse[];
}

export interface PolicyMcpCatalogEntryResponse {
  name: string | null;
  description: string | null;
  command: string | null;
  envPresent: Record<string, boolean>;
  startupTimeoutSeconds: number | null;
  idleTimeoutMinutes: number | null;
  enabled: boolean | null;
}

export interface PolicyAutonomyConfigResponse {
  enabled: boolean | null;
  tickIntervalSeconds: number | null;
  taskTimeLimitMinutes: number | null;
  autoStart: boolean | null;
  maxGoals: number | null;
  modelTier: string | null;
  reflectionEnabled: boolean | null;
  reflectionFailureThreshold: number | null;
  reflectionModelTier: string | null;
  reflectionTierPriority: boolean | null;
  notifyMilestones: boolean | null;
}

export interface PolicySdlcConfigResponse {
  taskCreationEnabled: boolean | null;
  autoApplyDecompositionEnabled: boolean | null;
  maxDecompositionFanOut: number | null;
  assignmentEnabled: boolean | null;
  reviewerAssignmentEnabled: boolean | null;
  requireReviewerSeparationOfDuties: boolean | null;
  approvalRequiredAboveFanOut: number | null;
  allowedCardKinds: string[];
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
  tools: PolicyToolsConfigResponse | null;
  memory: PolicyMemoryConfigResponse | null;
  mcp: PolicyMcpConfigResponse | null;
  autonomy: PolicyAutonomyConfigResponse | null;
  sdlc?: PolicySdlcConfigResponse | null;
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

export interface PolicyDraftEnvironmentVariable {
  name?: string | null;
  value?: string | null;
}

export interface PolicyDraftToolsConfig {
  filesystemEnabled?: boolean | null;
  shellEnabled?: boolean | null;
  skillManagementEnabled?: boolean | null;
  skillTransitionEnabled?: boolean | null;
  tierEnabled?: boolean | null;
  goalManagementEnabled?: boolean | null;
  shellEnvironmentVariables?: PolicyDraftEnvironmentVariable[];
}

export interface PolicyDraftMemoryConfig {
  version?: number | null;
  enabled?: boolean | null;
  softPromptBudgetTokens?: number | null;
  maxPromptBudgetTokens?: number | null;
  workingTopK?: number | null;
  episodicTopK?: number | null;
  semanticTopK?: number | null;
  proceduralTopK?: number | null;
  promotionEnabled?: boolean | null;
  promotionMinConfidence?: number | null;
  decayEnabled?: boolean | null;
  decayDays?: number | null;
  retrievalLookbackDays?: number | null;
  codeAwareExtractionEnabled?: boolean | null;
  disclosure?: PolicyDraftMemoryDisclosure | null;
  reranking?: PolicyDraftMemoryReranking | null;
  diagnostics?: PolicyDraftMemoryDiagnostics | null;
}

export interface PolicyDraftMemoryDisclosure {
  mode?: string | null;
  promptStyle?: string | null;
  toolExpansionEnabled?: boolean | null;
  disclosureHintsEnabled?: boolean | null;
  detailMinScore?: number | null;
}

export interface PolicyDraftMemoryReranking {
  enabled?: boolean | null;
  profile?: string | null;
}

export interface PolicyDraftMemoryDiagnostics {
  verbosity?: string | null;
}

export interface PolicyDraftMcpConfig {
  enabled?: boolean | null;
  defaultStartupTimeout?: number | null;
  defaultIdleTimeout?: number | null;
  catalog?: PolicyDraftMcpCatalogEntry[];
}

export interface PolicyDraftMcpCatalogEntry {
  name?: string | null;
  description?: string | null;
  command?: string | null;
  env?: Record<string, string | null> | null;
  startupTimeoutSeconds?: number | null;
  idleTimeoutMinutes?: number | null;
  enabled?: boolean | null;
}

export interface PolicyDraftAutonomyConfig {
  enabled?: boolean | null;
  tickIntervalSeconds?: number | null;
  taskTimeLimitMinutes?: number | null;
  autoStart?: boolean | null;
  maxGoals?: number | null;
  modelTier?: string | null;
  reflectionEnabled?: boolean | null;
  reflectionFailureThreshold?: number | null;
  reflectionModelTier?: string | null;
  reflectionTierPriority?: boolean | null;
  notifyMilestones?: boolean | null;
}

export interface PolicyDraftSdlcConfig {
  taskCreationEnabled?: boolean | null;
  autoApplyDecompositionEnabled?: boolean | null;
  maxDecompositionFanOut?: number | null;
  assignmentEnabled?: boolean | null;
  reviewerAssignmentEnabled?: boolean | null;
  requireReviewerSeparationOfDuties?: boolean | null;
  approvalRequiredAboveFanOut?: number | null;
  allowedCardKinds?: string[];
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
  tools: PolicyDraftToolsConfig | null;
  memory: PolicyDraftMemoryConfig | null;
  mcp: PolicyDraftMcpConfig | null;
  autonomy: PolicyDraftAutonomyConfig | null;
  sdlc?: PolicyDraftSdlcConfig | null;
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
