import { apiRequest } from './httpClient';
import type { CardKind } from './cardsApi';

export type DecompositionPlanStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'APPLIED';
export type DecompositionPlanLinkType = 'CHILD_OF' | 'DEPENDS_ON' | 'REVIEWS';

export interface DecompositionAssignmentSpec {
  columnId: string | null;
  teamId: string | null;
  objectiveId: string | null;
  assigneeGolemId: string | null;
  assignmentPolicy: string | null;
  autoAssign: boolean;
}

export interface DecompositionReviewSpec {
  reviewerGolemIds: string[];
  reviewerTeamId: string | null;
  requiredReviewCount: number;
}

export interface DecompositionPlanItem {
  clientItemId: string;
  kind: CardKind;
  title: string;
  description: string | null;
  prompt: string | null;
  acceptanceCriteria: string[];
  assignment: DecompositionAssignmentSpec | null;
  review: DecompositionReviewSpec | null;
  createdCardId: string | null;
  materializedAt: string | null;
}

export interface DecompositionPlanLink {
  fromClientItemId: string;
  toClientItemId: string;
  type: DecompositionPlanLinkType;
}

export interface DecompositionPlan {
  id: string;
  sourceCardId: string;
  epicCardId: string | null;
  serviceId: string;
  objectiveId: string | null;
  teamId: string | null;
  plannerGolemId: string | null;
  plannerDisplayName: string | null;
  status: DecompositionPlanStatus;
  items: DecompositionPlanItem[];
  links: DecompositionPlanLink[];
  rationale: string | null;
  createdAt: string;
  updatedAt: string;
  approvedAt: string | null;
  approvedByActorId: string | null;
  approvedByActorName: string | null;
  approvalComment: string | null;
  rejectedAt: string | null;
  rejectedByActorId: string | null;
  rejectedByActorName: string | null;
  rejectionComment: string | null;
  appliedAt: string | null;
  appliedByActorId: string | null;
  appliedByActorName: string | null;
}

export interface DecompositionPlanCreatedCard {
  clientItemId: string;
  cardId: string;
  threadId: string | null;
  title: string | null;
  kind: CardKind | null;
}

export interface DecompositionPlanApplicationResult {
  plan: DecompositionPlan;
  createdCards: DecompositionPlanCreatedCard[];
  alreadyApplied: boolean;
}

export interface CreateDecompositionPlanInput {
  epicCardId?: string | null;
  rationale?: string | null;
  items: DecompositionPlanItemInput[];
  links?: DecompositionPlanLinkInput[];
}

export interface DecompositionPlanItemInput {
  clientItemId: string;
  kind: CardKind;
  title: string;
  description?: string | null;
  prompt?: string | null;
  acceptanceCriteria?: string[];
  assignment?: DecompositionAssignmentSpecInput | null;
  review?: DecompositionReviewSpecInput | null;
}

export interface DecompositionAssignmentSpecInput {
  columnId?: string | null;
  teamId?: string | null;
  objectiveId?: string | null;
  assigneeGolemId?: string | null;
  assignmentPolicy?: string | null;
  autoAssign?: boolean;
}

export interface DecompositionReviewSpecInput {
  reviewerGolemIds?: string[];
  reviewerTeamId?: string | null;
  requiredReviewCount?: number;
}

export interface DecompositionPlanLinkInput {
  fromClientItemId: string;
  toClientItemId: string;
  type: DecompositionPlanLinkType;
}

export function listDecompositionPlans(input?: {
  sourceCardId?: string;
  epicCardId?: string;
  status?: DecompositionPlanStatus;
}) {
  const params = new URLSearchParams();
  if (input?.sourceCardId) {
    params.set('sourceCardId', input.sourceCardId);
  }
  if (input?.epicCardId) {
    params.set('epicCardId', input.epicCardId);
  }
  if (input?.status) {
    params.set('status', input.status);
  }
  const query = params.toString();
  return apiRequest<DecompositionPlan[]>(`/api/v1/decomposition-plans${query ? `?${query}` : ''}`);
}

export function getDecompositionPlan(planId: string) {
  return apiRequest<DecompositionPlan>(`/api/v1/decomposition-plans/${planId}`);
}

export function createDecompositionPlan(sourceCardId: string, input: CreateDecompositionPlanInput) {
  return apiRequest<DecompositionPlan>(`/api/v1/cards/${sourceCardId}/decomposition-plans`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function approveDecompositionPlan(planId: string, comment?: string) {
  return apiRequest<DecompositionPlan>(`/api/v1/decomposition-plans/${planId}:approve`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  });
}

export function rejectDecompositionPlan(planId: string, comment?: string) {
  return apiRequest<DecompositionPlan>(`/api/v1/decomposition-plans/${planId}:reject`, {
    method: 'POST',
    body: JSON.stringify({ comment }),
  });
}

export function applyDecompositionPlan(planId: string) {
  return apiRequest<DecompositionPlanApplicationResult>(`/api/v1/decomposition-plans/${planId}:apply`, {
    method: 'POST',
  });
}
