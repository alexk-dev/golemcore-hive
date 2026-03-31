import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { listApprovals } from '../../lib/api/approvalsApi';
import { readErrorMessage } from '../../lib/format';
import { getGolem } from '../../lib/api/golemsApi';
import {
  clearInspectionSession,
  compactInspectionSession,
  deleteInspectionSession,
  exportInspectionSessionTrace,
  exportInspectionSessionTraceSnapshotPayload,
  getInspectionSession,
  getInspectionSessionTrace,
  getInspectionSessionTraceSummary,
  listInspectionSessions,
} from '../../lib/api/inspectionApi';
import {
  getSelfEvolvingLineage,
  listSelfEvolvingCandidates,
  listSelfEvolvingRuns,
} from '../../lib/api/selfEvolvingApi';
import { InspectionActionDialog } from './InspectionActionDialog';
import {
  InspectionOnlineContent,
  InspectionStatusPanels,
  MissingGolemIdPanel,
} from './InspectionPageContent';
import { InspectionFeedbackBanner, InspectionPageHeader } from './InspectionPageSections';
import { downloadBlob, downloadJsonFile } from './inspectionDownloads';
import { buildTraceErrorMessage, type FeedbackState } from './inspectionPageUtils';

type InspectionAction = 'clear' | 'delete' | null;

interface ActionDialogConfig {
  description: string;
  confirmLabel: string;
  isPending: boolean;
  title: string;
  tone: 'default' | 'danger';
}

function hasGolemId(golemId: string): boolean {
  return golemId.length > 0;
}

function canLoadSessions(golemId: string, isOnline: boolean): boolean {
  return hasGolemId(golemId) && isOnline;
}

function canLoadSelectedSession(golemId: string, selectedSessionId: string | null, isOnline: boolean): boolean {
  return hasGolemId(golemId) && selectedSessionId != null && isOnline;
}

function canLoadTrace(
  golemId: string,
  selectedSessionId: string | null,
  isOnline: boolean,
  detailsRequested: boolean,
): boolean {
  return canLoadSelectedSession(golemId, selectedSessionId, isOnline) && detailsRequested;
}

function useSelectedSession(
  sessions: Array<{ id: string }>,
  selectedSessionId: string | null,
  setSelectedSessionId: (sessionId: string | null) => void,
) {
  useEffect(() => {
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
  }, [selectedSessionId, sessions, setSelectedSessionId]);
}

function buildActionDialogConfig(
  actionDialog: InspectionAction,
  clearPending: boolean,
  deletePending: boolean,
): ActionDialogConfig | null {
  if (actionDialog === 'clear') {
    return {
      description: 'Clear all messages and trace data for this session?',
      confirmLabel: clearPending ? 'Clearing...' : 'Clear session',
      isPending: clearPending,
      title: 'Clear session',
      tone: 'default',
    };
  }

  if (actionDialog === 'delete') {
    return {
      description: 'Delete this session permanently?',
      confirmLabel: deletePending ? 'Deleting...' : 'Delete session',
      isPending: deletePending,
      title: 'Delete session',
      tone: 'danger',
    };
  }

  return null;
}

async function runConfirmedAction(
  actionDialog: InspectionAction,
  clearMutation: { mutateAsync: () => Promise<unknown> },
  deleteMutation: { mutateAsync: () => Promise<unknown> },
) {
  if (actionDialog === 'clear') {
    await clearMutation.mutateAsync();
    return;
  }

  if (actionDialog === 'delete') {
    await deleteMutation.mutateAsync();
  }
}

function useInspectionActions({
  golemId,
  selectedSessionId,
  keepLast,
  queryClient,
  setFeedback,
  setDetailsRequested,
  setSelectedSessionId,
}: {
  golemId: string;
  selectedSessionId: string | null;
  keepLast: number;
  queryClient: ReturnType<typeof useQueryClient>;
  setFeedback: (feedback: FeedbackState) => void;
  setDetailsRequested: (value: boolean) => void;
  setSelectedSessionId: (value: string | null) => void;
}) {
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
    mutationFn: async () => compactInspectionSession(golemId, selectedSessionId ?? '', keepLast),
    onSuccess: async (result) => {
      setFeedback({ tone: 'success', message: `Compaction complete. Removed ${result.removed} messages.` });
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => setFeedback({ tone: 'danger', message: readErrorMessage(error) }),
  });

  const clearMutation = useMutation({
    mutationFn: async () => clearInspectionSession(golemId, selectedSessionId ?? ''),
    onSuccess: async () => {
      setFeedback({ tone: 'success', message: 'Session history cleared.' });
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => setFeedback({ tone: 'danger', message: readErrorMessage(error) }),
  });

  const deleteMutation = useMutation({
    mutationFn: async () => deleteInspectionSession(golemId, selectedSessionId ?? ''),
    onSuccess: async () => {
      setFeedback({ tone: 'success', message: 'Session deleted.' });
      setSelectedSessionId(null);
      setDetailsRequested(false);
      await invalidateInspectionQueries();
    },
    onError: (error) => setFeedback({ tone: 'danger', message: readErrorMessage(error) }),
  });

  const traceExportMutation = useMutation({
    mutationFn: async () => exportInspectionSessionTrace(golemId, selectedSessionId ?? ''),
    onSuccess: (payload) => {
      downloadJsonFile(payload, `session-trace-${selectedSessionId ?? 'inspection'}.json`);
      setFeedback({ tone: 'success', message: 'Trace export downloaded.' });
    },
    onError: (error) => setFeedback({ tone: 'danger', message: readErrorMessage(error) }),
  });

  const snapshotExportMutation = useMutation({
    mutationFn: async (input: { snapshotId: string; role: string | null; spanName: string | null }) =>
      exportInspectionSessionTraceSnapshotPayload(golemId, selectedSessionId ?? '', input.snapshotId),
    onSuccess: (payload, variables) => {
      const fileName =
        payload.fileName
        ?? `session-trace-${selectedSessionId ?? 'inspection'}-${variables.spanName ?? variables.snapshotId}.json`;
      downloadBlob(payload.blob, fileName);
      setFeedback({ tone: 'success', message: 'Snapshot payload downloaded.' });
    },
    onError: (error) => setFeedback({ tone: 'danger', message: readErrorMessage(error) }),
  });

  return {
    clearMutation,
    compactMutation,
    deleteMutation,
    snapshotExportMutation,
    traceExportMutation,
  };
}

