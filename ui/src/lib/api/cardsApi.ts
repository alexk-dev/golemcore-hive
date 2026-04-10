import { apiRequest } from './httpClient';
import type { AssignmentSuggestion } from './boardsApi';

export type CardKind = 'EPIC' | 'TASK' | 'REVIEW';
export type CardReviewStatus = 'NOT_REQUIRED' | 'REQUIRED' | 'IN_REVIEW' | 'APPROVED' | 'CHANGES_REQUESTED';

export interface CardTransition {
  fromColumnId: string | null;
  toColumnId: string;
  origin: string;
  summary: string;
  occurredAt: string;
  actorName: string | null;
}

export interface CardControlState {
  commandId: string | null;
  runId: string;
  golemId: string | null;
  commandStatus: string | null;
  runStatus: string;
  summary: string | null;
  queueReason: string | null;
  updatedAt: string | null;
  cancelRequestedAt: string | null;
  cancelRequestedByActorName: string | null;
  cancelRequestedPending: boolean;
  canCancel: boolean;
}

export interface CardSummary {
  id: string;
  serviceId: string;
  boardId: string;
  teamId: string | null;
  objectiveId: string | null;
  threadId: string;
  kind?: CardKind;
  title: string;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
  parentCardId?: string | null;
  epicCardId?: string | null;
  dependsOnCardIds?: string[];
  reviewOfCardId?: string | null;
  reviewerGolemIds?: string[];
  reviewerTeamId?: string | null;
  requiredReviewCount?: number;
  reviewStatus?: CardReviewStatus | null;
  position: number | null;
  archived: boolean;
  controlState: CardControlState | null;
}

export interface CardDetail {
  id: string;
  serviceId: string;
  boardId: string;
  teamId: string | null;
  objectiveId: string | null;
  threadId: string;
  kind?: CardKind;
  title: string;
  description: string | null;
  prompt: string;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
  parentCardId?: string | null;
  epicCardId?: string | null;
  dependsOnCardIds?: string[];
  reviewOfCardId?: string | null;
  reviewerGolemIds?: string[];
  reviewerTeamId?: string | null;
  requiredReviewCount?: number;
  reviewStatus?: CardReviewStatus | null;
  position: number | null;
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  lastTransitionAt: string | null;
  controlState: CardControlState | null;
  transitions: CardTransition[];
}

export interface CardAssigneeOptions {
  cardId: string;
  serviceId: string;
  boardId: string;
  teamCandidates: AssignmentSuggestion[];
  allCandidates: AssignmentSuggestion[];
}

export function listCards(serviceId: string, includeArchived = false) {
  return apiRequest<CardSummary[]>(
    `/api/v1/cards?serviceId=${encodeURIComponent(serviceId)}&includeArchived=${includeArchived}`,
  );
}

export function listCardsByQuery(input: {
  serviceId?: string;
  boardId?: string;
  objectiveId?: string;
  epicCardId?: string;
  parentCardId?: string;
  reviewOfCardId?: string;
  kind?: CardKind;
  includeArchived?: boolean;
}) {
  const params = new URLSearchParams();
  if (input.serviceId) {
    params.set('serviceId', input.serviceId);
  }
  if (input.boardId) {
    params.set('boardId', input.boardId);
  }
  if (input.objectiveId) {
    params.set('objectiveId', input.objectiveId);
  }
  if (input.epicCardId) {
    params.set('epicCardId', input.epicCardId);
  }
  if (input.parentCardId) {
    params.set('parentCardId', input.parentCardId);
  }
  if (input.reviewOfCardId) {
    params.set('reviewOfCardId', input.reviewOfCardId);
  }
  if (input.kind) {
    params.set('kind', input.kind);
  }
  params.set('includeArchived', String(input.includeArchived ?? false));
  return apiRequest<CardSummary[]>(`/api/v1/cards?${params.toString()}`);
}

export function getCard(cardId: string) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}`);
}

export function createCard(input: {
  serviceId: string;
  title: string;
  prompt: string;
  description?: string;
  columnId?: string;
  teamId?: string;
  objectiveId?: string;
  kind?: CardKind;
  parentCardId?: string | null;
  epicCardId?: string | null;
  dependsOnCardIds?: string[];
  assigneeGolemId?: string | null;
  assignmentPolicy?: string;
  autoAssign?: boolean;
}) {
  return apiRequest<CardDetail>('/api/v1/cards', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateCard(
  cardId: string,
  input: {
    title?: string;
    description?: string;
    prompt?: string;
    teamId?: string;
    objectiveId?: string;
    kind?: CardKind;
    parentCardId?: string | null;
    epicCardId?: string | null;
    dependsOnCardIds?: string[];
    assignmentPolicy?: string;
  },
) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function requestCardReview(
  cardId: string,
  input: {
    reviewerGolemIds?: string[];
    reviewerTeamId?: string | null;
    requiredReviewCount?: number | null;
  },
) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}:request-review`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function moveCard(cardId: string, input: { targetColumnId: string; targetIndex?: number; summary?: string }) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}:move`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function assignCard(cardId: string, assigneeGolemId: string | null) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}:assign`, {
    method: 'POST',
    body: JSON.stringify({ assigneeGolemId }),
  });
}

export function archiveCard(cardId: string) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}:archive`, {
    method: 'POST',
  });
}

export function getCardAssignees(cardId: string) {
  return apiRequest<CardAssigneeOptions>(`/api/v1/cards/${cardId}/assignees`);
}
