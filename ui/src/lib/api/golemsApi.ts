import { apiRequest } from './httpClient';

export interface GolemCapabilitySnapshot {
  providers: string[];
  modelFamilies: string[];
  enabledTools: string[];
  enabledAutonomyFeatures: string[];
  capabilityTags: string[];
  supportedChannels: string[];
  snapshotHash: string | null;
  defaultModel: string | null;
}

export interface HeartbeatSnapshot {
  status: string | null;
  currentRunState: string | null;
  currentCardId: string | null;
  currentThreadId: string | null;
  modelTier: string | null;
  inputTokens: number;
  outputTokens: number;
  accumulatedCostMicros: number;
  queueDepth: number;
  healthSummary: string | null;
  lastErrorSummary: string | null;
  uptimeSeconds: number;
  capabilitySnapshotHash: string | null;
}

export interface GolemSummary {
  id: string;
  displayName: string;
  hostLabel: string | null;
  runtimeVersion: string | null;
  state: string;
  lastHeartbeatAt: string | null;
  lastSeenAt: string | null;
  missedHeartbeatCount: number;
  roleSlugs: string[];
}

export interface GolemDetails {
  id: string;
  displayName: string;
  hostLabel: string | null;
  runtimeVersion: string | null;
  buildVersion: string | null;
  state: string;
  registeredAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  lastHeartbeatAt: string | null;
  lastSeenAt: string | null;
  lastStateChangeAt: string | null;
  heartbeatIntervalSeconds: number;
  missedHeartbeatCount: number;
  pauseReason: string | null;
  revokeReason: string | null;
  controlChannelUrl: string | null;
  supportedChannels: string[];
  capabilities: GolemCapabilitySnapshot | null;
  lastHeartbeat: HeartbeatSnapshot | null;
  roleSlugs: string[];
}

export interface EnrollmentToken {
  id: string;
  preview: string;
  note: string | null;
  createdByUsername: string | null;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  registrationCount: number;
  revokedAt: string | null;
  lastRegisteredGolemId: string | null;
  revokeReason: string | null;
  revoked: boolean;
}

export interface EnrollmentTokenCreated {
  id: string;
  token: string;
  joinCode: string;
  preview: string;
  note: string | null;
  createdAt: string;
  expiresAt: string;
}

export interface GolemRole {
  slug: string;
  name: string;
  description: string | null;
  capabilityTags: string[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface FleetFilters {
  query?: string;
  state?: string;
  role?: string;
}

interface RolePayload {
  slug?: string;
  name?: string;
  description?: string;
  capabilityTags?: string[];
}

function buildQueryString(filters: FleetFilters): string {
  const params = new URLSearchParams();
  if (filters.query) {
    params.set('query', filters.query);
  }
  if (filters.state) {
    params.set('state', filters.state);
  }
  if (filters.role) {
    params.set('role', filters.role);
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

export function listGolems(filters: FleetFilters = {}) {
  return apiRequest<GolemSummary[]>(`/api/v1/golems${buildQueryString(filters)}`);
}

export function getGolem(golemId: string) {
  return apiRequest<GolemDetails>(`/api/v1/golems/${golemId}`);
}

export function listEnrollmentTokens() {
  return apiRequest<EnrollmentToken[]>('/api/v1/enrollment-tokens');
}

export function createEnrollmentToken(note: string, expiresInMinutes?: number | null) {
  return apiRequest<EnrollmentTokenCreated>('/api/v1/enrollment-tokens', {
    method: 'POST',
    body: JSON.stringify({
      note,
      expiresInMinutes: expiresInMinutes || undefined,
    }),
  });
}

export function revokeEnrollmentToken(tokenId: string, reason?: string) {
  return apiRequest<EnrollmentToken>(`/api/v1/enrollment-tokens/${tokenId}:revoke`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function listGolemRoles() {
  return apiRequest<GolemRole[]>('/api/v1/golem-roles');
}

export function createGolemRole(payload: RolePayload & { slug: string }) {
  return apiRequest<GolemRole>('/api/v1/golem-roles', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateGolemRole(roleSlug: string, payload: RolePayload) {
  return apiRequest<GolemRole>(`/api/v1/golem-roles/${roleSlug}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function assignGolemRoles(golemId: string, roleSlugs: string[]) {
  return apiRequest<string[]>(`/api/v1/golems/${golemId}/roles:assign`, {
    method: 'POST',
    body: JSON.stringify({ roleSlugs }),
  });
}

export function unassignGolemRoles(golemId: string, roleSlugs: string[]) {
  return apiRequest<string[]>(`/api/v1/golems/${golemId}/roles:unassign`, {
    method: 'POST',
    body: JSON.stringify({ roleSlugs }),
  });
}

export function pauseGolem(golemId: string, reason?: string) {
  return apiRequest<GolemDetails>(`/api/v1/golems/${golemId}:pause`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function resumeGolem(golemId: string) {
  return apiRequest<GolemDetails>(`/api/v1/golems/${golemId}:resume`, {
    method: 'POST',
  });
}

export function revokeGolem(golemId: string, reason?: string) {
  return apiRequest<GolemDetails>(`/api/v1/golems/${golemId}:revoke`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}
