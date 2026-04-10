import { apiRequest } from './httpClient';

export interface ObjectiveDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  status: string;
  ownerTeamId: string;
  serviceIds: string[];
  participatingTeamIds: string[];
  targetDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export function listObjectives() {
  return apiRequest<ObjectiveDetail[]>('/api/v1/objectives');
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
