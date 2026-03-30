import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { formatTimestamp, readErrorMessage } from '../../lib/format';
import { getGolem } from '../../lib/api/golemsApi';
import {
  compactInspectionSession,
  deleteInspectionSession,
  clearInspectionSession,
  exportInspectionSessionTrace,
  exportInspectionSessionTraceSnapshotPayload,
  getInspectionSession,
  getInspectionSessionTrace,
  getInspectionSessionTraceSummary,
  listInspectionSessions,
  type InspectionMessage,
  type InspectionSessionSummary,
} from '../../lib/api/inspectionApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';
import { InspectionTraceExplorer } from './InspectionTraceExplorer';

interface FeedbackState {
  tone: 'success' | 'danger';
  message: string;
}

function downloadJsonFile(payload: unknown, fileName: string) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.click();
  window.URL.revokeObjectURL(objectUrl);
}

function downloadBlob(blob: Blob, fileName: string) {
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.click();
  window.URL.revokeObjectURL(objectUrl);
}

function feedbackClasses(tone: FeedbackState['tone']) {
  return tone === 'success'
    ? 'border-emerald-200 bg-emerald-100 text-emerald-900'
    : 'border-rose-200 bg-rose-100 text-rose-900';
}

function buildTraceErrorMessage(summaryError: unknown, traceError: unknown): string | null {
  if (summaryError != null) {
    return `Failed to load trace summary: ${readErrorMessage(summaryError)}`;
  }
  if (traceError != null) {
    return `Failed to load trace details: ${readErrorMessage(traceError)}`;
  }
  return null;
}

