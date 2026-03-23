import { apiRequest } from './httpClient';
import type { ThreadMessage } from './threadsApi';
import type { CommandRecord, RunProjection } from './commandsApi';

export interface DirectThread {
  threadId: string;
  golemId: string;
  golemDisplayName: string;
  golemState: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
  lastCommandAt: string | null;
}

export function getGolemDirectThread(golemId: string) {
  return apiRequest<DirectThread>(`/api/v1/golems/${golemId}/dm`);
}

export function listGolemDmMessages(golemId: string) {
  return apiRequest<ThreadMessage[]>(`/api/v1/golems/${golemId}/dm/messages`);
}

export function postGolemDmMessage(golemId: string, body: string) {
  return apiRequest<ThreadMessage>(`/api/v1/golems/${golemId}/dm/messages`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export function createGolemDmCommand(golemId: string, body: string) {
  return apiRequest<CommandRecord>(`/api/v1/golems/${golemId}/dm/commands`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export function listGolemDmCommands(golemId: string) {
  return apiRequest<CommandRecord[]>(`/api/v1/golems/${golemId}/dm/commands`);
}

export function listGolemDmRuns(golemId: string) {
  return apiRequest<RunProjection[]>(`/api/v1/golems/${golemId}/dm/runs`);
}
