import { apiRequest } from './httpClient';

export type ApprovalRequest = {
  id: string;
  commandId: string;
  runId: string;
  threadId: string;
  boardId: string;
  cardId: string;
  golemId: string;
  requestedByActorId: string;
  requestedByActorName: string;
  riskLevel: string;
  reason: string | null;
  estimatedCostMicros: number;
  commandBody: string;
  status: string;
  requestedAt: string;
  updatedAt: string;
  decidedAt: string | null;
  decidedByActorId: string | null;
  decidedByActorName: string | null;
  decisionComment: string | null;
};

type ApprovalFilter = {
  status?: string;
  boardId?: string;
  cardId?: string;
  golemId?: string;
};

function buildQuery(filter: ApprovalFilter) {
  const params = new URLSearchParams();
  if (filter.status) {
    params.set('status', filter.status);
  }
  if (filter.boardId) {
    params.set('boardId', filter.boardId);
  }
  if (filter.cardId) {
    params.set('cardId', filter.cardId);
  }
  if (filter.golemId) {
    params.set('golemId', filter.golemId);
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

export function listApprovals(filter: ApprovalFilter = {}) {
  return apiRequest<ApprovalRequest[]>(`/api/v1/approvals${buildQuery(filter)}`);
}

export function approveApproval(approvalId: string, comment: string) {
  return apiRequest<ApprovalRequest>(`/api/v1/approvals/${approvalId}:approve`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  });
}

export function rejectApproval(approvalId: string, comment: string) {
  return apiRequest<ApprovalRequest>(`/api/v1/approvals/${approvalId}:reject`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  });
}
