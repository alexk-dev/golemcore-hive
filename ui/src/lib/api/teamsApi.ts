import { apiRequest } from './httpClient';

export interface TeamDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  golemIds: string[];
  ownedServiceIds: string[];
  createdAt: string;
  updatedAt: string;
}

export function listTeams() {
  return apiRequest<TeamDetail[]>('/api/v1/teams');
}

export function getTeam(teamId: string) {
  return apiRequest<TeamDetail>(`/api/v1/teams/${teamId}`);
}

export function createTeam(input: {
  name: string;
  description?: string;
  golemIds?: string[];
  ownedServiceIds?: string[];
}) {
  return apiRequest<TeamDetail>('/api/v1/teams', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateTeam(
  teamId: string,
  input: { name?: string; description?: string; golemIds?: string[]; ownedServiceIds?: string[] },
) {
  return apiRequest<TeamDetail>(`/api/v1/teams/${teamId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}
