import { useMutation, type QueryClient } from '@tanstack/react-query';
import {
  clearInspectionSession,
  compactInspectionSession,
  deleteInspectionSession,
  exportInspectionSessionTrace,
  exportInspectionSessionTraceSnapshotPayload,
} from '../../lib/api/inspectionApi';
import { readErrorMessage } from '../../lib/format';
import { downloadBlob, downloadJsonFile } from './inspectionDownloads';
import type { FeedbackState } from './inspectionPageUtils';

export type InspectionAction = 'clear' | 'delete' | null;

export interface ActionDialogConfig {
  description: string;
  confirmLabel: string;
  isPending: boolean;
  title: string;
  tone: 'default' | 'danger';
}

export function buildActionDialogConfig(
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

export async function runConfirmedAction(
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

export function useInspectionActions({
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
  queryClient: QueryClient;
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
