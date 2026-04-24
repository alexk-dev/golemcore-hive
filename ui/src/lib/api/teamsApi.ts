import { apiRequest } from './httpClient';

export interface TeamDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  lifecycleState: string;
  golemIds: string[];
  ownedServiceIds: string[];
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

function buildTeamsPath(includeArchived?: boolean) {
  const params = new URLSearchParams();
  if (includeArchived) {
    params.set('includeArchived', 'true');
  }
  const query = params.toString();
  return query ? `/api/v1/teams?${query}` : '/api/v1/teams';
}

export function listTeams(options: { includeArchived?: boolean } = {}) {
  return apiRequest<TeamDetail[]>(buildTeamsPath(options.includeArchived));
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

export function archiveTeam(teamId: string) {
  return apiRequest<TeamDetail>(`/api/v1/teams/${teamId}:archive`, {
    method: 'POST',
  });
}

export function restoreTeam(teamId: string) {
  return apiRequest<TeamDetail>(`/api/v1/teams/${teamId}:restore`, {
    method: 'POST',
  });
}
