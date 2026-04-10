import { apiRequest } from './httpClient';

export interface OrganizationDetail {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export function getOrganization() {
  return apiRequest<OrganizationDetail>('/api/v1/organization');
}

export function updateOrganization(input: { name?: string; description?: string }) {
  return apiRequest<OrganizationDetail>('/api/v1/organization', {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}
