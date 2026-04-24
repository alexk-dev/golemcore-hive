import { apiRequest } from './httpClient';

export interface ObjectiveDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  status: string;
  lifecycleState: string;
  ownerTeamId: string;
  serviceIds: string[];
  participatingTeamIds: string[];
  targetDate: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

function buildObjectivesPath(includeArchived?: boolean) {
  const params = new URLSearchParams();
  if (includeArchived) {
    params.set('includeArchived', 'true');
  }
  const query = params.toString();
  return query ? `/api/v1/objectives?${query}` : '/api/v1/objectives';
}

export function listObjectives(options: { includeArchived?: boolean } = {}) {
  return apiRequest<ObjectiveDetail[]>(buildObjectivesPath(options.includeArchived));
}

export function getObjective(objectiveId: string) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}`);
}

export function createObjective(input: {
  name: string;
  description?: string;
  status?: string;
  ownerTeamId: string;
  serviceIds?: string[];
  participatingTeamIds?: string[];
  targetDate?: string | null;
}) {
  return apiRequest<ObjectiveDetail>('/api/v1/objectives', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateObjective(
  objectiveId: string,
  input: {
    name?: string;
    description?: string;
    status?: string;
    ownerTeamId?: string;
    serviceIds?: string[];
    participatingTeamIds?: string[];
    targetDate?: string | null;
    clearTargetDate?: boolean;
  },
) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function completeObjective(objectiveId: string) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}:complete`, {
    method: 'POST',
  });
}

export function reopenObjective(objectiveId: string) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}:reopen`, {
    method: 'POST',
  });
}

export function archiveObjective(objectiveId: string) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}:archive`, {
    method: 'POST',
  });
}

export function restoreObjective(objectiveId: string) {
  return apiRequest<ObjectiveDetail>(`/api/v1/objectives/${objectiveId}:restore`, {
    method: 'POST',
  });
}
