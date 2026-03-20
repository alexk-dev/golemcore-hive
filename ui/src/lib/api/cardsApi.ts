import { apiRequest } from './httpClient';
import type { AssignmentSuggestion } from './boardsApi';

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
  boardId: string;
  threadId: string;
  title: string;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
  position: number | null;
  archived: boolean;
  controlState: CardControlState | null;
}

export interface CardDetail {
  id: string;
  boardId: string;
  threadId: string;
  title: string;
  description: string | null;
  prompt: string;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
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
  boardId: string;
  teamCandidates: AssignmentSuggestion[];
  allCandidates: AssignmentSuggestion[];
}

export function listCards(boardId: string, includeArchived = false) {
  return apiRequest<CardSummary[]>(`/api/v1/cards?boardId=${encodeURIComponent(boardId)}&includeArchived=${includeArchived}`);
}

export function getCard(cardId: string) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}`);
}

export function createCard(input: {
  boardId: string;
  title: string;
  prompt: string;
  description?: string;
  columnId?: string;
  assigneeGolemId?: string | null;
  assignmentPolicy?: string;
  autoAssign?: boolean;
}) {
  return apiRequest<CardDetail>('/api/v1/cards', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateCard(cardId: string, input: { title?: string; description?: string; prompt?: string; assignmentPolicy?: string }) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}`, {
    method: 'PATCH',
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
