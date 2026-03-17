import { apiRequest } from './httpClient';

export type ThreadTargetGolem = {
  id: string;
  displayName: string;
  state: string;
  roleSlugs: string[];
};

export type CardThread = {
  threadId: string;
  cardId: string;
  boardId: string;
  title: string;
  cardColumnId: string;
  assignedGolemId: string | null;
  targetGolem: ThreadTargetGolem | null;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
  lastCommandAt: string | null;
};

export type ThreadMessage = {
  id: string;
  threadId: string;
  cardId: string;
  commandId: string | null;
  runId: string | null;
  signalId: string | null;
  type: string;
  participantType: string;
  authorId: string | null;
  authorName: string | null;
  body: string;
  createdAt: string;
};

export function getCardThread(cardId: string) {
  return apiRequest<CardThread>(`/api/v1/cards/${cardId}/thread`);
}

export function listThreadMessages(threadId: string) {
  return apiRequest<ThreadMessage[]>(`/api/v1/threads/${threadId}/messages`);
}

export function postThreadMessage(threadId: string, body: string) {
  return apiRequest<ThreadMessage>(`/api/v1/threads/${threadId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}
