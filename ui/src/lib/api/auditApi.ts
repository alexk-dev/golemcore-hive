import { apiRequest } from './httpClient';

export interface AuditEvent {
  id: string;
  eventType: string;
  severity: string;
  actorType: string | null;
  actorId: string | null;
  actorName: string | null;
  targetType: string | null;
  targetId: string | null;
  boardId: string | null;
  cardId: string | null;
  threadId: string | null;
  golemId: string | null;
  commandId: string | null;
  runId: string | null;
  approvalId: string | null;
  summary: string | null;
  details: string | null;
  createdAt: string;
}

export interface AuditFilter {
  actorId?: string;
  golemId?: string;
  boardId?: string;
  cardId?: string;
  eventType?: string;
  from?: string;
  to?: string;
}

export function listAuditEvents(filter: AuditFilter = {}) {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value) {
      params.set(key, value);
    }
  });
  const query = params.toString();
  return apiRequest<AuditEvent[]>(`/api/v1/audit${query ? `?${query}` : ''}`);
}
