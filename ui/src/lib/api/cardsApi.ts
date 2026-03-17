import { apiRequest } from './httpClient';
import { AssignmentSuggestion } from './boardsApi';

export type CardTransition = {
  fromColumnId: string | null;
  toColumnId: string;
  origin: string;
  summary: string;
  occurredAt: string;
  actorName: string | null;
};

export type CardSummary = {
  id: string;
  boardId: string;
  threadId: string;
  title: string;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
  position: number | null;
  archived: boolean;
};

export type CardDetail = {
  id: string;
  boardId: string;
  threadId: string;
  title: string;
  description: string | null;
  columnId: string;
  assigneeGolemId: string | null;
  assignmentPolicy: string;
  position: number | null;
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  lastTransitionAt: string | null;
  transitions: CardTransition[];
};

export type CardAssigneeOptions = {
  cardId: string;
  boardId: string;
  teamCandidates: AssignmentSuggestion[];
  allCandidates: AssignmentSuggestion[];
};

export function listCards(boardId: string, includeArchived = false) {
  return apiRequest<CardSummary[]>(`/api/v1/cards?boardId=${encodeURIComponent(boardId)}&includeArchived=${includeArchived}`);
}

export function getCard(cardId: string) {
  return apiRequest<CardDetail>(`/api/v1/cards/${cardId}`);
}

export function createCard(input: {
  boardId: string;
  title: string;
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

export function updateCard(cardId: string, input: { title?: string; description?: string; assignmentPolicy?: string }) {
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