function MessageCard({ message }: { message: InspectionMessage }) {
  const tags = [
    message.skill ? `skill ${message.skill}` : null,
    message.modelTier ? `tier ${message.modelTier}` : null,
    message.model ? `model ${message.model}` : null,
    message.reasoning ? `reasoning ${message.reasoning}` : null,
    message.hasToolCalls ? 'tool calls' : null,
    message.hasVoice ? 'voice' : null,
  ].filter((value): value is string => value != null && value.length > 0);

  return (
    <article
      className={
        message.role === 'user'
          ? 'border border-sky-200 bg-sky-50 p-3'
          : message.role === 'assistant'
            ? 'border border-emerald-200 bg-emerald-50 p-3'
            : 'border border-border bg-white p-3'
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">
            {message.role === 'user' ? 'User' : message.role === 'assistant' ? 'Assistant' : message.role}
          </p>
          <p className="text-xs text-muted-foreground">{formatTimestamp(message.timestamp)}</p>
        </div>
        {message.clientMessageId ? (
          <span className="text-[11px] text-muted-foreground">client {message.clientMessageId}</span>
        ) : null}
      </div>

      {tags.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-2">
          {tags.map((tag) => (
            <span
              key={`${message.id}:${tag}`}
              className="inline-flex items-center border border-border bg-white px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground"
            >
              {tag}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-3 whitespace-pre-wrap break-words text-sm text-foreground">{message.content || ' '}</div>

      {message.attachments.length > 0 ? (
        <div className="mt-3 border-t border-border/60 pt-3">
          <p className="text-xs font-semibold text-muted-foreground">Attachments</p>
          <div className="mt-2 grid gap-2">
            {message.attachments.map((attachment, index) => (
              <div key={`${message.id}:attachment:${index}`} className="border border-border/70 bg-white/80 px-3 py-2 text-xs">
                <p className="font-semibold text-foreground">{attachment.name || attachment.type || 'Attachment'}</p>
                <p className="text-muted-foreground">
                  {attachment.mimeType || 'n/a'}
                  {attachment.internalFilePath ? ` · ${attachment.internalFilePath}` : ''}
                </p>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </article>
  );
}

function SessionListItem({
  session,
  selected,
  onSelect,
}: {
  session: InspectionSessionSummary;
  selected: boolean;
  onSelect: (sessionId: string) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onSelect(session.id)}
      className={
        selected
          ? 'border border-primary/40 bg-primary/5 p-3 text-left'
          : 'border border-border/70 bg-white/70 p-3 text-left transition hover:bg-white'
      }
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{session.title || session.id}</p>
          <p className="text-xs text-muted-foreground">
            {session.channelType} · {session.transportChatId || session.chatId}
          </p>
        </div>
        <span className="text-[11px] text-muted-foreground">{session.messageCount}</span>
      </div>
      {session.preview ? (
        <p className="mt-2 line-clamp-2 text-xs text-muted-foreground">{session.preview}</p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2 text-[11px] text-muted-foreground">
        <span>{session.state}</span>
        <span>{formatTimestamp(session.updatedAt)}</span>
      </div>
    </button>
  );
}

export function InspectionPage() {
  const { golemId } = useParams();
  const queryClient = useQueryClient();
  const [channelFilter, setChannelFilter] = useState('');
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [keepLast, setKeepLast] = useState(20);
  const [detailsRequested, setDetailsRequested] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);

  const golemQuery = useQuery({
    queryKey: ['golem', golemId],
    queryFn: () => getGolem(golemId ?? ''),
    enabled: Boolean(golemId),
    refetchInterval: 10_000,
  });

  const isOnline = golemQuery.data?.state === 'ONLINE';

  const sessionsQuery = useQuery({
    queryKey: ['inspection-sessions', golemId, channelFilter],
    queryFn: () => listInspectionSessions(golemId ?? '', channelFilter || undefined),
    enabled: Boolean(golemId && isOnline),
    refetchInterval: 10_000,
  });

  useEffect(() => {
    const sessions = sessionsQuery.data ?? [];
    if (sessions.length === 0) {
      setSelectedSessionId(null);
      return;
    }
    if (selectedSessionId == null) {
      setSelectedSessionId(sessions[0].id);
      return;
    }
    if (!sessions.some((session) => session.id === selectedSessionId)) {
      setSelectedSessionId(sessions[0].id);
    }
  }, [selectedSessionId, sessionsQuery.data]);

  useEffect(() => {
    setDetailsRequested(false);
  }, [selectedSessionId]);

  const sessionQuery = useQuery({
    queryKey: ['inspection-session', golemId, selectedSessionId],
    queryFn: () => getInspectionSession(golemId ?? '', selectedSessionId ?? ''),
    enabled: Boolean(golemId && selectedSessionId && isOnline),
    refetchInterval: 10_000,
  });

  const traceSummaryQuery = useQuery({
    queryKey: ['inspection-trace-summary', golemId, selectedSessionId],
    queryFn: () => getInspectionSessionTraceSummary(golemId ?? '', selectedSessionId ?? ''),
    enabled: Boolean(golemId && selectedSessionId && isOnline),
    refetchInterval: 10_000,
  });

  const traceQuery = useQuery({
    queryKey: ['inspection-trace', golemId, selectedSessionId],
    queryFn: () => getInspectionSessionTrace(golemId ?? '', selectedSessionId ?? ''),
    enabled: Boolean(golemId && selectedSessionId && isOnline && detailsRequested),
  });

  const invalidateInspectionQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['inspection-sessions', golemId] }),
      queryClient.invalidateQueries({ queryKey: ['inspection-session', golemId] }),
      queryClient.invalidateQueries({ queryKey: ['inspection-trace-summary', golemId] }),
      queryClient.invalidateQueries({ queryKey: ['inspection-trace', golemId] }),
      queryClient.invalidateQueries({ queryKey: ['golem', golemId] }),
    ]);
  };

  const compactMutation = useMutation({
    mutationFn: async () => compactInspectionSession(golemId ?? '', selectedSessionId ?? '', keepLast),
    onSuccess: async (result) => {
      setFeedback({ tone: 'success', message: `Compaction complete. Removed ${result.removed} messages.` });
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => {
      setFeedback({ tone: 'danger', message: readErrorMessage(error) });
    },
  });

  const clearMutation = useMutation({
    mutationFn: async () => clearInspectionSession(golemId ?? '', selectedSessionId ?? ''),
    onSuccess: async () => {
      setFeedback({ tone: 'success', message: 'Session history cleared.' });
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => {
      setFeedback({ tone: 'danger', message: readErrorMessage(error) });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async () => deleteInspectionSession(golemId ?? '', selectedSessionId ?? ''),
    onSuccess: async () => {
      setFeedback({ tone: 'success', message: 'Session deleted.' });
      setSelectedSessionId(null);
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => {
      setFeedback({ tone: 'danger', message: readErrorMessage(error) });
    },
  });

  const traceExportMutation = useMutation({
    mutationFn: async () => exportInspectionSessionTrace(golemId ?? '', selectedSessionId ?? ''),
    onSuccess: (payload) => {
      downloadJsonFile(payload, `session-trace-${selectedSessionId ?? 'inspection'}.json`);
      setFeedback({ tone: 'success', message: 'Trace export downloaded.' });
    },
    onError: (error) => {
      setFeedback({ tone: 'danger', message: readErrorMessage(error) });
    },
  });

  const snapshotExportMutation = useMutation({
    mutationFn: async (input: { snapshotId: string; role: string | null; spanName: string | null }) =>
      exportInspectionSessionTraceSnapshotPayload(golemId ?? '', selectedSessionId ?? '', input.snapshotId),
    onSuccess: (payload, variables) => {
      const fileName =
        payload.fileName
        ?? `session-trace-${selectedSessionId ?? 'inspection'}-${variables.spanName ?? variables.snapshotId}.json`;
      downloadBlob(payload.blob, fileName);
      setFeedback({ tone: 'success', message: 'Snapshot payload downloaded.' });
    },
    onError: (error) => {
      setFeedback({ tone: 'danger', message: readErrorMessage(error) });
    },
  });

  const selectedSessionSummary = useMemo(
    () => (sessionsQuery.data ?? []).find((session) => session.id === selectedSessionId) ?? null,
    [selectedSessionId, sessionsQuery.data],
  );

  const channelOptions = useMemo(() => {
    const values = new Set(['']);
    (sessionsQuery.data ?? []).forEach((session) => values.add(session.channelType));
    return Array.from(values);
  }, [sessionsQuery.data]);

  if (golemId == null || golemId.length === 0) {
    return (
      <section className="panel p-4">
        <p className="text-sm text-muted-foreground">Missing golem id.</p>
      </section>
    );
  }

  return (
    <div className="grid gap-4">
      <section className="panel p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <Link to="/fleet" className="text-xs font-semibold text-primary hover:underline">
              Back to Fleet
            </Link>
            <div className="mt-2 flex flex-wrap items-center gap-3">
              <h1 className="text-lg font-bold tracking-tight text-foreground">
                Inspection {golemQuery.data?.displayName ? `· ${golemQuery.data.displayName}` : ''}
              </h1>
              {golemQuery.data ? <GolemStatusBadge state={golemQuery.data.state} /> : null}
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              {golemQuery.data ? `${golemQuery.data.hostLabel || golemQuery.data.id} · ${golemQuery.data.runtimeVersion || 'n/a'}` : 'Loading golem profile...'}
            </p>
          </div>

          <div className="min-w-[180px]">
            <label className="grid gap-1 text-xs font-semibold text-muted-foreground">
              Channel
              <select
                value={channelFilter}
                onChange={(event) => setChannelFilter(event.target.value)}
                disabled={!isOnline}
                className="border border-border bg-white px-3 py-2 text-sm font-medium text-foreground outline-none transition focus:border-primary disabled:opacity-60"
              >
                {channelOptions.map((value) => (
                  <option key={value || 'all'} value={value}>
                    {value || 'All channels'}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      </section>

      {feedback ? (
        <section className={`border p-3 text-sm font-medium ${feedbackClasses(feedback.tone)}`}>
          {feedback.message}
        </section>
      ) : null}

      {golemQuery.isLoading ? (
        <section className="panel p-4">
          <p className="text-sm text-muted-foreground">Loading golem profile...</p>
        </section>
      ) : null}

      {golemQuery.error ? (
        <section className="border border-rose-200 bg-rose-100 p-4 text-sm text-rose-900">
          {readErrorMessage(golemQuery.error)}
        </section>
      ) : null}

      {golemQuery.data && !isOnline ? (
        <section className="panel p-6">
          <h2 className="text-sm font-bold text-foreground">Inspection unavailable</h2>
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
            Inspection is only available while the golem is online. Reconnect this golem and open the page again to browse sessions, read messages, and inspect traces.
          </p>
        </section>
      ) : null}

      {isOnline ? (
        <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
          <aside className="panel p-3">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-bold text-foreground">Sessions</h2>
              <span className="text-xs text-muted-foreground">{(sessionsQuery.data ?? []).length}</span>
            </div>

            <div className="mt-3">
              {sessionsQuery.isLoading ? <p className="text-sm text-muted-foreground">Loading sessions...</p> : null}
              {sessionsQuery.error ? (
                <p className="text-sm text-rose-900">{readErrorMessage(sessionsQuery.error)}</p>
              ) : null}
              {(sessionsQuery.data ?? []).length === 0 && !sessionsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">No sessions reported by this golem for the selected channel.</p>
              ) : null}
              <div className="grid gap-2">
                {(sessionsQuery.data ?? []).map((session) => (
                  <SessionListItem
                    key={session.id}
                    session={session}
                    selected={session.id === selectedSessionId}
                    onSelect={(sessionId) => {
                      setSelectedSessionId(sessionId);
                      setFeedback(null);
                    }}
                  />
                ))}
              </div>
            </div>
          </aside>

          <div className="grid gap-4">
            {selectedSessionId == null ? (
              <section className="panel p-6">
                <p className="text-sm text-muted-foreground">Select a session to read messages and inspect traces.</p>
              </section>
            ) : (
              <>
                <section className="panel p-4">
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                      <h2 className="text-sm font-bold text-foreground">
                        {selectedSessionSummary?.title || selectedSessionId}
                      </h2>
                      <div className="mt-2 flex flex-wrap gap-3 text-xs text-muted-foreground">
                        <span>{selectedSessionSummary?.channelType ?? sessionQuery.data?.channelType ?? 'unknown'}</span>
                        <span>{selectedSessionSummary?.conversationKey ?? sessionQuery.data?.conversationKey ?? selectedSessionId}</span>
                        <span>{formatTimestamp(selectedSessionSummary?.updatedAt ?? sessionQuery.data?.updatedAt ?? null)}</span>
                      </div>
                      {selectedSessionSummary?.preview ? (
                        <p className="mt-3 max-w-3xl text-sm text-muted-foreground">{selectedSessionSummary.preview}</p>
                      ) : null}
                    </div>

                    <div className="grid gap-3 md:grid-cols-[minmax(0,120px)_repeat(4,minmax(0,auto))]">
                      <label className="grid gap-1 text-xs font-semibold text-muted-foreground">
                        Keep last
                        <input
                          type="number"
                          min={1}
                          value={keepLast}
                          onChange={(event) => setKeepLast(Number(event.target.value))}
                          className="border border-border bg-white px-3 py-1.5 text-sm font-medium text-foreground outline-none transition focus:border-primary"
                        />
                      </label>
                      <button
                        type="button"
                        onClick={() => {
                          void compactMutation.mutateAsync();
                        }}
                        disabled={compactMutation.isPending || clearMutation.isPending || deleteMutation.isPending}
                        className="border border-border bg-white px-3 py-2 text-xs font-semibold text-foreground disabled:opacity-60"
                      >
                        {compactMutation.isPending ? 'Compacting...' : 'Compact'}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm('Clear all messages and trace data for this session?')) {
                            void clearMutation.mutateAsync();
                          }
                        }}
                        disabled={compactMutation.isPending || clearMutation.isPending || deleteMutation.isPending}
                        className="border border-border bg-white px-3 py-2 text-xs font-semibold text-foreground disabled:opacity-60"
                      >
                        {clearMutation.isPending ? 'Clearing...' : 'Clear'}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          void traceExportMutation.mutateAsync();
                        }}
                        disabled={traceExportMutation.isPending}
                        className="border border-border bg-white px-3 py-2 text-xs font-semibold text-foreground disabled:opacity-60"
                      >
                        {traceExportMutation.isPending ? 'Exporting...' : 'Export trace'}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm('Delete this session permanently?')) {
                            void deleteMutation.mutateAsync();
                          }
                        }}
                        disabled={compactMutation.isPending || clearMutation.isPending || deleteMutation.isPending}
                        className="border border-rose-300 bg-rose-100 px-3 py-2 text-xs font-semibold text-rose-900 disabled:opacity-60"
                      >
                        {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
                      </button>
                    </div>
                  </div>
                </section>

                <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
                  <section className="panel p-4">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <h3 className="text-sm font-bold text-foreground">Messages</h3>
                        <p className="text-xs text-muted-foreground">
                          {(sessionQuery.data?.messages ?? []).length} visible messages in this session
                        </p>
                      </div>
                    </div>

                    <div className="mt-4 grid gap-3">
                      {sessionQuery.isLoading ? <p className="text-sm text-muted-foreground">Loading messages...</p> : null}
                      {sessionQuery.error ? (
                        <p className="text-sm text-rose-900">{readErrorMessage(sessionQuery.error)}</p>
                      ) : null}
                      {(sessionQuery.data?.messages ?? []).length === 0 && !sessionQuery.isLoading ? (
                        <p className="text-sm text-muted-foreground">No messages stored for this session.</p>
                      ) : null}
                      {(sessionQuery.data?.messages ?? []).map((message) => (
                        <MessageCard key={message.id} message={message} />
                      ))}
                    </div>
                  </section>

                  <InspectionTraceExplorer
                    summary={traceSummaryQuery.data ?? null}
                    trace={traceQuery.data ?? null}
                    messages={sessionQuery.data?.messages ?? []}
                    isLoadingSummary={traceSummaryQuery.isLoading}
                    isLoadingTrace={traceQuery.isLoading}
                    errorMessage={buildTraceErrorMessage(traceSummaryQuery.error, traceQuery.error)}
                    onLoadTrace={() => setDetailsRequested(true)}
                    onExportTrace={() => {
                      void traceExportMutation.mutateAsync();
                    }}
                    onExportSnapshotPayload={async (snapshotId, role, spanName) => {
                      await snapshotExportMutation.mutateAsync({ snapshotId, role, spanName });
                    }}
                    isExporting={traceExportMutation.isPending}
                    isExportingSnapshot={snapshotExportMutation.isPending}
                  />
                </div>
              </>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
