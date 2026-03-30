import { apiRequest, apiRequestBlob } from './httpClient';

export interface InspectionSessionSummary {
  id: string;
  channelType: string;
  chatId: string;
  conversationKey: string;
  transportChatId: string | null;
  messageCount: number;
  state: string;
  createdAt: string | null;
  updatedAt: string | null;
  title: string | null;
  preview: string | null;
  active?: boolean;
}

export interface InspectionSessionAttachment {
  type: string | null;
  name: string | null;
  mimeType: string | null;
  url: string | null;
  internalFilePath: string | null;
  thumbnailBase64: string | null;
}

export interface InspectionMessage {
  id: string;
  role: string;
  content: string;
  timestamp: string | null;
  hasToolCalls: boolean;
  hasVoice: boolean;
  model: string | null;
  modelTier: string | null;
  skill: string | null;
  reasoning: string | null;
  clientMessageId: string | null;
  attachments: InspectionSessionAttachment[];
}

export interface InspectionSessionDetail {
  id: string;
  channelType: string;
  chatId: string;
  conversationKey: string;
  transportChatId: string | null;
  state: string;
  createdAt: string | null;
  updatedAt: string | null;
  messages: InspectionMessage[];
}

export interface InspectionTraceStorageStats {
  compressedSnapshotBytes: number;
  uncompressedSnapshotBytes: number;
  evictedSnapshots: number;
  evictedTraces: number;
  truncatedTraces: number;
}

export interface InspectionTraceSummaryItem {
  traceId: string;
  rootSpanId: string | null;
  traceName: string | null;
  rootKind: string | null;
  rootStatusCode: string | null;
  startedAt: string | null;
  endedAt: string | null;
  durationMs: number | null;
  spanCount: number;
  snapshotCount: number;
  truncated: boolean;
}

export interface InspectionTraceSummary {
  sessionId: string;
  traceCount: number;
  spanCount: number;
  snapshotCount: number;
  storageStats: InspectionTraceStorageStats;
  traces: InspectionTraceSummaryItem[];
}

export interface InspectionTraceSnapshot {
  snapshotId: string;
  role: string | null;
  contentType: string | null;
  encoding: string | null;
  originalSize: number | null;
  compressedSize: number | null;
  truncated: boolean;
  payloadAvailable: boolean;
  payloadPreview: string | null;
  payloadPreviewTruncated: boolean;
}

export interface InspectionTraceSpanEvent {
  name: string | null;
  timestamp: string | null;
  attributes: Record<string, unknown>;
}

export interface InspectionTraceSpan {
  spanId: string;
  parentSpanId: string | null;
  name: string | null;
  kind: string | null;
  statusCode: string | null;
  statusMessage: string | null;
  startedAt: string | null;
  endedAt: string | null;
  durationMs: number | null;
  attributes: Record<string, unknown>;
  events: InspectionTraceSpanEvent[];
  snapshots: InspectionTraceSnapshot[];
}

export interface InspectionTraceRecord {
  traceId: string;
  rootSpanId: string | null;
  traceName: string | null;
  startedAt: string | null;
  endedAt: string | null;
  truncated: boolean;
  compressedSnapshotBytes: number | null;
  uncompressedSnapshotBytes: number | null;
  spans: InspectionTraceSpan[];
}

export interface InspectionTrace {
  sessionId: string;
  storageStats: InspectionTraceStorageStats;
  traces: InspectionTraceRecord[];
}

export interface InspectionTraceExport {
  sessionId: string;
  storageStats: Record<string, unknown>;
  traces: Record<string, unknown>[];
}

export interface InspectionCompactResult {
  removed: number;
}

export interface InspectionBinaryPayload {
  blob: Blob;
  fileName: string | null;
  contentType: string | null;
}

export function listInspectionSessions(golemId: string, channel?: string) {
  const params = new URLSearchParams();
  if (channel != null && channel.trim().length > 0) {
    params.set('channel', channel.trim());
  }
  const query = params.toString();
  return apiRequest<InspectionSessionSummary[]>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions${query ? `?${query}` : ''}`,
  );
}

export function getInspectionSession(golemId: string, sessionId: string) {
  return apiRequest<InspectionSessionDetail>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}`,
  );
}

export function getInspectionSessionTraceSummary(golemId: string, sessionId: string) {
  return apiRequest<InspectionTraceSummary>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/trace/summary`,
  );
}

export function getInspectionSessionTrace(golemId: string, sessionId: string) {
  return apiRequest<InspectionTrace>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/trace`,
  );
}

export function exportInspectionSessionTrace(golemId: string, sessionId: string) {
  return apiRequest<InspectionTraceExport>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/trace/export`,
  );
}

export function exportInspectionSessionTraceSnapshotPayload(golemId: string, sessionId: string, snapshotId: string) {
  return apiRequestBlob(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/trace/snapshots/${encodeURIComponent(snapshotId)}/payload`,
  );
}

export function compactInspectionSession(golemId: string, sessionId: string, keepLast = 20) {
  return apiRequest<InspectionCompactResult>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/compact?keepLast=${keepLast}`,
    {
      method: 'POST',
    },
  );
}

export function clearInspectionSession(golemId: string, sessionId: string) {
  return apiRequest<void>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}/clear`,
    {
      method: 'POST',
    },
  );
}

export function deleteInspectionSession(golemId: string, sessionId: string) {
  return apiRequest<void>(
    `/api/v1/golems/${encodeURIComponent(golemId)}/inspection/sessions/${encodeURIComponent(sessionId)}`,
    {
      method: 'DELETE',
    },
  );
}
