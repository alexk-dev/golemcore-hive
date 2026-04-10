import { apiRequest } from './httpClient';

export interface ApprovalRequest {
  id: string;
  subjectType: string;
  commandId: string | null;
  runId: string | null;
  threadId: string | null;
  boardId: string | null;
  cardId: string | null;
  golemId: string;
  requestedByActorId: string;
  requestedByActorName: string;
  riskLevel: string | null;
  reason: string | null;
  estimatedCostMicros: number;
  commandBody: string | null;
  status: string;
  requestedAt: string;
  updatedAt: string;
  decidedAt: string | null;
  decidedByActorId: string | null;
  decidedByActorName: string | null;
  decisionComment: string | null;
  promotionContext: SelfEvolvingPromotionApprovalContext | null;
}

export interface SelfEvolvingPromotionApprovalContext {
  candidateId: string;
  goal: string | null;
  artifactType: string | null;
  riskLevel: string | null;
  expectedImpact: string | null;
  sourceRunIds: string[];
}

interface ApprovalFilter {
  status?: string;
  boardId?: string;
  cardId?: string;
  golemId?: string;
}

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