export function InspectionPage() {
  const { golemId } = useParams();
  const resolvedGolemId = golemId ?? '';
  const hasResolvedGolemId = hasGolemId(resolvedGolemId);
  const queryClient = useQueryClient();
  const [channelFilter, setChannelFilter] = useState('');
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [selectedSelfEvolvingRunId, setSelectedSelfEvolvingRunId] = useState<string | null>(null);
  const [keepLast, setKeepLast] = useState(20);
  const [detailsRequested, setDetailsRequested] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [actionDialog, setActionDialog] = useState<InspectionAction>(null);

  const golemQuery = useQuery({
    queryKey: ['golem', resolvedGolemId],
    queryFn: () => getGolem(resolvedGolemId),
    enabled: hasResolvedGolemId,
    refetchInterval: 10_000,
  });
  const isOnline = golemQuery.data?.state === 'ONLINE';
  const sessionsEnabled = canLoadSessions(resolvedGolemId, isOnline);
  const selectedSessionEnabled = canLoadSelectedSession(resolvedGolemId, selectedSessionId, isOnline);
  const traceEnabled = canLoadTrace(resolvedGolemId, selectedSessionId, isOnline, detailsRequested);

  const sessionsQuery = useQuery({
    queryKey: ['inspection-sessions', resolvedGolemId, channelFilter],
    queryFn: () => listInspectionSessions(resolvedGolemId, channelFilter || undefined),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  useSelectedSession(sessionsQuery.data ?? [], selectedSessionId, setSelectedSessionId);

  useEffect(() => {
    setDetailsRequested(false);
  }, [selectedSessionId]);

  const sessionQuery = useQuery({
    queryKey: ['inspection-session', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSession(resolvedGolemId, selectedSessionId ?? ''),
    enabled: selectedSessionEnabled,
    refetchInterval: 10_000,
  });

  const traceSummaryQuery = useQuery({
    queryKey: ['inspection-trace-summary', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSessionTraceSummary(resolvedGolemId, selectedSessionId ?? ''),
    enabled: selectedSessionEnabled,
    refetchInterval: 10_000,
  });

  const traceQuery = useQuery({
    queryKey: ['inspection-trace', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSessionTrace(resolvedGolemId, selectedSessionId ?? ''),
    enabled: traceEnabled,
  });

  const selfEvolvingRunsQuery = useQuery({
    queryKey: ['self-evolving-runs', resolvedGolemId],
    queryFn: () => listSelfEvolvingRuns(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  const selfEvolvingCandidatesQuery = useQuery({
    queryKey: ['self-evolving-candidates', resolvedGolemId],
    queryFn: () => listSelfEvolvingCandidates(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  const selfEvolvingLineageQuery = useQuery({
    queryKey: ['self-evolving-lineage', resolvedGolemId],
    queryFn: () => getSelfEvolvingLineage(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  const approvalsQuery = useQuery({
    queryKey: ['approvals', resolvedGolemId, 'SELF_EVOLVING_PROMOTION'],
    queryFn: () => listApprovals({ golemId: resolvedGolemId }),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  const {
    clearMutation,
    compactMutation,
    deleteMutation,
    snapshotExportMutation,
    traceExportMutation,
  } = useInspectionActions({
    golemId: resolvedGolemId,
    selectedSessionId,
    keepLast,
    queryClient,
    setFeedback,
    setDetailsRequested,
    setSelectedSessionId,
  });

  const selectedSessionSummary = useMemo(
    () => (sessionsQuery.data ?? []).find((session) => session.id === selectedSessionId) ?? null,
    [selectedSessionId, sessionsQuery.data],
  );
  const promotionApprovals = useMemo(
    () => (approvalsQuery.data ?? []).filter((approval) => approval.subjectType === 'SELF_EVOLVING_PROMOTION'),
    [approvalsQuery.data],
  );
  const selfEvolvingRuns = selfEvolvingRunsQuery.data ?? [];
  const selectedSelfEvolvingRun = useMemo(
    () => selfEvolvingRuns.find((run) => run.id === selectedSelfEvolvingRunId) ?? selfEvolvingRuns[0] ?? null,
    [selectedSelfEvolvingRunId, selfEvolvingRuns],
  );

  const channelOptions = useMemo(() => {
    const values = new Set(['']);
    (sessionsQuery.data ?? []).forEach((session) => values.add(session.channelType));
    return Array.from(values);
  }, [sessionsQuery.data]);

  useEffect(() => {
    if (selfEvolvingRuns.length === 0) {
      setSelectedSelfEvolvingRunId(null);
      return;
    }
    if (selectedSelfEvolvingRunId == null || !selfEvolvingRuns.some((run) => run.id === selectedSelfEvolvingRunId)) {
      setSelectedSelfEvolvingRunId(selfEvolvingRuns[0].id);
    }
  }, [selectedSelfEvolvingRunId, selfEvolvingRuns]);

  const actionDialogConfig = buildActionDialogConfig(
    actionDialog,
    clearMutation.isPending,
    deleteMutation.isPending,
  );
  const selectedSession = sessionQuery.data;
  const isMutating = compactMutation.isPending || clearMutation.isPending || deleteMutation.isPending;

  async function handleActionConfirm() {
    try {
      await runConfirmedAction(actionDialog, clearMutation, deleteMutation);
      setActionDialog(null);
    } catch {
      // mutation handlers already surface feedback
    }
  }

  if (!hasResolvedGolemId) {
    return <MissingGolemIdPanel />;
  }

  return (
    <div className="grid gap-4">
      <InspectionPageHeader
        golem={golemQuery.data}
        channelFilter={channelFilter}
        channelOptions={channelOptions}
        isOnline={isOnline}
        onChannelFilterChange={setChannelFilter}
      />

      <InspectionFeedbackBanner feedback={feedback} />

      <InspectionStatusPanels
        isLoading={golemQuery.isLoading}
        error={golemQuery.error}
        showOffline={Boolean(golemQuery.data && !isOnline)}
      />

      {isOnline ? (
        <InspectionOnlineContent
          sessions={sessionsQuery.data ?? []}
          selectedSessionId={selectedSessionId}
          sessionsLoading={sessionsQuery.isLoading}
          sessionsError={sessionsQuery.error}
          selectedSessionSummary={selectedSessionSummary}
          selectedSession={selectedSession}
          sessionLoading={sessionQuery.isLoading}
          sessionError={sessionQuery.error}
          keepLast={keepLast}
          isMutating={isMutating}
          isExportingTrace={traceExportMutation.isPending}
          traceSummary={traceSummaryQuery.data ?? null}
          trace={traceQuery.data ?? null}
          messages={selectedSession?.messages ?? []}
          isLoadingTraceSummary={traceSummaryQuery.isLoading}
          isLoadingTrace={traceQuery.isLoading}
          traceErrorMessage={buildTraceErrorMessage(traceSummaryQuery.error, traceQuery.error)}
          isExportingSnapshot={snapshotExportMutation.isPending}
          selfEvolvingRuns={selfEvolvingRuns}
          selectedSelfEvolvingRunId={selectedSelfEvolvingRunId}
          selectedSelfEvolvingRun={selectedSelfEvolvingRun}
          selfEvolvingCandidates={selfEvolvingCandidatesQuery.data ?? []}
          selfEvolvingLineage={selfEvolvingLineageQuery.data ?? { golemId: resolvedGolemId, nodes: [] }}
          promotionApprovals={promotionApprovals}
          onSelectSession={(sessionId) => {
            setSelectedSessionId(sessionId);
            setFeedback(null);
          }}
          onSelectSelfEvolvingRun={setSelectedSelfEvolvingRunId}
          onKeepLastChange={setKeepLast}
          onCompact={() => {
            void compactMutation.mutateAsync();
          }}
          onClear={() => setActionDialog('clear')}
          onExportTrace={() => {
            void traceExportMutation.mutateAsync();
          }}
          onDelete={() => setActionDialog('delete')}
          onLoadTrace={() => setDetailsRequested(true)}
          onExportSnapshotPayload={async (snapshotId, role, spanName) => {
            await snapshotExportMutation.mutateAsync({ snapshotId, role, spanName });
          }}
        />
      ) : null}

      {actionDialogConfig ? (
        <InspectionActionDialog
          open
          title={actionDialogConfig.title}
          description={actionDialogConfig.description}
          confirmLabel={actionDialogConfig.confirmLabel}
          isPending={actionDialogConfig.isPending}
          tone={actionDialogConfig.tone}
          onClose={() => setActionDialog(null)}
          onConfirm={handleActionConfirm}
        />
      ) : null}
    </div>
  );
}
