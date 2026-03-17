import { apiRequest } from './httpClient';

export type CommandRecord = {
  id: string;
  threadId: string;
  cardId: string;
  golemId: string;
  runId: string;
  body: string;
  status: string;
  queueReason: string | null;
  dispatchAttempts: number;
  createdAt: string;
  updatedAt: string;
  lastDispatchAttemptAt: string | null;
  deliveredAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
};

export type RunProjection = {
  id: string;
  threadId: string;
  cardId: string;
  commandId: string | null;
  golemId: string | null;
  status: string;
  summary: string | null;
  lastRuntimeEventType: string | null;
  lastSignalType: string | null;
  eventCount: number;
  inputTokens: number;
  outputTokens: number;
  accumulatedCostMicros: number;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

export type EvidenceRef = {
  kind: string;
  ref: string;
};

export type CardLifecycleSignal = {
  id: string;
  cardId: string;
  golemId: string;
  commandId: string | null;
  runId: string | null;
  threadId: string | null;
  signalType: string;
  summary: string;
  details: string | null;
  blockerCode: string | null;
  evidenceRefs: EvidenceRef[];
  createdAt: string;
  decision: string | null;
  resolvedTargetColumnId: string | null;
  resolutionOutcome: string | null;
  resolutionSummary: string | null;
  resolvedAt: string | null;
};

export function listThreadCommands(threadId: string) {
  return apiRequest<CommandRecord[]>(`/api/v1/threads/${threadId}/commands`);
}

export function createThreadCommand(threadId: string, body: string) {
  return apiRequest<CommandRecord>(`/api/v1/threads/${threadId}/commands`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  });
}

export function listThreadRuns(threadId: string) {
  return apiRequest<RunProjection[]>(`/api/v1/threads/${threadId}/runs`);
}

export function listThreadSignals(threadId: string) {
  return apiRequest<CardLifecycleSignal[]>(`/api/v1/threads/${threadId}/signals`);
}
