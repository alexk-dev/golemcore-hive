import { apiRequest } from './httpClient';
import type {
  BoardDetail,
  BoardFlow,
  BoardSummary,
  BoardTeam,
  BoardTeamResolved,
  RemapPreview,
} from './boardsApi';

export type ServiceSummary = BoardSummary;
export type ServiceDetail = BoardDetail;
export type ServiceFlow = BoardFlow;
export type ServiceRouting = BoardTeam;
export type ServiceRoutingResolved = BoardTeamResolved;

export function listServices() {
  return apiRequest<ServiceSummary[]>('/api/v1/services');
}

export function getService(serviceId: string) {
  return apiRequest<ServiceDetail>(`/api/v1/services/${serviceId}`);
}

export function createService(input: {
  name: string;
  description?: string;
  templateKey?: string;
  defaultAssignmentPolicy?: string;
}) {
  return apiRequest<ServiceDetail>('/api/v1/services', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateService(
  serviceId: string,
  input: { name?: string; description?: string; defaultAssignmentPolicy?: string },
) {
  return apiRequest<ServiceDetail>(`/api/v1/services/${serviceId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function previewServiceFlow(serviceId: string, flow: ServiceFlow) {
  return apiRequest<RemapPreview>(`/api/v1/services/${serviceId}/flow:preview`, {
    method: 'POST',
    body: JSON.stringify(flow),
  });
}

export function updateServiceFlow(
  serviceId: string,
  input: { flow: ServiceFlow; columnRemap?: Record<string, string> },
) {
  return apiRequest<ServiceDetail>(`/api/v1/services/${serviceId}/flow`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function updateServiceRouting(serviceId: string, routing: ServiceRouting) {
  return apiRequest<ServiceDetail>(`/api/v1/services/${serviceId}/team`, {
    method: 'PUT',
    body: JSON.stringify(routing),
  });
}

export function getServiceRouting(serviceId: string) {
  return apiRequest<ServiceRoutingResolved>(`/api/v1/services/${serviceId}/team`);
}
